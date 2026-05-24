package com.nyasa.notifybridge.data.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import com.nyasa.notifybridge.localization.AssetJsonLanguage
import com.nyasa.notifybridge.localization.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the active locale from [AppCompatDelegate] and exposes a corresponding
 * [Language] via [languageFlow].
 *
 * The selected tag is mirrored locally because AppCompat/framework locale APIs
 * do not synchronously read back the new value on every device after a picker
 * click, but the Compose UI needs to update immediately.
 */
@Singleton
class LocalizationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalizationRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _selectedLanguageTag = MutableStateFlow(loadSelectedTag())
    private val _language = MutableStateFlow(loadLanguage(_selectedLanguageTag.value))
    override val languageFlow: StateFlow<Language> = _language.asStateFlow()

    override val selectedLanguageTag: StateFlow<String?> = _selectedLanguageTag.asStateFlow()

    override fun refresh() {
        _selectedLanguageTag.value = loadSelectedTag()
        _language.value = loadLanguage(_selectedLanguageTag.value)
    }

    override fun setApplicationLanguage(tag: String?) {
        val bundled = tag?.let(::bundledOrNull)
        saveSelectedTag(bundled)
        applyPlatformLocale(bundled)

        _selectedLanguageTag.value = bundled
        _language.value = loadLanguage(bundled)
    }

    private fun loadSelectedTag(): String? {
        val savedTag = prefs.getString(KEY_SELECTED_LANGUAGE, null)?.let(::bundledOrNull)
        if (savedTag != null) return savedTag

        val applied = AppCompatDelegate.getApplicationLocales()
        // If the system-applied locale is non-bundled, treat it as "follow system"
        // (null) rather than silently collapsing to EN — the picker uses this to
        // decide which row to mark selected.
        val appliedTag = if (applied.isEmpty) null else applied.get(0)?.toLanguageTag()
        return appliedTag?.let(::bundledOrNull)
    }

    private fun loadLanguage(selectedTag: String?): Language {
        val tag = selectedTag ?: Locale.getDefault().toLanguageTag()
        return AssetJsonLanguage(bundledOrFallback(tag), context)
    }

    private fun saveSelectedTag(tag: String?) {
        prefs.edit {
            if (tag == null) {
                remove(KEY_SELECTED_LANGUAGE)
            } else {
                putString(KEY_SELECTED_LANGUAGE, tag)
            }
        }
    }

    private fun applyPlatformLocale(tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = if (tag == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tag)
            }
        }

        val appCompatLocales = if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(appCompatLocales)
    }

    /** Returns the bundled primary subtag for [rawTag], or null if we don't ship it. */
    private fun bundledOrNull(rawTag: String): String? {
        val lang = rawTag.substringBefore('-').lowercase()
        return if (lang in BUNDLED) lang else null
    }

    /**
     * Like [bundledOrNull] but returns [FALLBACK] for non-bundled tags.
     * Use when picking which JSON to load.
     */
    private fun bundledOrFallback(rawTag: String): String = bundledOrNull(rawTag) ?: FALLBACK

    private companion object {
        const val PREFS_NAME = "localization"
        const val KEY_SELECTED_LANGUAGE = "selected_language"
        const val FALLBACK = "en"
        val BUNDLED = setOf("en", "fr", "es", "pt")
    }
}
