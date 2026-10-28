package com.voiceassistant.hindi

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ActionResult(val ok: Boolean, val message: String)

object ActionExecutor {

    private const val ARCHIVE_TUNE = "moe.rukamori.archivetune"
    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun search(context: Context, query: String): ActionResult {
        val clean = query.trim()
        if (clean.isBlank()) return ActionResult(false, "Search query khali hai.")

        val base = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, clean)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (isInstalled(context, GOOGLE_APP)) {
            val googleIntent = Intent(base).setPackage(GOOGLE_APP)
            if (launch(context, googleIntent)) {
                return ActionResult(true, "Google search khul gaya.")
            }
        }
        if (launch(context, base)) {
            return ActionResult(true, "Search khul gaya.")
        }

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(clean)}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (launch(context, web)) {
            ActionResult(true, "Browser me search khul gaya.")
        } else {
            ActionResult(false, "Koi browser ya search app nahi mili.")
        }
    }

    fun playMusic(context: Context, query: String): ActionResult {
        val clean = query.trim()
        if (clean.isBlank()) return ActionResult(false, "Song ka naam khali hai.")
        if (!isInstalled(context, ARCHIVE_TUNE)) {
            return ActionResult(false, "ArchiveTune install nahi hai.")
        }

        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(ARCHIVE_TUNE)
            .putExtra(SearchManager.QUERY, clean)
            .putExtra("query", clean)
            .putExtra("android.intent.extra.TITLE", clean)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launch(context, intent)) {
            return ActionResult(true, "ArchiveTune me play request bheja.")
        }

        val fallback = context.packageManager.getLaunchIntentForPackage(ARCHIVE_TUNE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (fallback != null && launch(context, fallback)) {
            ActionResult(true, "ArchiveTune khul gaya. Song manually chuno.")
        } else {
            ActionResult(false, "ArchiveTune play request nahi le paya.")
        }
    }

    fun call(context: Context, contact: ContactMatch): ActionResult {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:${contact.number}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return if (launch(context, intent)) {
            if (hasCallPermission) {
                ActionResult(true, "${contact.displayName} ko call lag raha hai.")
            } else {
                ActionResult(true, "Dialer khul gaya. Call button dabao.")
            }
        } else {
            ActionResult(false, "Call app nahi khuli.")
        }
    }

    fun executePending(context: Context, pending: PendingAction): ActionResult {
        if (System.currentTimeMillis() - pending.createdAtMillis > PendingAction.TTL_MS) {
            return ActionResult(false, "Confirmation expire ho gaya. Dobara command bolo.")
        }
        return when (pending.command.intent) {
            IntentType.CALL -> call(context, pending.contact)
            IntentType.WHATSAPP -> whatsapp(context, pending)
            else -> ActionResult(false, "Unsupported action.")
        }
    }

    fun whatsapp(context: Context, pending: PendingAction): ActionResult {
        val message = pending.message?.trim()
        if (message.isNullOrBlank()) {
            return ActionResult(false, "WhatsApp message khali hai.")
        }

        val packageName = when {
            isInstalled(context, WHATSAPP) -> WHATSAPP
            isInstalled(context, WHATSAPP_BUSINESS) -> WHATSAPP_BUSINESS
            else -> return ActionResult(false, "WhatsApp install nahi hai.")
        }

        val uri = Uri.parse(
            "https://wa.me/${pending.contact.number}?text=${Uri.encode(message)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!launch(context, intent)) {
            return ActionResult(false, "WhatsApp chat nahi khuli.")
        }

        if (!WhatsAppAccessibilityService.isEnabled(context)) {
            toast(context, "WhatsApp khul gaya. Send button manually dabao.")
            return ActionResult(true, "WhatsApp chat khuli. Send manually dabao.")
        }

        scope.launch {
            delay(1800)
            val clicked = WhatsAppAccessibilityService.clickSend(pending.contact.displayName)
            toast(
                context,
                if (clicked) "WhatsApp Send button dabaya."
                else "Send button nahi mila. Manually bhejo."
            )
        }
        return ActionResult(true, "WhatsApp khul gaya. Send button click kar raha hoon.")
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun toast(context: Context, message: String) {
        scope.launch {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
