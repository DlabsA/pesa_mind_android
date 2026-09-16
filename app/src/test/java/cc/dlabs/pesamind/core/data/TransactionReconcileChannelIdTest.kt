package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Covers the fix for a real bug report: two channels sharing a name/description (e.g. two
 * "MTN Mobile Money" channels for two SIMs) caused a transaction's `channelId` to get silently
 * flipped to the wrong channel on a later background sync. Root cause: `reconcileFromServer`'s
 * `UpdateExisting` branch let a fresh best-effort name-matched [resolvedChannelId] guess (the
 * server's `TransactionDetails` carries no real channel id, only a free-text name snapshot)
 * unconditionally overwrite an already-known-correct `channelId`. Backed by a real in-memory
 * Room database, same pattern as [TransactionProviderIdDedupTest].
 */
@RunWith(RobolectricTestRunner::class)
class TransactionReconcileChannelIdTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        TransactionRepository.database = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun channel(
        id: String,
        name: String,
    ) = ChannelEntity(
        id = id,
        serverId = null,
        userId = "user-1",
        name = name,
        channelType = "MOBILE_MONEY",
        description = "0700000000",
        status = true,
        channelDesc = "MTN Mobile Money",
        normalizedSenderKey = "mtn mobile money",
        availableBalance = 0.0,
        accountNumber = null,
        smsNotificationEnabled = true,
        syncStatus = SyncStatus.SYNCED,
        dirty = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )

    @Test
    fun `a clean synced row keeps its known channelId even when the name-matched guess resolves elsewhere`() =
        runBlocking {
            val correctChannelId = UUID.randomUUID().toString()
            val otherChannelId = UUID.randomUUID().toString()
            db.channelDao().insertIgnore(channel(correctChannelId, "main MTN"))
            db.channelDao().insertIgnore(channel(otherChannelId, "MTN Mobile Money"))

            val existing =
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    serverId = "server-1",
                    userId = "user-1",
                    channelId = correctChannelId,
                    channelDetailsName = "main MTN",
                    amount = 5000.0,
                    type = "expense",
                    note = "note",
                    username = "jane",
                    smsSourceKey = null,
                    providerTransactionId = null,
                    syncStatus = SyncStatus.SYNCED,
                    // Clean/synced (not dirty), so ReconcileResolver routes this to UpdateExisting.
                    dirty = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                )
            db.transactionDao().upsert(existing)

            // The server payload's name snapshot doesn't match the current local name of the
            // transaction's real channel — a name-based lookup would (wrongly) resolve to
            // otherChannelId, exactly the scenario that used to flip channelId.
            TransactionRepository.reconcileFromServer(
                details =
                    TransactionDetails(
                        id = "server-1",
                        amount = 5000.0,
                        type = "expense",
                        note = "note",
                        channelDetailsName = "MTN Mobile Money",
                        username = "jane",
                    ),
                resolvedChannelId = otherChannelId,
            )

            assertEquals(
                "an already-known channelId must survive reconcile even when the name-matched guess points elsewhere",
                correctChannelId,
                db.transactionDao().findByServerId("server-1")?.channelId,
            )
        }

    @Test
    fun `a row with no channelId yet still gets backfilled from the resolved guess`() =
        runBlocking {
            val guessedChannelId = UUID.randomUUID().toString()
            db.channelDao().insertIgnore(channel(guessedChannelId, "MTN Mobile Money"))

            val existing =
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    serverId = "server-2",
                    userId = "user-1",
                    channelId = null,
                    channelDetailsName = "MTN Mobile Money",
                    amount = 2000.0,
                    type = "expense",
                    note = "note",
                    username = "jane",
                    smsSourceKey = null,
                    providerTransactionId = null,
                    syncStatus = SyncStatus.SYNCED,
                    dirty = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                )
            db.transactionDao().upsert(existing)

            TransactionRepository.reconcileFromServer(
                details =
                    TransactionDetails(
                        id = "server-2",
                        amount = 2000.0,
                        type = "expense",
                        note = "note",
                        channelDetailsName = "MTN Mobile Money",
                        username = "jane",
                    ),
                resolvedChannelId = guessedChannelId,
            )

            assertEquals(
                "a still-unresolved channelId must be filled in from the name-matched guess",
                guessedChannelId,
                db.transactionDao().findByServerId("server-2")?.channelId,
            )
        }
}
