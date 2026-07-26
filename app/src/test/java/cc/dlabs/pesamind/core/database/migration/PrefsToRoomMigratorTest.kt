package cc.dlabs.pesamind.core.database.migration

import cc.dlabs.pesamind.core.data.parseLineItems
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for the DataStore -> Room conversion logic — the part of the
 * migration most likely to silently mis-map data, since it runs with no Android/Room
 * runtime available in a plain JVM unit test. Transactional/idempotency behavior
 * (db.withTransaction, the "already populated" guard) needs a real SQLite instance and
 * is exercised manually per the airplane-mode acceptance test in ADR-0004 instead.
 */
class PrefsToRoomMigratorTest {
    private fun channel(
        id: String = "server-1",
        name: String = "MTN Line",
        smsEnabled: Boolean = true,
    ) = ChannelDetails(
        id = id,
        userId = "user-1",
        name = name,
        channelType = "MOBILE_MONEY",
        description = "desc",
        status = true,
        channelDesc = "MTN",
        smsNotificationEnabled = smsEnabled,
    )

    @Test
    fun `channel conversion preserves every field and generates a fresh local id`() {
        val dto = channel()
        val entity = dto.toEntity(now = 1000L)

        assertEquals(dto.id, entity.serverId)
        assertEquals(dto.userId, entity.userId)
        assertEquals(dto.name, entity.name)
        assertEquals(dto.channelType, entity.channelType)
        assertEquals(dto.description, entity.description)
        assertEquals(dto.status, entity.status)
        assertEquals(dto.channelDesc, entity.channelDesc)
        assertEquals(dto.smsNotificationEnabled, entity.smsNotificationEnabled)
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
        assertTrue("migrated rows must not be dirty", !entity.dirty)
        assertNull("migrated rows have no soft-delete", entity.deletedAt)
        assertTrue("local id must be a real UUID, not blank/echoed server id", entity.id.isNotBlank() && entity.id != dto.id)
    }

    @Test
    fun `two channels never collide on local id`() {
        val a = channel(id = "server-1").toEntity(now = 1000L)
        val b = channel(id = "server-2").toEntity(now = 1000L)
        assertTrue(a.id != b.id)
    }

    @Test
    fun `unique channel name resolves to that channel's local id`() {
        val mtn = channel(id = "s1", name = "MTN Line").toEntity(now = 1000L)
        val airtel = channel(id = "s2", name = "Airtel Line").toEntity(now = 1000L)

        val resolved = resolveUniqueChannelIdsByName(listOf(mtn, airtel))

        assertEquals(mtn.id, resolved["MTN Line"])
        assertEquals(airtel.id, resolved["Airtel Line"])
    }

    @Test
    fun `ambiguous channel name resolves to nothing rather than guessing`() {
        val first = channel(id = "s1", name = "MTN Line").toEntity(now = 1000L)
        val second = channel(id = "s2", name = "MTN Line").toEntity(now = 1000L)

        val resolved = resolveUniqueChannelIdsByName(listOf(first, second))

        assertTrue("an ambiguous name must not appear in the map at all", !resolved.containsKey("MTN Line"))
    }

    @Test
    fun `transaction with a resolvable channel name gets the matching local channel id`() {
        val dto =
            TransactionDetails(
                id = "tx-1",
                amount = 5000.0,
                type = "expense",
                note = "lunch",
                channelDetailsName = "MTN Line",
                username = "jane",
            )

        val entity = dto.toEntity(now = 1000L, resolvedChannelId = "local-channel-uuid")

        assertEquals(dto.id, entity.serverId)
        assertEquals("local-channel-uuid", entity.channelId)
        assertEquals(dto.channelDetailsName, entity.channelDetailsName)
        assertEquals(dto.amount, entity.amount, 0.0)
        assertEquals(dto.type, entity.type)
        assertEquals(dto.note, entity.note)
        assertEquals(dto.username, entity.username)
        assertNull("manually-migrated rows carry no SMS dedup key", entity.smsSourceKey)
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
        assertTrue(!entity.dirty)
    }

