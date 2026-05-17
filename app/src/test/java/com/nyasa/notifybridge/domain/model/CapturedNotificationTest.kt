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
}
