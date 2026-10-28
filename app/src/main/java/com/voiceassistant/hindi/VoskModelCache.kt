package com.voiceassistant.hindi

import android.content.Context
import org.vosk.Model

object VoskModelCache {
    private var model: Model? = null
    private var language: AssistantLanguage? = null

    @Synchronized
    fun acquire(context: Context, requested: AssistantLanguage): Model {
        val current = model
        if (current != null && language == requested) return current

        current?.close()
        model = null
        language = null

        val loaded = Model(ModelInstaller.modelDir(context, requested).absolutePath)
        model = loaded
        language = requested
        return loaded
    }
}
