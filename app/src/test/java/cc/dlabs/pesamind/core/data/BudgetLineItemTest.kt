package cc.dlabs.pesamind.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for the ADR-0006 local line-item model — no Room/Android runtime,
 * matching [cc.dlabs.pesamind.core.database.PushCompletionResolverTest]/
 * [cc.dlabs.pesamind.core.database.ReconcileResolverTest]'s convention of unit-testing the
 * extracted decision logic directly rather than only through a repository + real database.
 */
class BudgetLineItemTest {
    // ── parseLineItems / normalizeLegacy ────────────────────────────────────────

    @Test
    fun `blank json parses to an empty list`() {
        assertEquals(emptyList<LocalBudgetLineItem>(), parseLineItems(""))
    }

    @Test
    fun `legacy JSON with no serverId or pendingAction keys is backfilled as synced`() {
        // "created_at", not "createdAt" — matches BudgetTransactionResponse's own
        // @SerializedName, which is what actually produced this shape of JSON pre-ADR-0006.
        val legacyJson = """[{"id":"srv-1","name":"Rent","amount":500.0,"type":"expense","created_at":"2026-01-01"}]"""

        val items = parseLineItems(legacyJson)

        assertEquals(1, items.size)
        assertEquals("srv-1", items[0].id)
        assertEquals("legacy row's id IS the server id — must be backfilled into serverId", "srv-1", items[0].serverId)
        assertNull(items[0].pendingAction)
    }

    @Test
    fun `round-tripping an already-normalized item through toTransactionsJson and back is a no-op`() {
        val item = LocalBudgetLineItem(id = "local-1", serverId = "srv-1", name = "Rent", amount = 500.0, type = "expense")

        val roundTripped = parseLineItems(listOf(item).toTransactionsJson())

        assertEquals(listOf(item), roundTripped)
    }

    // ── BudgetLineItemOps ────────────────────────────────────────────────────────

    @Test
    fun `add appends a new pending-add item with no serverId`() {
        val result = BudgetLineItemOps.add(emptyList(), "Groceries", 200.0, "expense")

        assertEquals(1, result.size)
        assertNull(result[0].serverId)
        assertEquals("add", result[0].pendingAction)
        assertEquals("Groceries", result[0].name)
    }

    @Test
    fun `deleting a never-synced item removes it outright with no pending op`() {
        val unsynced =
            LocalBudgetLineItem(id = "local-1", serverId = null, pendingAction = "add", name = "X", amount = 10.0, type = "expense")

        val result = BudgetLineItemOps.delete(listOf(unsynced), publicId = "local-1")

        assertTrue("never-synced items must be removed outright, not flagged for delete", result.isEmpty())
    }

    @Test
    fun `deleting an already-synced item flags pendingAction delete and keeps the row`() {
        val synced =
            LocalBudgetLineItem(id = "local-1", serverId = "srv-1", pendingAction = null, name = "X", amount = 10.0, type = "expense")

        val result = BudgetLineItemOps.delete(listOf(synced), publicId = "srv-1")

        assertEquals(1, result.size)
        assertEquals("delete", result[0].pendingAction)
        assertEquals("the row must be kept so a push can still read its serverId", "srv-1", result[0].serverId)
    }

    @Test
    fun `delete matches by the UI-facing id (serverId), not the internal local id`() {
        // Regression test: BudgetTransactionResponse.id (what the ViewModel/UI actually has)
        // is `serverId ?: id` per toResponse() — an already-synced item's local `id` and
        // `serverId` are deliberately different values (see BudgetLineItemMerge), so matching
        // on `id` alone would silently fail to find the row the user is trying to delete.
        val synced =
            LocalBudgetLineItem(
                id = "local-uuid-1",
                serverId = "server-assigned-1",
                pendingAction = null,
                name = "X",
                amount = 10.0,
                type = "expense",
            )

        val result = BudgetLineItemOps.delete(listOf(synced), publicId = "server-assigned-1")

        assertEquals(1, result.size)
        assertEquals("delete", result[0].pendingAction)
        assertEquals("local-uuid-1", result[0].id)
    }

    @Test
    fun `deleting an unknown id is a no-op`() {
        val existing = listOf(LocalBudgetLineItem(id = "local-1", serverId = "srv-1"))

        val result = BudgetLineItemOps.delete(existing, publicId = "does-not-exist")

        assertEquals(existing, result)
    }

