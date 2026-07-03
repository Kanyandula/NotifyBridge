package com.nyasa.notifybridge.data.db

import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxStatusTest {
    @Test fun `enum has exactly two values matching spec`() {
        val values = OutboxStatus.values().map { it.name }.toSet()
        assertEquals(setOf("PENDING", "FAILED_TERMINAL"), values)
    }

    @Test fun `MAX_PUBLISH_ATTEMPTS is 5 per spec`() {
        assertEquals(5, MAX_PUBLISH_ATTEMPTS)
    }
}
