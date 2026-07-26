package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real in-memory Room coverage for [MonthlyBudgetRepository] (ADR-0006), same convention as
 * [YearlyBudgetRepositoryTest]. Focused on the FK-to-yearly-budget resolution, since that's the
 * one piece of behavior this repository has that [YearlyBudgetRepositoryTest] can't cover.
 */
@RunWith(RobolectricTestRunner::class)
class MonthlyBudgetRepositoryTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        MonthlyBudgetRepository.database = db
        YearlyBudgetRepository.database = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adding a line item with no existing yearly budget auto-creates the yearly parent`() =
        runBlocking {
            val result = MonthlyBudgetRepository.addLineItem(month = 6, year = 2026, name = "Salary", amount = 3000.0, type = "income")

            assertEquals(1, result.transactions.size)
            val monthlyEntity = db.monthlyBudgetDao().getByMonthYear(6, 2026)!!
            assertNotNull("a monthly budget's yearlyBudgetId FK must be populated immediately, not left null", monthlyEntity.yearlyBudgetId)

            val yearlyEntity = db.yearlyBudgetDao().getById(monthlyEntity.yearlyBudgetId!!)
            assertNotNull("the auto-created yearly parent must actually exist locally", yearlyEntity)
            assertEquals(2026L, yearlyEntity?.year)

            // The yearly parent gets its own CREATE outbox row independent of the monthly one.
            assertEquals(OutboxOperation.CREATE, db.outboxDao().findFor(OutboxEntityType.YEARLY_BUDGET, yearlyEntity!!.id)?.operation)
            assertEquals(OutboxOperation.CREATE, db.outboxDao().findFor(OutboxEntityType.MONTHLY_BUDGET, monthlyEntity.id)?.operation)
        }

    @Test
    fun `adding a line item reuses an already-resolved yearly parent instead of duplicating it`() =
        runBlocking {
            YearlyBudgetRepository.resolveOrCreateLocalId(2026)
            MonthlyBudgetRepository.addLineItem(month = 3, year = 2026, name = "Rent", amount = 800.0, type = "expense")

            assertEquals(
                "only one yearly budget row must exist for 2026, not a second one from the monthly add",
                1,
                db.yearlyBudgetDao().getAllIncludingDeleted().size,
            )
        }

    @Test
    fun `adding a second line item to an unsynced monthly budget keeps the outbox row CREATE`() =
        runBlocking {
            MonthlyBudgetRepository.addLineItem(month = 6, year = 2026, name = "Salary", amount = 3000.0, type = "income")
            val result = MonthlyBudgetRepository.addLineItem(month = 6, year = 2026, name = "Groceries", amount = 200.0, type = "expense")

            assertEquals(2, result.transactions.size)
            val entity = db.monthlyBudgetDao().getByMonthYear(6, 2026)!!
            assertEquals(OutboxOperation.CREATE, db.outboxDao().findFor(OutboxEntityType.MONTHLY_BUDGET, entity.id)?.operation)
        }

    @Test
    fun `deleting an already-synced line item flags it for delete instead of removing it locally`() =
        runBlocking {
            val created = MonthlyBudgetRepository.addLineItem(month = 6, year = 2026, name = "Rent", amount = 800.0, type = "expense")
            val txId = created.transactions.first().id
            val entity = db.monthlyBudgetDao().getByMonthYear(6, 2026)!!
            // Simulate this specific line item having already synced once.
            val syncedItems = parseLineItems(entity.transactionsJson).map { it.copy(serverId = "srv-line-1", pendingAction = null) }
            db.monthlyBudgetDao().update(entity.copy(transactionsJson = syncedItems.toTransactionsJson()))

            val result = MonthlyBudgetRepository.deleteLineItem(month = 6, year = 2026, lineItemId = "srv-line-1")

            assertEquals(
                "a pending-delete item must be hidden from the UI-facing response immediately",
                0,
                result?.transactions?.size,
            )
            val rawItems = parseLineItems(db.monthlyBudgetDao().getByMonthYear(6, 2026)!!.transactionsJson)
            assertEquals("the row itself must still exist locally so a push can read its serverId", 1, rawItems.size)
            assertEquals("delete", rawItems.first().pendingAction)
        }

    @Test
    fun `reconcileFromServer resolves the local yearlyBudgetId from the caller-supplied lookup`() =
        runBlocking {
            val yearlyLocalId = YearlyBudgetRepository.resolveOrCreateLocalId(2026)

            val serverPayload =
                cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse(
                    id = "srv-mb-1",
                    userId = "user-1",
                    yearlyBudgetId = "srv-yb-1",
                    month = 5,
                    year = 2026,
                    totalExpenditures = 0,
                    totalIncome = 1200,
                    totalSavings = 0,
                    totalTransactions = 0,
                    transactions = emptyList(),
                    createdAt = "2026-05-01T00:00:00Z",
                    updatedAt = "2026-05-01T00:00:00Z",
                )

            val result = MonthlyBudgetRepository.reconcileFromServer(serverPayload, resolvedYearlyLocalId = yearlyLocalId)

            assertEquals(1200L, result.totalIncome)
            assertEquals(yearlyLocalId, db.monthlyBudgetDao().getByMonthYear(5, 2026)?.yearlyBudgetId)
        }

    @Test
    fun `reconcileFromServer with no resolvable yearly parent leaves the FK null rather than guessing`() =
        runBlocking {
            val serverPayload =
                cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse(
                    id = "srv-mb-2",
                    userId = "user-1",
                    yearlyBudgetId = "srv-yb-does-not-exist-locally",
                    month = 8,
                    year = 2026,
                    totalExpenditures = 0,
                    totalIncome = 0,
                    totalSavings = 0,
                    totalTransactions = 0,
                    transactions = emptyList(),
                    createdAt = "2026-08-01T00:00:00Z",
                    updatedAt = "2026-08-01T00:00:00Z",
                )

            MonthlyBudgetRepository.reconcileFromServer(serverPayload, resolvedYearlyLocalId = null)

            assertNull(db.monthlyBudgetDao().getByMonthYear(8, 2026)?.yearlyBudgetId)
        }
}
