package cc.dlabs.pesamind.core.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.text.get

private val Context.dataStore by preferencesDataStore("pesamind_prefs")

object TokenManager {
    private const val TAG = "TokenManager"

    enum class LockState {
        NONE,
        PIN,
        PATTERN,
    }

    private val TOKEN_KEY = stringPreferencesKey("jwt_token")
    private val REFRESH_KEY = stringPreferencesKey("refresh_token")
    private val PIN_KEY = stringPreferencesKey("user_pin")
    private val PATTERN_KEY = stringPreferencesKey("user_pattern")
    private val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    private val PATTERN_ENABLED = booleanPreferencesKey("pattern_enabled")
    private val CHANNELS_ONBOARDED = booleanPreferencesKey("channels_onboarded")

    lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
        TokenCryptoManager.init(appContext)
        // One-time-in-effect, idempotent-by-construction migration (ADR-0005): existing
        // plaintext values get encrypted in place on the next read/write anyway once this
        // ships, but PIN/pattern are written once at setup and may otherwise never be
        // rewritten again — this proactively re-encrypts them without waiting for that.
        // Fire-and-forget + caught, same discipline as PrefsToRoomMigrator's call site in
        // PesaMindApp.onCreate(): this runs unsupervised at process startup and must not crash
        // the launch it's trying to make safer.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                migrateToEncryptedStorage()
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted-storage migration failed; will retry next launch", e)
            }
        }
    }

    private suspend fun migrateToEncryptedStorage() {
        if (!isInitialized()) return
        appContext.dataStore.edit { prefs ->
            listOf(TOKEN_KEY, REFRESH_KEY, PIN_KEY, PATTERN_KEY).forEach { key ->
                val raw = prefs[key]
                if (raw != null && !TokenCryptoManager.isEncrypted(raw)) {
                    prefs[key] = TokenCryptoManager.encrypt(raw, key.name)
                }
            }
        }
    }

    /**
     * Reads [key], decrypts it, and — only on a genuine decrypt failure (the value was
     * encrypted but can no longer be read: new device, Keystore key invalidated/corrupted) —
     * runs [onFailure] before returning null. This is what lets a decrypt failure "fail
     * cleanly to re-auth / PIN re-enrol" (ADR-0005) instead of stranding the caller: every
     * existing get*() call site already treats a null return as "not set," and every existing
     * screen already handles that case (Login, "PIN not found. Please set up a PIN first.",
     * etc.) — so this needs no caller changes anywhere.
     */
    private suspend fun readSecret(
        key: Preferences.Key<String>,
        onFailure: suspend () -> Unit,
    ): String? {
        val raw = appContext.dataStore.data.first()[key]
        return when (val outcome = TokenCryptoManager.decryptOutcome(raw, key.name)) {
            is TokenCryptoManager.DecryptOutcome.Success -> outcome.plaintext
            TokenCryptoManager.DecryptOutcome.Absent -> null
            TokenCryptoManager.DecryptOutcome.Failed -> {
                onFailure()
                null
            }
        }
    }

    // ── Token ────────────────────────────────────────────────
    suspend fun saveTokens(
        token: String?,
        refresh: String?,
    ) {
        if (!isInitialized()) return
        appContext.dataStore.edit { preferences ->
            token?.let { preferences[TOKEN_KEY] = TokenCryptoManager.encrypt(it, TOKEN_KEY.name) }
            refresh?.let { preferences[REFRESH_KEY] = TokenCryptoManager.encrypt(it, REFRESH_KEY.name) }
        }
    }

    suspend fun getToken(): String? {
        if (!isInitialized()) return null
        return readSecret(TOKEN_KEY) { clearTokens() }
    }

    suspend fun getRefreshToken(): String? {
        if (!isInitialized()) return null
        return readSecret(REFRESH_KEY) { clearRefreshToken() }
    }

    suspend fun clearTokens() {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it.remove(TOKEN_KEY)
            it.remove(REFRESH_KEY)
        }
    }

    suspend fun clearRefreshToken() {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it.remove(REFRESH_KEY)
        }
    }

    suspend fun isLoggedIn(): Boolean = getToken() != null

    suspend fun getLockState(): LockState =
        when {
            isPinEnabled() -> LockState.PIN
            isPatternEnabled() -> LockState.PATTERN
            else -> LockState.NONE
        }

    suspend fun hasAnyLock(): Boolean = getLockState() != LockState.NONE

    suspend fun requiresLockSetup(): Boolean = isLoggedIn() && !hasAnyLock()

    // ── Channel onboarding ──────────────────────────────────
    // Local gate for the post-signup channel-onboarding flow. Non-secret UI-gate state (unlike
    // PIN_KEY/PATTERN_KEY above), so no TokenCryptoManager involvement — mirrors PIN_ENABLED's
    // plain booleanPreferencesKey treatment. Never derived from local channel count: finishing
    // onboarding with 0 channels is a valid completed state. Set to true locally as soon as the
    // user finishes onboarding (before the batch necessarily syncs, for offline-first safety);
    // only ever flipped true from a server response, never back to false — see AuthViewModel's
    // server-true-wins sync on login.
    suspend fun isChannelsOnboarded(): Boolean =
        if (!isInitialized()) false else appContext.dataStore.data.first()[CHANNELS_ONBOARDED] ?: false

    suspend fun setChannelsOnboarded(value: Boolean) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[CHANNELS_ONBOARDED] = value
        }
    }

    // ── PIN ──────────────────────────────────────────────────
    suspend fun savePin(pin: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[PIN_KEY] = TokenCryptoManager.encrypt(pin, PIN_KEY.name)
            it[PIN_ENABLED] = true
            // Disable pattern when PIN is set
            it[PATTERN_ENABLED] = false
            it.remove(PATTERN_KEY)
        }
    }

    suspend fun getPin(): String? {
        if (!isInitialized()) return null
        // onFailure clears the whole lock (not just PIN_KEY): a decrypt failure here means the
        // Keystore key is gone, which affects every field it was used for, not just this one —
        // see PATTERN's identical handling below.
        return readSecret(PIN_KEY) { clearLock() }
    }

    suspend fun isPinEnabled(): Boolean = if (!isInitialized()) false else appContext.dataStore.data.first()[PIN_ENABLED] ?: false

    // ── Pattern ──────────────────────────────────────────────
    suspend fun savePattern(pattern: String) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[PATTERN_KEY] = TokenCryptoManager.encrypt(pattern, PATTERN_KEY.name)
            it[PATTERN_ENABLED] = true
            // Disable PIN when pattern is set
            it[PIN_ENABLED] = false
            it.remove(PIN_KEY)
        }
    }

    suspend fun getPattern(): String? {
        if (!isInitialized()) return null
        return readSecret(PATTERN_KEY) { clearLock() }
    }

    suspend fun clearLock() {
        appContext.dataStore.edit {
            it[PIN_ENABLED] = false
            it[PATTERN_ENABLED] = false
            it.remove(PIN_KEY)
            it.remove(PATTERN_KEY)
        }
    }

    suspend fun isPatternEnabled(): Boolean = if (!isInitialized()) false else appContext.dataStore.data.first()[PATTERN_ENABLED] ?: false
}
