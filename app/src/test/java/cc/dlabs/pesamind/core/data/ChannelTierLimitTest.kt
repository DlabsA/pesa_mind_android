package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [ChannelRepository.createChannel]'s tier handling — there is no channel-count limit
 * for any tier, Free included. Real in-memory Room DB (Robolectric-provided [Context]), same
 * pattern as [ChannelSenderKeyDedupTest].
 */
@RunWith(RobolectricTestRunner::class)
class ChannelTierLimitTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ChannelRepository.database = db
        AccountManager.init(context)
        runBlocking { AccountManager.clearAccount() }
    }

    // See AccountManagerTest's tearDown doc comment: AccountManager is a process-lifetime
    // singleton with no reset for `isInitialized()`, so this class must leave it cleared for
    // whatever test class runs next in the shared test JVM (e.g. TransactionMonthlySummaryTest,
    // which relies on AccountManager never having been initialized).
    @After
    fun tearDown() {
        db.close()
        runBlocking { AccountManager.clearAccount() }
    }

    private suspend fun setTier(type: String) {
        AccountManager.saveAccount(
            id = "user-1",
            email = "test@example.com",
            username = "testuser",
            avatarUrl = "",
            balance = "0.0",
            type = type,
        )
    }

    @Test
    fun `Free tier allows more than 3 channels total`() =
        runBlocking {
            setTier("Free")
            ChannelRepository.createChannel("Cash", "cash", ChannelTypes.CASH, "", true)
            ChannelRepository.createChannel("Bank1", "b1", ChannelTypes.BANK, "Stanbic Bank", true)
            ChannelRepository.createChannel("Bank2", "b2", ChannelTypes.BANK, "Equity Bank", true)
            val fourth = ChannelRepository.createChannel("Bank3", "b3", ChannelTypes.BANK, "DFCU Bank", true)

            assertTrue(fourth is ChannelCreateOutcome.Created)
            assertEquals(4, db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()))
        }

    @Test
    fun `Free tier allows more than 1 mobile money channel`() =
        runBlocking {
            setTier("Free")
            val first = ChannelRepository.createChannel("MTN", "mtn", ChannelTypes.MOBILE_MONEY, "MTN Mobile Money", true)
            val second = ChannelRepository.createChannel("Airtel", "airtel", ChannelTypes.MOBILE_MONEY, "Airtel Money", true)

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(second is ChannelCreateOutcome.Created)
        }

    @Test
    fun `Premium tier is unlimited`() =
        runBlocking {
            setTier("Premium")
            repeat(5) { i ->
                val outcome =
                    ChannelRepository.createChannel("Bank$i", "b$i", ChannelTypes.BANK, "Bank Desc $i", true)
                assertTrue(outcome is ChannelCreateOutcome.Created)
            }
        }
}
