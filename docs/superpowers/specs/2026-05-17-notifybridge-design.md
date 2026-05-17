# NotifyBridge — Design Spec

**Date:** 2026-05-17
**Status:** Draft (rev. 4 — review C1/W1–W3/S1–S5 applied; UI reference added; app-lock & on-device privacy added) — awaiting user approval
**Author:** Ephraim Kanyandula

## 1. Purpose

A standalone Android app that captures notifications posted on a phone and
publishes them to a local MQTT broker (Mosquitto) for consumption by Home
Assistant. It exists to do reliably what the Home Assistant Companion app's
"Last Notification" sensor does unreliably:

- **No lost notifications.** Rapid, successive notifications are all delivered
  (the Companion sensor keeps only the most recent).
- **Works while locked / dozing.** A foreground service keeps the MQTT
  connection alive (the Companion sensor frequently stops updating when the
  phone is locked).
- **Structured, per-app payloads** with full title/body, including messaging
  apps (the Companion sensor often degrades to "3 new messages").
- **Local-first, zero cloud.** Publishes straight to the user's existing
  Mosquitto broker. No Firebase, no Google round-trip — consistent with the
  target user's local-first Home Assistant infrastructure.

Non-goals: bidirectional control, notification actions/replies, media
controls, multi-broker fan-out, iOS. These are explicitly out of scope.

## 2. Identity & Environment

| Aspect | Decision |
|---|---|
| Project location | `~/AndroidStudioProjects/NotifyBridge` (own git repo) |
| Package | `com.nyasa.notifybridge` |
| Language / UI | Kotlin, Jetpack Compose, no XML |
| DI | Hilt |
| `minSdk` | 26 (Android 8 — required for channel metadata) |
| `targetSdk` | 35 |
| Relationship to roborock-s5-valetudo repo | None. Separate project; that repo is documentation-only and belongs to a different effort. |

## 2.1 Manifest & Permissions

