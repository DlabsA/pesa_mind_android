package cc.dlabs.pesamind.features.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.network.analytics.*
import cc.dlabs.pesamind.core.network.models.AnalyticResponse
import cc.dlabs.pesamind.core.network.models.BudgetLineItem
import cc.dlabs.pesamind.core.network.models.BudgetVsActualSection
import cc.dlabs.pesamind.core.network.models.CashFlowEntry
import cc.dlabs.pesamind.core.network.models.CashFlowWaterfallSection
import cc.dlabs.pesamind.core.network.models.ExpenseForecastSection
import cc.dlabs.pesamind.core.network.models.MonthEntry
import cc.dlabs.pesamind.core.network.models.MonthlyTrendsSection
import cc.dlabs.pesamind.core.network.models.SpendingVelocitySection
import cc.dlabs.pesamind.core.network.models.SummarySection
import cc.dlabs.pesamind.core.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// ─── Formatters ───────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)
private fun Double.ugxFull()  = "UGX ${ugxFmt.format(this.toLong())}"
private fun Double.ugxShort() = when {
    this >= 1_000_000_000 -> "UGX ${String.format("%.1fB", this / 1_000_000_000)}"
    this >= 1_000_000     -> "UGX ${String.format("%.1fM", this / 1_000_000)}"
    this >= 1_000         -> "UGX ${String.format("%.0fK", this / 1_000)}"
    else                  -> "UGX ${ugxFmt.format(this.toLong())}"
}

// ─── Root Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "analytics_phase_switch"
        ) { phase ->
            when {
                phase is AnalyticsPhase.Loading && state.analytics == null ->
                    AnalyticsSkeletonView()

                phase is AnalyticsPhase.Error && state.analytics == null ->
                    AnalyticsErrorView(message = phase.message) { viewModel.load() }

                else ->
                    AnalyticsScrollBody(
                        state     = state,
                        viewModel = viewModel,
                        onRefresh = { viewModel.refresh() },
                    )
            }
        }
    }
}

