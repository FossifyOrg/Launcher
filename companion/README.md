# LAUNCHPAD Eltern — Companion App

Separate APK for the parent's phone. Connects to the LAUNCHPAD launcher over
local WiFi and lets parents see + approve Jake's requests in real time.

## ⚡ Build (one-liner from repo root)

```bash
./build-companion.sh
```

Output APK: `companion/app/build/outputs/apk/debug/app-debug.apk`

Under the hood this runs `./gradlew -p companion :app:assembleDebug` using the
launcher's Gradle wrapper. The companion is a **separate Gradle project** — not
a submodule — because Kotlin 2.x + AGP 9.x refuses to register the `kotlin`
extension in two modules of the same build.

## Setup

1. Build the APK (above), `adb install` it on the parent's phone.
2. On Jake's phone: open LAUNCHPAD → Eltern-Modus → **Kopplung**.
   Note the line `📡 Jakes Gerät IP: 192.168.x.x:7391`.
3. Open the companion app, enter that IP once → you're connected.

## What it does

- **Live status card**: current balance, Kindermodus on/off, cool-down state
- **Pending Doge-Coin media requests** → ✓ Genehmigen / ✗ Ablehnen with one tap
- **Pending Zusagen** → same
- Auto-refresh after every action

## Architecture

```
parent phone ── HTTP ──► Jake's phone :7391
   (companion)          (LaunchpadServer inside the launcher)
```

- No internet, no cloud — same WiFi only.
- Endpoints: `GET /api/status`  `GET /api/pending`  `POST /api/command`
- Commands flow through `CommandProcessor` (same engine used by QR pairing).

## Troubleshooting

**"Cannot add extension with name 'kotlin'…"** — you're trying to build it as
part of the launcher build (`./gradlew :companion:…`). Don't. Use
`./build-companion.sh` or `./gradlew -p companion :app:assembleDebug`.

**Connection fails** — both phones must be on the same WiFi network. Some
routers block client-to-client traffic ("AP isolation") — try a different
network or your phone's hotspot.
