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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.ui.theme.Teal

@Composable
fun AppsScreen(nav: NavHostController) {
    val vm: AppsViewModel = hiltViewModel()
    val allRows by vm.rows.collectAsState()
    val query by vm.query.collectAsState()

    val displayed = filterApps(allRows, query)
    val enabledCount = allRows.count { it.enabled }
    val totalCount = allRows.size

    var bannerDismissed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                // ── Title bar ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Apps",
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

                // ── Search field ─────────────────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = {
                        Text(
                            "Search apps…",
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
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
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

                // ── "N of M apps forwarding" strip ───────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$enabledCount of $totalCount apps forwarding",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Teal,
                    )
                    Text(
                        text = "Only selected apps are sent.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                // ── Dismissible "Empty by design" banner ─────────────────────
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
                            text = "Empty by design — you opted these in. Add or remove anytime.",
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
                                contentDescription = "Dismiss",
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
                onStatus = { nav.navigate("status") },
                onApps = { /* already here */ },
                onBroker = { nav.navigate("broker") },
                onAccess = { nav.navigate("permissions") },
            )
        },
    ) { innerPadding ->
        if (allRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading apps…",
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
                        onToggle = { on -> vm.setEnabled(row.pkg, on) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── App list row ──────────────────────────────────────────────────────────

@Composable
private fun AppRowItem(row: AppRow, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current

    // Load icon lazily; remembered per package so it's not re-fetched on recompose
    val icon = remember(row.pkg) {
        runCatching { context.packageManager.getApplicationIcon(row.pkg) }.getOrNull()
    }

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

// ── Bottom navigation ─────────────────────────────────────────────────────

@Composable
private fun AppsBottomNav(
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
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = true,
            onClick = onApps,
            icon = { Icon(Icons.Filled.Apps, contentDescription = "Apps") },
            label = { Text("Apps") },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onBroker,
            icon = { Icon(Icons.Filled.Router, contentDescription = "Broker") },
            label = { Text("Broker") },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onAccess,
            icon = { Icon(Icons.Filled.Security, contentDescription = "Access") },
            label = { Text("Access") },
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
