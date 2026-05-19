package com.nyasa.notifybridge.ui.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.ui.theme.Amber
import com.nyasa.notifybridge.ui.theme.ErrorRed
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal

// ── Idle-timeout options ─────────────────────────────────────────────────────
private data class TimeoutOption(val label: String, val ms: Long)

private val timeoutOptions = listOf(
    TimeoutOption("30 seconds", 30_000L),
    TimeoutOption("1 minute", 60_000L),
    TimeoutOption("5 minutes", 300_000L),
    TimeoutOption("15 minutes", 900_000L),
    TimeoutOption("30 minutes", 1_800_000L),
)

private fun labelForMs(ms: Long): String =
    timeoutOptions.firstOrNull { it.ms == ms }?.label ?: "${ms / 1000}s"

// ── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun PermissionsScreen(nav: NavHostController) {
    val vm: PermissionsViewModel = hiltViewModel()
    val notifGranted by vm.notifAccessGranted.collectAsState()
    val batteryExempt by vm.batteryExempt.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Refresh permission state every time the screen resumes (user may have
    // granted access in system Settings and returned).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refresh()
        }
    }

    PermissionsContent(
        notifGranted = notifGranted,
        batteryExempt = batteryExempt,
        appLock = appLock,
        onOpenNotifSettings = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onRequestBatteryExemption = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onLockEnabledChange = vm::setLockEnabled,
        onIdleTimeoutChange = vm::setIdleTimeout,
        onRedactBodyChange = vm::setRedactBody,
        onNavStatus = { nav.navigate("status") },
        onNavApps = { nav.navigate("apps") },
        onNavBroker = { nav.navigate("broker") },
    )
}

@Composable
private fun PermissionsContent(
    notifGranted: Boolean,
    batteryExempt: Boolean,
    appLock: AppLockPrefs,
    onOpenNotifSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onLockEnabledChange: (Boolean) -> Unit,
    onIdleTimeoutChange: (Long) -> Unit,
    onRedactBodyChange: (Boolean) -> Unit,
    onNavStatus: () -> Unit,
    onNavApps: () -> Unit,
    onNavBroker: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PermissionsBottomNav(
                onStatus = onNavStatus,
                onApps = onNavApps,
                onBroker = onNavBroker,
                onAccess = { /* already here */ },
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
            // ── Header ───────────────────────────────────────────────────────
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Card 1: Notification access ──────────────────────────────────
            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "Notification access",
                description = "NotifyBridge can read posted notifications. This is the core permission.",
                pill = permPill(notifGranted),
                actionLabel = "Open settings",
                onAction = onOpenNotifSettings,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Card 2: Battery optimization ─────────────────────────────────
            PermissionCard(
                icon = Icons.Filled.BatteryAlert,
                title = "Battery optimization",
                description = "Android Doze can suspend the bridge while the phone is idle, delaying or dropping notifications. Exempt NotifyBridge to keep it reliable while locked.",
                pill = permPill(batteryExempt),
                actionLabel = "Request exemption",
                onAction = onRequestBatteryExemption,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Card 3: Run at startup (informational, no action) ────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Run at startup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        PillChip(label = "ENABLED", color = Teal)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // §8 caveat copy (verbatim from spec §8 / §2.1):
                    // "BOOT_COMPLETED fires only after the user has opened the app at
                    //  least once — documented as a known gotcha; not a defect."
                    Text(
                        text = "Re-drains buffered notifications after a reboot. " +
                            "Note: on Android 12+, BOOT_COMPLETED fires only after " +
                            "the user has opened the app at least once — " +
                            "documented as a known gotcha; not a defect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── App lock card ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APP LOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Enable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable app lock",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Require biometric / PIN on launch and after idle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = appLock.enabled,
                            onCheckedChange = onLockEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = Teal,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            ),
                        )
                    }

                    if (appLock.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Idle-timeout dropdown
                        IdleTimeoutDropdown(
                            currentMs = appLock.idleTimeoutMs,
                            onSelect = onIdleTimeoutChange,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Redact body toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Require auth to reveal body",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Masks notification text in Recent — auth to reveal per item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = appLock.redactBody,
                                onCheckedChange = onRedactBodyChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = Teal,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── OTP / bank reassurance card ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Notification content can include OTP codes and bank alerts. " +
                            "NotifyBridge only forwards apps you explicitly allow, " +
                            "over your local network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── Reusable permission card ─────────────────────────────────────────────────
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    pill: PermPill,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val pillColor = if (pill == PermPill.GRANTED) Teal else Amber

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = pillColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                PillChip(
                    label = if (pill == PermPill.GRANTED) "GRANTED" else "ACTION NEEDED",
                    color = pillColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Pill chip ────────────────────────────────────────────────────────────────
@Composable
private fun PillChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── Idle timeout dropdown ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleTimeoutDropdown(currentMs: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = labelForMs(currentMs)

    Column {
        Text(
            text = "Lock after idle",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                timeoutOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelect(option.ms)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ── Bottom navigation ────────────────────────────────────────────────────────
@Composable
private fun PermissionsBottomNav(
    onStatus: () -> Unit,
    onApps: () -> Unit,
    onBroker: () -> Unit,
    onAccess: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = false,
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
            selected = true,
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

@Preview(showBackground = true, name = "Permissions · All granted")
@Composable
private fun PermissionsGrantedPreview() {
    NotifyBridgeTheme {
        PermissionsContent(
            notifGranted = true,
            batteryExempt = true,
            appLock = AppLockPrefs(enabled = true, idleTimeoutMs = 60_000L, redactBody = true),
            onOpenNotifSettings = {}, onRequestBatteryExemption = {},
            onLockEnabledChange = {}, onIdleTimeoutChange = {}, onRedactBodyChange = {},
            onNavStatus = {}, onNavApps = {}, onNavBroker = {},
        )
    }
}

@Preview(showBackground = true, name = "Permissions · Action needed")
@Composable
private fun PermissionsActionNeededPreview() {
    NotifyBridgeTheme {
        PermissionsContent(
            notifGranted = false,
            batteryExempt = false,
            appLock = AppLockPrefs(enabled = false),
            onOpenNotifSettings = {}, onRequestBatteryExemption = {},
            onLockEnabledChange = {}, onIdleTimeoutChange = {}, onRedactBodyChange = {},
            onNavStatus = {}, onNavApps = {}, onNavBroker = {},
        )
    }
}
