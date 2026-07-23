package cc.dlabs.pesamind.core.database

import cc.dlabs.pesamind.core.database.entity.OutboxOperation

sealed class PushCompletionDecision {
    /** No edit landed on this row while its push was in flight — safe to clear `dirty`,
     * mark `SYNCED`, and delete the outbox row. */
    data class ClearAndSync(val serverId: String?) : PushCompletionDecision()

    /** A repository write landed on this row *while its push was in flight* — the entity's
     * `dirty` flag must stay `true` and the outbox row must be requeued (not deleted), or
     * that newer edit is silently lost. If the completed push was a CREATE, the requeued
     * follow-up must be an UPDATE — the row now exists server-side, so replaying CREATE
     * would either 4xx or duplicate it. */
    data class RequeueDirty(val serverId: String?, val nextOperation: OutboxOperation) : PushCompletionDecision()
}

/**
 * Pure "did this row get re-dirtied mid-flight" decision function — no Room/Android
 * dependency, JVM-testable. This is the exact re-check [OutboxCoalescer]'s `SYNCING` /
 * `LeaveInFlight` branch was written to require of Slice A2 (see its doc comment): a
 * successful push must not blindly clear `dirty` at *dequeue* time, only after confirming
 * the entity's state at *completion* time still matches what was actually sent.
 *
 * [updatedAtAtDispatch] is the entity's `updatedAt` captured immediately before the network
 * call was sent (after the outbox row was marked `SYNCING`, so any concurrent repository
 * write during the call coalesces via `LeaveInFlight` instead of touching the outbox row).
 * [updatedAtNow] is read fresh immediately after the call returns. A mismatch means a write
 * landed in that window.
 */
object PushCompletionResolver {
    fun resolve(
        pushedOperation: OutboxOperation,
        updatedAtAtDispatch: Long,
        updatedAtNow: Long,
        responseServerId: String?,
    ): PushCompletionDecision =
        if (updatedAtNow != updatedAtAtDispatch) {
            val nextOp = if (pushedOperation == OutboxOperation.CREATE) OutboxOperation.UPDATE else pushedOperation
            PushCompletionDecision.RequeueDirty(responseServerId, nextOp)
        } else {
            PushCompletionDecision.ClearAndSync(responseServerId)
        }
}
