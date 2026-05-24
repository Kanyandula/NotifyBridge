package com.nyasa.notifybridge

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nyasa.notifybridge.applock.AppLockGate
import com.nyasa.notifybridge.applock.AppLockManager
import com.nyasa.notifybridge.applock.BiometricAuthenticator
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.localization.Localized
import com.nyasa.notifybridge.ui.NotifyBridgeNavHost
import com.nyasa.notifybridge.ui.locked.LockedScreen
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var localization: LocalizationRepository
    private lateinit var lock: AppLockManager
    private var prefsEnabled = true
    private var idle = 60_000L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        // AppCompat owns the active locale; refresh on every onCreate so a
        // post-locale-change activity recreate immediately reflects in the
        // LocalizationRepository's StateFlow.
        localization.refresh()
        enableEdgeToEdge()
        lock = AppLockManager(enabled = { prefsEnabled }, idleMs = { idle })
        lifecycleScope.launch {
            settings.appLock.collect {
                prefsEnabled = it.enabled; idle = it.idleTimeoutMs
                // Cold start: _locked initializes true (enabled-by-default). Once
                // prefs load, unlock if the user has app-lock disabled — onStart
                // is skipped on cold open (hasStartedOnce), so nothing else would.
                if (!it.enabled) lock.onAuthenticated()
            }
        }
        val notifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val brokerSet = runBlocking { settings.brokerConfig.first() }.host.isNotBlank()
        val appsChosen = runBlocking { settings.allowList.first() }.isNotEmpty()
        val startOnboarding = !(notifAccess && brokerSet && appsChosen)
        val auth = BiometricAuthenticator(this)
        setContent {
            Localized(repository = localization) {
                NotifyBridgeTheme {
                    AppLockGate(
                        manager = lock,
                        locked = {
                            LockedScreen(onUnlock = {
                                auth.prompt(this, onSuccess = { lock.onAuthenticated() },
                                    onFail = {})
                            })
                        },
                        content = { NotifyBridgeNavHost(startOnboarding = startOnboarding) })
                }
            }
        }
        // FLAG_SECURE applied per-screen in Status/Broker (Task 20/21).
    }

    private var hasStartedOnce = false
    override fun onStop() { super.onStop(); lock.onBackgrounded(System.currentTimeMillis()) }
    override fun onStart() {
        super.onStart()
        // Skip the first onStart (cold open): there was no prior background
        // session to time, and the initial lock state already reflects the
        // pref. (The old `currentState != INITIALIZED` guard was dead code —
        // by onStart the state is never INITIALIZED — and only worked by
        // accident via AppLockManager's null backgroundedAt guard.)
        if (hasStartedOnce) lock.onForegrounded(System.currentTimeMillis())
        hasStartedOnce = true
    }
}
