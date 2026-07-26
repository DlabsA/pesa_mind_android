package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

enum class OutboxEntityType {
    TRANSACTION,
    CHANNEL,
    PROFILE,
    MONTHLY_BUDGET,
    YEARLY_BUDGET,
}

enum class OutboxOperation {
    CREATE,
    UPDATE,
    DELETE,
}

/**
 * A durable record of "this local row needs to be pushed." One row per (entityType,
 * entityId) — repositories upsert rather than append, so [SyncWorker] always reads the
 * entity's *current* state from its own table at drain time instead of replaying a
 * stale snapshot captured when the mutation happened. Survives process death; nothing
 * here is lost until the push is confirmed (status becomes SYNCED, at which point the
 * row is deleted from this table — see ADR-0004).
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class OutboxEntry(
    @PrimaryKey
    val id: String,
    val entityType: OutboxEntityType,
    val entityId: String,
    val operation: OutboxOperation,
    val status: SyncStatus,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
