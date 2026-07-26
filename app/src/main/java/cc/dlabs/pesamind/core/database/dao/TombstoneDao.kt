package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.dlabs.pesamind.core.database.entity.Tombstone

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones WHERE synced = 0")
    suspend fun getUnsynced(): List<Tombstone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tombstone: Tombstone)

    @Query("UPDATE tombstones SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM tombstones WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
