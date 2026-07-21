package cc.dlabs.pesamind.features.settings.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.ErrorState
import cc.dlabs.pesamind.core.ui.ShimmerBox
import cc.dlabs.pesamind.core.ui.SkeletonCard
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// ─── Formatters ───────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)

private fun Double.toUgx() = ugxFmt.format(this)

// ─── Filter state ───────────────────────────────────────────────────────────────

private enum class TxFilter(val label: String) {
    ALL("All"),
    INCOME("Income"),
    EXPENSE("Expense"),
    SAVING("Saving"),
}

private fun TransactionDetails.matchesFilter(filter: TxFilter): Boolean =
    when (filter) {
        TxFilter.ALL -> true
        TxFilter.INCOME -> type.equals("income", ignoreCase = true)
        TxFilter.EXPENSE -> type.equals("expense", ignoreCase = true)
        TxFilter.SAVING -> type.equals("saving", ignoreCase = true) || type.equals("savings", ignoreCase = true)
    }

private fun TransactionDetails.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return channelDetailsName.contains(query, ignoreCase = true) ||
        username.contains(query, ignoreCase = true) ||
        note.contains(query, ignoreCase = true)
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavHostController,
    viewModel: TransactionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transactions = state.transactions
    val isLoading = state.isLoading
    val error = state.error

    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TxFilter.ALL) }
    var selectedTx by remember { mutableStateOf<TransactionDetails?>(null) }

    val hasActiveFilter = searchQuery.isNotBlank() || typeFilter != TxFilter.ALL

    // Summary totals — always computed from the full unfiltered list
    val totalIncome =
        remember(transactions) {
            transactions.filter { it.type.equals("income", ignoreCase = true) }
                .sumOf { it.amount }
        }
    val totalExpense =
        remember(transactions) {
            transactions.filter { it.type.equals("expense", ignoreCase = true) }
                .sumOf { it.amount }
        }
    val totalSaving =
        remember(transactions) {
            transactions.filter {
                it.type.equals("saving", ignoreCase = true) ||
                    it.type.equals("savings", ignoreCase = true)
            }.sumOf { it.amount }
        }

    val filteredTransactions =
        remember(transactions, searchQuery, typeFilter) {
            transactions.filter { it.matchesQuery(searchQuery) && it.matchesFilter(typeFilter) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transactions",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                // ── Loading skeleton ───────────────────────────────────────
                isLoading && transactions.isEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = Spacing.Space4.dp, vertical = Spacing.Space3.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
                    ) {
                        item {
                            SkeletonCard(
                                shape = RoundedCornerShape(20.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentPadding = 20.dp,
                            ) {
                                ShimmerBox(Modifier.width(100.dp).height(12.dp))
                                Spacer(Modifier.height(10.dp))
                                ShimmerBox(Modifier.width(200.dp).height(32.dp), cornerRadius = 10.dp)
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ShimmerBox(Modifier.weight(1f).height(52.dp), cornerRadius = 12.dp)
                                    ShimmerBox(Modifier.weight(1f).height(52.dp), cornerRadius = 12.dp)
                                }
                            }
                        }
                        items(5, key = { it }) {
                            SkeletonCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ShimmerBox(Modifier.size(46.dp), cornerRadius = 12.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        ShimmerBox(Modifier.width(130.dp).height(13.dp))
                                        Spacer(Modifier.height(6.dp))
                                        ShimmerBox(Modifier.width(80.dp).height(11.dp))
                                    }
                                    ShimmerBox(Modifier.width(70.dp).height(15.dp), cornerRadius = 6.dp)
                                }
                            }
                        }
                    }
                }

                // ── Error ──────────────────────────────────────────────────
                error != null && transactions.isEmpty() -> {
                    ErrorState(
                        message = error,
                        onRetry = { viewModel.refresh() },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                    )
                }

                // ── Empty ──────────────────────────────────────────────────
                transactions.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.SwapVert,
                        title = "No transactions yet",
                        subtitle = "Your recorded transactions will appear here",
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                    )
                }

                // ── Content ────────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                start = Spacing.Space4.dp,
                                top = Spacing.Space3.dp,
                                end = Spacing.Space4.dp,
                                bottom = Spacing.Space8.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Summary banner
                        item(key = "summary") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { -it / 4 },
                            ) {
                                SummaryBanner(
                                    totalIncome = totalIncome,
                                    totalExpense = totalExpense,
                                    totalSaving = totalSaving,
                                    count = transactions.size,
                                )
                            }
                        }

                        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }

                        // Search field
                        item(key = "search") {
                            TransactionSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                            )
                        }

                        // Type filter chips
                        item(key = "filters") {
                            TransactionFilterRow(
                                selected = typeFilter,
                                onSelect = { typeFilter = it },
                            )
                        }

                        if (hasActiveFilter) {
                            item(key = "result_count") {
                                Text(
                                    text = "${filteredTransactions.size} of ${transactions.size} transactions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                                )
                            }
                        }

                        if (filteredTransactions.isEmpty()) {
                            item(key = "no_results") {
                                EmptyState(
                                    icon = Icons.Outlined.Search,
                                    title = "No matching transactions",
                                    subtitle = "Try a different search term or filter",
                                    iconSize = 32.dp,
                                    iconContentSize = 32.dp,
                                    iconBackground = Color.Transparent,
                                    titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    subtitleStyle = MaterialTheme.typography.bodySmall,
                                    iconSpacing = 12.dp,
                                    textSpacing = 4.dp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    action = {
                                        TextButton(onClick = {
                                            searchQuery = ""
                                            typeFilter = TxFilter.ALL
                                        }) {
                                            Text("Clear search & filters")
                                        }
                                    },
                                )
                            }
                        } else {
                            // Transaction rows with staggered entrance
                            itemsIndexed(
                                items = filteredTransactions,
                                key = { _, tx -> tx.id },
                            ) { idx, tx ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter =
                                        fadeIn(tween(220, delayMillis = idx.coerceAtMost(8) * 40)) +
                                            slideInVertically(
                                                tween(220, delayMillis = idx.coerceAtMost(8) * 40),
                                            ) { it / 6 },
                                ) {
                                    TransactionCard(tx = tx, onClick = { selectedTx = tx })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Details bottom sheet ────────────────────────────────────────────────
    selectedTx?.let { tx ->
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { selectedTx = null },
            sheetState = sheetState,
        ) {
            TransactionDetailSheet(
                tx = tx,
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) selectedTx = null
                    }
                },
            )
        }
    }
}

