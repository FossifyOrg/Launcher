# LAUNCHPAD M1 Integration Quickstart

**Generated**: 15 self-contained Kotlin/XML files ready to integrate into Fossify Launcher fork.

**Target**: Poco X5 with HyperOS, minSdk 26, targetSdk 36.

**Time to first build**: ~2 hours (Fossify baseline + module setup + file placement).

---

## Pre-Integration Checklist

- [ ] Fork `org.fossify:commons` v6.1.6 or have it available
- [ ] Fossify Launcher source code cloned locally
- [ ] Gradle 8.0+ installed
- [ ] Kotlin 1.9.0 or later
- [ ] Android SDK 36 available
- [ ] Emulator or Poco X5 device for testing

---

## Step 1: Fossify Baseline Setup (15 min)

1. **Clone Fossify Launcher** (or create new Android project from template)
2. **Keep existing structure**:
   - `org.fossify.home` package name (initially)
   - MainActivity.kt, Activity.kt extensions
   - AppsDatabase.kt
   - res/ resources

3. **Update gradle.properties**:
   - Copy 12_gradle_setup.gradle section for gradle.properties
   - Set `APP_VERSION_NAME=0.1.0-M1`
   - Set `APP_VERSION_CODE=1`

4. **Gradle build test**:
   ```bash
   ./gradlew clean build
   ```
   Should succeed with Fossify baseline.

---

## Step 2: Create Module Structure (10 min)

1. **Create :shared module** (Android Library):
   ```bash
   mkdir -p shared/src/main/java/org/fossify/launchpad/models
   mkdir -p shared/src/test/java/org/fossify/launchpad/models
   ```

2. **Create :companion module** (stub, Android App):
   ```bash
   mkdir -p companion/src/main/java/org/fossify/launchpad/companion
   ```

3. **Copy 12_gradle_setup.gradle**:
   - Update `settings.gradle` to include `:shared` and `:companion`
   - Create `shared/build.gradle.kts` from template
   - Create `companion/build.gradle.kts` from template

4. **Gradle build**:
   ```bash
   ./gradlew clean build
   ```
   Should succeed with three modules.

---

## Step 3: Shared Module Core (20 min)

Place these in `shared/src/main/java/org/fossify/launchpad/`:

| File | Destination | Description |
|------|-------------|-------------|
| 03_shared_module_core.kt | `models/KryptoCashModels.kt` | Data classes, TimeBudget, LedgerEntry |
| 04_no_regression_tests.kt | `../test/java/.../models/NoRegressionTest.kt` | 8 unit tests |
| 11_qr_pairing_protocol.kt | `crypto/QrPairingProtocol.kt` | RSA-AES-GCM pairing |

**Test**:
```bash
./gradlew :shared:test
```
All 8 tests should pass.

---

## Step 4: Database & Config (30 min)

Place in `app/src/main/java/org/fossify/home/`:

| File | Destination | Integration |
|------|-------------|-------------|
| 01_room_entities.kt | `databases/entities/LaunchpadEntities.kt` | New file |
| 02_config_keys.kt | `helpers/Constants.kt` | Append to existing or new file |
| 13_room_database_setup.kt | `databases/AppsDatabase.kt` | **MERGE**: Add migration 5→6, new DAOs |

**AppsDatabase.kt merge strategy**:
1. Keep existing entities (App, Widget, etc)
2. Add `@Entity` classes from 01_room_entities.kt to same file or separate file
3. Update `@Database` version from 5 to 6
4. Add `MIGRATION_5_6` from 13_room_database_setup.kt
5. Add DAO interfaces (AllowedAppDao, CryptoCashDao, etc)

**Build & test**:
```bash
./gradlew :app:build
```

---

## Step 5: Launch Gate & Whitelist (45 min)

Place in `app/src/main/java/org/fossify/home/`:

| File | Destination |
|------|-------------|
| 05_launch_gate_core.kt | `extensions/LaunchGate.kt` |
| 06_whitelist_filtering.kt | `activities/AppWhitelistFilter.kt` |

**Integrate into MainActivity.kt**:

**Line 1082** (in `getAllAppLaunchers()`):
```kotlin
// BEFORE:
return apps

// AFTER:
val filter = AppWhitelistFilter(database)
return runBlocking { filter.filterToWhitelist(apps) }
```

**Integrate into Activity.kt (extensions)**:

**Line 40** (in `launchApp()`):
```kotlin
// BEFORE:
startActivity(intent)

// AFTER:
val gate = LaunchGate(context, database)
val budget = TimeBudgetManager(context, database).getCurrentBudget()
val decision = runBlocking { gate.canLaunch(packageName, budget) }
if (decision.allowed) {
    startActivity(intent)
} else {
    gate.showDenialDialog(decision)
}
```

---

## Step 6: PIN-Gating & Menus (45 min)

Place in `app/src/main/java/org/fossify/home/`:

| File | Destination |
|------|-------------|
| 07_pin_gate_helper.kt | `helpers/PinGateHelper.kt` |

**Integrate into MainActivity.kt**:

**Line 840** (in `showMainLongPressMenu()`):
```kotlin
// Before showing menu, check PIN gate
val pinGate = PinGateHelper(this)
if (!pinGate.checkMenuAction(R.id.launcher_settings)) {
    // Show PIN entry dialog instead of menu
    showPinDialog {
        // Re-show menu after verification
        showMainLongPressMenu()
    }
    return
}
// Otherwise show menu normally
```

**Integrate into Activity.kt**:

**Line 82** (in `handleGridItemPopupMenu()`):
```kotlin
// Before handling menu items that need PIN
if (pinGate.shouldGateAction(menuItemId)) {
    showPinDialog {
        // Re-execute action after PIN verified
    }
    return true
}
// Otherwise handle action normally
```

