// File: app/src/main/kotlin/org/fossify/home/services/TimeTrackingService.kt
// M2: Background service skeleton for tracking app usage and enforcing time budgets
//
// NOTE (LAUNCHPAD audit fix):
//  - Removed the non-existent `androidx.work.BackgroundWork` import.
//  - Stub tracking methods are no longer `suspend` (they were being called from a plain
//    Handler Runnable / Worker, which doesn't compile). Real DB work in M4/M5 should move to
//    a CoroutineWorker.
//  - Implements a proper foreground notification so startForegroundService() cannot crash
//    with "did not call startForeground()".

package org.fossify.home.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.fossify.home.models.TimeBudget
import java.util.concurrent.TimeUnit

class TimeTrackingService : Service() {
    private val tag = "TimeTrackingService"
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "TimeTrackingService created")
        handlerThread = HandlerThread("TimeTrackingWorker")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "TimeTrackingService started")
        startInForeground()
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "TimeTrackingService destroyed")
        if (this::handlerThread.isInitialized) handlerThread.quit()
    }

    private fun startInForeground() {
        val channelId = "launchpad_time_tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Bildschirmzeit",
                        NotificationManager.IMPORTANCE_MIN
                    )
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LAUNCHPAD")
            .setContentText("Bildschirmzeit wird verfolgt")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startTracking() {
        handler.post(object : Runnable {
            override fun run() {
                try {
                    trackActiveApp()
                    checkForTimeExpiration()
                    checkForCooldownExpiration()
                } catch (e: Exception) {
                    Log.e(tag, "Error in tracking loop", e)
                }
                handler.postDelayed(this, 10 * 1000)
            }
        })
    }

    // --- Stub tracking logic (full PACKAGE_USAGE_STATS implementation deferred to M5) ---

    private fun trackActiveApp() {
        Log.d(tag, "Tracking active app...")
    }

    private fun checkForTimeExpiration() {
        val budget = getTimeBudget()
        if (budget.balanceMinutes <= 0 && !budget.inCooldown) {
            Log.w(tag, "Time budget expired! Triggering cool-down...")
            triggerCooldown()
        }
    }

    private fun checkForCooldownExpiration() {
        val budget = getTimeBudget()
        if (budget.inCooldown && budget.cooldownExpiresAt != null) {
            if (System.currentTimeMillis() > budget.cooldownExpiresAt) {
                Log.d(tag, "Cool-down expired, resuming normal tracking")
            }
        }
    }

    private fun getTimeBudget(): TimeBudget {
        // TODO(M4): query Room for the real balance + cool-down state.
        return TimeBudget(balanceMinutes = 0)
    }

    private fun triggerCooldown() {
        val intent = Intent().setClassName(this, "org.fossify.home.activities.CooldownActivity")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Could not launch CooldownActivity", e)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
    }
}

/**
 * TimeTrackingWorker: WorkManager periodic task (every 30 min) for housekeeping.
 */
class TimeTrackingWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    private val tag = "TimeTrackingWorker"

    override fun doWork(): Result {
        return try {
            Log.d(tag, "Running periodic time tracking check...")
            autoExpireCooldown()
            cleanupExpiredDogeRequests()
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error in periodic check", e)
            Result.retry()
        }
    }

    private fun autoExpireCooldown() {
        Log.d(tag, "Checking cool-down expiration...")
    }

    private fun cleanupExpiredDogeRequests() {
        Log.d(tag, "Cleaning up expired doge requests...")
    }

    companion object {
        fun schedulePeriodicChecks(context: Context) {
            val work = PeriodicWorkRequestBuilder<TimeTrackingWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "launchpad_time_tracking",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}

/**
 * Helper to start tracking from app startup. Call from a foreground context.
 */
class TimeTrackingStartup {
    fun initializeTimeTracking(context: Context) {
        val intent = Intent(context, TimeTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        TimeTrackingWorker.schedulePeriodicChecks(context)
        Log.d("TimeTracking", "Time tracking initialized")
    }
}
