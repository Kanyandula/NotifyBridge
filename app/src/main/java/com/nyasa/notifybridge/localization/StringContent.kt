package com.nyasa.notifybridge.localization

/**
 * A locale-agnostic, resolvable text value.
 *
 * Lets ViewModels and non-Compose code carry strings that haven't been
 * localized yet. Resolution happens at the UI boundary via [Language.resolve].
 */
interface StringContent {
    fun resolve(language: Language): String
}

/**
 * A bundled localized string identified by `dictionary` + `key`, optionally
 * parameterized by `args` and/or nested `localizableArgs`.
 *
 * Nested [StringContent] arguments are resolved in the same [Language] pass so
 * the entire chain ends up in one language.
 */
data class LocalizedString(
    val key: String,
    val dictionary: String,
    val args: Map<String, Any> = emptyMap(),
    val localizableArgs: Map<String, StringContent> = emptyMap(),
) : StringContent {
    override fun resolve(language: Language): String {
        val resolvedNested = localizableArgs.mapValues { it.value.resolve(language) }
        return language.resolve(
            dictionary = dictionary,
            key = key,
            args = args + resolvedNested,
        )
    }
}

/** A literal string that is not localized — used for user-entered or server-supplied text. */
data class LiteralString(val value: String) : StringContent {
    override fun resolve(language: Language): String = value
}
