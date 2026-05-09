package cc.dlabs.pesamind.features.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.network.analytics.*
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.IncomeGreen
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ─── Formatters ───────────────────────────────────────────────────────────────
private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)
private fun Long.ugx() = ugxFmt.format(this)
private fun Double.ugx() = ugxFmt.format(this.toLong())

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // ── Hero Header ──────────────────────────────────────────────
                item {
                    DashboardHero(
                        userName = uiState.userDisplayName,
                        userInitials = uiState.userInitials,
                        summary = uiState.summary?.data,
                        isLoading = uiState.isLoading,
                        onAnalytics = { navController.navigate("analytics") }
                    )
                }

                // ── Quick Stats row ──────────────────────────────────────────
                item {
                    uiState.summary?.data?.let { data ->
                        QuickStatsRow(data, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    } ?: if (uiState.isLoading) {
                        QuickStatsRowSkeleton(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No summary data available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // ── Spending Velocity ────────────────────────────────────────
                item {
                    uiState.spendingVelocity?.data?.let { velocity ->
                        SpendingVelocityCard(
                            velocity,
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Budget Utilization ───────────────────────────────────────
                item {
                    uiState.budgetUtilization?.let { util ->
                        BudgetUtilizationCard(
                            utilization = util.budgetUtilization,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Anomaly Banner ───────────────────────────────────────────
                item {
                    uiState.anomalies?.data?.let { anomalies ->
                        if (anomalies.criticalCount > 0) {
                            AnomalyBanner(
                                criticalCount = anomalies.criticalCount,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // ── Financial Health Score ───────────────────────────────────
                item {
                    uiState.summary?.health?.let { health ->
                        FinancialHealthCard(
                            health = health,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Budget vs Actual ─────────────────────────────────────────
                item {
                    uiState.budgetVsActual?.let { bva ->
                        BudgetVsActualCard(
                            data = bva,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Monthly Trends ───────────────────────────────────────────
                item {
                    uiState.monthlyTrends?.data?.let { trends ->
                        if (trends.notEmpty) {
                            MonthlyTrendsCard(
                                trends = trends,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Hero Header ──────────────────────────────────────────────────────────────

@Composable
private fun DashboardHero(
    userName: String,
    userInitials: String,
    summary: SummaryData?,
    isLoading: Boolean,
    onAnalytics: () -> Unit
) {
    val now = Calendar.getInstance()
    val greeting = when (now.get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PesaMindTeal, PesaMindTeal.copy(alpha = 0.80f))
                )
            )
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = 10.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 28.dp)
        ) {
            // Greeting row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitials.ifEmpty { "U" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    if (userName.isNotBlank()) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                // Analytics FAB
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = onAnalytics) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = "Full Analytics",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Net movement hero figure
            if (isLoading && summary == null) {
                HeroSkeleton()
            } else if (summary != null) {
                val isPositive = summary.netMovement >= 0
                val netColor = if (isPositive) Color(0xFF90EE90) else Color(0xFFFF9999)

                Text(
                    text = "Net This Month",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.70f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                                color = Color.White
                            )
                        ) {
                            append(if (!isPositive) "−" else "+")
                            append(abs(summary.netMovement).ugx())
                        }
                        withStyle(
                            SpanStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        ) { append(" UGX") }
                    }
                )
                Spacer(Modifier.height(8.dp))
                // Trend pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = netColor.copy(alpha = 0.20f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                            contentDescription = null,
                            tint = netColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isPositive) "Surplus" else "Deficit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = netColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSkeleton() {
    val transition = rememberInfiniteTransition(label = "hero_shimmer")
    val alpha by transition.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .width(110.dp)
                .height(13.dp)
                .background(Color.White.copy(alpha = alpha * 0.4f), RoundedCornerShape(6.dp))
        )
        Box(
            Modifier
                .width(200.dp)
                .height(36.dp)
                .background(Color.White.copy(alpha = alpha * 0.35f), RoundedCornerShape(8.dp))
        )
    }
}

// ─── Quick Stats Row ──────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(data: SummaryData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(
            label = "Income",
            value = data.totalIncome.ugx(),
            color = IncomeGreen,
            icon = Icons.Outlined.TrendingUp,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "Expense",
            value = data.totalExpense.ugx(),
            color = ExpenseRed,
            icon = Icons.Outlined.TrendingDown,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label = "Savings",
            value = data.totalSavings.ugx(),
            color = PesaMindTeal,
            icon = Icons.Outlined.Savings,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickStatsRowSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "stats_shimmer")
    val alpha by transition.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(
                Modifier
                    .weight(1f)
                    .height(84.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.1f),
                        RoundedCornerShape(16.dp)
                    )
            )
        }
    }
}

// ─── Spending Velocity ────────────────────────────────────────────────────────

@Composable
private fun SpendingVelocityCard(velocity: VelocityData, modifier: Modifier = Modifier) {
    val progress = (velocity.totalSpent.toFloat() / velocity.budgetLimit.toFloat()).coerceIn(0f, 1f)
    val isAlert = velocity.alertLevel != "ok"
    val barColor = if (isAlert) ExpenseRed else PesaMindTeal
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    DashCard(modifier = modifier, title = "Spending Pace") {
        // Progress bar
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${velocity.totalSpent.ugx()} spent",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "of ${velocity.budgetLimit.ugx()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            // Custom progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(barColor.copy(alpha = 0.7f), barColor)
                            )
                        )
                )
            }
            // percentage label
            Text(
                text = "${(progress * 100).toInt()}% of budget used",
                style = MaterialTheme.typography.labelSmall,
                color = if (isAlert) ExpenseRed else TextSecondary
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        Spacer(Modifier.height(12.dp))

        // Daily + projected row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VelocityMetric(
                label = "Daily avg",
                value = velocity.dailyAverage.ugx(),
                modifier = Modifier.weight(1f)
            )
            VelocityMetric(
                label = "Month-end projection",
                value = velocity.projectedMonthEnd.ugx(),
                valueColor = if (velocity.projectedMonthEnd > velocity.budgetLimit) ExpenseRed else IncomeGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VelocityMetric(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = valueColor)) { append(value) }
                withStyle(SpanStyle(fontSize = 9.sp, color = valueColor.copy(alpha = 0.6f))) { append(" UGX") }
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ─── Budget Utilization ───────────────────────────────────────────────────────

@Composable
private fun BudgetUtilizationCard(utilization: Double, modifier: Modifier = Modifier) {
    val pct = utilization.coerceIn(0.0, 200.0)
    val color = when {
        pct <= 80 -> IncomeGreen
        pct <= 100 -> PesaMindTeal
        pct <= 130 -> Color(0xFFF39C12) // amber
        else -> ExpenseRed
    }
    val label = when {
        pct <= 80 -> "On track"
        pct <= 100 -> "Near limit"
        pct <= 130 -> "Over budget"
        else -> "Critical"
    }

    DashCard(modifier = modifier, title = "Budget Utilized") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${pct.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            // Segmented bar
            SegmentedBudgetBar(pct = pct / 100.0, color = color)
        }
    }
}

@Composable
private fun SegmentedBudgetBar(pct: Double, color: Color) {
    val segments = 10
    val filled = (pct * segments).toInt().coerceIn(0, segments)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(segments) { idx ->
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (idx < filled) color else color.copy(alpha = 0.12f)
                    )
            )
        }
    }
}

// ─── Anomaly Banner ───────────────────────────────────────────────────────────

@Composable
private fun AnomalyBanner(criticalCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ExpenseRed.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Spending Anomalies Detected",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ExpenseRed
                )
                Text(
                    "$criticalCount critical ${if (criticalCount == 1) "anomaly" else "anomalies"} found this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseRed.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = ExpenseRed.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ─── Financial Health Score ───────────────────────────────────────────────────

@Composable
private fun FinancialHealthCard(health: Health, modifier: Modifier = Modifier) {
    val scoreColor = when (health.status.lowercase()) {
        "good", "excellent" -> IncomeGreen
        "fair" -> Color(0xFFF39C12)
        else -> ExpenseRed
    }
    val trendIcon = when (health.trend.lowercase()) {
        "improving", "up" -> Icons.Outlined.TrendingUp
        "declining", "down" -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.TrendingFlat
    }

    DashCard(modifier = modifier, title = "Financial Health") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Score circle
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = (health.score / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.size(72.dp),
                    color = scoreColor,
                    trackColor = scoreColor.copy(alpha = 0.15f),
                    strokeWidth = 6.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${health.score}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )
                    )
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 20.dp)) {
                Text(
                    text = health.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(trendIcon, contentDescription = null, tint = scoreColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = health.trend.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Score bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(scoreColor.copy(alpha = 0.15f))
                ) {
                    val animScore by animateFloatAsState(
                        targetValue = health.score / 100f,
                        animationSpec = tween(1200, easing = FastOutSlowInEasing),
                        label = "health"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animScore)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(scoreColor)
                    )
                }
            }
        }
    }
}

// ─── Budget vs Actual ─────────────────────────────────────────────────────────

@Composable
private fun BudgetVsActualCard(data: BudgetVsActualResponse, modifier: Modifier = Modifier) {
    DashCard(modifier = modifier, title = "Budget vs Actual") {
        // Summary row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BvaMetric("Budget", data.data.budgetTotal.ugx(), PesaMindTeal, Modifier.weight(1f))
            BvaMetric("Actual", data.data.actualTotal.ugx(), ExpenseRed, Modifier.weight(1f))
            BvaMetric(
                label = "Variance",
                value = abs(data.data.variance).ugx(),
                color = if (data.data.variance >= 0) IncomeGreen else ExpenseRed,
                modifier = Modifier.weight(1f),
                prefix = if (data.data.variance >= 0) "+" else "−"
            )
        }
    }
}

