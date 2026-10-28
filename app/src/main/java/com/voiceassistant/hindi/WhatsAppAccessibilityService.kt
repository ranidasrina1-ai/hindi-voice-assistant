package com.voiceassistant.hindi

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: WhatsAppAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull().orEmpty()
            val component = ComponentName(context, WhatsAppAccessibilityService::class.java)
            val full = component.flattenToString()
            val short = component.flattenToShortString()
            return enabled.split(':').any {
                it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true)
            }
        }

        suspend fun clickSend(expectedName: String? = null, timeoutMs: Long = 6000L): Boolean =
            withContext(Dispatchers.Main) {
                val deadline = SystemClock.uptimeMillis() + timeoutMs
                while (SystemClock.uptimeMillis() < deadline) {
                    val service = instance
                    if (service != null && service.findAndClickSend(expectedName)) {
                        return@withContext true
                    }
                    delay(400L)
                }
                false
            }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    private fun findAndClickSend(expectedName: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString().orEmpty()
        if (!packageName.startsWith("com.whatsapp")) return false
        if (!expectedName.isNullOrBlank() && !conversationMatches(root, packageName, expectedName)) {
            return false
        }

        val direct = runCatching {
            root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        }.getOrNull().orEmpty()
        for (node in direct) {
            if (clickNodeOrParent(node)) return true
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited += 1
            if (looksLikeSend(node) && clickNodeOrParent(node)) return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return false
    }

    private fun conversationMatches(
        root: AccessibilityNodeInfo,
        packageName: String,
        expectedName: String
    ): Boolean {
        val expected = normalize(expectedName)
        if (expected.isBlank()) return true

        val ids = listOf(
            "$packageName:id/conversation_contact_name",
            "$packageName:id/conversation_title"
        )
        for (id in ids) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
                .getOrNull()
                .orEmpty()
            for (node in nodes) {
                if (matchesName(node.text?.toString(), expected) ||
                    matchesName(node.contentDescription?.toString(), expected)
                ) {
                    return true
                }
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 300) {
            val node = queue.removeFirst()
            visited += 1
            val id = node.viewIdResourceName.orEmpty()
            if (id.contains("conversation", ignoreCase = true) &&
                (matchesName(node.text?.toString(), expected) ||
                    matchesName(node.contentDescription?.toString(), expected))
            ) {
                return true
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return false
    }

    private fun matchesName(value: String?, expected: String): Boolean {
        val normalized = normalize(value.orEmpty())
        if (normalized.isBlank()) return false
        return normalized == expected ||
            (expected.length >= 3 && normalized.contains(expected)) ||
            (normalized.length >= 3 && expected.contains(normalized))
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    private fun looksLikeSend(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName.orEmpty()
        if (id.endsWith(":id/send") || id.endsWith("/send")) return true
        val description = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        return description == "send" ||
            description == "भेजें" ||
            text == "send" ||
            text == "भेजें"
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            val candidate = current ?: return false
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = candidate.parent
        }
        return false
    }
}
