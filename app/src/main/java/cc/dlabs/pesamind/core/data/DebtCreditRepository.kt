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
import cc.dlabs.pesamind.core.database.entity.DebtCreditEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.DebtCreditResponse
import cc.dlabs.pesamind.core.network.models.LinkTransactionRequest
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.OutboxPusher
import cc.dlabs.pesamind.core.utils.PhoneNumberNormalizer
import cc.dlabs.pesamind.core.utils.ReminderScheduler
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Room-backed source of truth for "Lent & Borrowed" debts (Phase 2) — mirrors
 * [ChannelRepository]'s shape and DI pattern exactly. Premium-gated: this feature is
 * Premium-only end to end (backend and here), so every write checks [AccountManager.isPremium]
 * live rather than trusting cached UI state, matching [ChannelRepository.createChannel]'s
 * free-tier check.
 */
object DebtCreditRepository {
    private const val TAG = "DebtCreditRepository"

    internal lateinit var database: PesaMindDatabase
    private val debtCreditDao get() = database.debtCreditDao()
    private val transactionDao get() = database.transactionDao()
    private val outboxDao get() = database.outboxDao()

    private var networkMonitor: NetworkMonitor? = null
    private var outboxPusher: OutboxPusher? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
        networkMonitor = NetworkMonitor(context.applicationContext)
        outboxPusher = OutboxPusher(database, ApiClient.api)
    }

    private fun pushEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushDebtCreditEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for debt/credit $entityId; will retry on next sync", e)
            }
        }
    }

    fun observeAll(): Flow<List<DebtCreditResponse>> =
        flow { emitAll(debtCreditDao.observeAll(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    fun observeActive(): Flow<List<DebtCreditResponse>> =
        flow { emitAll(debtCreditDao.observeActive(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    fun observeSettled(): Flow<List<DebtCreditResponse>> =
        flow { emitAll(debtCreditDao.observeSettled(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    suspend fun getById(id: String): DebtCreditResponse? = debtCreditDao.getById(id)?.toDetails()

    /**
     * Premium-gated at the repository, not just hidden in the UI — a lapsed trial mid-session
     * can't create a new debt via stale UI state, mirroring [ChannelRepository.createChannel]'s
     * free-tier gate. Schedules reminders immediately after the local commit (see
     * [ReminderScheduler]) so they exist even before the first sync completes.
     */
    suspend fun createDebtCredit(
        direction: String,
        counterpartyName: String,
        counterpartyPhone: String?,
        principalAmount: Double,
        dueAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ): DebtCreditResponse? {
        if (!AccountManager.isPremium()) return null
        val userId = currentUserId()
        val now = System.currentTimeMillis()
        val entity =
            DebtCreditEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                userId = userId,
                direction = direction,
                counterpartyName = counterpartyName,
                counterpartyPhone = counterpartyPhone,
                counterpartyPhoneNormalized = PhoneNumberNormalizer.normalize(counterpartyPhone),
                originalAmount = principalAmount,
                outstanding = principalAmount,
                note = note,
                dueAt = dueAtMillis,
                reminderOffsetsCsv = DebtCreditEntity.encode(reminderOffsets),
                settledAt = null,
                syncStatus = SyncStatus.PENDING,
                dirty = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        database.withTransaction {
            debtCreditDao.upsert(entity)
            outboxDao.upsert(newOutboxEntry(OutboxEntityType.DEBT_CREDIT, entity.id, OutboxOperation.CREATE, now))
        }
        pushEagerly(entity.id)
        scheduleReminders(entity)
        return entity.toDetails()
    }

    suspend fun updateDebtCredit(
        id: String,
        counterpartyName: String,
        counterpartyPhone: String?,
        dueAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ): DebtCreditResponse? {
        if (!AccountManager.isPremium()) return null
        val updated =
            database.withTransaction {
                val existing = debtCreditDao.getById(id) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                val updated =
                    existing.copy(
                        counterpartyName = counterpartyName,
                        counterpartyPhone = counterpartyPhone,
                        counterpartyPhoneNormalized = PhoneNumberNormalizer.normalize(counterpartyPhone),
                        dueAt = dueAtMillis,
                        note = note,
                        reminderOffsetsCsv = DebtCreditEntity.encode(reminderOffsets),
                        dirty = true,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = now,
                    )
                debtCreditDao.update(updated)
                enqueueOutbox(OutboxEntityType.DEBT_CREDIT, id, OutboxOperation.UPDATE, now)
                updated
            }
        if (updated != null) {
            pushEagerly(id)
            // Due date or offsets may have changed — reschedule unconditionally; scheduleReminders
            // internally cancels-then-re-adds, so this is safe even when nothing relevant changed.
            scheduleReminders(updated)
        }
        return updated?.toDetails()
    }

    suspend fun deleteDebtCredit(id: String): Boolean {
        var shouldPush = false
        val result =
            database.withTransaction {
                val existing = debtCreditDao.getById(id) ?: return@withTransaction false
                val now = System.currentTimeMillis()
                val existingOutbox = outboxDao.findFor(OutboxEntityType.DEBT_CREDIT, id)
                when (
                    val decision =
                        OutboxCoalescer.coalesce(
                            existingOutbox?.operation,
                            existingOutbox?.status,
                            OutboxOperation.DELETE,
                        )
                ) {
                    is CoalesceDecision.HardDeleteNoOutbox -> {
                        outboxDao.deleteFor(OutboxEntityType.DEBT_CREDIT, id)
                        debtCreditDao.hardDelete(id)
                    }
                    is CoalesceDecision.WriteOutbox -> {
                        debtCreditDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                        outboxDao.upsert(upsertedOutboxEntry(existingOutbox, OutboxEntityType.DEBT_CREDIT, id, decision.operation, now))
                        shouldPush = true
                    }
                    is CoalesceDecision.LeaveInFlight -> {
                        debtCreditDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                    }
                }
                true
            }
        if (shouldPush) pushEagerly(id)
        // A deleted debt must not keep reminding, regardless of outbox coalescing outcome.
        appContext?.let { ReminderScheduler.cancelAll(it, ReminderScheduler.KIND_DEBT, id) }
        return result
    }

    /**
     * Links [transactionId] (local id) to [debtCreditId] (local id). The local FK write is
     * authoritative and always happens immediately (offline-first). The actual server call is
     * a distinct verb, not routed through the generic CREATE/UPDATE outbox machinery (unlike a
     * field edit, and unlike transactions — which have no update endpoint at all) — fired
     * best-effort only when both sides have already synced (have a serverId) and the device is
     * online; otherwise this link only reaches the server via the transaction's own CREATE push
     * (see [TransactionRepository.createTransaction]/[cc.dlabs.pesamind.core.sync.OutboxPusher.pushTransactionEntry],
     * which resolves `debtCreditId` to a server id at push time) or a later manual re-link.
     */
    suspend fun linkTransaction(
        debtCreditId: String,
        transactionId: String,
    ) {
        val debt =
            database.withTransaction {
                val tx = transactionDao.getById(transactionId) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                transactionDao.update(tx.copy(debtCreditId = debtCreditId, updatedAt = now))
                debtCreditDao.getById(debtCreditId)
            } ?: return
        val tx = transactionDao.getById(transactionId) ?: return
        val debtServerId = debt.serverId
        val txServerId = tx.serverId
        if (debtServerId != null && txServerId != null && networkMonitor?.isConnectedNow == true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = ApiClient.api.linkDebtCreditTransaction(debtServerId, LinkTransactionRequest(txServerId))
                    // The link endpoint returns the debt with outstanding/settledAt already
                    // recomputed server-side — reconcile it immediately rather than leaving the
                    // local cache stale until the next periodic sync pull picks it up.
                    val details = response.body()
                    if (response.isSuccessful && details != null) {
                        reconcileFromServer(details)
                    } else {
                        Log.w(TAG, "Eager link-transaction call failed for debt $debtCreditId / tx $transactionId: HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Eager link-transaction call failed for debt $debtCreditId / tx $transactionId", e)
                }
            }
        }
    }

    suspend fun unlinkTransaction(
        debtCreditId: String,
        transactionId: String,
    ) {
        val debt =
            database.withTransaction {
                val tx = transactionDao.getById(transactionId) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                transactionDao.update(tx.copy(debtCreditId = null, updatedAt = now))
                debtCreditDao.getById(debtCreditId)
            } ?: return
        val debtServerId = debt.serverId
        if (debtServerId != null && networkMonitor?.isConnectedNow == true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // transactionId here is the local id; the unlink endpoint only needs to know
                    // which server transaction to detach, so resolve its serverId fresh.
                    val txServerId = transactionDao.getById(transactionId)?.serverId ?: return@launch
                    val response = ApiClient.api.unlinkDebtCreditTransaction(debtServerId, LinkTransactionRequest(txServerId))
                    // Same as linkTransaction above: reconcile the server-recomputed
                    // outstanding/settledAt immediately instead of waiting on the next sync pull.
                    val details = response.body()
                    if (response.isSuccessful && details != null) {
                        reconcileFromServer(details)
                    } else {
                        Log.w(
                            TAG,
                            "Eager unlink-transaction call failed for debt $debtCreditId / tx $transactionId: HTTP ${response.code()}",
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Eager unlink-transaction call failed for debt $debtCreditId / tx $transactionId", e)
                }
            }
        }
    }

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant, mirrors [ChannelRepository.reconcileFromServer]).
     * This is the ONLY call site where `settledAt` ever changes locally (server-computed, never
     * set by any local mutation) — the settle/un-settle transition diff below is exactly where
     * reminder cancel/reschedule must be wired; a reminder firing after settlement is a
     * trust-breaking bug.
     */
    suspend fun reconcileFromServer(details: DebtCreditResponse): DebtCreditResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = debtCreditDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val wasSettled = existing!!.settledAt != null
                    val updated =
                        existing.copy(
                            direction = details.direction,
                            counterpartyName = details.counterpartyName,
                            counterpartyPhone = details.counterpartyPhone,
                            counterpartyPhoneNormalized = PhoneNumberNormalizer.normalize(details.counterpartyPhone),
                            originalAmount = details.originalAmount,
                            outstanding = details.outstanding,
                            note = details.note.orEmpty(),
                            dueAt = parseRfc3339Millis(details.dueDate),
                            reminderOffsetsCsv = DebtCreditEntity.encode(details.reminderOffsets.orEmpty()),
                            settledAt = parseRfc3339Millis(details.settledAt),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    debtCreditDao.update(updated)
                    val nowSettled = updated.settledAt != null
                    appContext?.let { ctx ->
                        if (!wasSettled && nowSettled) {
                            ReminderScheduler.cancelAll(ctx, ReminderScheduler.KIND_DEBT, updated.id)
                        } else if (wasSettled && !nowSettled) {
                            scheduleReminders(updated)
                        }
                    }
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val sessionUserId = currentUserId()
                    val inserted =
                        DebtCreditEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = sessionUserId,
                            direction = details.direction,
                            counterpartyName = details.counterpartyName,
                            counterpartyPhone = details.counterpartyPhone,
                            counterpartyPhoneNormalized = PhoneNumberNormalizer.normalize(details.counterpartyPhone),
                            originalAmount = details.originalAmount,
                            outstanding = details.outstanding,
                            note = details.note.orEmpty(),
                            dueAt = parseRfc3339Millis(details.dueDate),
                            reminderOffsetsCsv = DebtCreditEntity.encode(details.reminderOffsets.orEmpty()),
                            settledAt = parseRfc3339Millis(details.settledAt),
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = parseRfc3339Millis(details.createdAt) ?: now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    val rowId = debtCreditDao.insertIgnore(inserted)
                    val result = if (rowId == -1L) debtCreditDao.findByServerId(details.id)!! else inserted
                    // A freshly-pulled record from another device must only be scheduled if it
                    // isn't already settled.
                    if (result.settledAt == null) scheduleReminders(result)
                    result.toDetails()
                }
            }
        }

    private fun scheduleReminders(entity: DebtCreditEntity) {
        val ctx = appContext ?: return
        ReminderScheduler.scheduleAll(
            context = ctx,
            kind = ReminderScheduler.KIND_DEBT,
            entityId = entity.id,
            dueOrTargetAtMillis = entity.dueAt,
            offsets = entity.reminderOffsets,
            deepLink = "pesamind://debt/${entity.id}",
        )
    }

    private suspend fun currentUserId(): String = AccountManager.currentUserIdOrEmpty()

    private suspend fun enqueueOutbox(
        entityType: OutboxEntityType,
        entityId: String,
        newOp: OutboxOperation,
        now: Long,
    ) {
        val existing = outboxDao.findFor(entityType, entityId)
        when (
            val decision =
                OutboxCoalescer.coalesce(existing?.operation, existing?.status, newOp)
        ) {
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
}

/** Parses an RFC3339 timestamp (as the Go backend sends via `time.RFC3339`) into epoch millis,
 * or null if absent/unparseable. */
internal fun parseRfc3339Millis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/**
 * Formats epoch millis as RFC3339, for request bodies.
 *
 * `ISO_INSTANT`, not `OffsetDateTime.toString()`: the latter emits the *shortest* valid ISO-8601
 * form, dropping the seconds field entirely when it is zero (`2026-09-30T00:00Z`). RFC3339 makes
 * seconds mandatory, so the backend's `time.Parse(time.RFC3339, ...)` rejects that with
 * `"<field> must be RFC3339"`. It only ever bit the date-picker fields — `due_date`/`target_date`
 * land on exact midnight, so their seconds are always zero, while `occurred_at`/`created_at` come
 * from `System.currentTimeMillis()` and essentially never do. `ISO_INSTANT` always writes the
 * seconds field and still preserves sub-second precision where it exists.
 */
internal fun formatRfc3339(millis: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

internal fun DebtCreditEntity.toDetails() =
    DebtCreditResponse(
        id = id,
        direction = direction,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        originalAmount = originalAmount,
        outstanding = outstanding,
        note = note,
        dueDate = dueAt?.let { formatRfc3339(it) },
        reminderOffsets = reminderOffsets,
        settledAt = settledAt?.let { formatRfc3339(it) },
        status = if (settledAt != null) "settled" else "active",
        syncStatus = syncStatus,
        createdAt = formatRfc3339(createdAt),
        updatedAt = formatRfc3339(updatedAt),
    )
