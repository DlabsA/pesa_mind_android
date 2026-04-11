package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.network.models.Account
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("pesamind_account")

object AccountManager {
    private val ID = stringPreferencesKey("id")
    private val Email = stringPreferencesKey("email")
    private val Username = stringPreferencesKey("username")
    private val Balance = stringPreferencesKey("balance")
    private val Type = stringPreferencesKey("type")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun saveAccount(id: String, email: String, username: String, balance: String, type: String) {
        appContext.dataStore.edit {
            it[ID] = id
            it[Email] = email
            it[Username] = username
            it[Balance] = balance
            it[Type] = type
        }
    }
    suspend fun getAccount(): Account {
        val data = appContext.dataStore.data.first()
        return Account(
            data[ID] ?: "",
            data[Username] ?: "",
            data[Email] ?: "",
            data[Type] ?: "",
            data[Balance]?.toDouble() ?: 0.0
        )
    }
    suspend fun clearAccount() {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it.remove(ID)
            it.remove(Email)
            it.remove(Username)
            it.remove(Balance)
            it.remove(Type)
        }
    }
}