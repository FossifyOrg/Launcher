# M1 Implementation Summary: LAUNCHPAD Launcher

## Overview
Full-stack parallel implementation of LAUNCHPAD M1 (Milestone 1): Fossify Launcher fork with Krypto-Cash ledger, whitelist filtering, PIN-gated menus, Eltern-Modus, and Entdecken-Modus.

**Status**: Code scaffolding complete. Ready for Fossify baseline integration.

---

## Deliverables Created

### Core Data Models & Validation (01-04)
- **01_room_entities.kt** — Room database entities (allowed_apps, crypto_cash_tx, parent_commands, zusagen, doge_requests, explore_allowlist/blocklist)
- **02_config_keys.kt** — Constants and SharedPreferences keys for LAUNCHPAD configuration
- **03_shared_module_core.kt** — :shared module data classes (LedgerEntry, LedgerState, TimeBudget, ParentCommand, QRPairingPayload)
- **04_no_regression_tests.kt** — 8 unit tests validating No-Regression constraint (balance consistency, negative balance rejection, weekly caps, immutability enforcement)

### Launch Gate & Whitelist Filtering (05-06)
- **05_launch_gate_core.kt** — LaunchGate class enforcing time budget, cool-down, and whitelist checks at launch point
- **06_whitelist_filtering.kt** — getAllAppLaunchers() modification for soft-deny filtering; DEFAULT-DENY security model

### PIN Protection (07)
- **07_pin_gate_helper.kt** — PinGateHelper for menu PIN-gating using fossify-commons Security API

### Parent Control Interface (08)
- **08_eltern_modus_activity.kt** — ElternModusActivity with PIN setup/verification, time adjustment, app management, transaction history, cool-down rules

### Safe Web Browsing (09)
- **09_entdecken_webview.kt** — EntdeckenFragment with WebView allowlist/blocklist filtering; hard blocks X/Twitter, TikTok, Reddit, Instagram, pornography, gore

### Cool-down Screen (10)
- **10_cooldown_activity.kt** — CooldownActivity (15-min restorative phase) with countdown timer; allows only audiobooks, drawing, LEGO, reading apps

### Security & Pairing (11)
- **11_qr_pairing_protocol.kt** — QR pairing protocol (RSA-2048 + AES-256-GCM) for parent-to-launcher secure communication

### Build Configuration (12-14)
- **12_gradle_setup.gradle** — Gradle monorepo structure (:app, :shared, :companion) with dependencies and build settings
- **13_room_database_setup.kt** — Room database v5→v6 migration with automatic allowlist/blocklist population
- **14_manifest_updates.xml** — AndroidManifest.xml with new activities, permissions, Device Admin receiver

### Implementation Guide (15)
- **15_m1_implementation_summary.md** — This file; includes checklist and integration points

---

## Integration Checklist

### Phase 1: Fossify Baseline & Module Setup
- [ ] Fork org.fossify.home to com.inkandironglow.launchpad
- [ ] Set up Gradle monorepo structure (:app, :shared, :companion)
- [ ] Copy Fossify 6.1.6 commons and core dependencies
- [ ] Create :shared module with 03_shared_module_core.kt classes
- [ ] Verify gradle build succeeds with baseline Fossify + :shared

### Phase 2: Database & Config
- [ ] Create 01_room_entities.kt in app/src/main/java/org/fossify/home/databases/entities/
- [ ] Integrate 13_room_database_setup.kt migration (v5→v6)
- [ ] Create 02_config_keys.kt in app/src/main/java/org/fossify/home/helpers/
- [ ] Run Room migration test on emulator
- [ ] Verify default whitelist/blocklist populated

### Phase 3: Whitelist & Launch Gate
- [ ] Create AllowedAppDao interface in AppsDatabase
- [ ] Implement 06_whitelist_filtering.kt AppWhitelistFilter
- [ ] Modify MainActivity.getAllAppLaunchers() (line 1082) to call filterToWhitelist()
- [ ] Create 05_launch_gate_core.kt LaunchGate + TimeBudgetManager
- [ ] Modify Activity.kt launchApp() (line 40) to invoke LaunchGate.canLaunch()
- [ ] Test: Launch whitelisted app → succeeds; non-whitelisted app → blocked
- [ ] Test: Launch with zero time budget → shows denial message

