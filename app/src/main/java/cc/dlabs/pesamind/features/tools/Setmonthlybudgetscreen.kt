package cc.dlabs.pesamind.features.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.IncomeGreen
import cc.dlabs.pesamind.core.theme.PesaMindGreen
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import cc.dlabs.pesamind.core.utils.TransactionTypes

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)
private fun Long.toUgx() = ugxFmt.format(this)
private fun Double.toUgx() = ugxFmt.format(this.toLong())

private val months = listOf(
    "January","February","March","April","May","June",
    "July","August","September","October","November","December"
)
private fun Int.toMonthName() = months.getOrElse(this - 1) { "Month $this" }


private fun typeColor(type: String): Color = when (type) {
    TransactionTypes.INCOME  -> IncomeGreen
    TransactionTypes.EXPENSE -> ExpenseRed
    TransactionTypes.SAVINGS  -> PesaMindTeal
    else -> Color.Gray
}

private fun typeIcon(type: String): ImageVector = when (type) {
    TransactionTypes.INCOME  -> Icons.Outlined.TrendingUp
    TransactionTypes.EXPENSE -> Icons.Outlined.TrendingDown
    TransactionTypes.SAVINGS  -> Icons.Outlined.Savings
    else -> Icons.Outlined.Payments
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetMonthlyBudgetScreen(
    navController: NavController,
    month: Int,
    year: Int,
    vm: SetMonthlyBudgetViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(month, year) { vm.init(month, year) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${month.toMonthName()} Budget") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Summary card
            item {
                BudgetSummaryCard(
                    income = state.totalIncome,
                    expenditure = state.totalExpenditures,
                    savings = state.totalSavings,
                    balance = state.balance,
                    isDeficit = state.isDeficit,
                    isLoading = state.isLoading
                )
            }

            // ── Add transaction form
            item {
                AddTransactionCard(
                    name = state.formName,
                    amount = state.formAmount,
                    type = state.formType,
                    nameError = state.formNameError,
                    amountError = state.formAmountError,
                    isSaving = state.isAddingTransaction,
                    onNameChange = vm::onNameChange,
                    onAmountChange = vm::onAmountChange,
                    onTypeChange = vm::onTypeChange,
                    onAdd = vm::addTransaction
                )
            }

            // ── Transactions section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budgeted Transactions",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.transactions.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = PesaMindTeal.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = "${state.transactions.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = PesaMindTeal,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
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
                        onDelete = { vm.confirmDeleteTransaction(tx) }
                    )
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
                    color = ExpenseRed.copy(alpha = 0.10f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    "Remove Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    "\"${tx.name}\" will be removed from this budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteTransaction() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDeleteTransaction() }) { Text("Cancel") }
            }
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
    isLoading: Boolean
) {
    val balanceColor = if (isDeficit) ExpenseRed else IncomeGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with balance indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Calculated Budget",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isLoading && (income > 0 || expenditure > 0)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = balanceColor.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = if (isDeficit) "Deficit" else "Surplus",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = balanceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        SummaryStatRow(
                            label = "Expenditure",
                            value = expenditure,
                            color = ExpenseRed,
                            icon = Icons.Outlined.TrendingDown
                        )
                        SummaryDivider()
                        SummaryStatRow(
                            label = "Income",
                            value = income,
                            color = PesaMindGreen,
                            icon = Icons.Outlined.TrendingUp
                        )
                        SummaryDivider()
                        SummaryStatRow(
                            label = "Savings",
                            value = savings,
                            color = PesaMindTeal,
                            icon = Icons.Outlined.Savings
                        )

                        // Balance row (only if there's data)
                        if (income > 0L || expenditure > 0L) {
                            SummaryDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Balance",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = balanceColor)) {
                                            append(if (isDeficit) "−" else "+")
                                        }
                                        withStyle(
                                            SpanStyle(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = balanceColor
                                            )
                                        ) { append(Math.abs(balance).toUgx()) }
                                        withStyle(
                                            SpanStyle(fontSize = 10.sp, color = balanceColor.copy(0.6f))
                                        ) { append(" UGX") }
                                    }
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
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.10f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)) {
                    append(value.toUgx())
                }
                withStyle(SpanStyle(fontSize = 10.sp, color = color.copy(0.6f))) {
                    append(" UGX")
                }
            }
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    )
}

// ─── Add Transaction Card ─────────────────────────────────────────────────────

@Composable
private fun AddTransactionCard(
    name: String,
    amount: String,
    type: String,
    nameError: String?,
    amountError: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add a Transaction",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Monthly Salary", color = TextSecondary) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PesaMindTeal,
                    focusedLabelColor = PesaMindTeal,
                    cursorColor = PesaMindTeal
                )
            )

            // Amount field
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (UGX)") },
                placeholder = { Text("0", color = TextSecondary) },
                singleLine = true,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                prefix = {
                    Text(
                        "UGX  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PesaMindTeal,
                    focusedLabelColor = PesaMindTeal,
                    cursorColor = PesaMindTeal
                )
            )

            // Type chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionTypes.valid.forEach { t ->
                        val selected = type == t
                        val color = typeColor(t)
                        FilterChip(
                            selected = selected,
                            onClick = { onTypeChange(t) },
                            label = {
                                Text(
                                    TransactionTypes.displayName(t),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    typeIcon(t),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                labelColor = TextSecondary
                            ),
                        )
                    }
                }
            }

            // Add button
            Button(
                onClick = onAdd,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
                contentPadding = PaddingValues(vertical = 13.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Adding…",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add Transaction",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

// ─── Transaction Row ──────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(
    tx: BudgetTransactionResponse,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    val color = typeColor(tx.type)
    val icon = typeIcon(tx.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Colored left strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(color)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Type icon badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.10f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    }
                }

                // Name + type label
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = TransactionTypes.displayName(tx.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.80f)
                    )
                }

                // Amount
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        ) { append(tx.amount.toUgx()) }
                        withStyle(
                            SpanStyle(fontSize = 10.sp, color = color.copy(0.6f))
                        ) { append(" UGX") }
                    }
                )

                // Delete button or spinner
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ExpenseRed,
                        strokeWidth = 2.dp
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ExpenseRed.copy(alpha = 0.08f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = ExpenseRed,
                                modifier = Modifier.size(16.dp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = PesaMindTeal.copy(alpha = 0.08f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    tint = PesaMindTeal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            "No transactions yet",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Add income, expenditure and savings\nitems using the form above.",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun TransactionsSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(66.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f),
                                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha * 0.08f))
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.fillMaxWidth(0.5f).height(12.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha * 0.12f),
                                        RoundedCornerShape(6.dp)
                                    )
                            )
                            Box(
                                Modifier.fillMaxWidth(0.3f).height(10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha * 0.08f),
                                        RoundedCornerShape(5.dp)
                                    )
                            )
                        }
                        Box(
                            Modifier.width(80.dp).height(14.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha * 0.10f),
                                    RoundedCornerShape(6.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "alpha"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier.width(90.dp).height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha * 0.20f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Box(
                        Modifier.width(110.dp).height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha * 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }
    }
}