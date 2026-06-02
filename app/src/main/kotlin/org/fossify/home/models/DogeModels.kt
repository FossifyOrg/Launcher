// File: shared/src/main/java/org/fossify/launchpad/models/DogeModels.kt
// M2: Doge-Coins (SOG media approvals) data models and logic

package org.fossify.home.models

import java.util.UUID

/**
 * Doge-Coin: Approval for SOG (short-form video) content.
 *
 * SOG = YouTube, TikTok, Instagram Reels, etc. (short videos, high stimulation)
 *
 * Purpose: Allow Jake to REQUEST specific content (e.g., "YouTube - Minecraft tutorials")
 * Parents APPROVE (not auto-grant), child gets explicit PERMISSION + duration
 *
 * Key differences from Krypto-Cash:
 * - Krypto-Cash: Earned time (homework, chores) → auto-spend on activities
 * - Doge-Coins: Specific content requests → explicit parent approval → timed access
 *
 * Example flow:
 * 1. Jake: "Can I watch 20 min YouTube - Minecraft tutorials?"
 * 2. Parent approves: "OK, 20 min YouTube approved until 18:00"
 * 3. Launcher: Allows YouTube access for 20 min or until 18:00, then blocks
 *
 * No artificial scarcity; genuine requests for specific content.
 * Not a reward system; not a punishment system.
 * Approval based on: time of day, content quality, Jake's behavior context.
 */
data class DogeRequest(
    val id: String = UUID.randomUUID().toString(),
    val contentDescription: String, // "YouTube - Minecraft tutorials"
    val requestedAt: Long = System.currentTimeMillis(),
    val requestedBy: String = "jake",
    val status: String, // PENDING, APPROVED, REJECTED, EXPIRED
    val decision: String? = null, // APPROVED or REJECTED
    val decidedBy: String? = null, // parent name
    val decidedAt: Long? = null,
    val reason: String? = null, // Why approved/rejected
    val durationMinutes: Int? = null, // How long was approved for
    val expiresAt: Long? = null, // When approval expires
    val childVisibleText: String = contentDescription
) {
    fun isPending(): Boolean = status == "PENDING" && decidedAt == null
    fun isApproved(): Boolean = decision == "APPROVED" && expiresAt != null && System.currentTimeMillis() < expiresAt
    fun isActive(): Boolean = status == "APPROVED" && isApproved()
    fun isExpired(): Boolean = expiresAt != null && System.currentTimeMillis() > expiresAt
}

/**
 * DogeManager: Service for managing SOG media approvals.
 *
 * Responsibilities:
 * 1. Create request from child (or parent on behalf)
 * 2. Parent reviews and decides (approve/reject)
 * 3. Approved requests grant temporary access
 * 4. Enforce duration limits (e.g., 20 min YouTube)
 * 5. Track request history (what Jake likes, patterns)
 */
class DogeManager {
    private val tag = "DogeManager"

    /**
     * Create request from child.
     * e.g., "Can I watch YouTube - Minecraft tutorials?"
     */
    fun createRequest(
        contentDescription: String,
        requestedBy: String = "jake"
    ): DogeRequest {
        return DogeRequest(
            contentDescription = contentDescription,
            requestedAt = System.currentTimeMillis(),
            requestedBy = requestedBy,
            status = "PENDING",
            childVisibleText = contentDescription
        )
    }

    /**
     * Parent approves request with specific duration.
     * Creates limited-time access window.
     *
     * Example:
     * - Request: "20 min YouTube - Minecraft tutorials"
     * - Approval: "Approved for 20 min until 18:00"
     */
    fun approveRequest(
        request: DogeRequest,
        approvedBy: String,
        durationMinutes: Int,
        reason: String = ""
    ): DogeRequest {
        if (!request.isPending()) {
            throw IllegalStateException("Request ${request.id} already decided")
        }

        val expiresAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)

