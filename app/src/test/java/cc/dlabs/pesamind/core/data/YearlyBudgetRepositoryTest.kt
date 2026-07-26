package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
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

/**
 * Real in-memory Room coverage for [YearlyBudgetRepository] (ADR-0006) — same convention as
 * [ChannelSenderKeyDedupTest]: a real database exercises the actual unique-index/upsert
 * behavior, not a mock. `runBlocking`, never `runTest`, per `.claude/CLAUDE.md`'s Testing
 * section landmine.
 */
@RunWith(RobolectricTestRunner::class)
class YearlyBudgetRepositoryTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        YearlyBudgetRepository.database = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adding a line item to a brand-new year writes a CREATE outbox row`() =
        runBlocking {
            val result = YearlyBudgetRepository.addLineItem(year = 2026, name = "Salary", amount = 5000.0, type = "income")

            assertEquals(1, result.transactions.size)
            assertEquals(5000L, result.totalIncome)

            val entity = db.yearlyBudgetDao().getByYear(2026)!!
            assertTrue(entity.dirty)
            assertEquals(SyncStatus.PENDING, entity.syncStatus)
            val outbox = db.outboxDao().findFor(OutboxEntityType.YEARLY_BUDGET, entity.id)
            assertEquals(OutboxOperation.CREATE, outbox?.operation)
        }

    @Test
    fun `adding a second line item to an unsynced budget keeps the outbox row CREATE, not UPDATE`() =
        runBlocking {
            YearlyBudgetRepository.addLineItem(year = 2026, name = "Salary", amount = 5000.0, type = "income")
            val result = YearlyBudgetRepository.addLineItem(year = 2026, name = "Rent", amount = 800.0, type = "expense")

            assertEquals(2, result.transactions.size)
            val entity = db.yearlyBudgetDao().getByYear(2026)!!
            val outbox = db.outboxDao().findFor(OutboxEntityType.YEARLY_BUDGET, entity.id)
            assertEquals(
                "OutboxCoalescer must keep a never-synced row's outbox operation CREATE, not UPDATE",
                OutboxOperation.CREATE,
                outbox?.operation,
            )
            assertEquals(1, db.outboxDao().getByStatus(SyncStatus.PENDING).size)
        }

    @Test
    fun `adding a line item to an already-synced budget coalesces the outbox to UPDATE`() =
        runBlocking {
            // Simulate a budget that already synced once (what SyncWorker leaves behind after
            // a successful push) — no PENDING outbox row, entity has a serverId, not dirty.
            YearlyBudgetRepository.addLineItem(year = 2026, name = "Salary", amount = 5000.0, type = "income")
            val entity = db.yearlyBudgetDao().getByYear(2026)!!
            db.outboxDao().delete(db.outboxDao().findFor(OutboxEntityType.YEARLY_BUDGET, entity.id)!!.id)
            db.yearlyBudgetDao().update(entity.copy(serverId = "srv-yb-1", dirty = false, syncStatus = SyncStatus.SYNCED))

            YearlyBudgetRepository.addLineItem(year = 2026, name = "Rent", amount = 800.0, type = "expense")

            val outbox = db.outboxDao().findFor(OutboxEntityType.YEARLY_BUDGET, entity.id)
            assertEquals(OutboxOperation.UPDATE, outbox?.operation)
        }

    @Test
    fun `deleting a never-synced line item removes it with no server round trip needed`() =
        runBlocking {
            val created = YearlyBudgetRepository.addLineItem(year = 2026, name = "Coffee", amount = 5.0, type = "expense")
            val txId = created.transactions.first().id

            val result = YearlyBudgetRepository.deleteLineItem(year = 2026, lineItemId = txId)

            assertEquals(0, result?.transactions?.size)
            assertEquals(0L, result?.totalExpenditures)
        }

    @Test
    fun `resolveOrCreateLocalId reuses an existing row instead of creating a duplicate`() =
        runBlocking {
            val firstId = YearlyBudgetRepository.resolveOrCreateLocalId(2026)
            val secondId = YearlyBudgetRepository.resolveOrCreateLocalId(2026)

            assertEquals(firstId, secondId)
            assertEquals(1, db.yearlyBudgetDao().getAllIncludingDeleted().size)
        }

    @Test
    fun `reconcileFromServer never overwrites a dirty local row`() =
        runBlocking {
            YearlyBudgetRepository.addLineItem(year = 2026, name = "Local edit", amount = 10.0, type = "income")
            val local = db.yearlyBudgetDao().getByYear(2026)!!
            db.yearlyBudgetDao().update(local.copy(serverId = "srv-yb-2"))

            val serverPayload =
                YearlyBudgetResponse(
                    id = "srv-yb-2",
                    userId = "user-1",
                    year = 2026,
                    totalExpenditures = 0,
                    totalIncome = 99999,
                    totalSavings = 0,
                    totalTransactions = 5,
                    transactions = emptyList(),
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z",
                )

            val result = YearlyBudgetRepository.reconcileFromServer(serverPayload)

            assertFalse("a pull must never silently clobber a dirty local row", result.totalIncome == 99999L)
        }

    @Test
    fun `reconcileFromServer inserts a genuinely new server row`() =
        runBlocking {
            val serverPayload =
                YearlyBudgetResponse(
                    id = "srv-yb-3",
                    userId = "user-1",
                    year = 2027,
                    totalExpenditures = 0,
                    totalIncome = 1000,
                    totalSavings = 0,
                    totalTransactions = 0,
                    transactions = emptyList(),
                    createdAt = "2027-01-01T00:00:00Z",
                    updatedAt = "2027-01-01T00:00:00Z",
                )

            val result = YearlyBudgetRepository.reconcileFromServer(serverPayload)

            assertEquals(1000L, result.totalIncome)
            assertEquals(1, db.yearlyBudgetDao().getAllIncludingDeleted().size)
        }

    @Test
    fun `applyPushCompletion clears dirty and stores the confirmed serverId`() =
        runBlocking {
            YearlyBudgetRepository.addLineItem(year = 2026, name = "Salary", amount = 5000.0, type = "income")
            val entity = db.yearlyBudgetDao().getByYear(2026)!!
            val items = parseLineItems(entity.transactionsJson)
            val responseItems = items.map { it.copy(serverId = "srv-line-1", pendingAction = null) }

            YearlyBudgetRepository.applyPushCompletion(
                entityId = entity.id,
                serverId = "srv-yb-9",
                dispatched = items,
                responseItems = responseItems,
                clearDirty = true,
            )

            val updated = db.yearlyBudgetDao().getById(entity.id)!!
            assertFalse(updated.dirty)
            assertEquals(SyncStatus.SYNCED, updated.syncStatus)
            assertEquals("srv-yb-9", updated.serverId)
            assertNull(parseLineItems(updated.transactionsJson).first().pendingAction)
        }
}
