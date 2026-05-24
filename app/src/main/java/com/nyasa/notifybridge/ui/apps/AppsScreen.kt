package com.nyasa.notifybridge.ui.apps

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.appsForwardingSummary
import com.nyasa.notifybridge.localization.bannerMessage
import com.nyasa.notifybridge.localization.clearSearch
import com.nyasa.notifybridge.localization.dismissBanner
import com.nyasa.notifybridge.localization.loading
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.navAccess
import com.nyasa.notifybridge.localization.navApps
import com.nyasa.notifybridge.localization.navBroker
import com.nyasa.notifybridge.localization.navStatus
import com.nyasa.notifybridge.localization.onlySelectedNote
import com.nyasa.notifybridge.localization.searchPlaceholder
import com.nyasa.notifybridge.localization.title
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal

@Composable
fun AppsScreen(nav: NavHostController) {
    val vm: AppsViewModel = hiltViewModel()
    val allRows by vm.rows.collectAsState()
    val query by vm.query.collectAsState()
    val icons by vm.icons.collectAsState()

    AppsContent(
        rows = allRows,
        query = query,
        icons = icons,
        onQueryChange = vm::setQuery,
        onToggle = vm::setEnabled,
        onNavStatus = { nav.navigate("status") },
        onNavBroker = { nav.navigate("broker") },
        onNavPermissions = { nav.navigate("permissions") },
    )
}

@Composable
private fun AppsContent(
    rows: List<AppRow>,
    query: String,
    icons: Map<String, android.graphics.drawable.Drawable?>,
    onQueryChange: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onNavStatus: () -> Unit,
    onNavBroker: () -> Unit,
    onNavPermissions: () -> Unit,
) {
    val displayed = filterApps(rows, query)
    val enabledCount = rows.count { it.enabled }
    val totalCount = rows.size

    var bannerDismissed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Dictionary.Apps.title.localized(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            Dictionary.Apps.searchPlaceholder.localized(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = Dictionary.Apps.clearSearch.localized(),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = Dictionary.Apps
                            .appsForwardingSummary(enabled = enabledCount, total = totalCount)
                            .localized(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Teal,
                    )
                    Text(
                        text = Dictionary.Apps.onlySelectedNote.localized(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                if (!bannerDismissed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(Teal.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Dictionary.Apps.bannerMessage.localized(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { bannerDismissed = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = Dictionary.Apps.dismissBanner.localized(),
                                tint = Teal,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppsBottomNav(
                onStatus = onNavStatus,
                onApps = { /* already here */ },
                onBroker = onNavBroker,
                onAccess = onNavPermissions,
            )
        },
    ) { innerPadding ->
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Dictionary.Apps.loading.localized(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                items(displayed, key = { it.pkg }) { row ->
                    AppRowItem(
                        row = row,
                        icon = icons[row.pkg],
                        onToggle = { on -> onToggle(row.pkg, on) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun AppRowItem(
    row: AppRow,
    icon: android.graphics.drawable.Drawable?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Teal left accent when enabled
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(if (row.enabled) Teal else Color.Transparent),
        )

        Spacer(Modifier.width(10.dp))

        // App icon — drawn via Canvas to avoid needing Coil or accompanist
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    icon.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                    icon.draw(drawContext.canvas.nativeCanvas)
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Label + package name (monospace)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = row.pkg,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }

        // Trailing switch
        Switch(
            checked = row.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Teal,
                checkedTrackColor = Teal.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun AppsBottomNav(
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
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = true,
            onClick = onApps,
            icon = { Icon(Icons.Filled.Apps, contentDescription = appsLabel) },
            label = { Text(appsLabel) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onBroker,
            icon = { Icon(Icons.Filled.Router, contentDescription = brokerLabel) },
            label = { Text(brokerLabel) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onAccess,
            icon = { Icon(Icons.Filled.Security, contentDescription = accessLabel) },
            label = { Text(accessLabel) },
            colors = navItemColors(),
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Teal,
    selectedTextColor = Teal,
    indicatorColor = Teal.copy(alpha = 0.15f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
)

@Preview(showBackground = true, name = "Apps · Populated")
@Composable
private fun AppsPopulatedPreview() {
    NotifyBridgeTheme {
        AppsContent(
            rows = listOf(
                AppRow("Signal", "org.thoughtcrime.securesms", true),
                AppRow("Gmail", "com.google.android.gm", false),
                AppRow("Slack", "com.Slack", true),
                AppRow("WhatsApp", "com.whatsapp", false),
            ),
            query = "",
            icons = emptyMap(),
            onQueryChange = {},
            onToggle = { _, _ -> },
            onNavStatus = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "Apps · Loading/empty")
@Composable
private fun AppsEmptyPreview() {
    NotifyBridgeTheme {
        AppsContent(
            rows = emptyList(),
            query = "",
            icons = emptyMap(),
            onQueryChange = {},
            onToggle = { _, _ -> },
            onNavStatus = {},
            onNavBroker = {},
            onNavPermissions = {},
        )
    }
}
