# NotifyBridge

NotifyBridge captures posted Android notifications and publishes them to a local
MQTT broker with Home Assistant auto-discovery, fully local — no cloud, no
Google/Firebase, no external service: the phone is the only source, the broker
is on your LAN, and Home Assistant subscribes directly.

Design spec: [docs/superpowers/specs/2026-05-17-notifybridge-design.md](docs/superpowers/specs/2026-05-17-notifybridge-design.md)

**Status:** v0.1.0 · pre-release &nbsp;·&nbsp; **License:** TBD

## Features

- Per-app allow-list — only notifications from apps you've explicitly enabled
  are forwarded.
- Reliable while locked / dozing — a `connectedDevice` foreground service keeps
  the MQTT connection alive so rapid-fire notifications all arrive (unlike the
  Companion app's "Last Notification" sensor, which clobbers).
- Offline buffering — notifications posted while the broker is unreachable are
  queued in a Room-backed outbox and drained when connectivity returns.
- Home Assistant MQTT auto-discovery — a retained config message on
  `homeassistant/sensor/notifybridge_<device>/config` lets HA pick the sensor
  up without manual YAML.
- Biometric app-lock with optional body redaction — the in-app Recent list
  masks notification text until a per-item biometric unlock.
- Fully local — no telemetry, no analytics, no third-party SDKs; the only
  outbound connection is to your broker.

## How it works

```
  Notification posted
       │
       ▼
  NotifListenerService   ── allow-list filter ──▶ drop
       │
       ▼
  Outbox  (Room)         ◀── survives reboot / broker outage
       │
       ▼
  MqttForegroundService  ───▶  Local MQTT broker  ───▶  Home Assistant
```

The outbox state machine, dedup/debounce rules, retry/backoff behaviour, and
the `BOOT_COMPLETED` Android-12 caveat are covered in detail in the
[design spec](docs/superpowers/specs/2026-05-17-notifybridge-design.md).

## Requirements

| Need                  | Detail                                                              |
|-----------------------|---------------------------------------------------------------------|
| Android version       | 8.0+ (API 26). App-lock biometric path is effectively API 30+ until the SDK branch lands — see caveat below. |
| MQTT broker           | Local broker on your LAN. Mosquitto is the reference; HiveMQ is compatible. Default port 1883; TLS optional (system CA or pinned PEM). |
| Home Assistant        | The MQTT integration enabled, pointed at the same broker.           |
| To build              | JDK 17 (all JVM tasks are pinned to the 17 toolchain).              |

## Tech stack

| Layer              | Library / version                                     |
|--------------------|-------------------------------------------------------|
| Language / build   | Kotlin 2.0.21, AGP 8.7.3, KSP 2.0.21-1.0.27           |
| SDK                | minSdk 26, targetSdk 35, compileSdk 35                |
| UI                 | Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose 2.8.4 |
| DI                 | Hilt 2.52 (incl. `hilt-work`, `hilt-navigation-compose`) |
| Persistence        | Room 2.6.1, DataStore Preferences 1.1.1               |
| Background work    | WorkManager 2.10.0, foreground service (`connectedDevice` type) |
| MQTT               | HiveMQ MQTT Client 1.3.5                              |
| Auth               | AndroidX Biometric 1.1.0                              |
| Concurrency / data | kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3 |

Full version pins live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Permissions

Declared in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml).

| Permission | Why |
|------------|-----|
| `INTERNET` | Publish to the MQTT broker. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Capture posted notifications. Granted via system Settings, not a runtime dialog. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the broker connection alive while locked. Declared as `connectedDevice` FGS type. |
| `CHANGE_NETWORK_STATE` | Companion permission required by `connectedDevice` FGS on API 34+. |
| `RECEIVE_BOOT_COMPLETED` | Drain the outbox after a reboot. (Android 12+ only fires once the user has opened the app at least once — see §8 in the spec.) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Exempt from Doze so the foreground service survives idle. |
| `USE_BIOMETRIC` | Local app-lock prompt. |
| `POST_NOTIFICATIONS` | Show the persistent foreground-service notification. |

## Project structure

Top-level packages under `com.nyasa.notifybridge`:

