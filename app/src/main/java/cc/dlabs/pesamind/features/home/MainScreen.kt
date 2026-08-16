package cc.dlabs.pesamind.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay

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
    // the FAB floats in the bottom-right corner, clear of the pills.
    //
    // Because content extends under the bar, give each screen GlassBottomBarClearance of
    // bottom padding in its scrollable so the last row isn't hidden behind the floating bar.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.Home.route,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
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

        var showAddHint by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(3_000)
            showAddHint = false
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Spacing.Space6.dp, bottom = GlassBottomBarClearance - Spacing.Space2.dp),
        ) {
            AddTransactionHint(
                visible = showAddHint,
                onDismiss = { showAddHint = false },
            )

            FloatingActionButton(
                onClick = { rootNav.navigate(Routes.AddTransaction.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((Spacing.Space12 + 4).dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add transaction",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Small dismissible speech-bubble hint pointing at the FAB, introducing "Add transaction". */
@Composable
private fun AddTransactionHint(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)),
    ) {
        val bubbleColor = MaterialTheme.colorScheme.primary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(bubbleColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .padding(horizontal = Spacing.Space3.dp, vertical = Spacing.Space2.dp),
            ) {
                Text(
                    text = "Add a transaction",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Canvas(modifier = Modifier.size(width = 7.dp, height = 12.dp)) {
                val path =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(path, color = bubbleColor)
            }
        }
    }
}
