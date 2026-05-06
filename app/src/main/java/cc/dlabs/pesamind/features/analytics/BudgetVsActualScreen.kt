package cc.dlabs.pesamind.features.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.dlabs.pesamind.core.network.models.BudgetVsActualItem
import cc.dlabs.pesamind.core.network.models.BudgetVsActualResponse
import java.text.NumberFormat
import java.util.*

private val TealPrimary = Color(0xFF1A9E8F)
private val SuccessGreen = Color(0xFF00C896)
private val WarningYellow = Color(0xFFFFB020)
private val ErrorRed = Color(0xFFFF4D6A)
private val SurfaceGray = Color(0xFFF2F4F7)
private val TextDark = Color(0xFF1F2937)
private val TextLight = Color(0xFF6B7280)

@Composable
fun BudgetVsActualScreen(
    viewModel: BudgetVsActualViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Budget vs Actual",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = TealPrimary
            )
        } else if (state.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f))
            ) {
                Text(
                    text = state.error!!,
                    color = ErrorRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (state.data != null) {
            BudgetVsActualContent(data = state.data!!)
        }
    }
}

@Composable
fun BudgetVsActualContent(data: BudgetVsActualResponse) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Card
        item {
            BudgetVsActualSummaryCard(data)
        }

        // Overall Status Banner
        item {
            OverallStatusBanner(data)
        }

        // Detailed Items
        items(data.budgetVsActual) { item ->
            BudgetVsActualItemCard(item)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BudgetVsActualSummaryCard(data: BudgetVsActualResponse) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Summary",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatItem(
                    label = "Budgeted",
                    value = formatCurrency(data.totalBudgeted),
                    color = TealPrimary
                )
                SummaryStatItem(
                    label = "Actual",
                    value = formatCurrency(data.totalActual),
                    color = TextDark
                )
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = SurfaceGray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Variance",
                        fontSize = 14.sp,
                        color = TextLight
                    )
                    Text(
                        text = "${if (data.totalVariance >= 0) "+" else ""}${formatCurrency(data.totalVariance)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            data.totalVariance < 0 -> SuccessGreen
                            data.totalVariance > data.totalBudgeted * 0.1 -> ErrorRed
                            else -> WarningYellow
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%.1f", data.variancePercent)}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            data.variancePercent < -10 -> SuccessGreen
                            data.variancePercent > 10 -> ErrorRed
                            else -> WarningYellow
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextLight,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun OverallStatusBanner(data: BudgetVsActualResponse) {
    val statusColor = when (data.overallStatus) {
        "over" -> ErrorRed
        "under" -> SuccessGreen
        else -> WarningYellow
    }

    val statusMessage = when (data.overallStatus) {
        "over" -> "You're over budget this month"
        "under" -> "Great! You're under budget"
        else -> "You're on track with your budget"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
    ) {
        Text(
            text = statusMessage,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            modifier = Modifier.padding(12.dp)
        )
    }
}



@Composable
fun BudgetVsActualItemCard(item: BudgetVsActualItem) {
    val statusColor = when (item.status) {
        "over" -> ErrorRed
        "under" -> SuccessGreen
        else -> WarningYellow
    }

    val progressValue: Any = if (item.budgetedAmount > 0) {
        (item.actualAmount / item.budgetedAmount).coerceIn(0.0, 1.5)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(targetValue = progressValue as Float, label = "progress")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    modifier = Modifier.weight(1f)
                )

                // Variance badge
                Badge(
                    containerColor = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.variance < 0) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.1f", item.variancePercent)}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = SurfaceGray,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Footer - Amounts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Budgeted",
                        fontSize = 11.sp,
                        color = TextLight
                    )
                    Text(
                        text = formatCurrency(item.budgetedAmount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Actual",
                        fontSize = 11.sp,
                        color = TextLight
                    )
                    Text(
                        text = formatCurrency(item.actualAmount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Variance",
                        fontSize = 11.sp,
                        color = TextLight
                    )
                    Text(
                        text = "${if (item.variance >= 0) "+" else ""}${formatCurrency(item.variance)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "UG"))
    formatter.currency = java.util.Currency.getInstance("UGX")
    val formatted = formatter.format(amount).replace("UGX", "").trim()
    return if (amount >= 1_000_000) {
        "${String.format("%.1f", amount / 1_000_000)}M"
    } else if (amount >= 1_000) {
        "${String.format("%.1f", amount / 1_000)}K"
    } else {
        formatted
    }
}