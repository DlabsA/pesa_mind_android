package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first "Saving Goal" row — mirrors [DebtCreditEntity]'s shape exactly, substituting
 * [progress]/[achievedAt] for outstanding/settledAt. See that entity's doc comment for the
 * server-computed-fields and reminder-offsets-encoding rationale, both identical here.
 */
@Entity(
    tableName = "saving_goals",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["userId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["userId", "achievedAt"]),
    ],
)
data class SavingGoalEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    val name: String,
    val targetAmount: Double,
    // Server-computed; refreshed only in reconcileFromServer.
    val progress: Double,
    val note: String,
    val targetAt: Long?,
    val reminderOffsetsCsv: String,
    // Server-computed; refreshed only in reconcileFromServer.
    val achievedAt: Long?,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    val reminderOffsets: List<Int>
        get() = DebtCreditEntity.decode(reminderOffsetsCsv)
}
