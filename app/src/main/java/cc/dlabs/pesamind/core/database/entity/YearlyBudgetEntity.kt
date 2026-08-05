package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first yearly budget row. Line items ([transactionsJson], a serialized
 * `List<BudgetTransactionResponse>`) are stored as a single JSON column rather than a
 * normalized child table: the backend's only mutation endpoint is a budget-level PATCH
 * carrying a `transaction_ops` add/update/delete batch, not per-line-item REST routes —
 * so "the whole budget" is the real sync unit, matching this table's dirty/outbox
 * granularity. See ADR-0004 for why line items aren't split into their own entity.
 *
 * [year] and [serverId] are indexed but NOT DB-unique: rows are soft-deleted
 * ([deletedAt]), so a table-wide unique constraint would block ever creating a new
 * 2026 budget after an old one for the same year was deleted. "At most one *live*
 * row per year" is enforced at the repository layer (Step 2/3), not the schema —
 * Room `@Index` can't express a `WHERE deletedAt IS NULL` partial index.
 *
 * [userId] scopes every list/lookup query on this table to the owning account — added
 * alongside [ChannelEntity.userId]'s scoping fix, since [YearlyBudgetDao.getByYear] matching
 * on [year] alone let a different account's leftover local row on a shared device silently
 * absorb this account's edits.
 */
@Entity(
    tableName = "yearly_budgets",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["userId", "year"]),
    ],
)
data class YearlyBudgetEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    val year: Long,
    val totalExpenditures: Long,
    val totalIncome: Long,
    val totalSavings: Long,
    val totalTransactions: Long,
    val transactionsJson: String,
    // See MonthlyBudgetEntity.lastSyncedTransactionsJson's doc comment — identical rationale
    // and write sites, mirrored here for yearly budgets.
    val lastSyncedTransactionsJson: String? = null,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
