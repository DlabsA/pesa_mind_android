package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.notificationDataStore by preferencesDataStore("pesamind_notifications")

object NotificationStorage {
    private val CACHED_MESSAGES = stringPreferencesKey("cached_messages")
    private val LAST_SYNC = stringPreferencesKey("last_sync_time")
    private val PENDING_SYNC = stringPreferencesKey("pending_messages")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Save incoming SMS locally before syncing
     */
    suspend fun savePendingMessage(message: String) {
        if (!isInitialized()) return
        appContext.notificationDataStore.edit { prefs ->
            val existing = prefs[PENDING_SYNC] ?: ""
            val updated = if (existing.isEmpty()) message else "$existing\n$message"
            prefs[PENDING_SYNC] = updated
        }
    }

    /**
     * Get all pending messages waiting to sync
     */
    suspend fun getPendingMessages(): String {
        if (!isInitialized()) return ""
        val data = appContext.notificationDataStore.data.first()
        return data[PENDING_SYNC] ?: ""
    }

    /**
     * Clear pending messages after successful sync
     */
    suspend fun clearPendingMessages() {
        if (!isInitialized()) return
        appContext.notificationDataStore.edit {
            it.remove(PENDING_SYNC)
        }
    }

    /**
     * Update last sync timestamp
     */
    suspend fun updateLastSyncTime(timestamp: Long) {
        if (!isInitialized()) return
        appContext.notificationDataStore.edit {
            it[LAST_SYNC] = timestamp.toString()
        }
    }

    suspend fun getLastSyncTime(): Long {
        if (!isInitialized()) return 0
        val data = appContext.notificationDataStore.data.first()
        return data[LAST_SYNC]?.toLongOrNull() ?: 0
    }

    suspend fun clearAll() {
        if (!isInitialized()) return
        appContext.notificationDataStore.edit { it.clear() }
    }
}

