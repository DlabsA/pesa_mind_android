package cc.dlabs.pesamind.core.database

import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [PushCompletionResolver] — the re-check-`dirty`-before-clear logic
 * `OutboxCoalescer`'s `LeaveInFlight` branch requires of Slice A2 (ADR-0004): a push must
 * never clear `dirty` just because the network call it sent succeeded, only if nothing
 * edited the row *after* that call was dispatched.
 */
class PushCompletionResolverTest {
    @Test
    fun `unchanged updatedAt clears dirty and syncs with the response serverId`() {
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.CREATE,
                updatedAtAtDispatch = 1000L,
                updatedAtNow = 1000L,
                responseServerId = "server-1",
            )

        assertEquals(PushCompletionDecision.ClearAndSync("server-1"), decision)
    }

    @Test
    fun `unchanged updatedAt on an update clears dirty with no serverId to carry`() {
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.UPDATE,
                updatedAtAtDispatch = 500L,
                updatedAtNow = 500L,
                responseServerId = null,
            )

        assertEquals(PushCompletionDecision.ClearAndSync(null), decision)
    }

    @Test
    fun `row re-dirtied mid-flight after a create push stays dirty and requeues as update`() {
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.CREATE,
                updatedAtAtDispatch = 1000L,
                updatedAtNow = 2000L,
                responseServerId = "server-1",
            )

        assertEquals(PushCompletionDecision.RequeueDirty("server-1", OutboxOperation.UPDATE), decision)
    }

    @Test
    fun `row re-dirtied mid-flight after an update push stays dirty and requeues as update`() {
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.UPDATE,
                updatedAtAtDispatch = 1000L,
                updatedAtNow = 1500L,
                responseServerId = null,
            )

        assertEquals(PushCompletionDecision.RequeueDirty(null, OutboxOperation.UPDATE), decision)
    }

    @Test
    fun `row re-dirtied mid-flight after a delete push requeues as delete not update`() {
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.DELETE,
                updatedAtAtDispatch = 1000L,
                updatedAtNow = 1200L,
                responseServerId = null,
            )

        assertEquals(PushCompletionDecision.RequeueDirty(null, OutboxOperation.DELETE), decision)
    }

    @Test
    fun `a re-dirtied create carries the newly learned serverId forward into the requeue`() {
        // The create itself still succeeded server-side even though a newer local edit
        // arrived mid-flight — losing the serverId here would strand the row without one.
        val decision =
            PushCompletionResolver.resolve(
                pushedOperation = OutboxOperation.CREATE,
                updatedAtAtDispatch = 10L,
                updatedAtNow = 20L,
                responseServerId = "server-99",
            )

        assertEquals("server-99", (decision as PushCompletionDecision.RequeueDirty).serverId)
    }
}
