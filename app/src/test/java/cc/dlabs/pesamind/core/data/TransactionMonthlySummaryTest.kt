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
import java.util.Calendar
import java.util.UUID

/**
 * Real in-memory Room coverage for [TransactionRepository.observeMonthlySummary] — the
 * on-device replacement for `DashboardResponse.summary.data` (income/expense/savings/net
 * movement), computed from local transactions with no network round-trip. Same convention as
 * [ChannelSenderKeyDedupTest]/[YearlyBudgetRepositoryTest]: a real database, `runBlocking`
 * (never `runTest`, per `.claude/CLAUDE.md`'s Testing section).
 */
@RunWith(RobolectricTestRunner::class)
class TransactionMonthlySummaryTest {
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

    private fun millisFor(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    private suspend fun insertTransaction(
        amount: Double,
        type: String,
        createdAt: Long,
        channelId: String? = null,
        deletedAt: Long? = null,
    ) {
        db.transactionDao().upsert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                serverId = null,
                channelId = channelId,
                channelDetailsName = "Test Channel",
                amount = amount,
                type = type,
                note = "",
                username = "tester",
                smsSourceKey = null,
                providerTransactionId = null,
                syncStatus = SyncStatus.PENDING,
                dirty = true,
                createdAt = createdAt,
                updatedAt = createdAt,
                deletedAt = deletedAt,
            ),
        )
    }

    @Test
    fun `sums income expense and savings for the requested month only`() =
        runBlocking {
            insertTransaction(amount = 1000.0, type = TransactionTypes.INCOME, createdAt = millisFor(2026, 7, 5))
            insertTransaction(amount = 300.0, type = TransactionTypes.EXPENSE, createdAt = millisFor(2026, 7, 10))
            insertTransaction(amount = 100.0, type = TransactionTypes.SAVINGS, createdAt = millisFor(2026, 7, 20))
            // Different months — must not be counted in July's summary.
            insertTransaction(amount = 5000.0, type = TransactionTypes.INCOME, createdAt = millisFor(2026, 6, 30))
            insertTransaction(amount = 5000.0, type = TransactionTypes.INCOME, createdAt = millisFor(2026, 8, 1))

            val summary = TransactionRepository.observeMonthlySummary(2026, 7).first()

            assertEquals(1000L, summary.totalIncome)
            assertEquals(300L, summary.totalExpense)
            assertEquals(100L, summary.totalSavings)
            assertEquals("income - expense - savings, matching the backend's own formula", 600L, summary.netMovement)
            assertEquals(3, summary.transactionCount)
            assertEquals("2026-07", summary.currentMonth)
        }

    @Test
    fun `excludes soft-deleted transactions from the summary`() =
        runBlocking {
            insertTransaction(amount = 1000.0, type = TransactionTypes.INCOME, createdAt = millisFor(2026, 7, 5))
            insertTransaction(
                amount = 9999.0,
                type = TransactionTypes.INCOME,
                createdAt = millisFor(2026, 7, 6),
                deletedAt = millisFor(2026, 7, 6),
            )

            val summary = TransactionRepository.observeMonthlySummary(2026, 7).first()

            assertEquals(1000L, summary.totalIncome)
            assertEquals(1, summary.transactionCount)
        }

    @Test
    fun `active categories counts distinct channels used this month`() =
        runBlocking {
            val now = System.currentTimeMillis()
            db.channelDao().upsert(
                ChannelEntity(
                    id = "chan-1", serverId = null, userId = "u1", name = "MTN", channelType = "MOBILE_MONEY",
                    description = "", status = true, channelDesc = "MTN", normalizedSenderKey = "mtn",
                    availableBalance = 0.0,
                    accountNumber = null,
                    smsNotificationEnabled = true, syncStatus = SyncStatus.SYNCED, dirty = false,
                    createdAt = now, updatedAt = now, deletedAt = null,
                ),
            )
            db.channelDao().upsert(
                ChannelEntity(
                    id = "chan-2", serverId = null, userId = "u1", name = "Airtel", channelType = "MOBILE_MONEY",
                    description = "", status = true, channelDesc = "Airtel", normalizedSenderKey = "airtel",
                    availableBalance = 0.0,
                    accountNumber = null,
                    smsNotificationEnabled = true, syncStatus = SyncStatus.SYNCED, dirty = false,
                    createdAt = now, updatedAt = now, deletedAt = null,
                ),
            )

            insertTransaction(amount = 100.0, type = TransactionTypes.INCOME, createdAt = millisFor(2026, 7, 1), channelId = "chan-1")
            insertTransaction(amount = 100.0, type = TransactionTypes.EXPENSE, createdAt = millisFor(2026, 7, 2), channelId = "chan-1")
            insertTransaction(amount = 100.0, type = TransactionTypes.EXPENSE, createdAt = millisFor(2026, 7, 3), channelId = "chan-2")

            val summary = TransactionRepository.observeMonthlySummary(2026, 7).first()

            assertEquals("two distinct channels used, despite three transactions", 2, summary.activeCategories)
        }

    @Test
    fun `a month with no transactions returns an all-zero summary, not an error`() =
        runBlocking {
            val summary = TransactionRepository.observeMonthlySummary(2026, 7).first()

            assertEquals(0L, summary.totalIncome)
            assertEquals(0L, summary.totalExpense)
            assertEquals(0L, summary.totalSavings)
            assertEquals(0L, summary.netMovement)
            assertEquals(0, summary.transactionCount)
            assertEquals(0, summary.activeCategories)
        }
}
