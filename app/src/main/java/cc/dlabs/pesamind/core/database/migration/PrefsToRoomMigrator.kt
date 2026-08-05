@file:Suppress("DEPRECATION") // TransactionManager is dead everywhere else post-Slice-A1, but
// this one-time historical import still legitimately needs to read whatever it last held
// before Room existed.

package cc.dlabs.pesamind.core.database.migration

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.MonthlyBudgetEntity
import cc.dlabs.pesamind.core.database.entity.ProfileEntity
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.entity.YearlyBudgetEntity
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.BudgetManager
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.TransactionManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.migrationDataStore by preferencesDataStore("pesamind_room_migration")
private val MIGRATION_DONE_KEY = booleanPreferencesKey("prefs_to_room_migration_done")

private const val TAG = "PrefsToRoomMigrator"

/**
 * One-time, idempotent import of the old DataStore-blob caches into Room. Guarded twice:
 * a DataStore flag (the fast path once done), and — in case a process death lands between
 * the Room transaction committing and that flag being written — a "does Room already have
 * rows" check, so a retry can never double-insert. The prefs sources are left untouched;
 * this only reads them. See ADR-0004 for the full migration design and its known gaps
 * (migrated rows get "now" as their timestamp, since the source blobs never captured one;
 * transaction->channel links are matched by name best-effort, since the transactions
 * endpoint never echoes back a channel id).
 */
object PrefsToRoomMigrator {
    data class Result(
        val transactionsSource: Int,
        val transactionsInserted: Int,
        val channelsSource: Int,
        val channelsInserted: Int,
        val monthlyBudgetsSource: Int,
        val monthlyBudgetsInserted: Int,
        val yearlyBudgetsSource: Int,
        val yearlyBudgetsInserted: Int,
        val profileMigrated: Boolean,
    )

    suspend fun migrateIfNeeded(
        context: Context,
        db: PesaMindDatabase,
    ) {
        val appContext = context.applicationContext
        val alreadyDone = appContext.migrationDataStore.data.first()[MIGRATION_DONE_KEY] ?: false
        if (alreadyDone) return

        // Belt-and-suspenders: if Room already has rows (a prior run committed but the
        // flag write below was interrupted by process death), don't re-import — just
        // mark done and move on. Fresh UUIDs on a second run would duplicate every row.
        val alreadyPopulated =
            db.transactionDao().count() > 0 ||
                db.channelDao().count() > 0 ||
                db.monthlyBudgetDao().count() > 0 ||
                db.yearlyBudgetDao().count() > 0
        if (alreadyPopulated) {
            Log.w(TAG, "Room already has rows but migration flag was unset — skipping re-import, marking done.")
            markDone(appContext)
            return
        }

        // These two were never wired into PesaMindApp.onCreate() (pre-existing bug —
        // their DataStore caching has always been dead code), so init defensively rather
        // than assume the app already did it.
        TransactionManager.init(appContext)
        ChannelManager.init(appContext)
        AccountManager.init(appContext)
        BudgetManager.init(appContext)

        val transactionDtos = TransactionManager.getTransactions()
        val channelDtos = ChannelManager.getChannels()
        val monthlyDtos = BudgetManager.getMonthlyBudgets()
        val yearlyDtos = BudgetManager.getYearlyBudgets()
        val account = AccountManager.getAccount()
        val hasProfile = account.id.isNotBlank() || account.username.isNotBlank() || account.email.isNotBlank()

        val now = System.currentTimeMillis()

        val channelEntities = channelDtos.map { it.toEntity(now) }
        val channelIdByUniqueName = resolveUniqueChannelIdsByName(channelEntities)

        val transactionEntities =
            transactionDtos.map { it.toEntity(now, channelIdByUniqueName[it.channelDetailsName], account.id) }

        val yearlyEntities = yearlyDtos.map { it.toEntity(now) }
        val yearlyLocalIdByServerId = yearlyEntities.associate { it.serverId to it.id }
        val monthlyEntities =
            monthlyDtos.map { it.toEntity(now, yearlyLocalIdByServerId[it.yearlyBudgetId]) }

        val profileEntity =
            if (hasProfile) {
                ProfileEntity(
                    serverId = account.id.ifBlank { null },
                    username = account.username,
                    email = account.email,
                    avatarUrl = account.avatarUrl,
                    balance = account.balance,
                    type = account.type,
                    syncStatus = SyncStatus.SYNCED,
                    dirty = false,
                    updatedAt = now,
                )
            } else {
                null
            }

        // Verification happens INSIDE the same transaction as the inserts: if an
        // unnoticed serverId collision made OnConflictStrategy.REPLACE silently drop a
        // row, the check() throws, the whole transaction rolls back (nothing partial
        // committed), and the migration stays retriable from a clean slate. Verifying
        // after commit would let a failed check leave partial data behind that the
        // "Room already has rows" guard above would then mistake for a completed run.
        val result =
            db.withTransaction {
                db.channelDao().upsertAll(channelEntities)
                db.transactionDao().upsertAll(transactionEntities)
                db.yearlyBudgetDao().upsertAll(yearlyEntities)
                db.monthlyBudgetDao().upsertAll(monthlyEntities)
                profileEntity?.let { db.profileDao().upsert(it) }

                val outcome =
                    Result(
                        transactionsSource = transactionDtos.size,
                        transactionsInserted = db.transactionDao().count(),
                        channelsSource = channelDtos.size,
                        channelsInserted = db.channelDao().count(),
                        monthlyBudgetsSource = monthlyDtos.size,
                        monthlyBudgetsInserted = db.monthlyBudgetDao().count(),
                        yearlyBudgetsSource = yearlyDtos.size,
                        yearlyBudgetsInserted = db.yearlyBudgetDao().count(),
                        profileMigrated = profileEntity != null,
                    )
                check(outcome.transactionsSource == outcome.transactionsInserted) { "Transaction migration count mismatch: $outcome" }
                check(outcome.channelsSource == outcome.channelsInserted) { "Channel migration count mismatch: $outcome" }
                check(
                    outcome.monthlyBudgetsSource == outcome.monthlyBudgetsInserted,
                ) { "Monthly budget migration count mismatch: $outcome" }
                check(outcome.yearlyBudgetsSource == outcome.yearlyBudgetsInserted) { "Yearly budget migration count mismatch: $outcome" }
                outcome
            }
        Log.i(TAG, "Prefs -> Room migration complete: $result")

        markDone(appContext)
    }

