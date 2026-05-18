package com.nyasa.notifybridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.nyasa.notifybridge.domain.model.TlsMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsTlsParseTest {

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(
                ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
                "t${System.nanoTime()}.preferences_pb"
            )
        }

    @Test fun corrupt_tls_value_falls_back_to_OFF() = runTest {
        // Single store instance shared by both the corrupt-value writer and the repo
        val ds = store()
        // Inject a corrupt tls value directly — same key name used by SettingsRepositoryImpl
        ds.edit { it[stringPreferencesKey("tls")] = "GARBAGE_NOT_AN_ENUM" }

        val repo = SettingsRepositoryImpl(ds)
        val config = repo.brokerConfig.first()

        assertEquals(TlsMode.OFF, config.tlsMode)
    }
}