| Permission / declaration | Why | Notes |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Bind the `NotificationListenerService` | Granted via system Settings, not a runtime dialog |
| `FOREGROUND_SERVICE` | Run `MqttForegroundService` | |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required for the chosen FGS type on API 34+ | See §3.3 / §8 |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` re-drains the outbox after reboot | Since Android 12, `BOOT_COMPLETED` only fires after the user has launched the app at least once — documented as a known gotcha; not a bug |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt Doze exemption so the service survives | User-grantable via the Permissions screen |
| `INTERNET` | MQTT socket to the broker | |
| `USE_BIOMETRIC` | Local app-lock prompt (§3.8) | Falls back to device PIN/pattern via `KeyguardManager` where biometric is unavailable |

`MqttForegroundService` declares
`android:foregroundServiceType="connectedDevice"` (see §3.3).

## 3. Architecture

Layered, Hilt-wired:

```
ui/        Compose screens + ViewModels
domain/    Models, repository interfaces, use cases
data/      Room (outbox), DataStore (settings), MqttClientManager, NotificationMapper
service/   NotifListenerService, MqttForegroundService, BootReceiver
```

### 3.1 Capture — `NotifListenerService`

Extends `NotificationListenerService`. On `onNotificationPosted(sbn)`:

1. **Filter:** drop if package not in the allow-list, or it is
   NotifyBridge's own foreground notification, or (configurable) the Home
   Assistant Companion app's notifications.
2. **Deduplicate (S5):** key each notification by
   `(package, tag, id)`. If a notification with the same key was published
   in the last 500 ms with identical title+body, drop it. `isOngoing`
   notifications (progress bars, downloads) are dropped on *content-unchanged*
   updates so a single download cannot flood the outbox with hundreds of
   ticks. The first post and any content change still pass.
3. **Resolve fields:** package, app label (PackageManager), title
   (`EXTRA_TITLE`), body via the priority chain below, subText, category,
   `postTime`, channel id, `isOngoing`, `isClearable`.
4. **Map** to `CapturedNotification`.
5. **Enqueue** into the Room outbox.
6. **Signal** the foreground service to drain the outbox.

**Body extraction priority chain (S1):**
`MessagingStyle` latest message text →
`EXTRA_BIG_TEXT` → `EXTRA_TEXT` → `null`.
Only the **latest** message of a `MessagingStyle` notification is taken (not
the whole coalesced thread) — the bridge mirrors "what just arrived", and the
full thread is recoverable from the source app. Sender name, when present in
the latest `MessagingStyle` message, is prefixed to the body
(`"<sender>: <text>"`).

Enqueue is the **only** synchronous critical step. Parsing or publish
failures must never crash or block the listener callback.

`onListenerDisconnected` / permission revocation updates app state so the UI
reflects "notification access lost" and publishing stops.

**`CapturedNotification` domain model (S2):**

| Field | Type | Source |
|---|---|---|
| `packageName` | `String` | `sbn.packageName` |
| `appLabel` | `String` | PackageManager (falls back to `packageName`) |
| `title` | `String?` | `EXTRA_TITLE` |
| `body` | `String?` | Body priority chain above |
| `subText` | `String?` | `EXTRA_SUB_TEXT` |
| `category` | `String?` | `Notification.category` |
| `channelId` | `String?` | `Notification.channelId` |
| `postTime` | `Long` | `sbn.postTime` (epoch ms) |
| `isOngoing` | `Boolean` | `sbn.isOngoing` |
| `isClearable` | `Boolean` | `sbn.isClearable` |
| `dedupeKey` | `String` | `"$packageName|${sbn.tag}|${sbn.id}"` (internal; not published) |

### 3.2 Buffer — Room outbox

`OutboxEntity(id, topic, jsonPayload, createdAt, attemptCount, status)`.

- Successful publish → row deleted.
- Bounded: rows older than 7 days pruned; hard cap of 5,000 rows, oldest
  pruned first. Prevents unbounded growth if the broker is long-unreachable.
- `attemptCount` drives exponential backoff for redelivery.
- Survives process death/reboot: `BootReceiver` re-drains on boot; a
  **15-minute** periodic WorkManager job (the minimum WorkManager allows) is
  a *safety net only*. The primary, low-latency drain path is the foreground
  service draining on connect and on every enqueue — the periodic job exists
  solely to recover if the service was killed and not yet restarted.

### 3.3 Transport — `MqttClientManager` + `MqttForegroundService`

`MqttClientManager` wraps the **HiveMQ MQTT client** behind a domain
interface (so it can be faked in tests). `MqttForegroundService` owns the
connection lifecycle and shows a persistent low-importance notification
("NotifyBridge active") — this is the accepted cost of working while locked.
It runs as `foregroundServiceType="connectedDevice"` (firm decision — see
§8); the persistent broker socket is the "connected network device".

- Configurable: host, port, TLS on/off, optional username/password, client
  id, keep-alive interval.
- **TLS cert handling (S4):** TLS validates against the system CA store by
  default. A separate **"trust self-signed / pinned cert"** option is
  provided for local Mosquitto deployments with a self-signed cert (the
  common HA setup) — implemented as user-supplied CA/cert pinning, *not* a
  blanket trust-all, so a misconfigured toggle can't silently downgrade to
  no validation.
- Auto-reconnect with exponential backoff.
- Publishes at QoS 1.
- **Last Will & Testament** on `notifybridge/<device>/status` →
  `offline`; on connect publishes `online` (retained) to the same topic.
- Drains the outbox on connect and whenever a new item is enqueued.

### 3.4 Home Assistant MQTT Discovery

On every successful connect, publish a **retained** discovery config to:

```
homeassistant/sensor/notifybridge_<device>/config
```

Payload defines a sensor:

- `state_topic`: `notifybridge/<device>/notification` — latest notification
  text (truncated to a safe length for the HA state string).
- `json_attributes_topic`: same topic carries a JSON object exposing
  `title`, `text`, `app`, `package`, `category`, `post_time`.
- `availability_topic`: `notifybridge/<device>/status` (the LWT topic) so
  HA shows the bridge online/offline.

Result: one auto-created HA entity, zero YAML. The state topic doubles as
the raw event feed for the user's own automations.

`<device>` is a user-set device name from settings, slugified for topic
safety.

**Topic scheme (S3):** *all* allow-listed apps publish to the single
`notifybridge/<device>/notification` topic. HA automations discriminate by
the `package` / `app` JSON attribute. Per-app topic routing
(`.../notification/<package>`) is an explicit **non-goal** for v1 — it would
multiply discovery entities and complicate the HA side for no current
requirement; revisit only if a concrete need appears.

### 3.5 UI (Compose, single-activity, Hilt ViewModels)

- **Setup:** broker host/port, TLS toggle, username/password, device name,
  "Test Connection" button, live connection status + last error.
- **Permissions:** detects notification-access grant; button deep-links to
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`; also requests
  battery-optimization exemption so Doze does not kill the service. This
  screen also hosts the **App lock** card (§3.8) — enable/disable, idle
  timeout, "require auth to reveal notification body". No separate Settings
  screen exists; Permissions is the single security hub.
