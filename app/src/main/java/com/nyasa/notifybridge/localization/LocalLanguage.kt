package com.nyasa.notifybridge.localization

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal that exposes the active [Language] to the entire UI tree.
 *
 * Provided by the [Localized] wrapper near the root of `setContent`. When no
 * Localized wrapper is present (e.g., inside a `@Preview` or a unit test), the
 * default falls through to [KeyEchoLanguage], which simply renders
 * `dictionary.key` — useful for visual diffing and structure checks without
 * needing to plumb a real Language through every preview.
 */
val LocalLanguage = staticCompositionLocalOf<Language> { KeyEchoLanguage }

/** Default Language used outside of [Localized] — emits `dictionary.key` verbatim. */
internal object KeyEchoLanguage : Language {
    override val tag: String = "preview"
    override fun resolve(dictionary: String, key: String, args: Map<String, Any>): String =
        "$dictionary.$key"
}
