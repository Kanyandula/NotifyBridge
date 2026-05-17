package com.nyasa.notifybridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryImplTest {
    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(ApplicationProvider.getApplicationContext<android.content.Context>()
                .cacheDir, "t${System.nanoTime()}.preferences_pb") }

    @Test fun broker_config_roundtrips() = runTest {
        val repo = SettingsRepositoryImpl(store())
        repo.setBrokerConfig(BrokerConfig(host = "h", port = 8883,
            tlsMode = TlsMode.PINNED, deviceName = "Pixel 7"))
        repo.brokerConfig.test {
            val c = awaitItem()
            assertEquals("h", c.host); assertEquals(8883, c.port)
            assertEquals(TlsMode.PINNED, c.tlsMode)
        }
    }

    @Test fun allow_list_roundtrips() = runTest {
        val repo = SettingsRepositoryImpl(store())
        repo.setAllowList(setOf("com.a", "com.b"))
        repo.allowList.test { assertEquals(setOf("com.a","com.b"), awaitItem()) }
    }

    @Test fun app_lock_defaults_enabled() = runTest {
        SettingsRepositoryImpl(store()).appLock.test {
            assertEquals(true, awaitItem().enabled)
        }
    }
}
