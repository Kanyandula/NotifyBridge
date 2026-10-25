package com.nyasa.notifybridge.data.mqtt

import com.hivemq.client.mqtt.MqttClientState
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveMqOptionsTest {
    @Test fun tls_off_means_no_ssl() {
        assertFalse(HiveMqClientManager.useTls(BrokerConfig(tlsMode = TlsMode.OFF)))
    }
    @Test fun pinned_requires_cert() {
        assertTrue(HiveMqClientManager.useTls(BrokerConfig(tlsMode = TlsMode.PINNED)))
        assertEquals(true, HiveMqClientManager.requiresPinnedCert(
            BrokerConfig(tlsMode = TlsMode.PINNED)))
        assertEquals(false, HiveMqClientManager.requiresPinnedCert(
            BrokerConfig(tlsMode = TlsMode.SYSTEM_CA)))
    }
    @Test fun client_id_is_stable_per_device() {
        assertEquals(
            HiveMqClientManager.clientId(BrokerConfig(deviceName = "Pixel 7")),
            HiveMqClientManager.clientId(BrokerConfig(deviceName = "Pixel 7")))
    }

    @Test fun reuses_only_a_live_or_reconnecting_client_for_the_same_config() {
        val cfg = BrokerConfig(host = "a")
        val other = BrokerConfig(host = "b")
        val reusable = setOf(
            MqttClientState.CONNECTED,
            MqttClientState.DISCONNECTED_RECONNECT,
            MqttClientState.CONNECTING_RECONNECT,
        )
        MqttClientState.values().forEach { s ->
            assertEquals(s.name, s in reusable, HiveMqClientManager.shouldReuse(s, cfg, cfg))
            assertFalse(s.name, HiveMqClientManager.shouldReuse(s, cfg, other))
        }
        assertFalse(HiveMqClientManager.shouldReuse(null, null, cfg))
        assertFalse(HiveMqClientManager.shouldReuse(MqttClientState.CONNECTED, null, cfg))
    }
}
