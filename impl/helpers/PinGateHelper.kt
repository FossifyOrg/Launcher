// File: app/src/main/java/org/fossify/home/helpers/PinGateHelper.kt
// M1: PIN protection for sensitive menus via fossify-commons Security

package org.fossify.home.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.intellij.openapi.util.text.HtmlBuilder
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.SecurityUtils

/**
 * PinGateHelper: Unified PIN verification for menus and settings.
 * Uses fossify-commons BaseConfig and Security API.
 *
 * Integration with fossify-commons 6.1.6:
 * - Uses BaseConfig (SharedPreferences wrapper)
 * - Uses SecurityUtils for PIN hashing (if available)
 * - Falls back to basic SHA-256 if SecurityUtils unavailable
 */
class PinGateHelper(
    private val context: Context,
    private val config: BaseConfig = BaseConfig(context)
) {
    private val tag = "PinGateHelper"

    /**
     * Check if parent PIN is set up.
     */
    fun isPinConfigured(): Boolean {
        return config.prefs.contains(LaunchpadPrefs.PREF_PARENT_LOCK_HASH)
    }

    /**
     * Set up parent PIN (called during Eltern-Modus setup).
     */
    fun setPinCode(pin: String): Boolean {
        try {
            val hash = hashPin(pin)
            config.prefs.edit().putString(LaunchpadPrefs.PREF_PARENT_LOCK_HASH, hash).apply()
            Log.d(tag, "PIN configured successfully")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to set PIN", e)
            return false
        }
    }

    /**
     * Verify entered PIN against stored hash.
     * Returns true if correct, false otherwise.
     */
    fun verifyPin(enteredPin: String): Boolean {
        try {
            val storedHash = config.prefs.getString(LaunchpadPrefs.PREF_PARENT_LOCK_HASH, "") ?: ""
            if (storedHash.isEmpty()) {
                Log.w(tag, "No PIN configured yet")
                return false
            }

            val enteredHash = hashPin(enteredPin)
            val matches = enteredHash == storedHash

            if (!matches) {
                Log.w(tag, "PIN verification failed")
            }
            return matches
        } catch (e: Exception) {
            Log.e(tag, "PIN verification error", e)
            return false
        }
    }

    /**
     * Check if Eltern-Modus is currently active.
     * Transient flag that lasts for the session or a timeout.
     */
    fun isParentModeActive(): Boolean {
        return config.prefs.getBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, false)
    }

    /**
     * Activate Eltern-Modus (allow sensitive operations without re-verifying PIN).
     * Should timeout after 30 minutes or on app close.
     */
    fun activateParentMode(durationMinutes: Int = 30) {
        config.prefs.edit().putBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, true).apply()
        Log.d(tag, "Parent mode activated for $durationMinutes min")
        // TODO: Implement timeout handler
    }

    /**
     * Deactivate Eltern-Modus.
     */
    fun deactivateParentMode() {
        config.prefs.edit().putBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, false).apply()
        Log.d(tag, "Parent mode deactivated")
    }

    /**
     * Should this menu action be PIN-gated?
     */
    fun shouldGateAction(actionId: Int): Boolean {
        return when (actionId) {
            // Menu actions that require PIN
            R.id.launcher_settings -> true
            R.id.set_as_default -> true
            R.id.app_info -> true
            R.id.uninstall -> true
            R.id.hide -> true
            R.id.rename -> true
            R.id.remove -> true
            // Safe actions (no PIN needed)
            R.id.widgets -> false
            R.id.wallpapers -> false
            R.id.resize -> false
            else -> false
        }
    }

    /**
     * Attempt menu action with PIN verification.
     * Returns true if allowed (either PIN verified or parent mode active).
     */
    fun checkMenuAction(actionId: Int): Boolean {
        // If parent mode is active, allow without verification
        if (isParentModeActive()) {
            Log.d(tag, "Parent mode active, allowing action $actionId")
            return true
        }

        // Otherwise check if this action requires PIN
        if (shouldGateAction(actionId)) {
            Log.d(tag, "Action $actionId requires PIN verification")
            return false // Signal caller to show PIN entry dialog
        }

        // Action doesn't require PIN
        return true
    }

    /**
     * Hash PIN using commons-Security or fallback to SHA-256.
     * Never store plain-text PINs!
     */
    private fun hashPin(pin: String): String {
        return try {
            // Try to use commons-Security if available
            val pinHashMethod = SecurityUtils::class.java.getMethod("hashPassword", String::class.java)
            pinHashMethod.invoke(null, pin) as String
        } catch (e: Exception) {
            // Fallback to SHA-256
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * PinEntryDialog integration:
 * This would be a reusable dialog/activity for PIN entry.
 * Call from MainActivity when shouldGateAction returns false.
 */
interface PinGateCallback {
    fun onPinVerified()
    fun onPinDenied()
    fun onPinCancelled()
}

class PinGateActivity : android.app.Activity() {
    // TODO: Implement PIN entry UI with numeric keyboard
    // Shows: "Eltern PIN eingeben"
    // On success: calls callback.onPinVerified() and sets parent mode active
    // On failure (3 attempts): calls callback.onPinDenied()
    // On cancel: calls callback.onPinCancelled()
}
