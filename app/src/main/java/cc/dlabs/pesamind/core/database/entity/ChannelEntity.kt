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
 * and must never be forced unique. Unique-indexed (nullable-safe: SQLite allows multiple
 * NULLs in a unique index) so two concurrent SMS auto-create attempts for the same real
 * sender — regardless of the casing either one happened to compute [channelDesc] with —
 * always converge to exactly one channel row instead of racing a check-then-insert.
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["normalizedSenderKey"], unique = true),
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
    val smsNotificationEnabled: Boolean,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
