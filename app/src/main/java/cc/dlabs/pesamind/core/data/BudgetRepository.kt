package cc.dlabs.pesamind.core.data

import android.content.Context
import android.util.Log
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
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.BudgetTransactionOperation
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.OutboxPusher
import cc.dlabs.pesamind.core.utils.TransactionTypes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private val gson = Gson()
private val transactionListType = object : TypeToken<List<BudgetTransactionResponse>>() {}.type

/**
 * Room-backed source of truth for yearly and monthly budgets (ADR-0004 Slice B). Same
 * repository justification and DI pattern as [ChannelRepository]/[TransactionRepository].
 *
 * Line items ([YearlyBudgetEntity.transactionsJson]/[MonthlyBudgetEntity.transactionsJson])
 * have no dedicated local dirty-tracking: the backend's only mutation endpoint is a
 * whole-budget PATCH, so — matching those entities' own doc comments — "the whole budget" is
 * the real sync unit here, not each line item. A local add/delete just rewrites the entire
 * blob and recomputes the aggregate totals client-side (mirrors
 * `budget.Service.CalculateYearlyBudgetTotals`/`calculateMonthlyBudgetTotals` on the Go side
 * exactly, including their "totalTransactions is actually net movement, not a count" naming
 * quirk), then queues one coalesced whole-row outbox entry.
 * [OutboxPusher.pushYearlyBudgetEntry]/[OutboxPusher.pushMonthlyBudgetEntry] push the *entire
 * current line-item list* on every push (via the backend's legacy full-replace field) rather
 * than an incremental ops diff — equivalent in effect to replaying every accumulated offline
 * edit, without needing to track an ops log at all.
 *
 * A monthly budget's [MonthlyBudgetEntity.yearlyBudgetId] FKs to the *local* yearly budget
 * row, not a server id (see that entity's doc comment) — [addMonthlyTransaction] resolves it
 * from the local yearly budget for the same year, and refuses to create a monthly budget with
 * no local yearly budget yet, preserving `SetMonthlyBudgetViewModel`'s pre-existing "create a
 * yearly budget first" behavior rather than silently auto-creating one.
 */
object BudgetRepository {
    private const val TAG = "BudgetRepository"

    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase/DAO directly,
    // same as ChannelRepository/TransactionRepository.
    internal lateinit var database: PesaMindDatabase
    private val yearlyBudgetDao get() = database.yearlyBudgetDao()
    private val monthlyBudgetDao get() = database.monthlyBudgetDao()
    private val outboxDao get() = database.outboxDao()

