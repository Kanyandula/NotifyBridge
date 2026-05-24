package com.nyasa.notifybridge.data.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LocalizationRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearBefore() {
        context.deleteSharedPreferences("localization")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun clearAfter() {
        context.deleteSharedPreferences("localization")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun setApplicationLanguage_updatesSelectedTagAndLanguageImmediately() {
        val repository = LocalizationRepositoryImpl(context)

        repository.setApplicationLanguage("fr")

        assertEquals("fr", repository.selectedLanguageTag.value)
        assertEquals("fr", repository.languageFlow.value.tag)
        assertEquals(
            "Retour",
            repository.languageFlow.value.resolve("common", "back", emptyMap()),
        )
    }

    @Test
    fun setApplicationLanguage_nullClearsExplicitSelection() {
        val repository = LocalizationRepositoryImpl(context)

        repository.setApplicationLanguage("es")
        repository.setApplicationLanguage(null)

        assertNull(repository.selectedLanguageTag.value)
    }

    @Test
    fun selectedLanguage_survivesRepositoryRecreation() {
        LocalizationRepositoryImpl(context).setApplicationLanguage("pt")

        val recreated = LocalizationRepositoryImpl(context)

        assertEquals("pt", recreated.selectedLanguageTag.value)
        assertEquals("pt", recreated.languageFlow.value.tag)
    }
}
