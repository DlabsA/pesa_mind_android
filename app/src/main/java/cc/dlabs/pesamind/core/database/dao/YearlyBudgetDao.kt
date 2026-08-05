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
    @Query("SELECT * FROM yearly_budgets WHERE userId = :userId AND deletedAt IS NULL ORDER BY year DESC")
    fun observeAll(userId: String): Flow<List<YearlyBudgetEntity>>

    /** [userId]-scoped: two different accounts can each have their own "2026" budget — matching
     * on [year] alone (as this query did before MIGRATION_5_6) let one silently absorb the
     * other's edits on a shared device. */
    @Query("SELECT * FROM yearly_budgets WHERE userId = :userId AND year = :year AND deletedAt IS NULL LIMIT 1")
    fun observeByYear(
        userId: String,
        year: Long,
    ): Flow<YearlyBudgetEntity?>

    @Query("SELECT * FROM yearly_budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): YearlyBudgetEntity?

    /** Deliberately includes soft-deleted rows — pull-reconciliation
     * ([cc.dlabs.pesamind.core.data.BudgetRepository.reconcileFromServer]) must see a
     * tombstoned row here to avoid resurrecting it as a duplicate live row. */
    @Query("SELECT * FROM yearly_budgets WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): YearlyBudgetEntity?

    /** See [observeByYear]'s doc comment for why this is scoped by [userId]. */
    @Query("SELECT * FROM yearly_budgets WHERE userId = :userId AND year = :year AND deletedAt IS NULL LIMIT 1")
    suspend fun getByYear(
        userId: String,
        year: Long,
    ): YearlyBudgetEntity?

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
