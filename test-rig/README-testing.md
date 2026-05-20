# NotifyBridge test rig

A disposable, local-only Mosquitto + Home Assistant stack to back the manual
verification checklist in the root [`README.md`](../README.md). Spin it up,
run the checklist, tear it down.

This file owns the **infrastructure** mechanics; the root README owns the
**behavioural** scenarios. The two are intentionally not duplicated.

---

## Prereqs

- Docker Desktop (Intel x86_64 build) running locally.
- Phone (or emulator) on the same Wi-Fi as the Mac.
- Debug APK installable from this repo (`./gradlew :app:installDebug` with a
  device or emulator attached). minSdk is 26.
- Optional: `brew install mosquitto` for the `mosquitto_sub` CLI. Not required
  — the `docker exec` form below works without it.

---

## Spin up

```bash
cd test-rig
docker compose up -d
docker compose logs -f          # watch logs; Ctrl-C to detach
```

Find the Mac's LAN IP (the phone connects to this):

```bash
ipconfig getifaddr en0 || ipconfig getifaddr en1
```

Endpoints:

| Service        | URL / address                        |
| -------------- | ------------------------------------ |
| Home Assistant | `http://<mac-ip>:8123`               |
| Mosquitto      | `<mac-ip>:1883` (TLS 8883 when opted in) |

---

## First-run HA wiring

1. Open `http://<mac-ip>:8123`. Complete the onboarding (name, location).
2. **Settings → Devices & Services → Add Integration → MQTT.**
3. Broker: `mosquitto`. Port: `1883`. Username/password: leave blank.
   (HA reaches Mosquitto over the compose-internal network by service name.)
4. Discovery prefix: leave as `homeassistant` — this matches the app's
   `DiscoveryPayloadBuilder` exactly.
5. Submit. HA confirms the connection.

---

## Install + configure the app

With a device or emulator connected (`adb devices`):

```bash
./gradlew :app:installDebug          # from the repo root, not test-rig/
```

In the app:

- Grant notification access (system Settings prompt).
- **Broker Setup**: Host = `<mac-ip>`, Port = `1883`, TLS = Off, leave auth
  blank. Tap **Test connection** → expect "Connected".
- **Apps**: enable one app (e.g. WhatsApp, or anything you can post test
  notifications from).

Stock app defaults match this rig: port `1883`, TLS `OFF`, device name
`phone`. Only the host needs to be set.

---

## Watch the wire

No-install (uses the running container):

```bash
docker exec -it nb-mosquitto mosquitto_sub -t '#' -v
```

Or, if `mosquitto_sub` is on PATH:

```bash
mosquitto_sub -h <mac-ip> -t 'notifybridge/#' -v
mosquitto_sub -h <mac-ip> -t 'homeassistant/sensor/notifybridge_phone/#' -v
```

### Topics published by the app (verbatim)

| Topic | Purpose |
| --- | --- |
| `notifybridge/<device>/notification` | state + `json_attributes` payload for each notification |
| `notifybridge/<device>/status` | LWT — `online` on connect (retained), `offline` on disconnect |
| `homeassistant/sensor/notifybridge_<device>/config` | discovery config (retained) so HA auto-creates the sensor |

`<device>` is slugified from the **Device name** in Broker Setup. Default is
`phone`, so the topics resolve to `notifybridge/phone/notification` etc.

---

## Run the checklist

The 10-step behavioural checklist lives in the **root README**:
[`README.md` → Device manual verification checklist](../README.md#device-manual-verification-checklist).
Open that section and walk it top to bottom. This rig backs steps 3–6 and 9
directly; steps 7–8 are app-internal (don't need the broker).

Do not copy the checklist here — it stays single-source.

---

## API 26–29 biometric verification

The fix for the biometric path on API 26–29 (commit
`b91a4b3 SDK-branch BiometricAuthenticator.prompt for API 26-29`) needs a
device in that range. An AVD is already prepared:

```bash
$ANDROID_HOME/emulator/emulator -avd nb_api28 -no-snapshot &
adb wait-for-device
./gradlew :app:installDebug
```

Then run checklist step 7 (lock / unlock with PIN/biometric) on the emulator.
The app should unlock via device-credential without crashing —
pre-`b91a4b3` it threw `IllegalArgumentException`.

---

## TLS path (optional)

Verifies Task 25's pinned trust manager end-to-end.

1. Generate a self-signed cert (acts as its own CA):

   ```bash
   mkdir -p mosquitto/config/certs
   openssl req -x509 -newkey rsa:4096 -sha256 -days 365 -nodes \
     -keyout mosquitto/config/certs/server.key \
     -out mosquitto/config/certs/server.crt \
     -subj "/CN=mosquitto.local" \
     -addext "subjectAltName=DNS:mosquitto.local,IP:<mac-ip>"
   ```

   The `subjectAltName` is required — modern TLS clients (incl. the HiveMQ
   Java client) reject certs without a matching SAN, even for IP addresses.

2. Uncomment the TLS stanza in `mosquitto/config/mosquitto.conf`.
3. Restart Mosquitto:

   ```bash
   docker compose restart mosquitto
   ```

4. In the app's Broker Setup: Port = `8883`, TLS = **Pinned**, paste the
   contents of `mosquitto/config/certs/server.crt` into the pinned CA field.
   Tap Test connection → expect "Connected".

`mosquitto/config/certs/` is `.gitignore`'d.

---

## Failure injection (the differentiators vs HA Companion sensor)

These are the cases the unit suite can't cover. Pair with checklist steps 5–6.

| Action | Expected |
| --- | --- |
| `docker compose stop mosquitto` | App Status screen → outbox depth rises with each notification; HA entity flips to *unavailable* (LWT). |
| `docker compose start mosquitto` | Outbox depth returns to 0; HA replays the buffered notifications in order; entity *available* again. |
| Toggle phone Wi-Fi off | HA entity → *unavailable* within keep-alive (60s). |
| Lock phone, post 3 notifications quickly | All 3 published; none dropped. |

---

## Teardown

```bash
docker compose down              # stop (keeps HA + Mosquitto state)
docker compose down -v           # stop + drop volumes (full reset)
```

If you ever need to reset HA's onboarding specifically:

```bash
rm -rf ha-config/* ha-config/.* 2>/dev/null
touch ha-config/.gitkeep
```
