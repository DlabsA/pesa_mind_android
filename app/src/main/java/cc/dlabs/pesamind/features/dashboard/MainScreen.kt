package cc.dlabs.pesamind.features.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.features.analytics.AnalyticsScreen
import cc.dlabs.pesamind.features.tools.BudgetScreen
import cc.dlabs.pesamind.features.settings.SettingsScreen

data class BottomNavItem(
    val label: String,
    val route: Routes,
    val icon: ImageVector
)

@Composable
fun MainScreen(rootNav: NavHostController) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem("Home", Routes.Dashboard, Icons.Filled.Home),
        BottomNavItem("Analytics", Routes.Analytics, Icons.Filled.AccountCircle),
        BottomNavItem("Tools", Routes.Tools, Icons.Filled.Build),
        BottomNavItem("Settings", Routes.Settings, Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentEntry by navController.currentBackStackEntryAsState()
                val current = currentEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        selected = current == item.route.route,
                        onClick = { navController.navigate(item.route.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { rootNav.navigate(Routes.AddTransaction.route) },
                containerColor = PesaMindTeal
            ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        NavHost(navController, startDestination = Routes.Dashboard.route,
            modifier = Modifier.padding(padding)) {
            composable(Routes.Dashboard.route) { DashboardScreen(rootNav) }
            composable(Routes.Analytics.route) { AnalyticsScreen(rootNav) }
            composable(Routes.Tools.route) { BudgetScreen(rootNav) }
            composable(Routes.Settings.route) { SettingsScreen(rootNav) }
        }
    }
}
