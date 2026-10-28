package com.voiceassistant.hindi

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class QwenPhase {
    ABSENT,
    WAITING,
    DOWNLOADING,
    NEEDS_VERIFY,
    VERIFYING,
    READY,
    FAILED
}

data class QwenSnapshot(
    val phase: QwenPhase,
    val downloaded: Long = 0L,
    val total: Long = QwenModelManager.EXPECTED_SIZE,
    val message: String? = null
) {
    val progress: Float
        get() {
            if (total <= 0L) return 0f
            return (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }
}

object QwenModelManager {
    const val EXPECTED_SIZE = 428_730_208L
    private const val FILENAME = "qwen2.5-0.5b-instruct-q4_0.gguf"
    private const val URL =
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf?download=true"
    private const val SHA256 =
        "7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed"
    private const val MIN_FREE_SPACE = EXPECTED_SIZE + 64L * 1024L * 1024L

    fun modelFile(context: Context): File = File(modelsDir(context), FILENAME)

    fun isReady(context: Context): Boolean {
        val file = modelFile(context)
        if (!file.isFile || file.length() != EXPECTED_SIZE) return false
        return SettingsStore(context).qwenVerified(file.length(), file.lastModified())
    }

    fun safeSnapshot(context: Context): QwenSnapshot =
        runCatching { snapshot(context) }.getOrElse {
            QwenSnapshot(QwenPhase.FAILED, message = "Model state check nahi hua. Dobara try karo.")
        }

    fun snapshot(context: Context): QwenSnapshot {
        val settings = SettingsStore(context)
        val file = modelFile(context)
        val downloadId = settings.qwenDownloadId

        if (downloadId > 0L) {
            val manager = context.getSystemService(DownloadManager::class.java)
            if (manager == null) {
                settings.qwenDownloadId = -1L
            } else {
                val cursor = runCatching {
                    manager.query(DownloadManager.Query().setFilterById(downloadId))
                }.getOrNull()
                if (cursor == null) {
                    settings.qwenDownloadId = -1L
                } else {
                    cursor.use {
                        if (!it.moveToFirst()) {
                            settings.qwenDownloadId = -1L
                        } else {
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val downloaded = it.getLong(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            ).coerceAtLeast(0L)
                            val total = it.getLong(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            ).takeIf { value -> value > 0L } ?: EXPECTED_SIZE

                            when (status) {
                                DownloadManager.STATUS_RUNNING -> return QwenSnapshot(
                                    QwenPhase.DOWNLOADING,
                                    downloaded = downloaded,
                                    total = total
                                )

                                DownloadManager.STATUS_PENDING,
                                DownloadManager.STATUS_PAUSED -> return QwenSnapshot(
                                    QwenPhase.WAITING,
                                    downloaded = downloaded,
                                    total = total,
                                    message = "WiFi ka wait ho raha hai."
                                )

                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    settings.qwenDownloadId = -1L
                                    if (file.isFile && file.length() == EXPECTED_SIZE) {
                                        return if (settings.qwenVerified(file.length(), file.lastModified())) {
                                            QwenSnapshot(QwenPhase.READY, downloaded = EXPECTED_SIZE)
                                        } else {
                                            QwenSnapshot(QwenPhase.NEEDS_VERIFY, downloaded = EXPECTED_SIZE)
                                        }
                                    }
                                    manager.remove(downloadId)
                                    file.delete()
                                    val message = "Download adhoora hai. Dobara try karo."
                                    settings.qwenError = message
                                    return QwenSnapshot(QwenPhase.FAILED, message = message)
                                }

                                DownloadManager.STATUS_FAILED -> {
                                    manager.remove(downloadId)
                                    file.delete()
                                    settings.qwenDownloadId = -1L
                                    val message = "Download fail ho gaya. WiFi check karke dobara try karo."
                                    settings.qwenError = message
                                    return QwenSnapshot(QwenPhase.FAILED, message = message)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (file.isFile && file.length() == EXPECTED_SIZE) {
            return if (settings.qwenVerified(file.length(), file.lastModified())) {
                QwenSnapshot(QwenPhase.READY, downloaded = EXPECTED_SIZE)
            } else {
                QwenSnapshot(QwenPhase.NEEDS_VERIFY, downloaded = EXPECTED_SIZE)
            }
        }

        settings.qwenError?.let { error ->
            return QwenSnapshot(QwenPhase.FAILED, message = error)
        }

        return QwenSnapshot(QwenPhase.ABSENT)
    }

    fun startDownload(context: Context): String? {
        val settings = SettingsStore(context)
        val current = runCatching { snapshot(context) }.getOrNull()
            ?: return "Model state check nahi hua. Dobara try karo."
        if (current.phase != QwenPhase.ABSENT && current.phase != QwenPhase.FAILED) {
            return null
        }

        val externalRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return "External storage available nahi hai."
        val dir = File(externalRoot, "models")
        if (!dir.exists() && !dir.mkdirs()) return "Model folder nahi ban paya."

        val stat = StatFs(dir.absolutePath)
        if (stat.availableBytes < MIN_FREE_SPACE) {
            return "Storage kam hai. Kam se kam 500 MB free chahiye."
        }

        val file = File(dir, FILENAME)
        if (file.exists() && file.length() != EXPECTED_SIZE) {
            file.delete()
        }

        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return "DownloadManager available nahi hai."

        val request = DownloadManager.Request(Uri.parse(URL))
            .setTitle("Qwen local AI model")
            .setDescription("428 MB, WiFi only")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "models/$FILENAME"
            )
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setMimeType("application/octet-stream")
            .addRequestHeader("User-Agent", "HindiVoiceAssistant/1.0")

        return try {
            settings.qwenError = null
            val downloadId = manager.enqueue(request)
            settings.setQwenDownloadIdSync(downloadId)
            null
        } catch (_: Exception) {
            "Download start nahi ho paya."
        }
    }

    suspend fun verify(context: Context): Boolean = withContext(Dispatchers.IO) {
        val settings = SettingsStore(context)
        val file = modelFile(context)
        if (!file.isFile || file.length() != EXPECTED_SIZE) {
            settings.qwenError = "Model file adhoori hai. Dobara download karo."
            return@withContext false
        }

        val actual = try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(1024 * 1024).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        } catch (_: Exception) {
            file.delete()
            settings.clearQwenVerification()
            settings.qwenDownloadId = -1L
            settings.qwenError = "Model verify fail ho gaya. Dobara download karo."
            return@withContext false
        }
        if (!actual.equals(SHA256, ignoreCase = true)) {
            file.delete()
            settings.clearQwenVerification()
            settings.qwenDownloadId = -1L
            settings.qwenError = "Model verify fail ho gaya. Dobara download karo."
            return@withContext false
        }

        settings.markQwenVerified(file.length(), file.lastModified())
        settings.qwenDownloadId = -1L
        settings.qwenError = null
        true
    }

    private fun modelsDir(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.noBackupFilesDir
        return File(root, "models")
    }
}
