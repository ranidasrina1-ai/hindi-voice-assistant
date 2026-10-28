package com.voiceassistant.hindi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    @Test
    fun `google search in hinglish`() {
        val command = CommandParser.parse("Google par weather search karo")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("weather", command.query)
    }

    @Test
    fun `search before google`() {
        val command = CommandParser.parse("Search cricket score on Google")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("cricket score", command.query)
    }

    @Test
    fun `search verb at end`() {
        val command = CommandParser.parse("weather search karo")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("weather", command.query)
    }

    @Test
    fun `google without search verb`() {
        val command = CommandParser.parse("Google cricket score")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("cricket score", command.query)
    }

    @Test
    fun `call contact`() {
        val command = CommandParser.parse("Rahul Sharma ko call karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul Sharma", command.contact)
    }

    @Test
    fun `call contact english`() {
        val command = CommandParser.parse("Call Rahul")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul", command.contact)
    }

    @Test
    fun `call without ko`() {
        val command = CommandParser.parse("Rahul call karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul", command.contact)
    }

    @Test
    fun `call phone lagao`() {
        val command = CommandParser.parse("Rahul ko phone lagao")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul", command.contact)
    }

    @Test
    fun `call another contact`() {
        val command = CommandParser.parse("Amit Kumar ko call karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Amit Kumar", command.contact)
    }

    @Test
    fun `call with alternate verb`() {
        val command = CommandParser.parse("Rahul ko phone karna hai")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul", command.contact)
    }

    @Test
    fun `call through se baat`() {
        val command = CommandParser.parse("Priya se baat karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Priya", command.contact)
    }

    @Test
    fun `call phone par baat`() {
        val command = CommandParser.parse("Rahul ko phone par baat karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul", command.contact)
    }

    @Test
    fun `call strips honorific`() {
        val command = CommandParser.parse("Rahul Sharma ji ko call karo")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Rahul Sharma", command.contact)
    }

    @Test
    fun `whatsapp message`() {
        val command = CommandParser.parse(
            "Rahul Sharma ko WhatsApp par message bhejo ki main ghar aa raha hoon"
        )
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul Sharma", command.contact)
        assertEquals("main ghar aa raha hoon", command.message)
    }

    @Test
    fun `whatsapp prefix`() {
        val command = CommandParser.parse("WhatsApp par Rahul ko message bhejo ki hello")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul", command.contact)
        assertEquals("hello", command.message)
    }

    @Test
    fun `whatsapp without message`() {
        val command = CommandParser.parse("Rahul ko WhatsApp message bhejo")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul", command.contact)
        assertNull(command.message)
    }

    @Test
    fun `whatsapp another contact without app word`() {
        val command = CommandParser.parse("Sneha ko message bhejo ki kal milte hain")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Sneha", command.contact)
        assertEquals("kal milte hain", command.message)
    }

    @Test
    fun `whatsapp bol do`() {
        val command = CommandParser.parse("Rahul ko bol do ki hello")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul", command.contact)
        assertEquals("hello", command.message)
    }

    @Test
    fun `whatsapp direct app action`() {
        val command = CommandParser.parse("Rahul ko WhatsApp karo ki hello")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul", command.contact)
        assertEquals("hello", command.message)
    }

    @Test
    fun `whatsapp message first bolo`() {
        val command = CommandParser.parse("Rahul ko kal milte hain bol do")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("Rahul", command.contact)
        assertEquals("kal milte hain", command.message)
    }

    @Test
    fun `hindi whatsapp message`() {
        val command = CommandParser.parse("राहुल को मेसेज भेजो कि कल मिलते हैं")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("राहुल", command.contact)
        assertEquals("कल मिलते हैं", command.message)
    }

    @Test
    fun `hindi whatsapp message first`() {
        val command = CommandParser.parse("राहुल को हैलो बोल दो")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("राहुल", command.contact)
        assertEquals("हैलो", command.message)
    }

    @Test
    fun `play song english`() {
        val command = CommandParser.parse("Play Kesariya")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `play song hinglish`() {
        val command = CommandParser.parse("Kesariya play karo")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `play song archive tune`() {
        val command = CommandParser.parse("ArchiveTune par Kesariya play karo")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `play song with gaana`() {
        val command = CommandParser.parse("Kesariya gaana chalao")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `play song lagao`() {
        val command = CommandParser.parse("Kesariya lagao")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `play song archive me`() {
        val command = CommandParser.parse("ArchiveTune me Kesariya chalao")
        assertEquals(IntentType.PLAY_MUSIC, command.intent)
        assertEquals("Kesariya", command.query)
    }

    @Test
    fun `hindi search`() {
        val command = CommandParser.parse("गूगल पर मौसम खोजो")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("मौसम", command.query)
    }

    @Test
    fun `hindi natural search`() {
        val command = CommandParser.parse("मौसम बताओ")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("मौसम", command.query)
    }

    @Test
    fun `search natural batao`() {
        val command = CommandParser.parse("weather batao")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("weather", command.query)
    }

    @Test
    fun `search natural question`() {
        val command = CommandParser.parse("aaj ka mausam kaisa hai")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("aaj ka mausam", command.query)
    }

    @Test
    fun `search on google`() {
        val command = CommandParser.parse("weather on Google")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("weather", command.query)
    }

    @Test
    fun `hindi call`() {
        val command = CommandParser.parse("राहुल को कॉल करो")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("राहुल", command.contact)
    }

    @Test
    fun `hindi phone call`() {
        val command = CommandParser.parse("राहुल को फ़ोन करो")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("राहुल", command.contact)
    }

    @Test
    fun `hindi whatsapp`() {
        val command = CommandParser.parse("व्हाट्सएप पर राहुल को मैसेज भेजो कि नमस्ते")
        assertEquals(IntentType.WHATSAPP, command.intent)
        assertEquals("राहुल", command.contact)
        assertEquals("नमस्ते", command.message)
    }

    @Test
    fun `leading fillers removed`() {
        val command = CommandParser.parse("कृपया सुनो गूगल पर समाचार खोजो")
        assertEquals(IntentType.SEARCH, command.intent)
        assertEquals("समाचार", command.query)
    }

    @Test
    fun `unknown command`() {
        val command = CommandParser.parse("Kya haal hai")
        assertEquals(IntentType.UNKNOWN, command.intent)
    }

    @Test
    fun `empty command`() {
        val command = CommandParser.parse("   ")
        assertEquals(IntentType.UNKNOWN, command.intent)
    }

    @Test
    fun `qwen json parse search`() {
        val command = QwenEngine.parseOutput(
            """{"intent":"SEARCH","contact":null,"query":"weather","message":null}"""
        )
        assertEquals(IntentType.SEARCH, command?.intent)
        assertEquals("weather", command?.query)
    }

    @Test
    fun `qwen json parse whatsapp`() {
        val command = QwenEngine.parseOutput(
            """{"intent":"WHATSAPP","contact":"Rahul","query":null,"message":"hello"}"""
        )
        assertEquals(IntentType.WHATSAPP, command?.intent)
        assertEquals("Rahul", command?.contact)
        assertEquals("hello", command?.message)
    }

    @Test
    fun `qwen invalid json`() {
        assertNull(QwenEngine.parseOutput("not json"))
    }
}
