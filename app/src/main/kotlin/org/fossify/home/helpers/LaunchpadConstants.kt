// File: app/src/main/kotlin/org/fossify/home/helpers/LaunchpadConstants.kt
// M1: LAUNCHPAD config keys and constants

package org.fossify.home.helpers

object LaunchpadConstants {
    // Launcher modes
    const val MODE_KID = "kid"
    const val MODE_PARENT = "parent"

    // Lockdown levels
    const val LOCKDOWN_SOFT = "soft" // PIN-gating only
    const val LOCKDOWN_DEVICE_OWNER = "device_owner" // M5+: hard lock via Device Admin

    // Krypto-Cash defaults
    const val DEFAULT_WEEK_CAP_MINUTES = 120
    const val DEFAULT_SCHOOL_DAY_CAP_MINUTES = 60
    const val DEFAULT_COOLDOWN_DURATION_MINUTES = 15
    const val DEFAULT_EXPIRE_UNUSED_AFTER_DAYS = 30

    // Transaction types
    const val TX_TYPE_EARN = "EARN"
    const val TX_TYPE_SPEND = "SPEND"
    const val TX_TYPE_EXPIRE = "EXPIRE"
    const val TX_TYPE_CORRECTION = "CORRECTION"

    // App categories
    const val CATEGORY_ACTIVE_LEISURE = "ACTIVE_LEISURE" // High-stimulation: games, YouTube
    const val CATEGORY_CREATIVE = "CREATIVE" // Creative tools: drawing, music
    const val CATEGORY_LEARNING = "LEARNING" // Educational: school apps
    const val CATEGORY_COOLDOWN = "COOLDOWN" // Low-stimulation: audiobooks, drawing, LEGO
    const val CATEGORY_COMMUNICATION = "COMMUNICATION" // Messaging, video calls
    const val CATEGORY_NEUTRAL = "NEUTRAL" // System, clock, calendar

    // Command types
    const val COMMAND_ADJUST_TIME = "adjust_time"
    const val COMMAND_TOGGLE_APP = "toggle_app"
    const val COMMAND_SET_COOLDOWN_RULES = "set_cooldown_rules"
    const val COMMAND_CLEAR_CACHE = "clear_cache"

    // Command status
    const val CMD_STATUS_PENDING = "PENDING"
    const val CMD_STATUS_DELIVERED = "DELIVERED"
    const val CMD_STATUS_APPLIED = "APPLIED"
    const val CMD_STATUS_REJECTED = "REJECTED"
    const val CMD_STATUS_EXPIRED = "EXPIRED"
    const val CMD_STATUS_CONFLICT = "CONFLICT"

    // Zusage status
    const val ZUSAGE_ACTIVE = "ACTIVE"
    const val ZUSAGE_FULFILLED = "FULFILLED"
    const val ZUSAGE_EXPIRED = "EXPIRED"
    const val ZUSAGE_REVOKED = "REVOKED"

    // Doge request status
    const val DOGE_APPROVED = "APPROVED"
    const val DOGE_REJECTED = "REJECTED"
    const val DOGE_EXPIRED = "EXPIRED"

    // Explore categories
    const val EXPLORE_CATEGORY_EDUCATIONAL = "EDUCATIONAL"
    const val EXPLORE_CATEGORY_CREATIVE = "CREATIVE"
    const val EXPLORE_CATEGORY_ENTERTAINMENT = "ENTERTAINMENT"
    const val EXPLORE_CATEGORY_COMMUNICATION = "COMMUNICATION"

    // QR pairing
    const val QR_VERSION = "1"
    const val QR_PAIRING_NONCE_LENGTH_BYTES = 16
    const val QR_PAIRING_KEY_LENGTH_BYTES = 32 // AES-256

    // Cool-down allowed packages. NOTE: must be `val` not `const val` — Kotlin `const`
    // only permits primitives and String, never a List.
    val COOLDOWN_ALLOWED_PACKAGES = listOf(
        // Audiobooks
        "org.librarysimplified.r2.simplereader",
        "com.audible.application",
        // Drawing
        "com.ibis.paintx",
        "com.medibang.android.paint",
        // LEGO
        "com.lego.common"
    )
}

object LaunchpadPrefs {
    // SharedPreferences keys
    const val PREF_LAUNCHER_MODE = "launcher_mode" // kid or parent
    const val PREF_PARENT_LOCK_TYPE = "parent_lock_type" // PIN, etc
    const val PREF_PARENT_LOCK_HASH = "parent_lock_hash"
    const val PREF_BASE_TIME_MINUTES = "base_time_minutes"
    const val PREF_WEEK_CAP_MINUTES = "week_cap_minutes"
    const val PREF_SCHOOL_DAY_CAP_MINUTES = "school_day_cap_minutes"
    const val PREF_COOLDOWN_MINUTES = "cooldown_minutes"
    const val PREF_LOCKDOWN_LEVEL = "lockdown_level"
    const val PREF_PARENT_MODE_ACTIVE = "parent_mode_active"
    const val PREF_PARENT_MODE_ACTIVATED_AT = "parent_mode_activated_at"
    const val PREF_LAST_SYNC_QR = "last_sync_qr"
    const val PREF_COOLDOWN_RULES_JSON = "cooldown_rules_json"
    const val PREF_COOLDOWN_UNTIL = "cooldown_until" // epoch ms; cool-down active while now < value
    const val PREF_KIOSK_ENABLED = "kiosk_enabled" // M3: lock-task kiosk mode (device owner only)
    // Master "Kindermodus" switch. OFF by default so the launcher behaves normally during
    // setup (all apps visible, no launch gate, no time metering). The parent turns it ON in
    // Eltern-Modus after configuring the whitelist + PIN + time budget.
    const val PREF_ENFORCEMENT_ENABLED = "enforcement_enabled"

    // M4: QR pairing — launcher keypair (Base64), AES session key, paired parent identity
    const val PREF_PAIR_PRIVATE_KEY = "pair_private_key" // PKCS8 Base64
    const val PREF_PAIR_PUBLIC_KEY = "pair_public_key" // X509 Base64
    const val PREF_PAIR_SESSION_KEY = "pair_session_key" // AES raw Base64
    const val PREF_PAIR_PARENT_ID = "pair_parent_id"
    const val PREF_PAIR_NONCE = "pair_nonce"

    // Dedicated SharedPreferences file for LAUNCHPAD (separate from commons config).
    const val PREFS_FILE = "launchpad_prefs"
}
