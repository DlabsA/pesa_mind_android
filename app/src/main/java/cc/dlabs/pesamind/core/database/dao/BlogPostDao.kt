package cc.dlabs.pesamind.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.dlabs.pesamind.core.database.entity.BlogPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlogPostDao {
    @Query("SELECT * FROM blog_posts ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<BlogPostEntity>>

    @Query("SELECT * FROM blog_posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BlogPostEntity?

    /** Cursor for the next incremental fetch — [BlogRepository.refreshPosts] passes this as
     * the `since` query param so a re-fetch only pulls posts published after what's already
     * cached, rather than re-downloading full history every time. */
    @Query("SELECT MAX(publishedAt) FROM blog_posts")
    suspend fun getLatestPublishedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BlogPostEntity>)

    @Update
    suspend fun update(entity: BlogPostEntity)
}
