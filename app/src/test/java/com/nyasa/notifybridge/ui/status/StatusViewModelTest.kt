package com.nyasa.notifybridge.ui.status
import org.junit.Assert.assertEquals
import org.junit.Test
class StatusViewModelTest {
    @Test fun body_redacted_until_revealed() {
        assertEquals("••••••", displayBody("secret", redact = true, revealed = false))
        assertEquals("secret", displayBody("secret", redact = true, revealed = true))
        assertEquals("secret", displayBody("secret", redact = false, revealed = false))
    }
}
