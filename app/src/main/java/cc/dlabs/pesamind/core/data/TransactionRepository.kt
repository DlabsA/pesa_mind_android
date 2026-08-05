package cc.dlabs.pesamind.core.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import cc.dlabs.pesamind.core.database.ExistingRowSnapshot
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.ReconcileDecision
import cc.dlabs.pesamind.core.database.ReconcileResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.database.migration.resolveUniqueChannelIdsByName
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.analytics.SummaryData
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.OutboxPusher
import cc.dlabs.pesamind.core.utils.TransactionTypes
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.UUID

/**
 * Outcome of [TransactionRepository.createTransaction] — distinguishes a genuine new row from
 * one discarded by the atomic dedup check, so callers that need to know (e.g.
 * `SMSMessageProcessor`, to log a discarded duplicate's raw SMS body) can branch on the real
 * `INSERT ... ON CONFLICT IGNORE` outcome rather than a SELECT performed before the insert,
 * which a concurrent caller could race.
 */
sealed class TransactionInsertOutcome {
    data class Inserted(val transaction: TransactionDetails) : TransactionInsertOutcome()

    data class DuplicateDiscarded(val existing: TransactionDetails) : TransactionInsertOutcome()
}

internal fun TransactionInsertOutcome.details(): TransactionDetails =
    when (this) {
        is TransactionInsertOutcome.Inserted -> transaction
        is TransactionInsertOutcome.DuplicateDiscarded -> existing
    }

/**
 * Room-backed source of truth for transactions (ADR-0004 Slice A1) — replaces
 * `TransactionManager`'s DataStore blob (which was never actually live in production; see
 * ADR-0004's Step 0 verification) for every `TransactionViewModel` call site. Same repository
 * justification and DI pattern as [ChannelRepository] — see its class doc.
 */
object TransactionRepository {
    private const val TAG = "TransactionRepository"

    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase/DAO directly
    // (no Android runtime / device available to run a real Room in-memory-database test).
    internal lateinit var database: PesaMindDatabase
    private val transactionDao get() = database.transactionDao()
    private val outboxDao get() = database.outboxDao()