**Line 1043-1056** (in `onFlingDown()`):
```kotlin
// REMOVE this block:
expandNotificationsPanel() // REMOVE - blocks notification shade
```

---

## Step 7: Parent Control Activity (30 min)

Place in `app/src/main/java/org/fossify/home/activities/`:

| File | Destination |
|------|-------------|
| 08_eltern_modus_activity.kt | `ElternModusActivity.kt` |

**Integrate into AndroidManifest.xml**:
```xml
<activity
    android:name="org.fossify.home.activities.ElternModusActivity"
    android:theme="@style/ElternTheme"
    android:launchMode="singleTask"
    android:exported="false"
    android:screenOrientation="portrait" />
```

**Trigger from MainActivity** (e.g., long-press Home icon):
```kotlin
val intent = Intent(this, ElternModusActivity::class.java)
startActivity(intent)
```

---

## Step 8: Cool-down Activity (20 min)

Place in `app/src/main/java/org/fossify/home/activities/`:

| File | Destination |
|------|-------------|
| 10_cooldown_activity.kt | `CooldownActivity.kt` |

**Integrate into AndroidManifest.xml**:
```xml
<activity
    android:name="org.fossify.home.activities.CooldownActivity"
    android:theme="@style/CooldownTheme"
    android:launchMode="singleInstance"
    android:exported="false"
    android:excludeFromRecents="true"
    android:screenOrientation="portrait" />
```

**Trigger when time expires** (in LaunchGate or TimeBudgetManager):
```kotlin
if (timeBudget.balanceMinutes <= 0) {
    val intent = Intent(context, CooldownActivity::class.java)
    intent.putExtra("cooldown_minutes", 15)
    context.startActivity(intent)
}
```

---

## Step 9: Safe Web Browsing (30 min)

Place in `app/src/main/java/org/fossify/home/`:

| File | Destination |
|------|-------------|
| 09_entdecken_webview.kt | `fragments/EntdeckenFragment.kt` |

**Add to AppsDatabase.kt**:
```kotlin
abstract fun exploreAllowlistDao(): ExploreAllowlistDao
abstract fun exploreBlocklistDao(): ExploreBlocklistDao
```

**Integrate into MainActivity** (e.g., as a tab or menu item):
```kotlin
val fragment = EntdeckenFragment()
supportFragmentManager.beginTransaction()
    .replace(R.id.content, fragment)
    .commit()
```

---

## Step 10: Manifest & Branding (20 min)

**14_manifest_updates.xml**:
- Merge new activities into `app/src/main/AndroidManifest.xml`
- Add permissions for WebView, camera (QR future)
- Add Device Admin receiver

**Branding**:
1. `res/values/strings.xml`:
   ```xml
   <string name="app_name">LAUNCHPAD</string>
   ```

2. `res/values/donottranslate.xml`:
   ```xml
   <string name="app_name">LAUNCHPAD</string>
   ```

3. **Brand font** (Luckiest Guy):
   - Download from Google Fonts
   - Place in `res/font/luckiest_guy.ttf`
   - Reference in `res/values/styles.xml`:
   ```xml
   <style name="LauncherTheme.Title">
       <item name="android:fontFamily">@font/luckiest_guy</item>
   </style>
   ```

4. **Icon** (rocket-scaled.png):
   - Place in `res/drawable/ic_launcher.png`
   - Update `app/src/main/AndroidManifest.xml`:
   ```xml
   android:icon="@drawable/ic_launcher"
   ```

---

## Step 11: Full Build & Deployment (30 min)

```bash
# Clean build all modules
./gradlew clean build

# Run unit tests
./gradlew :shared:test

# Build APK
./gradlew :app:assembleDebug

# Deploy to device/emulator
./gradlew :app:installDebug

# Launch
adb shell am start -n com.inkandironglow.launchpad/org.fossify.home.activities.MainActivity
```

---

## Step 12: Smoke Tests (20 min)

1. **App launches** → Home screen shows whitelisted apps only
2. **Long-press menu** → PIN required (if configured)
3. **Eltern-Modus** → Accessible, PIN setup works
4. **Time adjustment** → Creates transaction, balance updates
5. **Cool-down** → Triggers after time expires, blocks app launching
6. **WebView** → Blocks disallowed domains
7. **Launch gate** → Rejects non-whitelisted apps

---

## Integration Order (Recommended)

1. Fossify baseline + modules (Step 1-2) ✓
2. :shared module (Step 3) ✓
3. Database & config (Step 4) ✓
4. Launch gate & whitelist (Step 5) ✓
5. PIN-gating (Step 6) ✓
6. Eltern-Modus activity (Step 7) ✓
7. Cool-down activity (Step 8) ✓
8. Entdecken WebView (Step 9) ✓
9. Manifest & branding (Step 10) ✓
10. Build & deploy (Step 11) ✓
11. Smoke tests (Step 12) ✓

**Total time**: ~4-5 hours for experienced Android developer.

---

## If Stuck

Check:
1. Fossify 6.1.6 gradle dependencies available
2. Room entities match @Entity annotations
3. Kotlin version ≥ 1.9.0
4. Android SDK 36 available
5. Manifest merge no conflicts
6. Package names consistent (org.fossify.home internally for now)

---

## Next: M2 Prep

Once M1 builds successfully:

1. Create `.../models/Zusage.kt` and `.../models/DogeRequest.kt` UI screens
2. Implement JSON cool-down rule import
3. Add background service for time tracking
4. Minecraft integration (if scope)

See **15_m1_implementation_summary.md** for detailed M2 plan.

---

Good luck! Jake wird geliebt. ❤️
