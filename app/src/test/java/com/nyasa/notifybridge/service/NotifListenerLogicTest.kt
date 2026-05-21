package com.nyasa.notifybridge.service

import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifListenerLogicTest {
    private fun n(
        pkg: String,
        category: String? = null,
        isGroupSummary: Boolean = false,
    ) = CapturedNotification(
        packageName = pkg, appLabel = "L", title = "t", body = "b",
        subText = null, category = category, channelId = null,
        postTime = 1L, isOngoing = false, isClearable = true,
        tag = null, id = 1, isGroupSummary = isGroupSummary)

    private fun makePipeline(
        allow: Set<String> = setOf("com.a"),
        onAccepted: (CapturedNotification) -> Unit,
    ) = NotifPipeline(
        allowList = { allow },
        selfPackage = "com.nyasa.notifybridge",
        dedup = NotificationDeduplicator(),
        onAccepted = onAccepted)

    @Test fun only_allowlisted_non_self_pass_dedup() {
        val accepted = mutableListOf<String>()
        val pipeline = makePipeline { accepted += it.packageName }
        pipeline.handle(n("com.a"), 1000L)
        pipeline.handle(n("com.b"), 1000L)                       // not allow-listed
        pipeline.handle(n("com.nyasa.notifybridge"), 1000L)      // self
        assertEquals(listOf("com.a"), accepted)
    }

    @Test fun drops_group_summary_notifications() {
        val accepted = mutableListOf<CapturedNotification>()
        val pipeline = makePipeline { accepted += it }
        pipeline.handle(n("com.a", isGroupSummary = true), 1000L)
        assertEquals(emptyList<CapturedNotification>(), accepted)
    }

    @Test fun passes_allowlisted_transport_category_notifications() {
        val accepted = mutableListOf<CapturedNotification>()
        val pipeline = makePipeline { accepted += it }
        pipeline.handle(n("com.a", category = "transport"), 1000L)
        assertEquals(1, accepted.size)
    }

    @Test fun drops_non_allowlisted_transport_category_notifications() {
        val accepted = mutableListOf<CapturedNotification>()
        val pipeline = makePipeline { accepted += it }
        pipeline.handle(n("com.b", category = "transport"), 1000L)
        assertEquals(emptyList<CapturedNotification>(), accepted)
    }

    @Test fun passes_non_summary_non_transport_notifications() {
        val accepted = mutableListOf<CapturedNotification>()
        val pipeline = makePipeline { accepted += it }
        pipeline.handle(n("com.a", category = "msg"), 1000L)
        assertEquals(1, accepted.size)
    }
}
