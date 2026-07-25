package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the channel-auto-create half of the fix: a real sender must never resolve to two
 * different local [cc.dlabs.pesamind.core.database.entity.ChannelEntity] rows just because two
 * SMS happened to reach [ChannelRepository.createChannel]/[ChannelRepository.findByNormalizedSenderKey]
 * with differently-cased `channelDesc` strings. Backed by a real in-memory Room database
 * (Robolectric-provided [Context], not a mock) so the actual `normalizedSenderKey` unique index
 * and `insertIgnore` atomic-insert path are exercised, not a fake stand-in for them.
 *
 * `isProviderChannel` is no longer a caller-supplied argument — [ChannelRepository.createChannel]
 * derives it internally from `channelType != "Cash"`, so every test here uses a non-CASH
 * `channelType` ("MOBILE_MONEY"/"BANK") to get the dedup guarantee automatically.
 */
@RunWith(RobolectricTestRunner::class)
class ChannelSenderKeyDedupTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ChannelRepository.database = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `lowercase-created-then-uppercase-lookup converge on the same channel row`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "Auto-created MTN Mobile Money",
                    description = "0700000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "mtn mobile money",
                    status = true,
                )

            val second =
                ChannelRepository.createChannel(
                    name = "Auto-created MTN Mobile Money",
                    description = "0700000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN MOBILE MONEY",
                    status = true,
                )

            assertTrue("first create for a new sender must insert a new row", first is ChannelCreateOutcome.Created)
            assertTrue(
                "a second (differently-cased) create for the same sender must be deduped, not inserted again",
                second is ChannelCreateOutcome.AlreadyExists,
            )
            assertEquals(
                "a second (differently-cased) create for the same sender must resolve to the SAME row as the first",
                (first as ChannelCreateOutcome.Created).channel.id,
                (second as ChannelCreateOutcome.AlreadyExists).existing.id,
            )
            assertEquals(
                "exactly one channel row must exist for this sender, not two",
                1,
                db.channelDao().getAllIncludingDeleted().size,
            )
        }

    @Test
    fun `uppercase-created-then-lowercase-lookup converge on the same channel row`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "Auto-created Airtel Money",
                    description = "0750000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "AIRTEL MONEY",
                    status = true,
                )

            val second =
                ChannelRepository.createChannel(
                    name = "Auto-created Airtel Money",
                    description = "0750000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "airtel money",
                    status = true,
                )

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(second is ChannelCreateOutcome.AlreadyExists)
            assertEquals(
                "a second (differently-cased) create for the same sender must resolve to the SAME row as the first",
                (first as ChannelCreateOutcome.Created).channel.id,
                (second as ChannelCreateOutcome.AlreadyExists).existing.id,
            )
            assertEquals(
                "exactly one channel row must exist for this sender, not two",
                1,
                db.channelDao().getAllIncludingDeleted().size,
            )
        }

    @Test
    fun `findByNormalizedSenderKey resolves regardless of the casing used at lookup time`() =
        runBlocking {
            val created =
                ChannelRepository.createChannel(
                    name = "Auto-created Stanbic",
                    description = "0770000000",
                    channelType = "BANK",
                    channelDesc = "Stanbic Bank",
                    status = true,
                ) as ChannelCreateOutcome.Created

            val foundUpper = ChannelRepository.findByNormalizedSenderKey("STANBIC BANK")
            val foundPadded = ChannelRepository.findByNormalizedSenderKey("  Stanbic Bank  ")

            assertEquals(created.channel.id, foundUpper?.id)
            assertEquals(created.channel.id, foundPadded?.id)
        }

    @Test
    fun `concurrent differently-cased channel creates for the same sender still converge to one row`() =
        runBlocking {
            val results =
                listOf(
                    async(Dispatchers.IO) {
                        ChannelRepository.createChannel(
                            name = "Auto-created Centenary",
                            description = "0780000000",
                            channelType = "BANK",
                            channelDesc = "centenary bank",
                            status = true,
                        )
                    },
                    async(Dispatchers.IO) {
                        ChannelRepository.createChannel(
                            name = "Auto-created Centenary",
                            description = "0780000000",
                            channelType = "BANK",
                            channelDesc = "CENTENARY BANK",
                            status = true,
                        )
                    },
                ).awaitAll()

            fun ChannelCreateOutcome.id() =
                when (this) {
                    is ChannelCreateOutcome.Created -> channel.id
                    is ChannelCreateOutcome.AlreadyExists -> existing.id
                }

            assertEquals(
                "two concurrent creates for the same sender (different casing) must resolve to one row",
                results[0].id(),
                results[1].id(),
            )
            assertEquals(1, db.channelDao().getAllIncludingDeleted().size)
        }
}
