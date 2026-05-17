package com.nyasa.notifybridge.domain.notif

import com.nyasa.notifybridge.domain.model.CapturedNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeduplicatorTest {
    private fun n(body: String?, ongoing: Boolean = false) = CapturedNotification(
        "com.x", "X", "t", body, null, null, null, 0L, ongoing, true, null, 1)

    @Test fun first_post_forwards() {
        assertTrue(NotificationDeduplicator().shouldForward(n("a"), 1000L))
    }

    @Test fun same_content_within_debounce_dropped() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertFalse(d.shouldForward(n("a"), 1300L))
    }

    @Test fun content_change_forwards_even_within_debounce() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertTrue(d.shouldForward(n("b"), 1100L))
    }

    @Test fun same_content_after_debounce_forwards() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertTrue(d.shouldForward(n("a"), 1600L))
    }

    @Test fun ongoing_unchanged_dropped_regardless_of_time() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("50%", ongoing = true), 1000L)
        assertFalse(d.shouldForward(n("50%", ongoing = true), 9000L))
    }
}
