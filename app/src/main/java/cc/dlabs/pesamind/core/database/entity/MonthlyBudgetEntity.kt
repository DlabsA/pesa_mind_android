package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first monthly budget row. [yearlyBudgetId] FKs to the *local* [YearlyBudgetEntity]
 * row, not a server id — a monthly budget created offline may reference a yearly budget
 * that hasn't synced yet either. The sync worker must therefore push yearly budgets before
 * monthly budgets that depend on them, translating the local FK to the yearly budget's
 * `serverId` at push time (see ADR-0004's outbox-ordering note). Line items are a JSON
 * blob column for the same reason as [YearlyBudgetEntity] — see that class's doc comment.
 *
 * [month]+[year] and [serverId] are indexed but NOT DB-unique, same reasoning as
 * [YearlyBudgetEntity.year] — soft-delete means "at most one live row" has to be a
 * repository-layer rule, not a schema constraint.
 */
@Entity(
    tableName = "monthly_budgets",
    foreignKeys = [
        ForeignKey(
            entity = YearlyBudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["yearlyBudgetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["yearlyBudgetId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["month", "year"]),
    ],
)
data class MonthlyBudgetEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val yearlyBudgetId: String?,
    val month: Int,
    val year: Long,
    val totalExpenditures: Long,
    val totalIncome: Long,
    val totalSavings: Long,
    val totalTransactions: Long,
    val transactionsJson: String,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
