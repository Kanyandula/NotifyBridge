package com.nyasa.notifybridge.ui.broker
import com.nyasa.notifybridge.domain.model.BrokerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class BrokerViewModelTest {
    @Test fun validates_host_and_port() {
        assertFalse(isValid(BrokerConfig(host = "", port = 1883)))
        assertFalse(isValid(BrokerConfig(host = "h", port = 0)))
        assertTrue(isValid(BrokerConfig(host = "h", port = 1883)))
    }
    @Test fun pinned_requires_cert() {
        assertEquals("Select a CA/cert file",
            certError(com.nyasa.notifybridge.domain.model.TlsMode.PINNED, null))
        assertEquals(null,
            certError(com.nyasa.notifybridge.domain.model.TlsMode.PINNED, "PEM"))
    }
}
