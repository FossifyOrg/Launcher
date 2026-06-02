# LAUNCHPAD M1 Verification Checklist

**Purpose**: Verify that all M1 components are integrated, building, and functional.

**Status**: Run this after completing 00_INTEGRATION_QUICKSTART.md steps 1-11.

---

## Compilation & Build

- [ ] Fossify baseline + :shared module `gradle build` succeeds
- [ ] All three modules present: `:app`, `:shared`, `:companion`
- [ ] No unresolved import errors
- [ ] Room database schema compiles (entities recognized)
- [ ] AppsDatabase v5→v6 migration compiles
- [ ] fossify-commons 6.1.6 dependency resolved
- [ ] APK generated without warnings (except Gradle deprecations)
- [ ] No ProGuard/R8 obfuscation errors
- [ ] build.gradle.kts syntax correct across all modules

**Command**:
```bash
./gradlew clean build --info
```

---

## Unit Tests

- [ ] :shared:test runs without hanging
- [ ] All 8 No-Regression tests in 04_no_regression_tests.kt pass
  - [ ] testValidLedgerAccepted
  - [ ] testBalanceMismatchDetected
  - [ ] testNegativeBalanceRejected
  - [ ] testWeeklyCapsEnforced
  - [ ] testImmutabilityCheckPasses
  - [ ] testImmutabilityCheckFailsOnDeletion
  - [ ] testImmutabilityCheckFailsOnModification
  - [ ] testCorrectionTransactionAllowed
- [ ] No flaky tests (rerun 3x, all pass)
- [ ] Test output shows all assertions passed

**Command**:
```bash
./gradlew :shared:test --info
```

---

## Database & Config

- [ ] AppsDatabase.kt contains 8 new @Entity classes
- [ ] MIGRATION_5_6 present in AppsDatabase.kt
- [ ] Migration creates all required tables:
  - [ ] allowed_apps
  - [ ] crypto_cash_tx
  - [ ] parent_commands
  - [ ] zusagen
  - [ ] doge_requests
  - [ ] explore_allowlist
  - [ ] explore_blocklist
  - [ ] explore_suggestions
- [ ] All DAOs defined: AllowedAppDao, CryptoCashDao, ParentCommandDao, ZusageDao, DogeRequestDao, ExploreAllowlistDao, ExploreBlocklistDao, ExploreSuggestionDao
- [ ] Constants.kt contains LaunchpadConstants enum (modes, lockdown levels, TX types)
- [ ] LaunchpadPrefs object contains all SharedPreferences keys
- [ ] Room migration auto-populates default whitelist
- [ ] Room migration auto-populates default blocklist

**Verification**:
1. Android Studio → Database Inspector
2. Install APK on emulator
3. Navigate to: View → Tool Windows → App Inspection → Databases
4. Expand "apps.db"
5. Verify 8 tables listed above exist
6. Check allowed_apps contains system apps
7. Check explore_blocklist contains X/Twitter, TikTok, Reddit

---

## Whitelist & Launch Gate

- [ ] AppWhitelistFilter class compiles
- [ ] LaunchGate class compiles
- [ ] TimeBudgetManager class compiles
- [ ] MainActivity.getAllAppLaunchers() calls filterToWhitelist()
- [ ] Activity.launchApp() invokes LaunchGate.canLaunch()
- [ ] Whitelisted apps appear in home screen grid
- [ ] Non-whitelisted apps hidden from grid
- [ ] Attempt to launch hidden app → denied message shown
- [ ] Message text is child-friendly German ("Diese App ist nicht erlaubt.")

**Manual Test**:
1. Install APK on device
2. Open LAUNCHPAD
3. Home screen grid shows only system apps + whitelisted
4. Try to launch disallowed app (via adb `am start`) → fails gracefully

```bash
adb shell am start -n com.example.hiddenapp/com.example.hiddenapp.MainActivity
# Expected: Activity fails to start (gate blocks it)
```

---

## PIN-Gating & Menus

