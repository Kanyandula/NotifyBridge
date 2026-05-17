package com.nyasa.notifybridge.domain.discovery

import com.nyasa.notifybridge.domain.model.CapturedNotification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DiscoveryPayloadBuilder {
    private fun slug(device: String) =
        device.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
            .trim('-').ifEmpty { "phone" }

    fun stateTopic(device: String) = "notifybridge/${slug(device)}/notification"
    fun statusTopic(device: String) = "notifybridge/${slug(device)}/status"
    fun discoveryTopic(device: String) =
        "homeassistant/sensor/notifybridge_${slug(device)}/config"

    fun discoveryConfig(device: String): String {
        val s = slug(device)
        return buildJsonObject {
            put("name", "NotifyBridge $device")
            put("unique_id", "notifybridge_$s")
            put("state_topic", stateTopic(device))
            put("json_attributes_topic", stateTopic(device))
            put("availability_topic", statusTopic(device))
            put("payload_available", "online")
            put("payload_not_available", "offline")
            put("icon", "mdi:bell-ring")
        }.toString()
    }

    fun eventPayload(n: CapturedNotification): String {
        val state = (n.body ?: n.title ?: n.appLabel).take(255)
        return buildJsonObject {
            put("state", state)
            put("title", n.title ?: "")
            put("text", n.body ?: "")
            put("app", n.appLabel)
            put("package", n.packageName)
            put("category", n.category ?: "")
            put("post_time", n.postTime)
        }.toString()
    }
}
