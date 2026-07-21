package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = :id LIMIT 1")
    fun observe(id: String = ProfileEntity.LOCAL_PROFILE_ID): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id LIMIT 1")
    suspend fun get(id: String = ProfileEntity.LOCAL_PROFILE_ID): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProfileEntity)

    @Update
    suspend fun update(entity: ProfileEntity)
}
