// File: app/src/main/java/org/fossify/home/activities/MainActivity.kt (MODIFIED getAllAppLaunchers)
// M1: Whitelist filtering in getAllAppLaunchers()

package org.fossify.home.helpers

import android.content.pm.ApplicationInfo
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.LaunchpadConstants

/**
 * Modified getAllAppLaunchers() to implement whitelist filtering.
 *
 * INTEGRATION POINT in MainActivity.kt around line 1082:
 * Replace the existing return statement with whitelist filtering.
 *
 * Before:
 *   return apps
 *
 * After:
 *   return filterToWhitelist(apps)
 */

class AppWhitelistFilter(
    private val database: AppsDatabase
) {
    private val tag = "AppWhitelistFilter"

    /**
     * Filter app list to only include whitelisted apps.
     * Called from MainActivity.getAllAppLaunchers().
     *
     * Security model: DEFAULT-DENY
     * - Only apps explicitly in allowed_apps table and enabled=true appear
     * - System apps handled specially (launcher, settings, phone, messaging)
     * - Unknown apps hidden (not rejected at launch time, just hidden from grid)
     */
    suspend fun filterToWhitelist(apps: List<ApplicationInfo>): List<ApplicationInfo> {
        // Load whitelist from database (can cache with short TTL)
        val whitelist = database.allowedAppDao().getAllEnabledApps()
        val whitelistPackages = whitelist.map { it.packageName }.toSet()

        // System packages that are always allowed (essential functionality)
        val systemAllowed = setOf(
            "android.system.ui", // System UI
            "com.android.phone", // Phone
            "com.android.messaging", // SMS/MMS
            "com.android.contacts", // Contacts
            "com.android.clock", // Clock
            "com.android.calendar" // Calendar
        )

        return apps.filter { app ->
            val packageName = app.packageName

            // Check explicit whitelist
            if (whitelistPackages.contains(packageName)) {
                true
            }
            // Check system allowed list
            else if (systemAllowed.contains(packageName)) {
                true
            }
            // Check if it's the launcher itself (always available)
            else if (packageName == "org.fossify.home" || packageName == "com.inkandironglow.launchpad") {
                true
            }
            // All others hidden
            else {
                false
            }
        }
    }

    /**
     * Verify whitelist at launch time (hard gate).
     * Called from Activity.kt launchApp() before actually launching.
     */
    suspend fun isAppWhitelisted(packageName: String): Boolean {
        // Check explicit whitelist
        val isEnabled = database.allowedAppDao().isAppAllowed(packageName)
        if (isEnabled) return true

        // Check system allowed
        val systemAllowed = setOf(
            "android.system.ui",
            "com.android.phone",
            "com.android.messaging",
            "com.android.contacts",
            "com.android.clock",
            "com.android.calendar"
        )
        if (systemAllowed.contains(packageName)) return true

        // Check launcher itself
        if (packageName == "org.fossify.home" || packageName == "com.inkandironglow.launchpad") return true

        return false
    }

    /**
     * Get the category of an app (for time budget decisions).
     */
    suspend fun getAppCategory(packageName: String): String? {
        return database.allowedAppDao().getAppCategory(packageName)
    }
}

/**
 * Database DAO extensions for whitelist queries.
 * Add these to AppsDatabase or a dedicated AllowedAppsDao:
 */

// @Dao
// interface AllowedAppDao {
//     @Query("SELECT * FROM allowed_apps WHERE enabled = 1")
//     suspend fun getAllEnabledApps(): List<AllowedApp>
//
//     @Query("SELECT enabled FROM allowed_apps WHERE packageName = :packageName LIMIT 1")
//     suspend fun isAppAllowed(packageName: String): Boolean
//
//     @Query("SELECT category FROM allowed_apps WHERE packageName = :packageName LIMIT 1")
//     suspend fun getAppCategory(packageName: String): String?
//
//     @Insert(onConflict = OnConflictStrategy.REPLACE)
//     suspend fun insertApp(app: AllowedApp)
//
//     @Delete
//     suspend fun deleteApp(app: AllowedApp)
//
//     @Query("UPDATE allowed_apps SET enabled = :enabled WHERE packageName = :packageName")
//     suspend fun setAppEnabled(packageName: String, enabled: Boolean)
// }

/**
 * Escape route blocking:
 * The following menu items are removed/PIN-gated to prevent launcher circumvention.
 *
 * In MainActivity.showMainLongPressMenu() (line 840):
 *   - launcher_settings → PIN-gated
 *   - set_as_default → PIN-gated
 *   - widgets → can remain (low risk)
 *   - wallpapers → can remain (low risk)
 *
 * In Activity.handleGridItemPopupMenu() (line 82):
 *   - app_info → PIN-gated
 *   - uninstall → PIN-gated
 *   - hide → PIN-gated (change to "remove from launcher")
 *   - rename → PIN-gated
 *   - remove → PIN-gated
 *   - resize → can remain
 *
 * In MainActivity.onFlingDown() (line 1043):
 *   - expandNotificationsPanel (line 1056) → REMOVED (blocks notification shade)
 *
 * PIN-gating implemented via fossify-commons Security API in PinGateHelper.kt
 */
