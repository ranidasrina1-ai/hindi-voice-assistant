package com.voiceassistant.hindi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

object ContactResolver {

    private data class Entry(
        val name: String,
        val number: String,
        val normalizedName: String,
        val type: Int
    )

    @Volatile
    private var cache: List<Entry>? = null

    @Volatile
    private var cachedAt: Long = 0L

    private const val CACHE_MS = 5 * 60 * 1000L
    private val whitespace = Regex("\\s+")
    private val unsafe = Regex("[^\\p{L}\\p{N}\\s]")
    private val purePhone = Regex("^[+0-9\\s().-]{7,}$")

    fun resolve(context: Context, query: String): ContactMatch? {
        val raw = query.trim()
        if (raw.isBlank()) return null

        val digits = raw.filter(Char::isDigit)
        if (purePhone.matches(raw) || digits.length >= 10) {
            val number = normalizeNumber(raw) ?: return null
            return ContactMatch(displayName = raw, number = number, fromNumber = true)
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val normalizedQuery = normalizeName(raw)
            .replace(Regex("\\s+(?:ji|जी)$"), "")
            .trim()
        if (normalizedQuery.isBlank()) return null

        val entries = loadContacts(context)
        var best: Entry? = null
        var bestScore = 0

        for (entry in entries) {
            val score = score(normalizedQuery, entry.normalizedName)
            if (score > bestScore ||
                (score == bestScore && score > 0 && prefer(entry, best))
            ) {
                best = entry
                bestScore = score
            }
        }

        if (bestScore < 50 || best == null) return null
        return ContactMatch(displayName = best.name, number = best.number)
    }

    fun invalidate() {
        cache = null
        cachedAt = 0L
    }

    private fun prefer(candidate: Entry, current: Entry?): Boolean {
        if (current == null) return true
        val candidateMobile = candidate.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        val currentMobile = current.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        if (candidateMobile != currentMobile) return candidateMobile
        return candidate.name.length < current.name.length
    }

    private fun score(query: String, name: String): Int {
        if (name == query) return 100
        if (name.startsWith("$query ")) return 90
        if (name.contains(query)) return 75
        if (query.contains(name)) return 65

        val queryTokens = query.split(' ').filter { it.isNotBlank() }
        val nameTokens = name.split(' ').filter { it.isNotBlank() }.toSet()
        if (queryTokens.isEmpty()) return 0
        val overlap = queryTokens.count { it in nameTokens }
        val ratio = overlap.toDouble() / queryTokens.size
        return when {
            overlap == queryTokens.size -> 60
            ratio >= 0.6 -> 55
            else -> 0
        }
    }

    private fun loadContacts(context: Context): List<Entry> {
        val now = System.currentTimeMillis()
        val cached = cache
        if (cached != null && now - cachedAt < CACHE_MS) return cached

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val result = LinkedHashMap<String, Entry>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                if (name.isBlank()) continue
                val number = normalizeNumber(cursor.getString(numberIndex).orEmpty()) ?: continue
                val normalizedName = normalizeName(name)
                if (normalizedName.isBlank()) continue
                val type = cursor.getInt(typeIndex)
                val key = "$normalizedName|$number"
                result.putIfAbsent(key, Entry(name, number, normalizedName, type))
            }
        }

        val list = result.values.toList()
        cache = list
        cachedAt = now
        return list
    }

    private fun normalizeName(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(unsafe, " ")
            .replace(whitespace, " ")
            .trim()
    }

    private fun normalizeNumber(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 7 || digits.length > 15) return null

        val normalized = when {
            trimmed.startsWith("+") -> digits
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> digits
            else -> digits
        }
        return normalized.takeIf { it.length in 10..15 }
    }
}
