package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.utils.TransactionTypes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Covers [TransactionRepository.observeTopChannelsBySpend] (Analytics "Top Channels" card).
 * Previously grouped by the free-text `channelDetailsName` snapshot instead of `channelId`, so
 * two distinct channels sharing a name/description (e.g. two "MTN Mobile Money" channels) had
 * their spend merged into a single bar — same bug family as
 * [TransactionReconcileChannelIdTest], different symptom (display merge, not misattribution).
 */
@RunWith(RobolectricTestRunner::class)
class TransactionTopChannelsTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        TransactionRepository.database = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertChannel(
        id: String,
        name: String,
        // Two same-desc channels (e.g. dual-SIM MTN Mobile Money) are only distinguishable by
        // receiving number, matching ChannelEntity's real unique index — not by name/desc alone.
        receivingNumber: String = id,
    ) {
        db.channelDao().upsert(
            ChannelEntity(
                id = id,
                serverId = null,
                userId = "",
                name = name,
                channelType = "MOBILE_MONEY",
                description = "",
                status = true,
                channelDesc = name,
                normalizedSenderKey = name.lowercase(),
                receivingNumber = receivingNumber,
                availableBalance = 0.0,
                accountNumber = null,
                smsNotificationEnabled = true,
                syncStatus = SyncStatus.SYNCED,
                dirty = false,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
    }

    private suspend fun insertExpense(
        channelId: String,
        channelDetailsName: String,
        amount: Double,
    ) {
        db.transactionDao().upsert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                userId = "",
                channelId = channelId,
                channelDetailsName = channelDetailsName,
                amount = amount,
                type = TransactionTypes.EXPENSE,
                note = "",
                username = "tester",
                smsSourceKey = null,
                providerTransactionId = null,
                syncStatus = SyncStatus.PENDING,
                dirty = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deletedAt = null,
            ),
        )
    }

    @Test
    fun `two channels sharing a name are kept as separate entries, not merged`() =
        runBlocking {
            val mainMtnId = UUID.randomUUID().toString()
            val otherMtnId = UUID.randomUUID().toString()
            insertChannel(mainMtnId, "MTN Mobile Money")
            insertChannel(otherMtnId, "MTN Mobile Money")
            insertExpense(mainMtnId, "MTN Mobile Money", 1000.0)
            insertExpense(otherMtnId, "MTN Mobile Money", 4000.0)

            val ranking = TransactionRepository.observeTopChannelsBySpend(null, null).first()

            assertEquals(
                "same-named channels must stay separate entries, keyed by channelId",
                2,
                ranking.size,
            )
            assertEquals(4000.0, ranking[0].totalExpense, 0.0)
            assertEquals(1000.0, ranking[1].totalExpense, 0.0)
        }

    @Test
    fun `two transactions on the same channel are still summed together`() =
        runBlocking {
            val channelId = UUID.randomUUID().toString()
            insertChannel(channelId, "Airtel Money")
            insertExpense(channelId, "Airtel Money", 500.0)
            insertExpense(channelId, "Airtel Money", 250.0)

            val ranking = TransactionRepository.observeTopChannelsBySpend(null, null).first()

            assertEquals(1, ranking.size)
            assertEquals(750.0, ranking[0].totalExpense, 0.0)
            assertEquals(2, ranking[0].transactionCount)
        }
}
