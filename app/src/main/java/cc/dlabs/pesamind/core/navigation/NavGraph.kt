package cc.dlabs.pesamind.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cc.dlabs.pesamind.features.auth.LockSetupScreen
import cc.dlabs.pesamind.features.splash.SplashScreen
import cc.dlabs.pesamind.features.auth.LoginScreen
import cc.dlabs.pesamind.features.auth.PatternUnlockScreen
import cc.dlabs.pesamind.features.auth.PinUnlockScreen
import cc.dlabs.pesamind.features.auth.RegisterScreen
import cc.dlabs.pesamind.features.home.AddTransactionScreen
import cc.dlabs.pesamind.features.home.MainScreen
import cc.dlabs.pesamind.features.settings.account.AccountSettingsScreen
import cc.dlabs.pesamind.features.settings.account.ChangePasswordScreen
import cc.dlabs.pesamind.features.settings.channels.ChannelScreen
import cc.dlabs.pesamind.features.settings.notifications.TransactionListScreen
import cc.dlabs.pesamind.features.settings.security.SecuritySettingsScreen
import cc.dlabs.pesamind.features.settings.security.SetPatternScreen
import cc.dlabs.pesamind.features.settings.security.SetPinScreen
import cc.dlabs.pesamind.features.tools.SetMonthlyBudgetScreen
import cc.dlabs.pesamind.features.tools.YearlyBudgetDetailScreen
import java.util.Calendar

@Composable
fun PesaMindNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Routes.Splash.route) {
        composable(Routes.Splash.route) { SplashScreen(navController) }
        composable(Routes.Login.route) { LoginScreen(navController) }
        composable(Routes.Register.route) { RegisterScreen(navController) }
        composable(Routes.LockSetup.route) { LockSetupScreen(navController) }
        composable(Routes.PinSetup.route) { PinUnlockScreen(navController, isSetup = true) }
        composable(Routes.PatternSetup.route) { PatternUnlockScreen(navController, isSetup = true) }
        composable(Routes.PinUnlock.route) { PinUnlockScreen(navController) }
        composable(Routes.PatternUnlock.route) { PatternUnlockScreen(navController) }
        composable(Routes.Dashboard.route) { MainScreen(navController) }

        composable(Routes.SecuritySettings.route) { SecuritySettingsScreen(navController) }
        composable(Routes.SetPin.route) { SetPinScreen(navController) }
        composable(Routes.SetPattern.route) { SetPatternScreen(navController) }

        composable(Routes.AccountSettings.route) { AccountSettingsScreen(navController) }
        composable(Routes.ChangePassword.route) { ChangePasswordScreen(navController) }
        composable(Routes.Channels.route) { ChannelScreen(navController) }

//        Adding Transaction routes
        composable("add_transaction") {
            AddTransactionScreen(navController)
        }
        composable(Routes.TransactionList.route) { TransactionListScreen(navController) }
        composable(
            route = Routes.SetMonthlyBudget.route,
            arguments = listOf(
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val month = backStackEntry.arguments?.getInt("month") ?: 1
            val year = backStackEntry.arguments?.getInt("year") ?: 2026
            SetMonthlyBudgetScreen(
                navController = navController,
                month = month,
                year = year
            )
        }
        composable( Routes.SetYearlyBudget.route) {
            YearlyBudgetDetailScreen(navController, Calendar.getInstance().get(Calendar.YEAR))
        }
    }
}