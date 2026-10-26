package com.nyasa.notifybridge.domain.mqtt

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

interface MqttClientManager {
    val connectionState: StateFlow<ConnectionState>

    /** Increments on every successful (re)connection, including automatic reconnects. */
    val connectionEpoch: StateFlow<Long>

    /** Forced rebuild: tears down any existing client and starts connecting
     *  with [config]. Doesn't wait for the broker; see [awaitConnected]. */
    suspend fun connect(config: BrokerConfig)

    /** Starts connecting only if there is no live, connecting or reconnecting
     *  client for [config]. Doesn't wait for the broker; see [awaitConnected]. */
    suspend fun ensureConnected(config: BrokerConfig)

    suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): PublishOutcome
    suspend fun disconnect()
}

/**
 * Waits up to [timeoutMs] for CONNECTED; false on timeout or ERROR. Bounded
 * because the client retries an unreachable or misconfigured broker forever.
 */
suspend fun MqttClientManager.awaitConnected(timeoutMs: Long = 10_000): Boolean =
    withTimeoutOrNull(timeoutMs) {
        connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.ERROR }
    } == ConnectionState.CONNECTED
