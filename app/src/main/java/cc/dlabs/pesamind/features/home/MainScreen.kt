package cc.dlabs.pesamind.features.home

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.features.analytics.AnalyticsScreen
import cc.dlabs.pesamind.features.budgets.BudgetScreen
import cc.dlabs.pesamind.features.dashboard.DashboardScreen
import cc.dlabs.pesamind.features.settings.SettingsScreen

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

@Composable
fun MainScreen(rootNav: NavHostController) {
    val navController = rememberNavController()
    val items =
        listOf(
            BottomNavItem("Home", Routes.Home.route, Icons.Filled.Home),
            BottomNavItem("Analytics", Routes.Analytics.route, Icons.Filled.BarChart),
            BottomNavItem("Budget", Routes.Tools.route, Icons.Filled.MonetizationOn),
            BottomNavItem("Settings", Routes.Settings.route, Icons.Filled.Settings),
        )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
//                tonalElevation = 8.dp
            ) {
                val currentEntry by navController.currentBackStackEntryAsState()
                val current = currentEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        selected = current == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { rootNav.navigate(Routes.AddTransaction.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .size((Spacing.Space12 + 4).dp)
                        .offset(y = (-18).dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        NavHost(
            navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home.route) { DashboardScreen(rootNav) }
            composable(Routes.Analytics.route) { AnalyticsScreen(rootNav) }
            composable(Routes.Tools.route) { BudgetScreen(rootNav) }
            composable(Routes.Settings.route) { SettingsScreen(rootNav) }
        }
    }
}
