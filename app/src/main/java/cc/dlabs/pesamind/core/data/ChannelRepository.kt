package cc.dlabs.pesamind.core.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import cc.dlabs.pesamind.core.database.CoalesceDecision
import cc.dlabs.pesamind.core.database.ExistingRowSnapshot
import cc.dlabs.pesamind.core.database.OutboxCoalescer
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.ReconcileDecision
import cc.dlabs.pesamind.core.database.ReconcileResolver
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.OutboxPusher
import cc.dlabs.pesamind.core.utils.PhoneNumberNormalizer
import cc.dlabs.pesamind.features.settings.channels.ChannelLimits
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Outcome of [ChannelRepository.createChannel] — distinguishes a genuine new row from one
 * discarded by the atomic `normalizedSenderKey` dedup check, so a caller like
 * `ChannelViewModel` can tell the user their request was deduped against an existing channel
 * instead of reporting a false "created successfully" for a request that inserted nothing.
 */
sealed class ChannelCreateOutcome {
    data class Created(val channel: ChannelDetails) : ChannelCreateOutcome()

    data class AlreadyExists(val existing: ChannelDetails) : ChannelCreateOutcome()

    /** Free-tier cap for [channelType] already reached — nothing was created. [limit] is
     * echoed back so callers can build a message without re-deriving it from
     * [cc.dlabs.pesamind.features.settings.channels.ChannelLimits]. */
    data class LimitReached(val channelType: String, val limit: Int) : ChannelCreateOutcome()
}

/**
 * Room-backed source of truth for channels (ADR-0004 Slice A1) — replaces `ChannelManager`'s
 * DataStore blob for every `ChannelViewModel` call site.
 *
 * This crosses the repository-layer threshold `.claude/CLAUDE.md` sets ("do not introduce a
 * repository for a single feature in isolation... only when a domain has real cross-feature
 * reuse or multi-source merging") because Room here is a genuine multi-source merge point:
 * local cache + a durable outbox + a future sync worker (Slice A2), consumed by more than one
 * feature — `ChannelViewModel` AND the SMS auto-creation lookup in
 * `ChannelManager.isSmsAllowedForSender` (kept in sync via [findByNormalizedSenderKey]/
 * [reconcileFromServer] below, since that's the one remaining `ChannelManager` caller and it
 * would otherwise see an increasingly stale channel list now that `ChannelViewModel` no longer
 * writes through `ChannelManager` at all).
 *
 * Plain singleton object + [init], mirroring the existing manager pattern (`ChannelManager`,
 * `TokenManager`, ...) rather than Hilt constructor injection — `ChannelViewModel` is a plain
 * `ViewModel()` reached via `viewModel()` in Compose screens, not `hiltViewModel()`, and
 * converting every call site to Hilt-injected ViewModels is a wider change than this slice's
 * scope. Room access itself still goes through the Hilt-provided [PesaMindDatabase] via
 * [DatabaseEntryPoint], the same pattern `PrefsToRoomMigrator` already established.
 */
object ChannelRepository {
    private const val TAG = "ChannelRepository"

    // internal, not private: lets a JVM test inject a mocked PesaMindDatabase/DAO directly
    // (no Android runtime / device available to run a real Room in-memory-database test).
    internal lateinit var database: PesaMindDatabase
    private val channelDao get() = database.channelDao()
    private val outboxDao get() = database.outboxDao()

    // Nullable, not lateinit: JVM unit tests inject `database` directly without calling [init]
    // (no Android Context available off-device), so an eager push must be a safe no-op then,
    // not a crash — those tests already exercise the outbox/background-sync path instead.
    private var networkMonitor: NetworkMonitor? = null
    private var outboxPusher: OutboxPusher? = null

    /**
     * The one `channelType` value that legitimately allows many rows sharing the same
     * `channelDesc` ("Cash") — every other value represents a real-world provider/bank sender
     * that must get a non-null [ChannelEntity.normalizedSenderKey]. Derived internally from
     * [ChannelDetails.channelType]/the raw `channelType` param rather than a caller-supplied
     * flag: an earlier version of this fix took an explicit `isProviderChannel` parameter, and
     * both `SyncWorker`'s full-pull reconciliation and `PrefsToRoomMigrator`'s one-time import
     * simply never passed `true` — meaning every already-synced or migrated provider channel
     * (i.e. every existing user's data, not just fresh installs) kept `normalizedSenderKey =
     * null` forever, silently making the whole dedup fix inert for the exact users it targets
     * most. Deriving it here instead means every caller gets the guarantee automatically.
     *
     * Deliberately NOT applied to `PrefsToRoomMigrator`'s bulk one-time import — see that call
     * site's own comment for why a bulk `REPLACE`-strategy insert makes this column unsafe to
     * populate there without real duplicate-collision handling (Phase 3, not this pass).
     */
    private const val CASH_CHANNEL_TYPE = "Cash"

