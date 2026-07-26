package cc.dlabs.pesamind.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedStateCoordinator
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.LocalBudgetLineItem
import cc.dlabs.pesamind.core.data.MonthlyBudgetRepository
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.data.YearlyBudgetRepository
import cc.dlabs.pesamind.core.data.parseLineItems
import cc.dlabs.pesamind.core.data.toLocalLineItem
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.PushCompletionDecision
import cc.dlabs.pesamind.core.database.PushCompletionResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.dao.ChannelDao
import cc.dlabs.pesamind.core.database.dao.MonthlyBudgetDao
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.dao.YearlyBudgetDao
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import cc.dlabs.pesamind.core.database.migration.resolveUniqueChannelIdsByName
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.models.BudgetTransactionOperation
import cc.dlabs.pesamind.core.network.models.BudgetTransactionRequest
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.CreateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.CreateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UpdateYearlyBudgetRequest
import cc.dlabs.pesamind.core.storage.SyncMetadataManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

private const val TAG = "SyncWorker"

/**
 * Drains the outbox (push), then reconciles a full-list pull against Room (ADR-0004 Slice
 * A2). Not shippable-alone until this commit — Slice A1 wrote entities + outbox rows with
 * nothing to drain them; this is that drain.
 *
 * `@HiltWorker`/`@AssistedInject` constructor injection (not `@Inject lateinit var` field
 * injection) — confirmed in Step 0's spike to be a different Dagger codegen path than the
 * one this project's pinned Dagger 2.51.1 / Kotlin 2.1.0 combination can't parse, and the
 * same reasoning applies to why [cc.dlabs.pesamind.MainActivity]'s `PesaMindApp.Configuration.Provider`
 * uses an `@EntryPoint` for [androidx.hilt.work.HiltWorkerFactory] rather than a field.
 *
 * A fresh instance is constructed by WorkManager for every run, so the two `justSynced*`
 * sets below reset naturally each `doWork()` call — see [applyServerSideDeletions]'s doc
 * comment for why they exist.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val database: PesaMindDatabase,
        private val api: ApiService,
    ) : CoroutineWorker(context, params) {
        private enum class PushOutcome { SUCCESS, PERMANENT_FAILURE, TRANSIENT_FAILURE, SKIPPED }

        private val justSyncedChannelServerIds = mutableSetOf<String>()
        private val justSyncedTransactionServerIds = mutableSetOf<String>()
        private val justSyncedYearlyBudgetServerIds = mutableSetOf<String>()
        private val justSyncedMonthlyBudgetServerIds = mutableSetOf<String>()

        override suspend fun doWork(): Result {
            val pushClean = pushOutbox()
            val pullClean = pullChanges()
            // Published unconditionally, not just on full success: a transient failure only
            // fails specific rows (see PushOutcome.TRANSIENT_FAILURE), so a "partial" run can
            // still have pushed/pulled real changes that stats screens should reflect now
            // rather than waiting for a fully-clean run that may not come for a while.
            UnifiedStateCoordinator.publishEvent(StateEvent.SyncCompleted)
            // 4xx (PERMANENT_FAILURE) is terminal per-row and never retried — it does not
            // fail the worker run. Only a transient (network/5xx) failure asks WorkManager
            // to retry the whole run with backoff; PENDING rows are simply re-attempted then.
            return if (pushClean && pullClean) Result.success() else Result.retry()
        }

        // ── Push: drain the outbox ─────────────────────────────────────────────

        /** @return true if every pending row either succeeded, permanently failed (4xx), or
         * was legitimately skipped — false if any row hit a transient (network/5xx) failure. */
        private suspend fun pushOutbox(): Boolean {
            // Channels before transactions: a transaction create needs its channel's
            // *serverId* to populate `channel_details_id` — that only exists once the
            // channel's own outbox entry has drained. See pushTransactionEntry's skip branch.
            val channelsClean = pushChannelOutbox()
            val transactionsClean = pushTransactionOutbox()
            // Yearly before monthly: a monthly budget's yearlyBudgetId FK needs the yearly
            // row's *serverId* to populate the CREATE/UPDATE request body — same reasoning as
            // channels-before-transactions above (ADR-0006).
            val yearlyBudgetsClean = pushYearlyBudgetOutbox()
            val monthlyBudgetsClean = pushMonthlyBudgetOutbox()
            return channelsClean && transactionsClean && yearlyBudgetsClean && monthlyBudgetsClean
        }

        /** A row a *previous* run's process death left claimed but unresolved is invisible
         * to [OutboxDao.getByStatus] on [SyncStatus.PENDING] forever otherwise — nothing else
         * in this codebase ever reaps a stale `SYNCING` row. Anything still `SYNCING` when a
         * fresh push phase begins can only be such a leftover (this worker's own successful
         * paths always resolve `SYNCING` to `SYNCED`/deleted, `FAILED`, or back to `PENDING`
         * before returning), so it's always safe to reclaim here. */
        private suspend fun reclaimStaleSyncingRows(
            outboxDao: OutboxDao,
            entityType: OutboxEntityType,
        ) {
            val now = System.currentTimeMillis()
            outboxDao.getByStatus(SyncStatus.SYNCING)
                .filter { it.entityType == entityType }
                .forEach {
                    Log.w(TAG, "Reclaiming outbox row ${it.id} left SYNCING by a prior run")
                    outboxDao.update(it.copy(status = SyncStatus.PENDING, updatedAt = now))
                }
        }

        private suspend fun pushChannelOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            val channelDao = database.channelDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.CHANNEL)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.CHANNEL }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                if (pushChannelEntry(entityId, channelDao, outboxDao) == PushOutcome.TRANSIENT_FAILURE) clean = false
            }
            return clean
        }

        private suspend fun pushChannelEntry(
            entityId: String,
            channelDao: ChannelDao,
            outboxDao: OutboxDao,
        ): PushOutcome {
            // Re-read the outbox row fresh right before claiming it — the initial snapshot
            // list this entityId came from can be stale by the time we get here (a repository
            // write may have coalesced it, e.g. UPDATE→DELETE, since that snapshot was taken).
            // Claiming with a stale `operation` would silently push the old op and drop the
            // coalesce. `status != PENDING` means it's already been claimed/resolved.
            val current = outboxDao.findFor(OutboxEntityType.CHANNEL, entityId) ?: return PushOutcome.SKIPPED
            if (current.status != SyncStatus.PENDING) return PushOutcome.SKIPPED

            val entity = channelDao.getById(entityId)
            if (entity == null) {
                // Entity vanished under an outbox row that should have been coalesced away
                // with it (e.g. CREATE+DELETE→HardDeleteNoOutbox already removes both
                // together) — defensive cleanup, not an expected path.
                outboxDao.delete(current.id)
                return PushOutcome.SKIPPED
            }
            if (current.operation != OutboxOperation.CREATE && entity.serverId == null) {
                // UPDATE/DELETE only ever coalesce from a prior UPDATE, which itself only
                // exists once a CREATE has synced — see OutboxCoalescer. A null serverId
                // here means that invariant broke; fail loudly but don't crash the worker.
                markChannelPermanentFailure(
                    current,
                    entity,
                    channelDao,
                    outboxDao,
                    "Invariant violation: ${current.operation} with no serverId",
                )
                return PushOutcome.PERMANENT_FAILURE
            }

            val now = System.currentTimeMillis()
            // Claim the row: any repository write that lands after this point sees
            // status=SYNCING and coalesces via LeaveInFlight, leaving this outbox row alone
            // so the updatedAt comparison below stays meaningful.
            outboxDao.update(current.copy(status = SyncStatus.SYNCING, updatedAt = now))
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
                            response.isSuccessful -> {
                                finishChannelPush(current, channelDao, outboxDao, dispatchUpdatedAt, response.body()?.id?.ifBlank { null })
                                PushOutcome.SUCCESS
                            }
                            response.code() in 400..499 -> {
                                markChannelPermanentFailure(
                                    current,
                                    entity,
                                    channelDao,
                                    outboxDao,
                                    "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                                )
                                PushOutcome.PERMANENT_FAILURE
                            }
                            else -> {
                                markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                                PushOutcome.TRANSIENT_FAILURE
                            }
                        }
                    }
                    OutboxOperation.UPDATE -> {
                        val response =
                            api.updateChannel(
                                entity.serverId!!,
                                UpdateChannelRequest(entity.name, entity.description, entity.status),
                            )
                        when {
                            response.isSuccessful -> {
                                finishChannelPush(current, channelDao, outboxDao, dispatchUpdatedAt, null)
                                PushOutcome.SUCCESS
                            }
                            response.code() in 400..499 -> {
                                markChannelPermanentFailure(
                                    current,
                                    entity,
                                    channelDao,
                                    outboxDao,
                                    "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                                )
                                PushOutcome.PERMANENT_FAILURE
                            }
                            else -> {
                                markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                                PushOutcome.TRANSIENT_FAILURE
                            }
                        }
                    }
                    OutboxOperation.DELETE -> {
                        val response = api.deleteChannel(entity.serverId!!)
                        when {
                            response.isSuccessful -> {
                                // Terminal: the server has confirmed the delete. Hard-delete
                                // now — mandate #3 ("nothing is hard-deleted until the server
                                // confirms") means this IS the confirmation.
                                channelDao.hardDelete(entity.id)
                                outboxDao.delete(current.id)
                                PushOutcome.SUCCESS
                            }
                            response.code() in 400..499 -> {
                                markChannelPermanentFailure(
                                    current,
                                    entity,
                                    channelDao,
                                    outboxDao,
                                    "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                                )
                                PushOutcome.PERMANENT_FAILURE
                            }
                            else -> {
                                markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                                PushOutcome.TRANSIENT_FAILURE
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                markTransientFailure(current, outboxDao, e.message ?: "network error")
                PushOutcome.TRANSIENT_FAILURE
            }
        }

        private suspend fun finishChannelPush(
            current: OutboxEntry,
            channelDao: ChannelDao,
            outboxDao: OutboxDao,
            dispatchUpdatedAt: Long,
            responseServerId: String?,
        ) {
            val now = System.currentTimeMillis()
            val latest = channelDao.getById(current.entityId) ?: return
            when (val decision = PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseServerId)) {
                is PushCompletionDecision.ClearAndSync -> {
                    val resolvedServerId = decision.serverId ?: latest.serverId
                    channelDao.update(
                        latest.copy(serverId = resolvedServerId, dirty = false, syncStatus = SyncStatus.SYNCED, updatedAt = now),
                    )
                    outboxDao.delete(current.id)
                    // This run's own pull must not treat this row's absence from a (possibly
                    // replication-lagged) getChannels() response as a server-side delete.
                    resolvedServerId?.let { justSyncedChannelServerIds.add(it) }
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

        private suspend fun pushTransactionOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            val transactionDao = database.transactionDao()
            val channelDao = database.channelDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.TRANSACTION)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.TRANSACTION }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                if (pushTransactionEntry(entityId, transactionDao, channelDao, outboxDao) == PushOutcome.TRANSIENT_FAILURE) clean = false
            }
            return clean
        }

        private suspend fun pushTransactionEntry(
            entityId: String,
            transactionDao: TransactionDao,
            channelDao: ChannelDao,
            outboxDao: OutboxDao,
        ): PushOutcome {
            // See pushChannelEntry's comment on re-reading fresh instead of trusting the
            // initial snapshot's operation.
            val current = outboxDao.findFor(OutboxEntityType.TRANSACTION, entityId) ?: return PushOutcome.SKIPPED
            if (current.status != SyncStatus.PENDING) return PushOutcome.SKIPPED

            val entity = transactionDao.getById(entityId)
            if (entity == null) {
                outboxDao.delete(current.id)
                return PushOutcome.SKIPPED
            }
            if (current.operation != OutboxOperation.CREATE) {
                // No update/delete transaction endpoint exists yet (ApiService has none, and
                // no repository call site creates such an outbox row today) — defensive only.
                markTransactionPermanentFailure(
                    current,
                    entity,
                    transactionDao,
                    outboxDao,
                    "Unsupported transaction outbox operation: ${current.operation}",
                )
                return PushOutcome.PERMANENT_FAILURE
            }

            val channelEntity = entity.channelId?.let { channelDao.getById(it) }
            if (entity.channelId != null && channelEntity?.serverId == null) {
                if (channelEntity?.syncStatus == SyncStatus.FAILED) {
                    // The channel this transaction depends on permanently failed (4xx, never
                    // auto-retried) — it will never gain a serverId on its own, so re-skipping
                    // this transaction forever would strand it PENDING with no visible signal.
                    // Surface the same terminal state instead of silently retrying indefinitely.
                    markTransactionPermanentFailure(
                        current,
                        entity,
                        transactionDao,
                        outboxDao,
                        "Blocked: channel ${entity.channelId} failed to sync",
                    )
                    return PushOutcome.PERMANENT_FAILURE
                }
                // Channel hasn't synced yet (still PENDING/SYNCING) — its own outbox entry
                // (drained above) will populate serverId on a future run. Leave this row
                // PENDING and unclaimed; do not mark SYNCING for a call we're not making.
                return PushOutcome.SKIPPED
            }
            val channelServerId = channelEntity?.serverId

            val now = System.currentTimeMillis()
            outboxDao.update(current.copy(status = SyncStatus.SYNCING, updatedAt = now))
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
                    response.isSuccessful -> {
                        finishTransactionPush(current, transactionDao, outboxDao, dispatchUpdatedAt, response.body()?.id?.ifBlank { null })
                        PushOutcome.SUCCESS
                    }
                    response.code() in 400..499 -> {
                        markTransactionPermanentFailure(
                            current,
                            entity,
                            transactionDao,
                            outboxDao,
                            "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                        )
                        PushOutcome.PERMANENT_FAILURE
                    }
                    else -> {
                        markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                        PushOutcome.TRANSIENT_FAILURE
                    }
                }
            } catch (e: IOException) {
                markTransientFailure(current, outboxDao, e.message ?: "network error")
                PushOutcome.TRANSIENT_FAILURE
            }
        }

        private suspend fun finishTransactionPush(
            current: OutboxEntry,
            transactionDao: TransactionDao,
            outboxDao: OutboxDao,
            dispatchUpdatedAt: Long,
            responseServerId: String?,
        ) {
            val now = System.currentTimeMillis()
            val latest = transactionDao.getById(current.entityId) ?: return
            when (val decision = PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseServerId)) {
                is PushCompletionDecision.ClearAndSync -> {
                    val resolvedServerId = decision.serverId ?: latest.serverId
                    transactionDao.update(
                        latest.copy(serverId = resolvedServerId, dirty = false, syncStatus = SyncStatus.SYNCED, updatedAt = now),
                    )
                    outboxDao.delete(current.id)
                    resolvedServerId?.let { justSyncedTransactionServerIds.add(it) }
                }
                is PushCompletionDecision.RequeueDirty -> {
                    // No update endpoint exists for transactions; a mid-flight edit isn't
                    // reachable from any UI today (TransactionViewModel exposes no edit call),
                    // but honor the same requeue contract as Channel for when it eventually is.
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

        private suspend fun markTransientFailure(
            current: OutboxEntry,
            outboxDao: OutboxDao,
            error: String,
        ) {
            val now = System.currentTimeMillis()
            outboxDao.update(current.copy(status = SyncStatus.PENDING, attempts = current.attempts + 1, lastError = error, updatedAt = now))
        }

        // ── Pull: full-list fetch + client-side diff ───────────────────────────
        //
        // ApiService's GET endpoints take zero query parameters (confirmed by reading the
        // interface) — there is no server-supplied delta cursor to fetch against, so this
        // is a full-list pull, exactly as ADR-0004's original "Sync pull" decision predicted.
        // Deliberately not fabricating a client-clock-based delta filter in its absence.

        /** @return true if both entity types' pulls completed fully; false on any network
         * failure (worker retries the whole run — a partial pull is never left half-applied
         * across entity types, though each individual row's reconciliation is still atomic
         * via its own repository `withTransaction` call). */
        private suspend fun pullChanges(): Boolean =
            try {
                pullChannels()
                pullTransactions()
                // Yearly before monthly — pullMonthlyBudgets resolves each server row's
                // yearlyBudgetId against the yearly table already-pulled by this same run.
                pullYearlyBudgets()
                pullMonthlyBudgets()
                SyncMetadataManager.setLastFullPullAt(System.currentTimeMillis())
                true
            } catch (e: IOException) {
                Log.w(TAG, "Pull phase failed, will retry next run", e)
                false
            }

        private suspend fun pullChannels() {
            val response = api.getChannels()
            if (!response.isSuccessful) throw IOException("getChannels failed: HTTP ${response.code()}")
            val remote = response.body().orEmpty()
            val remoteServerIds = remote.mapNotNull { it.id.ifBlank { null } }.toSet()

            for (details in remote) {
                ChannelRepository.reconcileFromServer(details)
            }
            applyServerSideDeletions(remoteServerIds)
        }

        /**
         * A row absent from this pull's response is only treated as a server-side deletion
         * if it's non-dirty AND wasn't pushed successfully by *this same run* — this run's
         * own `getChannels()` call can otherwise race a replication-lagged backend that
         * hasn't yet observed the create/update this run's push phase just confirmed with a
         * 2xx, which would soft-delete a row the user just successfully synced, and — since
         * `ReconcileResolver` refuses to touch a tombstoned row — never un-delete it even once
         * the backend catches up. See [justSyncedChannelServerIds].
         */
        private suspend fun applyServerSideDeletions(remoteServerIds: Set<String>) {
            val channelDao = database.channelDao()
            val now = System.currentTimeMillis()
            channelDao.getAllIncludingDeleted()
                .filter {
                    it.serverId != null && it.deletedAt == null && !it.dirty &&
                        it.serverId !in remoteServerIds && it.serverId !in justSyncedChannelServerIds
                }
                .forEach { channelDao.update(it.copy(deletedAt = now, syncStatus = SyncStatus.SYNCED, updatedAt = now)) }
        }

        private suspend fun pullTransactions() {
            val response = api.getTransactions()
            if (!response.isSuccessful) throw IOException("getTransactions failed: HTTP ${response.code()}")
            val remote = response.body().orEmpty()
            val remoteServerIds = remote.mapNotNull { it.id.ifBlank { null } }.toSet()

            // TransactionDetails only ever carries a channel *name* (never an id) — same
            // best-effort, unique-name-only matching PrefsToRoomMigrator already established.
            val channelIdByUniqueName = resolveUniqueChannelIdsByName(database.channelDao().getAllActive())
            for (details in remote) {
                TransactionRepository.reconcileFromServer(details, channelIdByUniqueName[details.channelDetailsName])
            }

            val transactionDao = database.transactionDao()
            val now = System.currentTimeMillis()
            // See applyServerSideDeletions's doc comment — same just-synced-this-run guard.
            transactionDao.getAllIncludingDeleted()
                .filter {
                    it.serverId != null && it.deletedAt == null && !it.dirty &&
                        it.serverId !in remoteServerIds && it.serverId !in justSyncedTransactionServerIds
                }
                .forEach { transactionDao.update(it.copy(deletedAt = now, syncStatus = SyncStatus.SYNCED, updatedAt = now)) }
        }

        // ── Push: yearly & monthly budgets (ADR-0006) ──────────────────────────

        private suspend fun pushYearlyBudgetOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            val yearlyBudgetDao = database.yearlyBudgetDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.YEARLY_BUDGET)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.YEARLY_BUDGET }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                if (pushYearlyBudgetEntry(entityId, yearlyBudgetDao, outboxDao) == PushOutcome.TRANSIENT_FAILURE) clean = false
            }
            return clean
        }

        private suspend fun pushYearlyBudgetEntry(
            entityId: String,
            yearlyBudgetDao: YearlyBudgetDao,
            outboxDao: OutboxDao,
        ): PushOutcome {
            val current = outboxDao.findFor(OutboxEntityType.YEARLY_BUDGET, entityId) ?: return PushOutcome.SKIPPED
            if (current.status != SyncStatus.PENDING) return PushOutcome.SKIPPED

            val entity = yearlyBudgetDao.getById(entityId)
            if (entity == null) {
                outboxDao.delete(current.id)
                return PushOutcome.SKIPPED
            }
            if (current.operation != OutboxOperation.CREATE && entity.serverId == null) {
                markYearlyBudgetPermanentFailure(
                    current,
                    entity,
                    yearlyBudgetDao,
                    outboxDao,
                    "Invariant violation: ${current.operation} with no serverId",
                )
                return PushOutcome.PERMANENT_FAILURE
            }
            if (current.operation != OutboxOperation.CREATE && current.operation != OutboxOperation.UPDATE) {
                // No whole-budget delete is ever driven from the UI today — defensive only,
                // same status as pushTransactionEntry's non-CREATE branch.
                markYearlyBudgetPermanentFailure(
                    current,
                    entity,
                    yearlyBudgetDao,
                    outboxDao,
                    "Unsupported budget outbox operation: ${current.operation}",
                )
                return PushOutcome.PERMANENT_FAILURE
            }

            val now = System.currentTimeMillis()
            outboxDao.update(current.copy(status = SyncStatus.SYNCING, updatedAt = now))
            val dispatchUpdatedAt = entity.updatedAt
            val items = parseLineItems(entity.transactionsJson)
            val dispatchedItems = items.filter { it.pendingAction != null }

            return try {
                val response =
                    when (current.operation) {
                        OutboxOperation.CREATE ->
                            api.createYearlyBudget(
                                CreateYearlyBudgetRequest(year = entity.year, transactions = items.map { it.toBudgetTransactionRequest() }),
                            )
                        OutboxOperation.UPDATE ->
                            api.updateYearlyBudget(
                                entity.serverId!!,
                                UpdateYearlyBudgetRequest(
                                    transactionOps = items.filter { it.pendingAction != null }.map { it.toOperation() },
                                ),
                            )
                        OutboxOperation.DELETE -> error("Unreachable: filtered above")
                    }
                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body == null) {
                            markTransientFailure(current, outboxDao, "Empty response body")
                            PushOutcome.TRANSIENT_FAILURE
                        } else {
                            finishYearlyBudgetPush(current, yearlyBudgetDao, outboxDao, dispatchUpdatedAt, dispatchedItems, body)
                            PushOutcome.SUCCESS
                        }
                    }
                    response.code() in 400..499 -> {
                        markYearlyBudgetPermanentFailure(
                            current,
                            entity,
                            yearlyBudgetDao,
                            outboxDao,
                            "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                        )
                        PushOutcome.PERMANENT_FAILURE
                    }
                    else -> {
                        markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                        PushOutcome.TRANSIENT_FAILURE
                    }
                }
            } catch (e: IOException) {
                markTransientFailure(current, outboxDao, e.message ?: "network error")
                PushOutcome.TRANSIENT_FAILURE
            }
        }

        private suspend fun finishYearlyBudgetPush(
            current: OutboxEntry,
            yearlyBudgetDao: YearlyBudgetDao,
            outboxDao: OutboxDao,
            dispatchUpdatedAt: Long,
            dispatchedItems: List<LocalBudgetLineItem>,
            responseBody: cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse,
        ) {
            val now = System.currentTimeMillis()
            val latest = yearlyBudgetDao.getById(current.entityId) ?: return
            val responseItems = responseBody.transactions.map { it.toLocalLineItem() }
            when (
                val decision =
                    PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseBody.id.ifBlank { null })
            ) {
                is PushCompletionDecision.ClearAndSync -> {
                    val resolvedServerId = decision.serverId ?: latest.serverId
                    YearlyBudgetRepository.applyPushCompletion(
                        latest.id,
                        resolvedServerId,
                        dispatchedItems,
                        responseItems,
                        clearDirty = true,
                    )
                    outboxDao.delete(current.id)
                    resolvedServerId?.let { justSyncedYearlyBudgetServerIds.add(it) }
                }
                is PushCompletionDecision.RequeueDirty -> {
                    YearlyBudgetRepository.applyPushCompletion(
                        latest.id,
                        decision.serverId ?: latest.serverId,
                        dispatchedItems,
                        responseItems,
                        clearDirty = false,
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

        private suspend fun pushMonthlyBudgetOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            val monthlyBudgetDao = database.monthlyBudgetDao()
            val yearlyBudgetDao = database.yearlyBudgetDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.MONTHLY_BUDGET)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.MONTHLY_BUDGET }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                val outcome = pushMonthlyBudgetEntry(entityId, monthlyBudgetDao, yearlyBudgetDao, outboxDao)
                if (outcome == PushOutcome.TRANSIENT_FAILURE) clean = false
            }
            return clean
        }

        private suspend fun pushMonthlyBudgetEntry(
            entityId: String,
            monthlyBudgetDao: MonthlyBudgetDao,
            yearlyBudgetDao: YearlyBudgetDao,
            outboxDao: OutboxDao,
        ): PushOutcome {
            val current = outboxDao.findFor(OutboxEntityType.MONTHLY_BUDGET, entityId) ?: return PushOutcome.SKIPPED
            if (current.status != SyncStatus.PENDING) return PushOutcome.SKIPPED

            val entity = monthlyBudgetDao.getById(entityId)
            if (entity == null) {
                outboxDao.delete(current.id)
                return PushOutcome.SKIPPED
            }
            if (current.operation != OutboxOperation.CREATE && entity.serverId == null) {
                markMonthlyBudgetPermanentFailure(
                    current,
                    entity,
                    monthlyBudgetDao,
                    outboxDao,
                    "Invariant violation: ${current.operation} with no serverId",
                )
                return PushOutcome.PERMANENT_FAILURE
            }
            if (current.operation != OutboxOperation.CREATE && current.operation != OutboxOperation.UPDATE) {
                markMonthlyBudgetPermanentFailure(
                    current,
                    entity,
                    monthlyBudgetDao,
                    outboxDao,
                    "Unsupported budget outbox operation: ${current.operation}",
                )
                return PushOutcome.PERMANENT_FAILURE
            }

            // Channel-before-transaction's exact FK-not-ready-yet shape: a CREATE needs the
            // yearly parent's *serverId*, which only exists once that row's own outbox entry
            // (drained earlier this run by pushYearlyBudgetOutbox) has synced.
            val yearlyEntity = entity.yearlyBudgetId?.let { yearlyBudgetDao.getById(it) }
            if (current.operation == OutboxOperation.CREATE && entity.yearlyBudgetId != null && yearlyEntity?.serverId == null) {
                if (yearlyEntity?.syncStatus == SyncStatus.FAILED) {
                    markMonthlyBudgetPermanentFailure(
                        current,
                        entity,
                        monthlyBudgetDao,
                        outboxDao,
                        "Blocked: yearly budget ${entity.yearlyBudgetId} failed to sync",
                    )
                    return PushOutcome.PERMANENT_FAILURE
                }
                return PushOutcome.SKIPPED
            }
            val yearlyServerId = yearlyEntity?.serverId

            val now = System.currentTimeMillis()
            outboxDao.update(current.copy(status = SyncStatus.SYNCING, updatedAt = now))
            val dispatchUpdatedAt = entity.updatedAt
            val items = parseLineItems(entity.transactionsJson)
            val dispatchedItems = items.filter { it.pendingAction != null }

            return try {
                val response =
                    when (current.operation) {
                        OutboxOperation.CREATE ->
                            api.createMonthlyBudget(
                                CreateMonthlyBudgetRequest(
                                    yearlyBudgetId = yearlyServerId.orEmpty(),
                                    month = entity.month,
                                    year = entity.year,
                                    transactions = items.map { it.toBudgetTransactionRequest() },
                                ),
                            )
                        OutboxOperation.UPDATE ->
                            api.updateMonthlyBudget(
                                entity.serverId!!,
                                UpdateMonthlyBudgetRequest(
                                    transactionOps = items.filter { it.pendingAction != null }.map { it.toOperation() },
                                ),
                            )
                        OutboxOperation.DELETE -> error("Unreachable: filtered above")
                    }
                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body == null) {
                            markTransientFailure(current, outboxDao, "Empty response body")
                            PushOutcome.TRANSIENT_FAILURE
                        } else {
                            finishMonthlyBudgetPush(current, monthlyBudgetDao, outboxDao, dispatchUpdatedAt, dispatchedItems, body)
                            PushOutcome.SUCCESS
                        }
                    }
                    response.code() in 400..499 -> {
                        markMonthlyBudgetPermanentFailure(
                            current,
                            entity,
                            monthlyBudgetDao,
                            outboxDao,
                            "HTTP ${response.code()}: ${response.errorBody()?.string()}",
                        )
                        PushOutcome.PERMANENT_FAILURE
                    }
                    else -> {
                        markTransientFailure(current, outboxDao, "HTTP ${response.code()}")
                        PushOutcome.TRANSIENT_FAILURE
                    }
                }
            } catch (e: IOException) {
                markTransientFailure(current, outboxDao, e.message ?: "network error")
                PushOutcome.TRANSIENT_FAILURE
            }
        }

        private suspend fun finishMonthlyBudgetPush(
            current: OutboxEntry,
            monthlyBudgetDao: MonthlyBudgetDao,
            outboxDao: OutboxDao,
            dispatchUpdatedAt: Long,
            dispatchedItems: List<LocalBudgetLineItem>,
            responseBody: cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse,
        ) {
            val now = System.currentTimeMillis()
            val latest = monthlyBudgetDao.getById(current.entityId) ?: return
            val responseItems = responseBody.transactions.map { it.toLocalLineItem() }
            when (
                val decision =
                    PushCompletionResolver.resolve(current.operation, dispatchUpdatedAt, latest.updatedAt, responseBody.id.ifBlank { null })
            ) {
                is PushCompletionDecision.ClearAndSync -> {
                    val resolvedServerId = decision.serverId ?: latest.serverId
                    MonthlyBudgetRepository.applyPushCompletion(
                        latest.id,
                        resolvedServerId,
                        dispatchedItems,
                        responseItems,
                        clearDirty = true,
                    )
                    outboxDao.delete(current.id)
                    resolvedServerId?.let { justSyncedMonthlyBudgetServerIds.add(it) }
                }
                is PushCompletionDecision.RequeueDirty -> {
                    MonthlyBudgetRepository.applyPushCompletion(
                        latest.id,
                        decision.serverId ?: latest.serverId,
                        dispatchedItems,
                        responseItems,
                        clearDirty = false,
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

        // ── Pull: yearly & monthly budgets (ADR-0006) ──────────────────────────

        private suspend fun pullYearlyBudgets() {
            val response = api.getYearlyBudgets()
            if (!response.isSuccessful) throw IOException("getYearlyBudgets failed: HTTP ${response.code()}")
            val remote = response.body().orEmpty()
            val remoteServerIds = remote.mapNotNull { it.id.ifBlank { null } }.toSet()
            for (details in remote) {
                YearlyBudgetRepository.reconcileFromServer(details)
            }
            applyYearlyBudgetServerSideDeletions(remoteServerIds)
        }

        /** See applyServerSideDeletions's doc comment — same just-synced-this-run guard. */
        private suspend fun applyYearlyBudgetServerSideDeletions(remoteServerIds: Set<String>) {
            val yearlyBudgetDao = database.yearlyBudgetDao()
            val now = System.currentTimeMillis()
            yearlyBudgetDao.getAllIncludingDeleted()
                .filter {
                    it.serverId != null && it.deletedAt == null && !it.dirty &&
                        it.serverId !in remoteServerIds && it.serverId !in justSyncedYearlyBudgetServerIds
                }
                .forEach { yearlyBudgetDao.update(it.copy(deletedAt = now, syncStatus = SyncStatus.SYNCED, updatedAt = now)) }
        }

        private suspend fun pullMonthlyBudgets() {
            val response = api.getMonthlyBudgets()
            if (!response.isSuccessful) throw IOException("getMonthlyBudgets failed: HTTP ${response.code()}")
            val remote = response.body().orEmpty()
            val remoteServerIds = remote.mapNotNull { it.id.ifBlank { null } }.toSet()

            // MonthlyBudgetResponse.yearlyBudgetId is the server's yearly-budget id — resolve
            // it against the yearly table this same run's pullYearlyBudgets already refreshed,
            // same shape as pullTransactions' channelIdByUniqueName lookup.
            val yearlyLocalIdByServerId =
                database.yearlyBudgetDao().getAllIncludingDeleted()
                    .filter { it.deletedAt == null && it.serverId != null }
                    .associate { it.serverId!! to it.id }

            for (details in remote) {
                MonthlyBudgetRepository.reconcileFromServer(details, yearlyLocalIdByServerId[details.yearlyBudgetId])
            }

            val monthlyBudgetDao = database.monthlyBudgetDao()
            val now = System.currentTimeMillis()
            monthlyBudgetDao.getAllIncludingDeleted()
                .filter {
                    it.serverId != null && it.deletedAt == null && !it.dirty &&
                        it.serverId !in remoteServerIds && it.serverId !in justSyncedMonthlyBudgetServerIds
                }
                .forEach { monthlyBudgetDao.update(it.copy(deletedAt = now, syncStatus = SyncStatus.SYNCED, updatedAt = now)) }
        }
    }

private fun LocalBudgetLineItem.toBudgetTransactionRequest() = BudgetTransactionRequest(name = name, amount = amount, type = type)

private fun LocalBudgetLineItem.toOperation() =
    BudgetTransactionOperation(
        id = serverId.orEmpty(),
        name = name,
        amount = amount,
        type = type,
        action = pendingAction ?: "update",
    )
