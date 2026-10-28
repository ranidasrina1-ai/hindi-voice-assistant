package com.voiceassistant.hindi

import java.text.Normalizer
import java.util.Locale

object CommandParser {

    private const val CONTACT_LINKS = "(?:ko|को|se|से)"
    private const val CALL_WORDS =
        "(?:call|कॉल|phone|फोन|फ़ोन|ring|रिंग|baat|बात|milao|milana|मिलाओ|मिलाना)"
    private const val CALL_TAILS =
        "(?:karo|kar\\s+do|kar\\s+de|kar\\s+dena|karna|karni|karna\\s+hai|karni\\s+hai|" +
            "lagao|lagana|lagana\\s+hai|lagado|lagwao|karwao|milao|milana|milana\\s+hai|" +
            "baat\\s+karo|baat\\s+kar\\s+do|kar\\s+lo|karlo|" +
            "करो|कर\\s+दो|कर\\s+दे|कर\\s+देना|करना|करनी|करना\\s+है|करनी\\s+है|" +
            "लगाओ|लगाना|लगाना\\s+है|लगादो|लगवाओ|करवाओ|मिलाओ|मिलाना|मिलाना\\s+है|" +
            "बात\\s+करो|बात\\s+कर\\s+दो|कर\\s+लो)"
    private const val MESSAGE_WORDS =
        "(?:message|msg|text|मैसेज|मेसेज|संदेश|टेक्स्ट)"
    private const val SEND_VERBS =
        "(?:bhejo|bhej\\s+do|bhej\\s+de|bhej\\s+dena|bhejna|bhejni|bhejna\\s+hai|" +
            "bhejni\\s+hai|karo|kar\\s+do|kar\\s+de|करो|कर\\s+दो|भेजो|भेजें|भेजिए|" +
            "भेज\\s+दो|भेजना|भेज\\s+देना|भेज\\s+दे)"
    private const val WHATSAPP_WORDS =
        "(?:whats\\s*app|whatsapp|व्हाट्सएप|व्हाट्सऐप|वाट्सएप|व्हाट्सएप्प)"
    private const val GOOGLE_WORDS = "(?:google|गूगल|गुगल)"
    private const val ARCHIVE_WORDS =
        "(?:archive\\s*tune|archive\\s+tune|आर्काइव\\s*ट्यून|आर्चिव\\s*ट्यून|एको\\s*ट्यून)"
    private const val MUSIC_VERBS =
        "(?:play|chalao|bajao|chala\\s+do|chala\\s+de|baja\\s+do|baja\\s+de|lagao|lagado|" +
            "चलाओ|बजाओ|चला\\s+दो|चला\\s+दे|बजा\\s+दो|लगाओ)"
    private const val SEARCH_VERBS =
        "(?:search|सर्च|खोजो|खोज|dhundo|dhoondo|ढूंढो|ढूँढो|batao|bata\\s+do|bolo|" +
            "bol\\s+do|batado|bata\\s+de|dikhao|dikha\\s+do|बताओ|बता\\s+दो|बोलो|" +
            "बोल\\s+दो|दिखाओ|दिखा\\s+दो)"
    private const val POLITE_TAIL =
        "(?:karo|kar\\s+do|kar\\s+de|करो|कर\\s+दो)?"

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
    private val smallTalk = setOf(
        "hello", "hi", "namaste", "नमस्ते", "thanks", "thank you", "dhanyavad",
        "kya haal", "kya haal hai", "kaise ho", "kaise hain", "kaise hai",
        "क्या हाल", "क्या हाल है", "कैसे हो", "कैसे हैं"
    )

