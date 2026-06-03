// File: app/src/main/kotlin/org/fossify/home/services/TimeTrackingService.kt
// M2/M5: Foreground service that meters real screen-time usage and enforces the budget.
//
// Counts wall-clock time while the screen is ON and a *whitelisted, non-cool-down* app is in
// the foreground (detected via UsageStatsManager). Every whole minute is debited from the
// Krypto-Cash ledger via TimeBudgetManager.spend(); when the balance reaches 0 the cool-down
// window starts and CooldownActivity is shown. Requires Usage Access (granted in Eltern-Modus).

@file:Suppress("MagicNumber", "TooGenericExceptionCaught") // polling intervals; fail-safe catches

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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs
import org.fossify.home.helpers.TimeBudgetManager
import org.fossify.home.helpers.UsageTracker
import java.util.concurrent.TimeUnit

class TimeTrackingService : Service() {
    private val tag = "TimeTrackingService"
    private lateinit var database: AppsDatabase
    private lateinit var budgetManager: TimeBudgetManager
    private lateinit var powerManager: PowerManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    // Sub-minute carry of counted foreground time, and the timestamp of the last counted tick.
    private var accumulatedMs = 0L
    private var lastCountedAt = 0L
    @Volatile private var lastCooldownLaunch = 0L

    override fun onCreate() {
        super.onCreate()
        database = AppsDatabase.getInstance(this)
        budgetManager = TimeBudgetManager(this, database)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        handlerThread = HandlerThread("TimeTrackingWorker")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
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

    private fun tick() {
        // Only meter time once the parent has switched on Kindermodus (enforcement).
        val enforce = getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(LaunchpadPrefs.PREF_ENFORCEMENT_ENABLED, false)
        if (!enforce) {
            resetCounter()
            return
        }
        // Need Usage Access to know the foreground app.
        if (!UsageTracker.hasUsageAccess(this)) {
            resetCounter()
            return
        }
        // Only count while the screen is actually on.
        if (!powerManager.isInteractive) {
            resetCounter()
            return
        }
        val pkg = UsageTracker.getForegroundPackage(this)
        if (pkg == null) {
            resetCounter()
            return
        }

        runBlocking {
            val budget = budgetManager.getCurrentBudget()

            // If balance is 0 or in cooldown, keep re-launching CooldownActivity when a
            // coin-gated app is still in the foreground (e.g. user pressed Back to escape).
            if (budget.inCooldown || budget.balanceMinutes <= 0) {
                if (isCountedApp(pkg)) {
                    val now = System.currentTimeMillis()
                    if (now - lastCooldownLaunch >= COOLDOWN_RELAUNCH_THROTTLE_MS) {
                        lastCooldownLaunch = now
                        launchCooldown()
                    }
                }
                resetCounter()
                return@runBlocking
            }

            if (!isCountedApp(pkg)) {
                resetCounter()
                return@runBlocking
            }

            val now = System.currentTimeMillis()
            if (lastCountedAt == 0L) {
                lastCountedAt = now
                return@runBlocking
            }
            accumulatedMs += now - lastCountedAt
            lastCountedAt = now

            val wholeMinutes = (accumulatedMs / 60_000L).toInt()
            if (wholeMinutes >= 1) {
                accumulatedMs -= wholeMinutes * 60_000L
                val newBalance = budgetManager.spend(wholeMinutes, pkg)
                if (newBalance <= 0) {
                    resetCounter()
                    lastCooldownLaunch = System.currentTimeMillis()
                    launchCooldown()
                }
            }
        }
    }

    private fun resetCounter() {
        accumulatedMs = 0L
        lastCountedAt = 0L
    }

    private suspend fun isCountedApp(pkg: String): Boolean {
        if (pkg == packageName) return false
        if (!database.allowedAppDao().isAppAllowed(pkg)) return false
        val category = database.allowedAppDao().getAppCategory(pkg)
        return category == LaunchpadConstants.CATEGORY_ACTIVE_LEISURE
    }

    private fun launchCooldown() {
        try {
            startActivity(
                Intent()
                    .setClassName(this, "org.fossify.home.activities.CooldownActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(tag, "Could not launch CooldownActivity", e)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4711
        private const val POLL_INTERVAL_MS = 10_000L
        private const val COOLDOWN_RELAUNCH_THROTTLE_MS = 30_000L
    }
}

/**
 * Periodic (30 min) housekeeping. Full auto-expire / cleanup logic is M4/M5.
 */
class TimeTrackingWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result = try {
        Log.d("TimeTrackingWorker", "Periodic check")
        Result.success()
    } catch (e: Exception) {
        Log.w("TimeTrackingWorker", "Periodic check failed", e)
        Result.retry()
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
 * Starts the tracking service + schedules periodic checks. Call from a foreground context
 * (MainActivity.onCreate). Idempotent.
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
    }
}
