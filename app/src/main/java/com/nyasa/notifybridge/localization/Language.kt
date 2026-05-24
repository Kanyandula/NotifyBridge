package com.nyasa.notifybridge.localization

/**
 * Resolves typed-localization keys to display strings for a single locale.
 *
 * Implementations are immutable and safe to share. A new [Language] is created
 * whenever the active locale changes — see [LocalizationRepository][com.nyasa.notifybridge.domain.repo.LocalizationRepository].
 */
interface Language {
    /** Locale tag in IETF BCP-47 form (e.g. `"en"`, `"fr"`, `"pt-BR"`). */
    val tag: String

    /**
     * Resolves `dictionary.key` to a display string, substituting `args` into
     * any placeholders and selecting plural arms where applicable.
     *
     * If the key is missing, plural arg is malformed, or other runtime errors
     * occur, the implementation MUST return a defensive fallback (see
     * [Fallbacks]) rather than throw.
     */
    fun resolve(dictionary: String, key: String, args: Map<String, Any>): String
}
