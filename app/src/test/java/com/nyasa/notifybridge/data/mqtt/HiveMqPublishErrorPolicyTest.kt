package com.nyasa.notifybridge.data.mqtt

import com.hivemq.client.mqtt.exceptions.ConnectionClosedException
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAck
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pins the publish-error policy for HiveMqClientManager (remediation K).
 *
 * Bug repro: publish() mapped every exception to `false`, and the drain
 * counted every `false` toward MAX_PUBLISH_ATTEMPTS — so five drains during a
 * broker outage turned the head row FAILED_TERMINAL and it was never delivered.
 *
 * Policy: connection-level failures and broker backpressure are Transient
 * (never counted); everything else is Rejected (counted).
 */
class HiveMqPublishErrorPolicyTest {
    private fun classify(t: Throwable) = HiveMqClientManager.classifyPublishError(t)

    private fun pubAck(code: Mqtt5PubAckReasonCode) =
        Mqtt5PubAckException(mockk<Mqtt5PubAck> { every { reasonCode } returns code }, "puback $code")

    @Test fun connection_level_failures_are_transient() {
        listOf(
            MqttClientStateException("not connected"),
            MqttSessionExpiredException("session expired", RuntimeException()),
            ConnectionClosedException("closed"),
            ConnectionFailedException("failed"),
        ).forEach { assertEquals(it.javaClass.simpleName, PublishOutcome.Transient, classify(it)) }
    }

    @Test fun backpressure_puback_codes_are_transient() {
        listOf(Mqtt5PubAckReasonCode.QUOTA_EXCEEDED, Mqtt5PubAckReasonCode.PACKET_IDENTIFIER_IN_USE)
            .forEach { assertEquals(it.name, PublishOutcome.Transient, classify(pubAck(it))) }
    }

    @Test fun every_other_puback_error_code_is_rejected() {
        listOf(
            Mqtt5PubAckReasonCode.NOT_AUTHORIZED,
            Mqtt5PubAckReasonCode.TOPIC_NAME_INVALID,
            Mqtt5PubAckReasonCode.PAYLOAD_FORMAT_INVALID,
            Mqtt5PubAckReasonCode.UNSPECIFIED_ERROR,
            Mqtt5PubAckReasonCode.IMPLEMENTATION_SPECIFIC_ERROR,
        ).forEach { assertEquals(it.name, PublishOutcome.Rejected, classify(pubAck(it))) }
    }

    @Test fun unknown_throwable_is_rejected() {
        assertEquals(PublishOutcome.Rejected, classify(IllegalStateException("boom")))
    }

    @Test fun runPublish_maps_success_and_failures() = runTest {
        assertEquals(PublishOutcome.Success, HiveMqClientManager.runPublish {})
        assertEquals(
            PublishOutcome.Transient,
            HiveMqClientManager.runPublish { throw ConnectionClosedException("closed") },
        )
    }

    @Test(expected = CancellationException::class)
    fun runPublish_rethrows_cancellation() = runTest {
        HiveMqClientManager.runPublish { throw CancellationException() }
    }
}
