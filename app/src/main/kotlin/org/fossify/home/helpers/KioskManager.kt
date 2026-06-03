// File: app/src/main/kotlin/org/fossify/home/helpers/KioskManager.kt
// M3: Device-Owner + Lock-Task hardening.
//
// An app cannot make itself Device Owner at runtime — that is a one-time provisioning step
// (ADB `dpm set-device-owner …` on a fresh device, or QR provisioning). Everything here is
// guarded by isDeviceOwner(): on a non-provisioned device these calls no-op so the launcher
// still runs as a normal (soft-mode) launcher. When provisioned, the parent can toggle a true
// kiosk: lock-task pinned to the launcher + whitelisted apps, status bar disabled, launcher
// uninstall blocked, safe-boot / add-user restricted.

// Device-policy literals + intentional fail-safe catches.
@file:Suppress("MagicNumber", "TooManyFunctions", "TooGenericExceptionCaught")

package org.fossify.home.helpers

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.receivers.LockDeviceAdminReceiver

object KioskManager {
    private const val TAG = "KioskManager"
    private const val ADMIN_CLASS = "org.fossify.home.receivers.LockDeviceAdminReceiver"

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(context: Context) =
        ComponentName(context.applicationContext, LockDeviceAdminReceiver::class.java)

    private fun prefs(context: Context) =
        context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)

    fun isDeviceOwner(context: Context): Boolean =
        try {
            dpm(context).isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            android.util.Log.w("LAUNCHPAD", "isDeviceOwner check failed", e)
            false
        }

    fun isKioskEnabled(context: Context): Boolean =
        prefs(context).getBoolean(LaunchpadPrefs.PREF_KIOSK_ENABLED, false)

    fun setKioskEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(LaunchpadPrefs.PREF_KIOSK_ENABLED, enabled).apply()
    }

    /**
     * Allowlist the launcher + every enabled whitelisted app for lock-task, so launched apps
     * stay inside the locked session and everything else is blocked. Device-owner only.
     */
    suspend fun applyLockTaskAllowlist(context: Context, database: AppsDatabase) {
        if (!isDeviceOwner(context)) return
        val packages = database.allowedAppDao().getAllEnabledApps()
            .map { it.packageName }
            .toMutableSet()
        packages.add(context.packageName)
        try {
            dpm(context).setLockTaskPackages(admin(context), packages.toTypedArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Keep HOME working (returns to launcher); block recents/notifications/etc.
                dpm(context).setLockTaskFeatures(
                    admin(context),
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "setLockTaskPackages failed", e)
        }
    }

    /** Reversible hardening applied while kiosk is on. Device-owner only. */
    fun applyRestrictions(context: Context) {
        if (!isDeviceOwner(context)) return
        val d = dpm(context)
        val a = admin(context)
        runCatching { d.setStatusBarDisabled(a, true) }
        runCatching { d.setUninstallBlocked(a, context.packageName, true) }
        runCatching { d.addUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { d.addUserRestriction(a, UserManager.DISALLOW_ADD_USER) }
        // Deliberately NOT restricting factory reset, so a parent always has a recovery path.
    }

    fun clearRestrictions(context: Context) {
        if (!isDeviceOwner(context)) return
        val d = dpm(context)
        val a = admin(context)
        runCatching { d.setStatusBarDisabled(a, false) }
        runCatching { d.setUninstallBlocked(a, context.packageName, false) }
        runCatching { d.clearUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { d.clearUserRestriction(a, UserManager.DISALLOW_ADD_USER) }
    }

    /**
     * Called from the launcher's onResume. If kiosk is enabled and we are device owner, refresh
     * the allowlist, apply restrictions, and enter lock-task (silently, no user prompt).
     */
    fun onLauncherResumed(activity: Activity, database: AppsDatabase) {
        if (!isKioskEnabled(activity) || !isDeviceOwner(activity)) return
        try {
            runBlocking { applyLockTaskAllowlist(activity, database) }
            applyRestrictions(activity)
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                activity.startLockTask()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onLauncherResumed kiosk start failed", e)
        }
    }

    /** Turn kiosk off: leave lock-task (if active) and clear restrictions. */
    fun stopKiosk(activity: Activity) {
        runCatching {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                activity.stopLockTask()
            }
        }
        clearRestrictions(activity)
    }

    /** The exact ADB command a parent runs once to provision Device Owner. */
    fun deviceOwnerSetupCommand(context: Context): String =
        "adb shell dpm set-device-owner ${context.packageName}/$ADMIN_CLASS"
}
