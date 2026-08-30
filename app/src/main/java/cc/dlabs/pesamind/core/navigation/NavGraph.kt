package cc.dlabs.pesamind.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import cc.dlabs.pesamind.core.storage.SimSlotManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.storage.TokenManager.LockState
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.features.auth.LockSetupScreen
import cc.dlabs.pesamind.features.auth.LoginScreen
import cc.dlabs.pesamind.features.auth.PatternUnlockScreen
import cc.dlabs.pesamind.features.auth.PinUnlockScreen
import cc.dlabs.pesamind.features.auth.RegisterScreen
import cc.dlabs.pesamind.features.budgets.SetMonthlyBudgetScreen
import cc.dlabs.pesamind.features.budgets.YearlyBudgetDetailScreen
import cc.dlabs.pesamind.features.home.AddTransactionScreen
import cc.dlabs.pesamind.features.home.MainScreen
import cc.dlabs.pesamind.features.lentborrowed.LentBorrowedDetailScreen
import cc.dlabs.pesamind.features.lentborrowed.LentBorrowedListScreen
import cc.dlabs.pesamind.features.onboarding.ChannelOnboardingViewModel
import cc.dlabs.pesamind.features.onboarding.OnboardingAirtelScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingBankScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingCashScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingIntroScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingMoMoScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingReviewScreen
import cc.dlabs.pesamind.features.onboarding.OnboardingSimSlotsScreen
import cc.dlabs.pesamind.features.savinggoals.SavingGoalDetailScreen
import cc.dlabs.pesamind.features.savinggoals.SavingGoalListScreen
import cc.dlabs.pesamind.features.settings.account.AccountSettingsScreen
import cc.dlabs.pesamind.features.settings.account.ChangePasswordScreen
import cc.dlabs.pesamind.features.settings.channels.ChannelDetailScreen
import cc.dlabs.pesamind.features.settings.channels.ChannelScreen
import cc.dlabs.pesamind.features.settings.notifications.TransactionListScreen
import cc.dlabs.pesamind.features.settings.security.SecuritySettingsScreen
import cc.dlabs.pesamind.features.settings.security.SetPatternScreen
import cc.dlabs.pesamind.features.settings.security.SetPinScreen
import cc.dlabs.pesamind.features.settings.simslots.SimSlotFields
import cc.dlabs.pesamind.features.settings.simslots.SimSlotsScreen
import cc.dlabs.pesamind.features.settings.simslots.SimSlotsViewModel
import cc.dlabs.pesamind.features.subscription.SubscriptionScreen
import cc.dlabs.pesamind.features.subscription.SubscriptionViewModel
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
                        // NavHost's own startDestination must be a direct child of the root
                        // graph — it can't point straight at a screen nested inside the
                        // onboarding sub-graph, only at the sub-graph's own route. Regular
                        // navController.navigate(Routes.ChannelOnboardingIntro.route) calls
                        // elsewhere (AuthViewModel, popUpTo targets) don't have this
                        // restriction and are unaffected.
                        !TokenManager.isChannelsOnboarded() -> ONBOARDING_GRAPH_ROUTE
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
        // A tapped renewal reminder should land on the upgrade screen, not the
        // dashboard. Handled here rather than in the start-destination logic so it
        // works identically for a cold start and a resume via onNewIntent.
        val showUpgrade by PaymentDeepLink.showUpgrade.collectAsState()
        LaunchedEffect(showUpgrade) {
            if (showUpgrade) {
                navController.navigate(Routes.Upgrade.route)
                PaymentDeepLink.consumeUpgrade()
            }
        }

        // A tapped "SIM cards changed" alert should land on the dedicated SIM Slots screen,
        // same reasoning as the upgrade deep link above.
        val showSimSlots by SimSlotDeepLink.showSimSlots.collectAsState()
        LaunchedEffect(showSimSlots) {
            if (showSimSlots) {
                navController.navigate(Routes.SimSlots.route)
                SimSlotDeepLink.consume()
            }
        }

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
                composable(Routes.OnboardingSimSlots.route) { backStackEntry ->
                    val vm: ChannelOnboardingViewModel =
                        viewModel(remember(backStackEntry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) })
                    OnboardingSimSlotsScreen(navController, vm)
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
            composable(Routes.SimSlots.route) { SimSlotsScreen(navController) }
            composable(Routes.ChangePassword.route) { ChangePasswordScreen(navController) }
            composable(Routes.Upgrade.route) { backStackEntry ->
                // Scope the ViewModel to this entry so the polling job dies with the
                // screen, and open any 3DS authorisation URL in a Custom Tab. Uganda
                // mobile money authorises on the handset and never reaches this.
                val vm: SubscriptionViewModel = viewModel(backStackEntry)
                val context = LocalContext.current
                SubscriptionScreen(
                    navController = navController,
                    vm = vm,
                    onOpenRedirect = { url -> openPaymentAuthorization(context, url) },
                )
            }
            composable(Routes.Channels.route) { ChannelScreen(navController) }
            composable(
                route = Routes.ChannelDetail.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId")
                if (channelId != null) {
                    ChannelDetailScreen(navController, channelId)
                }
            }

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
                        navArgument("debtCreditId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("savingGoalId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
            ) { backStackEntry ->
                AddTransactionScreen(
                    navController,
                    initialChannelId = backStackEntry.arguments?.getString("channelId"),
                    initialDebtCreditId = backStackEntry.arguments?.getString("debtCreditId"),
                    initialSavingGoalId = backStackEntry.arguments?.getString("savingGoalId"),
                )
            }
            composable(Routes.TransactionList.route) { TransactionListScreen(navController) }
            composable(Routes.LentBorrowed.route) { LentBorrowedListScreen(navController) }
            composable(
                route = Routes.LentBorrowedDetail.route,
                arguments = listOf(navArgument("debtCreditId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val debtCreditId = backStackEntry.arguments?.getString("debtCreditId")
                if (debtCreditId != null) {
                    LentBorrowedDetailScreen(navController, debtCreditId)
                }
            }
            composable(Routes.SavingGoals.route) { SavingGoalListScreen(navController) }
            composable(
                route = Routes.SavingGoalDetail.route,
                arguments = listOf(navArgument("savingGoalId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val savingGoalId = backStackEntry.arguments?.getString("savingGoalId")
                if (savingGoalId != null) {
                    SavingGoalDetailScreen(navController, savingGoalId)
                }
            }
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

        // App-wide, non-dismissible: a detected (or still-unresolved) SIM change blocks every
        // screen behind this form until the user confirms their numbers — see
        // SimSlotManager.refreshDriftBlockingState's doc comment for why this can only ever be
        // (re-)triggered from MainActivity.onResume, never a background listener. The NavHost
        // above stays mounted underneath (not replaced), so whatever the user was doing is
        // exactly where they left it once this closes itself on a successful save.
        val driftBlocking by SimSlotManager.driftBlocking.collectAsState()
        if (driftBlocking) {
            val context = LocalContext.current
            val simSlotsVm: SimSlotsViewModel = viewModel()
            val simSlotsState by simSlotsVm.state.collectAsState()
            LaunchedEffect(Unit) { simSlotsVm.load(context) }

            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.Space6.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
                    ) {
                        Text(
                            "Your SIM card may have changed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Confirm your SIM numbers below to keep transactions matching the " +
                                "right account. The app is locked until this is resolved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SimSlotFields(
                            state = simSlotsState,
                            onNumberChange = simSlotsVm::onNumberChange,
                            onCountryChange = simSlotsVm::onCountryChange,
                            onSave = { simSlotsVm.save(context) },
                        )
                    }
                }
            }
        }
    }
}
