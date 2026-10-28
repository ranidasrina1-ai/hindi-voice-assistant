package com.voiceassistant.hindi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AssistantState {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    fun beginListening() {
        _state.value = AssistantUiState(
            phase = AssistantPhase.LISTENING,
            status = "",
            pendingAction = _state.value.pendingAction,
            isBusy = true
        )
    }

    fun transcript(text: String) {
        _state.update { it.copy(transcript = text) }
    }

    fun thinking() {
        _state.update {
            it.copy(
                phase = AssistantPhase.THINKING,
                isBusy = true,
                status = ""
            )
        }
    }

    fun status(text: String) {
        _state.update { it.copy(status = text) }
    }

    fun needConfirm(action: PendingAction) {
        _state.value = AssistantUiState(
            phase = AssistantPhase.NEED_CONFIRM,
            transcript = _state.value.transcript,
            status = "",
            pendingAction = action,
            isBusy = false
        )
    }

    fun done(message: String) {
        _state.update {
            it.copy(
                phase = AssistantPhase.DONE,
                status = message,
                pendingAction = null,
                isBusy = false
            )
        }
    }

    fun error(message: String) {
        _state.update {
            it.copy(
                phase = AssistantPhase.ERROR,
                status = message,
                pendingAction = null,
                isBusy = false
            )
        }
    }

    fun clearPending() {
        _state.update { it.copy(pendingAction = null) }
    }

    fun reset() {
        _state.value = AssistantUiState()
    }

    private inline fun MutableStateFlow<AssistantUiState>.update(block: (AssistantUiState) -> AssistantUiState) {
        while (true) {
            val current = value
            val next = block(current)
            if (compareAndSet(current, next)) return
        }
    }
}
