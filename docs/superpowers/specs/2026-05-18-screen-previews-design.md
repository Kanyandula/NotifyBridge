# Compose Previews via State Hoisting — Design

Date: 2026-05-18

## Goal

Add `@Preview` coverage for all 6 screens. Today there are **0** `@Preview`
functions in `app/src`. Five of the six screens are coupled to
`hiltViewModel()` + `NavHostController` + side effects, so they cannot be
previewed as written. This design refactors each into a stateless content
composable that previews (and, later, unit tests) can drive.

## Approach

State hoisting. Each screen file is split into three parts, all staying **in
the same file**:

1. **`XxxScreen(nav: NavHostController)`** — thin route wrapper. Keeps
   `hiltViewModel()`, `collectAsState`, every side effect (FLAG_SECURE,
   `LaunchedEffect` navigation, system `Intent`s, biometric prompt, file
   picker, lifecycle refresh) and `LocalContext`. Delegates rendering to
   `XxxContent`. **The `XxxScreen(nav)` signature is unchanged, so
   `NotifyBridgeNavHost` is not touched.**
2. **`XxxContent(state, callbacks…)`** — stateless. No Hilt, no
   `LocalContext`, no `nav`. Holds only pure UI state via `remember`
   (`passwordVisible`, `bannerDismissed`). Pure helpers (`filterApps`,
   counts) and private sub-composables (`SectionCard`, `StepCard`, the bottom
   navigation bars, etc.) stay `private` in the same file and are called by
   `XxxContent`.
3. **`@Preview private fun`s** — render `XxxContent` with local sample data,
   wrapped in `NotifyBridgeTheme { }`, `showBackground = true`. The theme is
   dark-only (`darkColorScheme`), so there is a single scheme — no light
   variant.

`LockedScreen` is already stateless (`onUnlock: () -> Unit`). It only gains a
`@Preview`; no split.

Rejected alternatives: a preview-only seam (awkward with Hilt ViewModels, no
testability gain) and a hybrid that hoists only the cheap screens
(inconsistent).

## Per-screen hoisting

| Screen | `Content` params: state → callbacks | Stays in `Screen` wrapper |
|---|---|---|
| **Locked** | *(already stateless: `onUnlock`)* | — |
| **Onboarding** | `OnboardingUiState` → `onGrantAccess`, `onConfigureBroker`, `onChooseApps` | VM, `LaunchedEffect(DONE → nav)`, the Settings `Intent` |
| **Permissions** | `notifGranted`, `batteryExempt`, `appLock` → `onOpenNotifSettings`, `onRequestBatteryExemption`, `onLockEnabledChange`, `onIdleTimeoutChange`, `onRedactBodyChange` | VM, lifecycle `refresh()`, the two system `Intent`s |
| **Status** | `StatusUiState`, `recentItems`, `revealedIds: Set<Long>` → `onRevealRequest(item)`, `onNavApps`, `onNavBroker`, `onNavPermissions` | VM, FLAG_SECURE, `revealedRows` map + biometric gating logic, navigation |
| **Broker** | `BrokerConfig`, `testResult: String?`, `saving: Boolean` → field setters (`onHostChange`, `onPortChange`, `onDeviceNameChange`, `onUsernameChange`, `onPasswordChange`, `onTlsModeChange`), `onPickCertFile`, `onTest`, `onSave`, `onBack`, `onNavStatus`, `onNavApps`, `onNavPermissions`. `passwordVisible` stays inside `Content`. | VM, FLAG_SECURE, `LaunchedEffect(saveSuccess → start service + popBackStack)`, the file-picker launcher |
| **Apps** | `rows`, `query`, `icons: Map<String, Drawable?>` → `onQueryChange`, `onToggle(pkg, on)`, `onNavStatus`, `onNavBroker`, `onNavPermissions`. `bannerDismissed` stays inside `Content`. | VM, navigation |

### Status reveal detail

`revealedRows` (`mutableStateMapOf<Long, Boolean>`) moves to the wrapper —
same composition scope, so lifetime semantics are unchanged. It is passed down
as `revealedIds: Set<Long>`. `RecentRow`'s click calls
`onRevealRequest(item)`; the wrapper owns the gating
(`redact && !revealed && activity != null` → `BiometricAuthenticator`) and
mutates the map on success. `Content` decides masking from `revealedIds` plus
`state.appLock.redactBody`, which it already receives.

## Preview catalog (~13)

- **Locked** (1): default.
- **Onboarding** (3): step 1 / step 2 / step 3. `DONE` renders no UI (it
  navigates away), so no preview.
- **Permissions** (2): all-granted + lock enabled (idle dropdown + redact
  toggle visible) / action-needed + lock disabled.
- **Status** (3): CONNECTED + populated recent / DISCONNECTED + empty outbox &
  recent / redacted (masked) recent.
- **Broker** (2): empty default (Save disabled) / filled valid + TLS on +
  `testResult = "Connected"`.
- **Apps** (2): populated (mixed enabled/disabled) / empty
  (`"Loading apps…"`).

Sample data is local to each file (inline in the `@Preview` or a
`private fun sampleXxx()`). `Drawable` icons are never constructed: previews
pass `emptyMap()` and the existing `icon == null` fallback glyph renders.

## Out of scope

- **No new unit tests.** Previews only, as requested. The refactor makes every
  `Content` unit-testable — recorded as a follow-up opportunity, not done
  here.
- No behavior change. Side effects and `remember` lifetimes are preserved
  exactly.
- `NotifyBridgeNavHost`, ViewModels, domain, and theme are unchanged.

## Verification

- `./gradlew :app:assembleDebug` compiles clean.
- Each `@Preview` is spot-checked to render in the IDE preview pane. No
  emulator required.
- Diff self-review for scope creep and behavior drift before completion.
