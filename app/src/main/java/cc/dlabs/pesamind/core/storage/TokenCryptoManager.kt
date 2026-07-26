package cc.dlabs.pesamind.core.storage

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Encrypts/decrypts the short secrets [TokenManager] persists (JWT, refresh token, PIN,
 * pattern) before they reach DataStore. Uses Tink's `Aead` primitive (AES256-GCM) with the
 * keyset wrapped by an Android Keystore-resident key via [AndroidKeysetManager] —
 * deliberately not `StreamingAead`: that primitive's chunked AES-GCM-HKDF-STREAMING format
 * exists for large file/stream encryption and adds per-segment framing overhead with no
 * benefit for encrypting a handful of short strings. Tink's own Android/Keystore integration
 * guide uses `Aead` for exactly this "small app secret, Keystore-wrapped key" case. Also
 * deliberately not `EncryptedSharedPreferences` — see ADR-0005 for why. See ADR-0005 for the
 * full rationale and the one-time migration this pairs with in [TokenManager].
 */
object TokenCryptoManager {
    private const val TAG = "TokenCryptoManager"
    private const val KEYSET_NAME = "pesamind_token_keyset"
    private const val PREF_FILE_NAME = "pesamind_token_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://pesamind_token_master_key"

    /** Marks a DataStore value as Tink-encrypted vs. a still-plaintext, pre-migration value. */
    private const val ENCRYPTED_PREFIX = "ENC1:"

    sealed class DecryptOutcome {
        data class Success(val plaintext: String) : DecryptOutcome()

        data object Absent : DecryptOutcome()

        data object Failed : DecryptOutcome()
    }

    @Volatile
    private var aead: Aead? = null

    /**
     * Idempotent — safe to call on every launch (mirrors [TokenManager.init]'s own call
     * pattern). Failure here (Keystore/Tink setup) is not expected on any real API 26+ device,
     * but must not crash startup if it ever happens: [encrypt] falls back to storing the value
     * unencrypted, which is exactly this app's behavior before this change, not a new failure
     * mode — logged loudly so it's not a silent regression either.
     */
    fun init(context: Context) {
        if (aead != null) return
        try {
            AeadConfig.register()
            val keysetHandle =
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
            aead = keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Tink/Keystore init failed — falling back to plaintext storage", e)
        }
    }

    fun isEncrypted(stored: String?): Boolean = stored?.startsWith(ENCRYPTED_PREFIX) == true

    /**
     * [associatedData] binds the ciphertext to the field it came from (callers pass the
     * DataStore key's own name, e.g. "jwt_token") so a ciphertext copied into a different
     * field's slot fails to decrypt instead of silently succeeding. Falls back to returning
     * [plaintext] unchanged, un-prefixed, if crypto isn't available — see [init].
     */
    fun encrypt(
        plaintext: String,
        associatedData: String,
    ): String {
        val currentAead = aead ?: return plaintext
        return try {
            val ciphertext = currentAead.encrypt(plaintext.toByteArray(Charsets.UTF_8), associatedData.toByteArray(Charsets.UTF_8))
            ENCRYPTED_PREFIX + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypt failed — storing value unencrypted", e)
            plaintext
        }
    }

    /**
     * [associatedData] must match what [encrypt] was called with for this value. Distinguishes
     * three outcomes rather than a bare nullable String so callers can tell "never set"
     * ([Absent]) apart from "was set, can't be read anymore" ([Failed]) — the latter is the
     * new-device / Keystore-key-invalidated case TokenManager needs to self-heal from instead
     * of getting stuck. A value with no [ENCRYPTED_PREFIX] is a pre-migration plaintext value
     * and is returned as-is.
     */
    fun decryptOutcome(
        stored: String?,
        associatedData: String,
    ): DecryptOutcome {
        if (stored == null) return DecryptOutcome.Absent
        if (!isEncrypted(stored)) return DecryptOutcome.Success(stored)
        val currentAead = aead ?: return DecryptOutcome.Failed
        return try {
            val ciphertext = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            val plaintext = currentAead.decrypt(ciphertext, associatedData.toByteArray(Charsets.UTF_8))
            DecryptOutcome.Success(String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Decrypt failed for a stored secret — treating as unavailable", e)
            DecryptOutcome.Failed
        }
    }
}