    @Test
    fun `update on an unconfirmed add keeps pendingAction add, not update`() {
        val unsynced =
            LocalBudgetLineItem(id = "local-1", serverId = null, pendingAction = "add", name = "X", amount = 10.0, type = "expense")

        val result = BudgetLineItemOps.update(listOf(unsynced), publicId = "local-1", name = "Y", amount = 20.0, type = "income")

        assertEquals("add", result[0].pendingAction)
        assertEquals("Y", result[0].name)
        assertEquals(20.0, result[0].amount, 0.0)
    }

    @Test
    fun `update on an already-synced item sets pendingAction update`() {
        val synced =
            LocalBudgetLineItem(id = "local-1", serverId = "srv-1", pendingAction = null, name = "X", amount = 10.0, type = "expense")

        val result = BudgetLineItemOps.update(listOf(synced), publicId = "srv-1", name = "Y", amount = 20.0, type = "income")

        assertEquals("update", result[0].pendingAction)
        assertEquals("Y", result[0].name)
    }

    // ── computeTotals ────────────────────────────────────────────────────────────

    @Test
    fun `computeTotals sums by type and excludes pending-delete items`() {
        val items =
            listOf(
                LocalBudgetLineItem(id = "1", amount = 100.0, type = "income", pendingAction = null),
                LocalBudgetLineItem(id = "2", amount = 40.0, type = "expense", pendingAction = "add"),
                LocalBudgetLineItem(id = "3", amount = 10.0, type = "saving", pendingAction = null),
                LocalBudgetLineItem(id = "4", amount = 999.0, type = "expense", pendingAction = "delete"),
            )

        val totals = items.computeTotals()

        assertEquals(100L, totals.income)
        assertEquals(40L, totals.expenditures)
        assertEquals(10L, totals.savings)
        assertEquals("pending-delete items are excluded from the visible count", 3L, totals.count)
    }

    // ── toResponse / UI-facing mapping ──────────────────────────────────────────

    @Test
    fun `toResponse exposes serverId when present, falling back to the local id`() {
        val synced = LocalBudgetLineItem(id = "local-1", serverId = "srv-1", name = "X", amount = 1.0, type = "income")
        val unsynced = LocalBudgetLineItem(id = "local-2", serverId = null, name = "Y", amount = 2.0, type = "income")

        assertEquals("srv-1", synced.toResponse().id)
        assertEquals("local-2", unsynced.toResponse().id)
    }

    // ── BudgetLineItemMerge ──────────────────────────────────────────────────────

    @Test
    fun `merge confirms a dispatched add by matching name-amount-type when no serverId exists yet`() {
        val pendingAdd =
            LocalBudgetLineItem(id = "local-1", serverId = null, name = "Rent", amount = 500.0, type = "expense", pendingAction = "add")
        val response = budgetTransactionResponseFixture("srv-1", "Rent", 500.0, "expense").toLocalLineItem()

        val merged = BudgetLineItemMerge.merge(listOf(pendingAdd), dispatched = listOf(pendingAdd), response = listOf(response))

        assertEquals(1, merged.size)
        assertEquals("the confirmed item must keep its original stable local id", "local-1", merged[0].id)
        assertEquals("srv-1", merged[0].serverId)
        assertNull("a confirmed item is clean again", merged[0].pendingAction)
    }

    @Test
    fun `merge matches an update or delete confirmation by serverId, not name-amount-type`() {
        val pendingUpdate =
            LocalBudgetLineItem(
                id = "local-1",
                serverId = "srv-1",
                name = "Old name",
                amount = 10.0,
                type = "expense",
                pendingAction = "update",
            )
        val response = budgetTransactionResponseFixture("srv-1", "New name", 15.0, "expense").toLocalLineItem()

        val merged = BudgetLineItemMerge.merge(listOf(pendingUpdate), dispatched = listOf(pendingUpdate), response = listOf(response))

        assertEquals(1, merged.size)
        assertEquals("local-1", merged[0].id)
        assertEquals("New name", merged[0].name)
        assertEquals(15.0, merged[0].amount, 0.0)
        assertNull(merged[0].pendingAction)
    }

