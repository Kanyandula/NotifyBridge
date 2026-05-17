package com.nyasa.notifybridge.service

import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifListenerLogicTest {
    private fun n(pkg: String) =
        CapturedNotification(pkg,"L","t","b",null,null,null,1L,false,true,null,1)

    @Test fun only_allowlisted_non_self_pass_dedup() {
        val accepted = mutableListOf<String>()
        val pipeline = NotifPipeline(
            allowList = { setOf("com.a") },
            selfPackage = "com.nyasa.notifybridge",
            dedup = NotificationDeduplicator(),
            onAccepted = { accepted += it.packageName })
        pipeline.handle(n("com.a"), 1000L)
        pipeline.handle(n("com.b"), 1000L)                       // not allow-listed
        pipeline.handle(n("com.nyasa.notifybridge"), 1000L)      // self
        assertEquals(listOf("com.a"), accepted)
    }
}
