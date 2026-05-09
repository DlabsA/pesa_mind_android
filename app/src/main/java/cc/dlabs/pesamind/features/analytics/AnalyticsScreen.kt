package cc.dlabs.pesamind.features.analytics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ─── Formatter ────────────────────────────────────────────────────────────────

private val numFmt = NumberFormat.getNumberInstance(Locale.US)
private fun Long.ugx() = numFmt.format(this)
private fun Double.ugx() = numFmt.format(this.toLong())

private val MONTH_ABBRS = listOf(
    "Jan","Feb","Mar","Apr","May","Jun",
    "Jul","Aug","Sep","Oct","Nov","Dec"
)
private fun String.toMonthAbbr(): String {
    val monthIndex = this.substringAfter("-").toIntOrNull() ?: return this
    return MONTH_ABBRS.getOrElse(monthIndex - 1) { this }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ViewModel.init already calls loadAllData() — no LaunchedEffect needed here.
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()      // clear after showing
        }
    }

    val initial = uiState.userDisplayName.firstOrNull()?.uppercase() ?: "U"

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
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // ── Header ───────────────────────────────────────────────────
                item(key = "header") {
                    AnalyticsHeader(
                        displayName = uiState.userDisplayName,
                        initial = initial,
                        onBack = { navController.popBackStack() }
                    )
                }

                // ── Section: Overview ────────────────────────────────────────
                item(key = "section_overview") { SectionHeader("Overview") }

                item(key = "key_metrics") {
                    if (uiState.isLoading && uiState.summary == null) {
                        ShimmerCard(height = 110, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    } else {
                        uiState.summary?.data?.let { KeyMetricsCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    }
                }

                item(key = "health") {
                    uiState.summary?.health?.let {
                        HealthScoreCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                // ── Section: Spending ────────────────────────────────────────
                item(key = "section_spending") { SectionHeader("Spending") }

                item(key = "velocity") {
                    if (uiState.isLoading && uiState.spendingVelocity == null) {
                        ShimmerCard(height = 130, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    } else {
                        uiState.spendingVelocity?.data?.let {
                            SpendingVelocityCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                }

                item(key = "utilization") {
                    uiState.budgetUtilization?.let {
                        BudgetUtilizationCard(it.budgetUtilization, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                item(key = "forecast") {
                    uiState.expenseForecast?.data?.let {
                        ExpenseForecastCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                // ── Section: Cash Flow ───────────────────────────────────────
                item(key = "section_cashflow") { SectionHeader("Cash Flow") }

                item(key = "cashflow") {
                    uiState.cashFlow?.data?.let {
                        CashFlowCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                item(key = "bva") {
                    uiState.budgetVsActual?.data?.let {
                        BudgetVsActualCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }

                // ── Section: Trends ──────────────────────────────────────────
                item(key = "section_trends") { SectionHeader("Monthly Trends") }

                item(key = "trends") {
                    if (uiState.isLoading && uiState.monthlyTrends == null) {
                        ShimmerCard(height = 200, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    } else {
                        uiState.monthlyTrends?.data?.let {
                            MonthlyTrendsCard(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                }

                // ── Section: Alerts ──────────────────────────────────────────
                uiState.anomalies?.data?.let { anomalies ->
                    if (anomalies.criticalCount > 0) {
                        item(key = "section_alerts") { SectionHeader("Alerts") }

                        item(key = "anomaly_header") {
                            AnomalyHeaderCard(anomalies, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }

                        // Anomaly items directly as LazyColumn items — avoids nested scroll
                        itemsIndexed(
                            items = anomalies.items,
                            key = { _, item -> "anomaly_${item.category}_${item.amount}" }
                        ) { _, item ->
                            AnomalyItemCard(item, Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                        }
                    }
                }

                // ── Section: Recommendations ─────────────────────────────────
                uiState.summary?.recommendations?.takeIf { it.isNotEmpty() }?.let { recs ->
                    item(key = "section_recs") { SectionHeader("Recommendations") }
                    item(key = "recommendations") {
                        // Horizontal carousel — no nested scroll issue since axis differs
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recs, key = { "rec_${it.title}" }) { rec ->
                                RecommendationCard(rec)
                            }
                        }
                    }
                }

                item(key = "bottom_space") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsHeader(displayName: String, initial: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(PesaMindTeal, PesaMindTeal.copy(alpha = 0.78f)))
            )
    ) {
        // Decorative circles
        Box(
            Modifier
                .size(140.dp)
                .offset((-30).dp, (-30).dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            Modifier
                .size(80.dp)
                .align(Alignment.TopEnd)
                .offset(20.dp, 12.dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(initial, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    Text(
                        text = when { hour < 12 -> "Good morning" ; hour < 17 -> "Good afternoon" ; else -> "Good evening" },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
                // Back button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sub-label
            Text(
                "Full Analytics",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )
            Text(
                "Detailed breakdown of your financial activity",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = TextSecondary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

// ─── Key Metrics ──────────────────────────────────────────────────────────────

@Composable
private fun KeyMetricsCard(data: SummaryData, modifier: Modifier = Modifier) {
    AnalyticsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            MetricCell("Income", data.totalIncome, IncomeGreen, Icons.Outlined.TrendingUp, Modifier.weight(1f))
            MetricDivider()
            MetricCell("Expense", data.totalExpense, ExpenseRed, Icons.Outlined.TrendingDown, Modifier.weight(1f))
            MetricDivider()
            MetricCell("Savings", data.totalSavings, PesaMindTeal, Icons.Outlined.Savings, Modifier.weight(1f))
            MetricDivider()
            MetricCell(
                label = "Net",
                value = data.netMovement,
                color = if (data.netMovement >= 0) IncomeGreen else ExpenseRed,
                icon = if (data.netMovement >= 0) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: Long, color: Color, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.size(26.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
        Text(
            text = value.ugx(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(56.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    )
}

// ─── Health Score ─────────────────────────────────────────────────────────────

@Composable
private fun HealthScoreCard(health: Health, modifier: Modifier = Modifier) {
    val scoreColor = when (health.status.lowercase()) {
        "good", "excellent" -> IncomeGreen
        "fair" -> Color(0xFFF39C12)
        else -> ExpenseRed
    }
    val animatedScore by animateFloatAsState(
        targetValue = health.score / 100f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "health_score"
    )

    AnalyticsCard(modifier = modifier, title = "Financial Health") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Score arc
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    // Track
                    drawArc(
                        color = scoreColor.copy(alpha = 0.15f),
                        startAngle = 135f, sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    // Fill
                    drawArc(
                        color = scoreColor,
                        startAngle = 135f, sweepAngle = 270f * animatedScore,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${health.score}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        ),
                        fontSize = 22.sp
                    )
                    Text("/100", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    health.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = scoreColor
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val trendIcon = when (health.trend.lowercase()) {
                        "improving" -> Icons.Outlined.TrendingUp
                        "declining" -> Icons.Outlined.TrendingDown
                        else -> Icons.Outlined.TrendingFlat
                    }
                    Icon(trendIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        "Trend: ${health.trend.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Animated score bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(scoreColor.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedScore)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(scoreColor)
                    )
                }
            }
        }
    }
}

// ─── Spending Velocity ────────────────────────────────────────────────────────

@Composable
private fun SpendingVelocityCard(velocity: VelocityData, modifier: Modifier = Modifier) {
    val isAlert = velocity.alertLevel != "ok"
    val barColor = if (isAlert) ExpenseRed else PesaMindTeal
    val progress = remember(velocity.totalSpent, velocity.budgetLimit) {
        (velocity.totalSpent.toFloat() / velocity.budgetLimit.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "velocity_progress"
    )

    AnalyticsCard(modifier = modifier, title = "Spending Velocity") {
        // Stat row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            VelocityStat("Daily Avg", velocity.dailyAverage.ugx())
            VelocityStat("Projected", velocity.projectedMonthEnd.ugx(),
                if (velocity.projectedMonthEnd > velocity.budgetLimit) ExpenseRed else PesaMindTeal,
                Modifier.weight(1f)
            )
            VelocityStat("Pattern", velocity.spendingPattern.replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        Spacer(Modifier.height(14.dp))

        // Custom progress track
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${velocity.totalSpent.ugx()} UGX",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = barColor
            )
            Text(
                text = "of ${velocity.budgetLimit.ugx()} UGX",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(barColor.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.7f), barColor))
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${(progress * 100).toInt()}% of budget consumed",
            style = MaterialTheme.typography.labelSmall,
            color = if (isAlert) ExpenseRed else TextSecondary
        )
    }
}

@Composable
private fun VelocityStat(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = valueColor, fontSize = 12.sp)) { append(value) }
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        pct <= 130 -> Color(0xFFF39C12)
        else -> ExpenseRed
    }
    val label = when {
        pct <= 80 -> "On track  🎯"
        pct <= 100 -> "Near limit"
        pct <= 130 -> "Over budget"
        else -> "Critical"
    }

    AnalyticsCard(modifier = modifier, title = "Budget Utilization") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "${pct.toInt()}%",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                )
                Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            // Segmented bar
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val segments = 10
                val filled = (pct / 10).toInt().coerceIn(0, segments)
                repeat(segments) { idx ->
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (idx < filled) color else color.copy(alpha = 0.12f))
                    )
                }
            }
        }
    }
}

// ─── Expense Forecast ─────────────────────────────────────────────────────────

@Composable
private fun ExpenseForecastCard(forecast: ForecastData, modifier: Modifier = Modifier) {
    val willExceed = forecast.willExceedBudget
    val accentColor = if (willExceed) ExpenseRed else IncomeGreen
    val progress = remember(forecast.actualSpent, forecast.budgetLimit) {
        (forecast.actualSpent.toFloat() / forecast.budgetLimit.toFloat()).coerceIn(0f, 1f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "forecast_progress"
    )

    AnalyticsCard(modifier = modifier, title = "Expense Forecast") {
        // Will exceed banner
        if (willExceed) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ExpenseRed.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(16.dp))
                    Text(
                        "Projected to exceed budget",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = ExpenseRed
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            ForecastStat("Daily Burn", forecast.dailyBurnRate.ugx(), modifier = Modifier.weight(1f))
            ForecastStat("Projected", forecast.projectedTotal.ugx(), accentColor, Modifier.weight(1f))
            ForecastStat("Confidence", "${(forecast.confidence * 100).toInt()}%", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.7f), accentColor)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${forecast.actualSpent.ugx()} spent of ${forecast.budgetLimit.ugx()} UGX budget",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ForecastStat(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = valueColor), textAlign = TextAlign.Center)
    }
}

// ─── Cash Flow ────────────────────────────────────────────────────────────────

@Composable
private fun CashFlowCard(cashFlow: WaterfallData, modifier: Modifier = Modifier) {
    AnalyticsCard(modifier = modifier, title = "Cash Flow") {
        // Summary row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CashFlowMetric("Income", cashFlow.income.total, IncomeGreen, Modifier.weight(1f))
            CashFlowMetric("Expenses", cashFlow.expenses.total, ExpenseRed, Modifier.weight(1f))
            CashFlowMetric("Savings", cashFlow.savingsTransfers, PesaMindTeal, Modifier.weight(1f))
        }

        val topCategories = remember(cashFlow) { cashFlow.expenses.categories.take(3) }
        if (topCategories.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(Modifier.height(10.dp))
            Text(
                "Top Expense Categories",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            val maxAmount = remember(topCategories) { topCategories.maxOf { it.amount }.toFloat().coerceAtLeast(1f) }
            topCategories.forEach { cat ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat.channel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(
                            cat.amount.ugx() + " UGX",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = ExpenseRed
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    val barPct = cat.amount.toFloat() / maxAmount
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ExpenseRed.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barPct)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(ExpenseRed.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CashFlowMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                value.ugx(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
        }
    }
}

// ─── Budget vs Actual ─────────────────────────────────────────────────────────

@Composable
private fun BudgetVsActualCard(budgetActual: BudgetActualData, modifier: Modifier = Modifier) {
    AnalyticsCard(modifier = modifier, title = "Budget vs Actual") {
        // Summary metrics
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BvaMetric("Budget", budgetActual.budgetTotal, PesaMindTeal, Modifier.weight(1f))
            BvaMetric("Actual", budgetActual.actualTotal, ExpenseRed, Modifier.weight(1f))
            BvaMetric(
                label = "Variance",
                value = abs(budgetActual.variance),
                color = if (budgetActual.variance < 0) ExpenseRed else IncomeGreen,
                prefix = if (budgetActual.variance < 0) "−" else "+",
                modifier = Modifier.weight(1f)
            )
        }

        if (budgetActual.categoriesOverBudget > 0) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = ExpenseRed.copy(alpha = 0.07f)) {
                Text(
                    "${budgetActual.categoriesOverBudget} ${if (budgetActual.categoriesOverBudget == 1) "category" else "categories"} over budget",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = ExpenseRed,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        val topItems = remember(budgetActual) { budgetActual.items.take(3) }
        if (topItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(Modifier.height(10.dp))
            Text("Category Breakdown", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            topItems.forEach { item ->
                val itemProgress = remember(item) { (item.actual.toFloat() / item.budget.toFloat()).coerceIn(0f, 1.5f) }
                val isOver = item.actual > item.budget
                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.category, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            "${item.actual.ugx()} / ${item.budget.ugx()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOver) ExpenseRed else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(itemProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isOver) ExpenseRed else PesaMindTeal)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BvaMetric(label: String, value: Long, color: Color, modifier: Modifier = Modifier, prefix: String = "") {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.10f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildAnnotatedString {
                if (prefix.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp)) { append(prefix) }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp)) { append(value.ugx()) }
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Monthly Trends Chart ─────────────────────────────────────────────────────

@Composable
private fun MonthlyTrendsCard(trends: TrendsData, modifier: Modifier = Modifier) {
    // Derive the 6-item list once, memoized
    val last6 = remember(trends) { trends.months.takeLast(6) }
    if (last6.isEmpty()) return

    AnalyticsCard(modifier = modifier, title = "Monthly Trends") {
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("Income", IncomeGreen)
            LegendDot("Expense", ExpenseRed)
        }
        Spacer(Modifier.height(4.dp))

        // Trend summary pills
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendPill("Income", trends.summary.incomeTrend)
            TrendPill("Expense", trends.summary.expenseTrend)
        }

        Spacer(Modifier.height(14.dp))

        // Dual-series line chart
        val incomePoints = remember(last6) { last6.map { it.income.toFloat() } }
        val expensePoints = remember(last6) { last6.map { it.expense.toFloat() } }
        val monthLabels = remember(last6) { last6.map { it.date.toMonthAbbr() } }
        val maxVal = remember(incomePoints, expensePoints) {
            maxOf(incomePoints.maxOrNull() ?: 1f, expensePoints.maxOrNull() ?: 1f).coerceAtLeast(1f)
        }

        GradientLineChart(
            series = listOf(
                LineSeries(incomePoints, IncomeGreen, "Income"),
                LineSeries(expensePoints, ExpenseRed, "Expense")
            ),
            labels = monthLabels,
            maxValue = maxVal,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
    }
}

private data class LineSeries(val points: List<Float>, val color: Color, val label: String)

@Composable
private fun GradientLineChart(
    series: List<LineSeries>,
    labels: List<String>,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    val chartHeight = 110.dp
    val labelHeight = 18.dp

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            if (series.isEmpty() || series.first().points.size < 2) return@Canvas

            val pointCount = series.first().points.size
            val stepX = size.width / (pointCount - 1).coerceAtLeast(1)
            val chartH = size.height

            series.forEach { line ->
                val pts = line.points.mapIndexed { i, v ->
                    Offset(x = i * stepX, y = chartH - (v / maxValue) * chartH * 0.85f - chartH * 0.05f)
                }

                // Gradient fill
                val fillPath = Path().apply {
                    moveTo(pts.first().x, chartH)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, chartH)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(line.color.copy(alpha = 0.25f), line.color.copy(alpha = 0f)),
                        startY = 0f, endY = chartH
                    )
                )

                // Line
                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    // Smooth curve via cubic bezier
                    for (i in 1 until pts.size) {
                        val cp1x = pts[i - 1].x + stepX / 3f
                        val cp1y = pts[i - 1].y
                        val cp2x = pts[i].x - stepX / 3f
                        val cp2y = pts[i].y
                        cubicTo(cp1x, cp1y, cp2x, cp2y, pts[i].x, pts[i].y)
                    }
                }
                drawPath(linePath, color = line.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                // Dots at endpoints
                pts.forEach { pt ->
                    drawCircle(color = line.color, radius = 3.5.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = pt)
                }
            }

            // Horizontal grid lines
            val gridSteps = 3
            repeat(gridSteps) { i ->
                val y = chartH / gridSteps * i
                drawLine(
                    color = Color.Gray.copy(alpha = 0.10f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Month labels
        Row(
            modifier = Modifier.fillMaxWidth().height(labelHeight),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun TrendPill(label: String, trend: String) {
    val isUp = trend.lowercase().contains("up") || trend.lowercase().contains("increas")
    val color = when {
        label == "Income" && isUp -> IncomeGreen
        label == "Expense" && !isUp -> IncomeGreen
        else -> ExpenseRed
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
            Text("·", color = color.copy(alpha = 0.5f), fontSize = 10.sp)
            Text(trend, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
        }
    }
}

// ─── Anomaly Cards ────────────────────────────────────────────────────────────

@Composable
private fun AnomalyHeaderCard(anomalies: AnomalyData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.07f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = ExpenseRed.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unusual Spending Detected",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ExpenseRed
                )
                Text(
                    "${anomalies.criticalCount} critical ${if (anomalies.criticalCount == 1) "anomaly" else "anomalies"} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseRed.copy(alpha = 0.75f)
                )
            }
        }
    }
}

// Placed as individual LazyColumn items — no nested scroll
@Composable
private fun AnomalyItemCard(item: AnomalyItem, modifier: Modifier = Modifier) {
    val severityColor = when (item.severity.lowercase()) {
        "high" -> ExpenseRed
        "medium" -> Color(0xFFF39C12)
        else -> TextSecondary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Severity indicator bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(severityColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(shape = RoundedCornerShape(4.dp), color = severityColor.copy(alpha = 0.15f)) {
                        Text(
                            item.severity.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = severityColor,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Amount: ${item.amount.ugx()} UGX",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Normal: ${item.normalMin.ugx()} – ${item.normalMax.ugx()} UGX",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (item.type == "spike") {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "⚡ ${item.sigmaMultiple.toInt()}× above normal",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = severityColor,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─── Recommendations Carousel ─────────────────────────────────────────────────

@Composable
private fun RecommendationCard(rec: Recommendation) {
    val bgColor = when (rec.severity.lowercase()) {
        "warning" -> Color(0xFFFFF8E1)
        "info" -> PesaMindTeal.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val accentColor = when (rec.severity.lowercase()) {
        "warning" -> Color(0xFFF39C12)
        else -> PesaMindTeal
    }
    val icon = when (rec.type.lowercase()) {
        "alert" -> Icons.Default.Warning
        "achievement" -> Icons.Default.Star
        else -> Icons.Default.Info
    }

    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    rec.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                rec.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
            if (rec.confidence > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(rec.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ─── Shared Card Shell ────────────────────────────────────────────────────────

@Composable
private fun AnalyticsCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

// ─── Shimmer Skeleton ─────────────────────────────────────────────────────────

@Composable
private fun ShimmerCard(height: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        0.25f, 0.65f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {}
}