package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.localization.Language
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the active [Language] for the app process.
 *
 * Source of truth for the active locale is `AppCompatDelegate.getApplicationLocales()`.
 * Implementations should call [refresh] when the activity is recreated (typically
 * after `setApplicationLocales` triggers configuration change) so the flow re-emits.
 */
interface LocalizationRepository {
    val languageFlow: StateFlow<Language>
    val selectedLanguageTag: StateFlow<String?>
    fun refresh()
    fun setApplicationLanguage(tag: String?)
}
