// File: app/src/main/kotlin/org/fossify/home/services/TimeTrackingService.kt
// M2: Foreground service that surfaces live screen-time budget; periodic WorkManager housekeeping.
//
// NOTE: the loop now reads the REAL budget from Room (via TimeBudgetManager) instead of a
// hardcoded 0 — wiring this into app launch is safe (no spurious cool-down). Decrementing the
// balance from actual foreground-app usage requires PACKAGE_USAGE_STATS and is M5 scope; the
// hook is marked below.

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
import kotlinx.coroutines.runBlocking
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.TimeBudgetManager
import java.util.concurrent.TimeUnit

class TimeTrackingService : Service() {
    private val tag = "TimeTrackingService"
    private lateinit var database: AppsDatabase
    private lateinit var budgetManager: TimeBudgetManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "TimeTrackingService created")
        database = AppsDatabase.getInstance(this)
        budgetManager = TimeBudgetManager(this, database)
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
                    NotificationChannel(channelId, "Bildschirmzeit", NotificationManager.IMPORTANCE_MIN)
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LAUNCHPAD")
            .setContentText("Bildschirmzeit aktiv")
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
                    tick()
                } catch (e: Exception) {
                    Log.e(tag, "Error in tracking loop", e)
                }
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        })
    }

    /**
     * Reads the live budget from Room. Runs on the background HandlerThread, so the blocking
     * DB read here is fine. M5: replace the log with real usage-based decrement + cool-down
     * trigger when balance hits 0 mid-session.
     */
    private fun tick() {
        val budget = runBlocking { budgetManager.getCurrentBudget() }
        Log.d(tag, "budget: balance=${budget.balanceMinutes}min inCooldown=${budget.inCooldown}")
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
        private const val POLL_INTERVAL_MS = 10_000L
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
            // M4/M5: auto-expire cool-down, clean up expired doge approvals, parent sync.
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error in periodic check", e)
            Result.retry()
        }
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
 * Starts the tracking service + schedules periodic checks. Must be called from a foreground
 * context (e.g. MainActivity.onCreate) so startForegroundService() is permitted. Idempotent.
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