    @Test
    fun `merge preserves an edit that raced the push instead of dropping it`() {
        val dispatchedAdd =
            LocalBudgetLineItem(id = "local-1", serverId = null, name = "Rent", amount = 500.0, type = "expense", pendingAction = "add")
        // Added AFTER `dispatched` was captured — never made it into the outgoing push body.
        val racedAdd =
            LocalBudgetLineItem(id = "local-2", serverId = null, name = "Utilities", amount = 80.0, type = "expense", pendingAction = "add")
        val response = budgetTransactionResponseFixture("srv-1", "Rent", 500.0, "expense").toLocalLineItem()

        val merged =
            BudgetLineItemMerge.merge(
                current = listOf(dispatchedAdd, racedAdd),
                dispatched = listOf(dispatchedAdd),
                response = listOf(response),
            )

        assertEquals(2, merged.size)
        val confirmed = merged.first { it.id == "local-1" }
        val raced = merged.first { it.id == "local-2" }
        assertEquals("srv-1", confirmed.serverId)
        assertNull(confirmed.pendingAction)
        assertEquals("a raced edit must survive the merge untouched, not be silently dropped", "add", raced.pendingAction)
        assertNull(raced.serverId)
    }

    @Test
    fun `merge on an ambiguous double-add still surfaces both confirmed items with correct values`() {
        // Known, accepted gap (see BudgetLineItemMerge's doc comment): two items dispatched as
        // "add" in the same push sharing identical name+amount+type can't be told apart by the
        // best-effort match. This test asserts the accepted behavior — both items end up
        // present and correct, even if which-local-id-maps-to-which is not guaranteed.
        val addA =
            LocalBudgetLineItem(id = "local-1", serverId = null, name = "Coffee", amount = 5.0, type = "expense", pendingAction = "add")
        val addB =
            LocalBudgetLineItem(id = "local-2", serverId = null, name = "Coffee", amount = 5.0, type = "expense", pendingAction = "add")
        val responseItems =
            listOf(
                budgetTransactionResponseFixture("srv-1", "Coffee", 5.0, "expense").toLocalLineItem(),
                budgetTransactionResponseFixture("srv-2", "Coffee", 5.0, "expense").toLocalLineItem(),
            )

        val merged =
            BudgetLineItemMerge.merge(
                current = listOf(addA, addB),
                dispatched = listOf(addA, addB),
                response = responseItems,
            )

        assertEquals(2, merged.size)
        assertTrue("every merged item must be confirmed (clean)", merged.all { it.pendingAction == null })
        assertEquals(setOf("srv-1", "srv-2"), merged.map { it.serverId }.toSet())
        assertEquals(setOf("local-1", "local-2"), merged.map { it.id }.toSet())
    }

    @Test
    fun `merge does not resurrect an item deleted locally while its add was in flight`() {
        // Regression test for a real race found in review: item A is dispatched as "add", then
        // deleted locally (BudgetLineItemOps.delete's outright-removal path, since it was still
        // unsynced) WHILE that exact push is in flight. The server still creates it — the add
        // was already sent — but the confirmation must not resurrect it as a clean, visible
        // item, since the user already deleted it.
        val dispatchedAdd =
            LocalBudgetLineItem(id = "local-1", serverId = null, name = "Rent", amount = 500.0, type = "expense", pendingAction = "add")
        // `current` no longer contains local-1 at all — it was deleted outright before the
        // response came back.
        val current = emptyList<LocalBudgetLineItem>()
        val response = budgetTransactionResponseFixture("srv-1", "Rent", 500.0, "expense").toLocalLineItem()

        val merged = BudgetLineItemMerge.merge(current = current, dispatched = listOf(dispatchedAdd), response = listOf(response))

        assertEquals(1, merged.size)
        assertEquals(
            "the confirmed-but-locally-deleted row must be queued for deletion, not resurrected as clean",
            "delete",
            merged[0].pendingAction,
        )
        assertEquals("srv-1", merged[0].serverId)
    }

    @Test
    fun `merge still resurrects a response row with no dispatch history to explain it`() {
        // If a response row can't be explained by anything this push knows about (no serverId
        // match, no best-effort match among still-present items, no vanished-dispatched-item
        // match either), there's no better option than trusting the response as-is.
        val response = budgetTransactionResponseFixture("srv-1", "Mystery", 1.0, "expense").toLocalLineItem()

        val merged = BudgetLineItemMerge.merge(current = emptyList(), dispatched = emptyList(), response = listOf(response))

        assertEquals(1, merged.size)
        assertNull(merged[0].pendingAction)
        assertEquals("srv-1", merged[0].serverId)
    }
}

/** Small local fixture avoiding a hard dependency on `BudgetTransactionResponse`'s full
 * constructor shape in every test above. */
private fun budgetTransactionResponseFixture(
    id: String,
    name: String,
    amount: Double,
    type: String,
) = cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse(id = id, name = name, amount = amount, type = type)
