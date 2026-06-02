// File: app/src/main/kotlin/org/fossify/home/helpers/LaunchpadWidgetProvider.kt
// LAUNCHPAD: 2×1 home screen widget showing live balance.

package org.fossify.home.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import org.fossify.home.R
import org.fossify.home.activities.JakeDashboardActivity
import org.fossify.home.databases.AppsDatabase

class LaunchpadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // AppWidgetProvider is a BroadcastReceiver — its process may be killed immediately
        // after onUpdate() returns. Coroutines launched here would be orphaned before completing.
        // runBlocking is safe: the DB read is < 10ms on a local SQLite file.
        ids.forEach { id -> runBlocking { render(context, manager, id) } }
    }

    companion object {
        /** Call this whenever the balance changes (e.g. after adding time). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LaunchpadWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                // Re-broadcast the update intent so onUpdate() fires.
                Intent(context, LaunchpadWidgetProvider::class.java).also {
                    it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    it.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    context.sendBroadcast(it)
                }
            }
        }
    }
}

private suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
    val db = AppsDatabase.getInstance(context)
    val balance = db.cryptoCashDao().getCurrentBalance()
    val prefs = context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
    val enforced = prefs.getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)
    val inCooldown = System.currentTimeMillis() < prefs.getLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, 0L)

    data class State(val icon: String, val text: String, val hex: String, val progress: Int)
    val s = when {
        inCooldown    -> State("⏸️", "Pause",       "#FF6B35", 0)
        !enforced     -> State("🔓", "Kein Limit",  "#4CAF50", 120)
        balance <= 0  -> State("📵", "0 Min",       "#FF4444", 0)
        balance < 10  -> State("⚡", "$balance Min", "#FF6B35", balance)
        else          -> State("⏱️", "$balance Min", "#4CAF50", balance.coerceAtMost(120))
    }
    val color = Color.parseColor(s.hex)

    val views = RemoteViews(context.packageName, R.layout.widget_launchpad)
    views.setTextViewText(R.id.widget_icon, s.icon)
    views.setTextViewText(R.id.widget_balance, s.text)
    views.setInt(R.id.widget_mode_dot, "setBackgroundColor", color)
    views.setProgressBar(R.id.widget_progress, 120, s.progress, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        views.setColorStateList(
            R.id.widget_progress, "setProgressTintList",
            android.content.res.ColorStateList.valueOf(color)
        )
    }

    val pi = PendingIntent.getActivity(
        context, widgetId,
        Intent(context, JakeDashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pi)
    manager.updateAppWidget(widgetId, views)
}