// ─── Scroll Body ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsScrollBody(
    state:     AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onRefresh: () -> Unit,
) {
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardsVisible = true }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh    = onRefresh,
        modifier     = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding      = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.fillMaxSize()
        ) {

            // ── Header
            item {
                AnalyticsHeader(
                    state     = state,
                    viewModel = viewModel,
                    modifier  = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                )
            }

            // ── Offline banner
            if (state.isOffline) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter   = slideInVertically() + fadeIn(),
                        exit    = slideOutVertically() + fadeOut(),
                    ) {
                        AnalyticsOfflineBanner(
                            caption  = viewModel.formattedLastUpdated,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            state.analytics?.let { a ->
                // Overall Health Score
                item {
                    StaggeredCard(index = 0, visible = cardsVisible) {
                        HealthScoreCard(
                            analytics = a,
                            score = viewModel.overallHealthScore,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // Summary Metrics
                a.summary?.let { summary ->
                    item {
                        StaggeredCard(index = 1, visible = cardsVisible) {
                            SummaryMetricsCard(
                                data = summary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Spending Velocity
                a.spendingVelocity?.let { velocity ->
                    item {
                        StaggeredCard(index = 2, visible = cardsVisible) {
                            SpendingVelocityCard(
                                section = velocity,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Monthly Trends
                a.monthlyTrends?.let { trends ->
                    item {
                        StaggeredCard(index = 3, visible = cardsVisible) {
                            MonthlyTrendsCard(
                                section = trends,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Budget vs Actual
                a.budgetVsActual?.let { bva ->
                    item {
                        StaggeredCard(index = 4, visible = cardsVisible) {
                            BudgetVsActualCard(
                                section = bva,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Expense Forecast
                a.expenseForecast?.let { forecast ->
                    item {
                        StaggeredCard(index = 5, visible = cardsVisible) {
                            ExpenseForecastCard(
                                section = forecast,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Cash Flow Waterfall
                a.cashFlowWaterfall?.let { cashFlow ->
                    item {
                        StaggeredCard(index = 6, visible = cardsVisible) {
                            CashFlowCard(
                                section = cashFlow,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }

                // Anomalies
                val anomalyData = a.anomalies?.data
                if (anomalyData != null && (anomalyData.anomaliesDetected ?: 0) > 0) {
                    item {
                        StaggeredCard(index = 7, visible = cardsVisible) {
                            AnomaliesCard(
                                data = anomalyData, // Now safely smart-cast to non-null 'AnomalyData'
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Stagger wrapper ──────────────────────────────────────────────────────────

@Composable
private fun StaggeredCard(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    val delayMs = (index * 70).coerceAtMost(350)
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(380, delayMs, FastOutSlowInEasing),
        label         = "stagger_alpha_$index",
    )
    val offsetY by animateFloatAsState(
        targetValue   = if (visible) 0f else 28f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "stagger_offset_$index",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = offsetY }) { content() }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsHeader(
    state:    AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = viewModel.greetingText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Text(
                text          = "Analytics",
                style         = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color         = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text  = viewModel.currentPeriodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        when {
            state.isRefreshing -> CircularProgressIndicator(
                modifier    = Modifier.size(22.dp),
                color       = PesaMindTeal,
                strokeWidth = 2.dp,
            )
            state.isOffline -> Icon(
                Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint               = ExpenseRed,
                modifier           = Modifier.size(20.dp),
            )
            else -> Surface(shape = RoundedCornerShape(50), color = PesaMindTeal.copy(alpha = 0.10f)) {
                Text(
                    text     = viewModel.currentPeriodLabel,
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color    = PesaMindTeal,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// ─── Offline Banner ───────────────────────────────────────────────────────────

@Composable
private fun AnalyticsOfflineBanner(caption: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFFFF9500), Color(0xFFFF6B00))),
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.AccessTime, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    "Offline — showing cached data",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.80f))
            }
        }
    }
}

// ─── Health Score Card ────────────────────────────────────────────────────────

@Composable
private fun HealthScoreCard(
    analytics: AnalyticResponse?,
    score:     Int,
    modifier:  Modifier = Modifier,
) {
    val ringColor = when {
        score >= 80 -> IncomeGreen
        score >= 60 -> Color(0xFFFF9500)
        else        -> ExpenseRed
    }
    val statusLabel = when {
        score >= 90 -> "Excellent 🎯"
        score >= 80 -> "Good 👍"
        score >= 70 -> "Fair"
        score >= 60 -> "Needs Work ⚠️"
        else        -> "Critical ⛔"
    }

    var ringTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(score) { ringTarget = score / 100f }
    val ringProgress by animateFloatAsState(
        targetValue   = ringTarget,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
        label         = "health_ring",
    )

    AnalyticsCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Animated ring
            Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 11.dp.toPx()
                    val inset  = stroke / 2f
                    val tl     = Offset(inset, inset)
                    val sz     = Size(size.width - stroke, size.height - stroke)
                    drawArc(ringColor.copy(alpha = 0.12f), -90f, 360f,                    false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(ringColor,                     -90f, 360f * ringProgress,     false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = ringColor,
                    )
                    Text(
                        "/ 100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Financial Health",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(shape = RoundedCornerShape(50), color = ringColor.copy(alpha = 0.10f)) {
                    Text(
                        statusLabel,
                        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color    = ringColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                val trend = analytics?.summary?.health?.trend
                val trendIcon = when (trend?.lowercase()) {
                    "improving" -> Icons.Outlined.TrendingUp
                    "declining" -> Icons.Outlined.TrendingDown
                    else        -> Icons.Outlined.TrendingFlat
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(trendIcon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    Text(
                        "Trend: ${trend?.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                Text(
                    "${analytics?.summary?.data?.transactionCount} transactions · ${analytics?.summary?.data?.activeCategories} categories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ─── Summary Metrics Card ─────────────────────────────────────────────────────

@Composable
private fun SummaryMetricsCard(
    data: SummarySection,
    modifier: Modifier = Modifier,
) {
    val d = data.data
    val c = data.context

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "This Month",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricChip(
                    label        = "Income",
                    amount       = d.totalIncome,
                    delta        = d.totalIncome - c.totalIncome,
                    icon         = Icons.Default.ArrowDownward,
                    color        = IncomeGreen,
                    modifier     = Modifier.weight(1f),
                )
                MetricChip(
                    label        = "Expenses",
                    amount       = d.totalExpense,
                    delta        = d.totalExpense - c.totalExpense,
                    icon         = Icons.Default.ArrowUpward,
                    color        = ExpenseRed,
                    invertDelta  = true,
                    modifier     = Modifier.weight(1f),
                )
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricChip(
                    label    = "Savings",
                    amount   = d.totalSavings,
                    delta    = d.totalSavings - c.totalSavings,
                    icon     = Icons.Default.Savings,
                    color    = PesaMindTeal,
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label    = "Net",
                    amount   = d.netMovement,
                    delta    = d.netMovement - c.netMovement,
                    icon     = Icons.Default.ShowChart,
                    color    = if (d.netMovement >= 0) IncomeGreen else ExpenseRed,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label:       String,
    amount:      Double,
    delta:       Double,
    icon:        ImageVector,
    color:       Color,
    modifier:    Modifier = Modifier,
    invertDelta: Boolean  = false,
) {
    val deltaPositive = if (invertDelta) delta <= 0 else delta >= 0
    val deltaColor    = if (delta == 0.0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    else if (deltaPositive) IncomeGreen else ExpenseRed

    Surface(
        modifier        = modifier,
        shape           = RoundedCornerShape(12.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Text(
                text     = if (amount == 0.0) "—" else amount.ugxShort(),
                style    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (delta != 0.0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        if (deltaPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        null,
                        tint     = deltaColor,
                        modifier = Modifier.size(9.dp),
                    )
                    Text(
                        abs(delta).ugxShort(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = deltaColor,
                    )
                }
            } else {
                Text(
                    "No change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
            }
        }
    }
}

// ─── Spending Velocity Card ───────────────────────────────────────────────────
@Composable
private fun SpendingVelocityCard(
    section: SpendingVelocitySection?,
    modifier: Modifier = Modifier,
) {
    val d = section?.data ?: return  // ← Skip entirely if data is null

    val alertColor = when (d.alertLevel) {
        "ok"      -> IncomeGreen
        "warning" -> Color(0xFFFF9500)
        else      -> ExpenseRed
    }

    var dayRingTarget    by remember { mutableStateOf(0f) }
    var budgetRingTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(d) {
        dayRingTarget    = d.dayFraction
        budgetRingTarget = d.budgetUsedFraction
    }
    val dayRing    by animateFloatAsState(dayRingTarget,    spring(dampingRatio = 0.7f,  stiffness = Spring.StiffnessLow), label = "day_ring")
    val budgetRing by animateFloatAsState(budgetRingTarget, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow), label = "budget_ring")

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Title row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Spending Velocity", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text("Day ${d.daysElapsed} of ${d.daysTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                AlertLevelBadge(level = d.alertLevel)
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Nested rings: outer = day progress, inner = budget used
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outerStroke = 8.dp.toPx()
                        val innerStroke = 9.dp.toPx()
                        val outerInset  = outerStroke / 2f
                        val innerInset  = outerStroke + 8.dp.toPx() + innerStroke / 2f
                        val outerTL     = Offset(outerInset, outerInset)
                        val outerSz     = Size(size.width - outerStroke, size.height - outerStroke)
                        val innerTL     = Offset(innerInset, innerInset)
                        val innerSz     = Size(size.width - innerInset * 2, size.height - innerInset * 2)
                        drawArc(PesaMindTeal.copy(alpha = 0.10f), -90f, 360f,              false, outerTL, outerSz, style = Stroke(outerStroke, cap = StrokeCap.Round))
                        drawArc(PesaMindTeal.copy(alpha = 0.45f), -90f, 360f * dayRing,    false, outerTL, outerSz, style = Stroke(outerStroke, cap = StrokeCap.Round))
                        drawArc(alertColor.copy(alpha = 0.10f),   -90f, 360f,              false, innerTL, innerSz, style = Stroke(innerStroke, cap = StrokeCap.Round))
                        drawArc(alertColor,                        -90f, 360f * budgetRing, false, innerTL, innerSz, style = Stroke(innerStroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(d.budgetUsedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = alertColor,
                        )
                        Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VelocityRow(Icons.Default.AttachMoney,   "Spent",       d.totalSpent.ugxShort())
                    VelocityRow(Icons.Default.CalendarToday, "Days left",   "${d.daysRemaining}d")
                    VelocityRow(Icons.Default.ShowChart,     "Daily avg",   d.dailyAverage.ugxShort())
                    VelocityRow(Icons.Default.TrackChanges,  "Projection",  d.projectedMonthEnd.ugxShort())
                    d.daysUntilBudgetExhausted?.let {
                        VelocityRow(Icons.Default.HourglassBottom, "Budget lasts", "${it.toInt()}d", valueColor = alertColor)
                    }
                }
            }

            // Pattern footer
            Surface(shape = RoundedCornerShape(8.dp), color = alertColor.copy(alpha = 0.08f)) {
                Row(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (d.alertLevel == "ok") Icons.Default.CheckCircle else Icons.Default.Warning,
                        null, tint = alertColor, modifier = Modifier.size(11.dp),
                    )
                    Text(
                        d.spendingPattern.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = alertColor,
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text(
                        "Limit: ${d.budgetLimit.ugxShort()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VelocityRow(icon: ImageVector, label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = PesaMindTeal, modifier = Modifier.size(10.dp).defaultMinSize(minWidth = 14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor)
    }
}

@Composable
private fun AlertLevelBadge(level: String) {
    val color = when (level) { "ok" -> IncomeGreen; "warning" -> Color(0xFFFF9500); else -> ExpenseRed }
    val icon  = when (level) { "ok" -> Icons.Default.CheckCircle; "warning" -> Icons.Default.Warning; else -> Icons.Default.Cancel }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.10f)) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Text(level.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
    }
}

// ─── Monthly Trends Card ──────────────────────────────────────────────────────

private enum class TrendMetric(val label: String) {
    EXPENSE("Expenses"), INCOME("Income"), SAVINGS("Savings"), NET("Net")
}

@Composable
private fun MonthlyTrendsCard(
    section: MonthlyTrendsSection,
    modifier: Modifier = Modifier,
) {
    val months = section.data.months
    val s      = section.data.summary

    var metric        by remember { mutableStateOf(TrendMetric.EXPENSE) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animPct       by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) { animPct = 1f }

    fun valueFor(m: MonthEntry) = when (metric) {
        TrendMetric.EXPENSE -> m.expense
        TrendMetric.INCOME  -> m.income
        TrendMetric.SAVINGS -> m.savings
        TrendMetric.NET     -> m.net
    }

    val maxVal = months.map { abs(valueFor(it)) }.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    val animatedPct by animateFloatAsState(
        targetValue   = animPct,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "bar_anim",
    )

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Title row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Monthly Trends", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text(s.incomeTrend.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                HealthScorePill(section.health.score)
            }

            // Metric toggle chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrendMetric.entries.forEach { m ->
                    val selected = metric == m
                    Surface(
                        shape   = RoundedCornerShape(50),
                        color   = if (selected) PesaMindTeal else MaterialTheme.colorScheme.background,
                        modifier = Modifier.clickable {
                            metric        = m
                            selectedIndex = null
                            animPct       = 0f
                            animPct       = 1f
                        }
                    ) {
                        Text(
                            m.label,
                            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                            color    = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Selected tooltip
            selectedIndex?.let { idx ->
                if (idx < months.size) {
                    val m = months[idx]
                    val v = valueFor(m)
                    Row(
                        modifier              = Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("${m.shortLabel} ${m.date.take(4)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        Text(v.ugxShort(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (v >= 0) IncomeGreen else ExpenseRed)
                        Text("· ${m.transactionCount} txns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
                }
            }

            // Bar chart
            Row(
                modifier              = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment     = Alignment.Bottom,
            ) {
                months.forEachIndexed { idx, month ->
                    val v        = valueFor(month)
                    val fraction = (abs(v) / maxVal).toFloat()
                    val h        = (90.dp.value * fraction * animatedPct).coerceAtLeast(3f)
                    val isSelected = selectedIndex == idx
                    val baseColor = when (metric) {
                        TrendMetric.EXPENSE -> ExpenseRed
                        TrendMetric.INCOME  -> IncomeGreen
                        TrendMetric.SAVINGS -> PesaMindTeal
                        TrendMetric.NET     -> if (v >= 0) IncomeGreen else ExpenseRed
                    }
                    val barColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.38f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                selectedIndex = if (selectedIndex == idx) null else idx
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(h.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            // Month labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                months.forEachIndexed { idx, m ->
                    Text(
                        m.shortLabel,
                        style    = MaterialTheme.typography.labelSmall.copy(
                            fontSize   = 7.sp,
                            fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color    = if (selectedIndex == idx) PesaMindTeal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Summary averages
            if (s.avgIncome + s.avgExpense > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TrendStatPill("Avg Income",  s.avgIncome.ugxShort(),  IncomeGreen)
                    TrendStatPill("Avg Expense", s.avgExpense.ugxShort(), ExpenseRed)
                    TrendStatPill("Avg Savings", s.avgSavings.ugxShort(), PesaMindTeal)
                }
            }
        }
    }
}

@Composable
private fun TrendStatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
    }
}

@Composable
private fun TrendDirectionRow(label: String, trend: String) {
    val isUp   = trend.lowercase() in listOf("increasing", "up")
    val isDown = trend.lowercase() in listOf("decreasing", "down")
    val icon   = if (isUp) Icons.Default.NorthEast else if (isDown) Icons.Default.SouthEast else Icons.Default.Remove
    val color  = if (isUp) IncomeGreen else if (isDown) ExpenseRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Text(trend.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
    }
}

// ─── Budget vs Actual Card ────────────────────────────────────────────────────

@Composable
private fun BudgetVsActualCard(
    section: BudgetVsActualSection,
    modifier: Modifier = Modifier,
) {
    val d = section.data
    val statusColor = when (d.status) {
        "on_budget"   -> IncomeGreen
        "over_budget" -> ExpenseRed
        else          -> Color(0xFFFF9500)
    }

    var barTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(d) { barTarget = d.usageFraction }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "budget_bar")

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Budget vs Actual", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text(d.status.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetCountChip("On Track", d.categoriesOnTrack,    IncomeGreen)
                    BudgetCountChip("Over",     d.categoriesOverBudget, ExpenseRed)
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (d.actualTotal == 0.0) "Nothing spent yet" else "${d.actualTotal.ugxShort()} spent",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (d.budgetTotal > 0) "of ${d.budgetTotal.ugxShort()}" else "No budget set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(statusColor.copy(alpha = 0.10f))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(statusColor, statusColor.copy(alpha = 0.70f))))
                    )
                }
                if (d.budgetTotal > 0) {
                    Text("${(d.usageFraction * 100).toInt()}% utilised", style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }

            // Category rows (top 3)
            val items = d.items
            if (!items.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(3).forEach { item -> BudgetLineItemRow(item) }
                    if (items.size > 3) {
                        Text(
                            "+ ${items.size - 3} more categories",
                            style     = MaterialTheme.typography.labelSmall,
                            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier  = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(13.dp))
                    Text("No budget categories configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun BudgetCountChip(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label,    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun BudgetLineItemRow(item: BudgetLineItem) {
    val color = if (item.status == "over_budget") ExpenseRed else IncomeGreen
    var barTarget by remember(item.category) { mutableStateOf(0f) }
    LaunchedEffect(item.category) { barTarget = item.usageFraction }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "item_${item.category}")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.category, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (item.budget > 0) "${item.actual.ugxShort()} / ${item.budget.ugxShort()}" else item.actual.ugxShort(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                color = color,
            )
            item.transactions?.let {
                Text("$it txn${if (it == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }
    }
}

// ─── Expense Forecast Card ────────────────────────────────────────────────────

@Composable
private fun ExpenseForecastCard(
    section: ExpenseForecastSection,
    modifier: Modifier = Modifier,
) {
    val d           = section.data
    val exceedColor = if (d.willExceedBudget) ExpenseRed else IncomeGreen

    var ringTarget   by remember { mutableStateOf(0f) }
    var actualTarget by remember { mutableStateOf(0f) }
    var projTarget   by remember { mutableStateOf(0f) }

    LaunchedEffect(d) {
        ringTarget   = d.confidence.toFloat()
        val maxVal   = maxOf(d.projectedTotal, d.budgetLimit, 1.0)
        actualTarget = (d.actualSpent / maxVal).toFloat().coerceIn(0f, 1f)
        projTarget   = (d.projectedTotal / maxVal).toFloat().coerceIn(0f, 1f)
    }

    val ringProg   by animateFloatAsState(ringTarget,   spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow), label = "forecast_ring")
    val actualProg by animateFloatAsState(actualTarget, spring(dampingRatio = 0.7f,  stiffness = Spring.StiffnessLow), label = "actual_bar")
    val projProg   by animateFloatAsState(projTarget,   spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow), label = "proj_bar")

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Expense Forecast", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Text(if (d.willExceedBudget) "Over budget projected" else "On track", style = MaterialTheme.typography.labelSmall, color = exceedColor)
                }
                HealthScorePill(section.data.confidencePct)
            }

            // Projection hero + confidence ring
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Confidence ring
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val inset  = stroke / 2f
                        val tl     = Offset(inset, inset)
                        val sz     = Size(size.width - stroke, size.height - stroke)
                        drawArc(exceedColor.copy(alpha = 0.12f), -90f, 360f,             false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(exceedColor,                     -90f, 360f * ringProg,  false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.confidencePct}%", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = exceedColor)
                        Text("conf.", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Month-end projection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(
                        d.projectedTotal.ugxFull(),
                        style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (d.willExceedBudget) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            null, tint = exceedColor, modifier = Modifier.size(12.dp),
                        )
                        Text(
                            if (d.willExceedBudget) "Will exceed budget" else "Within budget",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = exceedColor,
                        )
                    }
                }
            }

            // Burn stats row
            if (d.dailyBurnRate > 0) {
                Row(
                    modifier    = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ForecastStat("Daily burn",  d.dailyBurnRate.ugxShort())
                    VerticalDivider(modifier = Modifier.height(28.dp))
                    ForecastStat("Days in",     "${d.daysElapsed}")
                    VerticalDivider(modifier = Modifier.height(28.dp))
                    ForecastStat("Budget",      d.budgetLimit.ugxShort())
                }
            }

            // Comparison bars
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ForecastBar("Actual",    d.actualSpent,    PesaMindTeal,     actualProg)
                ForecastBar("Projected", d.projectedTotal, exceedColor,      projProg)
                ForecastBar("Budget",    d.budgetLimit,    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    (d.budgetLimit / maxOf(d.projectedTotal, d.budgetLimit, 1.0)).toFloat(), isStatic = true)
            }

            // First recommendation
            section.recommendations.firstOrNull()?.let { rec ->
                Row(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF9500).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Top,
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFF9500), modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(rec.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun ForecastStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
    }
}

@Composable
private fun ForecastBar(label: String, value: Double, color: Color, progress: Float, isStatic: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(value.ugxShort(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.10f))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isStatic) color else color)
            )
        }
    }
}

// ─── Cash Flow Waterfall Card ─────────────────────────────────────────────────

@Composable
private fun CashFlowCard(
    section: CashFlowWaterfallSection,
    modifier: Modifier = Modifier,
) {
    val d   = section.data
    val net = d.closingBalance - d.openingBalance

    val allValues = listOf(d.openingBalance, d.income.total, d.expenses.total, d.savingsTransfers, d.closingBalance)
    val maxVal    = allValues.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animated = true }

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cash Flow", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text("Waterfall", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }

            // Waterfall bars
            Row(
                modifier              = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.Bottom,
            ) {
                val bars = listOf(
                    Triple("Open",     d.openingBalance,   PesaMindTeal),
                    Triple("+Income",  d.income.total,     IncomeGreen),
                    Triple("-Expense", d.expenses.total,   ExpenseRed),
                    Triple("-Savings", d.savingsTransfers, Color(0xFF5856D6)),
                    Triple("Close",    d.closingBalance,   PesaMindTeal),
                )
                bars.forEach { (label, value, color) ->
                    val fraction = (value / maxVal).toFloat()
                    val animFrac by animateFloatAsState(
                        targetValue   = if (animated) fraction else 0f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label         = "waterfall_$label",
                    )
                    Column(
                        modifier            = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            value.ugxShort(),
                            style    = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.SemiBold),
                            color    = color,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((90 * animFrac).dp.coerceAtLeast(if (value > 0) 4.dp else 0.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(color.copy(alpha = 0.75f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            // Net indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (net >= 0) Icons.Default.NorthEast else Icons.Default.SouthEast,
                    null,
                    tint     = if (net >= 0) IncomeGreen else ExpenseRed,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (net == 0.0) "No movement this period"
                    else "Net ${if (net >= 0) "+" else ""}${net.ugxShort()} this period",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (net >= 0) IncomeGreen else ExpenseRed,
                )
            }

            // Income sources + expense categories summary
            val incomeSources = d.income.sources
            val expenseCats   = d.expenses.categories
            if (!incomeSources.isNullOrEmpty() || !expenseCats.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    incomeSources?.let { sources ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("INCOME", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            sources.forEach { src ->
                                CashFlowEntryChip(src, d.income.total, IncomeGreen)
                            }
                        }
                    }
                    expenseCats?.let { cats ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("EXPENSES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            cats.forEach { cat ->
                                CashFlowEntryChip(cat, d.expenses.total, ExpenseRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashFlowEntryChip(entry: CashFlowEntry, total: Double, color: Color) {
    val fraction = if (total > 0) (entry.amount / total).toFloat().coerceIn(0f, 1f) else 0f
    var barTarget by remember(entry.channel) { mutableStateOf(0f) }
    LaunchedEffect(entry.channel) { barTarget = fraction }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "cash_${entry.channel}")

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.channel.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(entry.amount.ugxShort(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
        entry.percent?.let {
            Text("${it.toInt()}% of total", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        }
    }
}

// ─── Anomalies Card ───────────────────────────────────────────────────────────

@Composable
private fun AnomaliesCard(
    data:     AnomalyData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ExpenseRed.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
            .border(1.dp, ExpenseRed.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
                Text("Anomalies Detected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (data.criticalCount > 0) AnomalyBadge("${data.criticalCount} Critical", ExpenseRed)
                if (data.warningCount  > 0) AnomalyBadge("${data.warningCount} Warning",   Color(0xFFFF9500))
            }
            data.items?.forEach { item ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .offset(y = 4.dp)
                            .background(if (item.severity == "critical") ExpenseRed else Color(0xFFFF9500), CircleShape)
                    )
//                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
//                        Text(item.type.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
//                        Text(item.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
//                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalyBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp), color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun HealthScorePill(score: Int) {
    val color = when {
        score >= 80 -> IncomeGreen
        score >= 60 -> Color(0xFFFF9500)
        else        -> ExpenseRed
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.10f)) {
        Text(
            "$score",
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color    = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsSkeletonView() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        0.3f, 0.7f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmer_alpha",
    )

    @Composable
    fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = modifier
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.10f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.05f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.10f),
                        )
                    )
                )
        )
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(height = 36.dp, modifier = Modifier.width(160.dp))
        SkeletonBlock(height = 90.dp)
        SkeletonBlock(height = 140.dp)
        SkeletonBlock(height = 200.dp)
        SkeletonBlock(height = 180.dp)
        SkeletonBlock(height = 160.dp)
    }
}

// ─── Error State ──────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), modifier = Modifier.size(52.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Couldn't load analytics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
            }
            Surface(shape = RoundedCornerShape(50), color = PesaMindTeal.copy(alpha = 0.10f)) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = PesaMindTeal, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try Again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = PesaMindTeal)
                }
            }
        }
    }
}

// ─── Shared card shell ────────────────────────────────────────────────────────

@Composable
private fun AnalyticsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier        = modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(16.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        tonalElevation  = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}