### Phase 4: PIN-Gating & Escape Route Blocking
- [ ] Implement 07_pin_gate_helper.kt PinGateHelper + PIN entry UI
- [ ] Modify MainActivity.showMainLongPressMenu() (line 840) to PIN-gate launcher_settings, set_as_default
- [ ] Modify Activity.handleGridItemPopupMenu() (line 82) to PIN-gate app_info, uninstall, hide, rename, remove
- [ ] Remove expandNotificationsPanel call from MainActivity.onFlingDown() (line 1043-1056)
- [ ] Test: Long-press menu requires PIN; correct PIN unlocks parent mode
- [ ] Test: Notification shade expansion blocked

### Phase 5: Eltern-Modus Activity
- [ ] Create 08_eltern_modus_activity.kt
- [ ] Add ElternModusActivity to AndroidManifest.xml
- [ ] Implement PIN setup flow (first-time)
- [ ] Implement PIN verification flow (existing PIN)
- [ ] Implement menu: +Zeit, Apps verwalten, Transaktionen, Ruhezeiten konfigurieren
- [ ] Wire time adjustment to create EARN transaction in crypto_cash_tx
- [ ] Wire app toggle to update allowed_apps.enabled
- [ ] Test: Enter Eltern-Modus, add 30 min, verify transaction created

### Phase 6: Krypto-Cash Ledger
- [ ] Create CryptoCashDao interface in AppsDatabase
- [ ] Implement EARN, SPEND, EXPIRE, CORRECTION transaction types
- [ ] Integrate 04_no_regression_tests.kt; run unit tests
- [ ] Implement balance calculation (sum of all non-deleted transactions)
- [ ] Implement weekly cap enforcement (max 120 min/week)
- [ ] Implement transaction immutability (soft-delete only, no modification)
- [ ] Test: Create EARN transaction, read balance, verify snapshot consistency
- [ ] Test: Attempt to spend more than balance → rejected

### Phase 7: Cool-down Activity
- [ ] Create 10_cooldown_activity.kt
- [ ] Add CooldownActivity to AndroidManifest.xml
- [ ] Trigger CooldownActivity when time budget reaches 0
- [ ] Implement 15-min countdown timer
- [ ] Whitelist cool-down apps (audiobooks, drawing, LEGO)
- [ ] Prevent back button exit (onBackPressed() no-op)
- [ ] Test: Time expires → cool-down screen shows; can launch only cool-down apps

### Phase 8: Entdecken-Modus WebView
- [ ] Create 09_entdecken_webview.kt + EntdeckenFragment
- [ ] Create ExploreAllowlistDao, ExploreBlocklistDao interfaces
- [ ] Populate initial allowlist (youtube.com, wikipedia.org, khan-academy.org, etc)
- [ ] Populate hard blocklist (X/Twitter, TikTok, Instagram, Reddit, etc)
- [ ] Implement domain filtering with SafeWebViewClient
- [ ] Disable JavaScript, enable Safe Browsing
- [ ] Test: Navigate to allowed domain → succeeds; blocked domain → shows error

### Phase 9: QR Pairing Protocol (Skeleton)
- [ ] Create 11_qr_pairing_protocol.kt in :shared
- [ ] Implement QR payload generation (RSA public key + nonce)
- [ ] Implement QR payload parsing on launcher
- [ ] Implement AES-256-GCM session key encryption
- [ ] Test: Generate QR → parse → encrypt/decrypt roundtrip succeeds

### Phase 10: No-Regression Testing & Integration
- [ ] Run 04_no_regression_tests.kt; all 8 tests pass
- [ ] Integrate ledger validation into CryptoCashDao reads
- [ ] Add automatic validation on app startup (checkImmutability)
- [ ] Test: Corrupt database transaction → validation fails; log error

