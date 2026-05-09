package cc.dlabs.pesamind.features.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.network.analytics.AnomalyData
import cc.dlabs.pesamind.core.network.analytics.AnomalyItem
import cc.dlabs.pesamind.core.network.analytics.BudgetActualData
import cc.dlabs.pesamind.core.network.analytics.ForecastData
import cc.dlabs.pesamind.core.network.analytics.Health
import cc.dlabs.pesamind.core.network.analytics.Recommendation
import cc.dlabs.pesamind.core.network.analytics.SummaryData
import cc.dlabs.pesamind.core.network.analytics.TrendsData
import cc.dlabs.pesamind.core.network.analytics.VelocityData
import cc.dlabs.pesamind.core.network.analytics.WaterfallData
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.IncomeGreen
import cc.dlabs.pesamind.core.theme.PesaMindGreen
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary
import cc.dlabs.pesamind.features.tools.DashboardHeader
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private val TealPrimary = Color(0xFF1A9E8F)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadAllData()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val headerState = HeaderCompatibleState(
        userDisplayName = uiState.userDisplayName,
        isFromCache = false
    )
    val initial = headerState.userDisplayName.firstOrNull()?.uppercase() ?: "U"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnalyticsHeader(
                userDisplayName = headerState.userDisplayName,  // Pass an object that matches the expected shape
                onBack = { navController.popBackStack() },
                initial = initial
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.summary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Key metrics row (income, expense, savings, net)
                uiState.summary?.data?.let { data ->
                    item {
                        KeyMetricsCard(data)
                    }
                }

                // Spending Velocity Card
                uiState.spendingVelocity?.data?.let { velocity ->
                    item {
                        SpendingVelocityCard(velocity)
                    }
                }

                // Budget Utilization progress
                uiState.budgetUtilization?.let { utilization ->
                    item {
                        BudgetUtilizationCard(utilization.budgetUtilization)
                    }
                }

                // Expense Forecast Card
                uiState.expenseForecast?.data?.let { forecast ->
                    item {
                        ExpenseForecastCard(forecast)
                    }
                }

                // Cash Flow Waterfall (simplified list)
                uiState.cashFlow?.data?.let { cashFlow ->
                    item {
                        CashFlowCard(cashFlow)
                    }
                }

                // Budget vs Actual (top category variances)
                uiState.budgetVsActual?.data?.let { budgetActual ->
                    item {
                        BudgetVsActualCard(budgetActual)
                    }
                }

                // Anomalies alert
                uiState.anomalies?.data?.let { anomalies ->
                    if (anomalies.criticalCount > 0) {
                        item {
                            AnomaliesAlertCard(anomalies)
                        }
                    }
                }

                // Monthly Trends (line chart)
                uiState.monthlyTrends?.data?.let { trends ->
                    item {
                        MonthlyTrendsCard(trends)
                    }
                }

                // Health score
                uiState.summary?.health?.let { health ->
                    item {
                        HealthScoreCard(health)
                    }
                }

                // Recommendations
                uiState.summary?.recommendations?.let { recs ->
                    items(recs) { rec ->
                        RecommendationCard(rec)
                    }
                }
            }
        }
    }
}

