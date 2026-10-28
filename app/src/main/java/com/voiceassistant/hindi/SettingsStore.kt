package com.voiceassistant.hindi

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    var language: AssistantLanguage
        get() = AssistantLanguage.fromCode(prefs.getString("language", AssistantLanguage.HINDI.code))
        set(value) = prefs.edit().putString("language", value.code).apply()

    var qwenDownloadId: Long
        get() = prefs.getLong("qwen_download_id", -1L)
        set(value) = prefs.edit().putLong("qwen_download_id", value).apply()

    fun setQwenDownloadIdSync(value: Long) {
        prefs.edit().putLong("qwen_download_id", value).commit()
    }

    fun qwenVerified(length: Long, modified: Long): Boolean {
        return prefs.getLong("qwen_verified_length", -1L) == length &&
            prefs.getLong("qwen_verified_modified", -1L) == modified
    }

    fun markQwenVerified(length: Long, modified: Long) {
        prefs.edit()
            .putLong("qwen_verified_length", length)
            .putLong("qwen_verified_modified", modified)
            .apply()
    }

    fun clearQwenVerification() {
        prefs.edit()
            .remove("qwen_verified_length")
            .remove("qwen_verified_modified")
            .apply()
    }

    var qwenError: String?
        get() = prefs.getString("qwen_error", null)
        set(value) = prefs.edit().putString("qwen_error", value).apply()
}
