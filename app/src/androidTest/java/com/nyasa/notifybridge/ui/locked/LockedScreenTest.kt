package com.nyasa.notifybridge.ui.locked
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nyasa.notifybridge.localization.AssetJsonLanguage
import com.nyasa.notifybridge.localization.LocalLanguage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class LockedScreenTest {
    @get:Rule val c = createComposeRule()
    @Test fun unlock_button_invokes_callback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val language = AssetJsonLanguage(tag = "en", context = context)
        var clicked = false
        c.setContent {
            CompositionLocalProvider(LocalLanguage provides language) {
                LockedScreen(onUnlock = { clicked = true })
            }
        }
        c.onNodeWithText("Unlock").performClick()
        assertTrue(clicked)
    }
}
