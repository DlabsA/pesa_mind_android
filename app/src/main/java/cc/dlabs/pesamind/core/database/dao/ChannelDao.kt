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
    @Query("SELECT * FROM channels WHERE userId = :userId AND deletedAt IS NULL ORDER BY name ASC")
    fun observeAll(userId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    /**
     * Best-effort match for transactions that only carry a channel *name*
     * (`TransactionDetails.channelDetailsName`, echoed from `ChannelDetails.name`) — see
     * TransactionEntity's doc comment.
     */
    @Query("SELECT * FROM channels WHERE userId = :userId AND name = :name AND deletedAt IS NULL")
    suspend fun findByName(
        userId: String,
        name: String,
    ): List<ChannelEntity>

    @Query("SELECT * FROM channels")
    suspend fun getAllIncludingDeleted(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE dirty = 1")
    suspend fun getDirty(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<ChannelEntity>

    /** Channel-type filter for `loadChannelsByType` — was network-only before Slice A1. */
    @Query("SELECT * FROM channels WHERE userId = :userId AND channelType = :type AND deletedAt IS NULL ORDER BY name ASC")
    suspend fun getByChannelType(
        userId: String,
        type: String,
    ): List<ChannelEntity>

    /** Active/inactive filter for `loadChannelsByStatus` — was network-only before Slice A1.
     * Named to avoid confusion with [getByStatus], which filters on [SyncStatus]. */
    @Query("SELECT * FROM channels WHERE userId = :userId AND status = :active AND deletedAt IS NULL ORDER BY name ASC")
    suspend fun getByActiveStatus(
        userId: String,
        active: Boolean,
    ): List<ChannelEntity>

    /**
     * Exact provider+number lookup, **live channels only** — the query
     * `ChannelManager.isSmsAllowedForSender` uses first to decide "does an active channel
     * already exist for this exact sender+receiving-number" before attaching a transaction to
     * it — a soft-deleted row must never be silently treated as the active channel here (that
     * would attach new SMS transactions to a channel the user believes is gone). For the
     * insert-time race guard that must also see tombstones, use
     * [findByNormalizedSenderKeyAndReceivingNumber] instead. For the "how many live channels
     * exist for this provider at all, regardless of number" fallback (ambiguous-match
     * handling), use [findAllLiveByNormalizedSenderKey].
     */
    @Query(
        "SELECT * FROM channels WHERE userId = :userId AND normalizedSenderKey = :normalizedSenderKey " +
            "AND receivingNumber = :receivingNumber AND deletedAt IS NULL LIMIT 1",
    )
    suspend fun findLiveByNormalizedSenderKeyAndReceivingNumber(
        userId: String,
        normalizedSenderKey: String,
        receivingNumber: String,
    ): ChannelEntity?

    /**
     * All live channels sharing a provider ([normalizedSenderKey]), regardless of
     * [ChannelEntity.receivingNumber] — the fallback [cc.dlabs.pesamind.core.data.ChannelRepository.findByNormalizedSenderKey]
     * uses when an exact receiving-number match misses (or the receiving number couldn't be
     * resolved at all): exactly one result means there's no real ambiguity even without a
     * number match; more than one means the caller must not guess which one an incoming SMS
     * belongs to.
     */
    @Query(
        "SELECT * FROM channels WHERE userId = :userId AND normalizedSenderKey = :normalizedSenderKey " +
            "AND deletedAt IS NULL",
    )
    suspend fun findAllLiveByNormalizedSenderKey(
        userId: String,
        normalizedSenderKey: String,
    ): List<ChannelEntity>

    /**
     * Exact provider+number lookup, **including soft-deleted rows** — `normalizedSenderKey` is
     * a pre-normalized (trim+lowercase) column and `receivingNumber` is pre-normalized digits
     * (or the `UNSPECIFIED` sentinel), so this is a plain indexed equality match, not a
     * `LIKE`/`COLLATE` scan. Includes tombstones deliberately: this pair is unique-indexed
     * *across* soft-deletes (mirrors `smsSourceKey`'s reasoning, not `serverId`'s), so an insert
     * attempt can conflict with an already-deleted row and this is the query used to find that
     * conflicting row again afterward (see [cc.dlabs.pesamind.core.data.ChannelRepository]'s
     * revive-on-conflict handling). Scoped by [ChannelEntity.userId] — the unique index this
     * mirrors is `(userId, normalizedSenderKey, receivingNumber)`, so a different account's row
     * (even soft-deleted) must never match here (see MIGRATION_5_6's doc comment for the bug
     * this fixes for the userId scoping itself). For deciding "does an active channel already
     * exist," use [findLiveByNormalizedSenderKeyAndReceivingNumber] instead — this one is for
     * insert-time conflict resolution only.
     */
    @Query(
        "SELECT * FROM channels WHERE userId = :userId AND normalizedSenderKey = :normalizedSenderKey " +
            "AND receivingNumber = :receivingNumber LIMIT 1",
    )
    suspend fun findByNormalizedSenderKeyAndReceivingNumber(
        userId: String,
        normalizedSenderKey: String,
        receivingNumber: String,
    ): ChannelEntity?

    /** Deliberately includes soft-deleted rows (no `deletedAt IS NULL` filter) — pull
     * reconciliation ([cc.dlabs.pesamind.core.data.ChannelRepository.reconcileFromServer]) must
     * see a tombstoned row here to avoid resurrecting it as a duplicate live row. */
    @Query("SELECT * FROM channels WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): ChannelEntity?

    /** One-shot full-list read for `ChannelViewModel.loadChannels()`. */
    @Query("SELECT * FROM channels WHERE userId = :userId AND deletedAt IS NULL ORDER BY name ASC")
    suspend fun getAllActive(userId: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChannelEntity>)

    /**
     * Race-safe insert for provider/bank channel creation — returns the new rowid, or `-1L` if
     * the insert was discarded because `normalizedSenderKey` already exists (a concurrent
     * insert won the race). Callers must branch on this return value, not a SELECT performed
     * before calling this — see [cc.dlabs.pesamind.core.data.ChannelRepository].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ChannelEntity): Long

    @Update
    suspend fun update(entity: ChannelEntity)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    /** Live (non-deleted) channel count for a user — the Free-tier total-cap check. */
    @Query("SELECT COUNT(*) FROM channels WHERE userId = :userId AND deletedAt IS NULL")
    suspend fun countByUserId(userId: String): Int

    /** Live (non-deleted) count of a given channel type for a user — the Free-tier
     * mobile-money sub-cap check. */
    @Query("SELECT COUNT(*) FROM channels WHERE userId = :userId AND channelType = :channelType AND deletedAt IS NULL")
    suspend fun countByUserIdAndChannelType(
        userId: String,
        channelType: String,
    ): Int
}
