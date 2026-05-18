# Compose Screen Previews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give all 6 screens `@Preview` coverage by hoisting state out of each screen into a stateless `XxxContent` composable.

**Architecture:** Each screen file splits into a thin `XxxScreen(nav)` route wrapper (keeps Hilt VM + all side effects) and a stateless `XxxContent(state, callbacks)` that previews render. `XxxScreen(nav)` signatures are unchanged, so `NotifyBridgeNavHost` is not touched. No behavior change.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, `androidx.compose.ui:ui-tooling-preview` (already a dependency).

**Spec:** `docs/superpowers/specs/2026-05-18-screen-previews-design.md`

---

## Conventions for every task

- **Verification is compilation, not unit tests.** The spec explicitly scopes
  unit tests out (previews only). Per-task gate: `./gradlew :app:compileDebugKotlin`
  prints `BUILD SUCCESSFUL`. Visual confirmation is done by the human opening
  the IDE preview pane; the agent cannot do this from the CLI.
- **Refactor rule (applies to every screen task):** Move the existing render
  body (`Scaffold { … }` / `Column { … }`) **verbatim** from `XxxScreen` into
  the new `XxxContent`. Do not change layout, modifiers, styling, copy, or
  private sub-composables. The *only* edits inside the moved body are the
  exact substitutions listed in that task's table (each `vm.…` / `nav.…` /
  `context.…` / `filePicker.…` call → a hoisted lambda parameter). Top-level
  helpers (`isValid`, `certError`, `permPill`, `displayBody`, `filterApps`,
  `toggle`) and `private` sub-composables stay where they are and remain
  callable from `XxxContent`.
- **Preview imports** to add in each file:
  `import androidx.compose.ui.tooling.preview.Preview`
  `import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme`
- Every preview is `private`, wrapped in `NotifyBridgeTheme { … }`, annotated
  `@Preview(showBackground = true, name = "…")`. Theme is dark-only — one scheme.

## Preconditions

`app/.../ui/apps/AppsScreen.kt` had an uncommitted external (IDE) modification
at planning time. **Before Task 4**, run `git status --porcelain` and
`git diff app/src/main/java/com/nyasa/notifybridge/ui/apps/AppsScreen.kt`. If
non-empty and not from this plan, stop and reconcile with the user before
editing that file.

## Domain model constructors (verified — use for sample data)

```kotlin
// com.nyasa.notifybridge.domain.model
enum class TlsMode { OFF, SYSTEM_CA, PINNED }
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
data class BrokerConfig(
    val host: String = "", val port: Int = 1883, val deviceName: String = "phone",
    val username: String? = null, val password: String? = null,
    val tlsMode: TlsMode = TlsMode.OFF, val pinnedCertPem: String? = null,
)
data class AppLockPrefs(
    val enabled: Boolean = true, val idleTimeoutMs: Long = 60_000L,
    val redactBody: Boolean = true,
)
```
Local UI data classes: `RecentItem(id: Long, app: String, title: String, body: String, postTime: Long)` (in `StatusViewModel.kt`), `AppRow(label: String, pkg: String, enabled: Boolean)` (in `AppsViewModel.kt`), `StatusUiState(connectionState, outboxDepth, brokerConfig, allowListSize, appLock)` (all defaulted), `OnboardingUiState(activeStep: OnboardingStep)`, `OnboardingStep { GRANT_ACCESS, CONNECT_BROKER, CHOOSE_APPS, DONE }`.

---

### Task 1: LockedScreen — add preview only

