package cc.dlabs.pesamind.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.dlabs.pesamind.core.database.SyncStatus

/**
 * Single-row profile table. Uses a fixed sentinel [id] rather than a generated UUID:
 * unlike transactions/channels, a profile is never POST-created client-side with an
 * idempotency key — it's always synced in from the existing server user record on
 * login, then edited via PATCH. There is exactly one row, ever; [id] just needs to be
 * a stable, known key to query it by.
 */
@Entity(
    tableName = "profile",
    indices = [Index(value = ["syncStatus"])],
)
data class ProfileEntity(
    @PrimaryKey
    val id: String = LOCAL_PROFILE_ID,
    val serverId: String?,
    val username: String,
    val email: String,
    val avatarUrl: String,
    val balance: Double,
    val type: String,
    val syncStatus: SyncStatus,
    val dirty: Boolean,
    val updatedAt: Long,
) {
    companion object {
        const val LOCAL_PROFILE_ID = "local_profile"
    }
}
