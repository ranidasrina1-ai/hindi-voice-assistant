package com.voiceassistant.hindi

object QwenNative {
    private val available: Boolean = runCatching {
        System.loadLibrary("qwenjni")
    }.isSuccess

    fun isAvailable(): Boolean = available

    external fun generate(
        modelPath: String,
        prompt: String,
        grammar: String,
        maxTokens: Int,
        nCtx: Int,
        nThreads: Int
    ): String
}
