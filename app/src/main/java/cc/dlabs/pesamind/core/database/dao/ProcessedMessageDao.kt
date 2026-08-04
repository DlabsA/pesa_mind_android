package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.entity.ProcessedMessageEntity

@Dao
interface ProcessedMessageDao {
    @Query("SELECT * FROM processed_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProcessedMessageEntity?

    /**
     * Race-safe insert — returns the new rowid, or `-1L` if discarded because `dedupeKey`
     * already exists (this exact SMS was already recorded, e.g. a redelivered broadcast).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ProcessedMessageEntity): Long

    @Update
    suspend fun update(entity: ProcessedMessageEntity)
}
