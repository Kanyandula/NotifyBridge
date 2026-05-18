package com.nyasa.notifybridge.ui.status

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.applock.BiometricAuthenticator
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.ui.theme.Amber
import com.nyasa.notifybridge.ui.theme.ErrorRed
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(nav: NavHostController) {
    val vm: StatusViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val recentItems by vm.recentItems.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    // FLAG_SECURE: prevent screen capture
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Per-row reveal state keyed by stable OutboxItem.id
    val revealedRows = remember { mutableStateMapOf<Long, Boolean>() }

    StatusContent(
        state = state,
        recentItems = recentItems,
        revealedIds = revealedRows.filterValues { it }.keys,
        onRevealRequest = { item ->
            val redact = state.appLock.redactBody
            val isRevealed = revealedRows[item.id] == true
            val activity = context as? FragmentActivity
            if (redact && !isRevealed && activity != null) {
                BiometricAuthenticator(context).prompt(
                    activity = activity,
                    onSuccess = { revealedRows[item.id] = true },
                    onFail = {},
                )
            }
        },
        onNavApps = { nav.navigate("apps") },
        onNavBroker = { nav.navigate("broker") },
        onNavPermissions = { nav.navigate("permissions") },
    )
}

@Composable
private fun StatusContent(
    state: StatusUiState,
    recentItems: List<RecentItem>,
    revealedIds: Set<Long>,
    onRevealRequest: (RecentItem) -> Unit,
    onNavApps: () -> Unit,
    onNavBroker: () -> Unit,
    onNavPermissions: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            StatusBottomNav(
                onStatus = { /* already here */ },
                onApps = onNavApps,
                onBroker = onNavBroker,
                onAccess = onNavPermissions,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NotifyBridge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.Router,
                    contentDescription = "Broker",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection state chip
            val (chipColor, chipLabel) = when (state.connectionState) {
                ConnectionState.CONNECTED -> Teal to "CONNECTED"
                ConnectionState.CONNECTING -> Amber to "CONNECTING"
                ConnectionState.ERROR -> ErrorRed to "ERROR"
                ConnectionState.DISCONNECTED -> ErrorRed to "DISCONNECTED"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(chipColor),
                )
                Text(
                    text = chipLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = chipColor,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val broker = state.brokerConfig
            Text(
                text = "mqtt://${broker.host}:${broker.port} · device: ${broker.deviceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Broker card ─────────────────────────────────────────────────
            SectionCard(title = "BROKER") {
                Text(
                    text = "${broker.host}:${broker.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val tlsText = when (broker.tlsMode) {
                    com.nyasa.notifybridge.domain.model.TlsMode.OFF -> "TLS: Off"
                    com.nyasa.notifybridge.domain.model.TlsMode.SYSTEM_CA -> "TLS: On (system CA)"
                    com.nyasa.notifybridge.domain.model.TlsMode.PINNED -> "TLS: On (pinned cert)"
                }
                Text(
                    text = tlsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "keep-alive: 60s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (state.connectionState == ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Outbox card ─────────────────────────────────────────────────
            SectionCard(title = "OUTBOX") {
                Text(
                    text = "${state.outboxDepth}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.outboxDepth == 0) "queued notifications\nall delivered"
                           else "${state.outboxDepth} queued notification${if (state.outboxDepth == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.outboxDepth == 0) Teal
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "buffers automatically if broker goes away",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Forwarding card ─────────────────────────────────────────────
            SectionCard(title = "FORWARDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.allowListSize} app${if (state.allowListSize == 1) "" else "s"} enabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onNavApps) {
                        Text(
                            text = "MANAGE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Recent Activity ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Last 24h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (recentItems.isEmpty()) {
                Text(
                    text = "No recent notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                recentItems.forEachIndexed { index, item ->
                    val isRevealed = item.id in revealedIds
                    val redact = state.appLock.redactBody

                    RecentRow(
                        item = item,
                        redact = redact,
                        revealed = isRevealed,
                        onClick = { onRevealRequest(item) },
                    )
                    if (index < recentItems.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RecentRow(
    item: RecentItem,
    redact: Boolean,
    revealed: Boolean,
    onClick: () -> Unit,
) {
    val bodyText = displayBody(item.body, redact = redact, revealed = revealed)
    val timeText = remember(item.postTime) {
        if (item.postTime > 0L)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.postTime))
        else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App initial badge
            Spacer(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.app.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = item.title.ifBlank { "(no title)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (bodyText.isNotBlank()) {
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (redact && !revealed)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            if (timeText.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun StatusBottomNav(
    onStatus: () -> Unit,
    onApps: () -> Unit,
    onBroker: () -> Unit,
    onAccess: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = true,
            onClick = onStatus,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Status") },
            label = { Text("Status") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Teal,
                selectedTextColor = Teal,
                indicatorColor = Teal.copy(alpha = 0.15f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )
        NavigationBarItem(
            selected = false,
            onClick = onApps,
            icon = { Icon(Icons.Filled.Apps, contentDescription = "Apps") },
            label = { Text("Apps") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Teal,
                selectedTextColor = Teal,
                indicatorColor = Teal.copy(alpha = 0.15f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )
        NavigationBarItem(
            selected = false,
            onClick = onBroker,
            icon = { Icon(Icons.Filled.Router, contentDescription = "Broker") },
            label = { Text("Broker") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Teal,
                selectedTextColor = Teal,
                indicatorColor = Teal.copy(alpha = 0.15f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )
        NavigationBarItem(
            selected = false,
            onClick = onAccess,
            icon = { Icon(Icons.Filled.Security, contentDescription = "Access") },
            label = { Text("Access") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Teal,
                selectedTextColor = Teal,
                indicatorColor = Teal.copy(alpha = 0.15f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )
    }
}

@Preview(showBackground = true, name = "Status · Connected + activity")
@Composable
private fun StatusConnectedPreview() {
    NotifyBridgeTheme {
        StatusContent(
            state = StatusUiState(
                connectionState = ConnectionState.CONNECTED,
                outboxDepth = 4,
                brokerConfig = BrokerConfig(host = "192.168.1.10"),
                allowListSize = 3,
                appLock = AppLockPrefs(redactBody = false),
            ),
            recentItems = listOf(
                RecentItem(1L, "Signal", "Alice", "Are we still on for 6?", 1_716_000_000_000L),
                RecentItem(2L, "Gmail", "Invoice #1042", "Your receipt is attached", 1_716_000_300_000L),
                RecentItem(3L, "Slack", "#deploys", "build green on main", 1_716_000_600_000L),
            ),
            revealedIds = emptySet(),
            onRevealRequest = {},
            onNavApps = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "Status · Disconnected + empty")
@Composable
private fun StatusDisconnectedPreview() {
    NotifyBridgeTheme {
        StatusContent(
            state = StatusUiState(
                connectionState = ConnectionState.DISCONNECTED,
                outboxDepth = 0,
                brokerConfig = BrokerConfig(host = "192.168.1.10"),
                allowListSize = 0,
                appLock = AppLockPrefs(redactBody = false),
            ),
            recentItems = emptyList(),
            revealedIds = emptySet(),
            onRevealRequest = {},
            onNavApps = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "Status · Redacted recent")
@Composable
private fun StatusRedactedPreview() {
    NotifyBridgeTheme {
        StatusContent(
            state = StatusUiState(
                connectionState = ConnectionState.CONNECTED,
                outboxDepth = 1,
                brokerConfig = BrokerConfig(host = "192.168.1.10"),
                allowListSize = 2,
                appLock = AppLockPrefs(redactBody = true),
            ),
            recentItems = listOf(
                RecentItem(1L, "Bank", "OTP", "Your code is 884213", 1_716_000_000_000L),
                RecentItem(2L, "Signal", "Bob", "see you then", 1_716_000_300_000L),
            ),
            revealedIds = emptySet(),
            onRevealRequest = {},
            onNavApps = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}
