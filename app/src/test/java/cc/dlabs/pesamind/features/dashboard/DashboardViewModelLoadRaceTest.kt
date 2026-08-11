package cc.dlabs.pesamind.features.dashboard

import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.database.dao.TransactionDao
import cc.dlabs.pesamind.core.database.entity.TransactionEntity
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.analytics.AnalyticsSummaryResponse
import cc.dlabs.pesamind.core.network.analytics.BudgetActualData
import cc.dlabs.pesamind.core.network.analytics.BudgetVsActualResponse
import cc.dlabs.pesamind.core.network.analytics.ContextData
import cc.dlabs.pesamind.core.network.analytics.DashboardResponse
import cc.dlabs.pesamind.core.network.analytics.FinancialHealthResponse
import cc.dlabs.pesamind.core.network.analytics.Health
import cc.dlabs.pesamind.core.network.analytics.Metadata
import cc.dlabs.pesamind.core.network.analytics.SpendingVelocityResponse
import cc.dlabs.pesamind.core.network.analytics.SummaryData
import cc.dlabs.pesamind.core.network.analytics.VelocityData
import cc.dlabs.pesamind.core.network.models.AnomalyData
import cc.dlabs.pesamind.core.network.models.AnomalyMetadata
import cc.dlabs.pesamind.core.network.models.AnomalySection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import retrofit2.Response

