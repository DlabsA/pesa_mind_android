package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached admin-authored finance write-up, fetched read-only from the backend (`GET
 * blog-posts`) and never written to by this app — there is no outbox/dirty/sync-status here
 * because the app is never the source of truth for a post, only a reader. [id] is the
 * server-assigned post id (not a client UUID, unlike [TransactionEntity]/[ChannelEntity]),
 * since posts are always created server-side by a separate admin web UI.
 */
@Entity(tableName = "blog_posts")
data class BlogPostEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val body: String,
    /** Server-supplied publish timestamp, epoch millis — used both for display and as the
     * `since` cursor for the next incremental [cc.dlabs.pesamind.core.data.BlogRepository.refreshPosts] fetch. */
    val publishedAt: Long,
    val fetchedAt: Long,
    val isRead: Boolean,
)