- [ ] PinGateHelper class compiles
- [ ] MainActivity.showMainLongPressMenu() checks PIN gate
- [ ] Activity.handleGridItemPopupMenu() checks PIN gate
- [ ] Home menu long-press requires PIN
- [ ] App icon context menu requires PIN
- [ ] expandNotificationsPanel call REMOVED from onFlingDown()
- [ ] Swipe-down (fling) does NOT show notification shade
- [ ] PIN entry dialog appears when gated action selected
- [ ] Correct PIN allows action
- [ ] Incorrect PIN rejects action
- [ ] 3 failed attempts shows error message
- [ ] Parent mode timeout implemented (30 min default)

**Manual Test**:
1. Long-press on home screen → PIN dialog appears
2. Enter wrong PIN → "PIN falsch" message
3. Enter correct PIN (from setup) → menus unlock for 30 min
4. Within 30 min, re-access menus → no PIN required
5. After 30 min, PIN required again

---

## Eltern-Modus Activity

- [ ] ElternModusActivity class compiles
- [ ] AndroidManifest.xml registers ElternModusActivity
- [ ] Activity launches via intent from MainActivity
- [ ] First-time: PIN setup flow shows (2 input fields, verify button)
- [ ] PIN validation: min 4 digits, must match
- [ ] After PIN set: parent mode menu appears (4+ buttons)
- [ ] "+ Zeit hinzufügen" button launches time adjustment dialog
- [ ] Time adjustment creates EARN transaction in Room
- [ ] "Apps verwalten" shows installed apps with toggle
- [ ] Toggling app updates allowed_apps.enabled in Room
- [ ] "Transaktionen anzeigen" queries crypto_cash_tx, displays in reverse chrono order
- [ ] "Ruhezeiten konfigurieren" shows cool-down duration + rules JSON field
- [ ] "Beenden" closes activity and deactivates parent mode

**Manual Test**:
1. Trigger Eltern-Modus from home menu
2. PIN setup: Enter "1234", confirm "1234" → succeeds
3. PIN setup: Try "123" → error "mindestens 4 Ziffern"
4. PIN setup: Try "1234" vs "1235" → error "stimmen nicht überein"
5. Parent mode menu: Click "+ Zeit" → dialog appears
6. Adjust time: +30 min → OK → transaction logged
7. Verify balance increased by 30 in Room
8. Apps verwalten: Toggle one app → update persisted in Room
9. Transaktionen: View +30 min entry, shows reason + timestamp
10. Beenden: Exit → parent mode deactivated

---

## Krypto-Cash Ledger

- [ ] CryptoCashDao interface defined
- [ ] LedgerEntry data class has all required fields
- [ ] LedgerState data class validates No-Regression
- [ ] Transaction types: EARN, SPEND, EXPIRE, CORRECTION all supported
- [ ] Balance calculation correct (sum of all deltaMinutes)
- [ ] Balance snapshots stored and verified
- [ ] No transaction can be modified (soft-delete only via deleted=1)
- [ ] Negative balances rejected (validation fails)
- [ ] Weekly cap (120 min) enforced
- [ ] Immutability check detects deleted or modified transactions
- [ ] Unit tests cover all scenarios

**Verification**:
1. Create EARN transaction: +10 min, verify balanceAfter = 10
2. Create SPEND transaction: -5 min, verify balanceAfter = 5
3. Query balance: should be 5 min
4. Try to SPEND -10 min → validation fails (would go negative)
5. Create CORRECTION: +2 min, verify balanceAfter = 7
6. Verify 3 transactions immutable (no modifications)
7. Run No-Regression unit tests → all pass

**Room Query Test**:
```sql
SELECT * FROM crypto_cash_tx WHERE deleted = 0 ORDER BY createdAt DESC;
```

---

## Cool-down Activity

