package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.features.settings.channels.ChannelDescMobileMoney
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import cc.dlabs.pesamind.features.settings.notifications.MessageSender
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [ChannelManager.isSmsAllowedForSender]'s use of [ChannelEntity.receivingNumber]
 * (via [cc.dlabs.pesamind.core.utils.PhoneNumberNormalizer]) to disambiguate between two
 * channels that share a provider — e.g. two MTN MoMo lines on the same account, told apart by
 * which phone number an incoming SMS actually arrived on. Real in-memory Room DB
 * (Robolectric-provided [Context]) wired directly into [ChannelRepository.database], same
 * pattern as [cc.dlabs.pesamind.features.settings.notifications.SMSMessageProcessorPremiumGateTest].
 * Premium tier throughout so the Free-tier per-type channel cap never interferes with these
 * disambiguation-only assertions.
 */
@RunWith(RobolectricTestRunner::class)
class ChannelManagerReceivingNumberTest {
    private lateinit var context: Context
    private lateinit var db: PesaMindDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(context, PesaMindDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ChannelRepository.database = db
        ChannelManager.init(context)

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

    private suspend fun createMtnChannel(
        name: String,
        rawNumber: String,
        receivingNumber: String,
    ) = ChannelRepository.createChannel(
        name = name,
        description = rawNumber,
        channelType = ChannelTypes.MOBILE_MONEY,
        channelDesc = ChannelDescMobileMoney.MTNMOBILEMONEY,
        status = true,
        accountNumber = rawNumber,
        receivingNumber = receivingNumber,
    )

    @Test
    fun `an SMS whose receiving number matches one of two same-provider channels attaches to the right one`() =
        runBlocking {
            val first = createMtnChannel("MTN Line 1", "0770123456", "770123456")
            val second = createMtnChannel("MTN Line 2", "0780123456", "780123456")
            val firstId = (first as cc.dlabs.pesamind.core.data.ChannelCreateOutcome.Created).channel.id
            val secondId = (second as cc.dlabs.pesamind.core.data.ChannelCreateOutcome.Created).channel.id

            val result =
                ChannelManager.isSmsAllowedForSender(
                    receivingSimNumber = "0770123456",
                    simInfo = 0,
                    senderID = MessageSender.MTN_MOB_MONEY,
                )

            assertNotNull(result)
            assertEquals(firstId, result!!.channel.id)
            assertEquals(
                "must not have resolved to the OTHER same-provider channel",
                true,
                result.channel.id != secondId,
            )
            // No third/auto-created channel should have appeared.
            assertEquals(2, db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()))
        }

    @Test
    fun `a single live channel for a provider is still matched when the SMS receiving number is unresolvable`() =
        runBlocking {
            val only = createMtnChannel("MTN Line 1", "0770123456", "770123456")
            val onlyId = (only as cc.dlabs.pesamind.core.data.ChannelCreateOutcome.Created).channel.id

            val result =
                ChannelManager.isSmsAllowedForSender(
                    // Blank -> PhoneNumberNormalizer.normalize returns null -> UNSPECIFIED sentinel,
                    // which misses this channel's real receiving number on an exact match, but the
                    // single-live-channel fallback in ChannelRepository.findByNormalizedSenderKey
                    // must still resolve it (no real ambiguity with only one candidate).
                    receivingSimNumber = "",
                    simInfo = 0,
                    senderID = MessageSender.MTN_MOB_MONEY,
                )

            assertNotNull(
                "a single existing channel for this provider must still be matched, not treated as ambiguous",
                result,
            )
            assertEquals(onlyId, result!!.channel.id)
            assertEquals(1, db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()))
        }

    /**
     * The genuinely-ambiguous case — [ChannelRepository.hasAmbiguousChannelsForProvider] is
     * what lets [ChannelManager.isSmsAllowedForSender] tell "no channel exists yet for this
     * sender" apart from "multiple channels exist and the receiving number didn't disambiguate
     * them," so the SMS is dropped (returns null) here instead of either guessing between the
     * two real channels or silently creating a THIRD one — see
     * [ChannelRepository.findByNormalizedSenderKey]'s doc comment for why a dropped/unattributed
     * SMS is the intended, safer outcome.
     */
    @Test
    fun `two live channels with an unresolvable SMS receiving number drops the SMS rather than guessing or creating a third channel`() =
        runBlocking {
            createMtnChannel("MTN Line 1", "0770123456", "770123456")
            createMtnChannel("MTN Line 2", "0780123456", "780123456")

            val result =
                ChannelManager.isSmsAllowedForSender(
                    receivingSimNumber = "",
                    simInfo = 0,
                    senderID = MessageSender.MTN_MOB_MONEY,
                )

            assertEquals("an ambiguous match must never guess or auto-create — SMS is dropped", null, result)
            assertEquals(
                "no new channel was created for the ambiguous SMS — still exactly the two pre-existing ones",
                2,
                db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()),
            )
        }
}
