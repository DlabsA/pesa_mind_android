package cc.dlabs.pesamind.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cc.dlabs.pesamind.core.database.dao.ChannelDao
import cc.dlabs.pesamind.core.database.dao.MonthlyBudgetDao
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.dao.ProfileDao
import cc.dlabs.pesamind.core.database.dao.TombstoneDao
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.dao.YearlyBudgetDao
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.ProfileEntity
import cc.dlabs.pesamind.core.database.entity.Tombstone
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity

const val DATABASE_NAME = "pesamind.db"

@Database(
    entities = [
        TransactionEntity::class,
        ChannelEntity::class,
        ProfileEntity::class,
        MonthlyBudgetEntity::class,
        YearlyBudgetEntity::class,
        OutboxEntry::class,
        Tombstone::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PesaMindDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun channelDao(): ChannelDao

    abstract fun profileDao(): ProfileDao

    abstract fun monthlyBudgetDao(): MonthlyBudgetDao

    abstract fun yearlyBudgetDao(): YearlyBudgetDao

    abstract fun outboxDao(): OutboxDao

    abstract fun tombstoneDao(): TombstoneDao
}
