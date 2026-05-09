package cc.dlabs.pesamind.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.network.analytics.Health
import cc.dlabs.pesamind.core.network.analytics.SummaryData
import cc.dlabs.pesamind.core.network.analytics.VelocityData
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.IncomeGreen
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

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
        topBar = {
            DashboardTopBar(
                userInitials = uiState.userInitials,
                userName = uiState.userDisplayName,
                onRefresh = { viewModel.refresh() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("analytics") },
                containerColor = PesaMindTeal
            ) {
                Icon(Icons.Default.Analytics, contentDescription = "Full Analytics")
            }
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
                // Key metrics summary
                uiState.summary?.data?.let { data ->
                    item { DashboardKeyMetricsCard(data) }
                }

                // Spending velocity (compact)
                uiState.spendingVelocity?.data?.let { velocity ->
                    item { DashboardSpendingCard(velocity) }
                }

                // Budget utilization
                uiState.budgetUtilization?.let { utilization ->
                    item { DashboardBudgetUtilizationCard(utilization.budgetUtilization) }
                }

                // Critical anomalies warning (if any)
                uiState.anomalies?.data?.let { anomalies ->
                    if (anomalies.criticalCount > 0) {
                        item { DashboardAnomalyWarning(anomalies.criticalCount) }
                    }
                }

                // Health score compact
                uiState.summary?.health?.let { health ->
                    item { DashboardHealthScore(health) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    userInitials: String,
    userName: String,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = PesaMindTeal.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitials.ifEmpty { "U" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PesaMindTeal
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (userName.isNotBlank()) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    )
}

@Composable
fun DashboardKeyMetricsCard(data: SummaryData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This Month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCardItem("Income", data.totalIncome, IncomeGreen, Modifier.weight(1f))
                MetricCardItem("Expense", data.totalExpense, ExpenseRed, Modifier.weight(1f))
                MetricCardItem("Savings", data.totalSavings, PesaMindTeal, Modifier.weight(1f))
                MetricCardItem("Net", data.netMovement, if (data.netMovement >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetricCardItem(title: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(
            text = formatUgx(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun DashboardSpendingCard(velocity: VelocityData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending Pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Daily avg:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(formatUgx(velocity.dailyAverage.toLong()), fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Projected end of month:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(formatUgx(velocity.projectedMonthEnd.toLong()), fontWeight = FontWeight.Bold)
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

@Composable
fun DashboardBudgetUtilizationCard(utilization: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Budget Utilized", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${utilization.toInt()}%", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = (utilization / 100f).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    utilization <= 100 -> IncomeGreen
                    utilization <= 150 -> Color.Yellow
                    else -> ExpenseRed
                }
            )
        }
    }
}

@Composable
fun DashboardAnomalyWarning(criticalCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = ExpenseRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "$criticalCount critical spending anomalies detected",
                style = MaterialTheme.typography.bodyMedium,
                color = ExpenseRed
            )
        }
    }
}

@Composable
fun DashboardHealthScore(health: Health) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Financial Health", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (health.status) {
                    "good" -> IncomeGreen.copy(alpha = 0.2f)
                    "fair" -> Color.Yellow.copy(alpha = 0.2f)
                    else -> ExpenseRed.copy(alpha = 0.2f)
                }
            ) {
                Text(
                    text = "${health.score} · ${health.status} (${health.trend})",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatUgx(amount: Long): String = NumberFormat.getNumberInstance(Locale.US).format(amount)