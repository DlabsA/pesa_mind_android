package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.transactionDataStore by preferencesDataStore("pesamind_transactions")

/**
 * Dead since ADR-0004 Slice A1: `TransactionViewModel` now reads/writes exclusively through
 * `TransactionRepository` (Room). Unlike `ChannelManager`, nothing else in the app calls this
 * object anymore (confirmed by fan-in search) — its DataStore cache was never actually live in
 * production anyway, since `init()` was never called from `PesaMindApp.onCreate()` (see
 * ADR-0004's Step 0 verification). Left `@Deprecated` rather than deleted pending Slice C's
 * cleanup pass, per `.claude/CLAUDE.md`'s "confirm with the user before deleting" rule.
 */
@Deprecated("Dead since ADR-0004 Slice A1 — TransactionRepository (Room) is the source of truth now.")
object TransactionManager {
    private val TRANSACTIONS_KEY = stringPreferencesKey("cached_transactions")
    private val LAST_SYNC = stringPreferencesKey("transactions_last_sync")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Save Transaction locally
     */
    suspend fun saveTransactions(transactions: List<TransactionDetails>) {
        if (!isInitialized()) return
        appContext.transactionDataStore.edit { prefs ->

            val transactionsJson = Gson().toJson(transactions)
            prefs[TRANSACTIONS_KEY] = transactionsJson

            // Update sync timestamp
            prefs[LAST_SYNC] = System.currentTimeMillis().toString()
        }
    }

    /**
     * Get all cached transactions
     */
    suspend fun getTransactions(): List<TransactionDetails> {
        if (!isInitialized()) return emptyList()
        try {
            val data = appContext.transactionDataStore.data.first()
            val transactionsJson = data[TRANSACTIONS_KEY] ?: return emptyList()

            val channels =
                Gson().fromJson<List<TransactionDetails>>(
                    transactionsJson,
                    object : TypeToken<List<TransactionDetails>>() {}.type,
                )
            return channels
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun getLastSyncTime(): Long {
        if (!isInitialized()) return 0L
        return try {
            val data = appContext.transactionDataStore.data.first()
            data[LAST_SYNC]?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun isCacheStale(): Boolean {
        return SyncPolicy.isStale(getLastSyncTime())
    }

    suspend fun clearTransactions() {
        if (!isInitialized()) return
        appContext.transactionDataStore.edit { prefs ->
            prefs.remove(TRANSACTIONS_KEY)
            prefs.remove(LAST_SYNC)
        }
    }
}
