package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.network.models.Account
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val Context.dataStore by preferencesDataStore("pesamind_account")

object AccountManager {
    private val ID = stringPreferencesKey("id")
    private val Email = stringPreferencesKey("email")
    private val Username = stringPreferencesKey("username")
    private val AvatarUrl = stringPreferencesKey("avatar_url")
    private val Balance = stringPreferencesKey("balance")
    private val Type = stringPreferencesKey("type")
    private val TrialExpiresAt = stringPreferencesKey("trial_expires_at")
    private val PremiumExpiresAt = stringPreferencesKey("premium_expires_at")

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
        trialExpiresAt: String? = null,
    ) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            it[ID] = id
            it[Email] = email
            it[Username] = username
            it[AvatarUrl] = avatarUrl
            it[Balance] = balance
            it[Type] = type
            if (trialExpiresAt != null) {
                it[TrialExpiresAt] = trialExpiresAt
            } else {
                it.remove(TrialExpiresAt)
            }
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
            trialExpiresAt = data[TrialExpiresAt],
            premiumExpiresAt = data[PremiumExpiresAt],
        )
    }

    /**
     * Records when a *paid* period ends, so "Premium — runs to 12 Oct" can be shown
     * without a network round-trip.
     *
     * Deliberately its own setter rather than another [saveAccount] parameter.
     * [saveAccount] removes the key when passed null, and Kotlin cannot tell "not
     * passed" from "passed null" — so a defaulted parameter would be silently wiped
     * by all five existing callers (`AuthViewModel` x3, `TokenRefreshInterceptor`,
     * `AccountViewModel`), none of which know anything about subscriptions.
     *
     * Null clears it, which is what a lapse back to Free must do.
     */
    suspend fun savePremiumExpiry(expiresAt: String?) {
        if (!isInitialized()) return
        appContext.dataStore.edit {
            if (expiresAt != null) {
                it[PremiumExpiresAt] = expiresAt
            } else {
                it.remove(PremiumExpiresAt)
            }
        }
    }

    /**
     * Whether the current account's plan is Premium or Enterprise (unlimited
     * tier). Free is the safe default on any failure (not initialized, no
     * account cached yet) — never assume Premium when the tier is unknown.
     * The server always sends the already-effective tier (see
     * `ToProfileDTO`/`EffectiveType` backend-side), so this never needs to
     * duplicate trial-expiry math itself — it just reads the cached `type`.
     */
    suspend fun isPremium(): Boolean =
        try {
            val type = getAccount().type
            type == "Premium" || type == "Enterprise"
        } catch (e: Exception) {
            false
        }

    /**
     * Days remaining in an active Premium trial, or `null` if the account
     * isn't on a trial (Free, paid Premium/Enterprise, or unparseable/missing
     * data). Floored at 0 rather than negative — a trial whose expiry has
     * technically passed but hasn't yet been re-synced from the server reads
     * as "0 days left," not a negative countdown.
     */
    suspend fun trialDaysRemaining(): Int? {
        val account =
            try {
                getAccount()
            } catch (e: Exception) {
                return null
            }
        val expiresAt = account.trialExpiresAt ?: return null
        val expiry =
            try {
                OffsetDateTime.parse(expiresAt).toInstant()
            } catch (e: Exception) {
                return null
            }
        val daysLeft = ChronoUnit.DAYS.between(Instant.now(), expiry)
        return daysLeft.coerceAtLeast(0).toInt()
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
            it.remove(TrialExpiresAt)
            it.remove(PremiumExpiresAt)
        }
    }
}
