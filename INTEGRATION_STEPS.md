# LAUNCHPAD M1 + M2 Integration in dein Fossify Fork

## Quick Copy-Paste Integration

```bash
# 1. Dein lokales Repo clonen (du hast es schon)
cd ~/your-workspace
git clone https://github.com/Lootziffer666/LAUNCHPAD.git your-launcher-fork
cd your-launcher-fork

# 2. M1 & M2 Dateien kopieren (aus /agent/workspace/)
# Option A: Wenn du die /agent/workspace Dateien noch hast:

cp /agent/workspace/impl/models/*.kt app/src/main/java/org/fossify/home/databases/entities/
cp /agent/workspace/impl/database/*.kt app/src/main/java/org/fossify/home/databases/
cp /agent/workspace/impl/activities/*.kt app/src/main/java/org/fossify/home/activities/
cp /agent/workspace/impl/fragments/*.kt app/src/main/java/org/fossify/home/fragments/
cp /agent/workspace/impl/helpers/*.kt app/src/main/java/org/fossify/home/helpers/
cp /agent/workspace/impl/crypto/*.kt shared/src/main/java/org/fossify/launchpad/crypto/
cp /agent/workspace/impl/m2/*.kt shared/src/main/java/org/fossify/launchpad/models/
cp /agent/workspace/impl/m2/CooldownRules.kt shared/src/main/java/org/fossify/launchpad/config/
cp /agent/workspace/impl/m2/TimeTrackingService.kt app/src/main/java/org/fossify/home/services/

# 3. Gradle-Setup
cp /agent/workspace/impl/build/gradle_setup.gradle ./gradle_setup_ref.gradle

# 4. Manifest mergen
# Manuell: impl/build/AndroidManifest_updates.xml in dein AndroidManifest.xml integrieren

# 5. Build
./gradlew clean build

# 6. Tests
./gradlew :shared:test  # Sollte 34 Tests bestätigen

# 7. Commit
git add .
git commit -m "Integrate LAUNCHPAD M1 + M2

- M1: Krypto-Cash ledger, launch gate, PIN-gating, Eltern-Modus, Cool-down, Entdecken, QR pairing
- M2: Zusagen, Doge-Coins, time-tracking, cool-down rules
- 34 unit tests passing
- Ready for device testing"

git push origin main
```

---

## Detaillierte Schritte

### 1️⃣ Projekt-Struktur vorbereiten

Falls noch nicht vorhanden, erstelle diese Verzeichnisse:

```bash
mkdir -p app/src/main/java/org/fossify/home/{databases/entities,activities,fragments,helpers,extensions,services}
mkdir -p shared/src/main/java/org/fossify/launchpad/{models,crypto,config}
mkdir -p shared/src/test/java/org/fossify/launchpad/models
```

### 2️⃣ M1 Dateien integrieren

**Modelle (shared)**:
```bash
# Krypto-Cash + No-Regression Tests
cp impl/models/KryptoCashModels.kt shared/src/main/java/org/fossify/launchpad/models/
cp impl/models/NoRegressionTest.kt shared/src/test/java/org/fossify/launchpad/models/
```

**Datenbank (app)**:
```bash
cp impl/database/LaunchpadEntities.kt app/src/main/java/org/fossify/home/databases/entities/
cp impl/database/Constants.kt app/src/main/java/org/fossify/home/helpers/  # Config-Keys
cp impl/database/AppsDatabase.kt app/src/main/java/org/fossify/home/databases/
# ⚠️  WICHTIG: Merge mit bestehender AppsDatabase.kt!
```

**Aktivitäten (app)**:
```bash
cp impl/activities/ElternModusActivity.kt app/src/main/java/org/fossify/home/activities/
cp impl/activities/CooldownActivity.kt app/src/main/java/org/fossify/home/activities/
```

**Fragments (app)**:
```bash
cp impl/fragments/EntdeckenFragment.kt app/src/main/java/org/fossify/home/fragments/
```

**Helpers (app)**:
```bash
cp impl/helpers/LaunchGate.kt app/src/main/java/org/fossify/home/helpers/
cp impl/helpers/AppWhitelistFilter.kt app/src/main/java/org/fossify/home/helpers/
cp impl/helpers/PinGateHelper.kt app/src/main/java/org/fossify/home/helpers/
```

**Crypto (shared)**:
```bash
cp impl/crypto/QrPairingProtocol.kt shared/src/main/java/org/fossify/launchpad/crypto/
```

### 3️⃣ M2 Dateien integrieren

**Models (shared)**:
```bash
cp impl/m2/ZusageModels.kt shared/src/main/java/org/fossify/launchpad/models/
cp impl/m2/DogeModels.kt shared/src/main/java/org/fossify/launchpad/models/
cp impl/m2/M2Tests.kt shared/src/test/java/org/fossify/launchpad/models/
```

**Config (shared)**:
```bash
cp impl/m2/CooldownRules.kt shared/src/main/java/org/fossify/launchpad/config/
```

**Activities (app)**:
```bash
cp impl/m2/ZusagenActivity.kt app/src/main/java/org/fossify/home/activities/
```

**Services (app)**:
```bash
mkdir -p app/src/main/java/org/fossify/home/services
cp impl/m2/TimeTrackingService.kt app/src/main/java/org/fossify/home/services/
```

