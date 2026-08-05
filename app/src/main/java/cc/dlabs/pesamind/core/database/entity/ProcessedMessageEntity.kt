package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first, write-once audit record of an SMS the app's ingestion pipeline saw — pushed to
 * `POST /processed-messages` via the outbox, never updated or deleted locally afterward
 * (unlike [TransactionEntity]/[cc.dlabs.pesamind.core.database.entity.ChannelEntity]), so
 * there's no `dirty`/`deletedAt` column here.
 *
 * [dedupeKey] mirrors [TransactionEntity.smsSourceKey]'s derivation
 * (`"$senderId:$timestamp:${content.hashCode()}"`) — a redelivered/reprocessed SMS enqueues
 * the outbox push exactly once via the same `insertIgnore`-is-the-correctness-mechanism
 * pattern [cc.dlabs.pesamind.core.data.TransactionRepository.createTransaction] uses.
 * Unique-indexed per [userId], not table-wide — two different accounts receiving a
 * structurally-identical SMS on the same physical SIM/device must never collide (see
 * `ChannelEntity`'s doc comment for the confirmed real-world bug this same table-wide-index
 * shape caused for channels).
 */
@Entity(
    tableName = "processed_messages",
    indices = [
        Index(value = ["userId", "dedupeKey"], unique = true),
        Index(value = ["syncStatus"]),
    ],
)
data class ProcessedMessageEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val simInfo: Int,
    val receivingSimNumber: String,
    val dedupeKey: String,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
)
