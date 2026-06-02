// File: app/src/main/kotlin/org/fossify/home/helpers/LaunchpadWidgetProvider.kt
// LAUNCHPAD: 2×1 home screen widget showing live balance.

package org.fossify.home.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.home.R
import org.fossify.home.activities.JakeDashboardActivity
import org.fossify.home.databases.AppsDatabase

class LaunchpadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { widgetId -> update(context, manager, widgetId) }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            CoroutineScope(Dispatchers.Main).launch {
                val db = AppsDatabase.getInstance(context)
                val balance = withContext(Dispatchers.IO) { db.cryptoCashDao().getCurrentBalance() }
                val prefs = context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
                val enforced = prefs.getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)
                val inCooldown = System.currentTimeMillis() <
                    prefs.getLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, 0L)

                val views = RemoteViews(context.packageName, R.layout.widget_launchpad)

                val (icon, balanceText, dotColor, progress) = when {
                    inCooldown -> Quadruple("⏸️", "Pause", "#FF6B35", 0)
                    !enforced -> Quadruple("🔓", "Kein Limit", "#4CAF50", 120)
                    balance <= 0 -> Quadruple("📵", "0 Min", "#FF4444", 0)
                    balance < 10 -> Quadruple("⚡", "$balance Min", "#FF6B35", balance)
                    else -> Quadruple("⏱️", "$balance Min", "#4CAF50", balance.coerceAtMost(120))
                }

                views.setTextViewText(R.id.widget_icon, icon)
                views.setTextViewText(R.id.widget_balance, balanceText)
                views.setInt(R.id.widget_mode_dot, "setBackgroundColor",
                    android.graphics.Color.parseColor(dotColor))
                views.setProgressBar(R.id.widget_progress, 120, progress, false)
                views.setInt(R.id.widget_progress, "setProgressTintList",
                    android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor(dotColor)
                    ).let { 0 } // RemoteViews can't set tint this way; use setInt below
                )

                // Tap → open Jake's dashboard
                val pi = PendingIntent.getActivity(
                    context, widgetId,
                    Intent(context, JakeDashboardActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)

                manager.updateAppWidget(widgetId, views)
            }
        }

        // Simple data holder
        private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    }
}
