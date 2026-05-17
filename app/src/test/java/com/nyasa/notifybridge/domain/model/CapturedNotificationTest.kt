package com.nyasa.notifybridge.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturedNotificationTest {
    @Test fun dedupeKey_combines_package_tag_id() {
        val n = CapturedNotification(
            packageName = "com.whatsapp", appLabel = "WhatsApp",
            title = "John", body = "hi", subText = null, category = "msg",
            channelId = "c", postTime = 1L, isOngoing = false,
            isClearable = true, tag = "t", id = 7)
        assertEquals("com.whatsapp|t|7", n.dedupeKey)
    }

    @Test fun dedupeKey_null_tag_uses_empty_string() {
        val n = CapturedNotification(
            packageName = "com.example", appLabel = "Ex",
            title = null, body = null, subText = null, category = null,
            channelId = null, postTime = 0L, isOngoing = false,
            isClearable = true, tag = null, id = 99)
        assertEquals("com.example||99", n.dedupeKey)
    }
}
