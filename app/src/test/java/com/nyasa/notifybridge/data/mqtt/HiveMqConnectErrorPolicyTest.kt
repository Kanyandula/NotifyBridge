package com.nyasa.notifybridge.data.mqtt

import com.nyasa.notifybridge.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the connect()-catch policy for HiveMqClientManager.
 *
 * Bug repro: when initial CONNECT succeeds (addConnectedListener fires
 * CONNECTED) but a post-connect publish (status/discovery retain) throws,
 * the catch block previously wrote ERROR unconditionally — overwriting the
 * live CONNECTED state. Future publishes kept working but the UI was stuck
 * on Error.
 *
 * Policy: only latch ERROR when the underlying client is NOT connected.
 */
class HiveMqConnectErrorPolicyTest {
    @Test fun latches_error_when_client_disconnected() {
        assertEquals(
            ConnectionState.ERROR,
            HiveMqClientManager.connectExceptionState(clientConnected = false),
        )
    }

    @Test fun preserves_state_when_client_still_connected() {
        assertNull(HiveMqClientManager.connectExceptionState(clientConnected = true))
    }
}