    @Test
    fun `transaction with no resolvable channel keeps the display name but a null channel id`() {
        val dto =
            TransactionDetails(
                id = "tx-2",
                amount = 1000.0,
                type = "income",
                note = "",
                channelDetailsName = "Deleted Or Ambiguous Channel",
                username = "jane",
            )

        val entity = dto.toEntity(now = 1000L, resolvedChannelId = null)

        assertNull(entity.channelId)
        assertEquals("Deleted Or Ambiguous Channel", entity.channelDetailsName)
    }

    @Test
    fun `yearly budget conversion serializes line items and preserves totals`() {
        val dto =
            YearlyBudgetResponse(
                id = "yb-1",
                userId = "user-1",
                year = 2026,
                totalExpenditures = 100,
                totalIncome = 200,
                totalSavings = 50,
                totalTransactions = 3,
                transactions = emptyList(),
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            )

        val entity = dto.toEntity(now = 1000L)

        assertEquals(dto.id, entity.serverId)
        assertEquals(dto.year, entity.year)
        assertEquals(dto.totalExpenditures, entity.totalExpenditures)
        assertEquals(dto.totalIncome, entity.totalIncome)
        assertEquals(dto.totalSavings, entity.totalSavings)
        assertEquals(dto.totalTransactions, entity.totalTransactions)
        assertEquals("[]", entity.transactionsJson)
    }

    @Test
    fun `monthly budget resolves its parent yearly budget's local id, not the server id`() {
        val dto =
            MonthlyBudgetResponse(
                id = "mb-1",
                userId = "user-1",
                yearlyBudgetId = "yb-server-1",
                month = 6,
                year = 2026,
                totalExpenditures = 10,
                totalIncome = 20,
                totalSavings = 5,
                totalTransactions = 1,
                transactions = emptyList(),
                createdAt = "2026-06-01T00:00:00Z",
                updatedAt = "2026-06-01T00:00:00Z",
            )

        val entity = dto.toEntity(now = 1000L, resolvedYearlyBudgetId = "local-yearly-uuid")

        assertEquals("local-yearly-uuid", entity.yearlyBudgetId)
        assertEquals(dto.month, entity.month)
        assertEquals(dto.year, entity.year)
    }

    @Test
    fun `monthly budget with an unmatched parent gets a null yearlyBudgetId, not a guess`() {
        val dto =
            MonthlyBudgetResponse(
                id = "mb-2",
                userId = "user-1",
                yearlyBudgetId = "some-yearly-budget-that-failed-to-migrate",
                month = 7,
                year = 2026,
                totalExpenditures = 0,
                totalIncome = 0,
                totalSavings = 0,
                totalTransactions = 0,
                transactions = emptyList(),
                createdAt = "2026-07-01T00:00:00Z",
                updatedAt = "2026-07-01T00:00:00Z",
            )

        val entity = dto.toEntity(now = 1000L, resolvedYearlyBudgetId = null)

        assertNull(entity.yearlyBudgetId)
    }

    // ── ADR-0006 compatibility: parseLineItems must read pre-A9 migrated JSON correctly ────

    @Test
    fun `migrated yearly budget line items round-trip through parseLineItems with serverId backfilled`() {
        val tx =
            BudgetTransactionResponse(
                id = "tx-server-1",
                name = "Rent",
                amount = 500.0,
                type = "expense",
                createdAt = "2026-01-01T00:00:00Z",
            )
        val dto =
            YearlyBudgetResponse(
                id = "yb-1",
                userId = "user-1",
                year = 2026,
                totalExpenditures = 500,
                totalIncome = 0,
                totalSavings = 0,
                totalTransactions = 1,
                transactions = listOf(tx),
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            )

        // This is exactly what PrefsToRoomMigrator.migrateIfNeeded persists today — a raw
        // Gson(transactions) blob with no serverId/pendingAction keys, since those fields
        // didn't exist before ADR-0006.
        val entity = dto.toEntity(now = 1000L)
        val items = parseLineItems(entity.transactionsJson)

        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("a migrated line item's local id must be the server id it always was", tx.id, item.id)
        assertEquals("legacy JSON has no serverId key — parseLineItems must backfill it from id", tx.id, item.serverId)
        assertNull("a migrated row is already synced — pendingAction must be null (clean)", item.pendingAction)
        assertEquals(tx.name, item.name)
        assertEquals(tx.amount, item.amount, 0.0)
        assertEquals(tx.type, item.type)
    }
}