- [ ] CooldownActivity class compiles
- [ ] AndroidManifest.xml registers CooldownActivity
- [ ] Activity launches when time budget = 0
- [ ] 15-minute countdown timer starts automatically
- [ ] Timer displays MM:SS format
- [ ] Progress bar reflects remaining time
- [ ] Cool-down message shown: "Dein Hirn braucht eine Pause..."
- [ ] Cool-down apps displayed: Audiobook, Drawing, LEGO, Reading buttons
- [ ] Clicking cool-down app launches it
- [ ] Back button disabled (onBackPressed no-op)
- [ ] Timer completes → activity auto-dismisses
- [ ] Timer is persistent (survives app pause/resume)

**Manual Test**:
1. Set time budget to 1 minute (via Eltern-Modus)
2. Launch app, use 1 minute
3. Cool-down triggers → screen shows
4. Verify countdown running (15:00 → 14:59 → ...)
5. Try back button → screen doesn't close
6. Click audiobook button → audiobook launches (if installed)
7. Return to cool-down → timer still running
8. Wait 15 min or fast-forward system clock → auto-dismisses

---

## Entdecken-Modus WebView

- [ ] EntdeckenFragment class compiles
- [ ] WebView configured with SafeWebViewClient
- [ ] JavaScript disabled (for M1)
- [ ] Safe Browsing enabled
- [ ] Allowlist enforcement:
  - [ ] youtube.com allowed
  - [ ] wikipedia.org allowed
  - [ ] khan-academy.org allowed
- [ ] Blocklist enforcement:
  - [ ] twitter.com blocked
  - [ ] x.com blocked
  - [ ] tiktok.com blocked
  - [ ] instagram.com blocked
  - [ ] reddit.com blocked
  - [ ] *.onion blocked
- [ ] Redirect to blocked domain → shows error page
- [ ] Error message: "Diese Seite ist nicht erlaubt."
- [ ] Hard blocklist patterns match regex correctly

**Manual Test**:
1. Navigate to https://youtube.com → loads
2. Navigate to https://wikipedia.org → loads
3. Navigate to https://twitter.com → blocked, error shown
4. Navigate to https://instagram.com → blocked, error shown
5. Navigate to https://reddit.com → blocked, error shown
6. Try to click link to tiktok.com → blocked, error shown
7. Verify no YouTube shorts, Instagram Reels, Reddit feeds accessible

---

## QR Pairing Protocol

- [ ] QrPairingProtocol class compiles in :shared
- [ ] RSA-2048 key pair generation works
- [ ] AES-256 session key generation works
- [ ] QR payload encoding (Base64 public key + nonce) works
- [ ] QR payload decoding (Base64 → PublicKey) works
- [ ] Session key encryption (RSA + plaintext AES key) works
- [ ] Session key decryption works
- [ ] Command signing (AES/GCM encryption) works
- [ ] Command verification (AES/GCM decryption) works
- [ ] Nonce properly generated (16 bytes, random)
- [ ] Timestamp included in payload

**Unit Test** (in :shared:test):
```kotlin
val protocol = QrPairingProtocol()
val payload = protocol.generateParentQrPayload("Papa Christian")
val parsed = protocol.parseQrPayload(payload.toJson())
assert(parsed != null)
assert(parsed.parentId == "Papa Christian")
```

---

## Manifest & Branding

- [ ] app/src/main/AndroidManifest.xml includes all new activities
- [ ] Activities registered with correct `android:name` paths
- [ ] Device Admin receiver registered
- [ ] Required permissions added:
  - [ ] INTERNET
  - [ ] PACKAGE_USAGE_STATS
  - [ ] QUERY_ALL_PACKAGES (if API 30+)
- [ ] App name in strings.xml = "LAUNCHPAD"
- [ ] App icon set to rocket-scaled.png
- [ ] Brand font (Luckiest Guy) available in res/font/
- [ ] Styles applied to launcher theme
- [ ] applicationId = "com.inkandironglow.launchpad"

**APK Verification**:
1. Unzip APK: `unzip app-debug.apk -d apk_contents`
2. Check: `apk_contents/AndroidManifest.xml` contains 5 activities
3. Check: `apk_contents/res/drawable/ic_launcher.png` present
4. Check: `apk_contents/res/font/luckiest_guy.ttf` present
5. Install and verify app label = "LAUNCHPAD"