// ─── Search & Filters ───────────────────────────────────────────────────────────

@Composable
private fun TransactionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text("Search by channel, sender or note", style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
    )
}

@Composable
private fun TransactionFilterRow(
    selected: TxFilter,
    onSelect: (TxFilter) -> Unit,
) {
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TxFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                colors = chipColors,
            )
        }
    }
}

// ─── Summary Banner ───────────────────────────────────────────────────────────

@Composable
private fun SummaryBanner(
    totalIncome: Double,
    totalExpense: Double,
    totalSaving: Double,
    count: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle decorative circles
            Box(
                modifier =
                    Modifier
                        .size(130.dp)
                        .offset(x = (-30).dp, y = (-30).dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.06f),
                            CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.05f),
                            CircleShape,
                        ),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$count transactions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "UGX",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Income / Expense / Saving row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryPill(
                        label = "Income",
                        amount = totalIncome,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryPill(
                        label = "Expense",
                        amount = totalExpense,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryPill(
                        label = "Saving",
                        amount = totalSaving,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(color, CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = amount.toUgx(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        letterSpacing = (-0.2).sp,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

// ─── Transaction Card ─────────────────────────────────────────────────────────

@Composable
private fun TransactionCard(
    tx: TransactionDetails,
    onClick: () -> Unit,
) {
    val isIncome = tx.type.equals("income", ignoreCase = true)
    val accentColor =
        if (isIncome) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        }
    val accentBg =
        if (isIncome) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Type badge ─────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentBg,
                modifier = Modifier.size(46.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Main content ───────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Channel name
                Text(
                    text = tx.channelDetailsName.ifBlank { "Transaction" },
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(2.dp))

                // Username
                Text(
                    text = tx.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Note chip (if present)
                if (tx.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = tx.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ── Amount ─────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = (-0.2).sp,
                                ),
                            ) {
                                append(if (isIncome) "+" else "−")
                                append(tx.amount.toUgx())
                            }
                        },
                )
                Text(
                    text = "UGX",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

// ─── Transaction Detail Sheet ─────────────────────────────────────────────────

@Composable
private fun TransactionDetailSheet(
    tx: TransactionDetails,
    onClose: () -> Unit,
) {
    val isIncome = tx.type.equals("income", ignoreCase = true)
    val accentColor = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val accentBg = if (isIncome) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val typeLabel = tx.type.replaceFirstChar { it.uppercase() }.ifBlank { "Transaction" }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = accentBg, modifier = Modifier.size(56.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text =
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                letterSpacing = (-0.3).sp,
                            ),
                        ) {
                            append(if (isIncome) "+" else "−")
                            append(tx.amount.toUgx())
                        }
                        withStyle(SpanStyle(fontSize = 14.sp, color = accentColor.copy(alpha = 0.6f))) {
                            append(" UGX")
                        }
                    },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            Surface(shape = RoundedCornerShape(999.dp), color = accentBg) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))

        DetailRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "Channel",
            value = tx.channelDetailsName.ifBlank { "—" },
        )
        DetailRow(
            icon = Icons.Outlined.Person,
            label = "From / Sender",
            value = tx.username.ifBlank { "—" },
        )
        if (tx.note.isNotBlank()) {
            DetailRow(
                icon = Icons.Outlined.Notes,
                label = "Note",
                value = tx.note,
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
