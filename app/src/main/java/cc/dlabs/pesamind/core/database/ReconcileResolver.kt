package cc.dlabs.pesamind.core.database

/**
 * Snapshot of whatever local row a pull-reconciliation lookup found by `serverId` — never
 * by local PK (ADR-0004 invariant: a full-list pull must dedup against the server's own
 * identity, not accidentally match an unrelated local row). `null` means no local row has
 * this `serverId` yet.
 */
data class ExistingRowSnapshot(val dirty: Boolean, val deletedAt: Long?)

sealed class ReconcileDecision {
    /** No local row for this `serverId` — insert a new one, `SYNCED`. */
    data object InsertNew : ReconcileDecision()

    /** A clean, live local row exists — safe to overwrite with the server's fields. */
    data object UpdateExisting : ReconcileDecision()

    /** A local row exists but is either unsynced (`dirty`) or soft-deleted — a pull must
     * never clobber an in-flight local edit, and must never resurrect a tombstoned row as
     * a live duplicate. Leave it untouched. */
    data object SkipDirtyOrDeleted : ReconcileDecision()
}

/**
 * Pure pull-reconciliation decision function — no Room/Android dependency, JVM-testable.
 * Extracted from `ChannelRepository`/`TransactionRepository.reconcileFromServer` (ADR-0004
 * Slice A2) specifically so this policy — previously inline inside a `database.withTransaction`
 * block and untestable without a real SQLite-backed Room instance — has direct unit coverage.
 * The call sites remain thin DAO glue (read by `serverId`, branch on this decision), the same
 * trust level [OutboxCoalescer]'s call sites already have.
 */
object ReconcileResolver {
    fun resolve(existing: ExistingRowSnapshot?): ReconcileDecision =
        when {
            existing == null -> ReconcileDecision.InsertNew
            existing.dirty || existing.deletedAt != null -> ReconcileDecision.SkipDirtyOrDeleted
            else -> ReconcileDecision.UpdateExisting
        }
}
