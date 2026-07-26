package cc.dlabs.pesamind.core.data

import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Local-only per-line-item state persisted inside `MonthlyBudgetEntity`/`YearlyBudgetEntity`'s
 * `transactionsJson` column (ADR-0006). [id] is a stable, client-assigned key that never
 * changes for the life of the item — for an item this repository created, it starts as a
 * fresh UUID; for an item that arrived via `reconcileFromServer`/migration, it's seeded from
 * the server's own id. [serverId] is null until the server has confirmed this exact item (an
 * "add" push succeeded and this item was matched in the response) — see [BudgetLineItemMerge].
 * [pendingAction] mirrors the backend's `transaction_ops` action vocabulary (add/update/delete);
 * null means "clean, nothing to push" for this item. Every mutation this repository performs
 * must set it explicitly, since [parseLineItems]'s legacy-JSON backfill relies on "null
 * serverId + null pendingAction" being otherwise unreachable for anything but pre-A9 migrated
 * data.
 */
data class LocalBudgetLineItem(
    val id: String,
    val serverId: String? = null,
    val name: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    // Matches BudgetTransactionResponse's own @SerializedName so a legacy row (serialized
    // from that DTO shape, pre-ADR-0006) deserializes this field correctly instead of Gson
    // leaving it null on a key mismatch — which then NPEs on the very next `.copy()` call,
    // since this is a non-null Kotlin field (confirmed the hard way: an earlier version of
    // this file had no annotation here and normalizeLegacy() crashed on real migrated data).
    @SerializedName("created_at")
    val createdAt: String = "",
    val pendingAction: String? = null,
)

private val gson = Gson()
private val lineItemListType = object : TypeToken<List<LocalBudgetLineItem>>() {}.type

/**
 * Deserializes a budget entity's `transactionsJson` column. Rows written by
 * `PrefsToRoomMigrator` before this shape existed serialize plain `BudgetTransactionResponse`
 * objects (`id`/`name`/`amount`/`type`/`created_at`, no `serverId`/`pendingAction` keys) —
 * Gson leaves the two new fields at their null default, which [normalizeLegacy] backfills.
 */
fun parseLineItems(json: String): List<LocalBudgetLineItem> {
    if (json.isBlank()) return emptyList()
    val raw: List<LocalBudgetLineItem> = gson.fromJson(json, lineItemListType) ?: return emptyList()
    return raw.normalizeLegacy()
}

/** See [parseLineItems]'s doc comment. Idempotent — already-normalized items are untouched. */
fun List<LocalBudgetLineItem>.normalizeLegacy(): List<LocalBudgetLineItem> =
    map { if (it.serverId == null && it.pendingAction == null) it.copy(serverId = it.id) else it }

fun List<LocalBudgetLineItem>.toTransactionsJson(): String = gson.toJson(this)

/** UI-facing view of a line item — hides `pendingAction`/local-vs-server id plumbing exactly
 * like `ChannelEntity.toDetails()` hides `syncStatus`/`dirty`. A pending-delete item must be
 * filtered out by the caller before this is invoked — this function has no way to signal
 * "omit me." */
fun LocalBudgetLineItem.toResponse() =
    BudgetTransactionResponse(
        id = serverId ?: id,
        name = name,
        amount = amount,
        type = type,
        createdAt = createdAt,
    )

/** A server-sourced line item is always "clean" (`pendingAction = null`) and already has a
 * `serverId` — used by both the pull-side full overwrite (`reconcileFromServer`) and the
 * push-completion merge ([BudgetLineItemMerge]) to build a canonical local item from a
 * response entry. */
fun BudgetTransactionResponse.toLocalLineItem() =
    LocalBudgetLineItem(
        id = id,
        serverId = id,
        name = name,
        amount = amount,
        type = type,
        createdAt = createdAt,
        pendingAction = null,
    )

data class BudgetTotals(
    val income: Long,
    val expenditures: Long,
    val savings: Long,
    val count: Long,
)

/** Computed locally rather than trusted from a push response's totals — correct uniformly
 * whether or not a mid-flight edit raced the push (see [BudgetLineItemMerge]), since it's
 * always derived from whatever the merged local list currently looks like. Pending-delete
 * items are excluded (already gone from the user's perspective); pending-add/update items
 * count immediately (optimistic), matching [toResponse]'s visibility rule. */
fun List<LocalBudgetLineItem>.computeTotals(): BudgetTotals {
    var income = 0L
    var expenditures = 0L
    var savings = 0L
    var count = 0L
    for (item in this) {
        if (item.pendingAction == "delete") continue
        count++
        val amount = item.amount.toLong()
        when (item.type) {
            "income" -> income += amount
            "expense" -> expenditures += amount
            "saving" -> savings += amount
        }
    }
    return BudgetTotals(income, expenditures, savings, count)
}

/**
 * Pure line-item mutation helpers — no Room dependency, applied by `MonthlyBudgetRepository`/
 * `YearlyBudgetRepository` inside their own `database.withTransaction` blocks. `update` has no
 * UI call site today (neither `SetMonthlyBudgetViewModel` nor `YearlyBudgetViewModel` exposes
 * an edit action, only add/delete) — kept for parity with the backend's own add/update/delete
 * `transaction_ops` vocabulary, same "defensive, reachable infrastructure" status as
 * `SyncWorker`'s non-CREATE transaction push branch.
 */
object BudgetLineItemOps {
    fun add(
        current: List<LocalBudgetLineItem>,
        name: String,
        amount: Double,
        type: String,
    ): List<LocalBudgetLineItem> =
        current +
            LocalBudgetLineItem(
                id = UUID.randomUUID().toString(),
                serverId = null,
                name = name,
                amount = amount,
                type = type,
                pendingAction = "add",
            )

