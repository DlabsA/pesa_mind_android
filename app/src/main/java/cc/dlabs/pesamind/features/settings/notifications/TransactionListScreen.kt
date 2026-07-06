package cc.dlabs.pesamind.features.settings.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import cc.dlabs.pesamind.core.theme.AmountSmall
import cc.dlabs.pesamind.core.theme.DarkColors
import cc.dlabs.pesamind.core.theme.LightColors
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import java.text.NumberFormat
import java.util.Locale

// ─── Formatters ───────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)
private fun Double.toUgx() = ugxFmt.format(this)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavHostController,
    viewModel: TransactionViewModel = viewModel()
) {
    val state       by viewModel.state.collectAsStateWithLifecycle()
    val transactions = state.transactions
    val isLoading   = state.isLoading
    val error       = state.error

    // Summary totals — computed once from the list
    val totalIncome  = remember(transactions) {
        transactions.filter { it.type.equals("income",  ignoreCase = true) }
            .sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.type.equals("expense", ignoreCase = true) }
            .sumOf { it.amount }
    }
    val totalSaving = remember(transactions) {
        transactions.filter {
            it.type.equals("saving", ignoreCase = true) ||
                it.type.equals("savings", ignoreCase = true)
        }.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transactions",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh    = { viewModel.refresh() },
            modifier     = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // ── Loading skeleton ───────────────────────────────────────
                isLoading && transactions.isEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { SummaryCardSkeleton() }
                        items(5) { TransactionCardSkeleton() }
                    }
                }

                // ── Error ──────────────────────────────────────────────────
                error != null && transactions.isEmpty() -> {
                    ErrorState(
                        message = error,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                }

                // ── Empty ──────────────────────────────────────────────────
                transactions.isEmpty() -> {
                    EmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                }

                // ── Content ────────────────────────────────────────────────
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start  = 16.dp,
                            top    = 12.dp,
                            end    = 16.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Summary banner
                        item(key = "summary") {
                            AnimatedVisibility(
                                visible = true,
                                enter   = fadeIn() + slideInVertically { -it / 4 }
                            ) {
                                SummaryBanner(
                                    totalIncome  = totalIncome,
                                    totalExpense = totalExpense,
                                    totalSaving  = totalSaving,
                                    count        = transactions.size
                                )
                            }
                        }

                        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }

                        // Transaction rows with staggered entrance
                        itemsIndexed(
                            items = transactions,
                            key   = { _, tx -> tx.id }
                        ) { idx, tx ->
                            AnimatedVisibility(
                                visible = true,
                                enter   = fadeIn(tween(220, delayMillis = idx.coerceAtMost(8) * 40))
                                        + slideInVertically(
                                    tween(220, delayMillis = idx.coerceAtMost(8) * 40)
                                ) { it / 6 }
                            ) {
                                TransactionCard(tx = tx)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Summary Banner ───────────────────────────────────────────────────────────

@Composable
private fun SummaryBanner(
    totalIncome:  Double,
    totalExpense: Double,
    totalSaving:  Double,
    count:        Int
) {
    val net      = totalIncome - totalExpense
    val isDeficit = net < 0

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle decorative circles
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.06f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = 10.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.05f),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text  = "$count transactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                )

                Spacer(Modifier.height(4.dp))

                // Income / Expense row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryPill(
                        label    = "Income",
                        amount   = totalIncome,
                        color    = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryPill(
                        label    = "Expense",
                        amount   = totalExpense,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryPill(
                        label    = "Saving",
                        amount   = totalSaving,
                        color    = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label:    String,
    amount:   Double,
    color:    Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = color
                        )
                    ) { append(amount.toUgx()) }
                    withStyle(
                        SpanStyle(
                            fontSize = 10.sp,
                            color    = color.copy(alpha = 0.65f)
                        )
                    ) { append(" UGX") }
                }
            )
        }
    }
}

// ─── Transaction Card ─────────────────────────────────────────────────────────

@Composable
private fun TransactionCard(tx: TransactionDetails) {
    val isIncome    = tx.type.equals("income", ignoreCase = true)
    val accentColor = if (isIncome) MaterialTheme.colorScheme.tertiary
    else          MaterialTheme.colorScheme.error
    val accentBg    = if (isIncome) MaterialTheme.colorScheme.tertiaryContainer
    else          MaterialTheme.colorScheme.errorContainer

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Type badge ─────────────────────────────────────────────────
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = accentBg,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text     = if (isIncome) "↑" else "↓",
                        fontSize = 20.sp,
                        color    = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Main content ───────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Channel name
                Text(
                    text     = tx.channelDetailsName.ifBlank { "Transaction" },
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                // Username
                Text(
                    text  = tx.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Note chip (if present)
                if (tx.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier          = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector  = Icons.Outlined.Notes,
                            contentDescription = null,
                            modifier     = Modifier.size(12.dp),
                            tint         = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text     = tx.note,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Amount ─────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = accentColor,
                                letterSpacing = (-0.2).sp
                            )
                        ) {
                            append(if (isIncome) "+" else "−")
                            append(tx.amount.toUgx())
                        }
                    }
                )
                Text(
                    text  = "UGX",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.55f)
                )
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Surface(
            shape  = CircleShape,
            color  = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = Icons.Outlined.SwapVert,
                    contentDescription = null,
                    modifier           = Modifier.size(36.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text      = "No transactions yet",
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color     = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Your recorded transactions will appear here",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Error State ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(
    message:  String,
    onRetry:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text      = "Something went wrong",
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color     = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onRetry,
            shape   = RoundedCornerShape(12.dp),
            colors  = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Try Again")
        }
    }
}

// ─── Skeleton / Shimmer ───────────────────────────────────────────────────────

@Composable
private fun ShimmerBox(
    modifier:     Modifier,
    cornerRadius: Int = 8
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue  = 0.25f,
        targetValue   = 0.65f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.10f)
            )
    )
}

@Composable
private fun SummaryCardSkeleton() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(Modifier.width(100.dp).height(12.dp))
            Spacer(Modifier.height(10.dp))
            ShimmerBox(Modifier.width(200.dp).height(32.dp), cornerRadius = 10)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.weight(1f).height(52.dp), cornerRadius = 12)
                ShimmerBox(Modifier.weight(1f).height(52.dp), cornerRadius = 12)
            }
        }
    }
}

@Composable
private fun TransactionCardSkeleton() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ShimmerBox(Modifier.size(46.dp), cornerRadius = 12)
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(Modifier.width(130.dp).height(13.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.width(80.dp).height(11.dp))
            }
            ShimmerBox(Modifier.width(70.dp).height(15.dp), cornerRadius = 6)
        }
    }
}