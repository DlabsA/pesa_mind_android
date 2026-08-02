package cc.dlabs.pesamind.core.di

import android.content.Context
import androidx.room.Room
import cc.dlabs.pesamind.core.database.DATABASE_NAME
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.dao.ChannelDao
import cc.dlabs.pesamind.core.database.dao.MonthlyBudgetDao
import cc.dlabs.pesamind.core.database.dao.OutboxDao
import cc.dlabs.pesamind.core.database.dao.ProcessedMessageDao
import cc.dlabs.pesamind.core.database.dao.ProfileDao
import cc.dlabs.pesamind.core.database.dao.TombstoneDao
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.dao.YearlyBudgetDao
import cc.dlabs.pesamind.core.database.migration.MIGRATION_1_2
import cc.dlabs.pesamind.core.database.migration.MIGRATION_2_3
import cc.dlabs.pesamind.core.database.migration.MIGRATION_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PesaMindDatabase =
        Room.databaseBuilder(context, PesaMindDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideTransactionDao(db: PesaMindDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideChannelDao(db: PesaMindDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideProfileDao(db: PesaMindDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideMonthlyBudgetDao(db: PesaMindDatabase): MonthlyBudgetDao = db.monthlyBudgetDao()

    @Provides
    fun provideYearlyBudgetDao(db: PesaMindDatabase): YearlyBudgetDao = db.yearlyBudgetDao()

    @Provides
    fun provideOutboxDao(db: PesaMindDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideTombstoneDao(db: PesaMindDatabase): TombstoneDao = db.tombstoneDao()

    @Provides
    fun provideProcessedMessageDao(db: PesaMindDatabase): ProcessedMessageDao = db.processedMessageDao()
}
