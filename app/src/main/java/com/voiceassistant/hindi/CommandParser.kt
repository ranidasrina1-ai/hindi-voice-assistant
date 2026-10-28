package com.voiceassistant.hindi

import java.text.Normalizer
import java.util.Locale

object CommandParser {

    private val whitespace = Regex("\\s+")
    private val leadingFillers = Regex(
        "^(?:(?:hey|ok|okay|please|kripya|assistant|sun|suno|सुनो|कृपया)\\s+)+",
        RegexOption.IGNORE_CASE
    )
    private val trailingFillers = Regex("(?:\\s+(?:please|kripya|कृपया))$", RegexOption.IGNORE_CASE)
    private val devanagariDigits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val whatsappPatterns = listOf(
        Regex(
            "^(.+?)\\s+(?:ko|को)\\s+(?:whats\\s*app|whatsapp|व्हाट्सएप|व्हाट्सऐप|वाट्सएप|व्हाट्सएप्प)\\s+(?:par|pe|पर|पे)?\\s*(?:message|msg|मैसेज|संदेश)\\s+(?:bhejo|bhej\\s+do|भेजो|भेज\\s+दो|भेजना|करो|karo|kar\\s+do)\\s*(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:whats\\s*app|whatsapp|व्हाट्सएप|व्हाट्सऐप|वाट्सएप)\\s+(?!par\\s|pe\\s|पर\\s|पे\\s)(.+?)\\s+(?:ko|को)\\s+(?:message|msg|मैसेज|संदेश)\\s+(?:bhejo|bhej\\s+do|भेजो|भेज\\s+दो|भेजना|करो|karo|kar\\s+do)\\s*(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:whats\\s*app|whatsapp|व्हाट्सएप|व्हाट्सऐप|वाट्सएप)\\s+(?:par|pe|पर|पे)?\\s*(.+?)\\s+(?:ko|को)\\s+(?:message|msg|मैसेज|संदेश)\\s+(?:bhejo|bhej\\s+do|भेजो|भेज\\s+दो|भेजना|करो|karo|kar\\s+do)\\s*(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        )
    )

    private val callPatterns = listOf(
        Regex(
            "^(.+?)\\s+(?:ko|को)\\s+(?:call|कॉल|phone|फोन)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो|lagao|लगाओ|milao|मिलाओ)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+(?:call|कॉल|phone|फोन)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो|lagao|लगाओ|milao|मिलाओ)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:call|कॉल|phone|फोन)\\s+(?:karo\\s+|kar\\s+do\\s+|करो\\s+|कर\\s+दो\\s+)?(.+?)$",
            RegexOption.IGNORE_CASE
        )
    )

    private val musicPatterns = listOf(
        Regex(
            "^(?:archive\\s*tune|archive tune|आर्काइव\\s*ट्यून|आर्चिव\\s*ट्यून|एको\\s*ट्यून)\\s+(?:par|pe|पर|पे|mein|में)?\\s*(?:play|chalao|bajao|चलाओ|बजाओ|chala\\s+do|चला\\s+दो)\\s+(.+?)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:archive\\s*tune|archive tune|आर्काइव\\s*ट्यून|आर्चिव\\s*ट्यून|एको\\s*ट्यून)\\s+(?:par|pe|पर|पे|mein|में)?\\s*(.+?)\\s+(?:play|chalao|bajao|चलाओ|बजाओ|chala\\s+do|चला\\s+दो)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:play|chalao|bajao|चलाओ|बजाओ|chala\\s+do|चला\\s+दो)\\s+(.+?)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+(?:play|chalao|bajao|चलाओ|बजाओ|chala\\s+do|चला\\s+दो)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        )
    )

    private val googleSearchPatterns = listOf(
        Regex(
            "^(?:google|गूगल|गुगल)\\s+(?:par|pe|पर|पे|mein|में)\\s*(.+?)\\s*(?:search|सर्च|खोजो|खोज|dhundo|dhoondo|ढूंढो|ढूँढो)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:search|सर्च|खोजो|dhundo|dhoondo|ढूंढो|ढूँढो)\\s+(.+?)\\s*(?:on|par|पर)?\\s*(?:google|गूगल)?\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+(?:search|सर्च|खोजो|dhundo|dhoondo|ढूंढो|ढूँढो)\\s*(?:karo|kar\\s+do|करो|कर\\s+दो)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:google|गूगल|गुगल)\\s+(?:par|pe|पर|पे|mein|में)\\s*(.+)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:google|गूगल|गुगल)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
    )

    fun parse(rawInput: String): ParsedCommand {
        val input = prepare(rawInput)
        if (input.isBlank()) return ParsedCommand(IntentType.UNKNOWN)

        for (pattern in whatsappPatterns) {
            val match = pattern.matchEntire(input) ?: continue
            val contact = cleanContact(match.groupValues[1])
            val message = cleanMessage(match.groupValues[2])
            if (contact.isNotBlank()) {
                return ParsedCommand(
                    intent = IntentType.WHATSAPP,
                    contact = contact,
                    message = message.ifBlank { null }
                )
            }
        }

        for (pattern in callPatterns) {
            val match = pattern.matchEntire(input) ?: continue
            val contact = cleanContact(match.groupValues[1])
            if (contact.isNotBlank() && !isNoise(contact)) {
                return ParsedCommand(intent = IntentType.CALL, contact = contact)
            }
        }

        for (pattern in musicPatterns) {
            val match = pattern.matchEntire(input) ?: continue
            val query = cleanMusicQuery(match.groupValues[1])
            if (query.isNotBlank()) {
                return ParsedCommand(intent = IntentType.PLAY_MUSIC, query = query)
            }
        }

        for (pattern in googleSearchPatterns) {
            val match = pattern.matchEntire(input) ?: continue
            val query = cleanSearchQuery(match.groupValues[1])
            if (query.isNotBlank()) {
                return ParsedCommand(intent = IntentType.SEARCH, query = query)
            }
        }

        return ParsedCommand(IntentType.UNKNOWN)
    }

    fun prepare(rawInput: String): String {
        var value = rawInput
        for ((from, to) in devanagariDigits) {
            value = value.replace(from, to)
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
        value = value
            .replace('।', ' ')
            .replace('?', ' ')
            .replace('!', ' ')
            .replace(',', ' ')
            .replace(Regex("[“”\"`]"), " ")
            .replace(whitespace, " ")
            .trim()
        value = leadingFillers.replace(value, "")
        value = trailingFillers.replace(value, "")
        return value.trim()
    }

    private fun cleanContact(raw: String): String {
        return raw
            .trim()
            .removePrefix("to ")
            .removePrefix("To ")
            .replace(Regex("^(?:to|को)\\s+", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', '?', '!', ':', ';')
    }

    private fun cleanMessage(raw: String): String {
        return raw
            .trim()
            .replace(Regex("^(?:ki|कि|that|saying)\\s+", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', '?', '!')
    }

    private fun cleanMusicQuery(raw: String): String {
        return raw
            .trim()
            .replace(Regex("^(?:song|gaana|gana|music|म्यूजिक|गाना|गीत)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:song|gaana|gana|म्यूजिक|गाना|गीत)$", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', '?', '!', ':', ';')
    }

    private fun cleanSearchQuery(raw: String): String {
        return raw
            .trim()
            .replace(Regex("^(?:for|about|ke\\s+baare\\s+mein|के\\s+बारे\\s+में)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:on|par|पर)\\s+(?:google|गूगल)$", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', '?', '!', ':', ';')
    }

    private fun isNoise(value: String): Boolean {
        val normalized = value.lowercase(Locale.ROOT)
        return normalized in setOf(
            "call", "phone", "karo", "kar", "kar do", "lagao", "milao",
            "कॉल", "फोन", "करो", "कर", "कर दो", "लगाओ", "मिलाओ",
            "whatsapp", "व्हाट्सएप"
        )
    }
}
