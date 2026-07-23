package cc.dlabs.pesamind.core.database

import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for the pure outbox-coalescing decision table in [OutboxCoalescer.coalesce] —
 * the single shared gate every repository write must go through before touching an
 * existing outbox row (ADR-0004 "Outbox coalescing rules"). This is the test suite that
 * must pass before `ChannelRepository`/`TransactionRepository` are wired to call it.
 */
class OutboxCoalescerTest {
    @Test
    fun `no existing outbox row writes the new operation unchanged for create`() {
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = null,
                existingStatus = null,
                newOperation = OutboxOperation.CREATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.CREATE), decision)
    }

    @Test
    fun `no existing outbox row writes the new operation unchanged for update`() {
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = null,
                existingStatus = null,
                newOperation = OutboxOperation.UPDATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE), decision)
    }

    @Test
    fun `no existing outbox row writes the new operation unchanged for delete`() {
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = null,
                existingStatus = null,
                newOperation = OutboxOperation.DELETE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.DELETE), decision)
    }

    @Test
    fun `createThenUpdateStaysCreate`() {
        // Server has never seen this row; a PUT/PATCH from an UPDATE would 404, so the
        // pending CREATE must never be downgraded/overwritten by a later UPDATE.
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.CREATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.UPDATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.CREATE), decision)
    }

    @Test
    fun `createThenDeleteHardDeletesWithNoOutboxRow`() {
        // Never synced, now deleted: nothing to tell the server. The outbox row must be
        // dropped entirely (no DELETE tombstone written) and the entity hard-deleted.
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.CREATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.DELETE,
            )

        assertEquals(CoalesceDecision.HardDeleteNoOutbox, decision)
    }

    @Test
    fun `createThenCreateStaysCreate`() {
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.CREATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.CREATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.CREATE), decision)
    }

    @Test
    fun `updateThenDeleteRequiresARealRoundTripDelete`() {
        // The row IS synced server-side (has a real serverId), so deleting it must go
        // through a real DELETE round-trip rather than being silently dropped.
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.UPDATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.DELETE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.DELETE), decision)
    }

    @Test
    fun `updateThenUpdateReCoalescesToUpdate`() {
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.UPDATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.UPDATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE), decision)
    }

    @Test
    fun `updateThenCreateStaysUpdate`() {
        // A synced row can't be re-created; a stray CREATE against an already-UPDATE row
        // must stay UPDATE rather than regressing to CREATE (which would 409/duplicate).
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.UPDATE,
                existingStatus = SyncStatus.PENDING,
                newOperation = OutboxOperation.CREATE,
            )

        assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE), decision)
    }

    @Test
    fun `deleteThenAnyOperationStaysDelete`() {
        // Defensive branch: a soft-deleted row's own outbox DELETE must never be
        // clobbered by another mutation attempt.
        listOf(OutboxOperation.CREATE, OutboxOperation.UPDATE, OutboxOperation.DELETE).forEach { newOp ->
            val decision =
                OutboxCoalescer.coalesce(
                    existingOperation = OutboxOperation.DELETE,
                    existingStatus = SyncStatus.PENDING,
                    newOperation = newOp,
                )

            assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.DELETE), decision)
        }
    }

    @Test
    fun `syncingInFlightAlwaysLeavesRowUntouchedEvenForCreatePlusDelete`() {
        // The SYNCING check must short-circuit *before* the CREATE/DELETE combination
        // logic below it: a CREATE row currently mid-push plus an incoming DELETE would,
        // under the operation-combination rules alone, resolve to HardDeleteNoOutbox —
        // but the sync worker owns this row right now, so it must resolve to
        // LeaveInFlight instead. This is the case most likely to silently regress if the
        // SYNCING guard is ever reordered below the `when (existingOperation)` block.
        val decision =
            OutboxCoalescer.coalesce(
                existingOperation = OutboxOperation.CREATE,
                existingStatus = SyncStatus.SYNCING,
                newOperation = OutboxOperation.DELETE,
            )

        assertEquals(CoalesceDecision.LeaveInFlight, decision)
    }

    @Test
    fun `syncingInFlightWinsOverEveryExistingAndNewOperationCombination`() {
        val allOps = OutboxOperation.entries
        allOps.forEach { existingOp ->
            allOps.forEach { newOp ->
                val decision =
                    OutboxCoalescer.coalesce(
                        existingOperation = existingOp,
                        existingStatus = SyncStatus.SYNCING,
                        newOperation = newOp,
                    )

                assertEquals(
                    "existingOperation=$existingOp newOperation=$newOp should leave the row in flight",
                    CoalesceDecision.LeaveInFlight,
                    decision,
                )
            }
        }
    }

    @Test
    fun `nonSyncingStatusesDoNotTriggerLeaveInFlight`() {
        // Only SYNCING short-circuits to LeaveInFlight — PENDING/SYNCED/FAILED must fall
        // through to the ordinary operation-combination rules.
        listOf(SyncStatus.PENDING, SyncStatus.SYNCED, SyncStatus.FAILED).forEach { status ->
            val decision =
                OutboxCoalescer.coalesce(
                    existingOperation = OutboxOperation.UPDATE,
                    existingStatus = status,
                    newOperation = OutboxOperation.UPDATE,
                )

            assertEquals(CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE), decision)
        }
    }
}
