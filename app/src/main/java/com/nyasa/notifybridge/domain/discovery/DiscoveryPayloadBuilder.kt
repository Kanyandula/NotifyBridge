package com.nyasa.notifybridge.domain.discovery

import com.nyasa.notifybridge.domain.model.CapturedNotification
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class DiscoveryPayloadBuilder @Inject constructor() {
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
            // Render the entity state as "App: Title" instead of the raw
            // JSON payload, so HA's Activity feed reads e.g.
            //   "NotifyBridge samsung-flip changed to SiriusXM: <track>"
            // The {{ … }} braces are HA Jinja — Kotlin's $-prefixed
            // string templates don't touch them.
            put("value_template", "{{ value_json.app }}: {{ value_json.title }}")
            // Strip internal keys (package, category) from HA's attribute
            // panel and convert post_time (epoch ms) into a local-time
            // string. HA's timestamp_local filter takes seconds.
            put("json_attributes_template", JSON_ATTRS_TEMPLATE)
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

    private companion object {
        // Pulled into a constant only because the Jinja blob is multi-line
        // and we want the JSON-string value to be a single compact line in
        // the discovery payload HA receives. Keys: app, title, text, time
        // (post_time converted from epoch ms to local time).
        const val JSON_ATTRS_TEMPLATE =
            """{"app":"{{ value_json.app }}",""" +
                """"title":"{{ value_json.title }}",""" +
                """"text":"{{ value_json.text }}",""" +
                """"time":"{{ (value_json.post_time | int / 1000) | timestamp_local }}"}"""
    }
}
