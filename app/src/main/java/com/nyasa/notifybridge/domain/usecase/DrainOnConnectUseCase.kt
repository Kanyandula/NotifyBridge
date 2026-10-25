package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import javax.inject.Inject

/**
 * Drains the outbox after every (re)connection, including HiveMQ's automatic
 * reconnects, which nothing else observes.
 *
 * Keyed on [MqttClientManager.connectionEpoch] rather than connectionState:
 * StateFlow conflates values and compares them for equality, so a
 * disconnect-then-reconnect between two collections can read as "still
 * CONNECTED" and be lost. A counter that only goes up can merge several
 * reconnects into one emission, but it can never hide that one happened. So
 * if a reconnect happens during a drain, the collector sees the newer value
 * afterwards and drains again.
 *
 * The initial value is collected immediately, so starting this also runs one
 * drain up front. Never returns; launch it in a scope tied to the bridge.
 */
class DrainOnConnectUseCase @Inject constructor(
    private val mqtt: MqttClientManager,
    private val drain: DrainOutboxUseCase,
) {
    suspend operator fun invoke() {
        mqtt.connectionEpoch.collect { drain() }
    }
}
