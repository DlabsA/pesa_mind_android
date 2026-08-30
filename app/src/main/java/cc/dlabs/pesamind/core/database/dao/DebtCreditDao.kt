package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.DebtCreditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtCreditDao {
    @Query("SELECT * FROM debt_credits WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<DebtCreditEntity>>

    @Query("SELECT * FROM debt_credits WHERE userId = :userId AND deletedAt IS NULL AND settledAt IS NULL ORDER BY dueAt ASC")
    fun observeActive(userId: String): Flow<List<DebtCreditEntity>>

    @Query("SELECT * FROM debt_credits WHERE userId = :userId AND deletedAt IS NULL AND settledAt IS NOT NULL ORDER BY settledAt DESC")
    fun observeSettled(userId: String): Flow<List<DebtCreditEntity>>

    @Query("SELECT * FROM debt_credits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DebtCreditEntity?

    /** Deliberately includes soft-deleted rows — pull reconciliation must see a tombstoned
     * row here to avoid resurrecting it as a duplicate live row (mirrors [ChannelDao]). */
    @Query("SELECT * FROM debt_credits WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): DebtCreditEntity?

    @Query("SELECT * FROM debt_credits")
    suspend fun getAllIncludingDeleted(): List<DebtCreditEntity>

    @Query("SELECT * FROM debt_credits WHERE dirty = 1")
    suspend fun getDirty(): List<DebtCreditEntity>

    @Query("SELECT * FROM debt_credits WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<DebtCreditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DebtCreditEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: DebtCreditEntity): Long

    @Update
    suspend fun update(entity: DebtCreditEntity)

    @Query("DELETE FROM debt_credits WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM debt_credits")
    suspend fun count(): Int
}