`LockedScreen(onUnlock: () -> Unit)` is already stateless. No split.

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/locked/LockedScreen.kt`

- [ ] **Step 1: Add preview imports**

Add to the import block:
```kotlin
import androidx.compose.ui.tooling.preview.Preview
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
```

- [ ] **Step 2: Append the preview** (end of file, after `LockedScreen`)

```kotlin
@Preview(showBackground = true, name = "Locked")
@Composable
private fun LockedScreenPreview() {
    NotifyBridgeTheme {
        LockedScreen(onUnlock = {})
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/locked/LockedScreen.kt
git commit -m "LockedScreen: add Compose preview"
```

---

### Task 2: OnboardingScreen — hoist + 3 previews

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Add preview imports** (see Conventions).

- [ ] **Step 2: Replace `OnboardingScreen` with the thin wrapper**

Replace the whole `fun OnboardingScreen(nav: NavHostController) { … }`
(the function body that today contains the `LaunchedEffect` + the
`Column { … }`) with exactly:

```kotlin
@Composable
fun OnboardingScreen(nav: NavHostController) {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.activeStep) {
        if (state.activeStep == OnboardingStep.DONE) {
            nav.navigate("status") {
                popUpTo("onboarding") { inclusive = true }
            }
        }
    }

    OnboardingContent(
        state = state,
        onGrantAccess = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onConfigureBroker = { nav.navigate("broker") },
        onChooseApps = { nav.navigate("apps") },
    )
}
```

- [ ] **Step 3: Add `OnboardingContent`** (immediately below the wrapper)

Create `OnboardingContent` whose body is the **moved-verbatim**
`Column( … ) { … }` block from the old `OnboardingScreen` (the block starting
`Column(modifier = Modifier.fillMaxSize().background(…)` through its closing
brace), with these substitutions applied inside it:

| Old call in body | Replace with |
|---|---|
| Step 1 `onClick = { context.startActivity( Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)… ) }` | `onClick = onGrantAccess` |
| Step 2 `onClick = { nav.navigate("broker") }` | `onClick = onConfigureBroker` |
| Step 3 `onClick = { nav.navigate("apps") }` | `onClick = onChooseApps` |

`state.activeStep` reads stay as-is (`OnboardingContent` receives `state`).

```kotlin
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onGrantAccess: () -> Unit,
    onConfigureBroker: () -> Unit,
    onChooseApps: () -> Unit,
) {
    // … moved-verbatim Column { … } body with the 3 substitutions above …
}
```

`StepCard` stays `private` and unchanged.

- [ ] **Step 4: Append the 3 previews**

```kotlin
@Preview(showBackground = true, name = "Onboarding · Step 1")
@Composable
private fun OnboardingStep1Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.GRANT_ACCESS),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding · Step 2")
@Composable
private fun OnboardingStep2Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.CONNECT_BROKER),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding · Step 3")
@Composable
private fun OnboardingStep3Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.CHOOSE_APPS),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}
```

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/onboarding/OnboardingScreen.kt
git commit -m "OnboardingScreen: hoist to OnboardingContent + previews"
```

---

### Task 3: PermissionsScreen — hoist + 2 previews

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/permissions/PermissionsScreen.kt`

- [ ] **Step 1: Add preview imports** (see Conventions).

- [ ] **Step 2: Replace `PermissionsScreen` with the thin wrapper**

```kotlin
@Composable
fun PermissionsScreen(nav: NavHostController) {
    val vm: PermissionsViewModel = hiltViewModel()
    val notifGranted by vm.notifAccessGranted.collectAsState()
    val batteryExempt by vm.batteryExempt.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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
    )
}
```

- [ ] **Step 3: Add `PermissionsContent`**

Body = moved-verbatim `Scaffold( … ) { … }` block from the old
`PermissionsScreen`, with these substitutions:

| Old call in body | Replace with |
|---|---|
| Card 1 `onAction = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)…) }` | `onAction = onOpenNotifSettings` |
| Card 2 `onAction = { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)…) }` | `onAction = onRequestBatteryExemption` |
| `onCheckedChange = { vm.setLockEnabled(it) }` | `onCheckedChange = onLockEnabledChange` |
| `IdleTimeoutDropdown(currentMs = appLock.idleTimeoutMs, onSelect = { vm.setIdleTimeout(it) })` | `IdleTimeoutDropdown(currentMs = appLock.idleTimeoutMs, onSelect = onIdleTimeoutChange)` |
| `onCheckedChange = { vm.setRedactBody(it) }` | `onCheckedChange = onRedactBodyChange` |

`notifGranted` / `batteryExempt` / `appLock` reads stay as-is.

```kotlin
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
) {
    // … moved-verbatim Scaffold { … } body with the 5 substitutions above …
}
```

Add import `import com.nyasa.notifybridge.domain.model.AppLockPrefs`.
`PermissionCard`, `PillChip`, `IdleTimeoutDropdown` (keeps its own
`@OptIn(ExperimentalMaterial3Api::class)`), `PermissionsBottomNav` stay
`private` and unchanged. `permPill(...)` (top-level in `PermissionsViewModel.kt`)
stays callable.

- [ ] **Step 4: Append the 2 previews**

```kotlin
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
        )
    }
}
```

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/permissions/PermissionsScreen.kt
git commit -m "PermissionsScreen: hoist to PermissionsContent + previews"
```

---

### Task 4: AppsScreen — hoist + 2 previews

