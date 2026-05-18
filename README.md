# NotifyBridge

NotifyBridge captures posted Android notifications and publishes them to a local
MQTT broker with Home Assistant auto-discovery, fully local — no cloud, no
Google/Firebase, no external service: the phone is the only source, the broker
is on your LAN, and Home Assistant subscribes directly.

Design spec: [docs/superpowers/specs/2026-05-17-notifybridge-design.md](docs/superpowers/specs/2026-05-17-notifybridge-design.md)

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
