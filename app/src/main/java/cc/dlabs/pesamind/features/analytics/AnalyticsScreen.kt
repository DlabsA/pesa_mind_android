package cc.dlabs.pesamind.features.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.network.analytics.AnomalyData
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
import java.text.NumberFormat
import java.util.Locale
import kotlin.io.path.Path

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
            viewModel.refresh()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.summary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This Month", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Income", data.totalIncome, IncomeGreen)
                MetricItem("Expense", data.totalExpense, ExpenseRed)
                MetricItem("Savings", data.totalSavings, PesaMindTeal)
                MetricItem("Net", data.netMovement, if (data.netMovement >= 0) IncomeGreen else ExpenseRed)
            }
        }
    }
}

@Composable
fun MetricItem(title: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(
            text = formatUgx(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
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
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending Velocity", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Daily Avg", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(velocity.dailyAverage.toLong()), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Projected", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(velocity.projectedMonthEnd.toLong()), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Pattern", style = MaterialTheme.typography.labelSmall)
                    Text(velocity.spendingPattern.replaceFirstChar { it.uppercase() })
                }
            }
            LinearProgressIndicator(
                progress = (velocity.totalSpent.toFloat() / velocity.budgetLimit.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = if (velocity.alertLevel == "ok") PesaMindTeal else ExpenseRed
            )
            Text("${formatUgx(velocity.totalSpent)} / ${formatUgx(velocity.budgetLimit.toLong())}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// BudgetUtilizationCard
@Composable
fun BudgetUtilizationCard(utilization: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Budget Utilization", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val percent = (utilization).coerceIn(0.0, 300.0)
            LinearProgressIndicator(
                progress = (percent / 100f).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    percent <= 100 -> IncomeGreen
                    percent <= 150 -> Color.Yellow
                    else -> ExpenseRed
                }
            )
            Text("${percent.toInt()}% of budget used", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// MonthlyTrendsCard with simple Canvas line chart
@Composable
fun MonthlyTrendsCard(trends: TrendsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Monthly Trends", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            // Only show last 6 months for clarity
            val last6Months = trends.months.takeLast(6)
            SimpleLineChart(
                data = last6Months.map { it.income.toFloat() },
                label = "Income",
                color = IncomeGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            SimpleLineChart(
                data = last6Months.map { it.expense.toFloat() },
                label = "Expense",
                color = ExpenseRed
            )
            Text("Trend: ${trends.summary.incomeTrend}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SimpleLineChart(data: List<Float>, label: String, color: Color) {
    if (data.isEmpty()) return
    val maxVal = data.maxOrNull() ?: 1f
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val step = size.width / (data.size - 1).coerceAtLeast(1)
            val points = data.mapIndexed { i, v ->
                Offset(x = i * step, y = size.height - (v / maxVal) * size.height)
            }
            if (points.size >= 2) {
                drawPath(
                    path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    } as Path,
                    color = color,
                    style = Stroke(width = 3f)
                )
            }
            points.forEach {
                drawCircle(color = color, radius = 4f, center = it)
            }
        }
    }
}

@Composable
fun ExpenseForecastCard(forecast: ForecastData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Expense Forecast", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Daily Burn Rate", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(forecast.dailyBurnRate.toLong()), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Projected Total", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(forecast.projectedTotal.toLong()), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Will exceed budget?", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (forecast.willExceedBudget) "Yes" else "No",
                        color = if (forecast.willExceedBudget) ExpenseRed else IncomeGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = (forecast.actualSpent.toFloat() / forecast.budgetLimit.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (forecast.willExceedBudget) ExpenseRed else PesaMindTeal
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cash Flow", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(cashFlow.income.total), fontWeight = FontWeight.Bold, color = IncomeGreen)
                }
                Column {
                    Text("Expenses", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(cashFlow.expenses.total), fontWeight = FontWeight.Bold, color = ExpenseRed)
                }
                Column {
                    Text("Savings", style = MaterialTheme.typography.labelSmall)
                    Text(formatUgx(cashFlow.savingsTransfers), fontWeight = FontWeight.Bold, color = PesaMindTeal)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Top Expense Categories", style = MaterialTheme.typography.labelMedium)
            cashFlow.expenses.categories.take(3).forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(category.channel, style = MaterialTheme.typography.bodySmall)
                    Text(formatUgx(category.amount), style = MaterialTheme.typography.bodySmall)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Budget vs Actual", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Budget", style = MaterialTheme.typography.bodyMedium)
                Text(formatUgx(budgetActual.budgetTotal), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Actual", style = MaterialTheme.typography.bodyMedium)
                Text(formatUgx(budgetActual.actualTotal), fontWeight = FontWeight.Bold, color = ExpenseRed)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Variance", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatUgx(budgetActual.variance),
                    color = if (budgetActual.variance < 0) ExpenseRed else IncomeGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Categories over budget: ${budgetActual.categoriesOverBudget}", style = MaterialTheme.typography.bodySmall)
            budgetActual.items.take(3).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.category, style = MaterialTheme.typography.bodySmall)
                    Text("${formatUgx(item.actual)} / ${formatUgx(item.budget)}", style = MaterialTheme.typography.bodySmall)
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
                    color = ExpenseRed
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("${anomalies.criticalCount} critical anomalies found", style = MaterialTheme.typography.bodyMedium)
            Text("Check your transaction patterns", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HealthScoreCard(health: Health) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Financial Health", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
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
                            "fair" -> Color.Yellow
                            else -> ExpenseRed
                        }
                    )
                    Text(
                        "${health.score}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Status: ${health.status.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Trend: ${health.trend}", style = MaterialTheme.typography.bodySmall)
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
                    "warning" -> Color.Yellow
                    else -> PesaMindTeal
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(recommendation.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(recommendation.message, style = MaterialTheme.typography.bodySmall)
                if (recommendation.confidence > 0) {
                    Text("Confidence: ${(recommendation.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}