| Package     | Contents                                                                |
|-------------|-------------------------------------------------------------------------|
| `applock/`  | `BiometricAuthenticator`, `AppLockManager`, `AppLockGate`.              |
| `data/`     | Room DAO/entities, HiveMQ client wrapper, notification mapper, settings persistence. |
| `domain/`   | Models, repository interfaces, use cases, MQTT/discovery payload builder. |
| `service/`  | `NotifListenerService`, `MqttForegroundService`, `BootReceiver`, `OutboxDrainWorker`. |
| `ui/`       | Compose screens + ViewModels (`onboarding/`, `broker/`, `permissions/`, `apps/`, `status/`, `locked/`, `theme/`). |

## Build and run

```bash
# Compile a debug APK
./gradlew :app:assembleDebug

# Run all JVM unit tests
./gradlew :app:testDebugUnitTest

# Run instrumented tests (requires emulator or device, API 30+ recommended)
./gradlew :app:connectedDebugAndroidTest
```

> **Note:** the debug APK excludes `MqttForegroundService`, `NotifListenerService`,
> and `BootReceiver` (removed via `src/debug/AndroidManifest.xml`), so the live
> notification-forwarding path is not exercised by the instrumented suite.
> Use a release build for end-to-end manual verification.

The release build is signed with the debug keystore — internal/manual testing
only, **not** a production signing identity.

## Broker setup quickstart

The app expects a reachable MQTT broker on your LAN. A minimal Mosquitto
listener (no auth, no TLS — fine for a trusted home network):

```conf
# /etc/mosquitto/conf.d/local.conf
listener 1883 0.0.0.0
allow_anonymous true
```

In the app's Broker screen, set:

- **Host** → broker IP (e.g. `192.168.1.10`)
- **Port** → `1883`
- **Device name** → a slug; default `phone`. Becomes the topic prefix.
- **TLS** → `Off` for plaintext; `System CA` for a CA-signed cert; `Pinned`
  with a PEM-pasted cert for self-signed setups. Trust-all is intentionally
  not exposed.
- **Username / Password** → optional, supported.

Topic shape (see [`DiscoveryPayloadBuilder`](app/src/main/java/com/nyasa/notifybridge/domain/discovery/DiscoveryPayloadBuilder.kt)):

- `notifybridge/<device>/notification` — JSON payload per notification
- `notifybridge/<device>/status` — LWT online/offline
- `homeassistant/sensor/notifybridge_<device>/config` — retained HA discovery config

With Home Assistant's MQTT integration pointed at the same broker, the sensor
appears automatically after the first publish.

## Testing

- **JVM unit tests** — `app/src/test/` (~21 files): notification mapping
  (`MessagingStyle`, body priority chain), dedup/debounce, outbox
  enqueue/drain/prune, discovery payload, app-lock idle timeout, settings
  parsing, plus ViewModel tests for Broker / Apps / Onboarding.
- **Instrumented tests** — `app/src/androidTest/` (~6 files): Room DAO tests
  (`OutboxDaoTest`), `BiometricAuthenticatorTest`, Hilt graph smoke test,
  locked-screen UI test. Runner: `HiltTestRunner`.
- The instrumented suite **does not** exercise the live forwarding path
  (FGS / BootReceiver removed from debug APK); that's covered by the device
  checklist below.
- No CI configured yet — all verification is local.

## Device manual verification checklist

1. Install; onboarding shows GRANT_ACCESS active, others gated.
2. Grant notification access → step 2 unlocks.
3. Configure broker against a local Mosquitto (TLS OFF) → Test Connection shows success.
4. Enable WhatsApp in Apps; post a test notification → appears in HA via discovery sensor; attributes (`title`,`text`,`app`,`package`) present.
5. Kill broker → notifications buffer (outbox depth rises); restore broker → outbox drains.
6. Lock phone, post 3 rapid notifications → all 3 arrive (no clobber, vs. Companion sensor).
7. Background app past idle timeout → returns locked; biometric/PIN unlocks.
8. Recent list bodies show redacted until reveal.
9. Reboot device, open app once, post notification → still forwarded (BootReceiver path).
10. With SYSTEM_CA + a CA-signed broker, and PINNED + self-signed PEM → both connect; trust-all is not an option (S4).

## API-version caveat (§3.8)

The biometric app-lock (`BiometricAuthenticator.prompt`) requires API 30+. On
API 26–29, `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`
throws, so the effective app-lock floor is API 30 until that path is
SDK-branched. This is a documented must-fix-before-release.

## Contributing

There's no formal contribution process yet — the
[design spec](docs/superpowers/specs/2026-05-17-notifybridge-design.md) is the
source of truth for scope and behaviour. Issues and patches welcome.

## License

License: TBD.
