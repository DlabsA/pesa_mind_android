package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Local-first transaction row. [channelId] is nullable because the transactions GET
 * response only ever returns a channel *name* (`channel_details_name`), never an id —
 * pulled rows are best-effort matched to a local [ChannelEntity] by name; when no unique
 * match exists, [channelId] stays null and [channelDetailsName] is the only channel
 * reference available for display. Rows created locally (manual entry or SMS) always
 * have [channelId] set, since the creating code already knows the local channel.
 *
 * [smsSourceKey] is the SMS-ingestion dedup key (see SMSMessageProcessor) — null for
 * manually-entered transactions, unique-indexed (nullable-safe: SQLite allows multiple
 * NULLs in a unique index) so reprocessing the same exact SMS is a no-op, not a duplicate.
 *
 * [providerTransactionId] is a provider-supplied transaction reference extracted from the
 * SMS body (e.g. Airtel's `TID`) — distinct from [smsSourceKey] because some providers
 * (confirmed: Airtel Uganda) send two *different* SMS bodies for one real transaction, so a
 * content-derived [smsSourceKey] alone can't catch that pair, only a shared provider TID
 * can. Unique-indexed on `(channelId, providerTransactionId)` rather than globally, since
 * provider TIDs are not confirmed unique across every supported provider — scoping by
 * channel (one channel per real sender/provider, see [ChannelEntity.normalizedSenderKey])
 * is the safer default until cross-provider uniqueness is verified. Null for manually
 * entered transactions and for any SMS a TID can't be extracted from. This index is *not*
 * additionally scoped by [userId] the way [smsSourceKey]'s is — it doesn't need to be, since
 * [channelId] already FKs to exactly one [ChannelEntity.userId]; two different accounts can
 * never share a `channelId` in the first place, so the pair is transitively user-safe already.
 *
 * [userId] scopes [smsSourceKey]'s uniqueness (and every list/lookup query on this table) to
 * the owning account — a table-wide unique `smsSourceKey` let a different account's SMS-derived
 * transaction silently block this account's structurally-identical one (see `ChannelEntity`'s
 * doc comment for the confirmed real-world bug this same shape caused for channels).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["channelId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["userId", "smsSourceKey"], unique = true),
        Index(value = ["channelId", "providerTransactionId"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val serverId: String?,
    val userId: String,
    val channelId: String?,
    val channelDetailsName: String,
    val amount: Double,
    val type: String,
    val note: String,
    val username: String,
    val smsSourceKey: String?,
    val providerTransactionId: String?,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
