package com.nyasa.notifybridge.data.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
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
        val normalized = tag?.let(::normalize)
        saveSelectedTag(normalized)
        applyPlatformLocale(normalized)

        _selectedLanguageTag.value = normalized
        _language.value = loadLanguage(normalized)
    }

    private fun loadSelectedTag(): String? {
        prefs.getString(KEY_SELECTED_LANGUAGE, null)?.let { return normalize(it) }

        val applied = AppCompatDelegate.getApplicationLocales()
        return if (!applied.isEmpty) {
            applied.get(0)?.toLanguageTag()?.substringBefore('-')?.lowercase()?.let(::normalize)
        } else {
            null
        }
    }

    private fun loadLanguage(selectedTag: String?): Language {
        val tag = selectedTag ?: Locale.getDefault().toLanguageTag()
        return AssetJsonLanguage(normalize(tag), context)
    }

    private fun saveSelectedTag(tag: String?) {
        prefs.edit().apply {
            if (tag == null) {
                remove(KEY_SELECTED_LANGUAGE)
            } else {
                putString(KEY_SELECTED_LANGUAGE, tag)
            }
        }.apply()
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

    private fun normalize(rawTag: String): String {
        val lang = rawTag.substringBefore('-').lowercase()
        return if (lang in BUNDLED) lang else FALLBACK
    }

    private companion object {
        const val PREFS_NAME = "localization"
        const val KEY_SELECTED_LANGUAGE = "selected_language"
        const val FALLBACK = "en"
        val BUNDLED = setOf("en", "fr", "es", "pt")
    }
}
