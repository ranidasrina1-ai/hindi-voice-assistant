package com.voiceassistant.hindi

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

object ModelInstaller {

    fun modelDir(context: Context, language: AssistantLanguage): File {
        return File(context.filesDir, "models/${language.modelFolder}")
    }

    fun isInstalled(context: Context, language: AssistantLanguage): Boolean {
        val dir = modelDir(context, language)
        return File(dir, ".installed").isFile && dir.isDirectory
    }

    fun ensureInstalled(context: Context, language: AssistantLanguage): File {
        val root = File(context.filesDir, "models")
        root.mkdirs()
        val target = modelDir(context, language)
        val marker = File(target, ".installed")
        if (marker.isFile) return target

        val work = File(root, "${language.modelFolder}.installing")
        if (work.exists()) work.deleteRecursively()
        work.mkdirs()

        val assetPath = "vosk/${language.assetName}"
        try {
            context.assets.open(assetPath).use { raw ->
                ZipInputStream(raw).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        if (name.isBlank() || name.startsWith("__MACOSX/")) {
                            zip.closeEntry()
                            continue
                        }
                        val output = File(work, name)
                        val canonical = output.canonicalFile
                        require(canonical.path.startsWith(work.canonicalFile.path + File.separator)) {
                            "Unsafe model archive entry: $name"
                        }
                        if (entry.isDirectory) {
                            canonical.mkdirs()
                        } else {
                            canonical.parentFile?.mkdirs()
                            canonical.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
            }
        } catch (error: Exception) {
            work.deleteRecursively()
            throw IllegalStateException("Model asset missing or broken: $assetPath", error)
        }

        val children = work.listFiles()?.filterNot { it.name.startsWith(".") }.orEmpty()
        val extracted = children.singleOrNull()?.takeIf { it.isDirectory }
        val source = extracted ?: work

        if (target.exists()) target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
        if (source != work) work.deleteRecursively()
        File(target, ".installed").writeText("1")
        return target
    }
}
