@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.voiceassistant.hindi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voiceassistant.hindi.AssistantLanguage
import com.voiceassistant.hindi.AssistantPhase
import com.voiceassistant.hindi.AssistantUiState
import com.voiceassistant.hindi.IntentType
import com.voiceassistant.hindi.QwenPhase
import com.voiceassistant.hindi.QwenSnapshot
import com.voiceassistant.hindi.R
import java.util.Locale

@Composable
fun HomeScreen(
    state: AssistantUiState,
    language: AssistantLanguage,
    qwen: QwenSnapshot,
    audioGranted: Boolean,
    notificationsGranted: Boolean,
    contactsGranted: Boolean,
    phoneGranted: Boolean,
    accessibilityEnabled: Boolean,
    onLanguageChange: (AssistantLanguage) -> Unit,
    onMic: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDownloadQwen: () -> Unit,
    onConfirm: () -> Unit,
    onCancelPending: () -> Unit
) {
    var showAccessibilityHelp by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Offline voice commands. Tap, bolo, kaam ho jayega.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistantLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = { onLanguageChange(option) },
                        label = {
                            Text(
                                if (option == AssistantLanguage.HINDI) {
                                    stringResource(R.string.hindi)
                                } else {
                                    stringResource(R.string.english)
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isBusy) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    .clickable(
                        enabled = !state.isBusy,
                        role = Role.Button,
                        onClick = onMic
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 4.dp
                    )
                } else {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.tap_to_speak),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state.phase) {
                    AssistantPhase.LISTENING -> stringResource(R.string.listening)
                    AssistantPhase.THINKING -> stringResource(R.string.thinking)
                    else -> stringResource(R.string.tap_to_speak)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (state.isBusy) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }

            if (state.transcript.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "“${state.transcript}”",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (state.status.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.phase == AssistantPhase.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionTitle(stringResource(R.string.setup))
            Text(
                text = stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    PermissionRow(
                        title = stringResource(R.string.microphone),
                        granted = audioGranted,
                        actionLabel = stringResource(R.string.grant),
                        onAction = { onRequestPermission(android.Manifest.permission.RECORD_AUDIO) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.notifications),
                        granted = notificationsGranted,
                        actionLabel = stringResource(R.string.grant),
                        onAction = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                onRequestPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.contacts),
                        granted = contactsGranted,
                        actionLabel = stringResource(R.string.grant),
                        onAction = { onRequestPermission(android.Manifest.permission.READ_CONTACTS) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.phone),
                        granted = phoneGranted,
                        actionLabel = stringResource(R.string.grant),
                        onAction = { onRequestPermission(android.Manifest.permission.CALL_PHONE) }
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.accessibility),
                        granted = accessibilityEnabled,
                        actionLabel = stringResource(R.string.enable),
                        onAction = { showAccessibilityHelp = true }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            QwenCard(qwen = qwen, onDownload = onDownloadQwen)

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Privacy: commands phone par process hote hain. Internet sirf model download ke liye use hota hai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    state.pendingAction?.takeIf { !state.isBusy }?.let { pending ->
        val isCall = pending.command.intent == IntentType.CALL
        AlertDialog(
            onDismissRequest = onCancelPending,
            title = {
                Text(
                    stringResource(
                        if (isCall) R.string.confirm_call_title else R.string.confirm_whatsapp_title
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${stringResource(R.string.contact)}: ${pending.contact.displayName}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("${pending.contact.number}")
                    if (!isCall) {
                        Text(
                            text = "${stringResource(R.string.message)}: ${pending.message.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelPending) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAccessibilityHelp) {
        AlertDialog(
            onDismissRequest = { showAccessibilityHelp = false },
            title = { Text(stringResource(R.string.accessibility_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.accessibility_help_body))
                    OutlinedButton(
                        onClick = {
                            showAccessibilityHelp = false
                            onOpenAppInfo()
                        }
                    ) {
                        Text("App info kholo")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityHelp = false
                        onOpenAccessibility()
                    }
                ) {
                    Text("Accessibility kholo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityHelp = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(if (granted) R.string.allowed else R.string.not_allowed),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        TextButton(onClick = onAction, enabled = !granted) {
            Text(if (granted) stringResource(R.string.on) else actionLabel)
        }
    }
}

@Composable
private fun QwenCard(qwen: QwenSnapshot, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.qwen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.qwen_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (qwen.phase) {
                QwenPhase.READY -> Text(
                    text = stringResource(R.string.qwen_ready),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                QwenPhase.WAITING -> {
                    LinearProgressIndicator(
                        progress = { qwen.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = qwen.message ?: stringResource(R.string.qwen_wifi),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                QwenPhase.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { qwen.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.0f / %.0f MB",
                            qwen.downloaded / 1_000_000.0,
                            qwen.total / 1_000_000.0
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.qwen_keep_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                QwenPhase.NEEDS_VERIFY,
                QwenPhase.VERIFYING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.qwen_verify),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                QwenPhase.FAILED -> {
                    if (qwen.message != null) {
                        Text(
                            text = qwen.message.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.qwen_download))
                    }
                }

                QwenPhase.ABSENT -> {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.qwen_download))
                    }
                    Text(
                        text = stringResource(R.string.qwen_wifi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
