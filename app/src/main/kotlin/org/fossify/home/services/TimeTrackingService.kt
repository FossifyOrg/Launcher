// File: app/src/main/java/org/fossify/home/services/TimeTrackingService.kt
// M2: Background service for tracking app usage and enforcing time budgets

package org.fossify.home.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.work.BackgroundWork
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.models.TimeBudget
import java.util.concurrent.TimeUnit

/**
 * TimeTrackingService: Background service for real-time app usage tracking.
 *
 * Responsibilities:
 * 1. Track active app (using PACKAGE_USAGE_STATS)
 * 2. Measure time spent in each app
 * 3. Deduct from Krypto-Cash balance
 * 4. Trigger cool-down when time expires
 * 5. Log transactions for ledger
 *
 * M2 approach: Periodic checks + event-driven enforcement
 * M5: Can upgrade to UsageStatsManager polling or Device Admin callbacks
 */
class TimeTrackingService : Service() {
    private val tag = "TimeTrackingService"
    private lateinit var database: AppsDatabase
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "TimeTrackingService created")
        database = AppsDatabase.getInstance(this)

        // Background thread for tracking
        handlerThread = HandlerThread("TimeTrackingWorker")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "TimeTrackingService started")

        // Start periodic tracking
        startTracking()

        // Return sticky: restart if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "TimeTrackingService destroyed")
        handlerThread.quit()
    }

    /**
     * Start background tracking loop.
     * Checks app usage every 10 seconds.
     */
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

                // Reschedule for next check (10 seconds)
                handler.postDelayed(this, 10 * 1000)
            }
        })
    }

    /**
     * Get currently active app (simplified version).
     * Full version uses UsageStatsManager in M5.
     *
     * M2: Simple polling via ActivityManager.getRunningAppProcesses()
     */
    private suspend fun trackActiveApp() {
        // TODO: Implement using:
        // - UsageStatsManager.queryUsageStats()
        // - Or getForegroundApp() via accessibility service
        // - Or Device Admin callbacks (M5)

        // For now, framework ready; actual implementation in M4-M5
        Log.d(tag, "Tracking active app...")
    }

    /**
     * Check if time budget expired during current session.
     * Trigger cool-down if yes.
     */
    private suspend fun checkForTimeExpiration() {
        val budget = getTimeBudget()

        if (budget.balanceMinutes <= 0 && !budget.inCooldown) {
            Log.w(tag, "Time budget expired! Triggering cool-down...")
            triggerCooldown()
        }
    }

    /**
     * Check if cool-down period expired.
     * Re-enable time tracking if yes.
     */
    private suspend fun checkForCooldownExpiration() {
        val budget = getTimeBudget()

        if (budget.inCooldown && budget.cooldownExpiresAt != null) {
            val now = System.currentTimeMillis()
            if (now > budget.cooldownExpiresAt) {
                Log.d(tag, "Cool-down expired, resuming normal tracking")
                // Cool-down auto-expires; no action needed
            }
        }
    }

    /**
     * Get current time budget from database.
     */
    private suspend fun getTimeBudget(): TimeBudget {
        // TODO: Query Room for latest balance and cool-down state
        return TimeBudget(
            balanceMinutes = 0,
            weekCapMinutes = 120,
            dailyCapMinutes = 60,
            cooldownDurationMinutes = 15,
            inCooldown = false,
            cooldownExpiresAt = null,
            lastTransactionTime = null
        )
    }

    /**
     * Trigger cool-down screen when time expires.
     */
    private fun triggerCooldown() {
        // TODO: Send broadcast or launch CooldownActivity
        val intent = Intent(this, Class.forName("org.fossify.home.activities.CooldownActivity"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

/**
 * TimeTrackingWorker: WorkManager background task for periodic checks.
 *
 * Runs every 30 minutes even if app is backgrounded.
 * Uses to:
 * 1. Auto-expire cool-down if needed
 * 2. Clean up expired doge-requests
 * 3. Auto-approve additional (if scope expands)
 * 4. Sync with parent app (M4)
 */
class TimeTrackingWorker(context: android.content.Context, params: WorkerParameters) :
    Worker(context, params) {

    private val tag = "TimeTrackingWorker"
    private val database = AppsDatabase.getInstance(context)

    override fun doWork(): Result {
        return try {
            Log.d(tag, "Running periodic time tracking check...")

            // 1. Auto-expire cool-down if needed
            autoExpireCooldown()

            // 2. Clean up expired doge-requests
            cleanupExpiredDogeRequests()

            // 3. Sync with parent (M4)
            // syncWithParentApp()

            Log.d(tag, "Periodic check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error in periodic check", e)
            Result.retry()
        }
    }

    private suspend fun autoExpireCooldown() {
        // TODO: Query Room for cool-down state
        // If expiresAt is in past, cool-down has naturally expired
        // No action needed (CooldownActivity will auto-dismiss)
        Log.d(tag, "Checking cool-down expiration...")
    }

    private suspend fun cleanupExpiredDogeRequests() {
        // TODO: Query Room for expired doge_requests
        // Mark as EXPIRED
        Log.d(tag, "Cleaning up expired doge requests...")
    }

    companion object {
        /**
         * Schedule periodic time tracking checks.
         * Call once on app startup.
         */
        fun schedulePeriodicChecks(context: android.content.Context) {
            val timeTrackingWork = PeriodicWorkRequestBuilder<TimeTrackingWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "launchpad_time_tracking",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                timeTrackingWork
            )
        }
    }
}

/**
 * TimeTrackingStartup: Initializer to start service on app startup.
 */
class TimeTrackingStartup {
    fun initializeTimeTracking(context: android.content.Context) {
        // Start background service
        val intent = Intent(context, TimeTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // Schedule periodic WorkManager tasks
        TimeTrackingWorker.schedulePeriodicChecks(context)

        Log.d("TimeTracking", "Time tracking initialized")
    }
}