    // Nullable, not lateinit: JVM unit tests inject `database` directly without calling [init]
    // (no Android Context available off-device), so an eager push must be a safe no-op then,
    // not a crash — those tests already exercise the outbox/background-sync path instead.
    private var networkMonitor: NetworkMonitor? = null
    private var outboxPusher: OutboxPusher? = null

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
        networkMonitor = NetworkMonitor(context.applicationContext)
        outboxPusher = OutboxPusher(database, ApiClient.api)
    }

    /**
     * Best-effort immediate push, fired right after a create's local commit when the device is
     * already known to be online — no reason to wait on WorkManager's periodic/connectivity-
     * regain triggers for the common case of "creating this while already connected." Launched
     * on its own scope rather than awaited: the caller (ViewModel) already got its snappy local
     * result, and this must never be able to fail the create just because the network hiccups —
     * the outbox row this pushes stays as the durable fallback regardless of outcome.
     */
    private fun pushEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushTransactionEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for transaction $entityId; will retry on next sync", e)
            }
        }
    }

    fun observeTransactions(): Flow<List<TransactionDetails>> =
        flow {
            emitAll(transactionDao.observeAll(AccountManager.currentUserIdOrEmpty()).map { list -> list.map { it.toDetails() } })
        }

    /** Live, offline-first list for one channel — instant from Room, refreshed by [refreshByChannel]. */
    fun observeByChannel(channelId: String): Flow<List<TransactionDetails>> =
        flow {
            val userId = AccountManager.currentUserIdOrEmpty()
            emitAll(transactionDao.observeByChannel(userId, channelId).map { list -> list.map { it.toDetails() } })
        }

    /**
     * Targeted pull, scoped to one channel — a cheaper alternative to a full-list pull for
     * [cc.dlabs.pesamind.features.settings.channels.ChannelDetailScreen], reusing
     * [reconcileFromServer] per row. [channelId] is passed straight through as
     * `resolvedChannelId`: unlike the full-pull case that primitive was built for, there's no
     * name-matching ambiguity here — every row in this response belongs to the channel we asked
     * for. Failures are left for the caller to surface; the local (possibly stale) list from
     * [observeByChannel] still renders regardless.
     */
    suspend fun refreshByChannel(channelId: String) {
        val response = ApiClient.api.getTransactionsByChannel(channelId)
        if (response.isSuccessful) {
            response.body()?.forEach { reconcileFromServer(it, resolvedChannelId = channelId) }
        }
    }

    /**
     * Targeted pull scoped to a date range (`startDate`/`endDate` as `yyyy-MM-dd`, matching the
     * backend's `/transactions/by-date-range` contract) — same shape as [refreshByChannel]
     * above, but unlike that one the channel id isn't already known here, so this needs the
     * same best-effort unique-name matching [SyncWorker.pullTransactions] already established
     * (reused via [resolveUniqueChannelIdsByName] rather than reimplemented). Failures are left
     * for the caller to surface; the local (possibly incomplete for this range) list from
     * [observeTransactions] still renders regardless — this only backfills any rows Room didn't
     * already have.
     */
    suspend fun refreshByDateRange(
        startDate: String,
        endDate: String,
    ) {
        val response = ApiClient.api.getTransactionsByDateRange(startDate, endDate)
        if (response.isSuccessful) {
            val channelIdByUniqueName =
                resolveUniqueChannelIdsByName(database.channelDao().getAllActive(AccountManager.currentUserIdOrEmpty()))
            response.body()?.forEach {
                reconcileFromServer(it, resolvedChannelId = channelIdByUniqueName[it.channelDetailsName])
            }
        }
    }

    /** One-shot read of the current list — used by `loadTransactions()`/`refresh()`, which
     * screens should otherwise prefer [observeTransactions] over for live updates. */
    suspend fun getAllTransactions(): List<TransactionDetails> =
        transactionDao.getAllActive(AccountManager.currentUserIdOrEmpty()).map { it.toDetails() }

    /**
     * Live income/expense/savings/net-movement summary for [year]/[month] (1-based), computed
     * entirely from Room — no network round-trip needed, and it updates the instant a
     * transaction is created/edited locally (`observeAll()`'s doc comment already anticipates
     * exactly this "summary totals" use case). Replaces `DashboardResponse.summary.data` for
     * the dashboard's headline card: those are the only fields in [SummaryData] this app can
     * actually derive from local transactions — anything requiring server-side computation
     * (financial health score, anomalies, spending velocity, streak) is deliberately out of
     * scope here and stays server-sourced.
     *
     * "Active categories" is counted as distinct non-null `channelId`s this month — this app
     * has no separate category concept; a transaction's only grouping dimension is its Channel
     * (Mobile Money/Bank/Cash), matching what the backend's own `CountActiveCategories` counts.
     *
     * Month boundaries use the device's local calendar, not UTC — this is now a fully local
     * computation with no server month-boundary convention to match.
     */
    fun observeMonthlySummary(
        year: Int,
        month: Int,
    ): Flow<SummaryData> {
        val (startInclusive, endExclusive) = monthRangeMillis(year, month)
        return flow {
            emitAll(
                transactionDao.observeAll(AccountManager.currentUserIdOrEmpty())
                    .map { list -> list.summarize(startInclusive, endExclusive, year, month) },
            )
        }
    }

    /**
     * Live weekday spending breakdown for the Analytics "Spending by Day of Week" card —
     * entirely local (no backend endpoint exists for this), reusing the same [observeAll] Room
     * flow [observeMonthlySummary] already established for client-side aggregation rather than
     * adding a new specialized SQL query. [startInclusive]/[endExclusive] scope the range to
     * match the Analytics screen's Month/Lifetime toggle; pass both null to aggregate over full
     * history.
     */
    fun observeSpendingByDayOfWeek(
        startInclusive: Long?,
        endExclusive: Long?,
    ): Flow<List<DayOfWeekSpend>> =
        flow {
            emitAll(
                transactionDao.observeAll(AccountManager.currentUserIdOrEmpty())
                    .map { list -> list.spendByDayOfWeek(startInclusive, endExclusive) },
            )
        }

    /** Live top-channels-by-spend ranking for the Analytics "Top Channels" card — same
     * rationale as [observeSpendingByDayOfWeek]. */
    fun observeTopChannelsBySpend(
        startInclusive: Long?,
        endExclusive: Long?,
        limit: Int = 5,
    ): Flow<List<ChannelSpend>> =
        flow {
            emitAll(
                transactionDao.observeAll(AccountManager.currentUserIdOrEmpty())
                    .map { list -> list.topChannelsBySpend(startInclusive, endExclusive, limit) },
            )
        }

    /**
     * [channelId] is the local `ChannelEntity.id` (UUID) — the creating code (manual entry or
     * SMS ingestion) always already knows which local channel this is, per ADR-0004's
     * "TransactionEntity.channelId is a nullable FK, resolved best-effort by name" section
     * (that best-effort path is only for server-pulled rows, not locally-created ones). The
     * display name is resolved here from Room rather than passed in, so the local row shows a
     * real channel name immediately without waiting on any sync.
     *
     * [smsSourceKey] and [providerTransactionId] both dedup against unique indices on
     * [TransactionEntity] (see its doc comment for why two separate keys exist) — but the
     * *correctness* mechanism is the atomic `INSERT ... ON CONFLICT IGNORE` below, not the
     * `find*` pre-checks: those are a fast-path optimization only (skip building a throwaway
     * entity when a hit is likely), since two concurrent calls for the same TID could otherwise
     * both pass the same pre-check before either has inserted. The [TransactionInsertOutcome]
     * return value reflects the real insert result, letting a caller (e.g.
     * `SMSMessageProcessor`) tell a genuine new row from a discarded duplicate without a
     * separate, racable verify step of its own.
     */
    suspend fun createTransaction(
        channelId: String,
        amount: Double,
        type: String,
        note: String,
        username: String,
        smsSourceKey: String? = null,
        providerTransactionId: String? = null,
    ): TransactionInsertOutcome {
        val now = System.currentTimeMillis()
        val userId = AccountManager.currentUserIdOrEmpty()
        val outcome =
            database.withTransaction {
                existingDuplicate(userId, channelId, smsSourceKey, providerTransactionId)
                    ?.let { return@withTransaction TransactionInsertOutcome.DuplicateDiscarded(it.toDetails()) }

                val channelName = database.channelDao().getById(channelId)?.name.orEmpty()
                val entity =
                    TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        serverId = null,
                        userId = userId,
                        channelId = channelId,
                        channelDetailsName = channelName,
                        amount = amount,
                        type = type,
                        note = note,
                        username = username,
                        smsSourceKey = smsSourceKey,
                        providerTransactionId = providerTransactionId,
                        syncStatus = SyncStatus.PENDING,
                        dirty = true,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                val rowId = transactionDao.insertIgnore(entity)
                if (rowId == -1L) {
                    // Lost a race against a concurrent insert sharing this smsSourceKey or
                    // (channelId, providerTransactionId) — surface the row that actually won.
                    val winner =
                        existingDuplicate(userId, channelId, smsSourceKey, providerTransactionId)
                            ?: error("insertIgnore reported a conflict but no matching row was found")
                    return@withTransaction TransactionInsertOutcome.DuplicateDiscarded(winner.toDetails())
                }
                outboxDao.upsert(
                    OutboxEntry(
                        id = UUID.randomUUID().toString(),
                        entityType = OutboxEntityType.TRANSACTION,
                        entityId = entity.id,
                        operation = OutboxOperation.CREATE,
                        status = SyncStatus.PENDING,
                        attempts = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                TransactionInsertOutcome.Inserted(entity.toDetails())
            }
        if (outcome is TransactionInsertOutcome.Inserted) {
            pushEagerly(outcome.transaction.id)
        }
        return outcome
    }

    private suspend fun existingDuplicate(
        userId: String,
        channelId: String,
        smsSourceKey: String?,
        providerTransactionId: String?,
    ): TransactionEntity? {
        smsSourceKey?.let { transactionDao.findBySmsSourceKey(userId, it) }?.let { return it }
        providerTransactionId?.let { transactionDao.findByChannelAndProviderTransactionId(channelId, it) }?.let { return it }
        return null
    }

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant: "one live row per serverId, never
     * overwrite a dirty=true row from a server payload"). Not called by anything in A1 —
     * written now, per the task brief, so Slice A2's full pull doesn't have to retrofit it.
     * [resolvedChannelId] is the best-effort name-matched local channel id (see
     * `PrefsToRoomMigrator.toEntity` for the exact matching logic this mirrors), or null if
     * unresolved.
     */
    suspend fun reconcileFromServer(
        details: TransactionDetails,
        resolvedChannelId: String?,
    ): TransactionDetails =
        database.withTransaction {
            val now = System.currentTimeMillis()
            // findByServerId deliberately includes soft-deleted rows so ReconcileResolver can
            // see (and refuse to touch) a tombstone instead of missing it and inserting a live
            // duplicate for the same serverId — see ReconcileResolver's doc comment.
            val existing = transactionDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            channelId = resolvedChannelId ?: existing.channelId,
                            channelDetailsName = details.channelDetailsName,
                            amount = details.amount,
                            type = details.type,
                            note = details.note,
                            username = details.username,
                            syncStatus = SyncStatus.SYNCED,
                            // Self-healing: any row previously inserted before this parser
                            // existed has createdAt wrongly stamped to whenever it happened to
                            // sync, not its real date — correct it here rather than only on a
                            // fresh insert, so already-corrupted local data heals on the very
                            // next ordinary sync instead of requiring another Room wipe.
                            createdAt = parseServerTimestampMillis(details.serverCreatedAt) ?: existing.createdAt,
                            updatedAt = now,
                        )
                    transactionDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    val inserted =
                        TransactionEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            // TransactionDetails carries no server userId field (unlike
                            // ChannelDetails/YearlyBudgetResponse) — safe to use the current
                            // session's own id regardless, since a pull only ever returns the
                            // authenticated user's own transactions.
                            userId = AccountManager.currentUserIdOrEmpty(),
                            channelId = resolvedChannelId,
                            channelDetailsName = details.channelDetailsName,
                            amount = details.amount,
                            type = details.type,
                            note = details.note,
                            username = details.username,
                            smsSourceKey = null,
                            providerTransactionId = null,
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            // The real transaction date, not "now" — see [parseServerTimestampMillis]
                            // and TransactionDetails.serverCreatedAt's doc comment for why this
                            // matters: local month-scoped queries (observeMonthlySummary,
                            // TransactionListScreen) filter by this column, so stamping it with
                            // "today" for every re-synced historical transaction (e.g. after a
                            // logout/Room-wipe) silently pulled the user's whole transaction
                            // history into whatever month they happened to resync in.
                            createdAt = parseServerTimestampMillis(details.serverCreatedAt) ?: now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    transactionDao.upsert(inserted)
                    inserted.toDetails()
                }
            }
        }
}

