package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import javax.inject.Inject

class TestConnectionUseCase @Inject constructor(
    private val mqtt: MqttClientManager,
) {
    suspend operator fun invoke(config: BrokerConfig): Boolean {
        mqtt.connect(config)
        return mqtt.connectionState.value == ConnectionState.CONNECTED
    }
}
