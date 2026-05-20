# NotifyBridge — Setup Guide

A friendly install + setup guide. Intended audience: someone running their
own Home Assistant + Mosquitto stack who wants their phone's notifications to
appear in HA, privately, over their local network.

---

## What this is

An Android app that watches notifications on your phone and republishes the
ones you choose to a local MQTT broker. Home Assistant picks them up
automatically via MQTT discovery — a new sensor appears, and its
title / body / app / timestamp are exposed as attributes you can use in
automations.

The pitch:

- **Reliable** — every chosen notification is delivered, including rapid
  bursts and while the phone is locked. Buffered locally if your broker is
  down; replayed when it comes back. (Specifically fixes the gaps in the HA
  Companion app's "Last Notification" sensor.)
- **Local-first** — publishes straight to your existing Mosquitto. Zero
  cloud, zero Google or Firebase round-trips.
- **You choose** — per-app allow-list. Empty by default; nothing is shared
  until you opt apps in.

---

## What you need

- An Android phone running **Android 8 (API 26) or newer**. (Some lock
  features work best on Android 11+ — see Known limitations.)
- Your existing **Mosquitto** broker reachable on your home LAN.
- **Home Assistant** with the **MQTT integration** already configured and
  pointed at your broker. Discovery prefix should be the default
  `homeassistant`.
- Your phone on the same Wi-Fi as the broker.

---

## Install

You'll receive a signed `.apk` file (NotifyBridge v0.1.0). To install:

1. On the phone: **Settings → Apps → Special app access → Install unknown
   apps**, allow your file manager / browser / messenger of choice.
2. Open the `.apk` and tap **Install**. Tap **Open** when done.

The app shows up as **NotifyBridge** with a bell icon.

---

## First-run setup (three steps)

The app walks you through these in order.

### 1. Grant notification access

Tap **Grant** on the first onboarding card → Android opens **Settings →
Notification access**. Toggle **NotifyBridge** on, confirm. This is the
permission the app uses to read notifications other apps post. Android
requires you to grant it manually; there's no shortcut.

Back in the app, the step ticks green and step 2 unlocks.

### 2. Point it at your broker

Tap **Configure** → fill in:

| Field | Value |
| --- | --- |
| **Host** | Your Mosquitto's LAN IP or hostname (e.g. `192.168.1.50` or `homeassistant.local`). |
| **Port** | `1883` for plain MQTT, `8883` for TLS. |
| **Device name** | A short name for this phone — used in the HA entity and MQTT topic. e.g. `conchita` or `pixel-7`. |
| **Username / Password** | Leave blank for anonymous brokers. Fill in if yours uses auth. |
| **TLS** | Off for plain. **System CA** if your broker has a CA-signed cert. **Pinned** if it's self-signed — paste the broker's cert PEM into the field that appears. |

Tap **Test connection**. You should see a green "Connected — broker
reachable, auth OK (… ms)" line. If not, the line says why; common causes:
wrong IP/port, firewall, wrong auth, cert mismatch on TLS.

Tap **Save**. Step 3 unlocks.

### 3. Choose which apps to forward

Tap **Choose apps**. Scroll (or search) and toggle on the apps you want
NotifyBridge to forward. **Nothing is forwarded until you toggle something
on** — this is deliberate (your bank, OTP codes, and private chats stay off
the wire unless you opt in).

Suggested starter set: doorbell, family messenger, bank alerts you want to
see on HA dashboards. Skip anything noisy.

That's it. The setup checklist now reads complete.

---

## Verify it works

1. In HA, go to **Settings → Devices & Services → MQTT**. A new device
   called **NotifyBridge `<device-name>`** should be listed, with a sensor.
2. Post a test notification from one of your allow-listed apps (send
   yourself a message, ring the doorbell, etc.).
3. The HA sensor's state updates within a second. Its attributes carry
   `title`, `text`, `app`, `package`, `category`, `post_time`.
4. Open the **NotifyBridge** app → **Status** tab. You'll see the broker
   connection, outbox depth (0 when caught up), and a Recent list showing
   the notifications that have been forwarded (bodies redacted by default —
   tap to unlock and reveal).

You can subscribe directly from your HA host to watch the wire:

```bash
mosquitto_sub -t 'notifybridge/#' -v
```

---

## How it behaves

- **In the background.** A persistent foreground notification ("NotifyBridge
  active") sits in your shade — that's the price of staying connected while
  the phone is locked. It's low-importance and silent; just an indicator.
- **Phone locked.** Notifications keep flowing. This is the main thing the
  app does better than HA's built-in companion sensor.
- **Broker offline.** Notifications buffer locally (Room database). When
  the broker is reachable again, the app replays the buffer in order.
- **Phone offline (no Wi-Fi).** HA flips the entity to *unavailable* via
  the MQTT Last-Will. Reconnect = back to available.
- **Reboot.** First time after install, Android requires you to open
  NotifyBridge **at least once** before its boot receiver is allowed to run.
  After that, on every boot the bridge restarts itself and replays anything
  the outbox was holding.

---

## App-lock and privacy

Notification content can include OTP codes, bank alerts, and private
messages. To protect what NotifyBridge *itself* shows you:

- The app is locked with **biometric or your device PIN/pattern**. You'll
  be prompted to unlock on launch and after a configurable idle timeout
  (default 1 minute). Disable in **Permissions → App lock** if you don't
  want it.
- In the **Recent** list, notification bodies are **masked by default** —
  tap a row to authenticate and reveal. The published MQTT message itself
  is never redacted; this is a UI-only protection.
- Screens that show notification content set `FLAG_SECURE` — Android won't
  let them be screenshotted or appear in the recent-apps thumbnail.

---

## Known limitations (v0.1.0)

Recorded honestly so you're not surprised:

- **Recent list shows only the latest forwarded notification** (one-shot).
  The full history is in MQTT/HA, not in the app's UI. v0.2 may add an
  in-app history.
- **Notification title is not redacted** — only the body is. Senders often
  use the title for the sender's name, which is acceptable to leave visible;
  if your bank puts the amount in the title, it'll show even when locked.
- **App settings (broker host/password, TLS cert) are stored in DataStore
  unencrypted on-device.** Android's per-app sandbox + file-based
  encryption protect them at rest; nothing transmits them. Encryption at
  rest within the sandbox is a v0.2 candidate.
- **Per-app MQTT topic routing is not supported in v0.1.0.** Every forwarded
  notification goes to the same topic (`notifybridge/<device>/notification`);
  filter by the `package` attribute in HA automations.

---

## Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| Test connection fails immediately | Wrong host/port, or your phone isn't on the same network as the broker. Ping the broker IP from the phone (e.g. via the Termux app) to confirm reachability. |
| Test connection succeeds, but no sensor in HA | MQTT discovery is disabled or pointed at a different prefix in your HA MQTT integration. Should be `homeassistant`. |
| Notifications stop after a while | Some manufacturers (Xiaomi, Huawei, Samsung "battery optimisation") aggressively kill background services. In **Settings → Battery → NotifyBridge**, set to **Unrestricted**. The app's Permissions screen has a one-tap action for this. |
| Phone reboots and notifications don't flow until you open the app | This is Android 12+ behaviour, not a bug — the OS only allows boot receivers after the user has launched the app at least once. After that one launch it survives reboots. |
| App refuses to unlock with fingerprint/PIN | Make sure you've actually set a device PIN/pattern in Android Settings. Without one, biometric prompts can't fall back. |
| Pinned-cert TLS rejects your self-signed cert | Make sure the cert has a Subject Alternative Name (SAN) matching the host you're connecting to. Modern TLS clients reject SAN-less certs. |

---

## Send feedback

Anything weird, anything missing, anything you want different — message me
(Ephraim). v0.2 is open; this is a personal build for your stack, so what
you want shapes what gets added.

If something's broken, a screenshot of the **Status** tab plus the most
recent few lines from the Recent list (with bodies revealed if you're
comfortable) is usually enough to diagnose.

---

*Built for the local-first ethos you already live by — same as the Valetudo
de-clouding. Have fun with it.*