        return request.copy(
            status = "APPROVED",
            decision = "APPROVED",
            decidedBy = approvedBy,
            decidedAt = System.currentTimeMillis(),
            reason = reason,
            durationMinutes = durationMinutes,
            expiresAt = expiresAt,
            childVisibleText = "✓ Genehmigt: $durationMinutes Min bis ${formatTime(expiresAt)}"
        )
    }

    /**
     * Parent rejects request.
     * No duration granted; Jake can re-request later.
     */
    fun rejectRequest(
        request: DogeRequest,
        rejectedBy: String,
        reason: String = ""
    ): DogeRequest {
        if (!request.isPending()) {
            throw IllegalStateException("Request ${request.id} already decided")
        }

        return request.copy(
            status = "REJECTED",
            decision = "REJECTED",
            decidedBy = rejectedBy,
            decidedAt = System.currentTimeMillis(),
            reason = reason,
            childVisibleText = "Nicht jetzt. ${if (reason.isNotEmpty()) "Grund: $reason" else ""}"
        )
    }

    /**
     * Mark request as expired (time window closed).
     */
    fun expireRequest(request: DogeRequest): DogeRequest {
        return request.copy(
            status = "EXPIRED",
            childVisibleText = "Genehmigung abgelaufen"
        )
    }

    /**
     * Check if Jake can access SOG content right now.
     * Returns true only if:
     * - Request is approved AND
     * - Not yet expired AND
     * - Duration not exceeded
     */
    fun canAccessContent(request: DogeRequest): Boolean {
        return request.isActive() && !request.isExpired()
    }

    /**
     * Get time remaining in approval.
     */
    fun getTimeRemaining(request: DogeRequest): Int? {
        if (!request.isApproved()) return null
        val expiresAt = request.expiresAt ?: return null
        val remaining = expiresAt - System.currentTimeMillis()
        return (remaining / 1000 / 60).toInt().coerceAtLeast(0)
    }

    /**
     * Query recent requests (for analytics/pattern recognition).
     * Parents can see what Jake likes to request.
     */
    fun getRequestHistory(
        allRequests: List<DogeRequest>,
        limit: Int = 20
    ): List<DogeRequest> {
        return allRequests
            .sortedByDescending { it.requestedAt }
            .take(limit)
    }

    /**
     * Get pending requests (awaiting parent decision).
     */
    fun getPendingRequests(allRequests: List<DogeRequest>): List<DogeRequest> {
        return allRequests.filter { it.isPending() }
    }

    /**
     * Get active approvals (granted, not yet expired).
     */
    fun getActiveApprovals(allRequests: List<DogeRequest>): List<DogeRequest> {
        return allRequests.filter { it.isActive() }
    }

    /**
     * Pattern analysis: What content is Jake requesting most?
     * Use case: Parents can understand Jake's interests & make informed approval decisions.
     */
    fun analyzeRequestPatterns(allRequests: List<DogeRequest>): Map<String, Int> {
        val patterns = mutableMapOf<String, Int>()

        for (request in allRequests) {
            // Extract content type from description
            // e.g., "YouTube - Minecraft tutorials" → "YouTube"
            val contentType = request.contentDescription.split(" - ").firstOrNull() ?: "Other"
            patterns[contentType] = (patterns[contentType] ?: 0) + 1
        }

        return patterns.toSortedMap { a, b -> (patterns[b] ?: 0).compareTo(patterns[a] ?: 0) }
    }

    /**
     * Suggestion: What's appropriate to approve?
     * Educational vs. entertainment heuristic.
     */
    fun suggestApprovalDuration(contentDescription: String): Int {
        return when {
            // Educational content → longer approval
            contentDescription.contains("tutorial", ignoreCase = true) -> 30
            contentDescription.contains("educational", ignoreCase = true) -> 30
            contentDescription.contains("learning", ignoreCase = true) -> 30
            // Minecraft → often educational (building, problem-solving)
            contentDescription.contains("minecraft", ignoreCase = true) -> 25
            // General entertainment → moderate
            contentDescription.contains("youtube", ignoreCase = true) -> 20
            // NOTE: no "video" branch — "Video streaming" and other generic video strings
            // should fall through to the default 15, not 20. The "video" branch was too
            // broad and caused testSuggestApprovalDuration to fail.
            // Short-form by default
            else -> 15
        }
    }

    private fun formatTime(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
}

/**
 * DogeRequestHistory: Audit trail for requests.
 */
data class DogeRequestHistory(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val action: String, // CREATED, APPROVED, REJECTED, EXPIRED
    val actor: String,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
