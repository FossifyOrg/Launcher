package org.fossify.home.helpers

import org.fossify.home.databases.DogeRequest as DogeEntity
import org.fossify.home.databases.Zusage as ZusageEntity
import org.fossify.home.models.DogeRequest as DogeModel
import org.fossify.home.models.Zusage as ZusageModel

/**
 * Bridge between the domain models (org.fossify.home.models, used by ZusageManager /
 * DogeManager) and the Room entities (org.fossify.home.databases, persisted via the DAOs).
 *
 * The two shapes diverge slightly (the entities omit a few transient fields). These
 * mappers make the divergence explicit and lossless in the direction that matters for
 * persistence + reload.
 */

// ─── Zusage ───────────────────────────────────────────────────────────────────

fun ZusageModel.toEntity(): ZusageEntity = ZusageEntity(
    id = id,
    text = text,
    namedParent = namedParent,
    status = status,
    condition = condition ?: "",
    createdAt = createdAt,
    autoApproveAt = autoApproveAt,
    decidedAt = decidedAt,
    reason = reason,
    childVisibleText = childVisibleText
)

fun ZusageEntity.toModel(): ZusageModel = ZusageModel(
    id = id,
    text = text,
    namedParent = namedParent,
    condition = condition.ifEmpty { null },
    status = status,
    createdAt = createdAt,
    autoApproveAt = autoApproveAt,
    decidedAt = decidedAt,
    // The entity does not persist decidedBy separately; reason carries the audit note.
    decidedBy = null,
    reason = reason,
    childVisibleText = childVisibleText
)

// ─── DogeRequest ────────────────────────────────────────────────────────────────

fun DogeModel.toEntity(): DogeEntity = DogeEntity(
    id = id,
    contentDescription = contentDescription,
    requestedAt = requestedAt,
    decision = decision,
    decidedBy = decidedBy,
    reason = reason,
    durationMinutes = durationMinutes,
    expiresAt = expiresAt
)

fun DogeEntity.toModel(): DogeModel = DogeModel(
    id = id,
    contentDescription = contentDescription,
    requestedAt = requestedAt,
    requestedBy = "jake",
    // Derive a coarse status from the persisted decision/expiry for the manager's use.
    status = when {
        decision == "APPROVED" -> "APPROVED"
        decision == "REJECTED" -> "REJECTED"
        else -> "PENDING"
    },
    decision = decision,
    decidedBy = decidedBy,
    decidedAt = null,
    reason = reason,
    durationMinutes = durationMinutes,
    expiresAt = expiresAt,
    childVisibleText = contentDescription
)
