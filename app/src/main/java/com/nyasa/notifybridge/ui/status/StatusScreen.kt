package com.nyasa.notifybridge.ui.status

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
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
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.appName
import com.nyasa.notifybridge.localization.appsEnabled
import com.nyasa.notifybridge.localization.brokerSummaryLine
import com.nyasa.notifybridge.localization.connected
import com.nyasa.notifybridge.localization.hostPort
import com.nyasa.notifybridge.localization.keepalive60s
import com.nyasa.notifybridge.localization.latest20
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.manageButton
import com.nyasa.notifybridge.localization.navAccess
import com.nyasa.notifybridge.localization.navApps
import com.nyasa.notifybridge.localization.navBroker
import com.nyasa.notifybridge.localization.navStatus
import com.nyasa.notifybridge.localization.noRecent
import com.nyasa.notifybridge.localization.noTitle
import com.nyasa.notifybridge.localization.outboxAllDelivered
import com.nyasa.notifybridge.localization.outboxBufferNote
import com.nyasa.notifybridge.localization.queuedNotifications
import com.nyasa.notifybridge.localization.recentHeading
import com.nyasa.notifybridge.localization.sectionBroker
import com.nyasa.notifybridge.localization.sectionForwarding
import com.nyasa.notifybridge.localization.sectionOutbox
import com.nyasa.notifybridge.localization.stateConnected
import com.nyasa.notifybridge.localization.stateConnecting
import com.nyasa.notifybridge.localization.stateDisconnected
import com.nyasa.notifybridge.localization.stateError
import com.nyasa.notifybridge.localization.tlsOff
import com.nyasa.notifybridge.localization.tlsPinned
import com.nyasa.notifybridge.localization.tlsSystemCa
import com.nyasa.notifybridge.localization.unknownApp
import com.nyasa.notifybridge.ui.theme.Amber
import com.nyasa.notifybridge.ui.theme.ErrorRed
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
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

    // Per-row reveal state keyed by RecentItem.id (the recent_notifications
    // table's autoIncrement ROWID — monotonic, never reused).
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Dictionary.Common.appName.localized(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.Router,
                    contentDescription = Dictionary.Common.navBroker.localized(),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection state chip
            val (chipColor, chipLabel) = when (state.connectionState) {
                ConnectionState.CONNECTED -> Teal to Dictionary.Status.stateConnected.localized()
                ConnectionState.CONNECTING -> Amber to Dictionary.Status.stateConnecting.localized()
                ConnectionState.ERROR -> ErrorRed to Dictionary.Status.stateError.localized()
                ConnectionState.DISCONNECTED -> ErrorRed to Dictionary.Status.stateDisconnected.localized()
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
                text = Dictionary.Status
                    .brokerSummaryLine(host = broker.host, port = broker.port, device = broker.deviceName)
                    .localized(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            SectionCard(title = Dictionary.Status.sectionBroker.localized()) {
                Text(
                    text = Dictionary.Status.hostPort(host = broker.host, port = broker.port).localized(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val tlsText = when (broker.tlsMode) {
                    com.nyasa.notifybridge.domain.model.TlsMode.OFF -> Dictionary.Status.tlsOff.localized()
                    com.nyasa.notifybridge.domain.model.TlsMode.SYSTEM_CA -> Dictionary.Status.tlsSystemCa.localized()
                    com.nyasa.notifybridge.domain.model.TlsMode.PINNED -> Dictionary.Status.tlsPinned.localized()
                }
                Text(
                    text = tlsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = Dictionary.Status.keepalive60s.localized(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                if (state.connectionState == ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Dictionary.Status.connected.localized(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(title = Dictionary.Status.sectionOutbox.localized()) {
                Text(
                    text = NumberFormat.getIntegerInstance(Locale.getDefault()).format(state.outboxDepth),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.outboxDepth == 0) Dictionary.Status.outboxAllDelivered.localized()
                           else Dictionary.Status.queuedNotifications(count = state.outboxDepth).localized(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.outboxDepth == 0) Teal
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Dictionary.Status.outboxBufferNote.localized(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(title = Dictionary.Status.sectionForwarding.localized()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Dictionary.Status.appsEnabled(count = state.allowListSize).localized(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onNavApps) {
                        Text(
                            text = Dictionary.Status.manageButton.localized(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Dictionary.Status.recentHeading.localized(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = Dictionary.Status.latest20.localized(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (recentItems.isEmpty()) {
                Text(
                    text = Dictionary.Status.noRecent.localized(),
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
    val icon = rememberAppIcon(item.packageName)
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
            RecentAppIcon(icon = icon)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.app.ifBlank { Dictionary.Status.unknownApp.localized() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = item.title.ifBlank { Dictionary.Status.noTitle.localized() },
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
private fun rememberAppIcon(packageName: String): Drawable? {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            if (packageName.isBlank()) {
                null
            } else {
                runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            }
        }
    }
    return icon
}

@Composable
private fun RecentAppIcon(icon: Drawable?) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Canvas(modifier = Modifier.size(28.dp)) {
                icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                icon.draw(drawContext.canvas.nativeCanvas)
            }
        } else {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
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
    val statusLabel = Dictionary.Common.navStatus.localized()
    val appsLabel = Dictionary.Common.navApps.localized()
    val brokerLabel = Dictionary.Common.navBroker.localized()
    val accessLabel = Dictionary.Common.navAccess.localized()
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = true,
            onClick = onStatus,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = statusLabel) },
            label = { Text(statusLabel) },
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
            icon = { Icon(Icons.Filled.Apps, contentDescription = appsLabel) },
            label = { Text(appsLabel) },
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
            icon = { Icon(Icons.Filled.Router, contentDescription = brokerLabel) },
            label = { Text(brokerLabel) },
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
            icon = { Icon(Icons.Filled.Security, contentDescription = accessLabel) },
            label = { Text(accessLabel) },
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
                RecentItem(
                    1L,
                    "com.bank",
                    "Bank",
                    "OTP",
                    "Your code is 884213",
                    1_716_000_000_000L,
                ),
                RecentItem(
                    2L,
                    "org.thoughtcrime.securesms",
                    "Signal",
                    "Bob",
                    "see you then",
                    1_716_000_300_000L,
                ),
            ),
            revealedIds = emptySet(),
            onRevealRequest = {},
            onNavApps = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}
