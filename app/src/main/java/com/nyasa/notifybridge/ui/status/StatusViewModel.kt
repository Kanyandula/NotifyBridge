package com.nyasa.notifybridge.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

fun displayBody(body: String, redact: Boolean, revealed: Boolean): String =
    if (redact && !revealed) "•".repeat(6) else body

data class RecentItem(
    val id: Long,
    val app: String,
    val title: String,
    val body: String,
    val postTime: Long,
)

data class StatusUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val outboxDepth: Int = 0,
    val brokerConfig: BrokerConfig = BrokerConfig(),
    val allowListSize: Int = 0,
    val appLock: AppLockPrefs = AppLockPrefs(),
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val mqtt: MqttClientManager,
    private val outbox: OutboxRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    // v1: read-on-load snapshot (plan §3.5 "read-only"); not a live stream.
    private val recentFlow = flow {
        val batch = outbox.nextBatch(20)
        val items = batch.mapNotNull { item ->
            runCatching {
                val obj = lenientJson.parseToJsonElement(item.payload).jsonObject
                RecentItem(
                    id = item.id,
                    app = obj["app"]?.jsonPrimitive?.content.orEmpty(),
                    title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                    body = obj["text"]?.jsonPrimitive?.content.orEmpty(),
                    postTime = obj["post_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: item.createdAt,
                )
            }.getOrNull()
        }
        emit(items)
    }

    val uiState: StateFlow<StatusUiState> = combine(
        mqtt.connectionState,
        outbox.depth(),
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

    val recentItems: StateFlow<List<RecentItem>> = recentFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
