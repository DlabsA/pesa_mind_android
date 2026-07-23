package cc.dlabs.pesamind.core.database

import cc.dlabs.pesamind.core.database.entity.OutboxOperation

/**
 * Resolved outcome of coalescing a new local mutation against whatever outbox row already
 * exists for the same entity — `OutboxEntry`'s `(entityType, entityId)` unique index means
 * there is at most one.
 */
sealed class CoalesceDecision {
    /** Upsert the outbox row with [operation]. */
    data class WriteOutbox(val operation: OutboxOperation) : CoalesceDecision()

    /** The row was never synced (existing op was CREATE) — drop the outbox row entirely and
     * hard-delete the entity locally. No tombstone: the server never knew this row existed. */
    data object HardDeleteNoOutbox : CoalesceDecision()

    /** The existing outbox row is `SYNCING` — a push is in flight right now and the sync
     * worker owns `operation`/`status` until it resolves. Leave the outbox row untouched. */
    data object LeaveInFlight : CoalesceDecision()
}

/**
 * Pure outbox-coalescing decision function — no Room/Android dependency, JVM-testable.
 * See ADR-0004 "Outbox coalescing rules." [existingOperation]/[existingStatus] describe any
 * outbox row already queued for this entity; both null means no existing row.
 */
object OutboxCoalescer {
    fun coalesce(
        existingOperation: OutboxOperation?,
        existingStatus: SyncStatus?,
        newOperation: OutboxOperation,
    ): CoalesceDecision {
        if (existingOperation == null || existingStatus == null) {
            return CoalesceDecision.WriteOutbox(newOperation)
        }

        if (existingStatus == SyncStatus.SYNCING) {
            // A push for this exact row is in flight right now; the sync worker owns
            // operation/status until it resolves. Never overwrite out from under it — the
            // entity's own dirty=true (set by the caller regardless of this decision) is
            // the signal that a newer edit exists. Slice A2's worker MUST re-check dirty
            // before clearing it on a successful push, or this edit is silently lost
            // instead of being queued as a follow-up push. Not resolved by this function
            // alone — tracked as an explicit A2 dependency.
            return CoalesceDecision.LeaveInFlight
        }

        return when (existingOperation) {
            OutboxOperation.CREATE ->
                when (newOperation) {
                    // Server has never seen this row — a PUT/PATCH would 404. Stay CREATE.
                    OutboxOperation.UPDATE -> CoalesceDecision.WriteOutbox(OutboxOperation.CREATE)
                    // Never synced, now deleted: nothing to tell the server. Drop entirely.
                    OutboxOperation.DELETE -> CoalesceDecision.HardDeleteNoOutbox
                    OutboxOperation.CREATE -> CoalesceDecision.WriteOutbox(OutboxOperation.CREATE)
                }
            OutboxOperation.UPDATE ->
                when (newOperation) {
                    // Row is synced server-side; deleting it needs a real round-trip.
                    OutboxOperation.DELETE -> CoalesceDecision.WriteOutbox(OutboxOperation.DELETE)
                    OutboxOperation.UPDATE -> CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE)
                    OutboxOperation.CREATE -> CoalesceDecision.WriteOutbox(OutboxOperation.UPDATE)
                }
            OutboxOperation.DELETE ->
                // A DELETE outbox row means the entity is already soft-deleted; nothing in
                // this repository layer re-mutates a soft-deleted row, so this branch is
                // defensive rather than reachable today.
                CoalesceDecision.WriteOutbox(OutboxOperation.DELETE)
        }
    }
}