**Run the Preconditions check first** (external IDE edit on this file).

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/apps/AppsScreen.kt`

- [ ] **Step 1: Add preview imports** (see Conventions).

- [ ] **Step 2: Replace `AppsScreen` with the thin wrapper**

```kotlin
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
```

- [ ] **Step 3: Add `AppsContent`**

Body = moved-verbatim block from the old `AppsScreen` starting at
`val displayed = filterApps(allRows, query)` through the end of the
`Scaffold( … ) { … }` (this keeps `displayed`, `enabledCount`, `totalCount`,
and the `bannerDismissed` `remember` **inside** `AppsContent`). Substitutions:

| Old call in body | Replace with |
|---|---|
| `onValueChange = vm::setQuery` (search field) | `onValueChange = onQueryChange` |
| `IconButton(onClick = { vm.setQuery("") })` (clear search) | `IconButton(onClick = { onQueryChange("") })` |
| `AppRowItem(... onToggle = { on -> vm.setEnabled(row.pkg, on) })` | `AppRowItem(... onToggle = { on -> onToggle(row.pkg, on) })` |
| `AppsBottomNav(onStatus = { nav.navigate("status") }, onApps = { /* already here */ }, onBroker = { nav.navigate("broker") }, onAccess = { nav.navigate("permissions") })` | `AppsBottomNav(onStatus = onNavStatus, onApps = { /* already here */ }, onBroker = onNavBroker, onAccess = onNavPermissions)` |

```kotlin
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
    // … moved-verbatim Scaffold { … } body with the substitutions above …
    // (the moved body no longer redeclares displayed/enabledCount/totalCount/
    //  bannerDismissed — those are the four lines declared just above)
}
```

`AppRowItem`, `AppsBottomNav`, `navItemColors` stay `private` and unchanged.
The `icons` param type stays fully-qualified (matches existing FQN style in
this file) — no new import.

- [ ] **Step 4: Append the 2 previews**

```kotlin
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
            onQueryChange = {}, onToggle = { _, _ -> },
            onNavStatus = {}, onNavBroker = {}, onNavPermissions = {},
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
            onQueryChange = {}, onToggle = { _, _ -> },
            onNavStatus = {}, onNavBroker = {}, onNavPermissions = {},
        )
    }
}
```

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/apps/AppsScreen.kt
git commit -m "AppsScreen: hoist to AppsContent + previews"
```

---

### Task 5: StatusScreen — hoist + 3 previews

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/status/StatusScreen.kt`

- [ ] **Step 1: Add preview imports** (see Conventions).

- [ ] **Step 2: Replace `StatusScreen` with the thin wrapper**

The reveal map and biometric gating move here. `revealedRows` keeps the same
composition scope (still a `remember` in the screen's composition).

```kotlin
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
```

- [ ] **Step 3: Add `StatusContent`**

Body = moved-verbatim `Scaffold( … ) { … }` block from the old
`StatusScreen`. Substitutions inside the moved body:

| Old code in body | Replace with |
|---|---|
| `StatusBottomNav(onStatus = { /* already here */ }, onApps = { nav.navigate("apps") }, onBroker = { nav.navigate("broker") }, onAccess = { nav.navigate("permissions") })` | `StatusBottomNav(onStatus = { /* already here */ }, onApps = onNavApps, onBroker = onNavBroker, onAccess = onNavPermissions)` |
| `TextButton(onClick = { nav.navigate("apps") })` (FORWARDING "MANAGE") | `TextButton(onClick = onNavApps)` |
| In the `recentItems.forEachIndexed { index, item -> … }` loop: the lines `val isRevealed = revealedRows[item.id] == true`, `val redact = state.appLock.redactBody`, `val activity = context as? FragmentActivity`, and the `RecentRow(... onClick = { if (redact && !isRevealed && activity != null) { BiometricAuthenticator(context).prompt(...) } })` | `val isRevealed = item.id in revealedIds` `val redact = state.appLock.redactBody` `RecentRow(item = item, redact = redact, revealed = isRevealed, onClick = { onRevealRequest(item) })` — delete the `activity` line and the inline `BiometricAuthenticator` call |

```kotlin
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
    // … moved-verbatim Scaffold { … } body with the substitutions above …
}
```

`SectionCard`, `RecentRow`, `StatusBottomNav` stay `private` and unchanged.
`displayBody(...)` (top-level in `StatusViewModel.kt`) stays callable.
The old `StatusScreen` imports for `Activity`, `WindowManager`,
`BiometricAuthenticator`, `FragmentActivity`, `LocalView` remain (still used
by the wrapper). Add import
`import com.nyasa.notifybridge.domain.model.AppLockPrefs` (used by previews).

- [ ] **Step 4: Append the 3 previews**

```kotlin
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
            onRevealRequest = {}, onNavApps = {}, onNavBroker = {}, onNavPermissions = {},
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
            onRevealRequest = {}, onNavApps = {}, onNavBroker = {}, onNavPermissions = {},
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
            onRevealRequest = {}, onNavApps = {}, onNavBroker = {}, onNavPermissions = {},
        )
    }
}
```

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/status/StatusScreen.kt
git commit -m "StatusScreen: hoist to StatusContent + previews"
```

