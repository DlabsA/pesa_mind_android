package cc.dlabs.pesamind.features.lentborrowed

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.HIDDEN_MOBILE_MONEY_HINT
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import cc.dlabs.pesamind.core.ui.QuickPaymentDialog
import cc.dlabs.pesamind.core.ui.TransactionCard
import cc.dlabs.pesamind.core.ui.TransactionDetailSheet
import cc.dlabs.pesamind.core.ui.asUgxAmount
import cc.dlabs.pesamind.core.ui.hasHiddenMobileMoneyChannels
import cc.dlabs.pesamind.core.ui.typeColor
import cc.dlabs.pesamind.core.ui.visibleChannels
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.home.TYPE_EXPENSE
import cc.dlabs.pesamind.features.home.TYPE_INCOME
import cc.dlabs.pesamind.features.settings.channels.ChannelViewModel
import java.text.SimpleDateFormat
import java.util.Locale as JavaLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LentBorrowedDetailScreen(
    navController: NavHostController,
    debtCreditId: String,
    viewModel: DebtCreditViewModel = viewModel(),
    transactionViewModel: TransactionViewModel = viewModel(),
    channelViewModel: ChannelViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val debt = state.debts.find { it.id == debtCreditId }

    var transactions by remember { mutableStateOf<List<TransactionDetails>>(emptyList()) }
    var selectedTx by remember { mutableStateOf<TransactionDetails?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    val txState by transactionViewModel.state.collectAsStateWithLifecycle()
    val channelState by channelViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(debtCreditId) {
        TransactionRepository.observeByDebtCredit(debtCreditId).collect { transactions = it }
    }

    // Close the dialog only on a real success; a failure keeps it open so the entered amount
    // isn't lost. Either way the message is cleared so it can't re-fire on the next open.
    LaunchedEffect(txState.message, txState.error) {
        val message = txState.message ?: txState.error ?: return@LaunchedEffect
        if (txState.message != null) showPaymentDialog = false
        transactionViewModel.clearMessages()
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BackStyleHeader(
                title = debt?.counterpartyName ?: "",
                onBack = { navController.popBackStack() },
                isRefreshing = state.isLoading,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = 8.dp),
            )
        },
    ) { padding ->
        if (debt == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                DebtCreditSummaryCard(debt = debt, onAddPayment = { showPaymentDialog = true })
            }

            if (transactions.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Receipt,
                            title = "No payments yet",
                            subtitle = "Payments linked to this debt will show up here.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    TransactionCard(tx = tx, onClick = { selectedTx = tx })
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Quick "Add payment" dialog — type follows the debt's direction, so the user never picks it.
    if (showPaymentDialog && debt != null) {
        val paymentType = if (debt.direction == "lent") TYPE_EXPENSE else TYPE_INCOME
        QuickPaymentDialog(
            title = "Add payment",
            subtitle = "${PesaMindStrings.DebtCredit.directionLabel(debt.direction)} · ${debt.counterpartyName}",
            channels = visibleChannels(channelState),
            channelSupportingText = HIDDEN_MOBILE_MONEY_HINT.takeIf { hasHiddenMobileMoneyChannels(channelState) },
            defaultNote = "Payment · ${debt.counterpartyName}",
            isSaving = txState.isSaving,
            accentColor = typeColor(paymentType),
            onConfirm = { channelId, amount, note ->
                transactionViewModel.createTransaction(
                    channelID = channelId,
                    amount = amount,
                    type = paymentType,
                    note = note,
                    debtCreditId = debtCreditId,
                )
            },
            onDismiss = { showPaymentDialog = false },
        )
    }

    // Transaction detail sheet
    selectedTx?.let { tx ->
        ModalBottomSheet(
            onDismissRequest = { selectedTx = null },
        ) {
            TransactionDetailSheet(
                tx = tx,
                onClose = { selectedTx = null },
            )
        }
    }
}

@Composable
private fun DebtCreditSummaryCard(
    debt: cc.dlabs.pesamind.core.network.models.DebtCreditResponse,
    onAddPayment: () -> Unit,
) {
    val isLent = debt.direction == "lent"
    val accentColor = if (isLent) getTertiaryColor() else getErrorColor()
    val accentBg = if (isLent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", JavaLocale.getDefault()) }
    val dueDateFormatted =
        remember(debt.dueDate) {
            debt.dueDate?.let {
                try {
                    dateFormatter.format(java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli())
                } catch (_: Exception) {
                    null
                }
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header row with icon and direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    color = accentBg,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.counterpartyName,
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
                        text = PesaMindStrings.DebtCredit.directionLabel(debt.direction),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.25.sp,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Status badge
                if (debt.status == "settled") {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = getTertiaryColor().copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = "Settled",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = getTertiaryColor(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Outstanding amount
            Text(
                text = "Outstanding",
                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = debt.outstanding.asUgxAmount(),
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "of ${debt.originalAmount.asUgxAmount()} original",
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )

            // Due date if present
            if (dueDateFormatted != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Due $dueDateFormatted",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Add payment button
            Button(
                onClick = onAddPayment,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Add Payment",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
