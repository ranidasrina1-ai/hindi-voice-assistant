package com.voiceassistant.hindi

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

class AssistantService : Service() {

    companion object {
        const val ACTION_START = "com.voiceassistant.hindi.action.START"
        const val ACTION_STOP = "com.voiceassistant.hindi.action.STOP"
        const val EXTRA_LANGUAGE = "language"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelCommand()
                return START_NOT_STICKY
            }

            ACTION_START -> Unit

            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (job?.isActive == true) return START_NOT_STICKY

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            AssistantState.error(getString(R.string.status_mic_permission))
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        try {
            startInForeground()
        } catch (_: Exception) {
            AssistantState.error("Foreground service start nahi hui.")
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val language = AssistantLanguage.fromCode(intent?.getStringExtra(EXTRA_LANGUAGE))
        job = scope.launch {
            runCommand(language)
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        Notifications.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.listening(this, getString(R.string.notification_listening_text)),
            type
        )
    }

    private suspend fun runCommand(language: AssistantLanguage) {
        try {
            AssistantState.beginListening()
            AssistantState.status("Voice model taiyar kar raha hoon…")
            updateNotification("Voice model taiyar kar raha hoon…")

            val model = try {
                ModelInstaller.ensureInstalled(this, language)
                VoskModelCache.acquire(this, language)
            } catch (_: Exception) {
                AssistantState.error(getString(R.string.status_model_missing))
                return
            }
            currentCoroutineContext().ensureActive()
            requestAudioFocus()
            AssistantState.status(getString(R.string.listening))
            val transcript = recordAndRecognize(model)
            abandonAudioFocus()
            currentCoroutineContext().ensureActive()

            if (transcript.isBlank()) {
                AssistantState.error(getString(R.string.status_no_speech))
                return
            }

            AssistantState.transcript(transcript)
            AssistantLog.record(this, transcript)

            val pending = AssistantState.state.value.pendingAction
            if (pending != null) {
                if (System.currentTimeMillis() - pending.createdAtMillis > PendingAction.TTL_MS) {
                    Notifications.cancelConfirmation(this)
                    AssistantState.error("Confirmation expire ho gaya. Dobara command bolo.")
                    return
                }
                val normalized = normalizeSpeech(transcript)
                if (isAffirmative(normalized)) {
                    Notifications.cancelConfirmation(this)
                    setActionResult(ActionExecutor.executePending(this, pending))
                    return
                }
                if (isNegative(normalized)) {
                    Notifications.cancelConfirmation(this)
                    AssistantState.done(getString(R.string.status_cancelled))
                    return
                }
                Notifications.cancelConfirmation(this)
                AssistantState.clearPending()
            }

            AssistantState.thinking()
            AssistantState.status(getString(R.string.thinking))
            updateNotification(getString(R.string.thinking))

            var command = CommandParser.parse(transcript)
            if (command.intent == IntentType.UNKNOWN && QwenModelManager.isReady(this)) {
                AssistantState.status("Local AI se samajh raha hoon…")
                command = QwenEngine.interpret(this, transcript) ?: command
            }

            currentCoroutineContext().ensureActive()
            handleCommand(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantState.error(error.message ?: "Command fail ho gaya.")
        } finally {
            abandonAudioFocus()
            stopForegroundAndSelf()
        }
    }

    private fun handleCommand(command: ParsedCommand) {
        when (command.intent) {
            IntentType.SEARCH -> {
                val query = command.query.orEmpty()
                setActionResult(ActionExecutor.search(this, query))
            }

            IntentType.PLAY_MUSIC -> {
                val query = command.query.orEmpty()
                setActionResult(ActionExecutor.playMusic(this, query))
            }

            IntentType.CALL -> prepareConfirmation(command)

            IntentType.WHATSAPP -> {
                if (command.message.isNullOrBlank()) {
                    AssistantState.error(getString(R.string.status_message_missing))
                } else {
                    prepareConfirmation(command)
                }
            }

            IntentType.UNKNOWN -> AssistantState.error(getString(R.string.status_unknown))
        }
    }

    private fun prepareConfirmation(command: ParsedCommand) {
        val contact = ContactResolver.resolve(this, command.contact.orEmpty())
        if (contact == null) {
            AssistantState.error(getString(R.string.status_contact_missing))
            return
        }

        val pending = PendingAction(command, contact, command.message)
        AssistantState.needConfirm(pending)

        val isCall = command.intent == IntentType.CALL
        val title = getString(
            if (isCall) R.string.confirm_call_title else R.string.confirm_whatsapp_title
        )
        val details = if (isCall) {
            "${contact.displayName} (${contact.number})"
        } else {
            "${contact.displayName} (${contact.number}): ${command.message.orEmpty().take(90)}"
        }
        Notifications.confirmation(this, title, details)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun normalizeSpeech(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isAffirmative(text: String): Boolean {
        val exact = setOf(
            "yes", "yeah", "yep", "haan", "han", "ha", "haa", "ok", "okay",
            "confirm", "sahi hai", "theek hai", "thik hai", "bhejo", "bhej do",
            "call karo", "kar do", "kardo",
            "हाँ", "हां", "हा", "ठीक है", "भेजो", "भेज दो", "कॉल करो", "कर दो"
        )
        return text in exact ||
            text.startsWith("yes ") ||
            text.startsWith("haan ") ||
            text.startsWith("han ") ||
            text.startsWith("हाँ ") ||
            text.startsWith("हां ") ||
            text.startsWith("हा ")
    }

    private fun isNegative(text: String): Boolean {
        val exact = setOf(
            "no", "nah", "nahi", "na", "cancel", "mat karo", "rehne do", "band karo",
            "नहीं", "ना", "मत करो", "रहने दो", "बंद करो"
        )
        return text in exact ||
            text.startsWith("nahi ") ||
            text.startsWith("नहीं ")
    }

    private fun setActionResult(result: ActionResult) {
        if (result.ok) {
            AssistantState.done(result.message)
        } else {
            AssistantState.error(result.message)
        }
    }

    private suspend fun recordAndRecognize(model: Model): String {
        var recognizer: Recognizer? = null
        var recorder: AudioRecord? = null

        try {
            val activeRecognizer = Recognizer(model, 16000.0f)
            recognizer = activeRecognizer
            val minBuffer = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "AudioRecord buffer nahi mila." }

            val activeRecorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer, 8192)
            )
            recorder = activeRecorder
            check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) {
                "Microphone initialize nahi hua."
            }

            activeRecorder.startRecording()
            val buffer = ShortArray(1600)
            val startedAt = SystemClock.elapsedRealtime()
            var speechStarted = false
            var lastVoiceAt = startedAt
            var lastPartial = ""

            while (true) {
                currentCoroutineContext().ensureActive()
                val read = activeRecorder.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (SystemClock.elapsedRealtime() - startedAt > 15000L) break
                    delay(10L)
                    continue
                }

                activeRecognizer.acceptWaveForm(buffer, read)
                val now = SystemClock.elapsedRealtime()

                var sum = 0.0
                for (index in 0 until read) {
                    val sample = buffer[index].toDouble()
                    sum += sample * sample
                }
                val rms = if (read > 0) sqrt(sum / read) else 0.0

                val partial = runCatching {
                    val json = JSONObject(activeRecognizer.partialResult)
                    json.optString("partial").ifBlank { json.optString("text") }.trim()
                }.getOrDefault("")
                if (partial.isNotBlank()) lastPartial = partial

                if (rms > 650.0 || partial.isNotBlank()) {
                    if (!speechStarted) {
                        speechStarted = true
                        AssistantState.status(getString(R.string.listening))
                    }
                    lastVoiceAt = now
                }

                val elapsed = now - startedAt
                if (!speechStarted && elapsed > 8000L) break
                if (speechStarted && now - lastVoiceAt > 1400L) break
                if (elapsed > 15000L) break
            }

            val finalText = runCatching {
                JSONObject(activeRecognizer.finalResult).optString("text").trim()
            }.getOrDefault("")
            return finalText.ifBlank { lastPartial }
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { recognizer?.close() }
        }
    }

    private fun requestAudioFocus() {
        if (focusRequest != null) return
        val manager = getSystemService(AudioManager::class.java) ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        manager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        val manager = getSystemService(AudioManager::class.java)
        manager?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.listening(this, text)
        )
    }

    private fun cancelCommand() {
        job?.cancel()
        AssistantState.error(getString(R.string.status_cancelled))
        abandonAudioFocus()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        abandonAudioFocus()
        if (AssistantState.state.value.isBusy) {
            AssistantState.error(getString(R.string.status_cancelled))
        }
        super.onDestroy()
    }
}
