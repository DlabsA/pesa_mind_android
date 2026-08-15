package cc.dlabs.pesamind.features.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.OfflineBanner
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import cc.dlabs.pesamind.core.ui.StaggeredCard

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavHostController,
    vm: BudgetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cardsVisible = true
        try {
            val storedAccount = AccountManager.getAccount()
            if (storedAccount.username.isBlank() && storedAccount.email.isBlank()) {
                snackbarHostState.showSnackbar("Account details not found. Please sign in again.")
            }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("Failed to load account details")
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BudgetHeader(
                state = state,
                viewModel = vm,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = 8.dp),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            indicator = {},
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            val showInitialSkeleton =
                state.isRefreshing ||
                    (
                        state.isLoading &&
                            state.yearlyBudget == null &&
                            state.currentMonthlyBudget == null &&
                            !state.isOffline
                    )

            if (showInitialSkeleton) {
                SkeletonColumn(
                    blockHeights = listOf(4.dp, 210.dp, 92.dp, 260.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
                    contentPadding = PaddingValues(bottom = Spacing.Space2.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    if (state.isOffline) {
                        item {
                            OfflineBanner(
                                caption = "Pull to refresh when connection is restored.",
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }

                    item {
                        StaggeredCard(index = 0, visible = cardsVisible) {
                            YearlyBudgetCard(
                                yearly = state.yearlyBudget,
                                isLoading = state.isLoadingYearly,
                                year = state.displayYear,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                navController = navController,
                            )
                        }
                    }

                    item {
                        StaggeredCard(index = 1, visible = cardsVisible) {
                            if (state.isPremium) {
                                NextMonthBudgetCard(
                                    nextMonth = state.nextMonthIndex,
                                    nextYear = state.nextMonthYear,
                                    hasExisting = state.hasNextMonthBudget,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    onSetBudget = {
                                        navController.navigate(
                                            Routes.SetMonthlyBudget.createRoute(
                                                state.nextMonthIndex,
                                                state.nextMonthYear,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    item {
                        StaggeredCard(index = 2, visible = cardsVisible) {
                            CurrentMonthCard(
                                yearly = state.yearlyBudget,
                                monthly = state.currentMonthlyBudget,
                                isLoading = state.isLoadingMonthly,
                                month = state.displayMonth,
                                year = state.displayYear,
                                balance = state.monthlyBalance,
                                isDeficit = state.isMonthlyDeficit,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                onDetails = {
                                    if (state.yearlyBudget == null) {
                                        navController.navigate(Routes.SetYearlyBudget.route)
                                    } else {
                                        navController.navigate(
                                            Routes.SetMonthlyBudget.createRoute(
                                                state.displayMonth,
                                                state.displayYear,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}
