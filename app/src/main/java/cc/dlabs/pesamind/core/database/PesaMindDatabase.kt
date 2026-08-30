package cc.dlabs.pesamind.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cc.dlabs.pesamind.core.database.dao.ChannelDao
import cc.dlabs.pesamind.core.database.dao.DebtCreditDao
import cc.dlabs.pesamind.core.database.dao.MonthlyBudgetDao
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.dao.ProcessedMessageDao
import cc.dlabs.pesamind.core.database.dao.ProfileDao
import cc.dlabs.pesamind.core.database.dao.SavingGoalDao
import cc.dlabs.pesamind.core.database.dao.TombstoneDao
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.dao.YearlyBudgetDao
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.DebtCreditEntity
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.ProcessedMessageEntity
import cc.dlabs.pesamind.core.database.entity.ProfileEntity
import cc.dlabs.pesamind.core.database.entity.SavingGoalEntity
import cc.dlabs.pesamind.core.database.entity.Tombstone
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        ProcessedMessageEntity::class,
        DebtCreditEntity::class,
        SavingGoalEntity::class,
    ],
    version = 9,
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

    abstract fun processedMessageDao(): ProcessedMessageDao

    abstract fun debtCreditDao(): DebtCreditDao

    abstract fun savingGoalDao(): SavingGoalDao

    /**
     * Full local-data wipe, run on logout so no account's cached channels/transactions/budgets
     * ever linger to be seen — or, worse, have their still-pending outbox entries pushed — by
     * whichever account logs in next on the same device. See [AuthManager.logout].
     */
    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) { clearAllTables() }
}