- **Allow-list:** list of installed launchable apps with per-app toggles.
  **Default is empty — nothing is forwarded until the user opts apps in**
  (deliberate: avoids forwarding OTP/banking content by default).
- **Diagnostics:** connection state, outbox depth, recently forwarded items.
  Recent items show app + title only; the **body is redacted by default**
  and revealed per-item after an auth challenge (§3.8). The published MQTT
  payload is never redacted — this is a UI-only protection.

Settings persisted via DataStore (Preferences). Allow-list persisted via
DataStore (string set of package names).

### 3.6 Onboarding / First-run (W1)

The privacy-safe empty default must not read as "broken". First launch shows
a guided setup checklist with three ordered, dismissable steps, each with a
clear call-to-action:

1. **Grant notification access** → deep-links to system Settings.
2. **Configure broker** → Setup screen; "Test Connection" must pass.
3. **Choose apps to forward** → Allow-list screen.

Until step 3 is satisfied, the Diagnostics/home screen shows an explicit
empty state: *"No apps selected yet — nothing is being forwarded. Tap to
choose which apps to forward."* with a button into the allow-list. This makes
the do-nothing default obviously intentional rather than a failure.

### 3.7 UI reference (Stitch)

Visual design lives in Stitch (design system **NotifyBridge Dark**: dark
near-black surfaces, single teal accent, Space Grotesk / Inter / monospace
for technical values, Material 3, 12px radius). It is *reference*, not a
contract — §3.5/§3.6 remain authoritative if the two diverge.

Stitch project: `6833643636006727286`.

| Screen | Stitch screen ID | Realises |
|---|---|---|
| Onboarding / first-run | `8adb31430c394a8cbe7b5a10e492a71b` | §3.6 — 3-step gated checklist, "no cloud" footer |
| Dashboard / Status | `b706f67ff307452fa027d44711ebdf3d` | §3.5 Diagnostics — connection chip, Broker/Outbox/Forwarding cards, Recent list |
| Broker Setup | `59dfbc89acfd4e3b8016f034cbee817b` | §3.5 Setup + §3.3 S4 — System CA vs pinned-cert choice + caution note |
| Permissions | `f0c268d81fc34bf18629428b48f6f276` | §3.5 + §8 — notification access, battery exemption, boot caveat, OTP reassurance |
| Apps allow-list | `661a1b472f9b4433b57f85b9842e768c` | §3.5 — searchable list, per-app switches, "empty by design" banner |

### 3.8 App lock & on-device privacy

The app has **no account and no server auth** — it is single-user and
local-first; a login would contradict the no-cloud premise and protect
nothing. The real exposure is on-device: the Diagnostics "Recent" view
aggregates notification *content* (OTP codes, bank alerts, private
messages) into one browsable history. Three proportionate protections:

1. **Local app-lock.** `androidx.biometric` `BiometricPrompt` with
   authenticators `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` (device PIN/pattern
   is an accepted fallback — no enrolled biometric required). Gates app
   launch and return-from-background after a configurable idle timeout
   (default 1 min). **Enabled by default** given the content class; the
   user can disable it explicitly in settings.
2. **`FLAG_SECURE`** is set on any screen that renders notification content
   (Diagnostics/Recent and Broker). Blocks screenshots and removes the
   content from the app-switcher thumbnail.
