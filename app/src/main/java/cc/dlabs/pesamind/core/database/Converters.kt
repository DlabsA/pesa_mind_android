package cc.dlabs.pesamind.core.database

import androidx.room.TypeConverter
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxOperation

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromOutboxEntityType(type: OutboxEntityType): String = type.name

    @TypeConverter
    fun toOutboxEntityType(value: String): OutboxEntityType = OutboxEntityType.valueOf(value)

    @TypeConverter
    fun fromOutboxOperation(operation: OutboxOperation): String = operation.name

    @TypeConverter
    fun toOutboxOperation(value: String): OutboxOperation = OutboxOperation.valueOf(value)
}