// Example: KeyMetricsCard
@Composable
fun KeyMetricsCard(data: SummaryData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("This Month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricItem("Income", data.totalIncome, IncomeGreen, Modifier.weight(1f))
                MetricItem("Expense", data.totalExpense, ExpenseRed, Modifier.weight(1f))
                MetricItem("Savings", data.totalSavings, PesaMindTeal, Modifier.weight(1f))
                MetricItem("Net", data.netMovement, if (data.netMovement >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetricItem(title: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUgx(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            fontSize = 13.sp
        )
    }
}

private fun formatUgx(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(amount)
}

// SpendingVelocityCard
@Composable
fun SpendingVelocityCard(velocity: VelocityData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Spending Velocity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily Avg", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(velocity.dailyAverage.toLong()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Projected", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(velocity.projectedMonthEnd.toLong()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Pattern", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(velocity.spendingPattern.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (velocity.totalSpent.toFloat() / velocity.budgetLimit.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (velocity.alertLevel == "ok") PesaMindTeal else ExpenseRed,
                trackColor = Color.LightGray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${formatUgx(velocity.totalSpent)} / ${formatUgx(velocity.budgetLimit.toLong())}", 
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// BudgetUtilizationCard
@Composable
fun BudgetUtilizationCard(utilization: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Budget Utilization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            val percent = (utilization).coerceIn(0.0, 300.0)
            LinearProgressIndicator(
                progress = ((percent / 100.0).coerceIn(0.0, 1.0)).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    percent <= 100 -> IncomeGreen
                    percent <= 150 -> Color(0xFFFFC107)
                    else -> ExpenseRed
                },
                trackColor = Color.LightGray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("${percent.toInt()}% of budget used", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

// MonthlyTrendsCard with simple Canvas line chart
@Composable
fun MonthlyTrendsCard(trends: TrendsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Monthly Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            // Only show last 6 months for clarity
            val last6Months = trends.months.takeLast(6)
            SimpleLineChart(
                data = last6Months.map { it.income.toFloat() },
                label = "Income",
                color = IncomeGreen
            )
            Spacer(modifier = Modifier.height(12.dp))
            SimpleLineChart(
                data = last6Months.map { it.expense.toFloat() },
                label = "Expense",
                color = ExpenseRed
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Income Trend: ${trends.summary.incomeTrend}", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
            Text("Expense Trend: ${trends.summary.expenseTrend}", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
        }
    }
}

@Composable
fun SimpleLineChart(data: List<Float>, label: String, color: Color) {
    if (data.isEmpty()) return
    val maxVal = data.maxOrNull() ?: 1f
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val step = size.width / (data.size - 1).coerceAtLeast(1)
            val points = data.mapIndexed { i, v ->
                Offset(x = i * step, y = size.height - (v / maxVal) * size.height)
            }
            if (points.size >= 2) {
                drawPath(
                    path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    },
                    color = color,
                    style = Stroke(width = 2.5f)
                )
            }
            points.forEach {
                drawCircle(color = color, radius = 3f, center = it)
            }
        }
    }
}

@Composable
fun ExpenseForecastCard(forecast: ForecastData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Expense Forecast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily Burn", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(forecast.dailyBurnRate.toLong()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Projected", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(forecast.projectedTotal.toLong()), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Exceed?", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(
                        if (forecast.willExceedBudget) "Yes" else "No",
                        color = if (forecast.willExceedBudget) ExpenseRed else IncomeGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = (forecast.actualSpent.toFloat() / forecast.budgetLimit.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (forecast.willExceedBudget) ExpenseRed else PesaMindTeal,
                trackColor = Color.LightGray.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Confidence: ${(forecast.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun CashFlowCard(cashFlow: WaterfallData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Cash Flow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Income", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(cashFlow.income.total), fontWeight = FontWeight.Bold, color = IncomeGreen, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Expenses", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(cashFlow.expenses.total), fontWeight = FontWeight.Bold, color = ExpenseRed, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Savings", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 11.sp)
                    Text(formatUgx(cashFlow.savingsTransfers), fontWeight = FontWeight.Bold, color = PesaMindTeal, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Top Expense Categories", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            cashFlow.expenses.categories.take(3).forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(category.channel, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                    Text(formatUgx(category.amount), style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BudgetVsActualCard(budgetActual: BudgetActualData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Budget vs Actual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Budget", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                Text(formatUgx(budgetActual.budgetTotal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Actual", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                Text(formatUgx(budgetActual.actualTotal), fontWeight = FontWeight.Bold, color = ExpenseRed, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Variance", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                Text(
                    formatUgx(budgetActual.variance),
                    color = if (budgetActual.variance < 0) ExpenseRed else IncomeGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Categories over budget: ${budgetActual.categoriesOverBudget}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            budgetActual.items.take(3).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.category, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("${formatUgx(item.actual)} / ${formatUgx(item.budget)}", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
fun AnomaliesAlertCard(anomalies: AnomalyData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ExpenseRed.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = ExpenseRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Unusual Spending Detected",
                    style = MaterialTheme.typography.titleMedium,
                    color = ExpenseRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${anomalies.criticalCount} critical anomalies found",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp
            )
            Text(
                "Check your transaction patterns",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = TextSecondary
            )

            if (anomalies.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Anomaly Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable list of anomalies
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    anomalies.items.forEach { item ->
                        AnomalyItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalyItemRow(item: AnomalyItem) {
    val severityColor = when (item.severity) {
        "high" -> ExpenseRed
        "medium" -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = severityColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = item.severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Amount: ${formatUgx(item.amount)}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
            Text(
                text = "Normal range: ${formatUgx(item.normalMin)} - ${formatUgx(item.normalMax)}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary
            )
            if (item.type == "spike") {
                Text(
                    text = "Spike detected (${item.sigmaMultiple.toInt()}x above norm)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = ExpenseRed
                )
            }
        }
    }
}
@Composable
fun HealthScoreCard(health: Health) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Financial Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = health.score / 100f,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = when (health.status) {
                            "good" -> PesaMindGreen
                            "fair" -> Color(0xFFFFC107)
                            else -> ExpenseRed
                        }
                    )
                    Text(
                        "${health.score}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Status: ${health.status.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
                    Text("Trend: ${health.trend}", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(recommendation: Recommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (recommendation.severity) {
                "warning" -> Color.Yellow.copy(alpha = 0.1f)
                "info" -> PesaMindTeal.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                when (recommendation.type) {
                    "alert" -> Icons.Default.Warning
                    "achievement" -> Icons.Default.Star
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = when (recommendation.severity) {
                    "warning" -> Color(0xFFFFC107)
                    else -> PesaMindTeal
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(recommendation.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(recommendation.message, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                if (recommendation.confidence > 0) {
                    Text("Confidence: ${(recommendation.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

private data class HeaderCompatibleState(
    val userDisplayName: String,
    val isFromCache: Boolean
)

@Composable
private fun AnalyticsHeader(
    userDisplayName: String,
    onBack: () -> Unit,
    initial: String = "U"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PesaMindTeal, PesaMindTeal.copy(alpha = 0.75f))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = PesaMindTeal.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Column {
                val now = Calendar.getInstance()
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val greeting = when {
                    hour < 12 -> "Good morning"
                    hour < 17 -> "Good afternoon"
                    else      -> "Good evening"
                }
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                if (userDisplayName.isNotBlank()) {
                    Text(
                        text = userDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}