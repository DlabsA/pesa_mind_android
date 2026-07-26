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
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** [details] mirrors what `observeYearlyBudget`/`observeMonthlyBudget` already return; [dirty]/
 * [updatedAt] are the extra sync-state fields `BudgetUiState.isFromCache`/`lastUpdated` need
 * now that there's a real local cache — see `YearlyBudgetRepository`/`MonthlyBudgetRepository`'s
 * `observe*Snapshot` doc comments (ADR-0006 Decision 4). */
data class BudgetSnapshot<T>(val details: T, val dirty: Boolean, val updatedAt: Long)

/**
 * Room-backed source of truth for yearly budgets (ADR-0006 / ADR-0004 "Slice B") — replaces
 * `BudgetManager`'s DataStore blob (never actually live in production, see ADR-0006/debt-
 * burndown A9) for every `BudgetViewModel`/`YearlyBudgetViewModel` call site.
 *
 * Structured exactly like `ChannelRepository`: plain singleton object + [init] via
 * [DatabaseEntryPoint], `database.withTransaction` for atomic entity-write + outbox-upsert.
 * `MonthlyBudgetRepository` makes one narrow read/write call into this object
 * ([resolveOrCreateLocalId]) to resolve a monthly budget's `yearlyBudgetId` FK before its
 * yearly parent has necessarily synced — the same shape of cross-repository dependency
 * `TransactionRepository`/`SyncWorker.pullTransactions()` already has on `ChannelDao`.
 */
object YearlyBudgetRepository {
    // internal, not private: lets a JVM test inject an in-memory Room database directly.
    internal lateinit var database: PesaMindDatabase
    private val yearlyBudgetDao get() = database.yearlyBudgetDao()
    private val outboxDao get() = database.outboxDao()

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    fun observeYearlyBudget(year: Long): Flow<YearlyBudgetResponse?> = yearlyBudgetDao.observeByYear(year).map { it?.toDetails() }

    /** See [BudgetSnapshot]'s doc comment. */
    fun observeYearlyBudgetSnapshot(year: Long): Flow<BudgetSnapshot<YearlyBudgetResponse>?> =
        yearlyBudgetDao.observeByYear(year).map { entity -> entity?.let { BudgetSnapshot(it.toDetails(), it.dirty, it.updatedAt) } }

    suspend fun getYearlyBudget(year: Long): YearlyBudgetResponse? = yearlyBudgetDao.getByYear(year)?.toDetails()

    /**
     * Resolves the *local* id of the live yearly budget row for [year], creating an empty one
     * (`PENDING`, `dirty`, outbox `CREATE`) if none exists yet. Called by
     * `MonthlyBudgetRepository.addLineItem` when a monthly budget is added before its yearly
     * parent exists locally — mirrors channels-before-transactions FK resolution in
     * `SyncWorker`, just resolved eagerly here instead of at push time, since the FK column
     * itself must be non-null-ready the moment the monthly row is written.
     */
    internal suspend fun resolveOrCreateLocalId(year: Long): String =
        database.withTransaction {
            yearlyBudgetDao.getByYear(year)?.let { return@withTransaction it.id }
            val now = System.currentTimeMillis()
            val entity =
                YearlyBudgetEntity(
                    id = UUID.randomUUID().toString(),
                    serverId = null,
                    year = year,
                    totalExpenditures = 0,
                    totalIncome = 0,
                    totalSavings = 0,
                    totalTransactions = 0,
                    transactionsJson = emptyList<LocalBudgetLineItem>().toTransactionsJson(),
                    syncStatus = SyncStatus.PENDING,
                    dirty = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            yearlyBudgetDao.upsert(entity)
            outboxDao.upsert(newOutboxEntry(OutboxEntityType.YEARLY_BUDGET, entity.id, OutboxOperation.CREATE, now))
            entity.id
        }

    /** Collapses `YearlyBudgetViewModel`'s old CREATE-vs-UPDATE branch into one call — a
     * brand-new budget is inserted with its outbox row written directly as `CREATE` (no
     * coalescing needed, nothing else can be queued yet); an existing budget is mutated and
     * routed through [enqueueOutbox] so `OutboxCoalescer` decides whether the outbox row stays
     * `CREATE` (never synced yet) or becomes `UPDATE` (already has a `serverId`). */
    suspend fun addLineItem(
        year: Long,
        name: String,
        amount: Double,
        type: String,
    ): YearlyBudgetResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = yearlyBudgetDao.getByYear(year)
            if (existing == null) {
                val items = BudgetLineItemOps.add(emptyList(), name, amount, type)
                val totals = items.computeTotals()
                val fresh =
                    YearlyBudgetEntity(
                        id = UUID.randomUUID().toString(),
                        serverId = null,
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
                yearlyBudgetDao.upsert(fresh)
                outboxDao.upsert(newOutboxEntry(OutboxEntityType.YEARLY_BUDGET, fresh.id, OutboxOperation.CREATE, now))
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
            yearlyBudgetDao.update(updated)
            enqueueOutbox(OutboxEntityType.YEARLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    suspend fun deleteLineItem(
        year: Long,
        lineItemId: String,
    ): YearlyBudgetResponse? =
        database.withTransaction {
            val existing = yearlyBudgetDao.getByYear(year) ?: return@withTransaction null
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
            yearlyBudgetDao.update(updated)
            enqueueOutbox(OutboxEntityType.YEARLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    /** Pull-reconciliation primitive, same invariant as `ChannelRepository.reconcileFromServer`:
     * never overwrite a `dirty=true` row from a server payload. Unlike Channel/Transaction,
     * the full `transactions` list is replaced wholesale from the server on `UpdateExisting` —
     * safe only because this branch is never reached for a dirty row in the first place. */
    suspend fun reconcileFromServer(details: YearlyBudgetResponse): YearlyBudgetResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = yearlyBudgetDao.getAllIncludingDeleted().firstOrNull { it.serverId == details.id }
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val items = details.transactions.map { it.toLocalLineItem() }
                    val updated =
                        existing!!.copy(
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = items.toTransactionsJson(),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    yearlyBudgetDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val items = details.transactions.map { it.toLocalLineItem() }
                    val inserted =
                        YearlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
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
                    yearlyBudgetDao.upsert(inserted)
                    inserted.toDetails()
                }
            }
        }

    /** Applied by `SyncWorker` after a push response comes back — merges the confirmed line
     * items ([BudgetLineItemMerge]) and either clears `dirty`/marks `SYNCED` or requeues,
     * exactly mirroring `SyncWorker.finishChannelPush`'s two branches. */
    internal suspend fun applyPushCompletion(
        entityId: String,
        serverId: String?,
        dispatched: List<LocalBudgetLineItem>,
        responseItems: List<LocalBudgetLineItem>,
        clearDirty: Boolean,
    ) {
        val entity = yearlyBudgetDao.getById(entityId) ?: return
        val merged = BudgetLineItemMerge.merge(parseLineItems(entity.transactionsJson), dispatched, responseItems)
        val totals = merged.computeTotals()
        val now = System.currentTimeMillis()
        yearlyBudgetDao.update(
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

internal fun YearlyBudgetEntity.toDetails() =
    YearlyBudgetResponse(
        id = serverId ?: id,
        userId = "",
        year = year,
        totalExpenditures = totalExpenditures,
        totalIncome = totalIncome,
        totalSavings = totalSavings,
        totalTransactions = totalTransactions,
        transactions = parseLineItems(transactionsJson).filter { it.pendingAction != "delete" }.map { it.toResponse() },
        createdAt = "",
        updatedAt = "",
        deletedAt = deletedAt?.toString(),
    )
