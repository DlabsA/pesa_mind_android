package cc.dlabs.pesamind.features.analytics

import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the streak-cache staleness bug: [AnalyticsViewModel.fetchFromNetwork]
 * (private) reads [StreakSessionCache.get] cache-first and returns whatever snapshot is
 * already there unconditionally, only hitting `/dashboard` for a fresh streak when the
 * cache is empty. Since the cache was previously cleared only on logout, once populated it
 * froze the displayed streak forever — even though `refresh()`/`refreshSuspend()` genuinely
 * re-run on every `TransactionCreated`/`Channel*`/`SyncCompleted` event.
 *
 * Fix: `onStateEvent()` now calls `StreakSessionCache.clear()` *synchronously*, before
 * queuing the network-refresh coroutine, for `TransactionCreated`, the `Channel*` events,
 * and `SyncCompleted` — the same invalidation `UserLoggedOut` already performed.
 * `SyncCompleted` matters specifically: it's documented in `onStateEvent` as the "accurate
 * correction" pass that runs *after* `TransactionCreated`'s own (possibly-stale) refresh has
 * already repopulated the cache — without clearing it again here, that correction pass would
 * silently re-serve the same cached snapshot instead of refetching, defeating its own
 * purpose. This test proves both the previously-broken repeat-populate scenario and the new
 * `SyncCompleted` case.
 *
 * `onStateEvent` is `protected` on `UnifiedViewModel`, so it's invoked here via reflection
 * rather than by making [AnalyticsViewModel] `open`/subclassable purely for this test.
 *
 * Per `.claude/CLAUDE.md`'s standing test-landmine warning: this file never advances
 * [mainDispatcher]'s scheduler (no `runTest`, no `advanceUntilIdle()`), so any coroutine
 * queued inside `onStateEvent`'s `viewModelScope.launch { ... }` (or `AnalyticsViewModel`'s
 * own `init` block) is left permanently pending and never executes — no real HTTP request
 * against `https://api.dlabs.cc/api/v1/` fires from this test. Only the synchronous
 * cache-invalidation line is exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelStreakCacheTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        StreakSessionCache.clear()
    }

    @After
    fun tearDown() {
        StreakSessionCache.clear()
        Dispatchers.resetMain()
    }

    private fun emit(
        viewModel: AnalyticsViewModel,
        event: StateEvent,
    ) {
        val method = AnalyticsViewModel::class.java.getDeclaredMethod("onStateEvent", StateEvent::class.java)
        method.isAccessible = true
        method.invoke(viewModel, event)
    }

    @Test
    fun secondTransactionCreatedEventClearsAnAlreadyPopulatedCache() =
        runBlocking {
            val viewModel = AnalyticsViewModel()

            // Simulate a prior fetch having already populated the streak cache.
            StreakSessionCache.set(count = 5, lastActiveDate = "2026-07-24")
            assertNotNull(StreakSessionCache.get())

            // First TransactionCreated event.
            emit(viewModel, StateEvent.TransactionCreated("tx-1", 100.0, "channel-1"))
            assertNull(
                "cache must be invalidated on the first TransactionCreated event",
                StreakSessionCache.get(),
            )

            // Re-populate the cache — simulates another screen's fetch landing between events,
            // reproducing the exact "non-null cache already present" precondition of the bug.
            StreakSessionCache.set(count = 5, lastActiveDate = "2026-07-24")
            assertNotNull(StreakSessionCache.get())

            // Second TransactionCreated event with a non-null cache present — this is the
            // scenario that was broken: the old code returned the cached snapshot forever.
            emit(viewModel, StateEvent.TransactionCreated("tx-2", 50.0, "channel-1"))
            assertNull(
                "streak cache must be invalidated again on the second TransactionCreated event",
                StreakSessionCache.get(),
            )
        }

    @Test
    fun channelUpdatedEventAlsoInvalidatesTheCache() =
        runBlocking {
            val viewModel = AnalyticsViewModel()
            StreakSessionCache.set(count = 3, lastActiveDate = "2026-07-20")

            emit(viewModel, StateEvent.ChannelUpdated("channel-1", "Mpesa"))

            assertNull(StreakSessionCache.get())
        }

    @Test
    fun channelCreatedEventAlsoInvalidatesTheCache() =
        runBlocking {
            val viewModel = AnalyticsViewModel()
            StreakSessionCache.set(count = 3, lastActiveDate = "2026-07-20")

            emit(viewModel, StateEvent.ChannelCreated("channel-2", "Airtel Money"))

            assertNull(StreakSessionCache.get())
        }

    @Test
    fun channelDeletedEventAlsoInvalidatesTheCache() =
        runBlocking {
            val viewModel = AnalyticsViewModel()
            StreakSessionCache.set(count = 3, lastActiveDate = "2026-07-20")

            emit(viewModel, StateEvent.ChannelDeleted("channel-2"))

            assertNull(StreakSessionCache.get())
        }

    @Test
    fun syncCompletedEventAlsoInvalidatesAnAlreadyPopulatedCache() =
        runBlocking {
            val viewModel = AnalyticsViewModel()

            // Simulates the state right after TransactionCreated's own fetchFromNetwork() has
            // already repopulated the cache with a (possibly stale-looking) snapshot — the
            // exact precondition SyncCompleted's "accurate correction" pass must not just
            // re-serve as-is.
            StreakSessionCache.set(count = 2, lastActiveDate = "2026-08-10")
            assertNotNull(StreakSessionCache.get())

            emit(viewModel, StateEvent.SyncCompleted)

            assertNull(
                "SyncCompleted must invalidate an already-repopulated cache, otherwise the " +
                    "'accurate correction' pass silently no-ops instead of refetching",
                StreakSessionCache.get(),
            )
        }
}