---

### Task 6: BrokerScreen — hoist + 2 previews

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/ui/broker/BrokerScreen.kt`

- [ ] **Step 1: Add preview imports** (see Conventions).

- [ ] **Step 2: Replace `BrokerScreen` with the thin wrapper**

Keep `@OptIn(ExperimentalMaterial3Api::class)` — move it onto
`BrokerContent` (Step 3); the wrapper itself no longer needs it.

```kotlin
@Composable
fun BrokerScreen(nav: NavHostController) {
    val vm: BrokerViewModel = hiltViewModel()
    val config by vm.config.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val saving by vm.saving.collectAsState()
    val saveSuccess by vm.saveSuccess.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            context.startForegroundService(
                Intent(context, MqttForegroundService::class.java)
            )
            vm.consumeSaveSuccess()
            nav.popBackStack()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val pem = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
            vm.updatePinnedCertPem(pem)
        }
    }

    BrokerContent(
        config = config,
        testResult = testResult,
        saving = saving,
        onHostChange = vm::updateHost,
        onPortChange = vm::updatePort,
        onDeviceNameChange = vm::updateDeviceName,
        onUsernameChange = vm::updateUsername,
        onPasswordChange = vm::updatePassword,
        onTlsModeChange = vm::updateTlsMode,
        onPickCertFile = {
            filePicker.launch(arrayOf("application/x-pem-file", "text/plain"))
        },
        onTest = vm::test,
        onSave = vm::save,
        onBack = { nav.popBackStack() },
        onNavStatus = { nav.navigate("status") },
        onNavApps = { nav.navigate("apps") },
        onNavPermissions = { nav.navigate("permissions") },
    )
}
```

- [ ] **Step 3: Add `BrokerContent`**

Body = moved-verbatim `Scaffold( … ) { … }` block from the old
`BrokerScreen` (this keeps `var passwordVisible by remember { … }` and
`val tlsEnabled = config.tlsMode != TlsMode.OFF` **inside** `BrokerContent`,
declared at the top of it before the `Scaffold`). Substitutions:

| Old code in body | Replace with |
|---|---|
| `IconButton(onClick = { nav.popBackStack() })` (topBar back) | `IconButton(onClick = onBack)` |
| `OutlinedButton(onClick = { vm.test() }, …)` | `OutlinedButton(onClick = onTest, …)` |
| `Button(onClick = { vm.save() }, … enabled = isValid(config) && !saving, …)` | `Button(onClick = onSave, … enabled = isValid(config) && !saving, …)` |
| `BrokerBottomNav(onStatus = { nav.navigate("status") }, onApps = { nav.navigate("apps") }, onBroker = { /* already here */ }, onAccess = { nav.navigate("permissions") })` | `BrokerBottomNav(onStatus = onNavStatus, onApps = onNavApps, onBroker = { /* already here */ }, onAccess = onNavPermissions)` |
| `MonoTextField(label = "Host", … onValueChange = vm::updateHost, …)` | `onValueChange = onHostChange` |
| `MonoTextField(label = "Port", … onValueChange = vm::updatePort, …)` | `onValueChange = onPortChange` |
| `MonoTextField(label = "Device name", … onValueChange = vm::updateDeviceName, …)` | `onValueChange = onDeviceNameChange` |
| `MonoTextField(label = "Username (optional)", … onValueChange = vm::updateUsername, …)` | `onValueChange = onUsernameChange` |
| password `OutlinedTextField(... onValueChange = vm::updatePassword, ...)` | `onValueChange = onPasswordChange` |
| `Switch(checked = tlsEnabled, onCheckedChange = { on -> vm.updateTlsMode(if (on) TlsMode.SYSTEM_CA else TlsMode.OFF) }, …)` | `onCheckedChange = { on -> onTlsModeChange(if (on) TlsMode.SYSTEM_CA else TlsMode.OFF) }` |
| System-CA `Button(onClick = { vm.updateTlsMode(TlsMode.SYSTEM_CA) }, …)` | `onClick = { onTlsModeChange(TlsMode.SYSTEM_CA) }` |
| `TextButton(onClick = { filePicker.launch(arrayOf("application/x-pem-file", "text/plain")) })` | `TextButton(onClick = onPickCertFile)` |

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrokerContent(
    config: BrokerConfig,
    testResult: String?,
    saving: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTlsModeChange: (TlsMode) -> Unit,
    onPickCertFile: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onNavStatus: () -> Unit,
    onNavApps: () -> Unit,
    onNavPermissions: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val tlsEnabled = config.tlsMode != TlsMode.OFF
    // … moved-verbatim Scaffold { … } body with the substitutions above …
    // (the moved body no longer redeclares passwordVisible / tlsEnabled)
}
```

