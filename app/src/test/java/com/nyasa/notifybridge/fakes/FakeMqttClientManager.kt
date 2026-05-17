package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMqttClientManager(
    var connectSucceeds: Boolean = true,
) : MqttClientManager {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    val published = mutableListOf<Triple<String, String, Boolean>>()
    var failPublish = false

    override suspend fun connect(config: BrokerConfig) {
        state.value = if (connectSucceeds) ConnectionState.CONNECTED
        else ConnectionState.ERROR
    }
    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean {
        if (failPublish) return false
        published += Triple(topic, payload, retained)
        return true
    }
    override suspend fun disconnect() { state.value = ConnectionState.DISCONNECTED }
}
