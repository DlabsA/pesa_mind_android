package cc.dlabs.pesamind.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    // Overlay layout (not Scaffold's bottomBar slot): content runs full-bleed under the
    // floating bar so the translucent glass has something behind it to show through, and
    // the FAB is lifted clear of the pills.
    //
    // Because content extends under the bar, give each screen ~96.dp of bottom padding in
    // its scrollable so the last row isn't hidden behind the floating bar + FAB.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.Home.route) { DashboardScreen(rootNav) }
            composable(Routes.Analytics.route) { AnalyticsScreen(rootNav) }
            composable(Routes.Tools.route) { BudgetScreen(rootNav) }
            composable(Routes.Settings.route) { SettingsScreen(rootNav) }
        }

        GlassBottomBar(
            navController = navController,
            items = items,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        FloatingActionButton(
            onClick = { rootNav.navigate(Routes.AddTransaction.route) },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-100).dp)
                    .size((Spacing.Space12 + 4).dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add transaction",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