    /** [publicId] is whatever the ViewModel/UI actually holds — `BudgetTransactionResponse.id`,
     * which [toResponse] maps from `serverId ?: id`. That is NOT the same value as [id] for an
     * already-synced item (its stable local [id] and its server-assigned [serverId] differ by
     * design — see [BudgetLineItemMerge]), so matching must go through the same `serverId ?: id`
     * projection [toResponse] used to hand this value to the UI in the first place, not [id]
     * directly. */
    fun delete(
        current: List<LocalBudgetLineItem>,
        publicId: String,
    ): List<LocalBudgetLineItem> {
        val target = current.firstOrNull { (it.serverId ?: it.id) == publicId } ?: return current
        return if (target.serverId == null) {
            // Never synced — the server never knew this row existed. Remove outright, no op
            // emitted (mirrors OutboxCoalescer.HardDeleteNoOutbox at line-item granularity).
            current.filterNot { it.id == target.id }
        } else {
            current.map { if (it.id == target.id) it.copy(pendingAction = "delete") else it }
        }
    }

    /** See [delete]'s doc comment on why matching goes through `serverId ?: id`, not [id]
     * directly. */
    fun update(
        current: List<LocalBudgetLineItem>,
        publicId: String,
        name: String,
        amount: Double,
        type: String,
    ): List<LocalBudgetLineItem> {
        val target = current.firstOrNull { (it.serverId ?: it.id) == publicId } ?: return current
        return current.map {
            when {
                it.id != target.id -> it
                // Still an unconfirmed add — the server has never seen it, so it's still one
                // "add," not "add-then-update."
                it.serverId == null -> it.copy(name = name, amount = amount, type = type)
                else -> it.copy(name = name, amount = amount, type = type, pendingAction = "update")
            }
        }
    }
}

/**
 * Reconciles a push response's confirmed line items back into the current local list — used
 * identically whether `PushCompletionResolver` decided `ClearAndSync` or `RequeueDirty`; this
 * function only decides the resulting item list, not the entity-level dirty/outbox handling.
 *
 * [dispatched] is exactly what was included in the outgoing push body — the full items, not
 * just their ids, captured before the network call (same "snapshot before dispatch" pattern
 * `SyncWorker` already uses for entity-level completion detection via `dispatchUpdatedAt`). The
 * full items are needed, not just ids, to detect the case documented below where a dispatched
 * item no longer exists in [current] at all.
 *
 * Matching a response item back to a local one: by `serverId` first (covers update/delete
 * confirmations, and re-confirmed adds from a retried push). For a genuinely new server row (no
 * local item already carries that `serverId`), match against still-unclaimed locally-dispatched
 * "add" items in [current] by `(name, amount, type)` — best-effort, since the backend does not
 * echo a client-supplied id on add (confirmed by reading `budget_handler.go`'s "add" case, which
 * builds a fresh `BudgetTransaction` with no `ID` field set from the request). Known, accepted
 * gap: two different items dispatched as "add" in the same push sharing an identical
 * name+amount+type could swap local ids — cosmetic (a Compose recomposition key swap), not a
 * correctness bug; both items still end up present with the right values.
 *
 * A response row that matches neither of the above, but DOES match a [dispatched] "add" item
 * that has since vanished from [current] entirely, means the user deleted that item locally
 * (via [BudgetLineItemOps.delete]'s never-synced outright-removal path) *while this exact push
 * was already in flight* — a real, confirmed race: the server still created the row (the add
 * was sent before the delete happened), but resurrecting it locally as a clean, confirmed item
 * would silently undo the user's delete. Instead, the confirmed row is kept as a
 * `pendingAction = "delete"` placeholder — invisible to the UI ([toResponse]'s caller filters
 * pending-deletes) and automatically pushed as a real DELETE on the next sync cycle.
 */
object BudgetLineItemMerge {
    fun merge(
        current: List<LocalBudgetLineItem>,
        dispatched: List<LocalBudgetLineItem>,
        response: List<LocalBudgetLineItem>,
    ): List<LocalBudgetLineItem> {
        val dispatchedIds = dispatched.map { it.id }.toSet()
        val currentIds = current.map { it.id }.toSet()
        val claimed = mutableSetOf<String>()

        fun matchesByContent(candidate: LocalBudgetLineItem) =
            { it: LocalBudgetLineItem ->
                it.id !in claimed && it.pendingAction == "add" &&
                    it.name == candidate.name && it.amount == candidate.amount && it.type == candidate.type
            }

        val merged =
            response
                .map { r ->
                    val bySeverId = current.firstOrNull { it.serverId != null && it.serverId == r.serverId }
                    if (bySeverId != null) {
                        claimed += bySeverId.id
                        return@map r.copy(id = bySeverId.id)
                    }
                    val byBestEffort = current.filter { it.id in dispatchedIds }.firstOrNull(matchesByContent(r))
                    if (byBestEffort != null) {
                        claimed += byBestEffort.id
                        return@map r.copy(id = byBestEffort.id)
                    }
                    val dispatchedButDeletedLocally = dispatched.filter { it.id !in currentIds }.firstOrNull(matchesByContent(r))
                    if (dispatchedButDeletedLocally != null) {
                        claimed += dispatchedButDeletedLocally.id
                        return@map r.copy(id = dispatchedButDeletedLocally.id, pendingAction = "delete")
                    }
                    r
                }.toMutableList()

        // Items that raced this push (added/edited/deleted after `dispatched` was captured)
        // never appeared in the outgoing body, so the response says nothing about them —
        // preserve as-is. This is what makes RequeueDirty safe: nothing is silently dropped.
        current.filter { it.pendingAction != null && it.id !in dispatchedIds }
            .forEach { merged += it }

        return merged
    }
}
