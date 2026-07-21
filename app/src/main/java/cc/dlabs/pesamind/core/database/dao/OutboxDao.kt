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

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteFor(
        entityType: OutboxEntityType,
        entityId: String,
    )
}
