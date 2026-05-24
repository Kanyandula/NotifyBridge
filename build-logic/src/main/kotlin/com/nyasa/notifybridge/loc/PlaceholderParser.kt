package com.nyasa.notifybridge.loc

/**
 * ICU subset parser: `{name}` interpolation + `{count, plural, one {…} other {…}}` plurals.
 *
 * Balanced-brace scanner — NOT regex. Nested `{name}` inside plural branches is supported;
 * `#` inside a plural branch becomes [Token.PluralCount].
 *
 * Out of scope: `selectordinal`, `select`, embedded `number`/`date` formats, `choice`.
 */
object PlaceholderParser {

    fun parse(text: String): List<Token> = parseTokens(text, 0, text.length).first

    /**
     * Returns parsed tokens for the substring [start, end) of [text] and the index
     * one past the last consumed char. Used recursively to parse plural-branch bodies.
     */
    private fun parseTokens(text: String, start: Int, end: Int): Pair<List<Token>, Int> {
        val out = mutableListOf<Token>()
        val literal = StringBuilder()
        var i = start
        while (i < end) {
            val c = text[i]
            when {
                c == '{' -> {
                    if (literal.isNotEmpty()) {
                        out += Token.Literal(literal.toString())
                        literal.clear()
                    }
                    val close = findMatchingBrace(text, i, end)
                    val inside = text.substring(i + 1, close).trim()
                    out += parsePlaceholder(inside)
                    i = close + 1
                }
                c == '#' -> {
                    if (literal.isNotEmpty()) {
                        out += Token.Literal(literal.toString())
                        literal.clear()
                    }
                    out += Token.PluralCount
                    i++
                }
                else -> {
                    literal.append(c)
                    i++
                }
            }
        }
        if (literal.isNotEmpty()) out += Token.Literal(literal.toString())
        return out to i
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
        throw PlaceholderParseException(
            "Unmatched '{' at offset $openIdx in: ${text.substring(0, end)}",
        )
    }

    private fun parsePlaceholder(inside: String): Token {
        // Simple `{name}` — no comma.
        val firstComma = topLevelComma(inside)
        if (firstComma < 0) {
            require(inside.isNotBlank() && inside.all { isIdentifierChar(it) }) {
                "Invalid placeholder name: '$inside'"
            }
            return Token.Simple(inside)
        }

        // Plural form: `{name, plural, one {…} other {…}}`
        val argName = inside.substring(0, firstComma).trim()
        val rest = inside.substring(firstComma + 1)
        val secondComma = topLevelComma(rest)
        require(secondComma > 0) {
            "Plural placeholder must be `{name, plural, one {…} other {…}}`, got `{$inside}`"
        }
        val kind = rest.substring(0, secondComma).trim()
        require(kind == "plural") {
            "Only `plural` is supported in this subset (got `$kind`)"
        }
        val branchesSource = rest.substring(secondComma + 1)
        val branches = parseBranches(branchesSource, 0, branchesSource.length)
        require("one" in branches && "other" in branches) {
            "Plural for `$argName` must define both `one` and `other` branches"
        }
        return Token.Plural(argName, branches)
    }

    /**
     * Parses ` keyword { body } keyword { body } ...` sequences within [start, end) of [text].
     * Used for plural-branch lists.
     */
    private fun parseBranches(text: String, start: Int, end: Int): Map<String, List<Token>> {
        val branches = LinkedHashMap<String, List<Token>>()
        var i = start
        while (i < end) {
            // Skip whitespace.
            while (i < end && text[i].isWhitespace()) i++
            if (i >= end) break

            // Read keyword.
            val kwStart = i
            while (i < end && isIdentifierChar(text[i])) i++
            require(i > kwStart) { "Expected plural keyword at offset $i in `${text.substring(start, end)}`" }
            val keyword = text.substring(kwStart, i)

            // Skip whitespace, then expect '{'.
            while (i < end && text[i].isWhitespace()) i++
            require(i < end && text[i] == '{') {
                "Expected '{' after plural keyword `$keyword` at offset $i"
            }
            val bodyClose = findMatchingBrace(text, i, end)
            val (bodyTokens, _) = parseTokens(text, i + 1, bodyClose)
            branches[keyword] = bodyTokens
            i = bodyClose + 1
        }
        return branches
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
}

class PlaceholderParseException(message: String) : RuntimeException(message)
