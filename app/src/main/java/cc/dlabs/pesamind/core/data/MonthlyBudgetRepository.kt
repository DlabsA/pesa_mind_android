package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.withTransaction
import cc.dlabs.pesamind.core.database.CoalesceDecision
import cc.dlabs.pesamind.core.database.ExistingRowSnapshot
import cc.dlabs.pesamind.core.database.OutboxCoalescer
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.ReconcileDecision
import cc.dlabs.pesamind.core.database.ReconcileResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room-backed source of truth for monthly budgets (ADR-0006 / ADR-0004 "Slice B") — replaces
 * `BudgetManager`'s DataStore blob for every `BudgetViewModel`/`SetMonthlyBudgetViewModel` call
 * site. Structured exactly like [YearlyBudgetRepository] — see that object's doc comment for
 * the shared design rationale.
 *
 * [MonthlyBudgetEntity.yearlyBudgetId] is a *local* FK, not a server id — a monthly budget
 * added offline may reference a yearly budget that hasn't synced yet either. [addLineItem]
 * resolves (or creates) that local row via [YearlyBudgetRepository.resolveOrCreateLocalId] the
 * one narrow cross-repository call this object makes, mirroring `TransactionRepository` reading
 * `ChannelDao` directly for its own FK resolution.
 */
object MonthlyBudgetRepository {
    internal lateinit var database: PesaMindDatabase
    private val monthlyBudgetDao get() = database.monthlyBudgetDao()
    private val outboxDao get() = database.outboxDao()

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    fun observeMonthlyBudget(
        month: Int,
        year: Long,
    ): Flow<MonthlyBudgetResponse?> = monthlyBudgetDao.observeByMonthYear(month, year).map { it?.toDetails() }

    /** See [BudgetSnapshot]'s doc comment on [YearlyBudgetRepository]. */
    fun observeMonthlyBudgetSnapshot(
        month: Int,
        year: Long,
    ): Flow<BudgetSnapshot<MonthlyBudgetResponse>?> =
        monthlyBudgetDao.observeByMonthYear(month, year).map {
                entity ->
            entity?.let { BudgetSnapshot(it.toDetails(), it.dirty, it.updatedAt) }
        }

    suspend fun getMonthlyBudget(
        month: Int,
        year: Long,
    ): MonthlyBudgetResponse? = monthlyBudgetDao.getByMonthYear(month, year)?.toDetails()

