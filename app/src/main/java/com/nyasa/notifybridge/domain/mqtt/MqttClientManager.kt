package com.nyasa.notifybridge.domain.mqtt

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface MqttClientManager {
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(config: BrokerConfig)
    suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean
    suspend fun disconnect()
}
