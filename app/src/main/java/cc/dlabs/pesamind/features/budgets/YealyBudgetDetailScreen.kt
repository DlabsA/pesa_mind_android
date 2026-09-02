package cc.dlabs.pesamind.features.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.AddTransactionSheet
import cc.dlabs.pesamind.core.ui.DetailScreenTopBar
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.ShimmerBox
import cc.dlabs.pesamind.core.ui.SkeletonCard
import cc.dlabs.pesamind.core.ui.StatsRowsSkeleton
import cc.dlabs.pesamind.core.ui.rememberShimmerAlpha
import cc.dlabs.pesamind.core.ui.typeColor
import cc.dlabs.pesamind.core.ui.typeIcon
import cc.dlabs.pesamind.core.utils.TransactionTypes
import java.text.NumberFormat
import java.util.Locale

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)

private fun Long.toUgx() = ugxFmt.format(this)

private fun Double.toUgx() = ugxFmt.format(this.toLong())

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyBudgetDetailScreen(
    navController: NavController,
    year: Int,
    vm: YearlyBudgetViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(year) { vm.init(year) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailScreenTopBar(
                title = "Yearly Budget",
                subtitle = "Manage your yearly plan",
                badge = year.toString(),
                onBack = { navController.popBackStack() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = vm::refresh,
            indicator = {},
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 14.dp,
                        bottom = Spacing.Space10.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Space5.dp),
            ) {
                // ── Summary card
                item {
                    BudgetSummaryCard(
                        income = state.totalIncome,
                        expenditure = state.totalExpenditures,
                        savings = state.totalSavings,
                        balance = state.balance,
                        isDeficit = state.isDeficit,
                        isLoading = state.isLoading,
                    )
                }

                // ── Transactions section header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Budgeted Transactions",
                            style =
                                MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.transactions.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            ) {
                                Text(
                                    text = "${state.transactions.size}",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                // ── Transaction list or empty state
                if (state.isLoading && state.transactions.isEmpty()) {
                    item { TransactionsSkeleton() }
                } else if (state.transactions.isEmpty()) {
                    item { TransactionsEmptyState() }
                } else {
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionRow(
                            tx = tx,
                            isDeleting = state.isDeletingTransactionId == tx.id,
                            onDelete = { vm.confirmDeleteTransaction(tx) },
                        )
                    }
                }
            }
        }
    }

    // ── Delete confirmation dialog
    state.pendingDeleteTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteTransaction() },
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    "Remove Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    "\"${tx.name}\" will be removed from this budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteTransaction() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDeleteTransaction() }) { Text("Cancel") }
            },
        )
    }

    // ── Add transaction sheet
    if (showAddSheet) {
        AddTransactionSheet(
            name = state.formName,
            amount = state.formAmount,
            type = state.formType,
            nameError = state.formNameError,
            amountError = state.formAmountError,
            isSaving = state.isAddingTransaction,
            onNameChange = vm::onNameChange,
            onAmountChange = vm::onAmountChange,
            onTypeChange = vm::onTypeChange,
            onAdd = vm::addTransaction,
            onDismiss = { showAddSheet = false },
        )
    }
}

// ─── Budget Summary Card ──────────────────────────────────────────────────────

@Composable
private fun BudgetSummaryCard(
    income: Long,
    expenditure: Long,
    savings: Long,
    balance: Long,
    isDeficit: Boolean,
    isLoading: Boolean,
) {
    val balanceColor = if (isDeficit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with balance indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Monthly Calculated Budget",
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!isLoading && (income > 0 || expenditure > 0)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = balanceColor.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = if (isDeficit) "Deficit" else "Surplus",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = balanceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading && income == 0L && expenditure == 0L) {
                SummaryCardSkeleton()
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        SummaryStatRow(
                            label = "Expenditure",
                            value = expenditure,
                            color = MaterialTheme.colorScheme.error,
                            icon = Icons.AutoMirrored.Outlined.TrendingDown,
                        )
                        SummaryDivider()
                        SummaryStatRow(
                            label = "Income",
                            value = income,
                            color = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        )
                        SummaryDivider()
                        SummaryStatRow(
                            label = "Savings",
                            value = savings,
                            color = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.Savings,
                        )

                        // Balance row (only if there's data)
                        if (income > 0L || expenditure > 0L) {
                            SummaryDivider()
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Balance",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text =
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = balanceColor)) {
                                                append(if (isDeficit) "−" else "+")
                                            }
                                            withStyle(
                                                SpanStyle(
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = balanceColor,
                                                ),
                                            ) { append(Math.abs(balance).toUgx()) }
                                            withStyle(
                                                SpanStyle(fontSize = 10.sp, color = balanceColor.copy(0.6f)),
                                            ) { append(" UGX") }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatRow(
    label: String,
    value: Long,
    color: Color,
    icon: ImageVector,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.10f),
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)) {
                        append(value.toUgx())
                    }
                    withStyle(SpanStyle(fontSize = 10.sp, color = color.copy(0.6f))) {
                        append(" UGX")
                    }
                },
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
    )
}

// ─── Transaction Row ──────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(
    tx: BudgetTransactionResponse,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    val color = typeColor(tx.type)
    val icon = typeIcon(tx.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
        ) {
            // Colored left strip
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(color),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Type icon badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.10f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    }
                }

                // Name + type label
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.name,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = TransactionTypes.displayName(tx.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.80f),
                    )
                }

                // Amount
                Text(
                    text =
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                ),
                            ) { append(tx.amount.toUgx()) }
                            withStyle(
                                SpanStyle(fontSize = 10.sp, color = color.copy(0.6f)),
                            ) { append(" UGX") }
                        },
                )

                // Delete button or spinner
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty / Skeleton states ──────────────────────────────────────────────────

@Composable
private fun TransactionsEmptyState() {
    EmptyState(
        icon = Icons.Outlined.Receipt,
        title = "No transactions yet",
        subtitle = "Add income, expenditure and savings\nitems using the form above.",
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        iconSize = 64.dp,
        iconContentSize = 28.dp,
        iconShape = RoundedCornerShape(18.dp),
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        subtitleStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        iconSpacing = 8.dp,
        textSpacing = 8.dp,
    )
}

@Composable
private fun TransactionsSkeleton() {
    val accentAlpha = rememberShimmerAlpha()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            SkeletonCard(
                accentBrush = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = accentAlpha * 0.2f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerBox(Modifier.size(38.dp), cornerRadius = 10.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.3f).height(10.dp))
                    }
                    ShimmerBox(Modifier.width(80.dp).height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryCardSkeleton() {
    StatsRowsSkeleton()
}
