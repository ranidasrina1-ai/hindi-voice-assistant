package com.voiceassistant.hindi

import org.json.JSONObject

object QwenEngine {

    private const val GRAMMAR = """
root ::= "{" ws "\"intent\"" ws ":" ws intent ws "," ws "\"contact\"" ws ":" ws nullable ws "," ws "\"query\"" ws ":" ws nullable ws "," ws "\"message\"" ws ":" ws nullable ws "}" ws
intent ::= "\"SEARCH\"" | "\"CALL\"" | "\"WHATSAPP\"" | "\"PLAY_MUSIC\"" | "\"UNKNOWN\""
nullable ::= string | "null"
string ::= "\"" ( [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}) )* "\"" ws
ws ::= [ \t\n\r]*
"""

    fun interpret(context: android.content.Context, transcript: String): ParsedCommand? {
        if (!QwenNative.isAvailable()) return null
        val model = QwenModelManager.modelFile(context)
        if (!model.isFile) return null

        val safeTranscript = transcript
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("<|", "< |")
            .replace("|>", "| >")
            .take(600)

        val prompt = """
<|im_start|>system
You convert one phone voice command into JSON. The command can be Hindi, Hinglish, or English.
Return only one JSON object. No markdown. No explanation.
Intents:
SEARCH: web search. Put the search text in query.
CALL: phone call. Put the person's name or number in contact.
WHATSAPP: send a WhatsApp message. Put the person in contact and the message in message.
PLAY_MUSIC: play a song in ArchiveTune. Put the song name in query.
UNKNOWN: only if the command is not one of the above.
Use null for fields that are not needed. Keep names and messages exactly as spoken. Do not invent a contact or message.
<|im_end|>
<|im_start|>user
Transcript: "$safeTranscript"
<|im_end|>
<|im_start|>assistant
""".trimIndent()

        return try {
            val output = QwenNative.generate(
                modelPath = model.absolutePath,
                prompt = prompt,
                grammar = GRAMMAR,
                maxTokens = 128,
                nCtx = 768,
                nThreads = 4
            )
            parseOutput(output)
        } catch (error: Throwable) {
            if (error is VirtualMachineError) throw error
            null
        }
    }

    fun parseOutput(raw: String): ParsedCommand? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            val json = JSONObject(raw.substring(start, end + 1))
            val intent = json.optString("intent").trim().uppercase()
            val contact = optional(json, "contact")
            val query = optional(json, "query")
            val message = optional(json, "message")

            when (intent) {
                "SEARCH" -> query?.let { ParsedCommand(IntentType.SEARCH, query = it) }
                "PLAY_MUSIC" -> query?.let { ParsedCommand(IntentType.PLAY_MUSIC, query = it) }
                "CALL" -> contact?.let { ParsedCommand(IntentType.CALL, contact = it) }
                "WHATSAPP" -> if (contact != null && message != null) {
                    ParsedCommand(IntentType.WHATSAPP, contact = contact, message = message)
                } else {
                    null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun optional(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optString(key).trim()
        if (value.isBlank() || value.equals("null", ignoreCase = true)) return null
        return value
    }
}