    private val whatsappPatterns = listOf(
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+$WHATSAPP_WORDS\\s+(?:par|pe|पर|पे|me|में)?\\s*" +
                "$MESSAGE_WORDS\\s+$SEND_VERBS\\s*(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$WHATSAPP_WORDS\\s+(?!par\\s|pe\\s|पर\\s|पे\\s|me\\s|में\\s)(.+?)\\s+" +
                "$CONTACT_LINKS\\s+$MESSAGE_WORDS\\s+$SEND_VERBS\\s*" +
                "(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$WHATSAPP_WORDS\\s+(?:par|pe|पर|पे|me|में)?\\s*(.+?)\\s+$CONTACT_LINKS\\s+" +
                "$MESSAGE_WORDS\\s+$SEND_VERBS\\s*(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+$MESSAGE_WORDS\\s+$SEND_VERBS\\s*" +
                "(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+$WHATSAPP_WORDS\\s+" +
                "(?:karo|kar\\s+do|kar\\s+de|करो|कर\\s+दो)\\s*" +
                "(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+" +
                "(?:bol\\s+do|bolo|likho|likh\\s+do|bata\\s+do|batao|text\\s+karo|message\\s+karo|" +
                "बोल\\s+दो|बोलो|लिखो|लिख\\s+दो|बता\\s+दो|बताओ|मैसेज\\s+करो|संदेश\\s+करो)\\s*" +
                "(?:ki|कि|that|saying)?\\s*(.*)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+(.+?)\\s+" +
                "(?:bol\\s+do|bolo|likho|likh\\s+do|बोल\\s+दो|बोलो|लिखो|लिख\\s+दो)$",
            RegexOption.IGNORE_CASE
        )
    )

    private val callPatterns = listOf(
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+$CALL_WORDS\\s*(?:$CALL_TAILS)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CONTACT_LINKS\\s+(?:phone|फोन|फ़ोन|call|कॉल)\\s+" +
                "(?:par|pe|पर|पे|me|में)\\s+(?:baat|बात|call|कॉल)\\s*" +
                "(?:$CALL_TAILS)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$CALL_WORDS\\s*(?:$CALL_TAILS)?$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(?:$CALL_WORDS)\\s+(.+?)(?:\\s+$CALL_TAILS)?$",
            RegexOption.IGNORE_CASE
        )
    )

    private val musicPatterns = listOf(
        Regex(
            "^$ARCHIVE_WORDS\\s+(?:par|pe|पर|पे|me|में|mein)?\\s*$MUSIC_VERBS\\s+" +
                "(.+?)\\s*(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$ARCHIVE_WORDS\\s+(?:par|pe|पर|पे|me|में|mein)?\\s*(.+?)\\s+$MUSIC_VERBS\\s*" +
                "(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$MUSIC_VERBS\\s+(.+?)\\s*(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$MUSIC_VERBS\\s*(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        )
    )

    private val googleSearchPatterns = listOf(
        Regex(
            "^$GOOGLE_WORDS\\s+(?:par|pe|पर|पे|mein|में|me)\\s*(.+?)\\s*$SEARCH_VERBS\\s*" +
                "(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$SEARCH_VERBS\\s+(.+?)\\s*(?:on|par|पर|me|में)?\\s*(?:$GOOGLE_WORDS)?\\s*" +
                "(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+$SEARCH_VERBS\\s*(?:$POLITE_TAIL)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$GOOGLE_WORDS\\s+(?:par|pe|पर|पे|mein|में|me)\\s*(.+)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^$GOOGLE_WORDS\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+(?:on|par|पर|me|में)\\s*$GOOGLE_WORDS$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^(.+?)\\s+(?:batao|bata\\s+do|bolo|bol\\s+do|batado|bata\\s+de|dikhao|dikha\\s+do|" +
                "kya\\s+hai|kaisa\\s+hai|kaisi\\s+hai|kahan\\s+hai|kitna\\s+hai|kitni\\s+hai)$",
            RegexOption.IGNORE_CASE
        )
    )

    fun parse(rawInput: String): ParsedCommand {
        val input = prepare(rawInput)
        if (input.isBlank()) return ParsedCommand(IntentType.UNKNOWN)
        if (input.lowercase(Locale.ROOT) in smallTalk) {
            return ParsedCommand(IntentType.UNKNOWN)
        }

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
            .replace(Regex("^(?:to|को|ko|se|से)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:ji|जी)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:$CALL_TAILS)$", RegexOption.IGNORE_CASE), "")
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
            .replace(
                Regex(
                    "^(?:for|about|ke\\s+baare\\s+mein|के\\s+बारे\\s+में)\\s+",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("\\s+(?:on|par|पर|me|में)\\s+(?:google|गूगल)$", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', '?', '!', ':', ';')
    }

    private fun isNoise(value: String): Boolean {
        val normalized = value.lowercase(Locale.ROOT)
        return normalized in setOf(
            "call", "phone", "ring", "karo", "kar", "kar do", "kar de", "karna",
            "karni", "karna hai", "karni hai", "lagao", "lagana", "lagana hai",
            "lagado", "lagwao", "karwao", "milao", "milana", "milana hai",
            "कॉल", "फोन", "फ़ोन", "रिंग", "करो", "कर", "कर दो", "कर दे", "करना",
            "करनी", "करना है", "करनी है", "लगाओ", "लगाना", "लगाना है", "लगादो",
            "लगवाओ", "करवाओ", "मिलाओ", "मिलाना", "मिलाना है",
            "whatsapp", "व्हाट्सएप"
        )
    }
}
