package com.nyasa.notifybridge.domain.mqtt

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface MqttClientManager {
    val connectionState: StateFlow<ConnectionState>

    /** Increments on every successful (re)connection, including automatic reconnects. */
    val connectionEpoch: StateFlow<Long>

    /** Forced rebuild: tears down any existing client and connects with [config]. */
    suspend fun connect(config: BrokerConfig)

    /** Connects only if there is no live or reconnecting client for [config]. */
    suspend fun ensureConnected(config: BrokerConfig)

    suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): PublishOutcome
    suspend fun disconnect()
}
