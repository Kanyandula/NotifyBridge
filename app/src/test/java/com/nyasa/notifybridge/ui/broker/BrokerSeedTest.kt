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
 * Regression (§3.4): the Broker form is bound to [BrokerViewModel.config]. The
 * persisted config is seeded off the main thread, so it must (a) end up applied
 * once DataStore emits, and (b) NOT clobber edits the user made before the seed
 * arrived (typed device name silently reverting to the "phone" default).
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

    private val persisted = BrokerConfig(
        host = "h", port = 8883, deviceName = "pixel7", tlsMode = TlsMode.SYSTEM_CA,
    )

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = BrokerViewModel(
        FakeSettings(persisted),
        TestConnectionUseCase(FakeMqttClientManager()),
    )

    @Test fun seed_applies_persisted_config() {
        val vm = newViewModel()
        main.scheduler.advanceUntilIdle()
        assertEquals("pixel7", vm.config.value.deviceName)
        assertEquals("h", vm.config.value.host)
        assertEquals(8883, vm.config.value.port)
        assertEquals(TlsMode.SYSTEM_CA, vm.config.value.tlsMode)
    }

    @Test fun seed_does_not_clobber_in_progress_edit() {
        val vm = newViewModel()
        // User starts typing before the off-main seed completes.
        vm.updateDeviceName("my-laptop")
        main.scheduler.advanceUntilIdle()
        // The late persisted seed must not overwrite the in-progress edit.
        assertEquals("my-laptop", vm.config.value.deviceName)
    }

    @Test fun seed_skipped_when_user_edited_back_to_defaults() {
        val vm = newViewModel()
        // User edits a field then clears it back to the default value before
        // the seed lands. A value-equality guard would mistake this for
        // "unedited" and clobber it with the persisted (non-default) config;
        // the userEdited flag must still suppress the seed.
        vm.updateHost("temp")
        vm.updateHost("")
        main.scheduler.advanceUntilIdle()
        assertEquals("", vm.config.value.host)
        assertEquals("phone", vm.config.value.deviceName)
    }
}
