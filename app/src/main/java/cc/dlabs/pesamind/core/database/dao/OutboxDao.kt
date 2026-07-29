package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    /** For a "pending sync" chip/count in the UI. */
    @Query("SELECT COUNT(*) FROM outbox WHERE status != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: SyncStatus): List<OutboxEntry>

    @Query("SELECT * FROM outbox WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun findFor(
        entityType: OutboxEntityType,
        entityId: String,
    ): OutboxEntry?

    /** Upsert-by-(entityType, entityId) — a repository enqueues once per row, not once per edit. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OutboxEntry)

    @Update
    suspend fun update(entry: OutboxEntry)

    /**
     * Atomically claims a row: flips PENDING -> SYNCING only if it's still PENDING right now,
     * in one statement. [OutboxPusher]'s old claim ("read the row, check its status, then
     * write SYNCING back") was a read-then-write race — two concurrent callers (an eager push
     * and a periodic [cc.dlabs.pesamind.core.sync.SyncWorker] drain, say) could both read
     * PENDING before either wrote SYNCING, and both dispatch the same row. The loser's own
     * completion then sees the winner's write as if a local edit had landed mid-flight,
     * misfiring [PushCompletionResolver]'s CREATE -> UPDATE requeue for an entity (e.g.
     * TRANSACTION) that may not even support UPDATE server-side.
     *
     * @return rows affected — 0 means another caller already claimed it; the caller must
     * treat that as [cc.dlabs.pesamind.core.sync.OutboxPusher.PushResult.Skipped], not retry.
     */
    @Query("UPDATE outbox SET status = 'SYNCING', updatedAt = :now WHERE id = :id AND status = 'PENDING'")
    suspend fun claimIfPending(
        id: String,
        now: Long,
    ): Int

    /**
     * Gives every FAILED row one more chance the instant connectivity comes back
     * ([cc.dlabs.pesamind.PesaMindApp.onCreate]'s `NetworkMonitor.isConnected` collector) —
     * a row marked FAILED by a transient cause (an expired token, a race like
     * [claimIfPending]'s doc comment describes, a backend hiccup) has no other path back to
     * PENDING today; without this it stays stuck until someone manually intervenes. A row
     * that's FAILED for a genuinely permanent reason (bad data, an unsupported operation)
     * just fails again on the next attempt — harmless, not an infinite tight loop, since this
     * only fires once per reconnect event.
     */
    @Query("UPDATE outbox SET status = 'PENDING', attempts = 0, lastError = NULL, updatedAt = :now WHERE status = 'FAILED'")
    suspend fun resetFailedToPending(now: Long): Int

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteFor(
        entityType: OutboxEntityType,
        entityId: String,
    )
}
