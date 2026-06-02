# M3 — Device Owner & Kiosk Setup

LAUNCHPAD's hard enforcement (lock-task kiosk, status-bar lockdown, uninstall protection)
requires the launcher to be the device's **Device Owner**. An app cannot grant itself this —
it is a one-time provisioning step on a fresh device.

## 1. Prepare the device

Device Owner can only be set when the device has **no added accounts** (no Google sign-in).

1. Factory reset the Poco X5 (or any test device).
2. During setup, **skip Wi-Fi / skip Google account** (add them only after provisioning).
3. Enable Developer Options → **USB debugging**.
4. Install the LAUNCHPAD APK:
   ```bash
   ./gradlew :app:installDebug
   ```

## 2. Provision Device Owner

Run the command shown in Eltern-Modus → "Kiosk-Modus" (it prints the exact applicationId):

```bash
# Debug build (applicationId org.fossify.home.debug):
adb shell dpm set-device-owner org.fossify.home.debug/org.fossify.home.receivers.LockDeviceAdminReceiver

# Release build (applicationId org.fossify.home):
adb shell dpm set-device-owner org.fossify.home/org.fossify.home.receivers.LockDeviceAdminReceiver
```

Success prints: `Success: Device owner set to package ...`

Common failures:
- `not allowed to set the device owner because there are already some accounts` → remove all
  accounts (Settings → Passwords & accounts) or factory reset again.
- `because there are already several users` → remove secondary users.

## 3. Enable kiosk

In the launcher: **Settings → LAUNCHPAD → Eltern-Modus → Kiosk-Modus (Gerätesperre)**.
Once Device Owner is detected the toggle flips to **AN**, and on the next launcher resume the
device enters lock-task:

- Pinned to LAUNCHPAD + whitelisted apps only (lock-task allowlist tracks `allowed_apps`).
- HOME returns to LAUNCHPAD; recents / notification shade / quick settings blocked.
- Status bar disabled; the launcher cannot be uninstalled; safe-boot and new users blocked.
- Factory reset is intentionally **left enabled** so a parent always has a recovery path.

Turn it off from the same button (PIN-gated) — it leaves lock-task and clears the restrictions.

## 4. Remove Device Owner (for testing)

```bash
adb shell dpm remove-active-admin org.fossify.home.debug/org.fossify.home.receivers.LockDeviceAdminReceiver
```

## Notes

- Without Device Owner the launcher still runs in **soft mode** (PIN gating, launch gate,
  time budget, cool-down) — only the hard kiosk lock is unavailable.
- All policy calls in `KioskManager` are guarded by `isDeviceOwner()`, so a non-provisioned
  device never crashes; the kiosk button shows the provisioning instructions instead.
