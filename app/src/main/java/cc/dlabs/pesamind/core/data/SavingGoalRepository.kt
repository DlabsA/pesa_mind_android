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
import cc.dlabs.pesamind.core.database.entity.SavingGoalEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.LinkTransactionRequest
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.OutboxPusher
import cc.dlabs.pesamind.core.utils.ReminderScheduler
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Room-backed source of truth for "Saving Goals" (Phase 2) — structural clone of
 * [DebtCreditRepository], substituting `achievedAt`/`progress` for `settledAt`/`outstanding`.
 * See that class's doc comments for the full rationale, identical here.
 */
object SavingGoalRepository {
    private const val TAG = "SavingGoalRepository"

    internal lateinit var database: PesaMindDatabase
    private val savingGoalDao get() = database.savingGoalDao()
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
                pusher.pushSavingGoalEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for saving goal $entityId; will retry on next sync", e)
            }
        }
    }

    fun observeAll(): Flow<List<SavingGoalResponse>> =
        flow { emitAll(savingGoalDao.observeAll(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    fun observeActive(): Flow<List<SavingGoalResponse>> =
        flow { emitAll(savingGoalDao.observeActive(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    fun observeAchieved(): Flow<List<SavingGoalResponse>> =
        flow { emitAll(savingGoalDao.observeAchieved(currentUserId()).map { list -> list.map { it.toDetails() } }) }

    suspend fun getById(id: String): SavingGoalResponse? = savingGoalDao.getById(id)?.toDetails()

    suspend fun createSavingGoal(
        name: String,
        targetAmount: Double,
        targetAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ): SavingGoalResponse? {
        if (!AccountManager.isPremium()) return null
        val userId = currentUserId()
        val now = System.currentTimeMillis()
        val entity =
            SavingGoalEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                userId = userId,
                name = name,
                targetAmount = targetAmount,
                progress = 0.0,
                note = note,
                targetAt = targetAtMillis,
                reminderOffsetsCsv = DebtCreditEntity.encode(reminderOffsets),
                achievedAt = null,
                syncStatus = SyncStatus.PENDING,
                dirty = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        database.withTransaction {
            savingGoalDao.upsert(entity)
            outboxDao.upsert(newOutboxEntry(OutboxEntityType.SAVING_GOAL, entity.id, OutboxOperation.CREATE, now))
        }
        pushEagerly(entity.id)
        scheduleReminders(entity)
        return entity.toDetails()
    }

    suspend fun updateSavingGoal(
        id: String,
        name: String,
        targetAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ): SavingGoalResponse? {
        if (!AccountManager.isPremium()) return null
        val updated =
            database.withTransaction {
                val existing = savingGoalDao.getById(id) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                val updated =
                    existing.copy(
                        name = name,
                        targetAt = targetAtMillis,
                        note = note,
                        reminderOffsetsCsv = DebtCreditEntity.encode(reminderOffsets),
                        dirty = true,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = now,
                    )
                savingGoalDao.update(updated)
                enqueueOutbox(OutboxEntityType.SAVING_GOAL, id, OutboxOperation.UPDATE, now)
                updated
            }
        if (updated != null) {
            pushEagerly(id)
            scheduleReminders(updated)
        }
        return updated?.toDetails()
    }

    suspend fun deleteSavingGoal(id: String): Boolean {
        var shouldPush = false
        val result =
            database.withTransaction {
                val existing = savingGoalDao.getById(id) ?: return@withTransaction false
                val now = System.currentTimeMillis()
                val existingOutbox = outboxDao.findFor(OutboxEntityType.SAVING_GOAL, id)
                when (
                    val decision = OutboxCoalescer.coalesce(existingOutbox?.operation, existingOutbox?.status, OutboxOperation.DELETE)
                ) {
                    is CoalesceDecision.HardDeleteNoOutbox -> {
                        outboxDao.deleteFor(OutboxEntityType.SAVING_GOAL, id)
                        savingGoalDao.hardDelete(id)
                    }
                    is CoalesceDecision.WriteOutbox -> {
                        savingGoalDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                        outboxDao.upsert(upsertedOutboxEntry(existingOutbox, OutboxEntityType.SAVING_GOAL, id, decision.operation, now))
                        shouldPush = true
                    }
                    is CoalesceDecision.LeaveInFlight -> {
                        savingGoalDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                    }
                }
                true
            }
        if (shouldPush) pushEagerly(id)
        appContext?.let { ReminderScheduler.cancelAll(it, ReminderScheduler.KIND_GOAL, id) }
        return result
    }

    /** See [DebtCreditRepository.linkTransaction]'s doc comment — identical shape. */
    suspend fun linkTransaction(
        savingGoalId: String,
        transactionId: String,
    ) {
        val goal =
            database.withTransaction {
                val tx = transactionDao.getById(transactionId) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                transactionDao.update(tx.copy(savingGoalId = savingGoalId, updatedAt = now))
                savingGoalDao.getById(savingGoalId)
            } ?: return
        val tx = transactionDao.getById(transactionId) ?: return
        val goalServerId = goal.serverId
        val txServerId = tx.serverId
        if (goalServerId != null && txServerId != null && networkMonitor?.isConnectedNow == true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = ApiClient.api.linkSavingGoalTransaction(goalServerId, LinkTransactionRequest(txServerId))
                    // See DebtCreditRepository.linkTransaction's identical comment — reconcile
                    // the server-recomputed progress/achievedAt immediately.
                    val details = response.body()
                    if (response.isSuccessful && details != null) {
                        reconcileFromServer(details)
                    } else {
                        Log.w(TAG, "Eager link-transaction call failed for goal $savingGoalId / tx $transactionId: HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Eager link-transaction call failed for goal $savingGoalId / tx $transactionId", e)
                }
            }
        }
    }

    suspend fun unlinkTransaction(
        savingGoalId: String,
        transactionId: String,
    ) {
        val goal =
            database.withTransaction {
                val tx = transactionDao.getById(transactionId) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                transactionDao.update(tx.copy(savingGoalId = null, updatedAt = now))
                savingGoalDao.getById(savingGoalId)
            } ?: return
        val goalServerId = goal.serverId
        if (goalServerId != null && networkMonitor?.isConnectedNow == true) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val txServerId = transactionDao.getById(transactionId)?.serverId ?: return@launch
                    val response = ApiClient.api.unlinkSavingGoalTransaction(goalServerId, LinkTransactionRequest(txServerId))
                    // See DebtCreditRepository.unlinkTransaction's identical comment — reconcile
                    // the server-recomputed progress/achievedAt immediately.
                    val details = response.body()
                    if (response.isSuccessful && details != null) {
                        reconcileFromServer(details)
                    } else {
                        Log.w(
                            TAG,
                            "Eager unlink-transaction call failed for goal $savingGoalId / tx $transactionId: HTTP ${response.code()}",
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Eager unlink-transaction call failed for goal $savingGoalId / tx $transactionId", e)
                }
            }
        }
    }

    /** See [DebtCreditRepository.reconcileFromServer]'s doc comment — identical shape, this is
     * the sole local origin of `achievedAt` changes. */
    suspend fun reconcileFromServer(details: SavingGoalResponse): SavingGoalResponse =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = savingGoalDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val wasAchieved = existing!!.achievedAt != null
                    val updated =
                        existing.copy(
                            name = details.name,
                            targetAmount = details.targetAmount,
                            progress = details.progress,
                            note = details.note.orEmpty(),
                            targetAt = parseRfc3339Millis(details.targetDate),
                            reminderOffsetsCsv =
                                DebtCreditEntity.encode(details.reminderOffsets.orEmpty()),
                            achievedAt = parseRfc3339Millis(details.achievedAt),
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    savingGoalDao.update(updated)
                    val nowAchieved = updated.achievedAt != null
                    appContext?.let { ctx ->
                        if (!wasAchieved && nowAchieved) {
                            ReminderScheduler.cancelAll(ctx, ReminderScheduler.KIND_GOAL, updated.id)
                        } else if (wasAchieved && !nowAchieved) {
                            scheduleReminders(updated)
                        }
                    }
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val sessionUserId = currentUserId()
                    val inserted =
                        SavingGoalEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = sessionUserId,
                            name = details.name,
                            targetAmount = details.targetAmount,
                            progress = details.progress,
                            note = details.note.orEmpty(),
                            targetAt = parseRfc3339Millis(details.targetDate),
                            reminderOffsetsCsv =
                                DebtCreditEntity.encode(details.reminderOffsets.orEmpty()),
                            achievedAt = parseRfc3339Millis(details.achievedAt),
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = parseRfc3339Millis(details.createdAt) ?: now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    val rowId = savingGoalDao.insertIgnore(inserted)
                    val result = if (rowId == -1L) savingGoalDao.findByServerId(details.id)!! else inserted
                    if (result.achievedAt == null) scheduleReminders(result)
                    result.toDetails()
                }
            }
        }

    private fun scheduleReminders(entity: SavingGoalEntity) {
        val ctx = appContext ?: return
        ReminderScheduler.scheduleAll(
            context = ctx,
            kind = ReminderScheduler.KIND_GOAL,
            entityId = entity.id,
            dueOrTargetAtMillis = entity.targetAt,
            offsets = entity.reminderOffsets,
            deepLink = "pesamind://goal/${entity.id}",
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

internal fun SavingGoalEntity.toDetails() =
    SavingGoalResponse(
        id = id,
        name = name,
        targetAmount = targetAmount,
        progress = progress,
        note = note,
        targetDate = targetAt?.let { formatRfc3339(it) },
        reminderOffsets = reminderOffsets,
        achievedAt = achievedAt?.let { formatRfc3339(it) },
        status = if (achievedAt != null) "achieved" else "active",
        syncStatus = syncStatus,
        createdAt = formatRfc3339(createdAt),
        updatedAt = formatRfc3339(updatedAt),
    )
