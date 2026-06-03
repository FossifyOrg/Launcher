// File: app/src/main/kotlin/org/fossify/home/helpers/LaunchGate.kt
// M1/M2: Launch gate + time-budget manager.
//
// NOTE (LAUNCHPAD audit fix): cool-down state is now derived from an explicit
// `cooldownUntil` timestamp in prefs, NOT from "is the last transaction a SPEND". The old
// heuristic flagged cool-down after EVERY spend, which would have blocked all launches the
// moment real per-minute usage tracking started writing SPEND rows.

@file:Suppress("MagicNumber", "ReturnCount") // budget thresholds; sequential guard returns

package org.fossify.home.helpers

import android.content.Context
import android.util.Log
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databases.CryptoCashTransaction
import org.fossify.home.models.TimeBudget

/**
 * LaunchGate: Central enforcement point for all app launches.
 */
class LaunchGate(
    private val context: Context,
    private val database: AppsDatabase
) {
    private val tag = "LaunchGate"

    data class LaunchDecision(
        val allowed: Boolean,
        val reason: String?,
        val childVisibleMessage: String?
    )

    suspend fun canLaunch(
        packageName: String,
        timeBudget: TimeBudget
    ): LaunchDecision {
        // Check 1: Whitelist
        if (!database.allowedAppDao().isAppAllowed(packageName)) {
            return LaunchDecision(false, "App not in whitelist", "Diese App ist nicht erlaubt.")
        }

        val category = database.allowedAppDao().getAppCategory(packageName)
        val isCooldownApp = category == LaunchpadConstants.CATEGORY_COOLDOWN

        // Cool-down apps (audiobooks, drawing, LEGO) are always allowed — they are the
        // restorative activities offered DURING cool-down and don't consume budget.
        if (isCooldownApp) {
            return LaunchDecision(true, null, null)
        }

        // Check 2: Cool-down phase
        if (timeBudget.inCooldown) {
            val minutesRemaining = timeBudget.minutesUntilCooldownExpires() ?: 0
            return LaunchDecision(
                false,
                "In cool-down phase ($minutesRemaining min remaining)",
                "Bildschirmpause! Noch $minutesRemaining Minuten. Audiobook, Zeichnen oder LEGO?"
            )
        }

        // Check 3: Time budget — only coin-gated (ACTIVE_LEISURE) apps are blocked at 0
        if (category == LaunchpadConstants.CATEGORY_ACTIVE_LEISURE && timeBudget.balanceMinutes <= 0) {
            return LaunchDecision(
                false,
                "Time budget exhausted",
                "Keine Zeit mehr. Erst wieder Zeit verdienen!"
            )
        }

        // Check 4: Minimum threshold for high-stimulation apps
        if (category == LaunchpadConstants.CATEGORY_ACTIVE_LEISURE && timeBudget.balanceMinutes < 5) {
            return LaunchDecision(
                false,
                "Insufficient time for high-stimulation app (minimum 5 min)",
                "Nur noch ${timeBudget.balanceMinutes} Minuten. Etwas Ruhigeres starten?"
            )
        }

        Log.d(tag, "Launch approved: $packageName (${timeBudget.balanceMinutes} min)")
        return LaunchDecision(true, null, null)
    }
}

/**
 * TimeBudgetManager: reads/writes the screen-time budget and cool-down window.
 */
class TimeBudgetManager(
    private val context: Context,
    private val database: AppsDatabase
) {
    private val tag = "TimeBudgetManager"

    private fun prefs() =
        context.getSharedPreferences(LaunchpadPrefs.PREFS_FILE, Context.MODE_PRIVATE)

    suspend fun getCurrentBudget(): TimeBudget {
        val balance = database.cryptoCashDao().getCurrentBalance()
        val lastTx = database.cryptoCashDao().getLastTransaction()
        val cooldownUntil = prefs().getLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, 0L)
        val now = System.currentTimeMillis()
        val inCooldown = now < cooldownUntil

        return TimeBudget(
            balanceMinutes = balance,
            weekCapMinutes = LaunchpadConstants.DEFAULT_WEEK_CAP_MINUTES,
            dailyCapMinutes = LaunchpadConstants.DEFAULT_SCHOOL_DAY_CAP_MINUTES,
            cooldownDurationMinutes = LaunchpadConstants.DEFAULT_COOLDOWN_DURATION_MINUTES,
            inCooldown = inCooldown,
            cooldownExpiresAt = if (inCooldown) cooldownUntil else null,
            lastTransactionTime = lastTx?.createdAt
        )
    }

    /**
     * Spend up to [minutes] of budget for [pkg]. Writes a single SPEND ledger row with a
     * correct balanceAfter snapshot (never negative). Begins cool-down when the balance hits 0.
     * Returns the new balance.
     */
    suspend fun spend(minutes: Int, pkg: String): Int {
        var balance = database.cryptoCashDao().getCurrentBalance()
        val toSpend = minutes.coerceAtMost(balance)
        if (toSpend <= 0) return balance

        balance -= toSpend
        database.cryptoCashDao().insertTransaction(
            CryptoCashTransaction(
                deltaMinutes = -toSpend,
                type = LaunchpadConstants.TX_TYPE_SPEND,
                actor = "jake",
                reasonType = "app_usage",
                reasonText = "Nutzung: $pkg",
                childVisibleText = "$pkg -$toSpend Min",
                source = "launcher_rule",
                balanceAfter = balance
            )
        )
        Log.d(tag, "Spent $toSpend min on $pkg, balance=$balance")
        if (balance <= 0) beginCooldown()
        return balance
    }

    /** Start a cool-down window of [durationMinutes] from now. */
    fun beginCooldown(durationMinutes: Int = LaunchpadConstants.DEFAULT_COOLDOWN_DURATION_MINUTES) {
        val until = System.currentTimeMillis() + durationMinutes * 60_000L
        prefs().edit().putLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, until).apply()
        Log.d(tag, "Cool-down started until $until")
    }

    fun isInCooldown(): Boolean =
        System.currentTimeMillis() < prefs().getLong(LaunchpadPrefs.PREF_COOLDOWN_UNTIL, 0L)
}