`FormSection`, `MonoTextField`, `BrokerBottomNav`, `navItemColors` stay
`private` and unchanged. `isValid(...)` / `certError(...)` (top-level in
`BrokerViewModel.kt`) stay callable. Add import
`import com.nyasa.notifybridge.domain.model.BrokerConfig` (used by previews;
`TlsMode` is already imported).

- [ ] **Step 4: Append the 2 previews**

```kotlin
@Preview(showBackground = true, name = "Broker · Empty")
@Composable
private fun BrokerEmptyPreview() {
    NotifyBridgeTheme {
        BrokerContent(
            config = BrokerConfig(),
            testResult = null,
            saving = false,
            onHostChange = {}, onPortChange = {}, onDeviceNameChange = {},
            onUsernameChange = {}, onPasswordChange = {}, onTlsModeChange = {},
            onPickCertFile = {}, onTest = {}, onSave = {}, onBack = {},
            onNavStatus = {}, onNavApps = {}, onNavPermissions = {},
        )
    }
}

@Preview(showBackground = true, name = "Broker · Filled + TLS + connected")
@Composable
private fun BrokerFilledPreview() {
    NotifyBridgeTheme {
        BrokerContent(
            config = BrokerConfig(
                host = "192.168.1.10",
                port = 1883,
                deviceName = "phone",
                tlsMode = TlsMode.SYSTEM_CA,
            ),
            testResult = "Connected",
            saving = false,
            onHostChange = {}, onPortChange = {}, onDeviceNameChange = {},
            onUsernameChange = {}, onPasswordChange = {}, onTlsModeChange = {},
            onPickCertFile = {}, onTest = {}, onSave = {}, onBack = {},
            onNavStatus = {}, onNavApps = {}, onNavPermissions = {},
        )
    }
}
```

- [ ] **Step 5: Compile** — `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/ui/broker/BrokerScreen.kt
git commit -m "BrokerScreen: hoist to BrokerContent + previews"
```

---

### Task 7: Full build + diff self-review

**Files:** none (verification only).

- [ ] **Step 1: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (`debugImplementation(libs.compose.tooling)` is
present, so previews resolve.)

- [ ] **Step 2: Confirm preview count**

Run: `grep -rc "@Preview" app/src/main/java/com/nyasa/notifybridge/ui`
Expected total across the 6 screen files: **13**
(Locked 1, Onboarding 3, Permissions 2, Apps 2, Status 3, Broker 2).

- [ ] **Step 3: Diff self-review**

Run: `git diff main -- app/src/main/java/com/nyasa/notifybridge/ui`
Check: no layout/style/copy changes inside moved bodies; only the documented
substitutions + new wrappers/Content/previews; no `NotifyBridgeNavHost`,
ViewModel, domain, or theme edits; no scope creep; no unrelated AppsScreen
changes bundled.

- [ ] **Step 4: Human visual check (hand back)**

Ask the human to open the IDE preview pane on each screen file and confirm the
13 previews render. CLI cannot do this.

---

## Self-Review (plan vs spec)

- **Spec coverage:** State-hoisting pattern → every screen task. `LockedScreen`
  preview-only → Task 1. Per-screen hoisting table → Tasks 2–6 (signatures &
  substitutions match the spec's param lists, incl. Status reveal detail and
  Broker `passwordVisible` staying in Content). Preview catalog (13: 1/3/2/3/2/2)
  → Tasks 1–6 + Task 7 Step 2 count. "No new tests" → Conventions (compile-only
  gate). "No behavior change / NavHost untouched" → Task 7 Step 3. Verification
  (`assembleDebug` + diff review) → Task 7.
- **Placeholder scan:** none — every code step has complete code; the
  deliberately-not-duplicated content is the *unchanged* moved body, fully
  pinned by an exhaustive substitution table per task.
- **Type consistency:** `XxxContent` signatures are defined once per task and
  reused verbatim in that task's previews; param names/types match the wrapper
  call sites; domain constructors match the verified definitions above.
