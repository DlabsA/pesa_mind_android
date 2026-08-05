package cc.dlabs.pesamind.core.data

import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BudgetRepository.buildTransactionOps] is the fix for a real, confirmed production data-loss
 * bug: adding a single transaction to a monthly budget wiped the whole month's prior history,
 * leaving only the new item. Root cause was the legacy full-replace push field (no ids) —
 * the backend's diff-and-delete update logic deleted every existing server-side transaction
 * not present in the incoming (locally-sourced, potentially incomplete) list. See
 * `MIGRATION_6_7`'s doc comment in `Migrations.kt` for the full incident writeup.
 */
class BudgetTransactionOpsDiffTest {
    private fun tx(
        id: String,
        name: String = "item",
        amount: Double = 1000.0,
        type: String = "expense",
    ) = BudgetTransactionResponse(id = id, name = name, amount = amount, type = type, createdAt = "")

    /**
     * The common case this bug actually manifested in: an existing, previously-synced budget
     * (baseline carries its real history forward, e.g. August's prior transactions) gets one
     * new item appended locally (`addMonthlyTransaction`'s "append to existing" behavior).
     * With a healthy baseline, the diff must emit exactly the one "add" — nothing else is
     * touched, since every prior item is still present and unchanged in both lists.
     */
    @Test
    fun addingOneItemToAnExistingBudget_emitsOnlyThatOneAdd() {
        val baseline =
            listOf(
                tx("server-1", name = "August salary", amount = 2000000.0, type = "income"),
                tx("server-2", name = "Rent", amount = 500000.0, type = "expense"),
            )
        // addMonthlyTransaction appends to the existing (unmodified) list, carrying server-1/
        // server-2 forward with their real ids intact.
        val current = baseline + tx("local-new", name = "Groceries", amount = 20000.0, type = "expense")

        val ops = BudgetRepository.buildTransactionOps(baseline, current)

        assertEquals(1, ops.size)
        assertEquals("add", ops[0].action)
        assertEquals("Groceries", ops[0].name)
    }

    /**
     * The guaranteed protective property this diff provides: a row whose baseline is empty
     * (never synced under the new scheme — e.g. a freshly-created local row) can structurally
     * only ever produce "add" ops, since a delete is only ever emitted for an item literally
     * present in [baseline]. This is what makes even a worst-case "fresh/empty local row"
     * push safe — before this fix, *every* push (even for a single item, healthy cache or
     * not) used the legacy full-replace field and wiped everything not in that one payload.
     *
     * Note this does NOT claim to detect/recover a baseline that's itself gone stale relative
     * to the server (a separate cache-corruption bug losing track of a previously-synced item
     * between syncs would still surface as an apparent deletion here) — seeded correctly (via
     * OutboxPusher's finish* functions / BudgetRepository's reconcile* functions on every
     * successful sync), the baseline should reflect real server state, and this test is about
     * what happens when there simply isn't one yet.
     */
    @Test
    fun neverSyncedRow_canOnlyEverAdd_evenWithMultipleNewItems() {
        val baseline = emptyList<BudgetTransactionResponse>()
        val current =
            listOf(
                tx("local-new-1", name = "Groceries", amount = 20000.0, type = "expense"),
                tx("local-new-2", name = "August salary", amount = 2000000.0, type = "income"),
            )

        val ops = BudgetRepository.buildTransactionOps(baseline, current)

        assertEquals(2, ops.size)
        assertTrue(
            "a never-synced row (empty baseline) must never be able to produce a delete op",
            ops.none { it.action == "delete" },
        )
    }

    @Test
    fun explicitlyRemovedItem_emitsDeleteWithRealId() {
        val baseline = listOf(tx("server-1"), tx("server-2"))
        // User genuinely deleted server-1 — current still correctly carries server-2 forward.
        val current = listOf(tx("server-2"))

        val ops = BudgetRepository.buildTransactionOps(baseline, current)

        assertEquals(1, ops.size)
        assertEquals("delete", ops[0].action)
        assertEquals("server-1", ops[0].id)
    }

    @Test
    fun changedItem_emitsUpdateWithFullFields() {
        val baseline = listOf(tx("server-1", name = "Rent", amount = 500000.0, type = "expense"))
        val current = listOf(tx("server-1", name = "Rent", amount = 550000.0, type = "expense"))

        val ops = BudgetRepository.buildTransactionOps(baseline, current)

        assertEquals(1, ops.size)
        assertEquals("update", ops[0].action)
        assertEquals("server-1", ops[0].id)
        assertEquals(550000.0, ops[0].amount, 0.0)
        assertEquals("Rent", ops[0].name)
        assertEquals("expense", ops[0].type)
    }

    @Test
    fun unchangedList_emitsNoOps() {
        val items = listOf(tx("server-1"), tx("server-2"))

        val ops = BudgetRepository.buildTransactionOps(items, items)

        assertTrue(ops.isEmpty())
    }

    @Test
    fun addUpdateAndDeleteTogether_eachEmittedIndependently() {
        val baseline =
            listOf(
                tx("server-1", name = "Rent", amount = 500000.0),
                tx("server-2", name = "Airtime", amount = 10000.0),
            )
        // server-1 updated, server-2 removed, local-new added.
        val current =
            listOf(
                tx("server-1", name = "Rent", amount = 600000.0),
                tx("local-new", name = "Groceries", amount = 20000.0),
            )

        val ops = BudgetRepository.buildTransactionOps(baseline, current)

        assertEquals(3, ops.size)
        assertEquals(1, ops.count { it.action == "add" })
        assertEquals(1, ops.count { it.action == "update" })
        assertEquals(1, ops.count { it.action == "delete" })
        assertEquals("server-2", ops.first { it.action == "delete" }.id)
    }
}