@Composable
private fun BvaMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = ""
) {
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.10f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildAnnotatedString {
                if (prefix.isNotEmpty()) withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(prefix) }
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(value) }
                withStyle(SpanStyle(fontSize = 9.sp, color = color.copy(alpha = 0.5f))) { append(" UGX") }
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ─── Monthly Trends ───────────────────────────────────────────────────────────

@Composable
private fun MonthlyTrendsCard(trends: TrendsData, modifier: Modifier = Modifier) {
    val last6 = trends.months.takeLast(6)
    if (last6.isEmpty()) return

    val maxIncome = last6.maxOf { it.income }.toFloat().coerceAtLeast(1f)
    val maxExpense = last6.maxOf { it.expense }.toFloat().coerceAtLeast(1f)
    val maxVal = maxOf(maxIncome, maxExpense)

    DashCard(modifier = modifier, title = "Monthly Trends") {
        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot("Income", IncomeGreen)
            LegendDot("Expense", ExpenseRed)
        }
        Spacer(Modifier.height(14.dp))

        // Mini bar chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            last6.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Income bar
                        val incomeH = ((item.income / maxVal) * 70).dp
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(incomeH.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(IncomeGreen)
                        )
                        // Expense bar
                        val expenseH = ((item.expense / maxVal) * 70).dp
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(expenseH.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(ExpenseRed)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.date.take(7),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ─── Shared Card Shell ────────────────────────────────────────────────────────

@Composable
private fun DashCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}