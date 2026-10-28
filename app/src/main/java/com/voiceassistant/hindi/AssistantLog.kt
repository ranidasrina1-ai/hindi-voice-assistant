package com.voiceassistant.hindi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AssistantLogEntry(
    val timestamp: Long,
    val transcript: String
)

object AssistantLog {
    private const val PREFS = "assistant_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 20

    private val _entries = MutableStateFlow<List<AssistantLogEntry>>(emptyList())
    val entries: StateFlow<List<AssistantLogEntry>> = _entries.asStateFlow()

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val storage = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = storage
        _entries.value = read(storage)
    }

    @Synchronized
    fun record(context: Context, transcript: String) {
        init(context)
        val clean = transcript.trim().take(500)
        if (clean.isBlank()) return

        val next = (_entries.value + AssistantLogEntry(
            timestamp = System.currentTimeMillis(),
            transcript = clean
        )).takeLast(MAX_ENTRIES)

        _entries.value = next
        write(next)
    }

    @Synchronized
    fun clear(context: Context) {
        init(context)
        _entries.value = emptyList()
        write(emptyList())
    }

    private fun read(storage: SharedPreferences): List<AssistantLogEntry> {
        val raw = storage.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val transcript = item.optString("transcript").trim()
                    if (transcript.isBlank()) continue
                    add(
                        AssistantLogEntry(
                            timestamp = item.optLong("timestamp", 0L),
                            transcript = transcript
                        )
                    )
                }
            }.takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<AssistantLogEntry>) {
        val storage = prefs ?: return
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("transcript", entry.transcript)
            )
        }
        storage.edit().putString(KEY, array.toString()).apply()
    }
}
