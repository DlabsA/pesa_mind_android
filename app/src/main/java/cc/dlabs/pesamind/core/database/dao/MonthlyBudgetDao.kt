package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE deletedAt IS NULL ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<MonthlyBudgetEntity>>

    @Query("SELECT * FROM monthly_budgets WHERE month = :month AND year = :year AND deletedAt IS NULL LIMIT 1")
    fun observeByMonthYear(
        month: Int,
        year: Long,
    ): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budgets WHERE month = :month AND year = :year AND deletedAt IS NULL LIMIT 1")
    suspend fun getByMonthYear(
        month: Int,
        year: Long,
    ): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budgets")
    suspend fun getAllIncludingDeleted(): List<MonthlyBudgetEntity>

    @Query("SELECT * FROM monthly_budgets WHERE dirty = 1")
    suspend fun getDirty(): List<MonthlyBudgetEntity>

    @Query("SELECT * FROM monthly_budgets WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<MonthlyBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MonthlyBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MonthlyBudgetEntity>)

    @Update
    suspend fun update(entity: MonthlyBudgetEntity)

    @Query("DELETE FROM monthly_budgets WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM monthly_budgets")
    suspend fun count(): Int
}
