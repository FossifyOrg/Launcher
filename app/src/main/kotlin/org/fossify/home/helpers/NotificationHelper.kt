// File: app/src/main/kotlin/org/fossify/home/helpers/NotificationHelper.kt
// LAUNCHPAD: Send local notifications to parent when Jake makes a request.

@file:Suppress("MagicNumber", "NestedBlockDepth", "TooGenericExceptionCaught") // framework literals; fail-safe catch

package org.fossify.home.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_REQUESTS = "launchpad_requests"
    private const val CHANNEL_UPDATES = "launchpad_updates"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_REQUESTS, "Jakes Anfragen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Wenn Jake eine Medien-Anfrage oder ein Versprechen stellt"
                }
            )
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, "LAUNCHPAD Updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notifyDogeRequest(context: Context, contentDescription: String) {
        notify(
            context,
            id = 1001,
            channel = CHANNEL_REQUESTS,
            title = "🎬 Jake fragt an",
            text = contentDescription,
            targetClass = "org.fossify.home.activities.DogeRequestsActivity",
            extras = mapOf("isParentMode" to true)
        )
    }

    fun notifyZusageRequest(context: Context, text: String) {
        notify(
            context,
            id = 1002,
            channel = CHANNEL_REQUESTS,
            title = "🤝 Neue Zusage erstellt",
            text = text,
            targetClass = "org.fossify.home.activities.ZusagenActivity",
            extras = mapOf("isParentMode" to true)
        )
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        targetClass: String,
        extras: Map<String, Any> = emptyMap()
    ) {
        try {
            val intent = Intent().setClassName(context, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                extras.forEach { (k, v) -> when (v) {
                    is Boolean -> putExtra(k, v)
                    is String -> putExtra(k, v)
                    is Int -> putExtra(k, v)
                } }
            }
            val pi = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(id, notification)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "notify failed", e)
        }
    }
}
