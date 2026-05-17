package com.nyasa.notifybridge.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import kotlinx.coroutines.future.await
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiveMqClientManager @Inject constructor(
    private val discovery: DiscoveryPayloadBuilder,
) : MqttClientManager {

    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    private var client: Mqtt5AsyncClient? = null
    private var device: String = "phone"

    override suspend fun connect(config: BrokerConfig) {
        state.value = ConnectionState.CONNECTING
        device = config.deviceName
        val builder = MqttClient.builder().useMqttVersion5()
            .identifier(clientId(config))
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnectWithDefaultConfig()
            .addDisconnectedListener { state.value = ConnectionState.DISCONNECTED }
        if (useTls(config)) {
            if (requiresPinnedCert(config)) {
                // Do NOT silently fall back to system-CA — that is the exact
                // validation downgrade §3.3/S4 forbids. Fail loud until the
                // pinned trust manager lands in Task 25.
                state.value = ConnectionState.ERROR
                throw IllegalStateException(
                    "Pinned-cert TLS not yet implemented (Task 25). " +
                    "Use TLS OFF or SYSTEM_CA until then.")
            }
            builder.sslWithDefaultConfig()
        }
        val c = builder.buildAsync()
        client = c
        try {
            val connect = c.connectWith()
                .willPublish()
                    .topic(discovery.statusTopic(device))
                    .payload("offline".toByteArray())
                    .applyWillPublish()
            if (!config.username.isNullOrBlank()) {
                connect.simpleAuth()
                    .username(config.username)
                    .password((config.password ?: "").toByteArray())
                    .applySimpleAuth()
            }
            connect.send().await()
            c.publishWith().topic(discovery.statusTopic(device))
                .payload("online".toByteArray()).retain(true)
                .qos(MqttQos.AT_LEAST_ONCE).send().await()
            c.publishWith().topic(discovery.discoveryTopic(device))
                .payload(discovery.discoveryConfig(device).toByteArray()).retain(true)
                .qos(MqttQos.AT_LEAST_ONCE).send().await()
            state.value = ConnectionState.CONNECTED
        } catch (t: Throwable) {
            state.value = ConnectionState.ERROR
        }
    }

    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean {
        val c = client ?: return false
        return try {
            c.publishWith().topic(topic).payload(payload.toByteArray())
                .qos(if (qos >= 1) MqttQos.AT_LEAST_ONCE else MqttQos.AT_MOST_ONCE)
                .retain(retained).send().await()
            true
        } catch (t: Throwable) { false }
    }

    override suspend fun disconnect() {
        runCatching { client?.disconnect()?.await() }
        state.value = ConnectionState.DISCONNECTED
    }

    companion object {
        fun useTls(c: BrokerConfig) = c.tlsMode != TlsMode.OFF
        fun requiresPinnedCert(c: BrokerConfig) = c.tlsMode == TlsMode.PINNED
        fun clientId(c: BrokerConfig) =
            "notifybridge-" + c.deviceName.lowercase().replace(Regex("[^a-z0-9]+"), "-")
    }
}