/** One weekday's total expense — [dayOfWeek] uses [Calendar] day constants
 * (1=[Calendar.SUNDAY]..7=[Calendar.SATURDAY]), matching [Calendar.DAY_OF_WEEK]'s own values. */
data class DayOfWeekSpend(
    val dayOfWeek: Int,
    val totalExpense: Double,
)

/** One channel's total expense + transaction count, for a top-channels ranking. */
data class ChannelSpend(
    val channelName: String,
    val totalExpense: Double,
    val transactionCount: Int,
)

private fun List<TransactionEntity>.spendByDayOfWeek(
    startInclusive: Long?,
    endExclusive: Long?,
): List<DayOfWeekSpend> {
    val totals = HashMap<Int, Double>()
    val cal = Calendar.getInstance()
    for (tx in this) {
        if (tx.type != TransactionTypes.EXPENSE) continue
        if (startInclusive != null && tx.createdAt < startInclusive) continue
        if (endExclusive != null && tx.createdAt >= endExclusive) continue
        cal.timeInMillis = tx.createdAt
        val day = cal.get(Calendar.DAY_OF_WEEK)
        totals[day] = (totals[day] ?: 0.0) + tx.amount
    }
    return (Calendar.SUNDAY..Calendar.SATURDAY).map { day ->
        DayOfWeekSpend(dayOfWeek = day, totalExpense = totals[day] ?: 0.0)
    }
}

