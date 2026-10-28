package com.voiceassistant.hindi

enum class IntentType {
    SEARCH,
    CALL,
    WHATSAPP,
    PLAY_MUSIC,
    UNKNOWN
}

data class ParsedCommand(
    val intent: IntentType,
    val contact: String? = null,
    val query: String? = null,
    val message: String? = null
)

data class ContactMatch(
    val displayName: String,
    val number: String,
    val fromNumber: Boolean = false
)

data class PendingAction(
    val command: ParsedCommand,
    val contact: ContactMatch,
    val message: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}

enum class AssistantPhase {
    IDLE,
    LISTENING,
    THINKING,
    NEED_CONFIRM,
    DONE,
    ERROR
}

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val transcript: String = "",
    val status: String = "",
    val pendingAction: PendingAction? = null,
    val isBusy: Boolean = false
)

enum class AssistantLanguage(
    val code: String,
    val label: String,
    val assetName: String,
    val modelFolder: String
) {
    HINDI(
        code = "hi",
        label = "Hindi",
        assetName = "vosk-model-small-hi-0.22.zip",
        modelFolder = "vosk-model-small-hi-0.22"
    ),
    ENGLISH(
        code = "en",
        label = "English",
        assetName = "vosk-model-small-en-in-0.4.zip",
        modelFolder = "vosk-model-small-en-in-0.4"
    );

    companion object {
        fun fromCode(code: String?): AssistantLanguage =
            entries.firstOrNull { it.code == code } ?: HINDI
    }
}
