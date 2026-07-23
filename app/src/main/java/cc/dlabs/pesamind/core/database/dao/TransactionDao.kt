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
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun pagingSource(): PagingSource<Int, TransactionEntity>

    /** For screens that need the full list reactively without paging (e.g. summary totals). */
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE smsSourceKey = :smsSourceKey LIMIT 1")
    suspend fun findBySmsSourceKey(smsSourceKey: String): TransactionEntity?

    /** Deliberately includes soft-deleted rows (no `deletedAt IS NULL` filter) — pull
     * reconciliation ([cc.dlabs.pesamind.core.data.TransactionRepository.reconcileFromServer])
     * must see a tombstoned row here to avoid resurrecting it as a duplicate live row. */
    @Query("SELECT * FROM transactions WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): TransactionEntity?

    /** One-shot full-list read for `TransactionViewModel.loadTransactions()`. */
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllActive(): List<TransactionEntity>

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

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
