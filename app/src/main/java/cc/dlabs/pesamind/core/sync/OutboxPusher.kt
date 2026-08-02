package cc.dlabs.pesamind.core.sync

import android.util.Log
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.PushCompletionDecision
import cc.dlabs.pesamind.core.database.PushCompletionResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.dao.ChannelDao
import cc.dlabs.pesamind.core.database.dao.MonthlyBudgetDao
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.dao.ProcessedMessageDao
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.dao.YearlyBudgetDao
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.ProcessedMessageEntity
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.models.BudgetTransactionRequest
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.CreateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.CreateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.ProcessedMessageRequest
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UpdateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import com.google.gson.Gson
import java.io.IOException

private const val TAG = "OutboxPusher"

/**
 * Pushes a single outbox row to the server. Extracted out of [SyncWorker] so the exact same
 * claim/dispatch/resolve logic can be triggered two ways: [SyncWorker]'s periodic/connectivity-
 * regain drain (which loops over every PENDING row), and a repository's eager "push this one
 * row right now" call the instant it's created locally, while the app is in the foreground and
 * already knows it's online — no reason to wait on WorkManager scheduling for that case.
 *
 * Every method here is safe to call concurrently with [SyncWorker]'s own drain of the same row:
 * both re-read the outbox row fresh and claim it by flipping status to SYNCING before dispatching,
 * so whichever caller gets there first wins and the other observes `status != PENDING` and skips.
 */
