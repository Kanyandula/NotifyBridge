package com.nyasa.notifybridge.ui.broker

import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.TestConnectionUseCase
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression: the Broker form is bound to [BrokerViewModel.config]; if the
 * persisted config is seeded asynchronously, a delayed emission overwrites the
 * user's in-progress edits (device name silently reverts to the "phone"
 * default). The seed must be applied synchronously at construction so the
 * StateFlow is the persisted value immediately — no async clobber window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrokerSeedTest {
    private val main = StandardTestDispatcher()

    private class FakeSettings(private val cfg: BrokerConfig) : SettingsRepository {
        override val brokerConfig: Flow<BrokerConfig> = flowOf(cfg)
        override suspend fun setBrokerConfig(config: BrokerConfig) {}
        override val allowList: Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun setAllowList(packages: Set<String>) {}
        override val appLock: Flow<AppLockPrefs> = MutableStateFlow(AppLockPrefs())
        override suspend fun setAppLock(prefs: AppLockPrefs) {}
    }

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun config_is_persisted_value_synchronously_at_construction() {
        val persisted = BrokerConfig(host = "h", port = 8883, deviceName = "pixel7",
            tlsMode = TlsMode.SYSTEM_CA)
        val vm = BrokerViewModel(
            FakeSettings(persisted),
            TestConnectionUseCase(FakeMqttClientManager()))
        // Read immediately — no advanceUntilIdle / no Main pump. With an async
        // seed this is still BrokerConfig() default (deviceName="phone"); the
        // fix makes it the persisted config right away.
        assertEquals("pixel7", vm.config.value.deviceName)
        assertEquals("h", vm.config.value.host)
        assertEquals(8883, vm.config.value.port)
        assertEquals(TlsMode.SYSTEM_CA, vm.config.value.tlsMode)
    }
}