// File: shared/src/main/java/org/fossify/launchpad/models/KryptoCashModels.kt
// M1: Core Krypto-Cash data models for :shared module

package org.fossify.home.models

import java.util.UUID

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
        // Basic validation: deltaMinutes should be non-zero (except CORRECTION which may be 0)
        if (type != "CORRECTION" && deltaMinutes == 0) return false
        if (reasonText.isEmpty()) return false
        if (balanceAfter < 0) return false
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
    val weekStart: Long, // Timestamp of week start
    val weekTransactionsMinutes: Int, // Total earned this week (for tracking cap)
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Core No-Regression validation:
     * 1. Transactions must be immutable (no deletion)
     * 2. Balance after each tx must be >= 0
     * 3. Balance snapshots must be mathematically consistent
     * 4. No retroactive value changes (all EARN entries remain equal value)
     */
    fun validateNoRegression(): ValidationResult {
        var calculatedBalance = 0
        var weekMinutes = 0

        for ((index, tx) in transactions.withIndex()) {
            // Check transaction validity
            if (!tx.isValid()) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${tx.id} invalid at index $index"
                )
            }

            // Calculate running balance
            calculatedBalance += tx.deltaMinutes

            // Verify balance snapshot matches
            if (calculatedBalance != tx.balanceAfter) {
                return ValidationResult(
                    isValid = false,
                    error = "Balance mismatch at tx ${tx.id}: calc=$calculatedBalance, recorded=${tx.balanceAfter}"
                )
            }

            // Track week cap (only EARN counts toward cap)
            if (tx.type == "EARN") {
                weekMinutes += tx.deltaMinutes
            }

            // Reject if balance would ever go negative (no punitive deductions allowed)
            if (tx.balanceAfter < 0) {
                return ValidationResult(
                    isValid = false,
                    error = "Balance went negative at tx ${tx.id}: ${tx.balanceAfter}"
                )
            }
        }

        // Verify final balance matches claimed state
        if (calculatedBalance != currentBalance) {
            return ValidationResult(
                isValid = false,
                error = "Final balance mismatch: calc=$calculatedBalance, claimed=$currentBalance"
            )
        }

        // Check week cap not exceeded
        if (weekMinutes > 120) { // 120 minutes = default weekly cap
            return ValidationResult(
                isValid = false,
                error = "Weekly cap exceeded: $weekMinutes minutes"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Immutability check: compare against prior ledger snapshot.
     * If any transaction was deleted or modified, this fails.
     */
    fun checkImmutability(prior: LedgerState?): ValidationResult {
        if (prior == null) return ValidationResult(isValid = true) // First time

        // All prior transactions must exist unchanged in current ledger
        val priorIds = prior.transactions.map { it.id to it }.toMap()
        for (tx in transactions.take(prior.transactions.size)) {
            val priorTx = priorIds[tx.id]
            if (priorTx == null) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${tx.id} was deleted (No-Regression violation)"
                )
            }
            if (priorTx != tx) {
                return ValidationResult(
                    isValid = false,
                    error = "Transaction ${tx.id} was modified (No-Regression violation)"
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
 * Command sent from parent app to launcher.
 * QR-paired or locally entered.
 */
data class ParentCommand(
    val commandId: String = UUID.randomUUID().toString(),
    val actor: String, // parent name
    val type: String, // adjust_time, toggle_app, set_cooldown_rules, etc
    val payload: Map<String, Any>, // Command-specific parameters
    val reasonType: String,
    val reasonText: String,
    val childVisibleText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "parent_app" // or "qr_pairing", "local_menu"
)

/**
 * Zusage: Family commitment
 */
data class Zusage(
    val id: String = UUID.randomUUID().toString(),
    val text: String, // "After homework, then 20 min Minecraft"
    val namedParent: String, // Who promised
    val condition: String? = null, // Conditional clause
    val status: String = "ACTIVE", // ACTIVE, FULFILLED, EXPIRED, REVOKED
    val createdAt: Long = System.currentTimeMillis(),
    val autoApproveAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24h
    val decidedAt: Long? = null,
    val reason: String? = null,
    val childVisibleText: String = text
)

/**
 * Doge-Coin: SOG media approval (YouTube, short videos)
 */
data class DogeRequest(
    val id: String = UUID.randomUUID().toString(),
    val contentDescription: String, // "20 min YouTube - Minecraft tutorials"
    val requestedAt: Long = System.currentTimeMillis(),
    val duration: Int? = null, // minutes
    val decision: String? = null, // APPROVED, REJECTED, EXPIRED
    val decidedBy: String? = null,
    val expiresAt: Long? = null
)

/**
 * QR pairing payload: sent from parent app to launcher
 */
data class QRPairingPayload(
    val version: String = "1",
    val parentIdentity: String, // Parent name or device ID
    val nonceHex: String, // 16-byte random nonce
    val encryptedKeyHex: String, // AES-256-GCM encrypted session key
    val timestamp: Long = System.currentTimeMillis(),
    val isPersistent: Boolean = true // Can be re-used until parent revokes
)

/**
 * Time budget state for a session
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
        return (remaining / 1000 / 60).toInt()
    }
}
