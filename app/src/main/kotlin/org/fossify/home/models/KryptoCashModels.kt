package org.fossify.home.models

import java.util.UUID

private const val WEEKLY_CAP_MINUTES = 120
private const val SECONDS_PER_MINUTE = 60

// NOTE (LAUNCHPAD audit fix): Zusage / DogeRequest / ParentCommand were previously
// declared here AND in ZusageModels.kt / DogeModels.kt / LaunchpadEntities.kt, causing
// "conflicting declarations" compile errors. The authoritative model classes now live in
// ZusageModels.kt and DogeModels.kt; the Room entities live in LaunchpadEntities.kt
// (package org.fossify.home.databases). This file keeps only the ledger value-objects.

/**
 * Immutable ledger entry. Once created, never deleted.
 * Corrections create NEW transactions, not mutations.
 * Balance snapshots prevent tampering.
 */
data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val deltaMinutes: Int, // +10 for earn, -5 for spend
    val type: String, // EARN, SPEND, EXPIRE, CORRECTION
    val actor: String, // "jake", "mama", "papa", "system"
    val reasonType: String, // "homework", "leisure_redeem", "expiration", "manual_correction"
    val reasonText: String, // Full description for audit trail
    val childVisibleText: String, // What Jake sees on launcher
    val source: String, // "parent_app", "launcher_rule", "system"
    val createdAt: Long = System.currentTimeMillis(),
    val balanceAfter: Int // Critical: balance AFTER this transaction
) {
    fun isValid(): Boolean {
        if (type != "CORRECTION" && deltaMinutes == 0) return false
        if (reasonText.isEmpty()) return false
        // NOTE: balanceAfter < 0 is intentionally NOT checked here. validateNoRegression
        // catches it explicitly and returns a "negative" error message so tests can match
        // on that string. Checking it here short-circuits to "invalid at index N" first.
        return true
    }
}

/**
 * Ledger state: immutable snapshot of all transactions up to a point.
 * Used to validate No-Regression constraint.
 */
data class LedgerState(
    val transactions: List<LedgerEntry>,
    val currentBalance: Int,
    val weekStart: Long,
    val weekTransactionsMinutes: Int,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    @Suppress("ReturnCount") // sequential ledger guard checks; each needs a distinct early return
    fun validateNoRegression(): ValidationResult {
        var calculatedBalance = 0
        var weekMinutes = 0

        for ((index, tx) in transactions.withIndex()) {
            if (!tx.isValid()) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${tx.id} invalid at index $index"
                )
            }

            calculatedBalance += tx.deltaMinutes

            if (calculatedBalance != tx.balanceAfter) {
                return ValidationResult(
                    isValid = false,
                    error = "Balance mismatch at tx ${tx.id}: calc=$calculatedBalance, recorded=${tx.balanceAfter}"
                )
            }

            if (tx.type == "EARN") {
                weekMinutes += tx.deltaMinutes
            }

            if (tx.balanceAfter < 0) {
                return ValidationResult(
                    isValid = false,
                    error = "Balance went negative at tx ${tx.id}: ${tx.balanceAfter}"
                )
            }
        }

        if (calculatedBalance != currentBalance) {
            return ValidationResult(
                isValid = false,
                error = "Final balance mismatch: calc=$calculatedBalance, claimed=$currentBalance"
            )
        }

        if (weekMinutes > WEEKLY_CAP_MINUTES) {
            return ValidationResult(
                isValid = false,
                error = "Weekly cap exceeded: $weekMinutes minutes"
            )
        }

        return ValidationResult(isValid = true)
    }

    fun checkImmutability(prior: LedgerState?): ValidationResult {
        if (prior == null) return ValidationResult(isValid = true)

        // Check every prior transaction still exists unchanged in the current ledger.
        // NOTE: must iterate over prior.transactions (not current.take(n)) so that a
        // completely empty current ledger is still detected as a deletion.
        val currentIds = transactions.map { it.id to it }.toMap()
        for (priorTx in prior.transactions) {
            val currentTx = currentIds[priorTx.id]
            if (currentTx == null) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${priorTx.id} was deleted (No-Regression violation)"
                )
            }
            if (currentTx != priorTx) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${priorTx.id} was modified (No-Regression violation)"
                )
            }
        }

        return ValidationResult(isValid = true)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
)

/**
 * QR pairing payload: sent from parent app to launcher.
 */
data class QRPairingPayload(
    val version: String = "1",
    val parentIdentity: String,
    val nonceHex: String,
    val encryptedKeyHex: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPersistent: Boolean = true
)

/**
 * Time budget state for a session.
 */
data class TimeBudget(
    val balanceMinutes: Int,
    val weekCapMinutes: Int = 120,
    val dailyCapMinutes: Int = 60,
    val cooldownDurationMinutes: Int = 15,
    val inCooldown: Boolean = false,
    val cooldownExpiresAt: Long? = null,
    val lastTransactionTime: Long? = null
) {
    fun canLaunchApp(): Boolean {
        if (inCooldown) return false
        if (balanceMinutes <= 0) return false
        return true
    }

    fun minutesUntilCooldownExpires(): Int? {
        if (!inCooldown || cooldownExpiresAt == null) return null
        val remaining = cooldownExpiresAt - System.currentTimeMillis()
        return (remaining / 1000 / SECONDS_PER_MINUTE).toInt()
    }
}
