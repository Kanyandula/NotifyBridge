package com.nyasa.notifybridge.ui.locked
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class LockedScreenTest {
    @get:Rule val c = createComposeRule()
    @Test fun unlock_button_invokes_callback() {
        var clicked = false
        c.setContent { LockedScreen(onUnlock = { clicked = true }) }
        c.onNodeWithText("Unlock").performClick()
        assertTrue(clicked)
    }
}