    private suspend fun markDone(context: Context) {
        context.migrationDataStore.edit { it[MIGRATION_DONE_KEY] = true }
    }
}

/**
 * Maps a channel name to its local id only when the name is unique among [channels] —
 * ambiguous (2+ channels sharing a name) or absent names resolve to no entry, so a
 * caller falls back to null rather than guessing which channel a transaction meant.
 */
internal fun resolveUniqueChannelIdsByName(channels: List<ChannelEntity>): Map<String, String> =
    channels.groupBy { it.name }
        .filterValues { it.size == 1 }
        .mapValues { (_, group) -> group.first().id }

internal fun ChannelDetails.toEntity(now: Long) =
    ChannelEntity(
        id = UUID.randomUUID().toString(),
        serverId = id.ifBlank { null },
        userId = userId,
        name = name,
        channelType = channelType,
        description = description,
        status = status,
        channelDesc = channelDesc,
        // Deliberately NOT derived here, unlike ChannelRepository.createChannel/
        // reconcileFromServer: this entity is inserted via a single bulk `upsertAll` call
        // (OnConflictStrategy.REPLACE, not the per-row insertIgnore-with-safe-fallback this
        // migration's siblings use). If this migration batch already contains two real
        // duplicate channels for the same provider — a plausible, non-hypothetical state for
        // an existing user, given the exact bug this fix closes let SMS auto-create silently
        // hit the network on every message pre-fix — populating this column here would make
        // REPLACE silently delete one of them mid-migration, violating this pass's own "must
        // not silently delete already-synced rows without a user-facing/logged confirmation
        // step" constraint. Left null; backfilling migrated rows safely is Phase 3's job.
        normalizedSenderKey = null,
        // Cached pre-migration blobs predate this field (or default to 0.0) — harmless, the
        // next server reconcile refreshes it like any other server-owned field.
        availableBalance = availableBalance,
        // Predates this field too — same story as availableBalance above.
        accountNumber = null,
        smsNotificationEnabled = smsNotificationEnabled,
        syncStatus = SyncStatus.SYNCED,
        dirty = false,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

internal fun TransactionDetails.toEntity(
    now: Long,
    resolvedChannelId: String?,
    userId: String,
) = TransactionEntity(
    id = UUID.randomUUID().toString(),
    serverId = id.ifBlank { null },
    userId = userId,
    channelId = resolvedChannelId,
    channelDetailsName = channelDetailsName,
    amount = amount,
    type = type,
    note = note,
    username = username,
    smsSourceKey = null,
    // Not derivable retroactively from an already-migrated row (no original raw SMS body is
    // available at this point) — same Phase 3 scoping note as normalizedSenderKey above.
    providerTransactionId = null,
    syncStatus = SyncStatus.SYNCED,
    dirty = false,
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
)

internal fun YearlyBudgetResponse.toEntity(now: Long) =
    YearlyBudgetEntity(
        id = UUID.randomUUID().toString(),
        serverId = id.ifBlank { null },
        userId = userId,
        year = year,
        totalExpenditures = totalExpenditures,
        totalIncome = totalIncome,
        totalSavings = totalSavings,
        totalTransactions = totalTransactions,
        transactionsJson = Gson().toJson(transactions),
        syncStatus = SyncStatus.SYNCED,
        dirty = false,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

internal fun MonthlyBudgetResponse.toEntity(
    now: Long,
    resolvedYearlyBudgetId: String?,
) = MonthlyBudgetEntity(
    id = UUID.randomUUID().toString(),
    serverId = id.ifBlank { null },
    userId = userId,
    yearlyBudgetId = resolvedYearlyBudgetId,
    month = month,
    year = year,
    totalExpenditures = totalExpenditures,
    totalIncome = totalIncome,
    totalSavings = totalSavings,
    totalTransactions = totalTransactions,
    transactionsJson = Gson().toJson(transactions),
    syncStatus = SyncStatus.SYNCED,
    dirty = false,
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
)
