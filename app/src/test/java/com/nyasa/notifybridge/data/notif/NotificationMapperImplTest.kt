package com.nyasa.notifybridge.data.notif

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk=33: extractMessagingStyleFromNotification is API 28+; the mapper guards
// SDK_INT < P and returns null, so MessagingStyle must be exercised at >= 28.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationMapperImplTest {
    private val mapper = NotificationMapperImpl()

    private fun sbn(extras: Bundle, ongoing: Boolean = false): StatusBarNotification {
        val notif = Notification().apply {
            this.extras = extras
            if (ongoing) flags = flags or Notification.FLAG_ONGOING_EVENT
            category = "msg"
        }
        return mockk(relaxed = true) {
            every { packageName } returns "com.x"
            every { notification } returns notif
            every { postTime } returns 42L
            every { tag } returns null
            every { id } returns 3
            every { isOngoing } returns ongoing
            every { isClearable } returns !ongoing
        }
    }

    @Test fun big_text_preferred_over_text() {
        val b = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "T")
            putCharSequence(Notification.EXTRA_TEXT, "short")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "big body")
        }
        val r = mapper.map(sbn(b), "App X")
        assertEquals("big body", r.body)
        assertEquals("T", r.title)
        assertEquals("App X", r.appLabel)
    }

    @Test fun falls_back_to_text_then_null() {
        val r1 = mapper.map(sbn(Bundle().apply {
            putCharSequence(Notification.EXTRA_TEXT, "only text") }), "X")
        assertEquals("only text", r1.body)
        val r2 = mapper.map(sbn(Bundle()), "X")
        assertEquals(null, r2.body)
    }

    @Test fun messaging_style_takes_latest_with_sender_prefix() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val person = android.app.Person.Builder().setName("Alice").build()
        val style = Notification.MessagingStyle(person)
            .addMessage("first", 1L, person)
            .addMessage("latest", 2L, person)
        val built = Notification.Builder(ctx, "ch")
            .setStyle(style)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        val sbn = mockk<StatusBarNotification>(relaxed = true) {
            every { packageName } returns "com.msg"
            every { notification } returns built
            every { postTime } returns 1L
            every { tag } returns null
            every { id } returns 1
            every { isOngoing } returns false
            every { isClearable } returns true
        }
        assertEquals("Alice: latest", mapper.map(sbn, "Msg").body)
    }
}
