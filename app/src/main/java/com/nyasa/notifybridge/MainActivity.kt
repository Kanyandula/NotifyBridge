package com.nyasa.notifybridge

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nyasa.notifybridge.applock.AppLockGate
import com.nyasa.notifybridge.applock.AppLockManager
import com.nyasa.notifybridge.applock.BiometricAuthenticator
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.ui.NotifyBridgeNavHost
import com.nyasa.notifybridge.ui.locked.LockedScreen
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsRepository
    private lateinit var lock: AppLockManager
    private var prefsEnabled = true
    private var idle = 60_000L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        lock = AppLockManager(enabled = { prefsEnabled }, idleMs = { idle })
        lifecycleScope.launch {
            settings.appLock.collect { prefsEnabled = it.enabled; idle = it.idleTimeoutMs }
        }
        val auth = BiometricAuthenticator(this)
        setContent {
            NotifyBridgeTheme {
                AppLockGate(
                    manager = lock,
                    locked = {
                        LockedScreen(onUnlock = {
                            auth.prompt(this, onSuccess = { lock.onAuthenticated() },
                                onFail = {})
                        })
                    },
                    content = { NotifyBridgeNavHost(startOnboarding = false) })
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