    // Nullable, not lateinit — see ChannelRepository's identical fields for why: JVM unit
    // tests inject `database` directly without calling [init].
    private var networkMonitor: NetworkMonitor? = null
    private var outboxPusher: OutboxPusher? = null

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
        networkMonitor = NetworkMonitor(context.applicationContext)
        outboxPusher = OutboxPusher(database, ApiClient.api)
    }

    fun observeYearlyBudget(year: Long): Flow<YearlyBudgetResponse?> =
        flow {
            val userId = AccountManager.currentUserIdOrEmpty()
            emitAll(yearlyBudgetDao.observeByYear(userId, year).map { it?.toDetails() })
        }

    suspend fun getYearlyBudgetByYear(year: Long): YearlyBudgetResponse? =
        yearlyBudgetDao.getByYear(AccountManager.currentUserIdOrEmpty(), year)?.toDetails()

    /**
     * Local-first add: creates the yearly budget row if this is the first transaction for
     * [year], or appends to the existing row's line items otherwise. Either way the local
     * read reflects the new transaction immediately, with totals recomputed client-side.
     */
    suspend fun addYearlyTransaction(
        year: Long,
        name: String,
        amount: Double,
        type: String,
    ): YearlyBudgetResponse {
        val now = System.currentTimeMillis()
        val userId = AccountManager.currentUserIdOrEmpty()
        val newItem = BudgetTransactionResponse(id = UUID.randomUUID().toString(), name = name, amount = amount, type = type)
        val result =
            database.withTransaction {
                val existing = yearlyBudgetDao.getByYear(userId, year)
                if (existing == null) {
                    val transactions = listOf(newItem)
                    val totals = computeTotals(transactions)
                    val entity =
                        YearlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = null,
                            userId = userId,
                            year = year,
                            totalExpenditures = totals.expense,
                            totalIncome = totals.income,
                            totalSavings = totals.savings,
                            totalTransactions = totals.net,
                            transactionsJson = gson.toJson(transactions),
                            syncStatus = SyncStatus.PENDING,
                            dirty = true,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    yearlyBudgetDao.upsert(entity)
                    outboxDao.upsert(newOutboxEntry(OutboxEntityType.YEARLY_BUDGET, entity.id, OutboxOperation.CREATE, now))
                    entity.toDetails()
                } else {
                    val transactions = parseTransactions(existing.transactionsJson) + newItem
                    val totals = computeTotals(transactions)
                    val updated =
                        existing.copy(
                            transactionsJson = gson.toJson(transactions),
                            totalExpenditures = totals.expense,
                            totalIncome = totals.income,
                            totalSavings = totals.savings,
                            totalTransactions = totals.net,
                            dirty = true,
                            syncStatus = SyncStatus.PENDING,
                            updatedAt = now,
                        )
                    yearlyBudgetDao.update(updated)
                    enqueueOutbox(OutboxEntityType.YEARLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
                    updated.toDetails()
                }
            }
        pushYearlyBudgetEagerly(result.id)
        return result
    }

    /** Local-first delete of a single line item, identified by [transactionId] — either a
     * server-assigned id (from a previous sync) or a locally-generated placeholder for a
     * line item that was added but never synced. Returns null if [year] has no local budget. */
    suspend fun deleteYearlyTransaction(
        year: Long,
        transactionId: String,
    ): YearlyBudgetResponse? {
        val now = System.currentTimeMillis()
        val result =
            database.withTransaction {
                val existing = yearlyBudgetDao.getByYear(AccountManager.currentUserIdOrEmpty(), year) ?: return@withTransaction null
                val transactions = parseTransactions(existing.transactionsJson).filterNot { it.id == transactionId }
                val totals = computeTotals(transactions)
                val updated =
                    existing.copy(
                        transactionsJson = gson.toJson(transactions),
                        totalExpenditures = totals.expense,
                        totalIncome = totals.income,
                        totalSavings = totals.savings,
                        totalTransactions = totals.net,
                        dirty = true,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = now,
                    )
                yearlyBudgetDao.update(updated)
                enqueueOutbox(OutboxEntityType.YEARLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
                updated.toDetails()
            }
        result?.let { pushYearlyBudgetEagerly(it.id) }
        return result
    }

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant: "one live row per serverId, never
     * overwrite a dirty=true row from a server payload") — mirrors
     * `ChannelRepository.reconcileFromServer` exactly. Used by `SyncWorker`'s full pull.
     */
    suspend fun reconcileFromServer(details: YearlyBudgetResponse): YearlyBudgetResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            // findByServerId deliberately includes soft-deleted rows so ReconcileResolver can
            // see (and refuse to touch) a tombstone instead of missing it and inserting a live
            // duplicate for the same serverId.
            val existing = yearlyBudgetDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            year = details.year,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = gson.toJson(details.transactions),
                            // A successful pull means local state now matches the server —
                            // seed the diff baseline OutboxPusher uses to build safe
                            // transaction_ops on the next push (see buildTransactionOps).
                            lastSyncedTransactionsJson = gson.toJson(details.transactions),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    yearlyBudgetDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    // A pull only ever returns the authenticated user's own budgets, so the
                    // current session's id is always correct — details.userId (the server's
                    // echoed user_id) must not be trusted for local scoping, since every read
                    // query filters by AccountManager's cached id, not the server's. Mirrors
                    // TransactionRepository.reconcileFromServer.
                    val inserted =
                        YearlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = AccountManager.currentUserIdOrEmpty(),
                            year = details.year,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = gson.toJson(details.transactions),
                            lastSyncedTransactionsJson = gson.toJson(details.transactions),
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

    // ── Monthly Budgets ─────────────────────────────────────────────────────

    fun observeMonthlyBudget(
        month: Int,
        year: Long,
    ): Flow<MonthlyBudgetResponse?> =
        flow {
            val userId = AccountManager.currentUserIdOrEmpty()
            emitAll(monthlyBudgetDao.observeByMonthYear(userId, month, year).map { it?.toDetails() })
        }

    suspend fun getMonthlyBudgetByMonthYear(
        month: Int,
        year: Long,
    ): MonthlyBudgetResponse? = monthlyBudgetDao.getByMonthYear(AccountManager.currentUserIdOrEmpty(), month, year)?.toDetails()

    /**
     * Local-first add: creates the monthly budget row if this is the first transaction for
     * [month]/[year], or appends to the existing row's line items otherwise — same shape as
     * [addYearlyTransaction]. Requires a local yearly budget for [year] to already exist
     * (throws [IllegalStateException] with the same message
     * `SetMonthlyBudgetViewModel.createBudgetWithTransaction` already showed otherwise) —
     * deliberately not auto-created, preserving the existing "create a yearly budget first"
     * product behavior.
     */
    suspend fun addMonthlyTransaction(
        month: Int,
        year: Long,
        name: String,
        amount: Double,
        type: String,
    ): MonthlyBudgetResponse {
        val userId = AccountManager.currentUserIdOrEmpty()
        val yearlyBudget =
            yearlyBudgetDao.getByYear(userId, year)
                ?: error("No yearly budget found for $year. Create one first.")
        val now = System.currentTimeMillis()
        val newItem = BudgetTransactionResponse(id = UUID.randomUUID().toString(), name = name, amount = amount, type = type)
        val result =
            database.withTransaction {
                val existing = monthlyBudgetDao.getByMonthYear(userId, month, year)
                if (existing == null) {
                    val transactions = listOf(newItem)
                    val totals = computeTotals(transactions)
                    val entity =
                        MonthlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = null,
                            userId = userId,
                            yearlyBudgetId = yearlyBudget.id,
                            month = month,
                            year = year,
                            totalExpenditures = totals.expense,
                            totalIncome = totals.income,
                            totalSavings = totals.savings,
                            totalTransactions = totals.net,
                            transactionsJson = gson.toJson(transactions),
                            syncStatus = SyncStatus.PENDING,
                            dirty = true,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    monthlyBudgetDao.upsert(entity)
                    outboxDao.upsert(newOutboxEntry(OutboxEntityType.MONTHLY_BUDGET, entity.id, OutboxOperation.CREATE, now))
                    entity.toDetails()
                } else {
                    val transactions = parseTransactions(existing.transactionsJson) + newItem
                    val totals = computeTotals(transactions)
                    val updated =
                        existing.copy(
                            transactionsJson = gson.toJson(transactions),
                            totalExpenditures = totals.expense,
                            totalIncome = totals.income,
                            totalSavings = totals.savings,
                            totalTransactions = totals.net,
                            dirty = true,
                            syncStatus = SyncStatus.PENDING,
                            updatedAt = now,
                        )
                    monthlyBudgetDao.update(updated)
                    enqueueOutbox(OutboxEntityType.MONTHLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
                    updated.toDetails()
                }
            }
        pushMonthlyBudgetEagerly(result.id)
        return result
    }

    /** Local-first delete of a single line item — mirrors [deleteYearlyTransaction]. */
    suspend fun deleteMonthlyTransaction(
        month: Int,
        year: Long,
        transactionId: String,
    ): MonthlyBudgetResponse? {
        val now = System.currentTimeMillis()
        val result =
            database.withTransaction {
                val existing =
                    monthlyBudgetDao.getByMonthYear(AccountManager.currentUserIdOrEmpty(), month, year)
                        ?: return@withTransaction null
                val transactions = parseTransactions(existing.transactionsJson).filterNot { it.id == transactionId }
                val totals = computeTotals(transactions)
                val updated =
                    existing.copy(
                        transactionsJson = gson.toJson(transactions),
                        totalExpenditures = totals.expense,
                        totalIncome = totals.income,
                        totalSavings = totals.savings,
                        totalTransactions = totals.net,
                        dirty = true,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = now,
                    )
                monthlyBudgetDao.update(updated)
                enqueueOutbox(OutboxEntityType.MONTHLY_BUDGET, updated.id, OutboxOperation.UPDATE, now)
                updated.toDetails()
            }
        result?.let { pushMonthlyBudgetEagerly(it.id) }
        return result
    }

    /**
     * Pull-reconciliation primitive for monthly budgets — mirrors [reconcileFromServer]
     * exactly, except [details]'s `yearlyBudgetId` is a *server* id that must be resolved
     * back to the corresponding local yearly budget row (per [MonthlyBudgetEntity]'s doc
     * comment on why the local FK isn't a server id). A pulled monthly budget whose parent
     * yearly budget hasn't been pulled yet (and so has no local row at all) is skipped for
     * this run — `SyncWorker` pulls yearly budgets before monthly ones, so a subsequent run
     * picks it up once the parent exists locally.
     */
    suspend fun reconcileMonthlyFromServer(details: MonthlyBudgetResponse): MonthlyBudgetResponse? =
        database.withTransaction {
            val localYearlyBudget = yearlyBudgetDao.findByServerId(details.yearlyBudgetId) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val existing = monthlyBudgetDao.getAllIncludingDeleted().find { it.serverId == details.id }
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            yearlyBudgetId = localYearlyBudget.id,
                            month = details.month,
                            year = details.year,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = gson.toJson(details.transactions),
                            // A successful pull means local state now matches the server —
                            // seed the diff baseline OutboxPusher uses to build safe
                            // transaction_ops on the next push (see buildTransactionOps).
                            lastSyncedTransactionsJson = gson.toJson(details.transactions),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    monthlyBudgetDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    // A pull only ever returns the authenticated user's own budgets, so the
                    // current session's id is always correct — details.userId (the server's
                    // echoed user_id) must not be trusted for local scoping, since every read
                    // query filters by AccountManager's cached id, not the server's. Mirrors
                    // TransactionRepository.reconcileFromServer.
                    val inserted =
                        MonthlyBudgetEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = AccountManager.currentUserIdOrEmpty(),
                            yearlyBudgetId = localYearlyBudget.id,
                            month = details.month,
                            year = details.year,
                            totalExpenditures = details.totalExpenditures,
                            totalIncome = details.totalIncome,
                            totalSavings = details.totalSavings,
                            totalTransactions = details.totalTransactions,
                            transactionsJson = gson.toJson(details.transactions),
                            lastSyncedTransactionsJson = gson.toJson(details.transactions),
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

    // ── Shared ──────────────────────────────────────────────────────────────

    /** Best-effort immediate push, fired right after a create/update's local commit when the
     * device is already known to be online — see [TransactionRepository]'s twin for the full
     * rationale. Never awaited by the caller; the outbox row this pushes stays as the
     * durable fallback regardless of outcome. */
    private fun pushYearlyBudgetEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushYearlyBudgetEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for yearly budget $entityId; will retry on next sync", e)
            }
        }
    }

    /** See [pushYearlyBudgetEagerly]. */
    private fun pushMonthlyBudgetEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushMonthlyBudgetEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for monthly budget $entityId; will retry on next sync", e)
            }
        }
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
                outboxDao.upsert(upsertedOutboxEntry(existing, entityType, entityId, decision.operation, now))
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

    internal fun parseTransactions(json: String): List<BudgetTransactionResponse> =
        if (json.isBlank()) emptyList() else gson.fromJson(json, transactionListType)

    /**
     * Diffs [current] against [baseline] (the line-item list as of the last successful server
     * sync — empty if this row has never synced) to build a minimal `transaction_ops` batch,
     * used by [cc.dlabs.pesamind.core.sync.OutboxPusher]'s push functions in place of the
     * legacy full-replace field. This is the fix for a real, confirmed data-loss bug: the
     * legacy field carries no ids, so the backend's update handler — which deletes any
     * existing server-side transaction whose id isn't present in the incoming list — wiped
     * history on *every* push, since that field never carried ids at all (see
     * `MIGRATION_6_7`'s doc comment for the full incident).
     *
     * A delete op is only ever emitted for an item literally present in [baseline] — so a
     * never-synced row ([baseline] empty/null) can structurally only ever produce "add" ops,
     * never "delete": a fresh local row can no longer wipe real server-side history just by
     * being pushed. This does *not* independently guard against a baseline that's genuinely
     * stale relative to the server (e.g. a separate reconcile bug losing track of a
     * previously-synced item between syncs) — that would still surface as an apparent
     * deletion here, same as it would to a human diffing two lists by hand. What it fixes is
     * the previous behavior, where *every* push — even a single new item, with a perfectly
     * healthy local cache — necessarily wiped everything not in that one push's payload.
     */
    internal fun buildTransactionOps(
        baseline: List<BudgetTransactionResponse>,
        current: List<BudgetTransactionResponse>,
    ): List<BudgetTransactionOperation> {
        val baselineById = baseline.associateBy { it.id }
        val currentIds = current.mapTo(mutableSetOf()) { it.id }
        val ops = mutableListOf<BudgetTransactionOperation>()

        for (item in current) {
            val before = baselineById[item.id]
            if (before == null) {
                // New since baseline — the server assigns a real id on success, "add" needs none.
                ops += BudgetTransactionOperation(name = item.name, amount = item.amount, type = item.type, action = "add")
            } else if (before.name != item.name || before.amount != item.amount || before.type != item.type) {
                ops += BudgetTransactionOperation(id = item.id, name = item.name, amount = item.amount, type = item.type, action = "update")
            }
        }
        for (before in baseline) {
            if (before.id !in currentIds) {
                ops += BudgetTransactionOperation(id = before.id, action = "delete")
            }
        }
        return ops
    }

    private data class Totals(val income: Long, val expense: Long, val savings: Long, val net: Long)

    /** Mirrors `budget.Service.CalculateYearlyBudgetTotals`/`calculateMonthlyBudgetTotals`'s
     * formula exactly, including the "totalTransactions is actually net movement, not a
     * count" naming quirk. */
    private fun computeTotals(transactions: List<BudgetTransactionResponse>): Totals {
        var income = 0L
        var expense = 0L
        var savings = 0L
        for (t in transactions) {
            val amount = t.amount.toLong()
            when (t.type) {
                TransactionTypes.INCOME -> income += amount
                TransactionTypes.EXPENSE -> expense += amount
                TransactionTypes.SAVINGS -> savings += amount
            }
        }
        return Totals(income, expense, savings, income - expense - savings)
    }
}

internal fun YearlyBudgetEntity.toDetails() =
    YearlyBudgetResponse(
        id = id,
        userId = userId,
        year = year,
        totalExpenditures = totalExpenditures,
        totalIncome = totalIncome,
        totalSavings = totalSavings,
        totalTransactions = totalTransactions,
        transactions = BudgetRepository.parseTransactions(transactionsJson),
        createdAt = "",
        updatedAt = "",
    )

internal fun MonthlyBudgetEntity.toDetails() =
    MonthlyBudgetResponse(
        id = id,
        userId = userId,
        yearlyBudgetId = yearlyBudgetId.orEmpty(),
        month = month,
        year = year,
        totalExpenditures = totalExpenditures,
        totalIncome = totalIncome,
        totalSavings = totalSavings,
        totalTransactions = totalTransactions,
        transactions = BudgetRepository.parseTransactions(transactionsJson),
        createdAt = "",
        updatedAt = "",
    )
