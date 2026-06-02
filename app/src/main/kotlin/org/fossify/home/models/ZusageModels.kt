// File: shared/src/main/java/org/fossify/launchpad/models/ZusageModels.kt
// M2: Zusagen (family promises/commitments) data models and validation logic

package org.fossify.home.models

import java.util.UUID

/**
 * Zusage: A family promise/commitment.
 *
 * Example: "After homework, then 20 min Minecraft"
 *
 * Purpose: Reduce "but you said..." conflicts by making promises explicit,
 * visible, and automatically approved if not contradicted within 24 hours.
 *
 * No-Regression principle applies: Once approved, can't be revoked.
 * Child sees full promise text; has certainty about what happens next.
 */
data class Zusage(
    val id: String = UUID.randomUUID().toString(),
    val text: String, // "After homework, then 20 min Minecraft"
    val namedParent: String, // "Mama" or "Papa"
    val status: String, // ACTIVE, FULFILLED, EXPIRED, REVOKED
    val condition: String? = null, // Optional: "homework completed"
    val createdAt: Long = System.currentTimeMillis(),
    val autoApproveAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24h
    val decidedAt: Long? = null,
    val decidedBy: String? = null,
    val reason: String? = null, // Why fulfilled/expired/revoked
    val childVisibleText: String = text
) {
    fun isActive(): Boolean = status == "ACTIVE"
    fun isApproved(): Boolean = decidedAt != null && status in listOf("ACTIVE", "FULFILLED")
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis()
        return now > autoApproveAt
    }
    fun canBeApproved(): Boolean {
        return status == "ACTIVE" && decidedAt == null
    }
    fun canBeFulfilled(): Boolean {
        return status == "ACTIVE" && (decidedAt != null || isExpired())
    }
}

/**
 * ZusageManager: Service for managing zusagen lifecycle.
 *
 * Responsibilities:
 * 1. Create new zusagen (parent)
 * 2. Auto-approve after 24h if not contradicted
 * 3. Mark as fulfilled when condition met
 * 4. Enforce No-Regression (can't revoke approved zusagen)
 * 5. Query active/pending zusagen for child
 */
class ZusageManager {
    private val tag = "ZusageManager"

    /**
     * Create new zusage from parent.
     * Automatically scheduled for 24-hour approval.
     */
    fun createZusage(
        text: String,
        parentName: String,
        condition: String? = null
    ): Zusage {
        return Zusage(
            text = text,
            namedParent = parentName,
            condition = condition,
            status = "ACTIVE",
            childVisibleText = text
        )
    }

    /**
     * Approve zusage before 24-hour deadline.
     * Once approved, cannot be revoked (No-Regression).
     */
    fun approveZusage(zusage: Zusage, approvedBy: String): Zusage {
        if (!zusage.canBeApproved()) {
            throw IllegalStateException("Zusage ${zusage.id} cannot be approved in status ${zusage.status}")
        }

        return zusage.copy(
            status = "ACTIVE",
            decidedAt = System.currentTimeMillis(),
            decidedBy = approvedBy,
            reason = "Approved by $approvedBy"
        )
    }

    /**
     * Reject zusage (within 24 hours, before auto-approval).
     * Parent can contradict/reject before deadline.
     */
    fun rejectZusage(zusage: Zusage, rejectedBy: String, reason: String): Zusage {
        if (zusage.decidedAt != null) {
            throw IllegalStateException("Zusage ${zusage.id} already decided; cannot reject after approval")
        }

        return zusage.copy(
            status = "REVOKED",
            decidedAt = System.currentTimeMillis(),
            decidedBy = rejectedBy,
            reason = reason,
            childVisibleText = "Zusage geändert: $reason"
        )
    }

    /**
     * Auto-approve zusage after 24 hours if no contradiction.
     * Called by background task or on-demand.
     */
    fun autoApproveExpired(zusage: Zusage): Zusage {
        if (zusage.decidedAt != null) {
            return zusage // Already decided
        }

        val now = System.currentTimeMillis()
        if (now <= zusage.autoApproveAt) {
            return zusage // Not yet expired
        }

        // Auto-approve
        return zusage.copy(
            status = "ACTIVE",
            decidedAt = zusage.autoApproveAt,
            decidedBy = "system",
            reason = "Auto-approved after 24 hours"
        )
    }

