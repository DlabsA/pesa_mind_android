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
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room-backed source of truth for channels (ADR-0004 Slice A1) — replaces `ChannelManager`'s
 * DataStore blob for every `ChannelViewModel` call site.
 *
 * This crosses the repository-layer threshold `.claude/CLAUDE.md` sets ("do not introduce a
 * repository for a single feature in isolation... only when a domain has real cross-feature
 * reuse or multi-source merging") because Room here is a genuine multi-source merge point:
 * local cache + a durable outbox + a future sync worker (Slice A2), consumed by more than one
 * feature — `ChannelViewModel` AND the SMS auto-creation lookup in
 * `ChannelManager.isSmsAllowedForSender` (kept in sync via [findByChannelDesc]/
 * [reconcileFromServer] below, since that's the one remaining `ChannelManager` caller and it
 * would otherwise see an increasingly stale channel list now that `ChannelViewModel` no longer
 * writes through `ChannelManager` at all).
 *
 * Plain singleton object + [init], mirroring the existing manager pattern (`ChannelManager`,
 * `TokenManager`, ...) rather than Hilt constructor injection — `ChannelViewModel` is a plain
 * `ViewModel()` reached via `viewModel()` in Compose screens, not `hiltViewModel()`, and
 * converting every call site to Hilt-injected ViewModels is a wider change than this slice's
 * scope. Room access itself still goes through the Hilt-provided [PesaMindDatabase] via
 * [DatabaseEntryPoint], the same pattern `PrefsToRoomMigrator` already established.
 */
object ChannelRepository {
    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase/DAO directly
    // (no Android runtime / device available to run a real Room in-memory-database test).
    internal lateinit var database: PesaMindDatabase
    private val channelDao get() = database.channelDao()
    private val outboxDao get() = database.outboxDao()

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    fun observeChannels(): Flow<List<ChannelDetails>> = channelDao.observeAll().map { list -> list.map { it.toDetails() } }

    /** One-shot read of the current full (unfiltered) list — used by `loadChannels()` to reset
     * out of a `getByChannelType`/`getByActiveStatus` filtered view. Screens should otherwise
     * prefer [observeChannels] for live updates. */
    suspend fun getAllChannels(): List<ChannelDetails> = channelDao.getAllActive().map { it.toDetails() }

    suspend fun getByChannelType(channelType: String): List<ChannelDetails> =
        channelDao.getByChannelType(
            channelType,
        ).map { it.toDetails() }

    suspend fun getByActiveStatus(active: Boolean): List<ChannelDetails> = channelDao.getByActiveStatus(active).map { it.toDetails() }

    suspend fun createChannel(
        name: String,
        description: String,
        channelType: String,
        channelDesc: String,
        status: Boolean,
    ): ChannelDetails {
        val now = System.currentTimeMillis()
        val entity =
            ChannelEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                userId = currentUserId(),
                name = name,
                channelType = channelType,
                description = description,
                status = status,
                channelDesc = channelDesc,
                smsNotificationEnabled = true,
                syncStatus = SyncStatus.PENDING,
                dirty = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        database.withTransaction {
            channelDao.upsert(entity)
            outboxDao.upsert(newOutboxEntry(OutboxEntityType.CHANNEL, entity.id, OutboxOperation.CREATE, now))
        }
        return entity.toDetails()
    }

    suspend fun updateChannel(
        id: String,
        name: String,
        description: String,
        status: Boolean,
    ): ChannelDetails? =
        database.withTransaction {
            val existing = channelDao.getById(id) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val updated =
                existing.copy(
                    name = name,
                    description = description,
                    status = status,
                    dirty = true,
                    syncStatus = SyncStatus.PENDING,
                    updatedAt = now,
                )
            channelDao.update(updated)
            enqueueOutbox(OutboxEntityType.CHANNEL, id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    suspend fun deleteChannel(id: String): Boolean =
        database.withTransaction {
            val existing = channelDao.getById(id) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            val existingOutbox = outboxDao.findFor(OutboxEntityType.CHANNEL, id)
            when (
                val decision =
                    OutboxCoalescer.coalesce(existingOutbox?.operation, existingOutbox?.status, OutboxOperation.DELETE)
            ) {
                is CoalesceDecision.HardDeleteNoOutbox -> {
                    outboxDao.deleteFor(OutboxEntityType.CHANNEL, id)
                    channelDao.hardDelete(id)
                }
                is CoalesceDecision.WriteOutbox -> {
                    channelDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                    outboxDao.upsert(
                        upsertedOutboxEntry(existingOutbox, OutboxEntityType.CHANNEL, id, decision.operation, now),
                    )
                }
                is CoalesceDecision.LeaveInFlight -> {
                    channelDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                }
            }
            true
        }

    suspend fun setSmsNotificationEnabled(
        id: String,
        enabled: Boolean,
    ): ChannelDetails? =
        database.withTransaction {
            val existing = channelDao.getById(id) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val updated = existing.copy(smsNotificationEnabled = enabled, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now)
            channelDao.update(updated)
            enqueueOutbox(OutboxEntityType.CHANNEL, id, OutboxOperation.UPDATE, now)
            updated.toDetails()
        }

    /** Used by [cc.dlabs.pesamind.core.storage.ChannelManager.isSmsAllowedForSender] — see class doc. */
    suspend fun findByChannelDesc(channelDesc: String): ChannelDetails? = channelDao.findByChannelDesc(channelDesc)?.toDetails()

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant: "one live row per serverId, never
     * overwrite a dirty=true row from a server payload"). Used today by
     * `ChannelManager.isSmsAllowedForSender`'s auto-create path so a server-created channel
     * becomes visible to future Room-based lookups; Slice A2's full pull reuses this per row.
     */
    suspend fun reconcileFromServer(details: ChannelDetails): ChannelDetails =
        database.withTransaction {
            val now = System.currentTimeMillis()
            // findByServerId deliberately includes soft-deleted rows so ReconcileResolver
            // can see (and refuse to touch) a tombstone instead of missing it and inserting
            // a live duplicate for the same serverId — see ReconcileResolver's doc comment.
            val existing = channelDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            name = details.name,
                            channelType = details.channelType,
                            description = details.description,
                            status = details.status,
                            channelDesc = details.channelDesc,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    channelDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val inserted =
                        ChannelEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = details.userId,
                            name = details.name,
                            channelType = details.channelType,
                            description = details.description,
                            status = details.status,
                            channelDesc = details.channelDesc,
                            smsNotificationEnabled = details.smsNotificationEnabled,
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    channelDao.upsert(inserted)
                    inserted.toDetails()
                }
            }
        }

    /** Best-effort current user id for locally-created rows (ADR-0004 Slice A2 — closes the
     * "userId = ''" gap A1 shipped with). Mirrors `TransactionViewModel.currentUsername()`'s
     * swallow-to-empty pattern: a repository write must never fail just because identity
     * lookup did. */
    private suspend fun currentUserId(): String =
        try {
            AccountManager.getAccount().id
        } catch (e: Exception) {
            ""
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

internal fun ChannelEntity.toDetails() =
    ChannelDetails(
        id = id,
        userId = userId,
        name = name,
        channelType = channelType,
        description = description,
        status = status,
        channelDesc = channelDesc,
        smsNotificationEnabled = smsNotificationEnabled,
    )
