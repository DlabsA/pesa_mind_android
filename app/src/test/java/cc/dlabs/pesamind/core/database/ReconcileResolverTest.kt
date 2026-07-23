package cc.dlabs.pesamind.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [ReconcileResolver] — the pull-reconciliation policy `ChannelRepository`/
 * `TransactionRepository.reconcileFromServer` delegate to (ADR-0004 Slice A2). Closes the
 * "reconcileFromServer ships untested" gap Slice A1 shipped with: this is the actual
 * conflict-policy decision, previously inline inside an untestable `withTransaction` block,
 * now a pure function with its own coverage.
 */
class ReconcileResolverTest {
    @Test
    fun `no local row for this serverId inserts a new one`() {
        val decision = ReconcileResolver.resolve(existing = null)

        assertEquals(ReconcileDecision.InsertNew, decision)
    }

    @Test
    fun `a clean live local row is safe to update from the server payload`() {
        val decision = ReconcileResolver.resolve(existing = ExistingRowSnapshot(dirty = false, deletedAt = null))

        assertEquals(ReconcileDecision.UpdateExisting, decision)
    }

    @Test
    fun `a dirty unsynced local row is never overwritten by a pull`() {
        val decision = ReconcileResolver.resolve(existing = ExistingRowSnapshot(dirty = true, deletedAt = null))

        assertEquals(ReconcileDecision.SkipDirtyOrDeleted, decision)
    }

    @Test
    fun `a soft-deleted local row is never resurrected as a live duplicate`() {
        val decision = ReconcileResolver.resolve(existing = ExistingRowSnapshot(dirty = false, deletedAt = 12345L))

        assertEquals(ReconcileDecision.SkipDirtyOrDeleted, decision)
    }

    @Test
    fun `a row that is both dirty and soft-deleted is still skipped, not updated`() {
        val decision = ReconcileResolver.resolve(existing = ExistingRowSnapshot(dirty = true, deletedAt = 999L))

        assertEquals(ReconcileDecision.SkipDirtyOrDeleted, decision)
    }
}
