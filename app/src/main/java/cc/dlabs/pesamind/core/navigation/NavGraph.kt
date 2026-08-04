package cc.dlabs.pesamind.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.storage.TokenManager.LockState
import cc.dlabs.pesamind.features.auth.LockSetupScreen
import cc.dlabs.pesamind.features.auth.LoginScreen
import cc.dlabs.pesamind.features.auth.PatternUnlockScreen
import cc.dlabs.pesamind.features.auth.PinUnlockScreen
import cc.dlabs.pesamind.features.auth.RegisterScreen
import cc.dlabs.pesamind.features.budgets.SetMonthlyBudgetScreen
import cc.dlabs.pesamind.features.budgets.YearlyBudgetDetailScreen
import cc.dlabs.pesamind.features.home.AddTransactionScreen
import cc.dlabs.pesamind.features.home.MainScreen
import cc.dlabs.pesamind.features.onboarding.ChannelOnboardingViewModel
import cc.dlabs.pesamind.features.onboarding.OnboardingAirtelScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingBankScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingCashScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingIntroScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingMoMoScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingReviewScreen
import cc.dlabs.pesamind.features.settings.account.AccountSettingsScreen
import cc.dlabs.pesamind.features.settings.account.ChangePasswordScreen
import cc.dlabs.pesamind.features.settings.channels.ChannelScreen
import cc.dlabs.pesamind.features.settings.notifications.TransactionListScreen
import cc.dlabs.pesamind.features.settings.security.SecuritySettingsScreen
import cc.dlabs.pesamind.features.settings.security.SetPatternScreen
import cc.dlabs.pesamind.features.settings.security.SetPinScreen
import java.util.Calendar

/** Route for the nested onboarding graph — screens within it share one
 * [ChannelOnboardingViewModel] instance scoped to this graph's back stack entry. */
private const val ONBOARDING_GRAPH_ROUTE = "onboarding_graph"

@Composable
fun PesaMindNavGraph(navController: NavHostController) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val destination =
            when (TokenManager.getLockState()) {
                LockState.NONE ->
                    when {
                        !TokenManager.isLoggedIn() -> Routes.Login.route
                        !TokenManager.isChannelsOnboarded() -> Routes.ChannelOnboardingIntro.route
                        else -> Routes.Dashboard.route
                    }
                LockState.PIN -> Routes.PinUnlock.route
                LockState.PATTERN -> Routes.PatternUnlock.route
            }
        startDestination = destination
    }
    if (startDestination == null) {
        // Show loading indicator (e.g., a simple progress bar)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(navController, startDestination = startDestination!!) {
            composable(Routes.Login.route) { LoginScreen(navController) }
            composable(Routes.Register.route) { RegisterScreen(navController) }
            composable(Routes.LockSetup.route) { LockSetupScreen(navController) }
            composable(Routes.PinSetup.route) { PinUnlockScreen(navController, isSetup = true) }
            composable(Routes.PatternSetup.route) {
                PatternUnlockScreen(
                    navController,
                    isSetup = true,
                )
            }
            composable(Routes.PinUnlock.route) { PinUnlockScreen(navController) }
            composable(Routes.PatternUnlock.route) { PatternUnlockScreen(navController) }
            composable(Routes.Dashboard.route) { MainScreen(navController) }

            navigation(startDestination = Routes.ChannelOnboardingIntro.route, route = ONBOARDING_GRAPH_ROUTE) {
                composable(Routes.ChannelOnboardingIntro.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingIntroScreen(navController, vm)
                }
                composable(Routes.OnboardingCash.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingCashScreen(navController, vm)
                }
                composable(Routes.OnboardingMoMo.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingMoMoScreen(navController, vm)
                }
                composable(Routes.OnboardingAirtel.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingAirtelScreen(navController, vm)
                }
                composable(Routes.OnboardingBank.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingBankScreen(navController, vm)
                }
                composable(Routes.OnboardingReview.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingReviewScreen(navController, vm)
                }
            }

            composable(Routes.SecuritySettings.route) { SecuritySettingsScreen(navController) }
            composable(Routes.SetPin.route) { SetPinScreen(navController) }
            composable(Routes.SetPattern.route) { SetPatternScreen(navController) }

            composable(Routes.AccountSettings.route) { AccountSettingsScreen(navController) }
            composable(Routes.ChangePassword.route) { ChangePasswordScreen(navController) }
            composable(Routes.Channels.route) { ChannelScreen(navController) }

//        Adding Transaction routes
            composable(
                route = Routes.AddTransaction.route,
                arguments =
                    listOf(
                        navArgument("channelId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { backStackEntry ->
                AddTransactionScreen(
                    navController,
                    initialChannelId = backStackEntry.arguments?.getString("channelId"),
                )
            }
            composable(Routes.TransactionList.route) { TransactionListScreen(navController) }
            composable(
                route = Routes.SetMonthlyBudget.route,
                arguments =
                    listOf(
                        navArgument("month") { type = NavType.IntType },
                        navArgument("year") { type = NavType.IntType },
                    ),
            ) { backStackEntry ->
                val month = backStackEntry.arguments?.getInt("month") ?: 1
                val year = backStackEntry.arguments?.getInt("year") ?: 2026
                SetMonthlyBudgetScreen(
                    navController = navController,
                    month = month,
                    year = year,
                )
            }
            composable(Routes.SetYearlyBudget.route) {
                YearlyBudgetDetailScreen(navController, Calendar.getInstance().get(Calendar.YEAR))
            }
        }
    }
}
