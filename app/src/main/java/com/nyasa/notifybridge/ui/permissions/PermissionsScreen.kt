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
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.LocalLanguage
import com.nyasa.notifybridge.localization.LocalizedString
import com.nyasa.notifybridge.localization.applockEnableDescription
import com.nyasa.notifybridge.localization.applockEnableTitle
import com.nyasa.notifybridge.localization.applockSection
import com.nyasa.notifybridge.localization.batteryButton
import com.nyasa.notifybridge.localization.batteryDescription
import com.nyasa.notifybridge.localization.batteryTitle
import com.nyasa.notifybridge.localization.lockAfterIdle
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.navAccess
import com.nyasa.notifybridge.localization.navApps
import com.nyasa.notifybridge.localization.navBroker
import com.nyasa.notifybridge.localization.navStatus
import com.nyasa.notifybridge.localization.notifAccessDescription
import com.nyasa.notifybridge.localization.languageCardButton
import com.nyasa.notifybridge.localization.languageCardDescription
import com.nyasa.notifybridge.localization.languageCardTitle
import com.nyasa.notifybridge.localization.languageSection
import com.nyasa.notifybridge.localization.notifAccessTitle
import com.nyasa.notifybridge.localization.openSettingsButton
import com.nyasa.notifybridge.localization.systemDefault
import com.nyasa.notifybridge.localization.pillActionNeeded
import com.nyasa.notifybridge.localization.pillEnabled
import com.nyasa.notifybridge.localization.pillGranted
import com.nyasa.notifybridge.localization.redactDescription
import com.nyasa.notifybridge.localization.redactTitle
import com.nyasa.notifybridge.localization.securityNote
import com.nyasa.notifybridge.localization.startupDescription
import com.nyasa.notifybridge.localization.startupTitle
import com.nyasa.notifybridge.localization.timeout15m
import com.nyasa.notifybridge.localization.timeout1m
import com.nyasa.notifybridge.localization.timeout30m
import com.nyasa.notifybridge.localization.timeout30s
import com.nyasa.notifybridge.localization.timeout5m
import com.nyasa.notifybridge.localization.title
import com.nyasa.notifybridge.ui.theme.Amber
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal

private data class TimeoutOption(val label: LocalizedString, val ms: Long)

private val timeoutOptions = listOf(
    TimeoutOption(Dictionary.Permissions.timeout30s, 30_000L),
    TimeoutOption(Dictionary.Permissions.timeout1m, 60_000L),
    TimeoutOption(Dictionary.Permissions.timeout5m, 300_000L),
    TimeoutOption(Dictionary.Permissions.timeout15m, 900_000L),
    TimeoutOption(Dictionary.Permissions.timeout30m, 1_800_000L),
)

@Composable
private fun labelForMs(ms: Long): String =
    timeoutOptions.firstOrNull { it.ms == ms }?.label?.localized() ?: "${ms / 1000}s"

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
        onNavLanguage = { nav.navigate("language") },
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
    onNavLanguage: () -> Unit,
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
            Text(
                text = Dictionary.Permissions.title.localized(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(20.dp))

            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = Dictionary.Permissions.notifAccessTitle.localized(),
                description = Dictionary.Permissions.notifAccessDescription.localized(),
                pill = permPill(notifGranted),
                actionLabel = Dictionary.Permissions.openSettingsButton.localized(),
                onAction = onOpenNotifSettings,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Once exempt, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a no-op
            // (system finish()-es the activity without UI), so the button would
            // look broken. Hide it when already granted; user revokes via system
            // Settings → Battery if they need to.
            PermissionCard(
                icon = Icons.Filled.BatteryAlert,
                title = Dictionary.Permissions.batteryTitle.localized(),
                description = Dictionary.Permissions.batteryDescription.localized(),
                pill = permPill(batteryExempt),
                actionLabel = Dictionary.Permissions.batteryButton.localized(),
                onAction = onRequestBatteryExemption,
                hideActionWhenGranted = true,
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                            text = Dictionary.Permissions.startupTitle.localized(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        PillChip(label = Dictionary.Permissions.pillEnabled.localized(), color = Teal)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // §8 caveat copy (verbatim from spec §8 / §2.1):
                    // "BOOT_COMPLETED fires only after the user has opened the app at
                    //  least once — documented as a known gotcha; not a defect."
                    Text(
                        text = Dictionary.Permissions.startupDescription.localized(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        text = Dictionary.Permissions.applockSection.localized(),
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
                                text = Dictionary.Permissions.applockEnableTitle.localized(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = Dictionary.Permissions.applockEnableDescription.localized(),
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
                                    text = Dictionary.Permissions.redactTitle.localized(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = Dictionary.Permissions.redactDescription.localized(),
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
                        text = Dictionary.Permissions.securityNote.localized(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LanguageEntryCard(onNavLanguage = onNavLanguage)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    pill: PermPill,
    actionLabel: String,
    onAction: () -> Unit,
    hideActionWhenGranted: Boolean = false,
) {
    val pillColor = if (pill == PermPill.GRANTED) Teal else Amber
    val showAction = !(hideActionWhenGranted && pill == PermPill.GRANTED)

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
                    label = if (pill == PermPill.GRANTED)
                        Dictionary.Permissions.pillGranted.localized()
                    else
                        Dictionary.Permissions.pillActionNeeded.localized(),
                    color = pillColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (showAction) {
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
}

@Composable
private fun LanguageEntryCard(onNavLanguage: () -> Unit) {
    val currentTag = LocalLanguage.current.tag
    val nativeName = when (currentTag) {
        "en" -> "English"
        "fr" -> "Français"
        "es" -> "Español"
        "pt" -> "Português"
        else -> Dictionary.Language.systemDefault.localized()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = Dictionary.Permissions.languageSection.localized(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = Dictionary.Permissions.languageCardTitle.localized(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Dictionary.Permissions
                    .languageCardDescription(language = nativeName)
                    .localized(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNavLanguage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text(
                    text = Dictionary.Permissions.languageCardButton.localized(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleTimeoutDropdown(currentMs: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = labelForMs(currentMs)

    Column {
        Text(
            text = Dictionary.Permissions.lockAfterIdle.localized(),
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
                        text = { Text(option.label.localized()) },
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

@Composable
private fun PermissionsBottomNav(
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
            selected = false,
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
            selected = true,
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
            onNavStatus = {}, onNavApps = {}, onNavBroker = {}, onNavLanguage = {},
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
            onNavStatus = {}, onNavApps = {}, onNavBroker = {}, onNavLanguage = {},
        )
    }
}
