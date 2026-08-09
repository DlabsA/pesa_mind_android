package cc.dlabs.pesamind.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Non-deleted transactions, newest first — paged since this list can be thousands of rows. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun pagingSource(userId: String): PagingSource<Int, TransactionEntity>

    /** For screens that need the full list reactively without paging (e.g. summary totals). */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<TransactionEntity>>

    /** Same as [observeAll], clamped to [cutoffMillis] and later — the Free-tier history-depth
     * limit. `cutoffMillis` is epoch millis, matching [TransactionEntity.createdAt]. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL AND createdAt >= :cutoffMillis ORDER BY createdAt DESC")
    fun observeAllSince(
        userId: String,
        cutoffMillis: Long,
    ): Flow<List<TransactionEntity>>

    /** Live, channel-scoped list for [cc.dlabs.pesamind.features.settings.channels.ChannelDetailScreen].
     * [userId] is redundant with [channelId] (a channel always belongs to exactly one account),
     * kept for consistency with every other list query here rather than as an independent gap. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND channelId = :channelId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByChannel(
        userId: String,
        channelId: String,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    /** Scoped by [userId] — the unique index this mirrors is now `(userId, smsSourceKey)`, not
     * `smsSourceKey` alone, so a different account's row must never match here. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND smsSourceKey = :smsSourceKey LIMIT 1")
    suspend fun findBySmsSourceKey(
        userId: String,
        smsSourceKey: String,
    ): TransactionEntity?

    /** Provider-TID dedup lookup — catches two *different* SMS bodies sharing one real
     * transaction (confirmed: Airtel Uganda), which a content-derived [smsSourceKey] can't. */
    @Query("SELECT * FROM transactions WHERE channelId = :channelId AND providerTransactionId = :providerTransactionId LIMIT 1")
    suspend fun findByChannelAndProviderTransactionId(
        channelId: String,
        providerTransactionId: String,
    ): TransactionEntity?

    /** Deliberately includes soft-deleted rows (no `deletedAt IS NULL` filter) — pull
     * reconciliation ([cc.dlabs.pesamind.core.data.TransactionRepository.reconcileFromServer])
     * must see a tombstoned row here to avoid resurrecting it as a duplicate live row. */
    @Query("SELECT * FROM transactions WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): TransactionEntity?

    /** One-shot full-list read for `TransactionViewModel.loadTransactions()`. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllActive(userId: String): List<TransactionEntity>

    /** Same as [getAllActive], clamped to [cutoffMillis] and later — see [observeAllSince]. */
    @Query("SELECT * FROM transactions WHERE userId = :userId AND deletedAt IS NULL AND createdAt >= :cutoffMillis ORDER BY createdAt DESC")
    suspend fun getAllActiveSince(
        userId: String,
        cutoffMillis: Long,
    ): List<TransactionEntity>

    /** Every row known locally, including soft-deleted — the full-pull diff (Step 3) needs this. */
    @Query("SELECT * FROM transactions")
    suspend fun getAllIncludingDeleted(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirty(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TransactionEntity>)

    /**
     * Race-safe insert for SMS-triggered transaction creation — returns the new rowid, or
     * `-1L` if the insert was discarded because `smsSourceKey` or `(channelId,
     * providerTransactionId)` already exists (a concurrent insert won the race). Callers must
     * branch on this return value, not a SELECT performed before calling this — see
     * [cc.dlabs.pesamind.core.data.TransactionRepository].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
