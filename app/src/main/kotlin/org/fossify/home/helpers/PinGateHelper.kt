// File: app/src/main/kotlin/org/fossify/home/helpers/PinGateHelper.kt
// M1: PIN protection for sensitive menus
//
// NOTE (LAUNCHPAD audit fix): previously depended on org.fossify.commons.helpers.BaseConfig
// (whose `prefs` is not reliably public) and a non-existent SecurityUtils class. Now uses a
// dedicated SharedPreferences file and a self-contained salted SHA-256 hash. Menu id `hide`
// was corrected to the real `hide_icon`. A real 30-minute parent-mode timeout is enforced.

@file:Suppress("MagicNumber", "TooManyFunctions", "TooGenericExceptionCaught") // crypto params; fail-safe catches

package org.fossify.home.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.fossify.home.R
import java.security.MessageDigest
import java.security.SecureRandom

class PinGateHelper(
    private val context: Context
) {
    private val tag = "PinGateHelper"

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE
        )

    private val parentModeTimeoutMs = 30 * 60 * 1000L

    fun isPinConfigured(): Boolean =
        prefs.contains(LaunchpadPrefs.PREF_PARENT_LOCK_HASH)

    fun setPinCode(pin: String): Boolean {
        return try {
            // Generate a per-install salt the first time a PIN is set.
            val salt = prefs.getString(SALT_KEY, null) ?: generateSalt().also {
                prefs.edit().putString(SALT_KEY, it).apply()
            }
            prefs.edit().putString(LaunchpadPrefs.PREF_PARENT_LOCK_HASH, hashPin(pin, salt)).apply()
            Log.d(tag, "PIN configured successfully")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to set PIN", e)
            false
        }
    }

    fun verifyPin(enteredPin: String): Boolean {
        return try {
            val storedHash = prefs.getString(LaunchpadPrefs.PREF_PARENT_LOCK_HASH, "").orEmpty()
            val salt = prefs.getString(SALT_KEY, "").orEmpty()
            if (storedHash.isEmpty() || salt.isEmpty()) {
                Log.w(tag, "No PIN configured yet")
                return false
            }
            constantTimeEquals(hashPin(enteredPin, salt), storedHash)
        } catch (e: Exception) {
            Log.e(tag, "PIN verification error", e)
            false
        }
    }

    fun isParentModeActive(): Boolean {
        if (!prefs.getBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, false)) return false
        val activatedAt = prefs.getLong(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVATED_AT, 0L)
        if (System.currentTimeMillis() - activatedAt > parentModeTimeoutMs) {
            // Session expired — auto-deactivate.
            deactivateParentMode()
            return false
        }
        return true
    }

    fun activateParentMode(durationMinutes: Int = 30) {
        prefs.edit()
            .putBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, true)
            .putLong(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
        Log.d(tag, "Parent mode activated for $durationMinutes min")
    }

    fun deactivateParentMode() {
        prefs.edit().putBoolean(LaunchpadPrefs.PREF_PARENT_MODE_ACTIVE, false).apply()
        Log.d(tag, "Parent mode deactivated")
    }

    /**
     * Which menu actions require PIN. Ids must exist in menu_home_screen / menu_app_icon.
     */
    fun shouldGateAction(actionId: Int): Boolean {
        return when (actionId) {
            R.id.launcher_settings -> true
            R.id.set_as_default -> true
            R.id.app_info -> true
            R.id.uninstall -> true
            R.id.hide_icon -> true
            R.id.rename -> true
            R.id.remove -> true
            else -> false
        }
    }

    /**
     * Returns true if the action may proceed without a PIN prompt (parent mode active or
     * action is not gated). Returns false to signal the caller to show a PIN dialog.
     */
    fun checkMenuAction(actionId: Int): Boolean {
        // First-run safety: if no parent PIN has been set yet, do NOT gate — otherwise the
        // parent can never reach Settings to set the PIN up in the first place.
        if (!isPinConfigured()) return true
        if (isParentModeActive()) return true
        return !shouldGateAction(actionId)
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        private const val SALT_KEY = "parent_lock_salt"
    }
}