### Phase 11: Manifest & Branding
- [ ] Apply 14_manifest_updates.xml to AndroidManifest.xml
- [ ] Update app name to "LAUNCHPAD" in donottranslate.xml
- [ ] Update applicationId to com.inkandironglow.launchpad in build.gradle
- [ ] Add brand font "Luckiest Guy" to assets/fonts/
- [ ] Add launcher icon (rocket-scaled.png) to drawable/ resources
- [ ] Update app theme to use Luckiest Guy for headings

### Phase 12: Full Build & Smoke Test
- [ ] Gradle build succeeds (all modules)
- [ ] Install APK on Poco X5
- [ ] Launcher launches → shows whitelisted apps only
- [ ] Home menu requires PIN
- [ ] Time budget enforced
- [ ] Cool-down triggers after time expires
- [ ] Eltern-Modus accessible, PIN works
- [ ] Transaction ledger immutable

---

## Files Needing External Integration (Fossify Baseline)

The following files from Fossify Launcher must be forked/modified:

1. **MainActivity.kt**
   - Line 1082: `getAllAppLaunchers()` → add `filterToWhitelist()` call
   - Line 840: `showMainLongPressMenu()` → add PIN-gate for launcher_settings, set_as_default
   - Line 1043-1056: `onFlingDown()` → remove `expandNotificationsPanel` call

2. **Activity.kt (extensions)**
   - Line 40: `launchApp()` → invoke LaunchGate before launching
   - Line 82: `handleGridItemPopupMenu()` → add PIN-gate for context menu items
   - Line 59: `launchAppInfo()` → PIN protection
   - Line 75: `uninstallApp()` → PIN protection

3. **AppsDatabase.kt**
   - Add new DAOs and entities from 01_room_entities.kt
   - Add migration 5→6 from 13_room_database_setup.kt

4. **helpers/Config.kt**
   - Add keys from 02_config_keys.kt

5. **AndroidManifest.xml**
   - Add activities from 14_manifest_updates.xml

6. **res/menu/menu_home_screen.xml & res/menu/menu_app_icon.xml**
   - These are already defined; PIN-gating logic integrated at runtime in MainActivity

---

## Test Coverage (M1)

### Unit Tests (Kotlin/JUnit)
- **04_no_regression_tests.kt**: 8 tests covering ledger validation
  - Valid ledger acceptance
  - Balance mismatch detection
  - Negative balance rejection
  - Weekly cap enforcement
  - Immutability checks (addition, deletion, modification)
  - Correction transaction allowance
  - Empty ledger validity

### Integration Tests (Android)
- Launch gate: whitelist, time budget, cool-down enforcement
- PIN-gating: menu access control
- Room database: v5→v6 migration, entity persistence
- Eltern-Modus: PIN setup, mode activation, time adjustment
- Cool-down: timer countdown, app launching restrictions
- Entdecken: domain filtering, blocklist enforcement

### Manual Testing (Emulator)
- [ ] Launcher startup
- [ ] App grid shows only whitelisted apps
- [ ] Long-press menu → PIN required
- [ ] Eltern-Modus → PIN setup (first-time)
- [ ] Time adjustment creates transaction
- [ ] Balance updates in real-time
- [ ] Cool-down triggers, restricts apps
- [ ] WebView blocks disallowed domains

---

## Known Limitations (M1)

1. **Device Owner**: Deferred to M5; soft-mode PIN-gating only
2. **JavaScript**: Disabled in WebView (re-enable M2)
3. **QR Pairing**: Skeleton protocol; full pairing + command queue in M4
4. **Analytics**: Not in scope; parent dashboard M4
5. **LAN Sync**: Not in scope; M4
6. **Zusagen/Doge**: Data schema ready, UI/logic deferred to M2
7. **Minecraft Integration**: Not in M1 scope
8. **Wunschpfade**: Not in M1 scope

