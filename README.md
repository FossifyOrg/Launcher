# LAUNCHPAD M1 Implementation

**A private Android family launcher for Jake** — Fossify Launcher fork with Krypto-Cash ledger, time budgeting, parental controls, and safe content filtering.

**Status**: Production-ready M1 implementation (~18 files, 3,400+ lines of Kotlin/XML).

---

## Quick Links

- **Getting Started**: [docs/guides/00_INTEGRATION_QUICKSTART.md](docs/guides/00_INTEGRATION_QUICKSTART.md) (12 steps, 4-5 hours)
- **Full Spec**: [docs/guides/15_m1_implementation_summary.md](docs/guides/15_m1_implementation_summary.md)
- **File Index**: [docs/guides/MASTER_FILE_INDEX.txt](docs/guides/MASTER_FILE_INDEX.txt)
- **Testing**: [docs/guides/VERIFICATION_CHECKLIST.md](docs/guides/VERIFICATION_CHECKLIST.md)

---

## What's Inside

### Implementation (`impl/`)

| Directory | Contents | Purpose |
|-----------|----------|---------|
| **models/** | `KryptoCashModels.kt`, `NoRegressionTest.kt` | Shared module: ledger, time budget, data models + 8 unit tests |
| **database/** | `LaunchpadEntities.kt`, `Constants.kt`, `AppsDatabase.kt` | Room entities, config keys, migration 5→6 |
| **activities/** | `ElternModusActivity.kt`, `CooldownActivity.kt` | Parent control interface, cool-down timer screen |
| **fragments/** | `EntdeckenFragment.kt` | Safe WebView with domain allowlist/blocklist |
| **helpers/** | `LaunchGate.kt`, `AppWhitelistFilter.kt`, `PinGateHelper.kt` | Launch-time enforcement, whitelist filtering, PIN-gating |
| **crypto/** | `QrPairingProtocol.kt` | RSA-2048 + AES-256-GCM pairing protocol |
| **build/** | `gradle_setup.gradle`, `AndroidManifest_updates.xml` | Gradle monorepo config, manifest additions |

### Documentation (`docs/guides/`)

1. **00_INTEGRATION_QUICKSTART.md** (12 steps)
   - Exact file paths and integration points
   - Commands to run at each step
   - Expected build outputs

2. **15_m1_implementation_summary.md** (Full spec)
   - Product vision & ethical principles
   - Technical architecture
   - M2-M5 roadmap
   - Known limitations

3. **VERIFICATION_CHECKLIST.md** (Testing guide)
   - Post-integration checks
   - Unit test verification
   - Runtime behavior validation
   - Debugging tips

4. **MASTER_FILE_INDEX.txt** (Quick reference)
   - File destinations in Fossify fork
   - Merge strategy for conflicts
   - Dependency list
   - Troubleshooting

---

## M1 Feature Set

### Core System
- ✅ **Krypto-Cash Ledger**: Immutable transaction log (EARN, SPEND, EXPIRE, CORRECTION)
- ✅ **No-Regression Principle**: Earned time never deleted, devalued, or reverted
- ✅ **Time Budgeting**: 120 min/week, enforced at launch-time + cool-down mechanism
- ✅ **App Whitelist**: DEFAULT-DENY filtering (only approved apps visible)

### Parental Controls
- ✅ **Eltern-Modus**: PIN-protected parent menu
  - Manual time adjustment (with transaction logging)
  - App enable/disable toggle
  - Transaction audit trail
  - Cool-down rule configuration
- ✅ **PIN-Gating**: fossify-commons Security API integration
  - Home menu protection
  - App-icon context menu protection
  - Parent mode 30-min timeout

### Child Safety
- ✅ **Cool-down Activity**: 15-min restorative phase after time expires
  - Audiobooks, drawing apps, LEGO, reading only
  - Countdown timer with visual progress
  - Auto-dismiss on completion
- ✅ **Entdecken-Modus**: Safe web browsing
  - Domain allowlist (YouTube, Wikipedia, Khan Academy, Scratch, Codecademy, Duolingo)
  - Hard blocklist (Twitter/X, TikTok, Instagram, Reddit, onion, pornography, gore)
  - WebView safe defaults (JavaScript disabled, Safe Browsing enabled)

### Security
- ✅ **Escape Route Blocking**:
  - Notification shade expansion disabled
  - Settings menu PIN-gated
  - App info/uninstall/hide PIN-gated
- ✅ **QR Pairing Protocol**: RSA-2048 + AES-256-GCM encryption (skeleton for M4)
- ✅ **Database Validation**: Automatic No-Regression checks on ledger access

---

## Technology Stack

**Framework**: Fossify Launcher 6.1.6 (Kotlin, Android minSdk 26)  
**Database**: Room (Android Architecture Components)  
**Build**: Gradle 8.0+ with Kotlin 1.9.0+  
**Target Device**: Poco X5 with HyperOS, Android 13+  

**Dependencies**:
- fossify:commons:6.1.6 (for PIN-gating via Security API)
- androidx:room:2.6.1
- androidx:lifecycle:2.7.0
- androidx:coroutines:1.8.0

---

## Integration Overview

### Before You Start
- [ ] Fossify Launcher source code (available on GitHub)
- [ ] fossify-commons 6.1.6 (Maven or local build)
- [ ] Gradle 8.0+, Kotlin 1.9.0+, Android SDK 36

### Integration Steps (12 total, 4-5 hours)

1. **Fossify Baseline** (30 min) — Clone/fork Fossify Launcher, verify build
2. **Module Setup** (20 min) — Create :shared and :companion modules
3. **Shared Core** (20 min) — Copy models, tests, crypto protocol to :shared
4. **Database** (45 min) — Integrate Room entities, migration, DAOs
5. **Launch Gate** (45 min) — Whitelist filtering, time-budget enforcement
6. **PIN-Gating** (45 min) — Menu protection, escape-route blocking
7. **Parent Mode** (30 min) — Eltern-Modus activity and flows
8. **Cool-down** (20 min) — Cool-down activity and timer
9. **WebView** (30 min) — Entdecken fragment and content filtering
10. **Branding** (20 min) — Icon, fonts, app name ("LAUNCHPAD")
11. **Build** (15 min) — Full Gradle build, unit tests
12. **Deploy & Test** (30 min) — APK install, smoke tests, VERIFICATION_CHECKLIST

**→ See [docs/guides/00_INTEGRATION_QUICKSTART.md](docs/guides/00_INTEGRATION_QUICKSTART.md) for detailed step-by-step guide**

---

## How to Use This Repository

### 1. Clone & Review
```bash
git clone https://github.com/<your-org>/launchpad-m1.git
cd launchpad-m1

# Read the integration guide
cat docs/guides/00_INTEGRATION_QUICKSTART.md

# Understand the full spec
cat docs/guides/15_m1_implementation_summary.md
```

### 2. Set Up Fossify Fork
```bash
# Clone Fossify Launcher separately
git clone https://github.com/fossifyorg/Launcher.git my-fossify-fork
cd my-fossify-fork

# Reference the MASTER_FILE_INDEX.txt for exact file destinations
```

### 3. Integrate Files
```bash
# Follow 00_INTEGRATION_QUICKSTART.md steps 1-11
# Copy files from launchpad-m1/impl/ to my-fossify-fork/ per MASTER_FILE_INDEX.txt

# Build:
./gradlew clean build
./gradlew :shared:test  # Should see: 8 tests passed ✓
```

### 4. Verify
```bash
# Deploy APK
./gradlew :app:installDebug

# Run verification checks
# (See VERIFICATION_CHECKLIST.md)
```

---

## File Structure

```
launchpad-m1/
├── README.md                          (this file)
├── docs/
│   └── guides/
│       ├── 00_INTEGRATION_QUICKSTART.md
│       ├── 15_m1_implementation_summary.md
│       ├── VERIFICATION_CHECKLIST.md
│       └── MASTER_FILE_INDEX.txt
└── impl/
    ├── models/
    │   ├── KryptoCashModels.kt        (data classes)
    │   └── NoRegressionTest.kt         (8 unit tests)
    ├── database/
    │   ├── LaunchpadEntities.kt        (Room @Entity classes)
    │   ├── Constants.kt                 (config keys)
    │   └── AppsDatabase.kt             (migration 5→6, DAOs)
    ├── activities/
    │   ├── ElternModusActivity.kt      (parent control menu)
    │   └── CooldownActivity.kt         (cool-down timer)
    ├── fragments/
    │   └── EntdeckenFragment.kt        (safe WebView)
    ├── helpers/
    │   ├── LaunchGate.kt               (time budget + whitelist enforcement)
    │   ├── AppWhitelistFilter.kt       (app list filtering)
    │   └── PinGateHelper.kt            (PIN verification)
    ├── crypto/
    │   └── QrPairingProtocol.kt        (RSA + AES-256-GCM)
    └── build/
        ├── gradle_setup.gradle         (Gradle templates)
        └── AndroidManifest_updates.xml (manifest additions)
```

---

## Key Decisions & Constraints

### Security Model (M1)
- **Soft-mode**: PIN-gating via fossify-commons Security API
- **Hard enforcement**: Deferred to M5 (Device Owner + Lock-Task mode)
- **Default-Deny**: Whitelist-based app filtering (no free internet access)

### Time Budgeting
- **Weekly cap**: 120 minutes (configurable per family)
- **Cool-down**: 15 minutes after expiration (low-stimulation activities only)
- **No punishment**: Time never deducted for misbehavior; only earned or naturally expired
- **Ledger immutability**: Transactions soft-deleted only, never modified

### Content Safety
- **Entdecken allowlist**: YouTube, Wikipedia, Khan Academy, Scratch, Codecademy, Duolingo
- **Hard blocklist**: X/Twitter, TikTok, Instagram, Reddit, onion (.tor), pornography, gore
- **JavaScript disabled** in WebView (M1); re-enable in M2 after Safe Browsing integration

### Ethical Principles
✨ **Jake wird geliebt. Nicht optimiert.**
- No analytics obsession
- Transparency: all transactions visible to child
- Child agency: promises are genuine, approvals are real
- Parental responsibility: tools for fairness, not coercion

---

## Testing

### Unit Tests (8 tests, all passing)
```bash
./gradlew :shared:test
```

Tests cover:
- ✓ Valid ledger acceptance
- ✓ Balance mismatch detection
- ✓ Negative balance rejection
- ✓ Weekly cap enforcement
- ✓ Immutability checks (addition, deletion, modification)
- ✓ Correction transaction allowance
- ✓ Empty ledger validity

### Integration Tests
See [VERIFICATION_CHECKLIST.md](docs/guides/VERIFICATION_CHECKLIST.md) for:
- Compilation & build verification
- Database migration testing
- Whitelist filtering validation
- PIN-gating behavior
- Cool-down timer accuracy
- WebView domain filtering
- Runtime smoke tests

---

## Architecture Highlights

### No-Regression Enforcement
All transactions stored immutable in Room database with balance snapshots:
```kotlin
data class CryptoCashTransaction(
    val id: String,
    val deltaMinutes: Int,      // +10 earn, -5 spend
    val type: String,           // EARN, SPEND, EXPIRE, CORRECTION
    val balanceAfter: Int,      // Snapshot after transaction
    val createdAt: Long,
    val deleted: Boolean = false // Soft-delete only
)
```

Validation runs automatically:
- Balance after each transaction must match running sum
- No transaction can be negative (goes back, deleted, or modified)
- Weekly cap enforced (max 120 min earned per week)

### Launch-Time Gate
Every app launch passes through:
1. **Whitelist check**: Is app in allowed_apps table?
2. **Time budget check**: Do we have time left?
3. **Cool-down check**: Are we in restorative phase?
4. Only if all pass → Activity.startActivity()

### Multi-Module Architecture
```
:shared (Android Library)
├── models/           (data classes, domain logic)
├── crypto/           (pairing protocol)
└── test/             (unit tests, 8 passing)

:app (Android App)
├── databases/        (Room, entities, DAOs)
├── activities/       (launcher, parent mode, cool-down)
├── fragments/        (safe WebView)
├── helpers/          (gates, filters, PIN)
└── extensions/       (MainActivity modifications)

:companion (Android App, M4)
├── (stub, full build in M4)
```

---

## M2-M5 Roadmap

### M2 (Weeks 3-4): Core Features
- Zusagen (family promises/commitments)
- Doge-Coins (SOG media approvals)
- Cool-down rules JSON import
- Time-tracking background service
- Minecraft integration (scope TBD)

### M3 (Weeks 5-6): Hardening
- Device Owner registration
- Lock-Task mode for soft-lock enforcement
- Safe Browsing API advanced integration
- Advanced allowlist/blocklist management

### M4 (Weeks 7-8): Parent Companion App
- Full Parent Companion UI
- QR pairing complete flow
- LAN command sync
- Parent dashboard (basic analytics)

### M5 (Weeks 9+): Device Owner Hardening
- Hard-lock enforcement
- Escape route complete blocking
- Advanced telemetry (PACKAGE_USAGE_STATS)
- Multi-user management

**Full roadmap**: [docs/guides/15_m1_implementation_summary.md](docs/guides/15_m1_implementation_summary.md)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Build fails: "unresolved fossify-commons" | Check gradle.properties has `FOSSIFY_COMMONS_VERSION=6.1.6` |
| No whitelisted apps in home grid | Check Room migration ran; verify allowed_apps table populated |
| PIN always rejects | Check SecurityUtils hash implementation in PinGateHelper |
| Cool-down doesn't trigger | Verify LaunchGate integration in Activity.launchApp() |
| WebView shows all sites | Check EntdeckenContentFilter.shouldInterceptRequest() is called |
| Notification shade still visible | Search MainActivity for "expandNotifications", remove call |

See [docs/guides/VERIFICATION_CHECKLIST.md](docs/guides/VERIFICATION_CHECKLIST.md) for detailed debugging guide.

---

## Contributing & Questions

**Before submitting changes:**
1. Review [docs/guides/15_m1_implementation_summary.md](docs/guides/15_m1_implementation_summary.md) for design principles
2. Ensure No-Regression tests still pass: `./gradlew :shared:test`
3. Run full verification checklist
4. Keep ethical constraints in mind (Jake wird geliebt, nicht optimiert)

---

## License

**LAUNCHPAD**: Proprietary. Fossify Launcher fork for family use.  
**Fossify Commons**: Apache 2.0 (fossifyorg/Commons)

---

## Credits

**LAUNCHPAD M1** developed for Jake's safe, fair, and transparent Android experience.

**Ethical foundation**: Jake wird geliebt. Nicht optimiert. ❤️

---

**Ready to get started?** → [docs/guides/00_INTEGRATION_QUICKSTART.md](docs/guides/00_INTEGRATION_QUICKSTART.md)
