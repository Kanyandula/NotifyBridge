package com.nyasa.notifybridge.data.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode
import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Parses a PEM-encoded X.509 certificate string into an [X509Certificate].
 *
 * Fail-loud contract (§3.3/S4): throws on blank input or parse failure —
 * never silently falls back to system-CA or trust-all.
 */
internal fun parsePinnedCert(pem: String): X509Certificate {
    require(pem.isNotBlank()) { "pinnedCertPem must not be blank" }
    val factory = CertificateFactory.getInstance("X.509")
    return factory.generateCertificate(pem.byteInputStream()) as? X509Certificate
        ?: throw IllegalArgumentException("PEM did not decode to an X.509 certificate")
}

@Singleton
class HiveMqClientManager @Inject constructor(
    private val discovery: DiscoveryPayloadBuilder,
) : MqttClientManager {

    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    private val epoch = MutableStateFlow(0L)
    override val connectionEpoch = epoch
    private var client: Mqtt5AsyncClient? = null
    private var activeConfig: BrokerConfig? = null
    private var device: String = "phone"

    // Serialises connect/ensureConnected so concurrent callers (service,
    // worker, Test button) can't build two clients or tear down each other's.
    private val connectionMutex = Mutex()

    override suspend fun connect(config: BrokerConfig) = connectionMutex.withLock { connectLocked(config) }

    override suspend fun ensureConnected(config: BrokerConfig) {
        connectionMutex.withLock {
            if (!shouldReuse(client?.state, activeConfig, config)) connectLocked(config)
        }
    }

    private suspend fun connectLocked(config: BrokerConfig) {
        state.value = ConnectionState.CONNECTING
        device = config.deviceName
        runCatching { client?.disconnect()?.await() }
        client = null
        activeConfig = null
        val builder = MqttClient.builder().useMqttVersion5()
            .identifier(clientId(config))
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnectWithDefaultConfig()
            .addDisconnectedListener { state.value = ConnectionState.DISCONNECTED }
            .addConnectedListener {
                state.value = ConnectionState.CONNECTED
                epoch.update { it + 1 }
            }
        if (useTls(config)) {
            if (requiresPinnedCert(config)) {
                // §3.3/S4: pin to ONLY the supplied cert.
                // parsePinnedCert throws on blank/invalid PEM — no silent downgrade.
                val cert = runCatching {
                    parsePinnedCert(config.pinnedCertPem ?: "")
                }.getOrElse { cause ->
                    state.value = ConnectionState.ERROR
                    throw IllegalStateException(
                        "Failed to parse pinnedCertPem — refusing to connect without pinning",
                        cause)
                }
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).also {
                    it.load(null, null)
                    it.setCertificateEntry("pinned", cert)
                }
                val tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()).also { it.init(keyStore) }
                builder.sslConfig().trustManagerFactory(tmf).applySslConfig()
            } else {
                builder.sslWithDefaultConfig()
            }
        }
        val c = builder.buildAsync()
        client = c
        activeConfig = config
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
            // A post-connect publish (status/discovery retain) can throw even
            // after the socket connected and addConnectedListener emitted
            // CONNECTED. Only latch ERROR if the underlying client is actually
            // down — otherwise we'd stamp ERROR over a live session and the UI
            // would stay stuck even while publish() succeeds.
            Log.w("HiveMq", "connect() catch fired (clientConnected=${c.state.isConnected})", t)
            connectExceptionState(c.state.isConnected)?.let { state.value = it }
        }
    }

    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): PublishOutcome {
        val c = client ?: return PublishOutcome.Transient
        return runPublish {
            c.publishWith().topic(topic).payload(payload.toByteArray())
                .qos(if (qos >= 1) MqttQos.AT_LEAST_ONCE else MqttQos.AT_MOST_ONCE)
                .retain(retained).send().await()
        }
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

        /**
         * Next state when connect() catches an exception. Null means "leave
         * state alone" — the catch fired but the client is still connected,
         * so a publish failure should not overwrite the live CONNECTED state.
         */
        fun connectExceptionState(clientConnected: Boolean): ConnectionState? =
            if (clientConnected) null else ConnectionState.ERROR

        /**
         * ensureConnected() keeps the current client iff it's connected or in
         * HiveMQ's own reconnect backoff, for the same config. Rebuilding a
         * reconnecting client would only reset its backoff; a settings change
         * (different config) still rebuilds.
         */
        fun shouldReuse(state: MqttClientState?, active: BrokerConfig?, wanted: BrokerConfig) =
            state != null && state.isConnectedOrReconnect && active == wanted

        @Suppress("TooGenericExceptionCaught") // every throwable must map to an outcome
        internal suspend inline fun runPublish(block: () -> Unit): PublishOutcome =
            try {
                block()
                PublishOutcome.Success
            } catch (e: CancellationException) {
                throw e // lifecycle control, never a publish failure
            } catch (t: Throwable) {
                classifyPublishError(t)
            }

        /**
         * Publish error policy (spec §3.2, remediation K).
         *
         * [PublishOutcome.Transient] — never counts toward MAX_PUBLISH_ATTEMPTS:
         * - [MqttClientStateException]: not connected (offline, or in HiveMQ's
         *   reconnect backoff).
         * - [MqttSessionExpiredException]: in-flight publish dropped on
         *   reconnect (session expiry 0, republishIfSessionExpired = false).
         * - [ConnectionClosedException], [ConnectionFailedException]: transport down.
         * - [Mqtt5PubAckException] with QUOTA_EXCEEDED or PACKET_IDENTIFIER_IN_USE:
         *   broker-side backpressure.
         *
         * [PublishOutcome.Rejected] — counts toward the cap: every other PUBACK
         * reason code (NOT_AUTHORIZED, TOPIC_NAME_INVALID, PAYLOAD_FORMAT_INVALID,
         * UNSPECIFIED_ERROR, IMPLEMENTATION_SPECIFIC_ERROR, …) and any unknown
         * throwable. Deliberate: keeps poison-row protection for failures that
         * only surface as generic errors.
         *
         * Trade-off: a broker that answers QUOTA_EXCEEDED forever holds the
         * queue until the 7-day TTL prunes it.
         */
        fun classifyPublishError(t: Throwable): PublishOutcome = when (t) {
            is MqttClientStateException,
            is MqttSessionExpiredException,
            is ConnectionClosedException,
            is ConnectionFailedException -> PublishOutcome.Transient
            is Mqtt5PubAckException -> when (t.mqttMessage.reasonCode) {
                Mqtt5PubAckReasonCode.QUOTA_EXCEEDED,
                Mqtt5PubAckReasonCode.PACKET_IDENTIFIER_IN_USE -> PublishOutcome.Transient
                else -> PublishOutcome.Rejected
            }
            else -> PublishOutcome.Rejected
        }
    }
}
