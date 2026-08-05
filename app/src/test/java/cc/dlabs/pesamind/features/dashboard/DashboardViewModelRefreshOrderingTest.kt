package cc.dlabs.pesamind.features.dashboard

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import retrofit2.Response

/**
 * Regression coverage for the "streak count doesn't update immediately" bug:
 * [DashboardViewModel.onStateEvent]'s `TransactionCreated` branch used to call the
 * non-suspend `refresh()` (which launches its own coroutine and returns immediately)
 * and then publish `DashboardRefreshed` on the very next line — meaning that event
 * fired before the network refetch had even started, let alone completed. The fix
 * splits `refresh()`'s body into an internal, `Mutex`-guarded suspend function,
 * [DashboardViewModel.refreshSuspend], that a caller can genuinely await.
 *
 * Same `runBlocking`-only convention as [cc.dlabs.pesamind.core.utils.TransactionViewModelValidationTest]
 * (see that file's doc comment for the full rationale): a dedicated, never-advanced
 * [StandardTestDispatcher] is set as `Dispatchers.Main`, so [DashboardViewModel]'s
 * `init` block (which subscribes to the app-wide event bus and observes
 * `networkMonitor.isConnected` on `viewModelScope`, i.e. `Dispatchers.Main`) is only
 * ever queued, never executed — `refreshSuspend()` itself is called directly, off
 * that dispatcher entirely, so its execution is unaffected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelRefreshOrderingTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeNetworkMonitor(): NetworkMonitor =
        mock {
            on { isConnectedNow } doReturn true
        }

    private fun cannedDashboardResponse(): DashboardResponse {
        val metadata = Metadata(period = "month", generatedAt = "2026-08-01T00:00:00Z", currency = "UGX", timezone = "Africa/Kampala")
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
    fun refreshSuspendDoesNotReturnUntilTheNetworkCallCompletes() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val fakeApiService =
                mock<ApiService> {
                    onBlocking { getDashboard(any(), any()) } doSuspendableAnswer {
                        gate.await()
                        Response.success(cannedDashboardResponse())
                    }
                }
            val viewModel = DashboardViewModel(fakeApiService, fakeNetworkMonitor())

            val job = launch { viewModel.refreshSuspend() }
            yield() // let the child coroutine run up to gate.await()

            assertTrue("isRefreshing must be true while the network call is still gated", viewModel.state.value.isRefreshing)
            assertNull("dashboard must still be null before the gated call returns", viewModel.state.value.dashboard)

            gate.complete(Unit)
            job.join()

            assertFalse(viewModel.state.value.isRefreshing)
            assertNotNull("dashboard must be populated once refreshSuspend() has genuinely returned", viewModel.state.value.dashboard)
        }

    @Test
    fun overlappingRefreshSuspendCallsBothPerformARealFetch() =
        runBlocking {
            var callCount = 0
            val fakeApiService =
                mock<ApiService> {
                    onBlocking { getDashboard(any(), any()) } doSuspendableAnswer {
                        callCount++
                        Response.success(cannedDashboardResponse())
                    }
                }
            val viewModel = DashboardViewModel(fakeApiService, fakeNetworkMonitor())

            // Two overlapping callers (e.g. a manual pull-to-refresh racing the
            // TransactionCreated handler) must each genuinely wait for and trigger their
            // own fetch via the shared Mutex — a naive `isRefreshing` boolean check would
            // let the second caller see isRefreshing == true and return instantly without
            // fetching, silently reintroducing the bug this split exists to fix.
            val job1 = launch { viewModel.refreshSuspend() }
            val job2 = launch { viewModel.refreshSuspend() }
            job1.join()
            job2.join()

            assertEquals("both overlapping callers must have performed a real fetch", 2, callCount)
            assertFalse(viewModel.state.value.isRefreshing)
            assertNotNull(viewModel.state.value.dashboard)
        }
}
