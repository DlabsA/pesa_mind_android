package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import cc.dlabs.pesamind.core.ui.PremiumUpsellCard
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalListScreen(
    navController: NavHostController,
    viewModel: SavingGoalViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackStyleHeader(
                title = PesaMindStrings.SavingGoal.FEATURE_NAME,
                onBack = { navController.popBackStack() },
                isRefreshing = state.isLoading,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = 8.dp),
            )
        },
        floatingActionButton = {
            if (state.isPremium) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add goal")
                }
            }
        },
    ) { padding ->
        if (!state.isPremium) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                PremiumUpsellCard(
                    feature = PesaMindStrings.SavingGoal.FEATURE_NAME,
                    onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                    description = "Set a target and track your progress toward it.",
                )
            }
            return@Scaffold
        }

        if (state.goals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                EmptyState(
                    icon = Icons.Filled.Savings,
                    title = "No saving goals yet",
                    subtitle = "Tap + to set a target and start tracking progress.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.goals, key = { it.id }) { goal ->
                    SavingGoalCard(goal = goal, onClick = { navController.navigate(Routes.SavingGoalDetail.createRoute(goal.id)) })
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateSavingGoalSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, targetAmount, targetAt, note, offsets ->
                viewModel.createSavingGoal(name, targetAmount, targetAt, note, offsets)
                showCreateSheet = false
            },
        )
    }
}

@Composable
private fun SavingGoalCard(
    goal: SavingGoalResponse,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium)
            val fraction = if (goal.targetAmount > 0) (goal.progress / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val fmt = NumberFormat.getNumberInstance(Locale.getDefault())
                Text("${fmt.format(goal.progress)} of ${fmt.format(goal.targetAmount)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