/**
 * Regression coverage for the cold-launch double-fetch bug: [DashboardViewModel]'s `init` block
 * (connectivity-triggered auto-load — fires within ~0ms of construction, since
 * [NetworkMonitor.isConnected] seeds its current value immediately on subscription) and
 * [DashboardViewModel.load] (called from `DashboardScreen.kt`'s `LaunchedEffect(Unit)`, which
 * runs ~30ms later than the ViewModel's own `init`) both independently called the private
 * `fetchFromNetwork()` with no shared guard — so on a real cold launch, both fired a genuine
 * `GET /api/v1/data/dashboard` within milliseconds of each other, since neither's
 * `dashboard == null` check observed the other's in-flight (but not yet completed) fetch. The
 * fix wraps both call sites' guard-check + fetch in the same `refreshMutex` already used by
 * [DashboardViewModel.refreshSuspend]/[DashboardViewModel.refreshAfterSyncSuspend], re-evaluating
 * the guard *inside* the lock.
 *
 * Deliberately a separate file/class from [DashboardViewModelRefreshOrderingTest]: that file's
 * class doc comment documents an invariant for every test in it — a frozen, never-advanced
 * [StandardTestDispatcher] as `Dispatchers.Main`, so `init` is only ever queued, never actually
 * executed. Every test in *this* file needs the opposite — `init`'s connectivity collector and
 * `load()`'s launch must both genuinely run and race — so keeping the two invariants in
 * different classes keeps each file's own doc comment accurate for every test inside it.
 *
 * `runTest` is safe here despite `.claude/CLAUDE.md`'s "never `runTest` a ViewModel whose `init`
 * can fall through to a real `ApiClient` call" rule (see its Testing section): that rule's own
 * text carves out "unless that call is mocked/faked", and [DashboardViewModel] takes [ApiService]
 * via constructor injection (not the static `ApiClient.api` singleton) — the mock below is the
 * only thing `init`/`load()` can reach, so no real HTTP call is reachable from this test.
 *
 * `TransactionRepository.database` is stubbed even though this test has nothing to do with
 * transactions: letting `init` genuinely run (required above) also genuinely runs its last line,
 * `observeLocalSummary()`, which touches the real `TransactionRepository` singleton — without a
 * stub this throws (`transactionDao` resolves through an uninitialized Room database) and fails
 * the test for a reason unrelated to what it's actually checking. `database` is `internal`, not
 * `private`, specifically so a JVM test can do this — see its own doc comment in
 * `TransactionRepository.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelLoadRaceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val fakeTransactionDao =
            mock<TransactionDao> {
                on { observeAll(any()) } doReturn emptyFlow<List<TransactionEntity>>()
            }
        TransactionRepository.database =
            mock<PesaMindDatabase> {
                on { transactionDao() } doReturn fakeTransactionDao
            }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cannedDashboardResponse(): DashboardResponse {
        val metadata =
            Metadata(period = "month", generatedAt = "2026-08-01T00:00:00Z", currency = "UGX", timezone = "Africa/Kampala")
        val health = Health(score = 80, status = "good", trend = "stable")
        val summaryData =
            SummaryData(
                totalIncome = 100_000L,
                totalExpense = 40_000L,
                totalSavings = 10_000L,
                netMovement = 50_000L,
                transactionCount = 5,
                activeCategories = 2,
                currentMonth = "2026-08",
            )
        val contextData =
            ContextData(
                totalIncome = 90_000L,
                totalExpense = 35_000L,
                totalSavings = 8_000L,
                netMovement = 47_000L,
                transactionCount = 4,
                activeCategories = 2,
                previousMonth = "2026-07",
            )
        val velocityData =
            VelocityData(
                period = "month",
                daysElapsed = 4,
                daysRemaining = 27,
                totalSpent = 40_000L,
                dailyAverage = 10_000.0,
                projectedMonthEnd = 300_000.0,
                budgetLimit = 500_000.0,
                amountRemaining = 460_000.0,
                spendingPattern = "steady",
                alertLevel = "ok",
                daysUntilBudgetExhausted = 30.0,
            )
        val budgetActualData =
            BudgetActualData(
                period = "month",
                budgetTotal = 500_000L,
                actualTotal = 40_000L,
                variance = 460_000L,
                variancePercent = 8.0,
                status = "on_budget",
                categoriesOnTrack = 2,
                categoriesOverBudget = 0,
            )
        val anomalies =
            AnomalySection(
                data = AnomalyData(anomaliesDetected = 0, items = emptyList(), criticalCount = 0, warningCount = 0),
                metadata =
                    AnomalyMetadata(
                        period = "month",
                        generatedAt = "2026-08-01T00:00:00Z",
                        currency = "UGX",
                        timezone = "Africa/Kampala",
                    ),
            )
        return DashboardResponse(
            summary = AnalyticsSummaryResponse(data = summaryData, metadata = metadata, context = contextData, health = health),
            spendingVelocity = SpendingVelocityResponse(data = velocityData, metadata = metadata, health = health),
            anomalies = anomalies,
            budgetUtilization = BudgetVsActualResponse(data = budgetActualData, metadata = metadata, health = health),
            financialHealth = FinancialHealthResponse(data = health, metadata = metadata),
            streak = null,
        )
    }

    @Test
    fun loadDuringInitsInFlightFetchDoesNotTriggerASecondNetworkCall() =
        runTest(dispatcher) {
            var callCount = 0
            val gate = CompletableDeferred<Unit>()
            val fakeApiService =
                mock<ApiService> {
                    onBlocking { getDashboard(any(), any()) } doSuspendableAnswer {
                        callCount++
                        gate.await()
                        Response.success(cannedDashboardResponse())
                    }
                }
            val fakeNetworkMonitor =
                mock<NetworkMonitor> {
                    on { isConnectedNow } doReturn true
                    // A MutableStateFlow, not flowOf(true): production NetworkMonitor.isConnected
                    // is a callbackFlow that never completes on its own (only via awaitClose,
                    // when the collector is cancelled) — MutableStateFlow matches that "hot,
                    // never completes" shape; flowOf(true) would complete after one emission,
                    // which isn't representative and isn't needed for this test anyway.
                    on { isConnected } doReturn MutableStateFlow(true)
                }

            // Constructing the ViewModel starts Path A: DashboardViewModel's init subscribes to
            // networkMonitor.isConnected, which (like the real callbackFlow) seeds its current
            // value immediately to the new collector — the fetch starts essentially inline here.
            val viewModel = DashboardViewModel(fakeApiService, fakeNetworkMonitor)

            // Path B: mirrors DashboardScreen.kt's `LaunchedEffect(Unit) { viewModel.load() }`.
            viewModel.load()

            // Runs every coroutine up to its next suspension point without advancing virtual
            // time. Path A's fetchFromNetwork() reaches gate.await() while holding refreshMutex;
            // Path B's load() reaches refreshMutex.withLock and parks there, since Path A still
            // holds it.
            runCurrent()

            assertEquals(
                "only init's fetch should have reached the network before load()'s " +
                    "mutex-guarded re-check could observe it in flight",
                1,
                callCount,
            )
            assertNull("dashboard must still be null before the gated call returns", viewModel.state.value.dashboard)
            // load()'s phase=Loading update runs before it ever touches refreshMutex (see
            // load()'s own comment on why), so it must already be visible even though load()
            // itself is still parked waiting for the lock.
            assertEquals(DashboardPhase.Loading, viewModel.state.value.phase)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "load() must skip its own fetch once its mutex-guarded re-check sees " +
                    "dashboard already populated by init's fetch, not perform a second one",
                1,
                callCount,
            )
            assertNotNull(viewModel.state.value.dashboard)
        }
}
