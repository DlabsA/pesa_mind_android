package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YearlyBudgetDao {
    @Query("SELECT * FROM yearly_budgets WHERE deletedAt IS NULL ORDER BY year DESC")
    fun observeAll(): Flow<List<YearlyBudgetEntity>>

    @Query("SELECT * FROM yearly_budgets WHERE year = :year AND deletedAt IS NULL LIMIT 1")
    fun observeByYear(year: Long): Flow<YearlyBudgetEntity?>

    @Query("SELECT * FROM yearly_budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): YearlyBudgetEntity?

    @Query("SELECT * FROM yearly_budgets WHERE year = :year AND deletedAt IS NULL LIMIT 1")
    suspend fun getByYear(year: Long): YearlyBudgetEntity?

    @Query("SELECT * FROM yearly_budgets")
    suspend fun getAllIncludingDeleted(): List<YearlyBudgetEntity>

    @Query("SELECT * FROM yearly_budgets WHERE dirty = 1")
    suspend fun getDirty(): List<YearlyBudgetEntity>

    @Query("SELECT * FROM yearly_budgets WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<YearlyBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: YearlyBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<YearlyBudgetEntity>)

    @Update
    suspend fun update(entity: YearlyBudgetEntity)

    @Query("DELETE FROM yearly_budgets WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM yearly_budgets")
    suspend fun count(): Int
}
