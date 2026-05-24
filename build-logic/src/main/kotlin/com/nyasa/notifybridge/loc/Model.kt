package com.nyasa.notifybridge.loc

/**
 * Parsed dictionary model — one master file's worth of scopes + entries.
 *
 * Per-locale parsing produces one `DictionaryModel`; the validator compares them
 * for parity, and the writer/reporter operate on the master (English).
 */
data class DictionaryModel(
    val scopes: Map<String, DictionaryScope>,
)

data class DictionaryScope(
    val name: String,
    val entries: Map<String, Entry>,
)

data class Entry(
    val rawKey: String,
    val rawValue: String,
    val tokens: List<Token>,
) {
    /** All placeholders referenced — top-level + inside plural branches. Order preserved. */
    val placeholders: List<Placeholder> by lazy { collectPlaceholders(tokens) }
}

sealed class Token {
    data class Literal(val text: String) : Token()
    data class Simple(val name: String) : Token()
    data class Plural(
        val argName: String,
        val branches: Map<String, List<Token>>,
    ) : Token()
    object PluralCount : Token()
}

data class Placeholder(val name: String, val kind: Kind) {
    enum class Kind { SIMPLE, PLURAL }
}

private fun collectPlaceholders(tokens: List<Token>): List<Placeholder> {
    val out = LinkedHashMap<String, Placeholder>()
    fun walk(list: List<Token>) {
        for (t in list) when (t) {
            is Token.Literal, Token.PluralCount -> Unit
            is Token.Simple -> out.putIfAbsent(t.name, Placeholder(t.name, Placeholder.Kind.SIMPLE))
            is Token.Plural -> {
                out.putIfAbsent(t.argName, Placeholder(t.argName, Placeholder.Kind.PLURAL))
                t.branches.values.forEach(::walk)
            }
        }
    }
    walk(tokens)
    return out.values.toList()
}