private fun List<TransactionEntity>.topChannelsBySpend(
    startInclusive: Long?,
    endExclusive: Long?,
    limit: Int,
): List<ChannelSpend> {
    data class Acc(var total: Double, var count: Int)
    val byChannel = LinkedHashMap<String, Acc>()
    for (tx in this) {
        if (tx.type != TransactionTypes.EXPENSE) continue
        if (startInclusive != null && tx.createdAt < startInclusive) continue
        if (endExclusive != null && tx.createdAt >= endExclusive) continue
        val acc = byChannel.getOrPut(tx.channelDetailsName) { Acc(0.0, 0) }
        acc.total += tx.amount
        acc.count += 1
    }
    return byChannel.entries
        .sortedByDescending { it.value.total }
        .take(limit)
        .map { (name, acc) -> ChannelSpend(channelName = name, totalExpense = acc.total, transactionCount = acc.count) }
}

internal fun TransactionEntity.toDetails() =
    TransactionDetails(
        id = id,
        amount = amount,
        type = type,
        note = note,
        channelDetailsName = channelDetailsName,
        username = username,
        syncStatus = syncStatus,
        createdAt = createdAt,
    )

/** Go's `time.Time.String()` default format (e.g. "2026-07-12 19:51:33.525482 +0000 UTC") —
 * not ISO-8601: space-separated date/time, variable-length (0-9 digit) fractional seconds,
 * a numeric offset, then a redundant trailing zone abbreviation with no clean
 * [DateTimeFormatter] pattern of its own — [parseServerTimestampMillis] strips that last
 * token before parsing. */