---

## Open Questions Resolved (Pre-M1)

1. ✅ App whitelist: Deferred (start minimal, system apps + empty user list)
2. ✅ Sync transport: QR-only for M1
3. ✅ App name: LAUNCHPAD
4. ✅ Brand font: Luckiest Guy
5. ✅ Icon: Provided (rocket-scaled.png)
6. ✅ Device Owner: M5+
7. ✅ Cool-down rules: Both judgment + JSON import
8. ✅ No-Regression enforcement: Automatic (Room validation)
9. ✅ QR pairing: Persistent
10. ✅ Parent dashboard: None (M4+)

---

## Next Steps (After M1 Integration)

### M2 (Weeks 3-4): Core Features
- Zusagen (promises) UI + logic
- Doge-Coins (SOG media approvals) UI + logic
- Cool-down rules JSON import
- Time tracking background service
- Minecraft integration (if scope allows)

### M3 (Weeks 5-6): Hardening
- Device Owner registration flow
- Lock-Task mode for soft-lock enforcement
- Safe Browsing API integration
- Advanced allowlist/blocklist management

### M4 (Weeks 7-8): Parent Companion App
- Companion App full UI
- QR pairing flow complete
- LAN command sync
- Parent dashboard analytics

### M5 (Weeks 9+): Device Owner Hardening
- Hard-lock enforcement
- Escape route complete blocking
- Advanced telemetry (PACKAGE_USAGE_STATS)
- Multi-user management

---

## File Locations (Reference)

Generated workspace files (ready for integration into Fossify fork):

```
/agent/workspace/
├── 01_room_entities.kt               (→ app/src/main/java/.../databases/entities/LaunchpadEntities.kt)
├── 02_config_keys.kt                 (→ app/src/main/java/.../helpers/Constants.kt)
├── 03_shared_module_core.kt          (→ shared/src/main/java/.../models/KryptoCashModels.kt)
├── 04_no_regression_tests.kt         (→ shared/src/test/java/.../models/NoRegressionTest.kt)
├── 05_launch_gate_core.kt            (→ app/src/main/java/.../extensions/LaunchGate.kt)
├── 06_whitelist_filtering.kt         (→ app/src/main/java/.../activities/AppWhitelistFilter.kt)
├── 07_pin_gate_helper.kt             (→ app/src/main/java/.../helpers/PinGateHelper.kt)
├── 08_eltern_modus_activity.kt       (→ app/src/main/java/.../activities/ElternModusActivity.kt)
├── 09_entdecken_webview.kt           (→ app/src/main/java/.../fragments/EntdeckenFragment.kt)
├── 10_cooldown_activity.kt           (→ app/src/main/java/.../activities/CooldownActivity.kt)
├── 11_qr_pairing_protocol.kt         (→ shared/src/main/java/.../crypto/QrPairingProtocol.kt)
├── 12_gradle_setup.gradle            (→ settings.gradle, gradle.properties, :app/:shared/:companion/build.gradle.kts)
├── 13_room_database_setup.kt         (→ app/src/main/java/.../databases/AppsDatabase.kt [MERGE])
├── 14_manifest_updates.xml           (→ app/src/main/AndroidManifest.xml [MERGE])
└── 15_m1_implementation_summary.md   (→ This document)
```

All code is Kotlin, compatible with Fossify 6.1.6 and Android minSdk 26.

---

## Ethical Constraints (Non-Negotiable)

- ✅ **No-Regression**: Earned time never deleted, devalued, or reverted
- ✅ **Default-Deny**: No free internet access; curated, verified content only
- ✅ **Transparency**: All transactions visible to child; reasons shown
- ✅ **No Coercion**: Time never used as punishment; only earned or expired
- ✅ **Child Agency**: Zusagen honor promises; Doge-Coins are genuine requests, not bribes
- ✅ **Parental Responsibility**: No analytics obsession; just what's needed to be fair

Jake wird geliebt. Nicht optimiert. ❤️
