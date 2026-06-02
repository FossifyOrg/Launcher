# LAUNCHPAD Eltern — Companion App

Separate APK for the parent's phone. Connects to the LAUNCHPAD launcher
over local WiFi and lets parents see + approve Jake's requests in real time.

## Build

```bash
cd companion
# Linux/Mac:
chmod +x gradlew && ./gradlew :app:assembleDebug
# Windows:
gradlew.bat :app:assembleDebug
```

APK: `companion/app/build/outputs/apk/debug/app-debug.apk`

## Setup

1. Install APK on parent's phone
2. On Jake's phone: LAUNCHPAD → Eltern-Modus → Kopplung
   Note the IP shown (e.g. 192.168.1.42:7391)
3. Open companion app → enter that IP once → connected

## Features

- Live balance + Kindermodus status
- Pending Doge-Coin media requests → Approve/Reject with one tap
- Pending Zusagen → Approve/Reject
- Auto-refresh after each action

## Architecture

Communication: plain HTTP to LaunchpadServer (port 7391) running inside
the launcher. No internet needed — same WiFi only.
Endpoints: GET /api/status  GET /api/pending  POST /api/command
