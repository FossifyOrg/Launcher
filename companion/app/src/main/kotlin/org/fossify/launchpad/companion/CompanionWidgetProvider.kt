// File: companion/app/src/main/kotlin/org/fossify/launchpad/companion/CompanionWidgetProvider.kt
// LAUNCHPAD Eltern widget — shows pending request counts on the parent's home screen.

package org.fossify.launchpad.companion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CompanionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // runBlocking is safe in a BroadcastReceiver — the network call has a 3s timeout.
        ids.forEach { id -> runBlocking { render(context, manager, id) } }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CompanionWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, CompanionWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }
}

private suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
    val ip = context.getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        .getString("launcher_ip", null)

    val views = RemoteViews(context.packageName, R.layout.widget_companion)

    // Tap → open companion app
    val pi = PendingIntent.getActivity(
        context, widgetId,
        Intent(context, CompanionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.companion_widget_root, pi)

    if (ip == null) {
        views.setTextViewText(R.id.widget_doge_count, "?")
        views.setTextViewText(R.id.widget_zusagen_count, "?")
        views.setTextViewText(R.id.widget_status_text, "IP nicht konfiguriert — tippen zum Einrichten")
        views.setInt(R.id.widget_status_dot, "setBackgroundColor", Color.parseColor("#FF6B35"))
        manager.updateAppWidget(widgetId, views)
        return
    }

    try {
        val response = get("http://$ip/api/pending", timeoutMs = 3000)
        if (response != null) {
            val obj = JSONObject(response)
            val dogeCount = obj.optJSONArray("doge")?.length() ?: 0
            val zusageCount = obj.optJSONArray("zusagen")?.length() ?: 0
            val total = dogeCount + zusageCount

            views.setTextViewText(R.id.widget_doge_count, dogeCount.toString())
            views.setTextViewText(R.id.widget_zusagen_count, zusageCount.toString())

            val dotColor = if (total > 0) "#FF6B35" else "#4CAF50"
            views.setInt(R.id.widget_status_dot, "setBackgroundColor", Color.parseColor(dotColor))

            // Urgency: highlight the count in orange if there are open items
            val countColor = if (total > 0) Color.parseColor("#FF6B35") else Color.WHITE
            views.setTextColor(R.id.widget_doge_count, countColor)
            views.setTextColor(R.id.widget_zusagen_count, countColor)

            views.setTextViewText(R.id.widget_status_text,
                if (total > 0) "$total offen — tippen zum Genehmigen"
                else "Alles erledigt ✓"
            )
        } else {
            views.setTextViewText(R.id.widget_doge_count, "!")
            views.setTextViewText(R.id.widget_zusagen_count, "!")
            views.setInt(R.id.widget_status_dot, "setBackgroundColor", Color.parseColor("#FF4444"))
            views.setTextViewText(R.id.widget_status_text, "Keine Verbindung zu $ip")
        }
    } catch (e: Exception) {
        views.setTextViewText(R.id.widget_status_text, "Fehler: ${e.message?.take(30)}")
    }

    manager.updateAppWidget(widgetId, views)
}

private fun get(url: String, timeoutMs: Int): String? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = timeoutMs
    conn.readTimeout = timeoutMs
    conn.connect()
    if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
} catch (e: Exception) { null }
