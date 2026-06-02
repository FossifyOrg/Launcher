// File: app/src/main/java/org/fossify/home/extensions/Activity.kt (MODIFIED)
// M1: Launch gate enforcement at app launch point

package org.fossify.home.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import org.fossify.home.models.TimeBudget
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.helpers.LaunchpadConstants
import org.fossify.home.helpers.LaunchpadPrefs

/**
 * LaunchGate: Central enforcement point for all app launches.
 * Checks:
 * 1. Whitelist (is app allowed?)
 * 2. Time budget (do we have time left?)
 * 3. Cool-down state (are we in cool-down?)
 * 4. Age restrictions (for future use)
 */
class LaunchGate(
    private val context: Context,
    private val database: AppsDatabase
) {
    private val tag = "LaunchGate"

    data class LaunchDecision(
        val allowed: Boolean,
        val reason: String?, // Why denied (if denied)
        val childVisibleMessage: String? // What to show Jake (friendly message)
    )

    /**
     * Main launch gate: called before ANY app launch.
     * Returns decision with reason if denied.
     */
    suspend fun canLaunch(
        packageName: String,
        timeBudget: TimeBudget
    ): LaunchDecision {
        Log.d(tag, "Launch gate check for: $packageName")

        // Check 1: Whitelist
        val allowed = database.allowedAppDao().isAppAllowed(packageName)
        if (!allowed) {
            return LaunchDecision(
                allowed = false,
                reason = "App not in whitelist",
                childVisibleMessage = "Diese App ist nicht erlaubt."
            )
        }

        // Check 2: Cool-down
        if (timeBudget.inCooldown) {
            val minutesRemaining = timeBudget.minutesUntilCooldownExpires() ?: 0
            return LaunchDecision(
                allowed = false,
                reason = "In cool-down phase ($minutesRemaining min remaining)",
                childVisibleMessage = "Bildschirmpause! Noch ${minutesRemaining} Minuten. Audiobook, Zeichnen oder LEGO?"
            )
        }

        // Check 3: Time budget
        if (timeBudget.balanceMinutes <= 0) {
            return LaunchDecision(
                allowed = false,
                reason = "Time budget exhausted",
                childVisibleMessage = "Keine Zeit mehr heute. Komm morgen wieder!"
            )
        }

        // Check 4: Category restrictions (future: can add per-app daily caps)
        val appCategory = database.allowedAppDao().getAppCategory(packageName)
        if (appCategory == LaunchpadConstants.CATEGORY_ACTIVE_LEISURE && timeBudget.balanceMinutes < 5) {
            return LaunchDecision(
                allowed = false,
                reason = "Insufficient time for high-stimulation app (minimum 5 min)",
                childVisibleMessage = "Nur noch ${timeBudget.balanceMinutes} Minuten. Etwas Ruhigeres starten?"
            )
        }

        // All checks passed
        Log.d(tag, "Launch approved: $packageName (${timeBudget.balanceMinutes} min available)")
        return LaunchDecision(
            allowed = true,
            reason = null,
            childVisibleMessage = null
        )
    }

    /**
     * Called when app launch is DENIED by gate.
     * Shows child-friendly message.
     */
    fun showDenialDialog(decision: LaunchDecision) {
        // This would integrate with MainActivity to show a dialog
        Log.w(tag, "Launch denied: ${decision.reason}")
        // Dialog would show decision.childVisibleMessage
    }
}

/**
 * TimeBudgetManager: Tracks current session time, expiration, cool-down state.
 * Synced from Room database.
 */
class TimeBudgetManager(
    private val context: Context,
    private val database: AppsDatabase
) {
    private val tag = "TimeBudgetManager"

    suspend fun getCurrentBudget(): TimeBudget {
        val ledger = database.cryptoCashDao().getLatestBalance()
        val lastTx = database.cryptoCashDao().getLastTransaction()
        val cooldownState = getCooldownState(lastTx)

        return TimeBudget(
            balanceMinutes = ledger?.balanceAfter ?: 0,
            weekCapMinutes = 120,
            dailyCapMinutes = 60,
            cooldownDurationMinutes = 15,
            inCooldown = cooldownState.first,
            cooldownExpiresAt = cooldownState.second,
            lastTransactionTime = lastTx?.createdAt
        )
    }

    private suspend fun getCooldownState(lastTx: org.fossify.home.databases.CryptoCashTransaction?): Pair<Boolean, Long?> {
        if (lastTx == null) return Pair(false, null)

        // Cool-down activates when time expires (balance reaches 0 or SPEND brings it to 0)
        val inCooldown = lastTx.type == "SPEND" && lastTx.deltaMinutes < 0
        if (!inCooldown) return Pair(false, null)

        val cooldownDuration = 15 * 60 * 1000 // 15 minutes
        val expiresAt = lastTx.createdAt + cooldownDuration
        val now = System.currentTimeMillis()

        return if (now > expiresAt) {
            Pair(false, null) // Cool-down has expired
        } else {
            Pair(true, expiresAt)
        }
    }

    suspend fun recordAppLaunch(packageName: String, durationMinutes: Int) {
        Log.d(tag, "Recording app launch: $packageName for $durationMinutes min")
        // Creates SPEND transaction and updates balance
        database.cryptoCashDao().insertTransaction(
            org.fossify.home.databases.CryptoCashTransaction(
                deltaMinutes = -durationMinutes,
                type = "SPEND",
                actor = "jake",
                reasonType = "app_usage",
                reasonText = "Launched: $packageName",
                childVisibleText = "App -$durationMinutes Min",
                source = "launcher_rule",
                balanceAfter = 0 // Will be recalculated on next read
            )
        )
    }

    suspend fun recordAppExit(packageName: String, actualMinutesUsed: Int) {
        // Update transaction if app was used less than expected
        Log.d(tag, "Recording app exit: $packageName (used $actualMinutesUsed min)")
    }
}