    /** Collapses `SetMonthlyBudgetViewModel`'s old CREATE-vs-UPDATE branch into one call — see
     * [YearlyBudgetRepository.addLineItem]'s doc comment for the same shape of split. A
     * brand-new monthly budget resolves its yearly parent's local id first (creating an empty
     * yearly row if none exists yet) before the monthly row itself is ever written, so the FK
     * is populated from the first insert, not backfilled later. */
    suspend fun addLineItem(
        month: Int,
        year: Long,
        name: String,
        amount: Double,
        type: String,
    ): MonthlyBudgetResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = monthlyBudgetDao.getByMonthYear(month, year)
            if (existing == null) {
                val yearlyLocalId = YearlyBudgetRepository.resolveOrCreateLocalId(year)
                val items = BudgetLineItemOps.add(emptyList(), name, amount, type)
                val totals = items.computeTotals()
                val fresh =
                    MonthlyBudgetEntity(
                        id = UUID.randomUUID().toString(),
                        serverId = null,
                        yearlyBudgetId = yearlyLocalId,
                        month = month,
                        year = year,
                        totalExpenditures = totals.expenditures,
                        totalIncome = totals.income,
                        totalSavings = totals.savings,
                        totalTransactions = totals.count,
                        transactionsJson = items.toTransactionsJson(),
                        syncStatus = SyncStatus.PENDING,
                        dirty = true,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                monthlyBudgetDao.upsert(fresh)
                outboxDao.upsert(newOutboxEntry(OutboxEntityType.MONTHLY_BUDGET, fresh.id, OutboxOperation.CREATE, now))
                return@withTransaction fresh.toDetails()
            }
            val items = BudgetLineItemOps.add(parseLineItems(existing.transactionsJson), name, amount, type)
            val totals = items.computeTotals()
            val updated =
                existing.copy(
                    totalExpenditures = totals.expenditures,
                    totalIncome = totals.income,
                    totalSavings = totals.savings,
                    totalTransactions = totals.count,
                    transactionsJson = items.toTransactionsJson(),
                    dirty = true,
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = now,
                )
            monthlyBudgetDao.update(updated)
            enqueueOutbox(OutboxEntityType.MONTHLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    suspend fun deleteLineItem(
        month: Int,
        year: Long,
        lineItemId: String,
    ): MonthlyBudgetResponse? =
        database.withTransaction {
            val existing = monthlyBudgetDao.getByMonthYear(month, year) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val items = BudgetLineItemOps.delete(parseLineItems(existing.transactionsJson), lineItemId)
            val totals = items.computeTotals()
            val updated =
                existing.copy(
                    totalExpenditures = totals.expenditures,
                    totalIncome = totals.income,
                    totalSavings = totals.savings,
                    totalTransactions = totals.count,
                    transactionsJson = items.toTransactionsJson(),
                    dirty = true,
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = now,
                )
            monthlyBudgetDao.update(updated)
            enqueueOutbox(OutboxEntityType.MONTHLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    /** [resolvedYearlyLocalId] is looked up by the caller (`SyncWorker.pullMonthlyBudgets`, by
     * matching [details]' server-side `yearlyBudgetId` against already-pulled yearly rows) —
     * mirrors `SyncWorker.pullTransactions()` resolving `channelId` before calling
     * `TransactionRepository.reconcileFromServer`. */
    suspend fun reconcileFromServer(
        details: MonthlyBudgetResponse,
        resolvedYearlyLocalId: String?,
    ): MonthlyBudgetResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = monthlyBudgetDao.getAllIncludingDeleted().firstOrNull { it.serverId == details.id }
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val items = details.transactions.map { it.toLocalLineItem() }
                    val updated =
                        existing!!.copy(
                            yearlyBudgetId = resolvedYearlyLocalId ?: existing.yearlyBudgetId,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = items.toTransactionsJson(),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    monthlyBudgetDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val items = details.transactions.map { it.toLocalLineItem() }
                    val inserted =
                        MonthlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            yearlyBudgetId = resolvedYearlyLocalId,
                            month = details.month,
                            year = details.year,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = items.toTransactionsJson(),
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    monthlyBudgetDao.upsert(inserted)
                    inserted.toDetails()
                }
            }
        }

    /** Applied by `SyncWorker` after a push response comes back — see
     * [YearlyBudgetRepository.applyPushCompletion]'s doc comment, identical shape. */
    internal suspend fun applyPushCompletion(
        entityId: String,
        serverId: String?,
        dispatched: List<LocalBudgetLineItem>,
        responseItems: List<LocalBudgetLineItem>,
        clearDirty: Boolean,
    ) {
        val entity = monthlyBudgetDao.getById(entityId) ?: return
        val merged = BudgetLineItemMerge.merge(parseLineItems(entity.transactionsJson), dispatched, responseItems)
        val totals = merged.computeTotals()
        val now = System.currentTimeMillis()
        monthlyBudgetDao.update(
            entity.copy(
                serverId = serverId ?: entity.serverId,
                totalExpenditures = totals.expenditures,
                totalIncome = totals.income,
                totalSavings = totals.savings,
                totalTransactions = totals.count,
                transactionsJson = merged.toTransactionsJson(),
                dirty = !clearDirty,
                syncStatus = if (clearDirty) SyncStatus.SYNCED else SyncStatus.PENDING,
                updatedAt = now,
            ),
        )
    }

    private suspend fun enqueueOutbox(
        entityType: OutboxEntityType,
        entityId: String,
        newOp: OutboxOperation,
        now: Long,
    ) {
        val existing = outboxDao.findFor(entityType, entityId)
        when (val decision = OutboxCoalescer.coalesce(existing?.operation, existing?.status, newOp)) {
            is CoalesceDecision.WriteOutbox ->
                outboxDao.upsert(
                    upsertedOutboxEntry(existing, entityType, entityId, decision.operation, now),
                )
            is CoalesceDecision.LeaveInFlight -> Unit
            is CoalesceDecision.HardDeleteNoOutbox ->
                error("Unreachable: UPDATE-family coalescing against $entityType/$entityId never resolves to a hard delete")
        }
    }

    private fun newOutboxEntry(
        entityType: OutboxEntityType,
        entityId: String,
        operation: OutboxOperation,
        now: Long,
    ) = OutboxEntry(
        id = UUID.randomUUID().toString(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        status = SyncStatus.PENDING,
        attempts = 0,
        lastError = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun upsertedOutboxEntry(
        existing: OutboxEntry?,
        entityType: OutboxEntityType,
        entityId: String,
        operation: OutboxOperation,
        now: Long,
    ) = OutboxEntry(
        id = existing?.id ?: UUID.randomUUID().toString(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        status = SyncStatus.PENDING,
        attempts = existing?.attempts ?: 0,
        lastError = null,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}

internal fun MonthlyBudgetEntity.toDetails() =
    MonthlyBudgetResponse(
        id = serverId ?: id,
        userId = "",
        yearlyBudgetId = yearlyBudgetId ?: "",
        month = month,
        year = year,
        totalExpenditures = totalExpenditures,
        totalIncome = totalIncome,
        totalSavings = totalSavings,
        totalTransactions = totalTransactions,
        transactions = parseLineItems(transactionsJson).filter { it.pendingAction != "delete" }.map { it.toResponse() },
        createdAt = "",
        updatedAt = "",
    )
