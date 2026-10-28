package com.voiceassistant.hindi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.voiceassistant.hindi.ui.HomeScreen
import com.voiceassistant.hindi.ui.HindiVoiceAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_LISTENING = "com.voiceassistant.hindi.START_LISTENING"
    }

    private lateinit var settings: SettingsStore
    private var autoStartRequested = false
    private var pendingStartAfterPermission = false
    private var permissionTick by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val audioGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        permissionTick += 1
        if (pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            if (audioGranted) {
                startAssistant()
            } else {
                Toast.makeText(this, "Microphone permission ke bina command nahi chalega.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        enableEdgeToEdge()

        setContent {
            HindiVoiceAssistantTheme {
                val state by AssistantState.state.collectAsStateWithLifecycle()
                var language by remember { mutableStateOf(settings.language) }
                var qwen by remember { mutableStateOf(QwenModelManager.safeSnapshot(this@MainActivity)) }
                var verifying by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val tick = permissionTick

                val audioGranted = remember(tick) {
                    hasPermission(Manifest.permission.RECORD_AUDIO)
                }
                val notificationsGranted = remember(tick) {
                    Build.VERSION.SDK_INT < 33 ||
                        hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                }
                val contactsGranted = remember(tick) {
                    hasPermission(Manifest.permission.READ_CONTACTS)
                }
                val phoneGranted = remember(tick) {
                    hasPermission(Manifest.permission.CALL_PHONE)
                }
                val accessibilityEnabled = remember(tick) {
                    WhatsAppAccessibilityService.isEnabled(this@MainActivity)
                }

                LaunchedEffect(Unit) {
                    this@MainActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (isActive) {
                            val snapshot = withContext(Dispatchers.IO) {
                                QwenModelManager.safeSnapshot(this@MainActivity)
                            }
                            if (snapshot.phase == QwenPhase.NEEDS_VERIFY && !verifying) {
                                verifying = true
                                qwen = snapshot.copy(phase = QwenPhase.VERIFYING)
                                try {
                                    val verified = QwenModelManager.verify(this@MainActivity)
                                    if (!verified) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Qwen model verify fail ho gaya. Dobara download karo.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    qwen = withContext(Dispatchers.IO) {
                                        QwenModelManager.safeSnapshot(this@MainActivity)
                                    }
                                } finally {
                                    verifying = false
                                }
                            } else {
                                qwen = snapshot
                            }
                            delay(1000L)
                        }
                    }
                }

                HomeScreen(
                    state = state,
                    language = language,
                    qwen = qwen,
                    audioGranted = audioGranted,
                    notificationsGranted = notificationsGranted,
                    contactsGranted = contactsGranted,
                    phoneGranted = phoneGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    onLanguageChange = { selected ->
                        language = selected
                        settings.language = selected
                    },
                    onMic = { beginListeningFlow() },
                    onCancel = { cancelCommand() },
                    onRequestPermission = { permission ->
                        permissionLauncher.launch(arrayOf(permission))
                    },
                    onOpenAccessibility = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                    onOpenAppInfo = {
                        runCatching {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    },
                    onDownloadQwen = {
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                QwenModelManager.startDownload(this@MainActivity)
                            }
                            if (error != null) {
                                Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                            }
                            qwen = withContext(Dispatchers.IO) {
                                QwenModelManager.safeSnapshot(this@MainActivity)
                            }
                        }
                    },
                    onConfirm = { confirmPending() },
                    onCancelPending = { cancelPending() }
                )
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        permissionTick += 1
        if (autoStartRequested) {
            autoStartRequested = false
            beginListeningFlow()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_START_LISTENING) {
            intent.action = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                beginListeningFlow()
            } else {
                autoStartRequested = true
            }
        }
    }

    private fun beginListeningFlow() {
        if (AssistantState.state.value.isBusy) return

        val audioGranted = hasPermission(Manifest.permission.RECORD_AUDIO)

        if (!audioGranted) {
            pendingStartAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        startAssistant()
    }

    private fun startAssistant() {
        if (AssistantState.state.value.isBusy) return
        Notifications.cancelConfirmation(this)
        AssistantState.beginListening()
        val intent = Intent(this, AssistantService::class.java)
            .setAction(AssistantService.ACTION_START)
            .putExtra(AssistantService.EXTRA_LANGUAGE, settings.language.code)

        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (error: Exception) {
            AssistantState.error("Service start nahi hui: ${error.message}")
        }
    }

    private fun cancelCommand() {
        Notifications.cancelConfirmation(this)
        try {
            startService(
                Intent(this, AssistantService::class.java)
                    .setAction(AssistantService.ACTION_STOP)
            )
        } catch (_: Exception) {
        }
        AssistantState.error(getString(R.string.status_cancelled))
    }

    private fun confirmPending() {
        val pending = AssistantState.state.value.pendingAction ?: return
        Notifications.cancelConfirmation(this)
        val result = ActionExecutor.executePending(this, pending)
        if (result.ok) {
            AssistantState.done(result.message)
        } else {
            AssistantState.error(result.message)
        }
    }

    private fun cancelPending() {
        Notifications.cancelConfirmation(this)
        AssistantState.done(getString(R.string.status_cancelled))
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
