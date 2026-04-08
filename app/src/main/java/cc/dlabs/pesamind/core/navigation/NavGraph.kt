package cc.dlabs.pesamind.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cc.dlabs.pesamind.features.splash.SplashScreen
import cc.dlabs.pesamind.features.auth.LoginScreen
import cc.dlabs.pesamind.features.auth.PatternUnlockScreen
import cc.dlabs.pesamind.features.auth.PinUnlockScreen
import cc.dlabs.pesamind.features.home.MainScreen

@Composable
fun PesaMindNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Routes.Splash.route) {
        composable(Routes.Splash.route) { SplashScreen(navController) }
        composable(Routes.Login.route) { LoginScreen(navController) }
        composable(Routes.PinUnlock.route) { PinUnlockScreen(navController) }
        composable(Routes.PatternUnlock.route) { PatternUnlockScreen(navController) }
        composable(Routes.Dashboard.route) { MainScreen(navController) }
    }
}