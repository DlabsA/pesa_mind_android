package cc.dlabs.pesamind.core.data

import android.content.Context
import android.util.Log
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.ProcessedMessageEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.sync.OutboxPusher
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Room-backed, write-once audit trail of SMS the ingestion pipeline saw
 * ([cc.dlabs.pesamind.features.settings.notifications.SMSMessageProcessor] is the only
 * caller) — pushed to the backend's `POST /processed-messages` via the outbox, same
 * offline-first shape as [ChannelRepository]/[TransactionRepository]. No UI reads from this
 * repository; it exists purely so [record] never blocks on network state.
 *
 * Simpler than its siblings in one respect: a [ProcessedMessageEntity] is never locally
 * updated or deleted after creation, so there's no [cc.dlabs.pesamind.core.database.OutboxCoalescer]
 * decision to make — a first-ever outbox row for a given entity id always resolves to a plain
 * CREATE regardless, so [record] just upserts one directly.
 */
object ProcessedMessageRepository {
    private const val TAG = "ProcessedMessageRepository"

    internal lateinit var database: PesaMindDatabase
    private val processedMessageDao get() = database.processedMessageDao()
    private val outboxDao get() = database.outboxDao()

    private var networkMonitor: NetworkMonitor? = null
    private var outboxPusher: OutboxPusher? = null

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
        networkMonitor = NetworkMonitor(context.applicationContext)
        outboxPusher = OutboxPusher(database, ApiClient.api)
    }

    /** See [ChannelRepository.pushEagerly]/[TransactionRepository]'s twin for the full
     * rationale — never lets a network hiccup surface as a failure of the local write. */
    private fun pushEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushProcessedMessageEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for processed message $entityId; will retry on next sync", e)
            }
        }
    }

    /**
     * [dedupeKey] mirrors [TransactionEntity][cc.dlabs.pesamind.core.database.entity.TransactionEntity]'s
     * `smsSourceKey` derivation — the caller (`SMSMessageProcessor`) computes it the same way,
     * so a redelivered/reprocessed SMS discards here via [ProcessedMessageDao.insertIgnore]
     * rather than enqueuing a second outbox push.
     */
    suspend fun record(
        senderId: String,
        content: String,
        timestamp: Long,
        simInfo: Int,
        receivingSimNumber: String,
        dedupeKey: String,
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val entity =
            ProcessedMessageEntity(
                id = id,
                serverId = null,
                senderId = senderId,
                content = content,
                timestamp = timestamp,
                simInfo = simInfo,
                receivingSimNumber = receivingSimNumber,
                dedupeKey = dedupeKey,
                syncStatus = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
        val rowId = processedMessageDao.insertIgnore(entity)
        if (rowId == -1L) return // already recorded locally — a redelivered/reprocessed SMS, not an error.

        outboxDao.upsert(
            OutboxEntry(
                id = UUID.randomUUID().toString(),
                entityType = OutboxEntityType.PROCESSED_MESSAGE,
                entityId = id,
                operation = OutboxOperation.CREATE,
                status = SyncStatus.PENDING,
                attempts = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        pushEagerly(id)
    }
}