    private fun isProviderChannelType(channelType: String): Boolean = channelType != CASH_CHANNEL_TYPE

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
        networkMonitor = NetworkMonitor(context.applicationContext)
        outboxPusher = OutboxPusher(database, ApiClient.api)
    }

    /**
     * Best-effort immediate push, fired right after a create/update/delete's local commit when
     * the device is already known to be online — see [TransactionRepository]'s twin for the
     * full rationale. Never awaited by the caller and never lets a network hiccup surface as a
     * failure of the local write; the outbox row this pushes stays as the durable fallback.
     */
    private fun pushEagerly(entityId: String) {
        val monitor = networkMonitor ?: return
        val pusher = outboxPusher ?: return
        if (!monitor.isConnectedNow) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pusher.pushChannelEntry(entityId)
            } catch (e: Exception) {
                Log.w(TAG, "Eager push failed for channel $entityId; will retry on next sync", e)
            }
        }
    }

    fun observeChannels(): Flow<List<ChannelDetails>> =
        flow {
            emitAll(channelDao.observeAll(currentUserId()).map { list -> list.map { it.toDetails() } })
        }

    /** One-shot read of the current full (unfiltered) list — used by `loadChannels()` to reset
     * out of a `getByChannelType`/`getByActiveStatus` filtered view. Screens should otherwise
     * prefer [observeChannels] for live updates. */
    suspend fun getAllChannels(): List<ChannelDetails> = channelDao.getAllActive(currentUserId()).map { it.toDetails() }

    /** One-shot single-channel lookup for `ChannelDetailViewModel` — there's no
     * `GET /categories/:id` on the backend, so this is Room-only, no network fallback needed. */
    suspend fun getById(id: String): ChannelDetails? = channelDao.getById(id)?.toDetails()

    /**
     * The server-side id for a local channel row — the value any `categories/{id}` API path
     * needs. [toDetails] maps the *local* Room UUID onto `ChannelDetails.id` and drops
     * [ChannelEntity.serverId], and the two only coincide for channels this device created (the
     * backend honours the client-supplied id on create). A channel pulled from the server gets a
     * fresh local UUID, so anything addressing the API by id must resolve through here.
     *
     * Null means the row's CREATE hasn't synced yet — there is no server-side channel to address.
     */
    suspend fun serverIdFor(localId: String): String? = channelDao.getById(localId)?.serverId

    suspend fun getByChannelType(channelType: String): List<ChannelDetails> =
        channelDao.getByChannelType(
            currentUserId(),
            channelType,
        ).map { it.toDetails() }

    suspend fun getByActiveStatus(active: Boolean): List<ChannelDetails> =
        channelDao.getByActiveStatus(currentUserId(), active).map { it.toDetails() }

    /**
     * Every non-CASH [channelType] gets a non-null [ChannelEntity.normalizedSenderKey] and the
     * atomic dedup-on-insert guarantee that comes with it (see [ChannelEntity]'s doc comment) —
     * CASH channels share a single `channelDesc` ("Cash") across many legitimate rows and must
     * never be forced unique. Non-provider callers get exactly the old unconditional-insert
     * behavior (a fresh UUID primary key never collides).
     *
     * Free-tier accounts are capped per channel type (see
     * [cc.dlabs.pesamind.features.settings.channels.ChannelLimits]) — checked live via
     * [AccountManager.isPremium] on every call, not a cached flag, so a lapsed trial or an
     * about-to-expire subscription can't be used to sneak past the cap. Premium/Enterprise
     * remain unlimited.
     */
    suspend fun createChannel(
        name: String,
        description: String,
        channelType: String,
        channelDesc: String,
        status: Boolean,
        accountNumber: String? = null,
        openingBalance: Double = 0.0,
        // Normalized separately from [accountNumber] even though they're usually the same
        // user-entered value — see ChannelEntity's doc comment for why this participates in
        // uniqueness and [accountNumber] doesn't. Callers with a resolved receiving number
        // (SMS ingestion) or a user-entered account number (onboarding/settings) should pass
        // the same normalized value for both.
        receivingNumber: String = ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER,
    ): ChannelCreateOutcome {
        val userId = currentUserId()
        val limit = ChannelLimits.freeLimitFor(channelType)
        if (limit != null && !AccountManager.isPremium()) {
            val currentCount = channelDao.countByUserIdAndChannelType(userId, channelType)
            if (currentCount >= limit) {
                return ChannelCreateOutcome.LimitReached(channelType, limit)
            }
        }
        val now = System.currentTimeMillis()
        val normalizedKey = if (isProviderChannelType(channelType)) normalizeSenderKey(channelDesc) else null
        val outcome =
            database.withTransaction {
                if (normalizedKey != null) {
                    channelDao.findByNormalizedSenderKeyAndReceivingNumber(userId, normalizedKey, receivingNumber)?.let {
                        // Found a pre-existing row under this exact provider+number pair — live
                        // or (rarely, see ChannelEntity's doc comment) soft-deleted. Either way,
                        // nothing is inserted, so the caller must be told "already exists," not
                        // "created" — see ChannelViewModel's handling of AlreadyExists.
                        return@withTransaction ChannelCreateOutcome.AlreadyExists(it.toDetails())
                    }
                }
                val entity =
                    ChannelEntity(
                        id = UUID.randomUUID().toString(),
                        serverId = null,
                        userId = userId,
                        name = name,
                        channelType = channelType,
                        description = description,
                        status = status,
                        channelDesc = channelDesc,
                        normalizedSenderKey = normalizedKey,
                        availableBalance = openingBalance,
                        accountNumber = accountNumber,
                        receivingNumber = receivingNumber,
                        smsNotificationEnabled = true,
                        syncStatus = SyncStatus.PENDING,
                        dirty = true,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                val rowId = channelDao.insertIgnore(entity)
                if (rowId == -1L) {
                    // Lost a race against a concurrent insert for the same provider+number —
                    // return the row that actually won instead of a second, discarded one.
                    val winner =
                        channelDao.findByNormalizedSenderKeyAndReceivingNumber(userId, normalizedKey!!, receivingNumber)!!
                    return@withTransaction ChannelCreateOutcome.AlreadyExists(winner.toDetails())
                }
                outboxDao.upsert(newOutboxEntry(OutboxEntityType.CHANNEL, entity.id, OutboxOperation.CREATE, now))
                ChannelCreateOutcome.Created(entity.toDetails())
            }
        if (outcome is ChannelCreateOutcome.Created) {
            pushEagerly(outcome.channel.id)
        }
        return outcome
    }

    /**
     * [channelDesc]'s [ChannelEntity.normalizedSenderKey] is recomputed here the same way
     * [createChannel]/[reconcileFromServer] derive it, so correcting a provider doesn't leave
     * a stale dedup key pointing at the old one behind — see [isProviderChannelType]'s doc
     * comment for why that derivation must stay automatic rather than caller-supplied.
     */
    suspend fun updateChannel(
        id: String,
        name: String,
        description: String,
        channelDesc: String,
        status: Boolean,
        // Null means "leave as-is" (most callers only touch name/description/channelDesc/status
        // today) — pass an explicit value to actually change the account number, since this
        // also drives [ChannelEntity.receivingNumber] and thus the provider-uniqueness key.
        accountNumber: String? = null,
    ): ChannelDetails? {
        val updated =
            database.withTransaction {
                val existing = channelDao.getById(id) ?: return@withTransaction null
                val now = System.currentTimeMillis()
                val updated =
                    existing.copy(
                        name = name,
                        description = description,
                        channelDesc = channelDesc,
                        normalizedSenderKey =
                            if (isProviderChannelType(existing.channelType)) normalizeSenderKey(channelDesc) else null,
                        status = status,
                        accountNumber = accountNumber ?: existing.accountNumber,
                        receivingNumber =
                            accountNumber?.let {
                                PhoneNumberNormalizer.normalize(it) ?: ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER
                            } ?: existing.receivingNumber,
                        dirty = true,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = now,
                    )
                channelDao.update(updated)
                enqueueOutbox(OutboxEntityType.CHANNEL, id, OutboxOperation.UPDATE, now)
                updated.toDetails()
            }
        if (updated != null) {
            pushEagerly(id)
        }
        return updated
    }

    suspend fun deleteChannel(id: String): Boolean {
        var shouldPush = false
        val result =
            database.withTransaction {
                val existing = channelDao.getById(id) ?: return@withTransaction false
                val now = System.currentTimeMillis()
                val existingOutbox = outboxDao.findFor(OutboxEntityType.CHANNEL, id)
                when (
                    val decision =
                        OutboxCoalescer.coalesce(existingOutbox?.operation, existingOutbox?.status, OutboxOperation.DELETE)
                ) {
                    is CoalesceDecision.HardDeleteNoOutbox -> {
                        outboxDao.deleteFor(OutboxEntityType.CHANNEL, id)
                        channelDao.hardDelete(id)
                    }
                    is CoalesceDecision.WriteOutbox -> {
                        channelDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                        outboxDao.upsert(
                            upsertedOutboxEntry(existingOutbox, OutboxEntityType.CHANNEL, id, decision.operation, now),
                        )
                        shouldPush = true
                    }
                    is CoalesceDecision.LeaveInFlight -> {
                        // An existing push is already SYNCING for this row — nothing new to push.
                        channelDao.update(existing.copy(deletedAt = now, dirty = true, syncStatus = SyncStatus.PENDING, updatedAt = now))
                    }
                }
                true
            }
        if (shouldPush) {
            pushEagerly(id)
        }
        return result
    }

    /**
     * Purely local — [enabled] has no backend representation at all (`CreateChannelRequest`/
     * `UpdateChannelRequest` never carry it, confirmed against the actual backend, which has no
     * such field either), so unlike every other mutation in this file, this deliberately does
     * NOT set `dirty`/`syncStatus = PENDING` or enqueue an outbox entry: there is nothing to
     * sync, and doing so previously left the channel showing "pending sync" for as long as it
     * took the next unrelated periodic drain to re-send the unchanged name/description/status
     * and clear it — a real network round-trip that never actually transmitted this field.
     */
    suspend fun setSmsNotificationEnabled(
        id: String,
        enabled: Boolean,
    ): ChannelDetails? =
        database.withTransaction {
            val existing = channelDao.getById(id) ?: return@withTransaction null
            val updated = existing.copy(smsNotificationEnabled = enabled, updatedAt = System.currentTimeMillis())
            channelDao.update(updated)
            updated.toDetails()
        }

    /**
     * Case-insensitive, local-only, **live-channels-only** lookup for provider/bank channels
     * (mobile money, bank), keyed on the normalized form [ChannelEntity.normalizedSenderKey]
     * stores, disambiguated by [receivingNumber] when more than one channel shares a provider.
     * Used by [cc.dlabs.pesamind.core.storage.ChannelManager.isSmsAllowedForSender] to decide
     * whether an active channel already exists for a sender+number before attaching a
     * transaction to it — deliberately excludes soft-deleted rows (unlike the DAO-level
     * [ChannelDao.findByNormalizedSenderKeyAndReceivingNumber] used internally by
     * [createChannel]/[reconcileFromServer]'s conflict resolution), so a channel the user
     * deleted is never silently treated as active.
     *
     * Resolution order:
     * 1. Exact `(normalizedSenderKey, receivingNumber)` match — the common case once numbers are
     *    resolved/entered.
     * 2. If that misses (including when [receivingNumber] is the `UNSPECIFIED` sentinel because
     *    it couldn't be resolved for this SMS), fall back to every live channel sharing the
     *    provider: exactly one → no real ambiguity, return it; zero → null (falls through to
     *    auto-create); **more than one → null**, never guess which one an incoming SMS belongs
     *    to. A dropped/unattributed SMS is strictly better than silently corrupting the wrong
     *    account's balance.
     */
    suspend fun findByNormalizedSenderKey(
        channelDesc: String,
        receivingNumber: String = ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER,
    ): ChannelDetails? {
        val userId = currentUserId()
        val key = normalizeSenderKey(channelDesc)
        channelDao.findLiveByNormalizedSenderKeyAndReceivingNumber(userId, key, receivingNumber)?.let {
            return it.toDetails()
        }
        val candidates = channelDao.findAllLiveByNormalizedSenderKey(userId, key)
        return when (candidates.size) {
            0 -> null
            1 -> candidates.first().toDetails()
            else -> {
                Log.w(
                    TAG,
                    "Ambiguous channel match for provider key '$key': ${candidates.size} live channels, " +
                        "receiving number '$receivingNumber' didn't exact-match any — refusing to guess",
                )
                null
            }
        }
    }

    /** Trim+lowercase fold used for [ChannelEntity.normalizedSenderKey] — see its doc comment. */
    fun normalizeSenderKey(channelDesc: String): String = channelDesc.trim().lowercase(Locale.ROOT)

    /**
     * True when 2+ live channels already share [channelDesc]'s provider key — i.e. a `null`
     * from [findByNormalizedSenderKey] means genuine ambiguity (multiple candidates, none an
     * exact receiving-number match), not "no channel exists yet for this provider." Used by
     * [cc.dlabs.pesamind.core.storage.ChannelManager.isSmsAllowedForSender] to decide whether
     * that `null` should fall through to auto-create (the "no channel yet" case) or drop the
     * SMS instead (the ambiguous case) — auto-creating here would silently produce a *third*
     * channel for the provider rather than either guessing or dropping, which is worse than
     * both of the outcomes [findByNormalizedSenderKey]'s doc comment already commits to.
     */
    suspend fun hasAmbiguousChannelsForProvider(channelDesc: String): Boolean {
        val candidates = channelDao.findAllLiveByNormalizedSenderKey(currentUserId(), normalizeSenderKey(channelDesc))
        return candidates.size > 1
    }

    /**
     * Pull-reconciliation primitive (ADR-0004 invariant: "one live row per serverId, never
     * overwrite a dirty=true row from a server payload"). Used today by
     * `ChannelManager.isSmsAllowedForSender`'s auto-create path so a server-created channel
     * becomes visible to future Room-based lookups; `SyncWorker`'s full pull reuses this per row
     * too — [isProviderChannelType] is derived from `details.channelType` here (see its doc
     * comment for why that must be automatic, not caller-supplied), so both callers get the
     * same dedup guarantee without either needing to know or pass a flag.
     *
     * The [ChannelEntity.normalizedSenderKey] conflict check applies only in the [InsertNew]
     * branch below, not [UpdateExisting] — deliberately. An earlier version of this method
     * checked it *before* the `serverId` lookup, which meant a row that already has this exact
     * key set (from a previous reconcile) would find *itself* on every later pull and
     * short-circuit before ever reaching `UpdateExisting`'s actual field refresh, permanently
     * freezing that row's name/description/status against future server-side edits. Backfilling
     * the key onto an *existing* row's `UpdateExisting` update was also considered and rejected:
     * `channelDao.update` has no `onConflict` strategy (Room defaults `@Update` to `ABORT`), so
     * if two already-locally-known duplicate rows (a real, plausible state for existing users —
     * see ADR-0004) both get backfilled across separate pull cycles, the second `update()` would
     * throw a `SQLiteConstraintException` instead of resolving gracefully. Only [InsertNew] uses
     * the atomic insertIgnore-or-discard path that's actually built to handle that collision
     * safely; a genuinely new server row can independently reach the network-create step (this
     * method's caller may have already missed the local lookup once), so the transaction-scoped
     * check immediately before inserting is what closes that race, not the caller's earlier miss.
     */
    suspend fun reconcileFromServer(details: ChannelDetails): ChannelDetails =
        database.withTransaction {
            val now = System.currentTimeMillis()
            // findByServerId deliberately includes soft-deleted rows so ReconcileResolver
            // can see (and refuse to touch) a tombstone instead of missing it and inserting
            // a live duplicate for the same serverId — see ReconcileResolver's doc comment.
            val existing = channelDao.findByServerId(details.id)
            when (ReconcileResolver.resolve(existing?.let { ExistingRowSnapshot(it.dirty, it.deletedAt) })) {
                ReconcileDecision.SkipDirtyOrDeleted -> existing!!.toDetails()
                ReconcileDecision.UpdateExisting -> {
                    val updated =
                        existing!!.copy(
                            name = details.name,
                            channelType = details.channelType,
                            description = details.description,
                            status = details.status,
                            channelDesc = details.channelDesc,
                            // Server-computed, never written locally — always refreshed here.
                            availableBalance = details.availableBalance,
                            accountNumber = details.accountNumber,
                            // Not backfilled here — see this method's doc comment for why an
                            // already-known row's normalizedSenderKey/receivingNumber are left
                            // exactly as-is (same reasoning applies to receivingNumber: the
                            // server has no dedicated field for it, and blindly re-deriving it
                            // from accountNumber on every pull risks the same freeze-on-conflict
                            // hazard this method's doc comment already worked through).
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now,
                        )
                    channelDao.update(updated)
                    updated.toDetails()
                }
                ReconcileDecision.InsertNew -> {
                    // A pull only ever returns the authenticated user's own channels, so the
                    // current session's id is always correct — details.userId (the server's
                    // echoed user_id) must not be trusted for local scoping, since every read
                    // query filters by AccountManager's cached id, not the server's, and a
                    // mismatch here silently orphans the row (invisible to every screen, no
                    // crash, no retry fixes it). Mirrors TransactionRepository.reconcileFromServer.
                    val sessionUserId = currentUserId()
                    val normalizedKey =
                        if (isProviderChannelType(details.channelType)) normalizeSenderKey(details.channelDesc) else null
                    // Server has no dedicated receiving-number field — best-effort derive one
                    // from account_number (the same value onboarding/settings enter) so a
                    // server-pulled row still participates correctly in the local
                    // provider+number uniqueness, falling back to the sentinel when unresolvable.
                    val receivingNumber =
                        PhoneNumberNormalizer.normalize(details.accountNumber) ?: ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER
                    if (normalizedKey != null) {
                        channelDao.findByNormalizedSenderKeyAndReceivingNumber(sessionUserId, normalizedKey, receivingNumber)
                            ?.let {
                                // A different serverId, same real provider+number — a
                                // pre-existing local duplicate (plausible for existing users, see
                                // ADR-0004) or a concurrent SMS auto-create that already won.
                                // Either way, this pulled row must not become a second local row
                                // for the same provider+number.
                                return@withTransaction it.toDetails()
                            }
                    }
                    val inserted =
                        ChannelEntity(
                            id = UUID.randomUUID().toString(),
                            serverId = details.id,
                            userId = sessionUserId,
                            name = details.name,
                            channelType = details.channelType,
                            description = details.description,
                            status = details.status,
                            channelDesc = details.channelDesc,
                            normalizedSenderKey = normalizedKey,
                            availableBalance = details.availableBalance,
                            accountNumber = details.accountNumber,
                            receivingNumber = receivingNumber,
                            smsNotificationEnabled = details.smsNotificationEnabled,
                            syncStatus = SyncStatus.SYNCED,
                            dirty = false,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    val rowId = channelDao.insertIgnore(inserted)
                    if (rowId == -1L) {
                        // Lost a race against a concurrent insert for the same provider+number.
                        channelDao.findByNormalizedSenderKeyAndReceivingNumber(sessionUserId, normalizedKey!!, receivingNumber)!!
                            .toDetails()
                    } else {
                        inserted.toDetails()
                    }
                }
            }
        }

    /** Current user id, for both stamping locally-created rows and scoping every read query to
     * the logged-in account. See [AccountManager.currentUserIdOrEmpty] for the swallow-to-empty
     * semantics. */
    private suspend fun currentUserId(): String = AccountManager.currentUserIdOrEmpty()

    private suspend fun enqueueOutbox(
        entityType: OutboxEntityType,
        entityId: String,
        newOp: OutboxOperation,
        now: Long,
    ) {
        val existing = outboxDao.findFor(entityType, entityId)
        when (val decision = OutboxCoalescer.coalesce(existing?.operation, existing?.status, newOp)) {
            is CoalesceDecision.WriteOutbox ->
                outboxDao.upsert(
                    upsertedOutboxEntry(existing, entityType, entityId, decision.operation, now),
                )
            is CoalesceDecision.LeaveInFlight -> Unit
            is CoalesceDecision.HardDeleteNoOutbox ->
                error("Unreachable: UPDATE-family coalescing against $entityType/$entityId never resolves to a hard delete")
        }
    }

    private fun newOutboxEntry(
        entityType: OutboxEntityType,
        entityId: String,
        operation: OutboxOperation,
        now: Long,
    ) = OutboxEntry(
        id = UUID.randomUUID().toString(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        status = SyncStatus.PENDING,
        attempts = 0,
        lastError = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun upsertedOutboxEntry(
        existing: OutboxEntry?,
        entityType: OutboxEntityType,
        entityId: String,
        operation: OutboxOperation,
        now: Long,
    ) = OutboxEntry(
        id = existing?.id ?: UUID.randomUUID().toString(),
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        status = SyncStatus.PENDING,
        attempts = existing?.attempts ?: 0,
        lastError = null,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}

internal fun ChannelEntity.toDetails() =
    ChannelDetails(
        id = id,
        userId = userId,
        name = name,
        channelType = channelType,
        description = description,
        status = status,
        channelDesc = channelDesc,
        availableBalance = availableBalance,
        accountNumber = accountNumber,
        smsNotificationEnabled = smsNotificationEnabled,
        syncStatus = syncStatus,
    )
