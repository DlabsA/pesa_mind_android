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

            val channels = Gson().fromJson<List<TransactionDetails>>(
                transactionsJson,
                object : TypeToken<List<TransactionDetails>>() {}.type
            )
            return channels
        } catch (e: Exception) {
            return emptyList()
        }
    }


}