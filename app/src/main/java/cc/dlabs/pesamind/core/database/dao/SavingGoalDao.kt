package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.SavingGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingGoalDao {
    @Query("SELECT * FROM saving_goals WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<SavingGoalEntity>>

    @Query("SELECT * FROM saving_goals WHERE userId = :userId AND deletedAt IS NULL AND achievedAt IS NULL ORDER BY targetAt ASC")
    fun observeActive(userId: String): Flow<List<SavingGoalEntity>>

    @Query("SELECT * FROM saving_goals WHERE userId = :userId AND deletedAt IS NULL AND achievedAt IS NOT NULL ORDER BY achievedAt DESC")
    fun observeAchieved(userId: String): Flow<List<SavingGoalEntity>>

    @Query("SELECT * FROM saving_goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SavingGoalEntity?

    /** Deliberately includes soft-deleted rows — see [DebtCreditDao.findByServerId]. */
    @Query("SELECT * FROM saving_goals WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): SavingGoalEntity?

    @Query("SELECT * FROM saving_goals")
    suspend fun getAllIncludingDeleted(): List<SavingGoalEntity>

    @Query("SELECT * FROM saving_goals WHERE dirty = 1")
    suspend fun getDirty(): List<SavingGoalEntity>

    @Query("SELECT * FROM saving_goals WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<SavingGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavingGoalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SavingGoalEntity): Long

    @Update
    suspend fun update(entity: SavingGoalEntity)

    @Query("DELETE FROM saving_goals WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM saving_goals")
    suspend fun count(): Int
}
