package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.features.settings.channels.ChannelLimits
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
 * Covers [ChannelRepository.createChannel]'s Free-tier per-type channel caps (see
 * [ChannelLimits]) — Premium/Enterprise remain unlimited. Real in-memory Room DB
 * (Robolectric-provided [Context]), same pattern as [ChannelSenderKeyDedupTest].
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
    fun `Free tier rejects a bank channel beyond the cap`() =
        runBlocking {
            setTier("Free")
            val first = ChannelRepository.createChannel("Bank1", "b1", ChannelTypes.BANK, "Stanbic Bank", true)
            val second = ChannelRepository.createChannel("Bank2", "b2", ChannelTypes.BANK, "Equity Bank", true)
            val third = ChannelRepository.createChannel("Bank3", "b3", ChannelTypes.BANK, "DFCU Bank", true)

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(second is ChannelCreateOutcome.Created)
            assertEquals(
                ChannelCreateOutcome.LimitReached(ChannelTypes.BANK, ChannelLimits.FREE_BANK_LIMIT),
                third,
            )
            assertEquals(
                ChannelLimits.FREE_BANK_LIMIT,
                db.channelDao().countByUserIdAndChannelType(AccountManager.currentUserIdOrEmpty(), ChannelTypes.BANK),
            )
        }

    @Test
    fun `Free tier rejects a mobile money channel beyond the cap`() =
        runBlocking {
            setTier("Free")
            val first = ChannelRepository.createChannel("MTN", "mtn", ChannelTypes.MOBILE_MONEY, "MTN Mobile Money", true)
            val second = ChannelRepository.createChannel("Airtel", "airtel", ChannelTypes.MOBILE_MONEY, "Airtel Money", true)
            val third = ChannelRepository.createChannel("Other", "other", ChannelTypes.MOBILE_MONEY, "Other Momo", true)

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(second is ChannelCreateOutcome.Created)
            assertEquals(
                ChannelCreateOutcome.LimitReached(ChannelTypes.MOBILE_MONEY, ChannelLimits.FREE_MOBILE_MONEY_LIMIT),
                third,
            )
        }

    @Test
    fun `Free tier rejects a cash channel beyond the cap`() =
        runBlocking {
            setTier("Free")
            val first = ChannelRepository.createChannel("Cash", "cash", ChannelTypes.CASH, "", true)
            val second = ChannelRepository.createChannel("Cash 2", "cash2", ChannelTypes.CASH, "", true)

            assertTrue(first is ChannelCreateOutcome.Created)
            assertEquals(
                ChannelCreateOutcome.LimitReached(ChannelTypes.CASH, ChannelLimits.FREE_CASH_LIMIT),
                second,
            )
        }

    @Test
    fun `Premium tier is unlimited across all channel types`() =
        runBlocking {
            setTier("Premium")
            repeat(5) { i ->
                val bank = ChannelRepository.createChannel("Bank$i", "b$i", ChannelTypes.BANK, "Bank Desc $i", true)
                assertTrue(bank is ChannelCreateOutcome.Created)
            }
            repeat(3) { i ->
                val momo = ChannelRepository.createChannel("Momo$i", "m$i", ChannelTypes.MOBILE_MONEY, "Momo Desc $i", true)
                assertTrue(momo is ChannelCreateOutcome.Created)
            }
            val cash1 = ChannelRepository.createChannel("Cash1", "c1", ChannelTypes.CASH, "", true)
            val cash2 = ChannelRepository.createChannel("Cash2", "c2", ChannelTypes.CASH, "", true)
            assertTrue(cash1 is ChannelCreateOutcome.Created)
            assertTrue(cash2 is ChannelCreateOutcome.Created)
        }

    @Test
    fun `Upgrading mid-session lifts a previously hit cap`() =
        runBlocking {
            setTier("Free")
            ChannelRepository.createChannel("Cash", "cash", ChannelTypes.CASH, "", true)
            val blocked = ChannelRepository.createChannel("Cash 2", "cash2", ChannelTypes.CASH, "", true)
            assertTrue(blocked is ChannelCreateOutcome.LimitReached)

            setTier("Premium")
            val afterUpgrade = ChannelRepository.createChannel("Cash 3", "cash3", ChannelTypes.CASH, "", true)
            assertTrue(afterUpgrade is ChannelCreateOutcome.Created)
        }
}
