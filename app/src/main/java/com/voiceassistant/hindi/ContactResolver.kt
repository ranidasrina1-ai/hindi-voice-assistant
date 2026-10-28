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
        if (query.length >= 4 && name.length >= 4 &&
            Math.abs(query.length - name.length) <= 1 &&
            editDistance(query, name) <= 1
        ) {
            return 62
        }

        val queryTokens = query.split(' ').filter { it.isNotBlank() }
        val nameTokens = name.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return 0

        val exactOverlap = queryTokens.count { it in nameTokens }
        val fuzzyOverlap = queryTokens.count { queryToken ->
            nameTokens.any { nameToken -> tokensSimilar(queryToken, nameToken) }
        }
        val nameCovered = nameTokens.isNotEmpty() && nameTokens.all { nameToken ->
            queryTokens.any { queryToken -> tokensSimilar(queryToken, nameToken) }
        }
        val ratio = exactOverlap.toDouble() / queryTokens.size
        return when {
            exactOverlap == queryTokens.size -> 60
            fuzzyOverlap == queryTokens.size -> 58
            nameCovered -> 56
            ratio >= 0.6 -> 55
            fuzzyOverlap >= 2 && fuzzyOverlap.toDouble() / queryTokens.size >= 0.6 -> 52
            else -> 0
        }
    }

    private fun tokensSimilar(left: String, right: String): Boolean {
        if (left == right) return true
        if (left.length < 4 || right.length < 4) return false
        if (Math.abs(left.length - right.length) > 1) return false
        return editDistance(left, right) <= 1
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    substitution
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
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

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "r",
        'ढ़' to "rh", 'फ़' to "f", 'य़' to "y"
    )
    private val nuktaConsonants = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z",
        'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
    )
    private val vowelSigns = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )

    private fun normalizeName(value: String): String {
        return romanize(Normalizer.normalize(value, Normalizer.Form.NFKC))
            .lowercase(Locale.ROOT)
            .replace(unsafe, " ")
            .replace(whitespace, " ")
            .trim()
    }

    private fun romanize(value: String): String {
        val output = StringBuilder(value.length + 8)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            val independent = independentVowels[character]
            if (independent != null) {
                output.append(independent)
                index++
                continue
            }

            val consonant = consonants[character]
            if (consonant != null) {
                output.append(consonant)
                index++
                if (index < value.length && value[index] == '़') {
                    val nukta = nuktaConsonants[character]
                    if (nukta != null) {
                        output.setLength(output.length - consonant.length)
                        output.append(nukta)
                    }
                    index++
                }
                if (index >= value.length) continue
                if (value[index] == '्') {
                    index++
                    continue
                }
                val sign = vowelSigns[value[index]]
                if (sign != null) {
                    output.append(sign)
                    index++
                    continue
                }
                if (value[index].isLetter()) output.append('a')
                continue
            }

            val sign = vowelSigns[character]
            if (sign != null) {
                output.append(sign)
                index++
                continue
            }

            output.append(character)
            index++
        }
        return output.toString()
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
