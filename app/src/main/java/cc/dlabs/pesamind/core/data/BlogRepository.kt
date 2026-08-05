package cc.dlabs.pesamind.core.data

import android.content.Context
import android.util.Log
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.entity.BlogPostEntity
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.BlogPostResponse
import dagger.hilt.EntryPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Read-only Room cache for admin-authored finance blog posts (see [BlogPostEntity]'s doc
 * comment) — fetched from the backend's `GET blog-posts` and never written back by this app.
 * Same singleton-object + [init] DI pattern as [ChannelRepository]/[TransactionRepository].
 */
object BlogRepository {
    private const val TAG = "BlogRepository"

    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase directly, same
    // rationale as TransactionRepository.database.
    internal lateinit var database: PesaMindDatabase
    private val blogPostDao get() = database.blogPostDao()

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    fun observePosts(): Flow<List<BlogPostEntity>> = flow { emitAll(blogPostDao.observeAll()) }

    /**
     * Incremental pull — fetches everything published after the newest post already cached
     * ([cc.dlabs.pesamind.core.database.dao.BlogPostDao.getLatestPublishedAt]) and upserts the
     * result. Called on [cc.dlabs.pesamind.features.blog.BlogViewModel] load/refresh, and from
     * [cc.dlabs.pesamind.features.blog.BlogMessagingService] when a push notification signals
     * new content — so a post shows up locally whichever path runs first. Failures (including
     * the expected 404 until the backend implements this endpoint — see [BlogPostResponse]'s
     * doc comment) are swallowed here the same way [ChannelRepository]/[TransactionRepository]
     * treat a failed background refresh: the caller's existing local list still renders.
     */
    suspend fun refreshPosts() {
        try {
            val since = blogPostDao.getLatestPublishedAt()
            val response = ApiClient.api.getBlogPosts(since = since?.toString())
            if (response.isSuccessful) {
                val now = System.currentTimeMillis()
                val posts = response.body().orEmpty().map { it.toEntity(fetchedAt = now) }
                if (posts.isNotEmpty()) blogPostDao.upsertAll(posts)
            } else {
                Log.w(TAG, "getBlogPosts failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "getBlogPosts failed", e)
        }
    }

    suspend fun markRead(id: String) {
        val existing = blogPostDao.getById(id) ?: return
        if (existing.isRead) return
        blogPostDao.update(existing.copy(isRead = true))
    }
}

/** The exact timestamp format `GET blog-posts` will return isn't defined yet (the endpoint
 * doesn't exist — see [BlogPostResponse]'s doc comment), so this tries standard ISO-8601
 * first (the ordinary case for a new JSON endpoint) and falls back to treating the string as
 * raw epoch millis; either failing, the row is stamped with [fetchedAt] rather than dropped. */
private fun BlogPostResponse.toEntity(fetchedAt: Long): BlogPostEntity =
    BlogPostEntity(
        id = id,
        title = title,
        body = body,
        publishedAt = parseBlogTimestampMillis(publishedAt) ?: fetchedAt,
        fetchedAt = fetchedAt,
        isRead = false,
    )

private fun parseBlogTimestampMillis(raw: String): Long? =
    try {
        Instant.parse(raw).toEpochMilli()
    } catch (e: DateTimeParseException) {
        raw.toLongOrNull()
    }