    /**
     * Mark zusage as fulfilled when condition met.
     * Only possible if zusage is approved (active).
     */
    fun fulfillZusage(zusage: Zusage, fulfilledBy: String): Zusage {
        if (!zusage.canBeFulfilled()) {
            throw IllegalStateException("Zusage ${zusage.id} cannot be fulfilled in status ${zusage.status}")
        }

        return zusage.copy(
            status = "FULFILLED",
            decidedAt = System.currentTimeMillis(),
            decidedBy = fulfilledBy,
            reason = "Condition met",
            childVisibleText = "✓ Erfüllt: ${zusage.text}"
        )
    }

    /**
     * Expire zusage if condition not met within reasonable time.
     * (e.g., promise made but condition never happens)
     */
    fun expireZusage(zusage: Zusage, expiredBy: String = "system"): Zusage {
        if (zusage.status == "FULFILLED") {
            return zusage // Already fulfilled, don't expire
        }

        return zusage.copy(
            status = "EXPIRED",
            decidedAt = System.currentTimeMillis(),
            decidedBy = expiredBy,
            reason = "Condition not met within time window",
            childVisibleText = "Zusage abgelaufen"
        )
    }

    /**
     * Query active zusagen for child view.
     * Shows: ACTIVE + approved, not yet fulfilled.
     */
    fun getActiveZusagen(allZusagen: List<Zusage>): List<Zusage> {
        return allZusagen.filter { zusage ->
            zusage.status == "ACTIVE" && zusage.isApproved()
        }.sortedByDescending { it.createdAt }
    }

    /**
     * Query pending zusagen for parent review.
     * Shows: ACTIVE, not yet decided, within 24-hour window.
     */
    fun getPendingZusagen(allZusagen: List<Zusage>): List<Zusage> {
        return allZusagen.filter { zusage ->
            zusage.status == "ACTIVE" && zusage.decidedAt == null && !zusage.isExpired()
        }.sortedByDescending { it.autoApproveAt }
    }

    /**
     * Query future auto-approvals.
     * Shows: ACTIVE, not yet decided, approaching 24-hour deadline.
     */
    fun getAutoApprovingZusagen(allZusagen: List<Zusage>, withinMinutes: Int = 60): List<Zusage> {
        val now = System.currentTimeMillis()
        val window = withinMinutes * 60 * 1000L

        return allZusagen.filter { zusage ->
            zusage.status == "ACTIVE" &&
                    zusage.decidedAt == null &&
                    zusage.autoApproveAt in (now..(now + window))
        }.sortedBy { it.autoApproveAt }
    }

    /**
     * Validate zusage state consistency.
     * No-Regression check: approved zusagen can't become inactive.
     */
    fun validateNoRegression(current: Zusage, prior: Zusage?): ValidationResult {
        if (prior == null) return ValidationResult(isValid = true)

        // Once approved, can't go back to ACTIVE pending
        if (prior.isApproved() && !current.isApproved()) {
            return ValidationResult(
                isValid = false,
                error = "Zusage ${current.id}: Approved promise cannot be unapproved (No-Regression violation)"
            )
        }

        // Once fulfilled, can't revert to active
        if (prior.status == "FULFILLED" && current.status in listOf("ACTIVE", "REVOKED")) {
            return ValidationResult(
                isValid = false,
                error = "Zusage ${current.id}: Fulfilled promise cannot be reverted"
            )
        }

        // decidedAt can't be changed
        if (prior.decidedAt != null && current.decidedAt != prior.decidedAt) {
            return ValidationResult(
                isValid = false,
                error = "Zusage ${current.id}: Decision timestamp cannot be changed"
            )
        }

        return ValidationResult(isValid = true)
    }
}

/**
 * ZusageHistory: Audit trail for zusagen.
 * Records all state changes for transparency.
 */
data class ZusageHistory(
    val id: String = UUID.randomUUID().toString(),
    val zusageId: String,
    val previousStatus: String,
    val newStatus: String,
    val changedBy: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
