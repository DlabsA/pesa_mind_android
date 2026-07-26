package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncMetadataDataStore by preferencesDataStore("pesamind_sync_metadata")

/**
 * Bookkeeping only — `ApiService`'s GET endpoints take zero query parameters (confirmed by
 * reading the interface directly), so there is no server-supplied delta cursor to advance
 * against. [lastFullPullAt] records when [cc.dlabs.pesamind.core.sync.SyncWorker]'s pull
 * phase last completed fully successfully, for a future "last synced" UI indicator — it is
 * never sent as a request parameter, and Slice A2 deliberately does not fabricate a
 * client-clock-based delta filter in its place (see ADR-0004).
 */
object SyncMetadataManager {
    private val LastFullPullAt = longPreferencesKey("last_full_pull_at")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun setLastFullPullAt(timestampMs: Long) {
        if (!isInitialized()) return
        appContext.syncMetadataDataStore.edit { it[LastFullPullAt] = timestampMs }
    }

    suspend fun getLastFullPullAt(): Long {
        if (!isInitialized()) return 0L
        return appContext.syncMetadataDataStore.data.map { it[LastFullPullAt] ?: 0L }.first()
    }
}
