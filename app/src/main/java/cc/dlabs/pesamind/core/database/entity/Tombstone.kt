package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records a soft-deleted row so the sync worker can push the deletion (if [serverId] is
 * non-null — a row that was only ever local never needs a server DELETE) and so a full-
 * pull diff doesn't resurrect a row the user just deleted before the pull sees the
 * server-side removal. [id] matches the deleted row's original local id.
 */
@Entity(
    tableName = "tombstones",
    indices = [Index(value = ["synced"])],
)
data class Tombstone(
    @PrimaryKey
    val id: String,
    val entityType: OutboxEntityType,
    val serverId: String?,
    val deletedAt: Long,
    val synced: Boolean,
)
