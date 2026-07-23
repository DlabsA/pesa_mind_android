package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.withTransaction
import cc.dlabs.pesamind.core.database.ExistingRowSnapshot
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.ReconcileDecision
import cc.dlabs.pesamind.core.database.ReconcileResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room-backed source of truth for transactions (ADR-0004 Slice A1) — replaces
 * `TransactionManager`'s DataStore blob (which was never actually live in production; see
 * ADR-0004's Step 0 verification) for every `TransactionViewModel` call site. Same repository
 * justification and DI pattern as [ChannelRepository] — see its class doc.
 */
object TransactionRepository {
    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase/DAO directly
    // (no Android runtime / device available to run a real Room in-memory-database test).
    internal lateinit var database: PesaMindDatabase
    private val transactionDao get() = database.transactionDao()
    private val outboxDao get() = database.outboxDao()

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    fun observeTransactions(): Flow<List<TransactionDetails>> = transactionDao.observeAll().map { list -> list.map { it.toDetails() } }

    /** One-shot read of the current list — used by `loadTransactions()`/`refresh()`, which
     * screens should otherwise prefer [observeTransactions] over for live updates. */
    suspend fun getAllTransactions(): List<TransactionDetails> = transactionDao.getAllActive().map { it.toDetails() }

    /**
     * [channelId] is the local `ChannelEntity.id` (UUID) — the creating code (manual entry or
     * SMS ingestion) always already knows which local channel this is, per ADR-0004's
     * "TransactionEntity.channelId is a nullable FK, resolved best-effort by name" section
     * (that best-effort path is only for server-pulled rows, not locally-created ones). The
     * display name is resolved here from Room rather than passed in, so the local row shows a
     * real channel name immediately without waiting on any sync.
     *
     * [smsSourceKey] dedups against `TransactionEntity.smsSourceKey`'s unique index (see its
     * doc comment) when a caller supplies one — reprocessing the same SMS is a no-op, returning
     * the already-created row instead of inserting a duplicate. No caller passes a real key
     * yet (SMS dedup key generation is ADR-0004 Slice A3's job), so this is currently inert in
     * practice, not exercised — but it's the repository's job to hold the invariant the moment
     * A3 starts supplying keys, not something to retrofit then.
     */
    suspend fun createTransaction(
        channelId: String,
        amount: Double,
        type: String,
        note: String,
        username: String,
        smsSourceKey: String? = null,
    ): TransactionDetails {
        val now = System.currentTimeMillis()
        return database.withTransaction {
            if (smsSourceKey != null) {
                transactionDao.findBySmsSourceKey(smsSourceKey)?.let { return@withTransaction it.toDetails() }
            }
            val channelName = database.channelDao().getById(channelId)?.name.orEmpty()
            val entity =
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    serverId = null,
                    channelId = channelId,
                    channelDetailsName = channelName,
                    amount = amount,
                    type = type,
                    note = note,
                    username = username,
                    smsSourceKey = smsSourceKey,
                    syncStatus = SyncStatus.PENDING,
                    dirty = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            transactionDao.upsert(entity)
            outboxDao.upsert(
                OutboxEntry(
                    id = UUID.randomUUID().toString(),
                    entityType = OutboxEntityType.TRANSACTION,
                    entityId = entity.id,
                    operation = OutboxOperation.CREATE,
                    status = SyncStatus.PENDING,
                    attempts = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            entity.toDetails()
        }
    }

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant: "one live row per serverId, never
     * overwrite a dirty=true row from a server payload"). Not called by anything in A1 —
     * written now, per the task brief, so Slice A2's full pull doesn't have to retrofit it.
     * [resolvedChannelId] is the best-effort name-matched local channel id (see
     * `PrefsToRoomMigrator.toEntity` for the exact matching logic this mirrors), or null if
     * unresolved.
     */
    suspend fun reconcileFromServer(
        details: TransactionDetails,
        resolvedChannelId: String?,
    ): TransactionDetails =
        database.withTransaction {
            val now = System.currentTimeMillis()
            // findByServerId deliberately includes soft-deleted rows so ReconcileResolver can
            // see (and refuse to touch) a tombstone instead of missing it and inserting a live
            // duplicate for the same serverId — see ReconcileResolver's doc comment.
            val existing = transactionDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            channelId = resolvedChannelId ?: existing.channelId,
                            channelDetailsName = details.channelDetailsName,
                            amount = details.amount,
                            type = details.type,
                            note = details.note,
                            username = details.username,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    transactionDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val inserted =
                        TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            channelId = resolvedChannelId,
                            channelDetailsName = details.channelDetailsName,
                            amount = details.amount,
                            type = details.type,
                            note = details.note,
                            username = details.username,
                            smsSourceKey = null,
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    transactionDao.upsert(inserted)
                    inserted.toDetails()
                }
            }
        }
}

internal fun TransactionEntity.toDetails() =
    TransactionDetails(
        id = id,
        amount = amount,
        type = type,
        note = note,
        channelDetailsName = channelDetailsName,
        username = username,
    )
