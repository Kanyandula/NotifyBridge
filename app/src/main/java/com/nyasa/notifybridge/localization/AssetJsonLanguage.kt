package com.nyasa.notifybridge.localization

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [Language] backed by `assets/localization/<tag>/strings.json`.
 *
 * The active locale's file is loaded once at construction (eager, on the main thread —
 * the file is small, ~30 KB JSON). The English file is also loaded as a fallback
 * source for missing keys.
 *
 * Supports the ICU subset documented in the build-time generator:
 * - `{name}` interpolation
 * - `{count, plural, one {…} other {…}}` plurals (with `#` for the count itself)
 */
class AssetJsonLanguage(
    override val tag: String,
    context: Context,
) : Language {

    private val active: Map<String, Map<String, String>> = loadOrEmpty(context, tag)
    private val english: Map<String, Map<String, String>> =
        if (tag == EN_TAG) active else loadOrEmpty(context, EN_TAG)

    override fun resolve(dictionary: String, key: String, args: Map<String, Any>): String {
        val template = active[dictionary]?.get(key)
            ?: english[dictionary]?.get(key)
            ?: return Fallbacks.keyNotFound(dictionary, key)
        return render(template, args)
    }

    // ── ICU-subset renderer (runtime mirror of build-time PlaceholderParser) ──────────

    private fun render(template: String, args: Map<String, Any>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c != '{') {
                sb.append(c)
                i++
                continue
            }
            val close = findMatchingBrace(template, i, template.length)
            val inside = template.substring(i + 1, close).trim()
            sb.append(renderPlaceholder(inside, args))
            i = close + 1
        }
        return sb.toString()
    }

    @Suppress("ReturnCount")
    private fun renderPlaceholder(inside: String, args: Map<String, Any>): String {
        val firstComma = topLevelComma(inside)
        if (firstComma < 0) {
            val value = args[inside] ?: return Fallbacks.placeholderMissing(inside)
            return value.toString()
        }
        val argName = inside.substring(0, firstComma).trim()
        val rest = inside.substring(firstComma + 1)
        val secondComma = topLevelComma(rest)
        if (secondComma < 0) return "{$inside}"
        val kind = rest.substring(0, secondComma).trim()
        if (kind != "plural") return "{$inside}"

        val branchesSource = rest.substring(secondComma + 1)
        val count = (args[argName] as? Number)?.toInt()
            ?: return Fallbacks.placeholderMissing(argName)
        val branchKey = if (count == 1) "one" else "other"
        val arm = extractBranch(branchesSource, branchKey)
            ?: extractBranch(branchesSource, "other")
            ?: return "{$inside}".also { Fallbacks.warnOnce("Malformed plural in `{$inside}`") }
        return render(arm.replace("#", count.toString()), args)
    }

    @Suppress("ReturnCount")
    private fun extractBranch(source: String, keyword: String): String? {
        var i = 0
        val end = source.length
        while (i < end) {
            while (i < end && source[i].isWhitespace()) i++
            val kwStart = i
            while (i < end && isIdentifierChar(source[i])) i++
            val kw = source.substring(kwStart, i)
            while (i < end && source[i].isWhitespace()) i++
            if (i >= end || source[i] != '{') return null
            val close = findMatchingBrace(source, i, end)
            if (kw == keyword) return source.substring(i + 1, close)
            i = close + 1
        }
        return null
    }

    private fun findMatchingBrace(text: String, openIdx: Int, end: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < end) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return end - 1
    }

    private fun topLevelComma(s: String): Int {
        var depth = 0
        for ((i, c) in s.withIndex()) {
            when (c) {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun isIdentifierChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '-'

    companion object {
        private const val EN_TAG = "en"
        private val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        // Parsed dictionaries are immutable + small (~30 KB each), shared across all
        // AssetJsonLanguage instances. Caching here means switching the active locale
        // (or recreating the singleton) does not re-parse JSON.
        private val parsedCache: MutableMap<String, Map<String, Map<String, String>>> =
            ConcurrentHashMap()

        private fun loadOrEmpty(context: Context, tag: String): Map<String, Map<String, String>> =
            parsedCache.getOrPut(tag) { loadFromAssets(context, tag) }

        private fun loadFromAssets(context: Context, tag: String): Map<String, Map<String, String>> {
            val path = "localization/$tag/strings.json"
            return try {
                val text = context.assets.open(path).bufferedReader().use { it.readText() }
                parse(text)
            } catch (_: IOException) {
                Fallbacks.localeNotFound(tag)
                emptyMap()
            } catch (e: SerializationException) {
                Fallbacks.warnOnce("Malformed localization JSON for `$tag`: ${e.message}")
                emptyMap()
            }
        }

        private fun parse(text: String): Map<String, Map<String, String>> {
            val root: JsonObject = json.parseToJsonElement(text).jsonObject
            return root.entries.associate { (scope, value) ->
                scope to (value as JsonObject).entries.associate { (k, v) ->
                    k to (v as JsonPrimitive).jsonPrimitive.content
                }
            }
        }
    }
}
