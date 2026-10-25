package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMqttClientManager(
    var connectSucceeds: Boolean = true,
) : MqttClientManager {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    private val epoch = MutableStateFlow(0L)
    override val connectionEpoch = epoch
    val published = mutableListOf<Triple<String, String, Boolean>>()
    var publishOutcome: PublishOutcome = PublishOutcome.Success

    /** Override to suspend or script per-call outcomes; defaults to [publishOutcome]. */
    var onPublish: suspend () -> PublishOutcome = { publishOutcome }

    override suspend fun connect(config: BrokerConfig) {
        state.value = if (connectSucceeds) ConnectionState.CONNECTED
        else ConnectionState.ERROR
        if (connectSucceeds) epoch.value++
    }
    override suspend fun ensureConnected(config: BrokerConfig) {
        if (state.value != ConnectionState.CONNECTED) connect(config)
    }

    /** Mimics HiveMQ's automatic reconnect: no connect() call, epoch still bumps. */
    fun simulateReconnect() {
        state.value = ConnectionState.CONNECTED
        epoch.value++
    }
    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): PublishOutcome =
        onPublish().also { if (it == PublishOutcome.Success) published += Triple(topic, payload, retained) }
    override suspend fun disconnect() { state.value = ConnectionState.DISCONNECTED }
}
