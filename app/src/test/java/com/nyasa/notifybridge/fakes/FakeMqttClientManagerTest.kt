package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeMqttClientManagerTest {
    @Test fun connect_sets_state_and_records_publishes() = runTest {
        val m = FakeMqttClientManager()
        m.connect(BrokerConfig(host = "h"))
        assertEquals(ConnectionState.CONNECTED, m.connectionState.value)
        assertEquals(PublishOutcome.Success, m.publish("t", "p", 1, false))
        assertEquals("t" to "p", m.published.single().let { it.first to it.second })
    }
}
