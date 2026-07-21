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
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["updatedAt"]),
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
    val smsNotificationEnabled: Boolean,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
