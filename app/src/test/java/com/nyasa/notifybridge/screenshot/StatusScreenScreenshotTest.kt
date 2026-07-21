package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.ui.status.StatusContent
import com.nyasa.notifybridge.ui.status.StatusUiState
import org.junit.Test

/**
 * Screen-level baselines for Status — one per [ConnectionState] chip plus the
 * redacted-recent variant. Fixtures mirror the existing @Preview data so
 * preview and screenshot stay in sync.
 */
class StatusScreenScreenshotTest : ScreenshotTest() {

    private val recents = listOf(
        RecentItem(
            1L,
            "org.thoughtcrime.securesms",
            "Signal",
            "Alice",
            "Are we still on for 6?",
            1_716_000_000_000L,
        ),
        RecentItem(
            2L,
            "com.google.android.gm",
            "Gmail",
            "Invoice #1042",
            "Your receipt is attached",
            1_716_000_300_000L,
        ),
        RecentItem(
            3L,
            "com.Slack",
            "Slack",
            "#deploys",
            "build green on main",
            1_716_000_600_000L,
        ),
    )

    private fun state(cs: ConnectionState, depth: Int = 0, redact: Boolean = false) =
        StatusUiState(
            connectionState = cs,
            outboxDepth = depth,
            brokerConfig = BrokerConfig(host = "192.168.1.10"),
            allowListSize = 3,
            appLock = AppLockPrefs(redactBody = redact),
        )

    private fun shoot(name: String, s: StatusUiState, items: List<RecentItem>, fontScale: Float = 1f) {
        setScreen(fontScale = fontScale) {
            StatusContent(
                state = s,
                recentItems = items,
                revealedIds = emptySet(),
                onRevealRequest = {},
                onNavApps = {},
                onNavBroker = {},
                onNavPermissions = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/StatusScreen_$name.png")
    }

    @Test
    fun connected() = shoot("connected", state(ConnectionState.CONNECTED, depth = 4), recents)

    @Test
    fun connecting() = shoot("connecting", state(ConnectionState.CONNECTING, depth = 2), recents)

    @Test
    fun error() = shoot("error", state(ConnectionState.ERROR), recents)

    @Test
    fun disconnected() = shoot("disconnected", state(ConnectionState.DISCONNECTED), emptyList())

    @Test
    fun redacted() = shoot("redacted", state(ConnectionState.CONNECTED, depth = 1, redact = true), recents)

    @Test
    fun connectedLargeFont() =
        shoot("connected_largeFont", state(ConnectionState.CONNECTED, depth = 4), recents, fontScale = 1.5f)
}
