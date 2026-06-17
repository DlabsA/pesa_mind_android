package cc.dlabs.pesamind.features.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import cc.dlabs.pesamind.core.network.models.MonthSummary
import cc.dlabs.pesamind.core.network.models.SpendingVelocitySection
import cc.dlabs.pesamind.core.network.models.SummarySection
import cc.dlabs.pesamind.core.theme.*
import cc.dlabs.pesamind.features.dashboard.FinancialHealthCard
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
private fun Double.amountShort() = when {
    this >= 1_000_000_000 -> String.format("%.1fB", this / 1_000_000_000)
    this >= 1_000_000     -> String.format("%.1fM", this / 1_000_000)
    this >= 1_000         -> String.format("%.0fK", this / 1_000)
    else                  -> "${ugxFmt.format(this.toLong())}"
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
                color       = MaterialTheme.colorScheme.secondary,
                strokeWidth = 2.dp,
            )
            state.isOffline -> Icon(
                Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint               = MaterialTheme.colorScheme.error,
                modifier           = Modifier.size(20.dp),
            )
            else -> Surface(shape = RoundedCornerShape(70), color = MaterialTheme.colorScheme.surface) {
                Text(
                    text     = viewModel.currentPeriodLabel,
                    style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(caption, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

// ─── Health Score Card ────────────────────────────────────────────────────────



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
                    color        = LightColors.Income,
                    modifier     = Modifier.weight(1f),
                )
                MetricChip(
                    label        = "Expenses",
                    amount       = d.totalExpense,
                    delta        = d.totalExpense - c.totalExpense,
                    icon         = Icons.Default.ArrowUpward,
                    color        = LightColors.Expense,
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
                    color    = LightColors.Savings,
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label    = "Net",
                    amount   = d.netMovement,
                    delta    = d.netMovement - c.netMovement,
                    icon     = Icons.Default.ShowChart,
                    color    = if (d.netMovement >= 0) LightColors.Income else LightColors.Expense,
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
    val deltaColor    = if (delta == 0.0) MaterialTheme.colorScheme.onSurface
    else if (deltaPositive) LightColors.Income else LightColors.Expense

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
                    color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
        "ok"      -> LightColors.Income
        "warning" -> Color(0xFFFF9500)
        else      -> LightColors.Expense
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
                    Text("Day ${d.daysElapsed} of ${d.daysTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                AlertLevelBadge(level = d.alertLevel, alertColor = alertColor)
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
                        drawArc(LightColors.Savings.copy(alpha = 0.10f), -90f, 360f,              false, outerTL, outerSz, style = Stroke(outerStroke, cap = StrokeCap.Round))
                        drawArc(LightColors.Savings, -90f, 360f * dayRing,    false, outerTL, outerSz, style = Stroke(outerStroke, cap = StrokeCap.Round))
                        drawArc(alertColor.copy(alpha = 0.10f),   -90f, 360f,              false, innerTL, innerSz, style = Stroke(innerStroke, cap = StrokeCap.Round))
                        drawArc(alertColor,-90f, 360f * budgetRing, false, innerTL, innerSz, style = Stroke(innerStroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(d.budgetUsedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = alertColor,
                        )
                        Text("used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
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
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
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
                    Text("·", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Limit: ${d.budgetLimit.ugxShort()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun VelocityRow(icon: ImageVector, label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = LightColors.Savings, modifier = Modifier.size(10.dp).defaultMinSize(minWidth = 14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor)
    }
}

@Composable
private fun AlertLevelBadge(level: String, alertColor: Color) {
    val color = when (level) { "ok" -> LightColors.Income.copy(alpha = 0.10f); "warning" -> Color(0xFFFF9500).copy(alpha = 0.10f); else -> LightColors.Expense.copy(alpha = 0.10f)}
    val icon  = when (level) { "ok" -> Icons.Default.CheckCircle; "warning" -> Icons.Default.Warning; else -> Icons.Default.Cancel }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = alertColor, modifier = Modifier.size(10.dp))
            Text(level.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ─── Monthly Trends Card ──────────────────────────────────────────────────────

private enum class TrendMetric(val label: String) {
    EXPENSE("Expense"), INCOME("Income"), SAVINGS("Savings"), NET("Net")
}

//private object TrendColors {
//    val Income  = Color(0xFF1D9E75)
//    val Expense = Color(0xFFD85A30)
//    val Savings = Color(0xFF378ADD)
//
//
//    val IncomeBg  = Color(0xFFE1F5EE)
//    val IncomeText = Color(0xFF085041)
//
//    val ChipActive     = Color(0xFF378ADD)
//    val ChipActiveText = Color.White
//}

private fun TrendMetric.barColor(value: Double): Color = when (this) {
    TrendMetric.EXPENSE -> LightColors.Expense
    TrendMetric.INCOME  -> LightColors.Income
    TrendMetric.SAVINGS -> LightColors.Savings
    TrendMetric.NET     -> if (value >= 0) NetPos else NetNeg
}

private fun MonthEntry.valueFor(metric: TrendMetric): Double = when (metric) {
    TrendMetric.EXPENSE -> expense
    TrendMetric.INCOME  -> income
    TrendMetric.SAVINGS -> savings
    TrendMetric.NET     -> net
}

@Composable
private fun MonthlyTrendsCard(
    section: MonthlyTrendsSection,
    modifier: Modifier = Modifier,
) {
    val months  = section.data.months
    val summary = section.data.summary

    var metric        by remember { mutableStateOf(TrendMetric.EXPENSE) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Animate bars to full height on first composition and on metric change.
    var animTrigger by remember { mutableStateOf(0) }
    var animPct by remember { mutableStateOf(0f) }
    LaunchedEffect(animTrigger) { 
        animPct = 1f 
    }
    val springPct by animateFloatAsState(
        targetValue   = animPct,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "bar_spring",
    )

    val maxVal = remember(months, metric) {
        months.maxOfOrNull { abs(it.valueFor(metric)) }?.takeIf { it > 0 } ?: 1.0
    }

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Header ────────────────────────────────────────────────────
            TrendsHeaderComposable(
                trend       = summary.incomeTrend,
                healthScore = section.health.score,
            )

            // ── Metric chips ──────────────────────────────────────────────
            MetricChipRowComposable(
                active   = metric,
                onSelect = { m ->
                    metric        = m
                    selectedIndex = null
                    animTrigger++
                },
            )

            // ── Tooltip / always visible ──────────────────────────────────
            BarTooltipComposable(
                selectedIndex = selectedIndex,
                months        = months,
                metric        = metric,
            )

            // ── Bar chart with values ─────────────────────────────────────
            BarChartComposable(
                months        = months,
                metric        = metric,
                maxVal        = maxVal,
                animatedPct   = springPct,
                selectedIndex = selectedIndex,
                onBarClick    = { idx ->
                    selectedIndex = if (selectedIndex == idx) null else idx
                },
            )

            // ── Month labels ──────────────────────────────────────────────
            MonthLabelRowComposable(months = months, selectedIndex = selectedIndex)

            // ── Summary stats ─────────────────────────────────────────────
            if (summary.avgIncome + summary.avgExpense > 0) {
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    thickness = 0.5.dp,
                )
                SummaryStatsRowComposable(summary = summary)
            }
        }
    }
}

@Composable
private fun TrendsHeaderComposable(trend: String, healthScore: Int) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text  = "Monthly Trends",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = trend.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
        }
        HealthScorePillComposable(score = healthScore)
    }
}

@Composable
private fun HealthScorePillComposable(score: Int) {
    Surface(
        shape = CircleShape,
        color = LightColors.IncomeBg,
    ) {
        Text(
            text     = "Score $score",
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color    = IncomeText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetricChipRowComposable(
    active: TrendMetric,
    onSelect: (TrendMetric) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrendMetric.entries.forEach { m ->
            MetricChipComposable(
                label      = m.label,
                isActive   = m == active,
                onClick    = { onSelect(m) },
            )
        }
    }
}

@Composable
private fun MetricChipComposable(label: String, isActive: Boolean, onClick: () -> Unit) {
    val bgColor   by animateColorAsState(
        if (isActive) ChipActive else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip_bg",
    )
    val textColor by animateColorAsState(
        if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_text",
    )
    Surface(
        shape   = CircleShape,
        color   = bgColor,
        border  = if (!isActive) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)) else null,
        modifier = Modifier.clickable(
            indication            = null,
            interactionSource     = remember { MutableInteractionSource() },
            onClick               = onClick,
        ),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color    = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun BarTooltipComposable(
    selectedIndex: Int?,
    months: List<MonthEntry>,
    metric: TrendMetric,
) {
    val tooltipBg = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tooltipBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selectedIndex != null && selectedIndex < months.size) {
            val m = months[selectedIndex]
            val v = m.valueFor(metric)
            val valueColor = metric.barColor(v)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Colored dot indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(valueColor, CircleShape)
                )
                Text(
                    text  = "${m.shortLabel} ${m.date.take(4)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = v.ugxShort(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = valueColor,
                )
                Text(
                    text  = "· ${m.transactionCount} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                )
            }
        } else {
            // Show total of all months
            val totalValue = months.sumOf { it.valueFor(metric) }
            val totalColor = metric.barColor(totalValue)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(totalColor, CircleShape)
                )
                Text(
                    text  = "Total",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = totalValue.ugxShort(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = totalColor,
                )
                Text(
                    text  = "· ${months.sumOf { it.transactionCount }} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                )
            }
        }
    }
}

@Composable
private fun BarChartComposable(
    months: List<MonthEntry>,
    metric: TrendMetric,
    maxVal: Double,
    animatedPct: Float,
    selectedIndex: Int?,
    onBarClick: (Int) -> Unit,
) {
    val hasSelection = selectedIndex != null
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        months.forEachIndexed { idx, month ->
            val value    = month.valueFor(metric)
            val fraction = (abs(value) / maxVal).toFloat()
            val barHeightDp = (90f * fraction * animatedPct).coerceAtLeast(3f).dp
            val barColor = metric.barColor(value)
            val isSelected = selectedIndex == idx
            // Dim unselected bars when something is selected
            val barAlpha by animateFloatAsState(
                targetValue   = when {
                    !hasSelection -> 1f
                    isSelected    -> 1f
                    else          -> 0.25f
                },
                animationSpec = tween(180),
                label         = "bar_alpha_$idx",
            )
            // Scale selected bar slightly up for emphasis
            val barScale by animateFloatAsState(
                targetValue   = if (isSelected) 1.04f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label         = "bar_scale_$idx",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick           = { onBarClick(idx) },
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Value text above bar
                    Text(
                        text  = value.amountShort(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize   = 7.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color     = barColor,
                        maxLines  = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeightDp)
                            .graphicsLayer {
                                alpha  = barAlpha
                                scaleX = barScale
                                scaleY = barScale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            }
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                            // Selected: white inner border for "lifted" feel
                            .then(
                                if (isSelected)
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = Color.White.copy(alpha = .55f),
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    )
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthLabelRowComposable(months: List<MonthEntry>, selectedIndex: Int?) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        months.forEachIndexed { idx, m ->
            val isSelected = selectedIndex == idx
            Text(
                text  = m.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize   = 8.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color     = if (isSelected) ChipActive
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryStatsRowComposable(summary: MonthSummary) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPillComposable(label = "Avg income",  value = summary.avgIncome.ugxShort(),  color = LightColors.Income,  modifier = Modifier.weight(1f))
        StatPillComposable(label = "Avg expense", value = summary.avgExpense.ugxShort(), color = LightColors.Expense, modifier = Modifier.weight(1f))
        StatPillComposable(label = "Avg savings", value = summary.avgSavings.ugxShort(), color = LightColors.Savings, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatPillComposable(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier              = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
            Text(
                text  = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
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
        "on_budget"   -> LightColors.Income
        "over_budget" -> LightColors.Expense
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
                    BudgetCountChip("On Track", d.categoriesOnTrack,    LightColors.Income)
                    BudgetCountChip("Over",     d.categoriesOverBudget, LightColors.Expense)
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(statusColor)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(statusColor, statusColor)))
                    )
                }
                if (d.budgetTotal > 0) {
                    Text("${(d.usageFraction * 100).toInt()}% utilised", style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }

            // Category rows (top 3)
            val items = d.items
            if (!items.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(3).forEach { item -> BudgetLineItemRow(item) }
                    if (items.size > 3) {
                        Text(
                            "+ ${items.size - 3} more categories",
                            style     = MaterialTheme.typography.labelSmall,
                            color     = MaterialTheme.colorScheme.onSurface,
                            modifier  = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                    Text("No budget categories configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun BudgetCountChip(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label,    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BudgetLineItemRow(item: BudgetLineItem) {
    val color = if (item.status == "over_budget") LightColors.Expense else LightColors.Income
    var barTarget by remember(item.category) { mutableStateOf(0f) }
    LaunchedEffect(item.category) { barTarget = item.usageFraction }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "item_${item.category}")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.category, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (item.budget > 0) "${item.actual.ugxShort()} / ${item.budget.ugxShort()}" else item.actual.ugxShort(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(color)) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                color = color,
            )
            item.transactions?.let {
                Text("$it txn${if (it == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface)
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
    val exceedColor = if (d.willExceedBudget) LightColors.Expense else LightColors.Income

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
                        drawArc(exceedColor, -90f, 360f,             false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(exceedColor,                     -90f, 360f * ringProg,  false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${d.confidencePct}%", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = exceedColor)
                        Text("conf.", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Month-end projection", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ForecastBar("Actual",    d.actualSpent,    LightColors.Savings,     actualProg)
                ForecastBar("Projected", d.projectedTotal, exceedColor,      projProg)
                ForecastBar("Budget",    d.budgetLimit,    MaterialTheme.colorScheme.onSurface,
                    (d.budgetLimit / maxOf(d.projectedTotal, d.budgetLimit, 1.0)).toFloat(), isStatic = true)
            }

            // First recommendation
            section.recommendations.firstOrNull()?.let { rec ->
                Row(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF9500), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Top,
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFF9500), modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(rec.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ForecastStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ForecastBar(label: String, value: Double, color: Color, progress: Float, isStatic: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(value.ugxShort(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color)) {
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
                Text("Waterfall", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }

            // Waterfall bars
            Row(
                modifier              = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.Bottom,
            ) {
                val bars = listOf(
                    Triple("Open",     d.openingBalance,   LightColors.Savings),
                    Triple("+Income",  d.income.total,     LightColors.Income),
                    Triple("-Expense", d.expenses.total,   LightColors.Expense),
                    Triple("-Savings", d.savingsTransfers, Color(0xFF5856D6)),
                    Triple("Close",    d.closingBalance,   LightColors.Savings),
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
                                .background(color)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Net indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (net >= 0) Icons.Default.NorthEast else Icons.Default.SouthEast,
                    null,
                    tint     = if (net >= 0) LightColors.Income else LightColors.Expense,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (net == 0.0) "No movement this period"
                    else "Net ${if (net >= 0) "+" else ""}${net.ugxShort()} this period",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (net >= 0) LightColors.Income else LightColors.Expense,
                )
            }

            // Income sources + expense categories summary
            val incomeSources = d.income.sources
            val expenseCats   = d.expenses.categories
            if (!incomeSources.isNullOrEmpty() || !expenseCats.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    incomeSources?.let { sources ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("INCOME", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurface)
                            sources.forEach { src ->
                                CashFlowEntryChip(src, d.income.total, LightColors.Income)
                            }
                        }
                    }
                    expenseCats?.let { cats ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("EXPENSES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurface)
                            cats.forEach { cat ->
                                CashFlowEntryChip(cat, d.expenses.total, LightColors.Expense)
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
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color)) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
        entry.percent?.let {
            Text("${it.toInt()}% of total", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurface)
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
            .background(LightColors.Expense, RoundedCornerShape(16.dp))
            .border(1.dp, LightColors.Expense, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = LightColors.Expense, modifier = Modifier.size(14.dp))
                Text("Anomalies Detected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (data.criticalCount > 0) AnomalyBadge("${data.criticalCount} Critical", LightColors.Expense)
                if (data.warningCount  > 0) AnomalyBadge("${data.warningCount} Warning",   Color(0xFFFF9500))
            }
            data.items?.forEach { item ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .offset(y = 4.dp)
                            .background(if (item.severity == "critical") LightColors.Expense else Color(0xFFFF9500), CircleShape)
                    )
//                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
//                        Text(item.type.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
//                        Text(item.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
//                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalyBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp), color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun HealthScorePill(score: Int) {
    val color = when {
        score >= 80 -> LightColors.Income
        score >= 60 -> Color(0xFFFF9500)
        else        -> LightColors.Expense
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
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
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurface,
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
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(52.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Couldn't load analytics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            }
            Surface(shape = RoundedCornerShape(50), color = LightColors.Savings) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = LightColors.Savings, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try Again", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = LightColors.Savings)
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