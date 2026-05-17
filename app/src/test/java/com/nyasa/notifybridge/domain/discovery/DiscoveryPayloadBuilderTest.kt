package com.nyasa.notifybridge.domain.discovery

import com.nyasa.notifybridge.domain.model.CapturedNotification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPayloadBuilderTest {
    private val b = DiscoveryPayloadBuilder()

    @Test fun slugifies_device_in_topics() {
        assertEquals("notifybridge/pixel-7/notification", b.stateTopic("Pixel 7"))
        assertEquals("notifybridge/pixel-7/status", b.statusTopic("Pixel 7"))
        assertEquals(
            "homeassistant/sensor/notifybridge_pixel-7/config",
            b.discoveryTopic("Pixel 7"))
    }

    @Test fun discovery_config_wires_state_attrs_availability() {
        val o = Json.parseToJsonElement(b.discoveryConfig("Pixel 7")).jsonObject
        assertEquals("notifybridge/pixel-7/notification",
            o["state_topic"]!!.jsonPrimitive.content)
        assertEquals("notifybridge/pixel-7/notification",
            o["json_attributes_topic"]!!.jsonPrimitive.content)
        assertEquals("notifybridge/pixel-7/status",
            o["availability_topic"]!!.jsonPrimitive.content)
        assertTrue(o.containsKey("unique_id"))
    }

    @Test fun event_payload_truncates_state_to_255_and_keeps_attrs() {
        val n = CapturedNotification("com.x", "X", "Title",
            "y".repeat(400), null, "msg", "c", 99L, false, true, null, 1)
        val o = Json.parseToJsonElement(b.eventPayload(n)).jsonObject
        assertEquals(255, o["state"]!!.jsonPrimitive.content.length)
        assertEquals("Title", o["title"]!!.jsonPrimitive.content)
        assertEquals("com.x", o["package"]!!.jsonPrimitive.content)
        assertEquals("X", o["app"]!!.jsonPrimitive.content)
        assertEquals(99L, o["post_time"]!!.jsonPrimitive.content.toLong())
    }
}
