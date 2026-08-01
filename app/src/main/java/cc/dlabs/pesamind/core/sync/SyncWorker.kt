package cc.dlabs.pesamind.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedStateCoordinator
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.migration.resolveUniqueChannelIdsByName
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.storage.SyncMetadataManager
import cc.dlabs.pesamind.core.storage.TokenManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

private const val TAG = "SyncWorker"

/**
 * Drains the outbox (push), then reconciles a full-list pull against Room (ADR-0004 Slice
 * A2). Not shippable-alone until this commit — Slice A1 wrote entities + outbox rows with
 * nothing to drain them; this is that drain.
 *
 * The per-row push mechanics (claim, dispatch, resolve, retry bookkeeping) live in
 * [OutboxPusher], shared with the eager push [ChannelRepository]/[TransactionRepository] trigger
 * the instant a row is created locally while already online — this worker's job is draining
 * *everything still PENDING* (the periodic/connectivity-regain safety net for whatever the eager
 * path didn't catch), plus the pull phase below, which stays worker-only.
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
        private val outboxPusher = OutboxPusher(database, api)

        private val justSyncedChannelServerIds = mutableSetOf<String>()
        private val justSyncedTransactionServerIds = mutableSetOf<String>()
        private val justSyncedYearlyBudgetServerIds = mutableSetOf<String>()
        private val justSyncedMonthlyBudgetServerIds = mutableSetOf<String>()

        override suspend fun doWork(): Result {
            // Early auth check: if user is not logged in, fail gracefully
            // Don't attempt sync without valid credentials — prevents 401 cascade crashes
            if (!TokenManager.isLoggedIn()) {
                Log.w(TAG, "User is not logged in, skipping sync. Worker will retry once re-authenticated.")
                return Result.failure()
            }

            val pushClean = pushOutbox()
            val pullClean = pullChanges()
            // Published unconditionally, not just on full success: a transient failure only
            // fails specific rows, so a "partial" run can still have pushed/pulled real changes
            // that stats screens should reflect now rather than waiting for a fully-clean run
            // that may not come for a while.
            UnifiedStateCoordinator.publishEvent(StateEvent.SyncCompleted)
            // 4xx (PermanentFailure) is terminal per-row and never retried — it does not fail
            // the worker run. Only a transient (network/5xx) failure asks WorkManager to retry
            // the whole run with backoff; PENDING rows are simply re-attempted then.
            return if (pushClean && pullClean) Result.success() else Result.retry()
        }

        // ── Push: drain the outbox ─────────────────────────────────────────────

        /** @return true if every pending row either succeeded, permanently failed (4xx), or
         * was legitimately skipped — false if any row hit a transient (network/5xx) failure. */
        private suspend fun pushOutbox(): Boolean {
            resurrectFailedRows()
            // Channels before transactions: a transaction create needs its channel's
            // *serverId* to populate `channel_details_id` — that only exists once the
            // channel's own outbox entry has drained. See OutboxPusher.pushTransactionEntry's
            // skip branch. Same reasoning for yearly budgets before monthly budgets — see
            // OutboxPusher.pushMonthlyBudgetEntry's skip branch.
            val channelsClean = pushChannelOutbox()
            val transactionsClean = pushTransactionOutbox()
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

        /**
         * A FAILED outbox row has no other path back to PENDING — [reclaimStaleSyncingRows]
         * only rescues SYNCING (mid-push process death), not FAILED (a completed, terminal
         * decision). [SyncScheduler.triggerSyncNow] fires this worker the instant connectivity
         * comes back, so that's the natural moment to give every FAILED row one more shot: the
         * condition that failed it (an expired token, a claim race — see
         * [cc.dlabs.pesamind.core.database.dao.OutboxDao.claimIfPending]'s doc comment) may no
         * longer hold. A row that's FAILED for a genuinely permanent reason (bad data, an
         * unsupported operation) just fails again on the next attempt — this only runs once per
         * [SyncWorker] invocation, not a tight loop, so that's a wasted request, not runaway
         * retries.
         */
        private suspend fun resurrectFailedRows() {
            val count = database.outboxDao().resetFailedToPending(System.currentTimeMillis())
            if (count > 0) Log.i(TAG, "Resurrected $count FAILED outbox row(s) for retry")
        }

        private suspend fun pushChannelOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.CHANNEL)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.CHANNEL }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                when (val result = outboxPusher.pushChannelEntry(entityId)) {
                    is OutboxPusher.PushResult.Success -> result.serverId?.let { justSyncedChannelServerIds.add(it) }
                    OutboxPusher.PushResult.TransientFailure -> clean = false
                    OutboxPusher.PushResult.PermanentFailure, OutboxPusher.PushResult.Skipped -> Unit
                }
            }
            return clean
        }

        private suspend fun pushTransactionOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.TRANSACTION)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.TRANSACTION }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                when (val result = outboxPusher.pushTransactionEntry(entityId)) {
                    is OutboxPusher.PushResult.Success -> result.serverId?.let { justSyncedTransactionServerIds.add(it) }
                    OutboxPusher.PushResult.TransientFailure -> clean = false
                    OutboxPusher.PushResult.PermanentFailure, OutboxPusher.PushResult.Skipped -> Unit
                }
            }
            return clean
        }

        private suspend fun pushYearlyBudgetOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.YEARLY_BUDGET)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.YEARLY_BUDGET }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                when (val result = outboxPusher.pushYearlyBudgetEntry(entityId)) {
                    is OutboxPusher.PushResult.Success -> result.serverId?.let { justSyncedYearlyBudgetServerIds.add(it) }
                    OutboxPusher.PushResult.TransientFailure -> clean = false
                    OutboxPusher.PushResult.PermanentFailure, OutboxPusher.PushResult.Skipped -> Unit
                }
            }
            return clean
        }

        private suspend fun pushMonthlyBudgetOutbox(): Boolean {
            val outboxDao = database.outboxDao()
            reclaimStaleSyncingRows(outboxDao, OutboxEntityType.MONTHLY_BUDGET)
            val pendingEntityIds =
                outboxDao.getByStatus(SyncStatus.PENDING).filter { it.entityType == OutboxEntityType.MONTHLY_BUDGET }.map { it.entityId }
            var clean = true
            for (entityId in pendingEntityIds) {
                when (val result = outboxPusher.pushMonthlyBudgetEntry(entityId)) {
                    is OutboxPusher.PushResult.Success -> result.serverId?.let { justSyncedMonthlyBudgetServerIds.add(it) }
                    OutboxPusher.PushResult.TransientFailure -> clean = false
                    OutboxPusher.PushResult.PermanentFailure, OutboxPusher.PushResult.Skipped -> Unit
                }
            }
            return clean
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
                // Yearly before monthly: reconcileMonthlyFromServer resolves each pulled
                // monthly budget's yearly_budget_id (a server id) back to a local yearly
                // budget row, which must exist locally first.
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

        private suspend fun pullYearlyBudgets() {
            val response = api.getYearlyBudgets()
            if (!response.isSuccessful) throw IOException("getYearlyBudgets failed: HTTP ${response.code()}")
            val remote = response.body().orEmpty()
            val remoteServerIds = remote.mapNotNull { it.id.ifBlank { null } }.toSet()

            for (details in remote) {
                BudgetRepository.reconcileFromServer(details)
            }

            val yearlyBudgetDao = database.yearlyBudgetDao()
            val now = System.currentTimeMillis()
            // See applyServerSideDeletions's doc comment — same just-synced-this-run guard.
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

            for (details in remote) {
                // Skipped (null) when the parent yearly budget hasn't been pulled locally
                // yet — see BudgetRepository.reconcileMonthlyFromServer's doc comment. A
                // later run picks it up once pullYearlyBudgets has inserted the parent.
                BudgetRepository.reconcileMonthlyFromServer(details)
            }

            val monthlyBudgetDao = database.monthlyBudgetDao()
            val now = System.currentTimeMillis()
            // See applyServerSideDeletions's doc comment — same just-synced-this-run guard.
            monthlyBudgetDao.getAllIncludingDeleted()
                .filter {
                    it.serverId != null && it.deletedAt == null && !it.dirty &&
                        it.serverId !in remoteServerIds && it.serverId !in justSyncedMonthlyBudgetServerIds
                }
                .forEach { monthlyBudgetDao.update(it.copy(deletedAt = now, syncStatus = SyncStatus.SYNCED, updatedAt = now)) }
        }
    }
