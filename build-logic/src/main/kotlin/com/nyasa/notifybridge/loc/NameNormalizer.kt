package com.nyasa.notifybridge.loc

/**
 * Maps raw JSON keys and placeholder names to safe Kotlin identifiers.
 *
 * Rules (from the Notion guide):
 * - snake/kebab/dot → camelCase
 * - digit-leading → underscore-prefixed
 * - Kotlin hard keyword → suffix `String`
 *
 * Collision resolution (deterministic ordinal suffix) lives in [DictionaryKotlinWriter]
 * because it needs scope-wide visibility.
 */
object NameNormalizer {

    fun toCamelCase(raw: String): String {
        if (raw.isEmpty()) return raw
        val parts = raw.split('_', '-', '.', ' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return raw
        val first = parts.first().replaceFirstChar { it.lowercase() }
        val rest = parts.drop(1).joinToString("") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
        return first + rest
    }

    fun safeKotlinIdentifier(raw: String): String {
        val camel = toCamelCase(raw)
        val withDigitGuard = if (camel.isNotEmpty() && camel[0].isDigit()) "_$camel" else camel
        return if (withDigitGuard in KOTLIN_HARD_KEYWORDS) "${withDigitGuard}String" else withDigitGuard
    }

    private val KOTLIN_HARD_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for",
        "fun", "if", "in", "interface", "is", "null", "object", "package",
        "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",
    )
}