class OutboxPusher(
    private val database: PesaMindDatabase,
    private val api: ApiService,
) {
    sealed class PushResult {
        /** [serverId] is non-null only when this call resolved a *new* serverId (a CREATE that
         * just succeeded) — callers that need to guard a same-run pull against re-deleting a
         * row they just pushed should track it themselves; this class has no run-scoped state
         * of its own. */
        data class Success(val serverId: String?) : PushResult()

        object PermanentFailure : PushResult()

        object TransientFailure : PushResult()

        object Skipped : PushResult()
    }

    /**
     * 401 must never be lumped in with genuine 4xx data-rejections (400/404/422/...): by the
     * time a response reaches here, [cc.dlabs.pesamind.core.network.TokenRefreshInterceptor]
     * has already tried exactly one token refresh + retry, so a 401 here means either the
     * refresh token was also invalid (the interceptor already logged the user out) or a
     * transient race — in both cases the *data* was never actually rejected. Treating it as
     * permanent stranded rows in FAILED forever, even past a fresh re-login, since neither
     * [SyncWorker] nor the eager-push callers ever re-drain a FAILED row. 401 falls through to
     * the transient branch instead, so it naturally retries once a valid token exists again.
     */
    private fun isPermanentFailureCode(code: Int): Boolean = code in 400..499 && code != 401

    // ── Channels ────────────────────────────────────────────────────────────

    suspend fun pushChannelEntry(entityId: String): PushResult {
        val outboxDao = database.outboxDao()
        val channelDao = database.channelDao()

        // Re-read the outbox row fresh right before claiming it — a stale snapshot's `operation`
        // can have been coalesced (e.g. UPDATE→DELETE) since it was taken. `status != PENDING`
        // means it's already been claimed/resolved by another caller.
        val current = outboxDao.findFor(OutboxEntityType.CHANNEL, entityId) ?: return PushResult.Skipped
        if (current.status != SyncStatus.PENDING) return PushResult.Skipped

        val entity = channelDao.getById(entityId)
        if (entity == null) {
            // Entity vanished under an outbox row that should have been coalesced away with it
            // (e.g. CREATE+DELETE→HardDeleteNoOutbox already removes both together) — defensive
            // cleanup, not an expected path.
            outboxDao.delete(current.id)
            return PushResult.Skipped
        }
        if (current.operation != OutboxOperation.CREATE && entity.serverId == null) {
            // UPDATE/DELETE only ever coalesce from a prior UPDATE, which itself only exists
            // once a CREATE has synced. A null serverId here means that invariant broke; fail
            // loudly but don't crash the caller.
            markChannelPermanentFailure(
                current,
                entity,
                channelDao,
                outboxDao,
                "Invariant violation: ${current.operation} with no serverId",
            )
            return PushResult.PermanentFailure
        }

        val now = System.currentTimeMillis()
        // Atomically claim the row (see OutboxDao.claimIfPending's doc comment) — a concurrent
        // caller that loses the race observes 0 rows affected and skips instead of also
        // dispatching this same row. Any repository write that lands after this point sees
        // status=SYNCING and coalesces via LeaveInFlight, leaving this outbox row alone so the
        // updatedAt comparison below stays meaningful.
        if (outboxDao.claimIfPending(current.id, now) == 0) return PushResult.Skipped
        val dispatchUpdatedAt = entity.updatedAt

        return try {
            when (current.operation) {
                OutboxOperation.CREATE -> {
                    val response =
                        api.createChannel(
                            CreateChannelRequest(
                                id = entity.id,
                                name = entity.name,
                                channelType = entity.channelType,
                                description = entity.description,
                                channelDesc = entity.channelDesc,
                                status = entity.status,
                            ),
                        )
                    when {
                        response.isSuccessful ->
                            finishChannelPush(current, channelDao, outboxDao, dispatchUpdatedAt, response.body()?.id?.ifBlank { null })
                        isPermanentFailureCode(response.code()) -> {
                            markChannelPermanentFailure(
                                current,
                                entity,
                                channelDao,
                                outboxDao,
                                "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                            )
                            PushResult.PermanentFailure
                        }
                        else -> {
                            markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                            PushResult.TransientFailure
                        }
                    }
                }
                OutboxOperation.UPDATE -> {
                    val response =
                        api.updateChannel(
                            entity.serverId!!,
                            UpdateChannelRequest(entity.name, entity.description, entity.channelDesc, entity.status),
                        )
                    when {
                        response.isSuccessful -> finishChannelPush(current, channelDao, outboxDao, dispatchUpdatedAt, null)
                        isPermanentFailureCode(response.code()) -> {
                            markChannelPermanentFailure(
                                current,
                                entity,
                                channelDao,
                                outboxDao,
                                "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                            )
                            PushResult.PermanentFailure
                        }
                        else -> {
                            markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                            PushResult.TransientFailure
                        }
                    }
                }
                OutboxOperation.DELETE -> {
                    val response = api.deleteChannel(entity.serverId!!)
                    when {
                        response.isSuccessful -> {
                            // Terminal: the server has confirmed the delete. Hard-delete now —
                            // mandate #3 ("nothing is hard-deleted until the server confirms")
                            // means this IS the confirmation.
                            channelDao.hardDelete(entity.id)
                            outboxDao.delete(current.id)
                            PushResult.Success(null)
                        }
                        isPermanentFailureCode(response.code()) -> {
                            markChannelPermanentFailure(
                                current,
                                entity,
                                channelDao,
                                outboxDao,
                                "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                            )
                            PushResult.PermanentFailure
                        }
                        else -> {
                            markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                            PushResult.TransientFailure
                        }
                    }
                }
            }
        } catch (e: IOException) {
            markTransientFailure(current, outboxDao, e.message ?: "network error")
            PushResult.TransientFailure
        }
    }

    private suspend fun finishChannelPush(
        current: OutboxEntry,
        channelDao: ChannelDao,
        outboxDao: OutboxDao,
        dispatchUpdatedAt: Long,
        responseServerId: String?,
    ): PushResult {
        val now = System.currentTimeMillis()
        val latest = channelDao.getById(current.entityId) ?: return PushResult.Skipped
        return when (
            val decision =
                PushCompletionResolver.resolve(
                    current.operation,
                    dispatchUpdatedAt,
                    latest.updatedAt,
                    responseServerId,
                )
        ) {
            is PushCompletionDecision.ClearAndSync -> {
                val resolvedServerId = decision.serverId ?: latest.serverId
                channelDao.update(latest.copy(serverId = resolvedServerId, dirty = false, syncStatus = SyncStatus.SYNCED, updatedAt = now))
                outboxDao.delete(current.id)
                PushResult.Success(resolvedServerId)
            }
            is PushCompletionDecision.RequeueDirty -> {
                channelDao.update(
                    latest.copy(serverId = decision.serverId ?: latest.serverId, syncStatus = SyncStatus.PENDING, updatedAt = now),
                )
                outboxDao.update(
                    current.copy(
                        operation = decision.nextOperation,
                        status = SyncStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        updatedAt = now,
                    ),
                )
                PushResult.Skipped
            }
        }
    }

    private suspend fun markChannelPermanentFailure(
        current: OutboxEntry,
        entity: ChannelEntity,
        channelDao: ChannelDao,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        Log.w(TAG, "Channel ${entity.id} push permanently failed: $error")
        channelDao.update(entity.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
        outboxDao.update(current.copy(status = SyncStatus.FAILED, lastError = error, updatedAt = now))
    }

    // ── Transactions ────────────────────────────────────────────────────────

    suspend fun pushTransactionEntry(entityId: String): PushResult {
        val outboxDao = database.outboxDao()
        val transactionDao = database.transactionDao()
        val channelDao = database.channelDao()

        // See pushChannelEntry's comment on re-reading fresh instead of trusting a stale snapshot.
        val current = outboxDao.findFor(OutboxEntityType.TRANSACTION, entityId) ?: return PushResult.Skipped
        if (current.status != SyncStatus.PENDING) return PushResult.Skipped

        val entity = transactionDao.getById(entityId)
        if (entity == null) {
            outboxDao.delete(current.id)
            return PushResult.Skipped
        }
        if (current.operation != OutboxOperation.CREATE) {
            if (entity.serverId != null) {
                // The CREATE already succeeded (this row has a serverId) — a stray UPDATE/
                // DELETE here is [OutboxDao.claimIfPending]'s now-closed double-dispatch race
                // misfiring PushCompletionResolver's CREATE->UPDATE requeue (the only way this
                // was ever reachable: no UI lets a user edit a transaction today, and no
                // update/delete transaction endpoint exists to send one to regardless). The
                // data that matters is already correctly on the server, so resolve this as
                // synced instead of surfacing a permanent "sync failed" for a row that isn't
                // actually missing anything.
                val now = System.currentTimeMillis()
                transactionDao.update(entity.copy(dirty = false, syncStatus = SyncStatus.SYNCED, updatedAt = now))
                outboxDao.delete(current.id)
                return PushResult.Success(entity.serverId)
            }
            // No update/delete transaction endpoint exists, and this row was never even
            // created server-side — an outbox invariant broke somewhere upstream; fail loudly.
            markTransactionPermanentFailure(
                current,
                entity,
                transactionDao,
                outboxDao,
                "Unsupported transaction outbox operation: ${current.operation}",
            )
            return PushResult.PermanentFailure
        }

        val channelEntity = entity.channelId?.let { channelDao.getById(it) }
        if (entity.channelId != null && channelEntity?.serverId == null) {
            if (channelEntity?.syncStatus == SyncStatus.FAILED) {
                // The channel this transaction depends on permanently failed (4xx, never
                // auto-retried) — it will never gain a serverId on its own, so re-skipping this
                // transaction forever would strand it PENDING with no visible signal. Surface the
                // same terminal state instead of silently retrying indefinitely.
                markTransactionPermanentFailure(
                    current,
                    entity,
                    transactionDao,
                    outboxDao,
                    "Blocked: channel ${entity.channelId} failed to sync",
                )
                return PushResult.PermanentFailure
            }
            // Channel hasn't synced yet (still PENDING/SYNCING) — its own outbox entry will
            // populate serverId on a future push. Leave this row PENDING and unclaimed; do not
            // mark SYNCING for a call we're not making.
            return PushResult.Skipped
        }
        val channelServerId = channelEntity?.serverId

        val now = System.currentTimeMillis()
        if (outboxDao.claimIfPending(current.id, now) == 0) return PushResult.Skipped
        val dispatchUpdatedAt = entity.updatedAt

        return try {
            val response =
                api.createTransaction(
                    TransactionRequest(
                        id = entity.id,
                        amount = entity.amount,
                        type = entity.type,
                        note = entity.note,
                        channelId = channelServerId.orEmpty(),
                    ),
                )
            when {
                response.isSuccessful ->
                    finishTransactionPush(current, transactionDao, outboxDao, dispatchUpdatedAt, response.body()?.id?.ifBlank { null })
                isPermanentFailureCode(response.code()) -> {
                    markTransactionPermanentFailure(
                        current,
                        entity,
                        transactionDao,
                        outboxDao,
                        "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                    )
                    PushResult.PermanentFailure
                }
                else -> {
                    markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                    PushResult.TransientFailure
                }
            }
        } catch (e: IOException) {
            markTransientFailure(current, outboxDao, e.message ?: "network error")
            PushResult.TransientFailure
        }
    }

    private suspend fun finishTransactionPush(
        current: OutboxEntry,
        transactionDao: TransactionDao,
        outboxDao: OutboxDao,
        dispatchUpdatedAt: Long,
        responseServerId: String?,
    ): PushResult {
        val now = System.currentTimeMillis()
        val latest = transactionDao.getById(current.entityId) ?: return PushResult.Skipped
        return when (
            val decision =
                PushCompletionResolver.resolve(
                    current.operation,
                    dispatchUpdatedAt,
                    latest.updatedAt,
                    responseServerId,
                )
        ) {
            is PushCompletionDecision.ClearAndSync -> {
                val resolvedServerId = decision.serverId ?: latest.serverId
                transactionDao.update(
                    latest.copy(serverId = resolvedServerId, dirty = false, syncStatus = SyncStatus.SYNCED, updatedAt = now),
                )
                outboxDao.delete(current.id)
                PushResult.Success(resolvedServerId)
            }
            is PushCompletionDecision.RequeueDirty -> {
                // No update endpoint exists for transactions; a mid-flight edit isn't reachable
                // from any UI today, but honor the same requeue contract as Channel for when it
                // eventually is.
                transactionDao.update(
                    latest.copy(serverId = decision.serverId ?: latest.serverId, syncStatus = SyncStatus.PENDING, updatedAt = now),
                )
                outboxDao.update(
                    current.copy(
                        operation = decision.nextOperation,
                        status = SyncStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        updatedAt = now,
                    ),
                )
                PushResult.Skipped
            }
        }
    }

    private suspend fun markTransactionPermanentFailure(
        current: OutboxEntry,
        entity: TransactionEntity,
        transactionDao: TransactionDao,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        Log.w(TAG, "Transaction ${entity.id} push permanently failed: $error")
        transactionDao.update(entity.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
        outboxDao.update(current.copy(status = SyncStatus.FAILED, lastError = error, updatedAt = now))
    }

    // ── Yearly Budgets ──────────────────────────────────────────────────────

    /**
     * Pushes the *entire current line-item list* on every call (via the backend's legacy
     * full-replace field, `transactions`, not the `transaction_ops` diff field) — see
     * [BudgetRepository]'s class doc for why that's equivalent to replaying every
     * accumulated offline edit without needing an ops log at all.
     */
    suspend fun pushYearlyBudgetEntry(entityId: String): PushResult {
        val outboxDao = database.outboxDao()
        val yearlyBudgetDao = database.yearlyBudgetDao()

        val current = outboxDao.findFor(OutboxEntityType.YEARLY_BUDGET, entityId) ?: return PushResult.Skipped
        if (current.status != SyncStatus.PENDING) return PushResult.Skipped

        val entity = yearlyBudgetDao.getById(entityId)
        if (entity == null) {
            outboxDao.delete(current.id)
            return PushResult.Skipped
        }
        if (current.operation == OutboxOperation.UPDATE && entity.serverId == null) {
            // UPDATE only ever coalesces from a prior UPDATE, which itself only exists once
            // a CREATE has synced. A null serverId here means that invariant broke.
            markYearlyBudgetPermanentFailure(current, entity, yearlyBudgetDao, outboxDao, "Invariant violation: UPDATE with no serverId")
            return PushResult.PermanentFailure
        }
        if (current.operation == OutboxOperation.DELETE) {
            // No delete flow exists from the app's UI for yearly budgets today — defensive
            // only, mirroring pushTransactionEntry's "unsupported operation" guard.
            markYearlyBudgetPermanentFailure(
                current,
                entity,
                yearlyBudgetDao,
                outboxDao,
                "Unsupported yearly budget outbox operation: DELETE",
            )
            return PushResult.PermanentFailure
        }

        val now = System.currentTimeMillis()
        if (outboxDao.claimIfPending(current.id, now) == 0) return PushResult.Skipped
        val dispatchUpdatedAt = entity.updatedAt
        val requestTransactions =
            BudgetRepository.parseTransactions(entity.transactionsJson).map {
                BudgetTransactionRequest(name = it.name, amount = it.amount, type = it.type)
            }

        return try {
            val response =
                when (current.operation) {
                    OutboxOperation.CREATE ->
                        api.createYearlyBudget(
                            CreateYearlyBudgetRequest(id = entity.id, year = entity.year, transactions = requestTransactions),
                        )
                    else ->
                        api.updateYearlyBudget(entity.serverId!!, UpdateYearlyBudgetRequest(transactions = requestTransactions))
                }
            when {
                response.isSuccessful ->
                    finishYearlyBudgetPush(current, yearlyBudgetDao, outboxDao, dispatchUpdatedAt, response.body()!!)
                isPermanentFailureCode(response.code()) -> {
                    markYearlyBudgetPermanentFailure(
                        current,
                        entity,
                        yearlyBudgetDao,
                        outboxDao,
                        "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                    )
                    PushResult.PermanentFailure
                }
                else -> {
                    markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                    PushResult.TransientFailure
                }
            }
        } catch (e: IOException) {
            markTransientFailure(current, outboxDao, e.message ?: "network error")
            PushResult.TransientFailure
        }
    }

    private suspend fun finishYearlyBudgetPush(
        current: OutboxEntry,
        yearlyBudgetDao: YearlyBudgetDao,
        outboxDao: OutboxDao,
        dispatchUpdatedAt: Long,
        responseBody: YearlyBudgetResponse,
    ): PushResult {
        val now = System.currentTimeMillis()
        val latest = yearlyBudgetDao.getById(current.entityId) ?: return PushResult.Skipped
        return when (
            val decision = PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseBody.id)
        ) {
            is PushCompletionDecision.ClearAndSync -> {
                val resolvedServerId = decision.serverId ?: latest.serverId
                yearlyBudgetDao.update(
                    latest.copy(
                        serverId = resolvedServerId,
                        transactionsJson = Gson().toJson(responseBody.transactions),
                        totalExpenditures = responseBody.totalExpenditures,
                        totalIncome = responseBody.totalIncome,
                        totalSavings = responseBody.totalSavings,
                        totalTransactions = responseBody.totalTransactions,
                        dirty = false,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = now,
                    ),
                )
                outboxDao.delete(current.id)
                PushResult.Success(resolvedServerId)
            }
            is PushCompletionDecision.RequeueDirty -> {
                // A local edit landed while this push was in flight — its transactionsJson
                // is newer than what was just sent/received here, so only serverId is safe
                // to persist; overwriting transactionsJson with this response would silently
                // drop that newer edit. The requeued push sends the newer state next time.
                yearlyBudgetDao.update(
                    latest.copy(serverId = decision.serverId ?: latest.serverId, syncStatus = SyncStatus.PENDING, updatedAt = now),
                )
                outboxDao.update(
                    current.copy(
                        operation = decision.nextOperation,
                        status = SyncStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        updatedAt = now,
                    ),
                )
                PushResult.Skipped
            }
        }
    }

    private suspend fun markYearlyBudgetPermanentFailure(
        current: OutboxEntry,
        entity: YearlyBudgetEntity,
        yearlyBudgetDao: YearlyBudgetDao,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        Log.w(TAG, "Yearly budget ${entity.id} push permanently failed: $error")
        yearlyBudgetDao.update(entity.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
        outboxDao.update(current.copy(status = SyncStatus.FAILED, lastError = error, updatedAt = now))
    }

    // ── Monthly Budgets ─────────────────────────────────────────────────────

    /**
     * Same whole-list-replace strategy as [pushYearlyBudgetEntry]. A monthly budget's local
     * `yearlyBudgetId` FK must resolve to that parent's *serverId* before either a CREATE or
     * UPDATE can be dispatched (the backend requires it on create, and the parent must exist
     * server-side at all) — [SyncWorker] pushes yearly budgets before monthly ones for
     * exactly this reason, mirroring channels-before-transactions.
     */
    suspend fun pushMonthlyBudgetEntry(entityId: String): PushResult {
        val outboxDao = database.outboxDao()
        val monthlyBudgetDao = database.monthlyBudgetDao()
        val yearlyBudgetDao = database.yearlyBudgetDao()

        val current = outboxDao.findFor(OutboxEntityType.MONTHLY_BUDGET, entityId) ?: return PushResult.Skipped
        if (current.status != SyncStatus.PENDING) return PushResult.Skipped

        val entity = monthlyBudgetDao.getById(entityId)
        if (entity == null) {
            outboxDao.delete(current.id)
            return PushResult.Skipped
        }
        if (current.operation == OutboxOperation.UPDATE && entity.serverId == null) {
            markMonthlyBudgetPermanentFailure(current, entity, monthlyBudgetDao, outboxDao, "Invariant violation: UPDATE with no serverId")
            return PushResult.PermanentFailure
        }
        if (current.operation == OutboxOperation.DELETE) {
            markMonthlyBudgetPermanentFailure(
                current,
                entity,
                monthlyBudgetDao,
                outboxDao,
                "Unsupported monthly budget outbox operation: DELETE",
            )
            return PushResult.PermanentFailure
        }

        val parentYearlyBudget = entity.yearlyBudgetId?.let { yearlyBudgetDao.getById(it) }
        val parentServerId = parentYearlyBudget?.serverId
        if (parentServerId == null) {
            if (parentYearlyBudget?.syncStatus == SyncStatus.FAILED) {
                // The yearly budget this row depends on permanently failed (4xx, never
                // auto-retried) — it will never gain a serverId on its own, so re-skipping
                // this monthly budget forever would strand it PENDING with no visible signal.
                markMonthlyBudgetPermanentFailure(
                    current,
                    entity,
                    monthlyBudgetDao,
                    outboxDao,
                    "Blocked: yearly budget ${entity.yearlyBudgetId} failed to sync",
                )
                return PushResult.PermanentFailure
            }
            // Parent hasn't synced yet (still PENDING/SYNCING, or not pulled locally yet) —
            // its own outbox entry will populate serverId on a future push. Leave this row
            // PENDING and unclaimed; do not mark SYNCING for a call we're not making.
            return PushResult.Skipped
        }

        val now = System.currentTimeMillis()
        if (outboxDao.claimIfPending(current.id, now) == 0) return PushResult.Skipped
        val dispatchUpdatedAt = entity.updatedAt
        val requestTransactions =
            BudgetRepository.parseTransactions(entity.transactionsJson).map {
                BudgetTransactionRequest(name = it.name, amount = it.amount, type = it.type)
            }

        return try {
            val response =
                when (current.operation) {
                    OutboxOperation.CREATE ->
                        api.createMonthlyBudget(
                            CreateMonthlyBudgetRequest(
                                id = entity.id,
                                yearlyBudgetId = parentServerId,
                                month = entity.month,
                                year = entity.year,
                                transactions = requestTransactions,
                            ),
                        )
                    else ->
                        api.updateMonthlyBudget(entity.serverId!!, UpdateMonthlyBudgetRequest(transactions = requestTransactions))
                }
            when {
                response.isSuccessful ->
                    finishMonthlyBudgetPush(current, monthlyBudgetDao, outboxDao, dispatchUpdatedAt, response.body()!!)
                isPermanentFailureCode(response.code()) -> {
                    markMonthlyBudgetPermanentFailure(
                        current,
                        entity,
                        monthlyBudgetDao,
                        outboxDao,
                        "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                    )
                    PushResult.PermanentFailure
                }
                else -> {
                    markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                    PushResult.TransientFailure
                }
            }
        } catch (e: IOException) {
            markTransientFailure(current, outboxDao, e.message ?: "network error")
            PushResult.TransientFailure
        }
    }

    private suspend fun finishMonthlyBudgetPush(
        current: OutboxEntry,
        monthlyBudgetDao: MonthlyBudgetDao,
        outboxDao: OutboxDao,
        dispatchUpdatedAt: Long,
        responseBody: MonthlyBudgetResponse,
    ): PushResult {
        val now = System.currentTimeMillis()
        val latest = monthlyBudgetDao.getById(current.entityId) ?: return PushResult.Skipped
        return when (
            val decision = PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseBody.id)
        ) {
            is PushCompletionDecision.ClearAndSync -> {
                val resolvedServerId = decision.serverId ?: latest.serverId
                monthlyBudgetDao.update(
                    latest.copy(
                        serverId = resolvedServerId,
                        transactionsJson = Gson().toJson(responseBody.transactions),
                        totalExpenditures = responseBody.totalExpenditures,
                        totalIncome = responseBody.totalIncome,
                        totalSavings = responseBody.totalSavings,
                        totalTransactions = responseBody.totalTransactions,
                        dirty = false,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = now,
                    ),
                )
                outboxDao.delete(current.id)
                PushResult.Success(resolvedServerId)
            }
            is PushCompletionDecision.RequeueDirty -> {
                // See finishYearlyBudgetPush's identical branch: a local edit landed
                // mid-flight, so only serverId is safe to persist here.
                monthlyBudgetDao.update(
                    latest.copy(serverId = decision.serverId ?: latest.serverId, syncStatus = SyncStatus.PENDING, updatedAt = now),
                )
                outboxDao.update(
                    current.copy(
                        operation = decision.nextOperation,
                        status = SyncStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        updatedAt = now,
                    ),
                )
                PushResult.Skipped
            }
        }
    }

    private suspend fun markMonthlyBudgetPermanentFailure(
        current: OutboxEntry,
        entity: MonthlyBudgetEntity,
        monthlyBudgetDao: MonthlyBudgetDao,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        Log.w(TAG, "Monthly budget ${entity.id} push permanently failed: $error")
        monthlyBudgetDao.update(entity.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
        outboxDao.update(current.copy(status = SyncStatus.FAILED, lastError = error, updatedAt = now))
    }

    // ── Processed Messages ──────────────────────────────────────────────────

    /**
     * Write-once, CREATE-only — unlike [pushTransactionEntry]/[pushChannelEntry] there is no
     * local edit path once a [ProcessedMessageEntity] exists, so a success here never needs
     * [PushCompletionResolver]'s "requeue if a newer edit landed mid-flight" branch: it just
     * marks SYNCED and deletes the outbox row directly.
     */
    suspend fun pushProcessedMessageEntry(entityId: String): PushResult {
        val outboxDao = database.outboxDao()
        val processedMessageDao = database.processedMessageDao()

        val current = outboxDao.findFor(OutboxEntityType.PROCESSED_MESSAGE, entityId) ?: return PushResult.Skipped
        if (current.status != SyncStatus.PENDING) return PushResult.Skipped

        val entity = processedMessageDao.getById(entityId)
        if (entity == null) {
            outboxDao.delete(current.id)
            return PushResult.Skipped
        }
        if (current.operation != OutboxOperation.CREATE) {
            // No update/delete flow exists for processed messages anywhere in the app —
            // defensive only, mirroring pushTransactionEntry's "unsupported operation" guard.
            markProcessedMessagePermanentFailure(
                current,
                entity,
                processedMessageDao,
                outboxDao,
                "Unsupported processed message outbox operation: ${current.operation}",
            )
            return PushResult.PermanentFailure
        }

        val now = System.currentTimeMillis()
        if (outboxDao.claimIfPending(current.id, now) == 0) return PushResult.Skipped

        return try {
            val response =
                api.createProcessedMessage(
                    ProcessedMessageRequest(
                        senderId = entity.senderId,
                        content = entity.content,
                        timestamp = entity.timestamp,
                        simInfo = entity.simInfo,
                        receivingSimNumber = entity.receivingSimNumber,
                    ),
                )
            when {
                response.isSuccessful -> {
                    val serverId = response.body()?.id?.ifBlank { null }
                    processedMessageDao.update(
                        entity.copy(serverId = serverId, syncStatus = SyncStatus.SYNCED, updatedAt = System.currentTimeMillis()),
                    )
                    outboxDao.delete(current.id)
                    PushResult.Success(serverId)
                }
                isPermanentFailureCode(response.code()) -> {
                    markProcessedMessagePermanentFailure(
                        current,
                        entity,
                        processedMessageDao,
                        outboxDao,
                        "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                    )
                    PushResult.PermanentFailure
                }
                else -> {
                    markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                    PushResult.TransientFailure
                }
            }
        } catch (e: IOException) {
            markTransientFailure(current, outboxDao, e.message ?: "network error")
            PushResult.TransientFailure
        }
    }

    private suspend fun markProcessedMessagePermanentFailure(
        current: OutboxEntry,
        entity: ProcessedMessageEntity,
        processedMessageDao: ProcessedMessageDao,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        Log.w(TAG, "Processed message ${entity.id} push permanently failed: $error")
        processedMessageDao.update(entity.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
        outboxDao.update(current.copy(status = SyncStatus.FAILED, lastError = error, updatedAt = now))
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    private suspend fun markTransientFailure(
        current: OutboxEntry,
        outboxDao: OutboxDao,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        outboxDao.update(current.copy(status = SyncStatus.PENDING, attempts = current.attempts + 1, lastError = error, updatedAt = now))
    }
}
