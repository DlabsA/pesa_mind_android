package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    /**
     * Best-effort match for transactions that only carry a channel *name*
     * (`TransactionDetails.channelDetailsName`, echoed from `ChannelDetails.name`) — see
     * TransactionEntity's doc comment.
     */
    @Query("SELECT * FROM channels WHERE name = :name AND deletedAt IS NULL")
    suspend fun findByName(name: String): List<ChannelEntity>

    @Query("SELECT * FROM channels")
    suspend fun getAllIncludingDeleted(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE dirty = 1")
    suspend fun getDirty(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<ChannelEntity>

    /** Channel-type filter for `loadChannelsByType` — was network-only before Slice A1. */
    @Query("SELECT * FROM channels WHERE channelType = :type AND deletedAt IS NULL ORDER BY name ASC")
    suspend fun getByChannelType(type: String): List<ChannelEntity>

    /** Active/inactive filter for `loadChannelsByStatus` — was network-only before Slice A1.
     * Named to avoid confusion with [getByStatus], which filters on [SyncStatus]. */
    @Query("SELECT * FROM channels WHERE status = :active AND deletedAt IS NULL ORDER BY name ASC")
    suspend fun getByActiveStatus(active: Boolean): List<ChannelEntity>

    /** Used by ChannelManager.isSmsAllowedForSender's channel-desc lookup (see ChannelRepository). */
    @Query("SELECT * FROM channels WHERE channelDesc = :channelDesc AND deletedAt IS NULL LIMIT 1")
    suspend fun findByChannelDesc(channelDesc: String): ChannelEntity?

    /** Deliberately includes soft-deleted rows (no `deletedAt IS NULL` filter) — pull
     * reconciliation ([cc.dlabs.pesamind.core.data.ChannelRepository.reconcileFromServer]) must
     * see a tombstoned row here to avoid resurrecting it as a duplicate live row. */
    @Query("SELECT * FROM channels WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): ChannelEntity?

    /** One-shot full-list read for `ChannelViewModel.loadChannels()`. */
    @Query("SELECT * FROM channels WHERE deletedAt IS NULL ORDER BY name ASC")
    suspend fun getAllActive(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChannelEntity>)

    @Update
    suspend fun update(entity: ChannelEntity)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int
}
