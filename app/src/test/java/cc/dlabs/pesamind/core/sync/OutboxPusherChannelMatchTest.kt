package cc.dlabs.pesamind.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.database.entity.OutboxEntityType
import cc.dlabs.pesamind.core.database.entity.OutboxEntry
import cc.dlabs.pesamind.core.database.entity.OutboxOperation
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.UUID

/**
 * Covers the fix for a confirmed production bug: creating a second channel for a provider you
 * already have one of (e.g. a second Airtel Money channel for a different SIM) could get
 * silently stamped with the *existing*, unrelated channel's `serverId` — because
 * [OutboxPusher.findExistingServerChannel] used to accept a lone same-provider server candidate
 * as a match without ever checking its phone number. That shared `serverId` is what then let a
 * later sync pull overwrite one channel's name/description/balance with the other's — see
 * `MIGRATION_9_10`'s doc comment for the full mechanism. Backed by a real in-memory Room
 * database (same pattern as [cc.dlabs.pesamind.core.data.TransactionProviderIdDedupTest]) and a
 * mocked [ApiService], `runBlocking` (never `runTest`, per `.claude/CLAUDE.md`'s Testing
 * section).
 */
@RunWith(RobolectricTestRunner::class)
class OutboxPusherChannelMatchTest {
    private lateinit var db: PesaMindDatabase
    private lateinit var api: ApiService
    private lateinit var pusher: OutboxPusher

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        api = mock()
        pusher = OutboxPusher(db, api)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedPendingCreate(entity: ChannelEntity) {
        db.channelDao().insertIgnore(entity)
        db.outboxDao().upsert(
            OutboxEntry(
                id = UUID.randomUUID().toString(),
                entityType = OutboxEntityType.CHANNEL,
                entityId = entity.id,
                operation = OutboxOperation.CREATE,
                status = SyncStatus.PENDING,
                attempts = 0,
                lastError = null,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            ),
        )
    }

    private fun newChannelEntity(
        id: String,
        receivingNumber: String,
        now: Long,
    ) = ChannelEntity(
        id = id,
        serverId = null,
        userId = "user-1",
        name = "Airtel test",
        channelType = "MOBILE_MONEY",
        description = "0700175388",
        status = true,
        channelDesc = "Airtel Money",
        normalizedSenderKey = "airtel money",
        availableBalance = 0.0,
        accountNumber = "0700175388",
        receivingNumber = receivingNumber,
        smsNotificationEnabled = true,
        syncStatus = SyncStatus.PENDING,
        dirty = true,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    @Test
    fun `a lone same-provider server candidate with a different number is not treated as a match`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val newLocalChannel = newChannelEntity(id = "local-airtel-test", receivingNumber = "700175388", now = now)
            seedPendingCreate(newLocalChannel)

            // Server has exactly ONE Airtel Money channel — a different, pre-existing one.
            whenever(api.getChannels()).thenReturn(
                Response.success(
                    listOf(
                        ChannelDetails(
                            id = "server-existing-airtel",
                            name = "Airtel Money",
                            channelType = "MOBILE_MONEY",
                            description = "0755175388",
                            channelDesc = "Airtel Money",
                            accountNumber = "0755175388",
                        ),
                    ),
                ),
            )
            whenever(api.createChannel(any())).thenReturn(
                Response.success(ChannelDetails(id = "server-new-airtel-test", channelDesc = "Airtel Money")),
            )

            pusher.pushChannelEntry(newLocalChannel.id)

            val updated = db.channelDao().getById(newLocalChannel.id)
            assertNotEquals(
                "the new channel must never borrow the unrelated existing channel's serverId",
                "server-existing-airtel",
                updated?.serverId,
            )
            assertEquals("a real create call must have been made instead", "server-new-airtel-test", updated?.serverId)
            verify(api).createChannel(any())
            Unit
        }

    @Test
    fun `a lone same-provider server candidate with the matching number is reused, not duplicated`() =
        runBlocking {
            val now = System.currentTimeMillis()
            val newLocalChannel = newChannelEntity(id = "local-airtel-retry", receivingNumber = "755175388", now = now)
            seedPendingCreate(newLocalChannel)

            // Server already has this exact channel (e.g. created from another device) — same
            // provider AND same number.
            whenever(api.getChannels()).thenReturn(
                Response.success(
                    listOf(
                        ChannelDetails(
                            id = "server-existing-airtel",
                            name = "Airtel Money",
                            channelType = "MOBILE_MONEY",
                            description = "0755175388",
                            channelDesc = "Airtel Money",
                            accountNumber = "0755175388",
                        ),
                    ),
                ),
            )

            pusher.pushChannelEntry(newLocalChannel.id)

            val updated = db.channelDao().getById(newLocalChannel.id)
            assertEquals(
                "a genuinely matching lone candidate must still be reused, not duplicated",
                "server-existing-airtel",
                updated?.serverId,
            )
            verify(api, never()).createChannel(any<CreateChannelRequest>())
            Unit
        }
}
