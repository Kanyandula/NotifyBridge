package com.nyasa.notifybridge.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

fun displayBody(body: String, redact: Boolean, revealed: Boolean): String =
    if (redact && !revealed) "•".repeat(6) else body

data class StatusUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val outboxDepth: Int = 0,
    val brokerConfig: BrokerConfig = BrokerConfig(),
    val allowListSize: Int = 0,
    val appLock: AppLockPrefs = AppLockPrefs(),
)

@HiltViewModel
class StatusViewModel @Inject constructor(
    mqtt: MqttClientManager,
    outbox: OutboxRepository,
    settings: SettingsRepository,
    recentRepo: RecentNotificationsRepository,
) : ViewModel() {

    val uiState: StateFlow<StatusUiState> = combine(
        mqtt.connectionState,
        // Drainable depth only — excludes FAILED_TERMINAL poison rows, which
        // depth() would count forever even though they never publish (spec §3.2).
        outbox.pendingCount(),
        settings.brokerConfig,
        settings.allowList,
        settings.appLock,
    ) { connState, depth, broker, allow, lock ->
        StatusUiState(
            connectionState = connState,
            outboxDepth = depth,
            brokerConfig = broker,
            allowListSize = allow.size,
            appLock = lock,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatusUiState(),
    )

    val recentItems: StateFlow<List<RecentItem>> = recentRepo.recent
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
