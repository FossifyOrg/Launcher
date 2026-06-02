// File: app/src/main/kotlin/org/fossify/home/helpers/UsageTracker.kt
// M5 hook (now live): foreground-app detection via UsageStatsManager.

package org.fossify.home.helpers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Thin wrapper around UsageStatsManager so the time-tracking service can tell which app is in
 * the foreground. Requires the special-access PACKAGE_USAGE_STATS permission, which the parent
 * must grant manually in Settings > Usage access.
 */
object UsageTracker {

    /** True if the user has granted Usage Access to this app. */
    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the package name of the most recently foregrounded app within [lookbackMs], or
     * null if usage data is unavailable.
     */
    fun getForegroundPackage(context: Context, lookbackMs: Long = 60_000L): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val begin = end - lookbackMs
        return try {
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isForeground) {
                    latestPkg = event.packageName
                }
            }
            latestPkg
        } catch (e: Exception) {
            null
        }
    }
}
