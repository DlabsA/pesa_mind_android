package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first "Lent & Borrowed" debt row — [id] is a client-generated UUID assigned at
 * creation, [serverId] populated once the create has synced, mirroring [ChannelEntity].
 *
 * [outstanding]/[settledAt] are server-computed (summed from linked transactions — see the
 * backend's `debtcredit.Service.Outstanding`/`RecomputeSettlement`) and only ever refreshed in
 * [cc.dlabs.pesamind.core.data.DebtCreditRepository.reconcileFromServer], never written by any
 * local mutation.
 *
 * [reminderOffsetsCsv] stores the user-customizable "days before due date" list as a
 * comma-joined string rather than introducing a Room `TypeConverter` — this codebase has none
 * today (see [encode]/[DebtCreditEntity.reminderOffsets]).
 */
@Entity(
    tableName = "debt_credits",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["userId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["userId", "settledAt"]),
    ],
)
data class DebtCreditEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    // "lent" | "borrowed" — the raw enum value. NEVER shown as UI copy directly; see
    // PesaMindStrings.DebtCredit.directionLabel.
    val direction: String,
    val counterpartyName: String,
    val counterpartyPhone: String?,
    // PhoneNumberNormalizer.normalize(counterpartyPhone) — for SMS-sender-match suggestions.
    val counterpartyPhoneNormalized: String?,
    val originalAmount: Double,
    // Server-computed; refreshed only in reconcileFromServer.
    val outstanding: Double,
    val note: String,
    val dueAt: Long?,
    val reminderOffsetsCsv: String,
    // Server-computed; refreshed only in reconcileFromServer.
    val settledAt: Long?,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    val reminderOffsets: List<Int>
        get() = decode(reminderOffsetsCsv)

    companion object {
        fun encode(offsets: List<Int>): String = offsets.joinToString(",")

        fun decode(csv: String): List<Int> = csv.split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
    }
}