private val SERVER_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendLiteral(' ')
        .appendOffset("+HHMM", "+0000")
        .toFormatter()

/** Parses [TransactionDetails.serverCreatedAt] into epoch millis, or null if absent/unparseable
 * — callers fall back to a locally-meaningful default (e.g. "now" on insert, or the existing
 * row's value on update) rather than propagating the failure. Internal, not private, so a JVM
 * test can exercise this hand-rolled non-ISO-8601 parser directly without needing Room. */
internal fun parseServerTimestampMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        val withoutZoneAbbreviation = raw.trim().substringBeforeLast(' ')
        OffsetDateTime.parse(withoutZoneAbbreviation, SERVER_TIMESTAMP_FORMATTER).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/** [Pair.first] is the month's start (inclusive), [Pair.second] is the following month's start
 * (exclusive) — a `[start, end)` range, avoiding any end-of-month day-count arithmetic. Not
 * private: reused by [cc.dlabs.pesamind.features.analytics.AnalyticsViewModel] to scope the
 * local day-of-week/top-channels aggregates to the same "This Month" window when its period
 * toggle is set to Month. */
internal fun monthRangeMillis(
    year: Int,
    month: Int,
): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month - 1, 1)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    val end = cal.timeInMillis
    return start to end
}

private fun List<TransactionEntity>.summarize(
    startInclusive: Long,
    endExclusive: Long,
    year: Int,
    month: Int,
): SummaryData {
    var income = 0L
    var expense = 0L
    var savings = 0L
    var count = 0
    val activeChannelIds = mutableSetOf<String>()
    for (tx in this) {
        if (tx.createdAt < startInclusive || tx.createdAt >= endExclusive) continue
        count++
        tx.channelId?.let { activeChannelIds += it }
        val amount = tx.amount.toLong()
        when (tx.type) {
            TransactionTypes.INCOME -> income += amount
            TransactionTypes.EXPENSE -> expense += amount
            TransactionTypes.SAVINGS -> savings += amount
        }
    }
    return SummaryData(
        totalIncome = income,
        totalExpense = expense,
        totalSavings = savings,
        // Matches the backend's own SummaryData.NetMovement formula (comprehensive_service.go):
        // income - expense - savings, not just income - expense.
        netMovement = income - expense - savings,
        transactionCount = count,
        activeCategories = activeChannelIds.size,
        currentMonth = "%04d-%02d".format(year, month),
    )
}