---

### 4️⃣ Gradle konfigurieren

Aus `impl/build/gradle_setup.gradle`:

**settings.gradle**:
```gradle
include ':app'
include ':shared'
include ':companion'
```

**gradle.properties** (hinzufügen):
```properties
FOSSIFY_COMMONS_VERSION=6.1.6
ROOM_VERSION=2.6.1
LIFECYCLE_VERSION=2.7.0
COROUTINES_VERSION=1.8.0
```

**shared/build.gradle.kts**:
```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("kapt")
}

android {
    namespace = "org.fossify.launchpad"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.room:room-common:${findProperty("ROOM_VERSION")}")
    testImplementation("junit:junit:4.13.2")
}
```

---

### 5️⃣ Manifest integrieren

Aus `impl/build/AndroidManifest_updates.xml`:

Füge zu `app/src/main/AndroidManifest.xml` hinzu:

```xml
<!-- Neue Activities -->
<activity
    android:name="org.fossify.home.activities.ElternModusActivity"
    android:exported="false" />

<activity
    android:name="org.fossify.home.activities.CooldownActivity"
    android:exported="false"
    android:launchMode="singleInstance" />

<activity
    android:name="org.fossify.home.activities.ZusagenActivity"
    android:exported="false" />

<!-- Permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

---

### 6️⃣ MainActivity.kt integrieren

**Zeile ~1082** (getAllAppLaunchers):
```kotlin
// VORHER:
return apps

// NACHHER:
val filter = AppWhitelistFilter(database)
return runBlocking { filter.filterToWhitelist(apps) }
```

**Zeile ~840** (showMainLongPressMenu):
```kotlin
// PIN-Gate hinzufügen
val pinGate = PinGateHelper(this)
if (!pinGate.checkMenuAction(R.id.launcher_settings)) {
    showPinDialog { showMainLongPressMenu() }
    return
}
```

**Zeile ~1043-1056** (onFlingDown):
```kotlin
// ENTFERNEN:
// expandNotificationsPanel() // <- Diese Zeile löschen
```

---

### 7️⃣ Activity.kt integrieren

**Zeile ~40** (launchApp):
```kotlin
// Vor startActivity:
val gate = LaunchGate(context, database)
val budget = TimeBudgetManager(context, database).getCurrentBudget()
val decision = runBlocking { gate.canLaunch(packageName, budget) }
if (!decision.allowed) {
    gate.showDenialDialog(decision)
    return
}
startActivity(intent)
```

---

### 8️⃣ Build & Test

```bash
./gradlew clean build --info

# Sollte erfolgreich sein
```

**Tests laufen**:
```bash
./gradlew :shared:test

# Erwartet:
# 34 tests passed ✓
```

---

### 9️⃣ Commit

```bash
git status  # Kontrolle: welche Dateien geändert

git add app/ shared/ gradle.properties settings.gradle

git commit -m "Integrate LAUNCHPAD M1 + M2 into Fossify fork

Core Features (M1):
- Krypto-Cash ledger with No-Regression enforcement
- Launch gate (whitelist + time budget)
- PIN-gating (menu protection)
- Eltern-Modus activity
- Cool-down activity (15-min restorative phase)
- Entdecken WebView (safe browsing)
- QR pairing protocol (RSA + AES-256-GCM)

Family Features (M2):
- Zusagen (family promises, 24h auto-approval)
- Doge-Coins (SOG media approvals)
- Time-tracking service
- Cool-down rules (JSON config)

Test Coverage:
- 34 unit tests passing
- No-Regression validation
- Family feature flows

Ready for device testing on Poco X5"

git push origin main
```

---

## ⚠️ Wichtige Merge-Punkte

| Datei | Aktion | Wichtig |
|-------|--------|---------|
| AppsDatabase.kt | MERGE mit existing | ADD new entities + migration |
| MainActivity.kt | MODIFY (3 Punkte) | Whitelist, PIN, notification shade |
| Activity.kt | MODIFY (1 Punkt) | Launch gate check |
| AndroidManifest.xml | MERGE | ADD activities + permissions |

**Nicht überschreiben**, immer **mergen**!

---

## ✅ Erfolgs-Kriterien

- [ ] Gradle build succeeds
- [ ] 34 unit tests pass
- [ ] Manifest no conflicts
- [ ] No import errors
- [ ] APK builds
- [ ] App launches on device
- [ ] Home screen shows whitelisted apps only
- [ ] Long-press menu requires PIN
- [ ] Eltern-Modus accessible

---

## 🆘 Troubleshooting

**Build fails - unresolved imports**:
```bash
# Check gradle.properties
grep FOSSIFY_COMMONS_VERSION gradle.properties
# Should be: 6.1.6
```

**Tests fail**:
```bash
./gradlew :shared:test --info
# Check: LedgerState validation logic
```

**Manifest conflicts**:
```bash
# Android Studio → Build → Analyze APK
# Look for duplicate activity registrations
```

---

## 📞 Support

Alle Dateien sind im `/agent/workspace` Repository:
- Implementation: `impl/` 
- Documentation: `docs/guides/`

Bei Fragen → `docs/guides/00_INTEGRATION_QUICKSTART.md`

---

**Ready to integrate!** 🚀

`Jake wird geliebt. Nicht optimiert.` ❤️
