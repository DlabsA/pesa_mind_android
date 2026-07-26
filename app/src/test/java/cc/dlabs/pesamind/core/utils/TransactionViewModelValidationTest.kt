package cc.dlabs.pesamind.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TransactionViewModel]'s private `validateTransactionInput`, exercised
 * only through the public suspend entry point `createTransactionAwaited` (matches how the
 * task requesting this coverage framed it).
 *
 * Scope note — this file intentionally stops at validation and does NOT cover
 * [TransactionViewModel]'s Success/Failure HTTP-response mapping (in
 * `performCreateTransaction`), nor the "valid input falls through with no validation
 * error" case. Both were found to be genuinely blocked, for two independent reasons
 * confirmed empirically while writing this file:
 *
 * 1. `ApiClient.api` (core/network/ApiClient.kt) is a `val` on a Kotlin `object`
 *    singleton, eagerly built as a real Retrofit client pointed at the production
 *    `https://api.dlabs.cc/api/v1/`. It is not a `var`, not behind an interface seam,
 *    and not Hilt-injected, so there is no way to substitute a fake/mocked
 *    `ApiService` response for it from a test without a production-code change.
 * 2. Independent of (1): the moment `performCreateTransaction` runs at all (success,
 *    failure, or thrown-exception branch), it calls `android.util.Log.d`/`Log.e`
 *    directly. This project's unit tests run on the plain JVM (no Robolectric, no
 *    `isReturnDefaultValues` unit-test option), so any `Log.*` call throws
 *    `RuntimeException: Method ... in android.util.Log not mocked` and crashes the
 *    test outright — confirmed by running a throwaway experiment locally.
 *
 * Together this means exercising anything past the validation short-circuit —
 * including the "valid input" case, since valid input is exactly what lets execution
 * continue into `performCreateTransaction` — cannot be done safely/deterministically
 * here. Forcing it would mean a "unit" test that fires a real network request against
 * the production API and is one Log call away from an unconditional crash. See the
 * task write-up for the full report of this blocker.
 *
 * Every test below sets a dedicated, *never-advanced* [StandardTestDispatcher] as the
 * Main dispatcher. [TransactionViewModel]'s `init` block unconditionally starts
 * `loadTransactions()` on `viewModelScope` (backed by `Dispatchers.Main`), which — since
 * there is no Android `Context` available to `TransactionManager` in a JVM unit test —
 * always falls through to a real `ApiClient.api.getTransactions()` call. That coroutine
 * is only ever *queued* on this dispatcher's scheduler, never run, PROVIDED the test
 * body uses plain [runBlocking] rather than `kotlinx.coroutines.test.runTest` — `runTest`
 * auto-detects a `Dispatchers.Main` backed by a `TestDispatcher` and drains its scheduler
 * as part of finishing, which would run the queued `loadTransactions()` coroutine to
 * completion, firing a real HTTP request against the production API (confirmed
 * empirically: an earlier version of this file used `runTest` and reliably triggered a
 * live `OkHttp Dispatcher` thread hitting `https://api.dlabs.cc/api/v1/` on every test).
 * `runBlocking` has its own unrelated event loop and never touches `mainDispatcher`'s
 * scheduler, so the queued coroutine is left permanently pending and is simply
 * discarded — no live network call fires from any test below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelValidationTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankChannelIdFailsValidationBeforeTouchingNetwork() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("", 10.0, TransactionTypes.EXPENSE, "lunch")

            assertTrue(result is TransactionCreationResult.Failure)
            assertEquals("Channel ID is required", (result as TransactionCreationResult.Failure).message)
            assertEquals("Channel ID is required", viewModel.state.value.error)
        }

    @Test
    fun whitespaceOnlyChannelIdIsTreatedAsBlank() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("   ", 10.0, TransactionTypes.EXPENSE, "lunch")

            assertEquals(TransactionCreationResult.Failure("Channel ID is required"), result)
        }

    @Test
    fun zeroAmountFailsValidation() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("channel-1", 0.0, TransactionTypes.EXPENSE, "lunch")

            assertEquals(TransactionCreationResult.Failure("Amount must be greater than zero"), result)
        }

    @Test
    fun negativeAmountFailsValidation() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("channel-1", -25.0, TransactionTypes.EXPENSE, "lunch")

            assertEquals(TransactionCreationResult.Failure("Amount must be greater than zero"), result)
        }

    @Test
    fun unrecognizedTransactionTypeFailsValidationAndListsValidOnes() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("channel-1", 10.0, "not-a-real-type", "lunch")

            val expectedMessage = "Invalid transaction type. Use: ${TransactionTypes.valid.joinToString()}"
            assertEquals(TransactionCreationResult.Failure(expectedMessage), result)
            assertEquals(expectedMessage, viewModel.state.value.error)
        }

    @Test
    fun blankNoteFailsValidationEvenWhenEverythingElseIsValid() =
        runBlocking {
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("channel-1", 10.0, TransactionTypes.EXPENSE, "   ")

            assertEquals(TransactionCreationResult.Failure("Note is required"), result)
        }

    @Test
    fun validationChecksChannelIdBeforeAmountBeforeTypeBeforeNote() =
        runBlocking {
            // All four fields are simultaneously invalid; the channel-ID check must win.
            // If validateTransactionInput's `when` branches were ever reordered (e.g. amount
            // checked first), this test would start failing with a different message.
            val viewModel = TransactionViewModel()

            val result = viewModel.createTransactionAwaited("", -1.0, "bogus", "")

            assertEquals(TransactionCreationResult.Failure("Channel ID is required"), result)
        }
}
