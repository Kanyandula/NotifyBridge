package com.nyasa.notifybridge.ui.permissions

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PermPill { GRANTED, ACTION_NEEDED }
fun permPill(granted: Boolean) = if (granted) PermPill.GRANTED else PermPill.ACTION_NEEDED

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    // Permission booleans — refreshed on demand via refresh()
    private val _notifAccessGranted = MutableStateFlow(readNotifAccess())
    val notifAccessGranted: StateFlow<Boolean> = _notifAccessGranted.asStateFlow()

    private val _batteryExempt = MutableStateFlow(readBatteryExempt())
    val batteryExempt: StateFlow<Boolean> = _batteryExempt.asStateFlow()

    // App-lock prefs from DataStore
    val appLock: StateFlow<AppLockPrefs> = settings.appLock
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLockPrefs())

    // Re-read permission state (call from screen's LaunchedEffect on resume)
    fun refresh() {
        _notifAccessGranted.value = readNotifAccess()
        _batteryExempt.value = readBatteryExempt()
    }

    fun setLockEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setAppLock(appLock.value.copy(enabled = enabled))
    }

    fun setIdleTimeout(idleTimeoutMs: Long) = viewModelScope.launch {
        settings.setAppLock(appLock.value.copy(idleTimeoutMs = idleTimeoutMs))
    }

    fun setRedactBody(redact: Boolean) = viewModelScope.launch {
        settings.setAppLock(appLock.value.copy(redactBody = redact))
    }

    private fun readNotifAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    private fun readBatteryExempt(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
}
