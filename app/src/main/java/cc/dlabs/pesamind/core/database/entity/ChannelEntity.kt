package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first channel row. Room is the source of truth — [id] is a client-generated UUID
 * assigned at creation, [serverId] is populated once the create has synced. Merges what
 * used to be two separate DataStore blobs (`cached_channels` + `sms_notification_flags`)
 * into one column ([smsNotificationEnabled]).
 *
 * [normalizedSenderKey] is a trim+lowercase fold of [channelDesc], populated only for
 * provider/bank channels resolved from a known SMS sender (mobile money, bank) — null for
 * CASH channels, which legitimately share a single `channelDesc` ("Cash") across many rows
 * and must never be forced unique.
 *
 * [receivingNumber] is the [cc.dlabs.pesamind.core.utils.PhoneNumberNormalizer]-normalized
 * form of the account's own phone/receiving number (mobile money) or [UNSPECIFIED_RECEIVING_NUMBER]
 * when it can't be resolved — added so two channels can share one [normalizedSenderKey] (e.g.
 * two MTN MoMo lines on the same phone, or a personal + business account) and still be told
 * apart by which number an incoming SMS actually arrived on. Deliberately NOT nullable: SQLite
 * treats every `NULL` in a unique index as distinct from every other `NULL`, so a nullable
 * column here would silently disable the dedup guarantee below for every row without a
 * resolved number — i.e. almost every row that existed before this field was added.
 * [UNSPECIFIED_RECEIVING_NUMBER] keeps those rows genuinely deduped against each other instead.
 *
 * Unique-indexed *per [userId]* (nullable-safe for [normalizedSenderKey]: SQLite allows
 * multiple NULLs in a unique index) so two concurrent SMS auto-create attempts for the same
 * real sender+number under the same account — regardless of the casing either one happened to
 * compute [channelDesc] with — always converge to exactly one channel row instead of racing a
 * check-then-insert. Scoped by [userId], not table-wide: a table-wide unique index let a
 * previous account's (even soft-deleted) channel silently block a different account from ever
 * creating its own channel under the same provider name — confirmed as a real bug on a
 * reused/shared device (see MIGRATION_5_6's doc comment).
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["userId", "normalizedSenderKey", "receivingNumber"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    val name: String,
    val channelType: String,
    val description: String,
    val status: Boolean,
    val channelDesc: String,
    val normalizedSenderKey: String?,
    val availableBalance: Double,
    // Optional, shared across channel types: a phone number for MobileMoney/Airtel
    // channels, a bank account number for Bank channels.
    val accountNumber: String?,
    // See this entity's doc comment — never blank, use UNSPECIFIED_RECEIVING_NUMBER instead.
    val receivingNumber: String = UNSPECIFIED_RECEIVING_NUMBER,
    val smsNotificationEnabled: Boolean,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
) {
    companion object {
        const val UNSPECIFIED_RECEIVING_NUMBER = "UNSPECIFIED"
    }
}
