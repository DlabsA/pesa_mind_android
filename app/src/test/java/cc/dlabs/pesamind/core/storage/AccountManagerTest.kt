package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.OffsetDateTime

/**
 * Covers [AccountManager.isPremium]/[AccountManager.trialDaysRemaining] — the two derived-tier
 * helpers every Free/Premium gating call site in the app reads from. Backed by a real
 * DataStore-backed [Context] (Robolectric-provided, not a mock), matching this repo's testing
 * rule: plain [runBlocking], never `kotlinx.coroutines.test.runTest` (see `.claude/CLAUDE.md`'s
 * "Testing" section — `AccountManager` has no network path today, but the same discipline keeps
 * this file consistent with every other manager test in the repo).
 */
@RunWith(RobolectricTestRunner::class)
class AccountManagerTest {
    @Before
    fun setUp() {
        AccountManager.init(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { AccountManager.clearAccount() }
    }

    // AccountManager is a process-lifetime singleton (no reset for `isInitialized()`), so once
    // this class calls `init()`, `currentUserIdOrEmpty()` no longer swallows to "" for the rest
    // of the shared test JVM — other test classes that deliberately never call `init()` (e.g.
    // TransactionMonthlySummaryTest) rely on that swallow-to-empty behavior. Clearing here
    // leaves `getAccount()` resolving to an empty account again, restoring that behavior for
    // whatever test class runs next.
    @After
    fun tearDown() {
        runBlocking { AccountManager.clearAccount() }
    }

    @Test
    fun `isPremium is false with no account saved`() =
        runBlocking {
            assertFalse(AccountManager.isPremium())
        }

    @Test
    fun `isPremium is false for Free`() =
        runBlocking {
            saveAccountWithType("Free")
            assertFalse(AccountManager.isPremium())
        }

    @Test
    fun `isPremium is true for Premium`() =
        runBlocking {
            saveAccountWithType("Premium")
            assertTrue(AccountManager.isPremium())
        }

    @Test
    fun `isPremium is true for Enterprise`() =
        runBlocking {
            saveAccountWithType("Enterprise")
            assertTrue(AccountManager.isPremium())
        }

    @Test
    fun `trialDaysRemaining is null with no trial expiry saved`() =
        runBlocking {
            saveAccountWithType("Premium", trialExpiresAt = null)
            assertNull(AccountManager.trialDaysRemaining())
        }

    @Test
    fun `trialDaysRemaining reflects a future expiry`() =
        runBlocking {
            val expiry = OffsetDateTime.now().plusDays(29).plusHours(1)
            saveAccountWithType("Premium", trialExpiresAt = expiry.toString())
            val days = AccountManager.trialDaysRemaining()
            assertEquals(29, days)
        }

    @Test
    fun `trialDaysRemaining floors at zero rather than going negative`() =
        runBlocking {
            val expiry = OffsetDateTime.now().minusDays(5)
            saveAccountWithType("Premium", trialExpiresAt = expiry.toString())
            assertEquals(0, AccountManager.trialDaysRemaining())
        }

    @Test
    fun `trialDaysRemaining is null for an unparseable expiry string`() =
        runBlocking {
            saveAccountWithType("Premium", trialExpiresAt = "not-a-date")
            assertNull(AccountManager.trialDaysRemaining())
        }

    private suspend fun saveAccountWithType(
        type: String,
        trialExpiresAt: String? = null,
    ) {
        AccountManager.saveAccount(
            id = "user-1",
            email = "test@example.com",
            username = "testuser",
            avatarUrl = "",
            balance = "0.0",
            type = type,
            trialExpiresAt = trialExpiresAt,
        )
    }
}
