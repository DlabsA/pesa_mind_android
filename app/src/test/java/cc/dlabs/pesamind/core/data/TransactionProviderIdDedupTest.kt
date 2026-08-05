package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Covers the SMS-transaction-dedup half of the fix: Airtel Uganda (confirmed) sends two
 * *different* SMS bodies for one real transaction sharing a provider TID, which a
 * content-derived `smsSourceKey` alone cannot catch — [TransactionEntity.providerTransactionId]
 * plus the `(channelId, providerTransactionId)` unique index is what closes that gap. Backed by
 * a real in-memory Room database (Robolectric-provided [Context], not a mock) so the actual
 * `INSERT ... ON CONFLICT IGNORE` outcome is what every assertion here is based on, not a
 * pre-insert SELECT that a concurrent caller could race.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionProviderIdDedupTest {
    private lateinit var db: PesaMindDatabase
    private lateinit var channelId: String

    @Before
    fun setUp() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            TransactionRepository.database = db

            channelId = UUID.randomUUID().toString()
            db.channelDao().insertIgnore(
                ChannelEntity(
                    id = channelId,
                    serverId = null,
                    userId = "user-1",
                    name = "Airtel Money",
                    channelType = "MOBILE_MONEY",
                    description = "0700000000",
                    status = true,
                    channelDesc = "Airtel Money",
                    normalizedSenderKey = "airtel money",
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
            Unit
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `two SMS bodies sharing one provider TID yield exactly one transaction row`() =
        runBlocking {
            val first =
                TransactionRepository.createTransaction(
                    channelId = channelId,
                    amount = 50000.0,
                    type = "expense",
                    note = "SENT.TID 998877.UGX 50,000 to John",
                    username = "jane",
                    smsSourceKey = "airtel:1000:111",
                    providerTransactionId = "998877",
                )

            val second =
                TransactionRepository.createTransaction(
                    channelId = channelId,
                    amount = 50000.0,
                    type = "expense",
                    note = "You have sent UGX 50,000. TID: 998877",
                    username = "jane",
                    // Different content-derived key (a genuinely different SMS body/timestamp) —
                    // only the shared providerTransactionId should catch this pair.
                    smsSourceKey = "airtel:1001:222",
                    providerTransactionId = "998877",
                )

            assertTrue("first call for a new TID must insert a new row", first is TransactionInsertOutcome.Inserted)
            assertTrue(
                "second call sharing the same (channelId, providerTransactionId) must be discarded as a duplicate",
                second is TransactionInsertOutcome.DuplicateDiscarded,
            )
            assertEquals(
                (first as TransactionInsertOutcome.Inserted).transaction.id,
                (second as TransactionInsertOutcome.DuplicateDiscarded).existing.id,
            )

            val rowCount = db.transactionDao().getAllIncludingDeleted().count { it.channelId == channelId }
            assertEquals("exactly one transaction row must exist for this TID, not two", 1, rowCount)
        }

    @Test
    fun `raw insertIgnore returns -1 for a second insert sharing (channelId, providerTransactionId)`() =
        runBlocking {
            val dao = db.transactionDao()
            val entity1 = transactionEntity(providerTransactionId = "TID-1")
            val entity2 = transactionEntity(providerTransactionId = "TID-1")

            val rowId1 = dao.insertIgnore(entity1)
            val rowId2 = dao.insertIgnore(entity2)

            assertNotEquals("the first insert for a fresh TID must succeed", -1L, rowId1)
            assertEquals(
                "a second raw insert sharing (channelId, providerTransactionId) must be discarded by the unique index",
                -1L,
                rowId2,
            )
            assertEquals(1, dao.getAllIncludingDeleted().count { it.providerTransactionId == "TID-1" })
        }

    @Test
    fun `a message with no extractable TID still dedupes via the smsSourceKey fallback`() =
        runBlocking {
            val first =
                TransactionRepository.createTransaction(
                    channelId = channelId,
                    amount = 20000.0,
                    type = "income",
                    note = "You have received UGX 20,000",
                    username = "jane",
                    smsSourceKey = "mtn:2000:555",
                    providerTransactionId = null,
                )
            val second =
                TransactionRepository.createTransaction(
                    channelId = channelId,
                    amount = 20000.0,
                    type = "income",
                    note = "You have received UGX 20,000",
                    username = "jane",
                    // Same fallback key — reprocessing/redelivery of the exact same SMS.
                    smsSourceKey = "mtn:2000:555",
                    providerTransactionId = null,
                )

            assertTrue("first call must insert a new row", first is TransactionInsertOutcome.Inserted)
            assertTrue(
                "second call sharing the same smsSourceKey (no TID either time) must be discarded as a duplicate",
                second is TransactionInsertOutcome.DuplicateDiscarded,
            )

            val rowCount = db.transactionDao().getAllIncludingDeleted().count { it.smsSourceKey == "mtn:2000:555" }
            assertEquals(1, rowCount)
        }

    @Test
    fun `two near-simultaneous creates for the same TID leave exactly one row after the race`() =
        runBlocking {
            // Both coroutines run on Dispatchers.IO's real thread pool (not a single test
            // thread taking turns) and both attempt createTransaction — including its own
            // pre-check — before either has necessarily committed. A check-then-insert bug
            // (no real unique index / no atomic INSERT ... ON CONFLICT IGNORE) could let both
            // pre-checks miss and both inserts "succeed" against a mock; against this real
            // in-memory SQLite database, only one can actually win.
            val outcomes =
                listOf(
                    async(Dispatchers.IO) {
                        TransactionRepository.createTransaction(
                            channelId = channelId,
                            amount = 15000.0,
                            type = "expense",
                            note = "message A",
                            username = "jane",
                            smsSourceKey = "airtel:race:A",
                            providerTransactionId = "RACE-TID",
                        )
                    },
                    async(Dispatchers.IO) {
                        TransactionRepository.createTransaction(
                            channelId = channelId,
                            amount = 15000.0,
                            type = "expense",
                            note = "message B",
                            username = "jane",
                            smsSourceKey = "airtel:race:B",
                            providerTransactionId = "RACE-TID",
                        )
                    },
                ).awaitAll()

            val insertedCount = outcomes.count { it is TransactionInsertOutcome.Inserted }
            val duplicateCount = outcomes.count { it is TransactionInsertOutcome.DuplicateDiscarded }
            assertEquals("exactly one of the two concurrent creates must win the insert", 1, insertedCount)
            assertEquals(
                "the other concurrent create must be discarded as a duplicate, not silently succeed",
                1,
                duplicateCount,
            )

            val survivingRows = db.transactionDao().getAllIncludingDeleted().count { it.providerTransactionId == "RACE-TID" }
            assertEquals("exactly one row must survive the race in the actual table", 1, survivingRows)
        }

    private fun transactionEntity(
        providerTransactionId: String?,
        smsSourceKey: String? = null,
    ) = TransactionEntity(
        id = UUID.randomUUID().toString(),
        serverId = null,
        // Matches what AccountManager.currentUserIdOrEmpty() resolves to in this test (no
        // AccountManager.init() call), same as every row TransactionRepository.createTransaction
        // creates elsewhere in this file.
        userId = "",
        channelId = channelId,
        channelDetailsName = "Airtel Money",
        amount = 1000.0,
        type = "expense",
        note = "note",
        username = "jane",
        smsSourceKey = smsSourceKey,
        providerTransactionId = providerTransactionId,
        syncStatus = SyncStatus.PENDING,
        dirty = true,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
