package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlin.text.get

private val Context.dataStore by preferencesDataStore("pesamind_prefs")

object TokenManager {
    enum class LockState {
        NONE,
        PIN,
        PATTERN
    }

    private val TOKEN_KEY = stringPreferencesKey("jwt_token")
    private val REFRESH_KEY = stringPreferencesKey("refresh_token")
    private val PIN_KEY = stringPreferencesKey("user_pin")
    private val PATTERN_KEY = stringPreferencesKey("user_pattern")
    private val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    private val PATTERN_ENABLED = booleanPreferencesKey("pattern_enabled")

    lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Token ────────────────────────────────────────────────
    suspend fun saveTokens(token: String?, refresh: String?) {
        if (!isInitialized()) return
        appContext.dataStore.edit { preferences ->
            token?.let { preferences[TOKEN_KEY] = it }
            refresh?.let { preferences[REFRESH_KEY] = it }
        }
    }

    suspend fun getToken(): String? {
        if (!isInitialized()) return null
        return appContext.dataStore.data.first()[TOKEN_KEY]
    }

    suspend fun getRefreshToken(): String? {
        if (!isInitialized()) return null
        return appContext.dataStore.data.first()[REFRESH_KEY]
    }

    suspend fun clearTokens() {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it.remove(TOKEN_KEY)
            it.remove(REFRESH_KEY)
        }
    }

    suspend fun isLoggedIn(): Boolean = getToken() != null

    suspend fun getLockState(): LockState = when {
        isPinEnabled() -> LockState.PIN
        isPatternEnabled() -> LockState.PATTERN
        else -> LockState.NONE
    }

    suspend fun hasAnyLock(): Boolean = getLockState() != LockState.NONE

    suspend fun requiresLockSetup(): Boolean = isLoggedIn() && !hasAnyLock()

    // ── PIN ──────────────────────────────────────────────────
    suspend fun savePin(pin: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[PIN_KEY] = pin
            it[PIN_ENABLED] = true
            // Disable pattern when PIN is set
            it[PATTERN_ENABLED] = false
            it.remove(PATTERN_KEY)
        }
    }

    suspend fun getPin(): String? {
        if (!isInitialized()) return null
        return appContext.dataStore.data.first()[PIN_KEY]
    }

    suspend fun isPinEnabled(): Boolean =
        if (!isInitialized()) false else appContext.dataStore.data.first()[PIN_ENABLED] ?: false

    // ── Pattern ──────────────────────────────────────────────
    suspend fun savePattern(pattern: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[PATTERN_KEY] = pattern
            it[PATTERN_ENABLED] = true
            // Disable PIN when pattern is set
            it[PIN_ENABLED] = false
            it.remove(PIN_KEY)
        }
    }

    suspend fun getPattern(): String? {
        if (!isInitialized()) return null
        return appContext.dataStore.data.first()[PATTERN_KEY]
    }

    suspend fun clearLock() {
        appContext.dataStore.edit {
            it[PIN_ENABLED] = false
            it[PATTERN_ENABLED] = false
            it.remove(PIN_KEY)
            it.remove(PATTERN_KEY)
        }
    }

    suspend fun isPatternEnabled(): Boolean =
        if (!isInitialized()) false else appContext.dataStore.data.first()[PATTERN_ENABLED] ?: false
}