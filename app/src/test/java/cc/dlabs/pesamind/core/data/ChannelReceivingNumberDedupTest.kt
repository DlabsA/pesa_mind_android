package cc.dlabs.pesamind.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.storage.AccountManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the "multiple channels per provider" half of the [ChannelEntity.receivingNumber] fix:
 * [ChannelRepository.createChannel]/[ChannelRepository.findByNormalizedSenderKey] now dedup on
 * `(userId, normalizedSenderKey, receivingNumber)` instead of `(userId, normalizedSenderKey)`
 * alone, so two rows for the same provider (e.g. two MTN MoMo lines) can coexist as long as they
 * carry different [ChannelEntity.receivingNumber] values, while the old "one channel per
 * provider" guarantee still holds whenever a receiving number is never supplied (the
 * [ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER] sentinel keeps participating in the same unique
 * index — see that entity's doc comment). Real in-memory Room DB (Robolectric-provided
 * [Context]), same harness as [ChannelSenderKeyDedupTest]/[ChannelTierLimitTest]. Uses Premium
 * tier throughout so the Free-tier per-type cap ([ChannelTierLimitTest]) never interferes with
 * these dedup-only assertions.
 */
@RunWith(RobolectricTestRunner::class)
class ChannelReceivingNumberDedupTest {
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ChannelRepository.database = db
        AccountManager.init(context)
        runBlocking {
            AccountManager.clearAccount()
            AccountManager.saveAccount(
                id = "user-1",
                email = "test@example.com",
                username = "testuser",
                avatarUrl = "",
                balance = "0.0",
                type = "Premium",
            )
        }
    }

    // See ChannelTierLimitTest's teardown doc comment: AccountManager is a process-lifetime
    // singleton with no reset for `isInitialized()`, so this class must leave it cleared for
    // whatever test class runs next in the shared test JVM.
    @After
    fun tearDown() {
        db.close()
        runBlocking { AccountManager.clearAccount() }
    }

    @Test
    fun `two channels for the same provider with different receiving numbers both get created`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "MTN Line 1",
                    description = "0770123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0770123456",
                    receivingNumber = "770123456",
                )
            val second =
                ChannelRepository.createChannel(
                    name = "MTN Line 2",
                    description = "0780123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0780123456",
                    receivingNumber = "780123456",
                )

            assertTrue("first channel for a distinct receiving number must be created", first is ChannelCreateOutcome.Created)
            assertTrue(
                "second channel for a different receiving number under the same provider must also be created",
                second is ChannelCreateOutcome.Created,
            )
            assertEquals(
                "the two channels must be distinct rows, not deduped against each other",
                2,
                db.channelDao().getAllIncludingDeleted().size,
            )
            assertTrue(
                (first as ChannelCreateOutcome.Created).channel.id != (second as ChannelCreateOutcome.Created).channel.id,
            )
        }

    @Test
    fun `same provider and same receiving number is deduped to the same row`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "MTN Line 1",
                    description = "0770123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0770123456",
                    receivingNumber = "770123456",
                )
            val second =
                ChannelRepository.createChannel(
                    name = "MTN Line 1 (dup attempt)",
                    description = "0770123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0770123456",
                    receivingNumber = "770123456",
                )

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(
                "a second create for the same provider+receiving number must be deduped, not inserted again",
                second is ChannelCreateOutcome.AlreadyExists,
            )
            assertEquals(
                "the deduped create must resolve to the same row as the first",
                (first as ChannelCreateOutcome.Created).channel.id,
                (second as ChannelCreateOutcome.AlreadyExists).existing.id,
            )
            assertEquals(1, db.channelDao().getAllIncludingDeleted().size)
        }

    @Test
    fun `two creates that both omit receiving number still dedup to a single channel`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "Airtel Money",
                    description = "0750000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "Airtel Money",
                    status = true,
                )
            val second =
                ChannelRepository.createChannel(
                    name = "Airtel Money (dup attempt)",
                    description = "0750000000",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "Airtel Money",
                    status = true,
                )

            assertTrue(first is ChannelCreateOutcome.Created)
            assertTrue(
                "with no receiving number ever supplied to either call, today's single-channel-per-provider " +
                    "behavior must be preserved via the UNSPECIFIED sentinel",
                second is ChannelCreateOutcome.AlreadyExists,
            )
            assertEquals(1, db.channelDao().getAllIncludingDeleted().size)
            assertEquals(
                ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER,
                db.channelDao().getAllIncludingDeleted().single().receivingNumber,
            )
        }

    @Test
    fun `findByNormalizedSenderKey exact-matches the right row when two channels share a provider`() =
        runBlocking {
            val first =
                ChannelRepository.createChannel(
                    name = "MTN Line 1",
                    description = "0770123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0770123456",
                    receivingNumber = "770123456",
                ) as ChannelCreateOutcome.Created
            val second =
                ChannelRepository.createChannel(
                    name = "MTN Line 2",
                    description = "0780123456",
                    channelType = "MOBILE_MONEY",
                    channelDesc = "MTN Mobile Money",
                    status = true,
                    accountNumber = "0780123456",
                    receivingNumber = "780123456",
                ) as ChannelCreateOutcome.Created

            val foundFirst = ChannelRepository.findByNormalizedSenderKey("MTN Mobile Money", "770123456")
            val foundSecond = ChannelRepository.findByNormalizedSenderKey("MTN Mobile Money", "780123456")

            assertEquals(first.channel.id, foundFirst?.id)
            assertEquals(second.channel.id, foundSecond?.id)
            assertTrue(foundFirst?.id != foundSecond?.id)
        }

    @Test
    fun `findByNormalizedSenderKey with an unknown receiving number for an ambiguous provider refuses to guess`() =
        runBlocking {
            ChannelRepository.createChannel(
                name = "MTN Line 1",
                description = "0770123456",
                channelType = "MOBILE_MONEY",
                channelDesc = "MTN Mobile Money",
                status = true,
                accountNumber = "0770123456",
                receivingNumber = "770123456",
            )
            ChannelRepository.createChannel(
                name = "MTN Line 2",
                description = "0780123456",
                channelType = "MOBILE_MONEY",
                channelDesc = "MTN Mobile Money",
                status = true,
                accountNumber = "0780123456",
                receivingNumber = "780123456",
            )

            val found = ChannelRepository.findByNormalizedSenderKey("MTN Mobile Money", "999999999")

            assertNull(
                "an exact-match miss against two live channels for the same provider must fall back to null, " +
                    "never guess which one an incoming SMS belongs to",
                found,
            )
        }
}
