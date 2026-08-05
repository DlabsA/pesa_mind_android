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
    private val AvatarUrl = stringPreferencesKey("avatar_url")
    private val Balance = stringPreferencesKey("balance")
    private val Type = stringPreferencesKey("type")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun saveAccount(
        id: String,
        email: String,
        username: String,
        avatarUrl: String,
        balance: String,
        type: String,
    ) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[ID] = id
            it[Email] = email
            it[Username] = username
            it[AvatarUrl] = avatarUrl
            it[Balance] = balance
            it[Type] = type
        }
    }

    suspend fun saveEmail(email: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[Email] = email
        }
    }

    suspend fun saveUsername(username: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[Username] = username
        }
    }

    suspend fun saveBalance(balance: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[Balance] = balance
        }
    }

    suspend fun getAccount(): Account {
        if (!isInitialized()) {
            throw IllegalStateException("Account storage not initialized")
        }
        val data = appContext.dataStore.data.first()
        return Account(
            id = data[ID] ?: "",
            username = data[Username] ?: "",
            email = data[Email] ?: "",
            avatarUrl = data[AvatarUrl] ?: "",
            type = data[Type] ?: "",
            balance = data[Balance]?.toDoubleOrNull() ?: 0.0,
        )
    }

    /**
     * Best-effort current-user id for scoping local Room rows/queries to the logged-in account
     * (ADR-0004-adjacent cross-account isolation fix) — swallows to `""` on any failure (not
     * initialized, no account saved yet) rather than throwing, mirroring [getAccount]'s
     * exception on missing init but never propagating it: a repository read/write must not
     * crash just because identity lookup failed. Was previously duplicated per-repository as
     * each one's own private `currentUserId()`; this is the single shared implementation.
     */
    suspend fun currentUserIdOrEmpty(): String =
        try {
            getAccount().id
        } catch (e: Exception) {
            ""
        }

    suspend fun clearAccount() {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it.remove(ID)
            it.remove(Email)
            it.remove(Username)
            it.remove(AvatarUrl)
            it.remove(Balance)
            it.remove(Type)
        }
    }
}
