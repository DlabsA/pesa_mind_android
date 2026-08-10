package cc.dlabs.pesamind.features.settings.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.ProcessedMessageRepository
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.utils.TransactionCreationResult
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.home.TYPE_INCOME
import cc.dlabs.pesamind.features.settings.channels.ChannelDescMobileMoney
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Covers the Free-tier gate in [SMSMessageProcessor.processMessage]: `!AccountManager.isPremium()`
 * short-circuits before [cc.dlabs.pesamind.core.storage.ChannelManager] is ever touched, so a
 * lapsed/Free account neither auto-creates a channel for a never-seen sender nor a transaction on
 * an existing one — manual entry is unaffected since it never goes through this pipeline.
 *
 * Real in-memory Room DB wired directly into [ChannelRepository.database] and
 * [ProcessedMessageRepository.database] (Robolectric-provided [Context]), same pattern as
 * [cc.dlabs.pesamind.core.data.ChannelTierLimitTest]/[cc.dlabs.pesamind.core.data.ChannelSenderKeyDedupTest].
 * `.init()` is deliberately never called on either repository object here: both go through Hilt's
 * [cc.dlabs.pesamind.core.di.DatabaseEntryPoint], which those two existing files' own doc
 * comments establish isn't reliably available to a plain Robolectric context with no
 * `@HiltAndroidTest` component — hence assigning `database` directly instead. Wiring
 * [ChannelRepository.database] here is defensive, not incidental: it's what turns "the gate
 * silently regressed" into a *visible* test failure (a channel really gets created) instead of a
 * swallowed `UninitializedPropertyAccessException` inside `processMessage`'s own outer catch
 * block.
 *
 * [ProcessedMessageRepository.database] is wired the same way for the same defensive reason, but
 * empirically (confirmed while writing this file) it never actually gets exercised in this
 * harness: `processMessage` unconditionally calls `ProcessedMessageRepository.init(context)`
 * itself, first thing, and — since this bare Robolectric run has no manifest/Hilt component
 * (`ApplicationProvider.getApplicationContext()` here is a plain `Application`, not a
 * Hilt-processed `PesaMindApp`) — that call's own `EntryPoints.get(...)` throws before
 * `.record()` is ever reached. That's caught by `processMessage`'s own outer try/catch (by
 * design, per its doc comment: "a failure recording it must never block the transaction-creation
 * logic that follows") and confirmed harmless by every test below reaching its gate-check
 * assertions normally. Asserting the audit-trail row actually got persisted would need real
 * `@HiltAndroidTest`/`HiltTestApplication` scaffolding, which is out of proportion to what this
 * file is for (the premium gate, not Hilt entry-point wiring) — left un-asserted rather than
 * forcing a test around a harness limitation.
 *
 * [TransactionViewModel] is a Mockito mock throughout — safe for the blocked-gate tests because
 * `viewModel.createTransactionAwaited` is never reached at all when the gate returns early (see
 * [SMSMessageProcessorParsingTest]'s doc comment for why a *real* [TransactionViewModel] is
 * normally the landmine to avoid: its `init` block launches on `viewModelScope`). The
 * allowed-through test below also uses a mock, stubbed to return
 * [TransactionCreationResult.Failure] (not `.Success`) — deliberately, to avoid a second, unrelated
 * harness gap confirmed while writing this file: a `Success` result falls through into
 * `showLocalNotification`/`ensureNotificationChannels`, which throws
 * `NoSuchMethodError: NotificationManager.getNotificationChannel` under this project's current
 * Robolectric setup (a `LinkageError`, not caught by `processMessage`'s `catch (e: Exception)`) —
 * a Robolectric shadow-framework gap unrelated to the gate this file tests, not a real bug (that
 * API exists on every device since API 26). `Failure` returns immediately after the
 * `createTransactionAwaited` call, before any notification code, which is exactly the boundary
 * this test needs: proof the gate *attempted* the transaction with the correct parsed values,
 * without depending on notification-posting internals to be Robolectric-clean.
 */
@RunWith(RobolectricTestRunner::class)
class SMSMessageProcessorPremiumGateTest {
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
        ProcessedMessageRepository.database = db

        AccountManager.init(context)
        runBlocking { AccountManager.clearAccount() }
    }

    // See ChannelTierLimitTest/AccountManagerTest's teardown doc comments: AccountManager is a
    // process-lifetime singleton with no reset for `isInitialized()`, so this class must leave it
    // cleared for whatever test class runs next in the shared test JVM.
    @After
    fun tearDown() {
        db.close()
        runBlocking { AccountManager.clearAccount() }
    }

    private suspend fun setTier(type: String) {
        AccountManager.saveAccount(
            id = "user-1",
            email = "test@example.com",
            username = "testuser",
            avatarUrl = "",
            balance = "0.0",
            type = type,
        )
    }

    @Test
    fun `processMessage creates no channel and no transaction when account is Free tier`() =
        runBlocking {
            setTier("Free")
            val viewModel = mock<TransactionViewModel>()
            val processor = SMSMessageProcessor(context, viewModel)

            processor.processMessage(
                senderId = MessageSender.MTN_MOB_MONEY,
                content = "You have received UGX 50,000 from 256700000000 Jane Doe. New balance: UGX 120,000.",
                timestamp = 1_700_000_000_000L,
                simInfo = 0,
                receivingSimNumber = "0700000000",
            )

            assertEquals(0, db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()))
            verifyNoInteractions(viewModel)
        }

    @Test
    fun `processMessage creates no channel and no transaction with no account ever saved`() =
        runBlocking {
            // AccountManager.isPremium() swallows "no account cached yet" to false (its own doc
            // comment: "Free is the safe default on any failure") — a device that has never
            // synced a profile must be blocked exactly like a confirmed Free account, not
            // accidentally let through just because the tier is merely unknown.
            val viewModel = mock<TransactionViewModel>()
            val processor = SMSMessageProcessor(context, viewModel)

            processor.processMessage(
                senderId = MessageSender.AIRTEL_MONEY,
                content = "RECEIVED UGX 1,000 from 256789404730. Balance UGX 4,613. TID:153038584469.",
                timestamp = 1_700_000_000_000L,
                simInfo = 1,
                receivingSimNumber = "0750000000",
            )

            assertEquals(0, db.channelDao().countByUserId(AccountManager.currentUserIdOrEmpty()))
            verifyNoInteractions(viewModel)
        }

    @Test
    fun `processMessage still creates a channel and attempts the transaction when account is Premium`() =
        runBlocking {
            setTier("Premium")
            val viewModel = mock<TransactionViewModel>()
            // Failure (not Success), and why: see class doc comment — a Success result falls
            // through into notification-posting code that hits an unrelated Robolectric shadow
            // gap in this project's current setup. Failure returns immediately after this call,
            // which is exactly the boundary this test needs.
            whenever(
                viewModel.createTransactionAwaited(
                    channelID = any(),
                    amount = any(),
                    type = any(),
                    note = any(),
                    smsSourceKey = anyOrNull(),
                    providerTransactionId = anyOrNull(),
                ),
            ).thenReturn(TransactionCreationResult.Failure("stubbed failure — see class doc comment"))
            val processor = SMSMessageProcessor(context, viewModel)

            val content = "You have received UGX 50,000 from 256700000000 Jane Doe. New balance: UGX 120,000."
            val timestamp = 1_700_000_000_000L
            processor.processMessage(
                senderId = MessageSender.MTN_MOB_MONEY,
                content = content,
                timestamp = timestamp,
                simInfo = 0,
                receivingSimNumber = "0700000000",
            )

            val channels = ChannelRepository.getAllChannels()
            assertEquals(1, channels.size)
            val expectedSmsSourceKey = "${MessageSender.MTN_MOB_MONEY}:$timestamp:${content.trim().hashCode()}"

            verify(viewModel).createTransactionAwaited(
                channelID = eq(channels.first().id),
                amount = eq(50000.0),
                type = eq(TYPE_INCOME),
                note = eq(content),
                smsSourceKey = eq(expectedSmsSourceKey),
                providerTransactionId = isNull(),
            )
            Unit
        }

    /**
     * The per-channel half of this gate, layered on top of the Premium/trial check above: even
     * a Premium account gets no transaction from a sender whose channel has SMS auto-capture
     * explicitly toggled off ([ChannelRepository.setSmsNotificationEnabled], the write path
     * behind the Channels screen's per-card switch). The channel must already exist (created
     * with the default `smsNotificationEnabled = true`, then flipped off) so
     * [cc.dlabs.pesamind.core.storage.ChannelManager.isSmsAllowedForSender] takes its
     * `findByNormalizedSenderKey` match branch, not the auto-create branch — a freshly
     * auto-created channel always starts enabled.
     */
    @Test
    fun `processMessage creates no transaction when the channel's own SMS auto-capture is off, even when Premium`() =
        runBlocking {
            setTier("Premium")
            ChannelRepository.createChannel(
                name = "MTN Mobile Money",
                description = "0700000000",
                channelType = ChannelTypes.MOBILE_MONEY,
                channelDesc = ChannelDescMobileMoney.MTNMOBILEMONEY,
                status = true,
            )
            val channelId = ChannelRepository.getAllChannels().single().id
            ChannelRepository.setSmsNotificationEnabled(channelId, false)

            val viewModel = mock<TransactionViewModel>()
            val processor = SMSMessageProcessor(context, viewModel)

            processor.processMessage(
                senderId = MessageSender.MTN_MOB_MONEY,
                content = "You have received UGX 50,000 from 256700000000 Jane Doe. New balance: UGX 120,000.",
                timestamp = 1_700_000_000_000L,
                simInfo = 0,
                receivingSimNumber = "0700000000",
            )

            assertEquals(1, ChannelRepository.getAllChannels().size)
            verifyNoInteractions(viewModel)
        }
}
