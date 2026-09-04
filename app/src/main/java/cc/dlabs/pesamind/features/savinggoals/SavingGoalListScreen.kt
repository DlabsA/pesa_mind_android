package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import cc.dlabs.pesamind.core.ui.PremiumUpsellCard
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import cc.dlabs.pesamind.core.ui.StaggeredCard
import cc.dlabs.pesamind.core.ui.SyncStatusBadge
import cc.dlabs.pesamind.core.ui.asUgxAmount
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalListScreen(
    navController: NavHostController,
    viewModel: SavingGoalViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The ViewModel has always set error/message on create/update/delete; nothing rendered
    // them before, so a failed save looked like a silent no-op.
    LaunchedEffect(state.error, state.message) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            BackStyleHeader(
                title = PesaMindStrings.SavingGoal.FEATURE_NAME,
                onBack = { navController.popBackStack() },
                isRefreshing = state.isLoading,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = Spacing.Space2.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isPremium && state.goals.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New goal") },
                )
            }
        },
    ) { padding ->
        if (!state.isPremium) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.Space4.dp)) {
                PremiumUpsellCard(
                    feature = PesaMindStrings.SavingGoal.FEATURE_NAME,
                    onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                    description = "Set a target and track your progress toward it.",
                )
            }
            return@Scaffold
        }

        when {
            state.isLoading && state.goals.isEmpty() -> {
                SkeletonColumn(
                    blockHeights = listOf(120.dp, 96.dp, 96.dp, 96.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            state.goals.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.Space8.dp)) {
                    EmptyState(
                        icon = Icons.Filled.Savings,
                        title = "No saving goals yet",
                        subtitle = "Set a target — a trip, an emergency fund, a new phone — and watch the progress fill up.",
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            ExtendedFloatingActionButton(
                                onClick = { showCreateSheet = true },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text("Add your first goal") },
                            )
                        },
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding =
                        PaddingValues(
                            start = Spacing.Space3.dp,
                            end = Spacing.Space3.dp,
                            top = Spacing.Space3.dp,
                            // Clear the extended FAB.
                            bottom = Spacing.space20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
                ) {
                    item(key = "summary") {
                        StaggeredCard(index = 0, visible = true) {
                            SavingGoalsSummaryCard(goals = state.goals)
                        }
                    }

                    itemsIndexed(state.goals, key = { _, goal -> goal.id }) { index, goal ->
                        StaggeredCard(index = index + 1, visible = true) {
                            SavingGoalCard(
                                goal = goal,
                                onClick = { navController.navigate(Routes.SavingGoalDetail.createRoute(goal.id)) },
                            )
                        }
                    }
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

/** Roll-up across every goal: what's been put away, against the combined target. */
@Composable
private fun SavingGoalsSummaryCard(goals: List<SavingGoalResponse>) {
    val totalSaved = goals.sumOf { it.progress }
    val totalTarget = goals.sumOf { it.targetAmount }
    val achievedCount = goals.count { it.isAchieved }
    val fraction = if (totalTarget > 0) (totalSaved / totalTarget).coerceIn(0.0, 1.0).toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.Space4.dp)) {
            Text(
                text = "Total saved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.Space1.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = totalSaved.asUgxAmount(),
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.Space2.dp))
                Text(
                    text = "UGX",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(Spacing.Space1.dp))
            Text(
                text = "of ${totalTarget.asUgxAmount()} across ${goals.size} goal${if (goals.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.Space3.dp))
            GoalProgressBar(fraction = fraction, height = 10.dp)
            Spacer(Modifier.height(Spacing.Space2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${(fraction * 100).roundToInt()}% of the way there",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (achievedCount > 0) {
                    Text(
                        text = "$achievedCount achieved",
                        style = MaterialTheme.typography.labelMedium,
                        color = getTertiaryColor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingGoalCard(
    goal: SavingGoalResponse,
    onClick: () -> Unit,
) {
    val achieved = goal.isAchieved
    val accent = if (achieved) getTertiaryColor() else MaterialTheme.colorScheme.primary
    val fraction = goal.progressFraction
    val targetDate = rememberFormattedDate(goal.targetDate)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Large.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.Space4.dp - 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIconTile(achieved = achieved)
                Spacer(Modifier.width(Spacing.Space3.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        SyncStatusBadge(
                            status = goal.syncStatus,
                            modifier = Modifier.padding(start = Spacing.Space2.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = targetDate?.let { "Target by $it" } ?: "No target date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(Spacing.Space2.dp))
                GoalPill(
                    text = if (achieved) "Achieved" else "${(fraction * 100).roundToInt()}%",
                    color = accent,
                )
            }

            Spacer(Modifier.height(Spacing.Space3.dp))
            GoalProgressBar(fraction = fraction, color = accent)
            Spacer(Modifier.height(Spacing.Space2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${goal.progress.asUgxAmount()} of ${goal.targetAmount.asUgxAmount()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!achieved) {
                    Text(
                        text = "${goal.remainingAmount.asUgxAmount()} to go",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
