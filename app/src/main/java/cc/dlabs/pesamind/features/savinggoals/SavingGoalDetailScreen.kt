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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.HIDDEN_MOBILE_MONEY_HINT
import cc.dlabs.pesamind.core.ui.QuickPaymentDialog
import cc.dlabs.pesamind.core.ui.SectionHeader
import cc.dlabs.pesamind.core.ui.TransactionCard
import cc.dlabs.pesamind.core.ui.TransactionDetailSheet
import cc.dlabs.pesamind.core.ui.asUgxAmount
import cc.dlabs.pesamind.core.ui.hasHiddenMobileMoneyChannels
import cc.dlabs.pesamind.core.ui.typeColor
import cc.dlabs.pesamind.core.ui.visibleChannels
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.home.TYPE_SAVING
import cc.dlabs.pesamind.features.settings.channels.ChannelViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalDetailScreen(
    navController: NavHostController,
    savingGoalId: String,
    viewModel: SavingGoalViewModel = viewModel(),
    transactionViewModel: TransactionViewModel = viewModel(),
    channelViewModel: ChannelViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goal = state.goals.find { it.id == savingGoalId }

    var transactions by remember { mutableStateOf<List<TransactionDetails>>(emptyList()) }
    var selectedTx by remember { mutableStateOf<TransactionDetails?>(null) }
    var showContributionDialog by remember { mutableStateOf(false) }

    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val channelState by channelViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(savingGoalId) {
        TransactionRepository.observeBySavingGoal(savingGoalId).collect { transactions = it }
    }

    // Close the dialog only on a real success; a failure keeps it open so the entered amount
    // isn't lost. Either way the message is cleared so it can't re-fire on the next open.
    LaunchedEffect(txState.message, txState.error) {
        val message = txState.message ?: txState.error ?: return@LaunchedEffect
        if (txState.message != null) showContributionDialog = false
        transactionViewModel.clearMessages()
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BackStyleHeader(
                title = goal?.name ?: "",
                onBack = { navController.popBackStack() },
                isRefreshing = state.isLoading,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = Spacing.Space2.dp),
            )
        },
    ) { padding ->
        if (goal == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.Space3.dp, vertical = Spacing.Space3.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            item(key = "summary") {
                SavingGoalSummaryCard(goal = goal, onAddContribution = { showContributionDialog = true })
            }

            item(key = "contributions_header") {
                SectionHeader(
                    title = "Contributions",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (transactions.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Space10.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Receipt,
                            title = "No contributions yet",
                            subtitle = "Money you put toward this goal will show up here.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    TransactionCard(tx = tx, onClick = { selectedTx = tx })
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(Spacing.Space6.dp)) }
        }
    }

    // Quick "Add contribution" dialog — always a savings transaction, so the user never picks a type.
    if (showContributionDialog && goal != null) {
        QuickPaymentDialog(
            title = "Add contribution",
            subtitle = "Toward ${goal.name}",
            channels = visibleChannels(channelState),
            channelSupportingText = HIDDEN_MOBILE_MONEY_HINT.takeIf { hasHiddenMobileMoneyChannels(channelState) },
            defaultNote = "Contribution · ${goal.name}",
            isSaving = txState.isSaving,
            accentColor = typeColor(TYPE_SAVING),
            onConfirm = { channelId, amount, note ->
                transactionViewModel.createTransaction(
                    channelID = channelId,
                    amount = amount,
                    type = TYPE_SAVING,
                    note = note,
                    savingGoalId = savingGoalId,
                )
            },
            onDismiss = { showContributionDialog = false },
        )
    }

    selectedTx?.let { tx ->
        ModalBottomSheet(onDismissRequest = { selectedTx = null }) {
            TransactionDetailSheet(tx = tx, onClose = { selectedTx = null })
        }
    }
}

@Composable
private fun SavingGoalSummaryCard(
    goal: SavingGoalResponse,
    onAddContribution: () -> Unit,
) {
    val achieved = goal.isAchieved
    val accent = if (achieved) getTertiaryColor() else MaterialTheme.colorScheme.primary
    val fraction = goal.progressFraction
    val targetDate = rememberFormattedDate(goal.targetDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.Space4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GoalIconTile(achieved = achieved)
                Spacer(Modifier.width(Spacing.Space3.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (achieved) "Target reached" else "Saving toward your target",
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

            Spacer(Modifier.height(Spacing.Space4.dp))

            Text(
                text = "Saved so far",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = goal.progress.asUgxAmount(),
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.Space1.dp))
            Text(
                text = "of ${goal.targetAmount.asUgxAmount()} target",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.Space3.dp))
            GoalProgressBar(fraction = fraction, height = 10.dp, color = accent)
            Spacer(Modifier.height(Spacing.Space2.dp))
            Text(
                text =
                    if (achieved) {
                        "You hit this goal — nice work."
                    } else {
                        "${goal.remainingAmount.asUgxAmount()} still to save"
                    },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (achieved) getTertiaryColor() else MaterialTheme.colorScheme.onSurface,
            )

            if (targetDate != null) {
                Spacer(Modifier.height(Spacing.Space3.dp))
                Surface(
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Space3.dp, vertical = Spacing.Space2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Spacing.Space2.dp))
                        Text(
                            text = "Target by $targetDate",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!goal.note.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.Space2.dp))
                Text(
                    text = goal.note!!,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.Space4.dp))

            Button(
                onClick = onAddContribution,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.Space2.dp - 2.dp))
                Text(
                    text = "Add contribution",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