3. **Redaction by default.** Recent rows show app + title; the body is
   masked until the user reveals it (gated by the same auth). This is a
   UI-only measure — the outbox payload and MQTT publish are unaffected
   (forwarding full content is the app's purpose).

**Version caveat:** combined `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is
reliable on API 30+. On `minSdk 26`–29 the implementation falls back to
`KeyguardManager.createConfirmDeviceCredentialIntent()` for the
device-credential path. Treated like the other version caveats in this
spec — handled in implementation, not deferred.

Out of scope (deliberate, single-user app): per-screen passcodes, a
separate encrypted "vault", or app-specific credential storage. The
Android keystore / file-based encryption already protects app-private data
at rest.

**UI surface (minimal, no new top-level screen):**

- *Auth challenge:* system `BiometricPrompt` sheet — Android-rendered, not
  designed by us.
- *Locked placeholder:* one small scrim composable (app mark, "Unlock to
  continue", Unlock button to re-invoke the prompt if dismissed). Shown on
  cold start and on resume past the idle timeout.
- *Controls:* an "App lock" card on the existing **Permissions** screen
  (§3.5) — no separate Settings screen.
- *Redaction:* a row-state change on the existing Dashboard "Recent" list
  (masked body + reveal affordance), not a new screen.

Stitch impact: amend the Permissions and Dashboard screens; add the locked
placeholder. Captured here; producing the visuals is optional pre-build.

## 4. Data Flow

```
App posts notification
  → NotifListenerService.onNotificationPosted
  → allow-list filter
  → NotificationMapper → CapturedNotification
  → OutboxDao.insert
  → MqttForegroundService.drain()
  → MqttClientManager.publish(QoS1) to notifybridge/<device>/notification
  → on ack: OutboxDao.delete(row)
HA: discovery sensor updates from state/attributes topic;
    availability from LWT topic.
```

## 5. Error Handling

| Failure | Behaviour |
|---|---|
| Broker unreachable | Backoff reconnect; items stay in outbox; UI shows disconnected; LWT marks HA entity offline. |
| Publish not acked | Row retained, `attemptCount++`, backoff retry. |
| Outbox growth | 7-day TTL + 5,000-row cap, oldest pruned. |
| Missing notification extras | Null fields, never throw. |
| Notification access revoked | UI reflects loss; publishing stops; no crash. |
| Reboot / process death | BootReceiver + periodic WorkManager re-drain. |
| Notification update flood (progress bars, ongoing) | Deduped by `(package, tag, id)` + 500 ms debounce; content-unchanged ongoing updates dropped (§3.1 step 2). |

## 6. Testing (TDD)

- **Unit:** `NotificationMapper` (body priority chain incl. `MessagingStyle`
  latest-message + sender prefix, big-text fallback, missing-extras);
  dedup/debounce logic (same-key within 500 ms dropped, content change
  passes, ongoing-unchanged dropped); outbox enqueue/drain/prune/backoff;
  discovery-payload builder; settings repository; app-lock idle-timeout
  state machine (locks after timeout, unlock clears, background re-locks)
  with a fake authenticator.
- **Instrumented:** Room DAO (insert/delete/prune/query); `BiometricPrompt`
  integration smoke test (auth success/cancel paths).
- **Use-case tests:** against a fake `MqttClientManager`.
- Live-broker integration test: out of unit-suite scope (manual / optional).

## 7. Dependencies

Compose BOM, Hilt, Room, DataStore, HiveMQ MQTT client,
kotlinx-coroutines, kotlinx-serialization (JSON payloads), WorkManager,
AndroidX lifecycle.

## 8. Resolved Decisions & Residual Risks

**Resolved — foreground-service type (C1):** the FGS uses
`foregroundServiceType="connectedDevice"` with the
`FOREGROUND_SERVICE_CONNECTED_DEVICE` permission. `dataSync` was rejected:
Android 15 deprecated it and imposes a ~6-hour/day cumulative runtime cap
that would silently kill a persistent broker connection. `connectedDevice`
has no such time cap and fits a service maintaining a continuous network
connection to the broker/HA host. Contingency: if a specific OEM or a future
Android release kills `connectedDevice` for this use, fall back to
`specialUse` — this app is sideloaded for a personal HA setup, so
`specialUse`'s Google Play justification requirement does not apply. No
longer an open question.

**Residual risks:**

- **Foreground-service notification** is unavoidable for locked-state
  reliability. Accepted as the cost of the core value proposition.
- **`BOOT_COMPLETED` post-Android-12:** fires only after the user has opened
  the app at least once. Expected behaviour, surfaced to the user in
  onboarding; not a defect.
- **HiveMQ client size/method count:** acceptable for this single-purpose
  app; revisit only if APK size becomes a concern.