---

## Runtime Behavior

### Startup
- [ ] App launches to home screen
- [ ] Grid shows only whitelisted apps
- [ ] Grid layout intact (no visual glitches)
- [ ] Launcher is responsive (no ANR)

### Time Budget Enforcement
- [ ] App tracks active time (or user can adjust via Eltern-Modus)
- [ ] Time-check before launch prevents disallowed access
- [ ] Cool-down triggers at 0 minutes
- [ ] Balance visible somewhere (or queryable in Room)

### Eltern-Modus Entry
- [ ] Easy access point to Eltern-Modus (e.g., long-press home, top-right icon)
- [ ] PIN setup/verify flow intuitive
- [ ] Parent can adjust time, enable/disable apps
- [ ] Changes immediately reflected in launcher

### Escape Prevention
- [ ] Settings menu blocked (PIN-gated)
- [ ] Notification shade blocked (fling-down removed)
- [ ] App info blocked (PIN-gated)
- [ ] No way to uninstall LAUNCHPAD (uninstall blocked or hidden)
- [ ] No way to install foreign launcher (restricted by pkg filtering)

### Security Sanity Checks
- [ ] PIN stored as hash, never plaintext
- [ ] Passwords/keys never logged
- [ ] Database queries are parameterized (no SQL injection)
- [ ] No hardcoded sensitive data

---

## Optional (Not M1, but verify if added early)

- [ ] Minecraft integration (M2)
- [ ] Zusagen UI (M2)
- [ ] Doge-Coins UI (M2)
- [ ] Time-tracking background service (M2)
- [ ] Advanced analytics (M4+)

---

## Failure Modes & Debugging

If verification fails, check:

| Symptom | Diagnosis | Fix |
|---------|-----------|-----|
| Build fails, unresolved Room imports | Room dependency missing | Check gradle.properties, fossify-commons version |
| No tables in database | Migration not running | Check Room.databaseBuilder, migration registered |
| No whitelisted apps visible | Filter logic broken or no apps in allowed_apps | Populate allowed_apps with system apps in migration |
| PIN always rejects correct PIN | Hash mismatch | Verify SecurityUtils hash implementation |
| Cool-down doesn't trigger | Time budget logic not wired | Check TimeBudgetManager integration in LaunchGate |
| WebView shows all sites | Allowlist/blocklist not enforced | Verify shouldInterceptRequest() is called |
| Notification shade still visible | expandNotificationsPanel not removed | Search MainActivity for "expandNotifications", remove |
| Manifest merge conflicts | Activities registered twice | Verify no duplicate intent-filter definitions |

---

## Performance Benchmarks (Target)

- [ ] App startup time: < 2 sec
- [ ] Home screen grid rendering: < 500 ms
- [ ] App launch (after gate check): < 1 sec
- [ ] PIN entry: instant response
- [ ] Cool-down timer: smooth 1 Hz update
- [ ] Room queries: < 100 ms for transaction list

---

## Sign-Off

Once all checks pass:

- [ ] Screenshot: Home screen with whitelisted apps
- [ ] Screenshot: Eltern-Modus with parent menu
- [ ] Screenshot: Cool-down countdown
- [ ] Screenshot: WebView blocked message
- [ ] Log: All unit tests passed
- [ ] Log: Gradle build successful
- [ ] Device: APK installed without errors

**Final confirmation**: LAUNCHPAD M1 is production-ready for testing on Poco X5.

---

## Next: M2 Planning

Once M1 verified:
1. Determine Zusagen + Doge-Coins UI/UX
2. Scope time-tracking background service
3. Plan Minecraft integration (if needed)
4. Decide on Wunschpfade feature set
5. Review cool-down rules JSON import syntax

See **15_m1_implementation_summary.md** for M2 timeline.

---

Good luck! Jake wird geliebt. ❤️
