package cc.dlabs.pesamind.features.analytics

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.data.ChannelSpend
import cc.dlabs.pesamind.core.data.DayOfWeekSpend
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.analytics.*
import cc.dlabs.pesamind.core.network.models.AnalyticsRecommendation
import cc.dlabs.pesamind.core.network.models.AnomalyData
import cc.dlabs.pesamind.core.network.models.AnomalyItem
import cc.dlabs.pesamind.core.network.models.AnomalyRecommendation
import cc.dlabs.pesamind.core.network.models.AnomalySection
import cc.dlabs.pesamind.core.network.models.BudgetLineItem
import cc.dlabs.pesamind.core.network.models.BudgetVsActualSection
import cc.dlabs.pesamind.core.network.models.BvaHealth
import cc.dlabs.pesamind.core.network.models.BvaHealthComponents
import cc.dlabs.pesamind.core.network.models.CashFlowEntry
import cc.dlabs.pesamind.core.network.models.CashFlowWaterfallSection
import cc.dlabs.pesamind.core.network.models.ExpenseForecastSection
import cc.dlabs.pesamind.core.network.models.ForecastData
import cc.dlabs.pesamind.core.network.models.ForecastRecommendation
import cc.dlabs.pesamind.core.network.models.MonthEntry
import cc.dlabs.pesamind.core.network.models.MonthSummary
import cc.dlabs.pesamind.core.network.models.MonthlyTrendsSection
import cc.dlabs.pesamind.core.network.models.SpendingVelocitySection
import cc.dlabs.pesamind.core.network.models.SummarySection
import cc.dlabs.pesamind.core.theme.*
import cc.dlabs.pesamind.core.ui.DashboardStyleHeader
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.ErrorState
import cc.dlabs.pesamind.core.ui.FinancialHealthCard
import cc.dlabs.pesamind.core.ui.OfflineBanner
import cc.dlabs.pesamind.core.ui.PremiumUpsellCard
import cc.dlabs.pesamind.core.ui.SectionHeader
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ─── Formatters ───────────────────────────────────────────────────────────────

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)

private fun Double.ugxFull() = "UGX ${ugxFmt.format(this.toLong())}"

private fun Double.ugxShort() =
    when {
        this >= 1_000_000_000 -> "UGX ${String.format("%.1fB", this / 1_000_000_000)}"
        this >= 1_000_000 -> "UGX ${String.format("%.1fM", this / 1_000_000)}"
        this >= 1_000 -> "UGX ${String.format("%.0fK", this / 1_000)}"
        else -> "UGX ${ugxFmt.format(this.toLong())}"
    }

private fun Double.amountShort() =
    when {
        this >= 1_000_000_000 -> String.format("%.1fB", this / 1_000_000_000)
        this >= 1_000_000 -> String.format("%.1fM", this / 1_000_000)
        this >= 1_000 -> String.format("%.0fK", this / 1_000)
        else -> "${ugxFmt.format(this.toLong())}"
    }

// ─── Root Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "analytics_phase_switch",
        ) { phase ->
            when {
                (phase is AnalyticsPhase.Loading && state.analytics == null) || state.isRefreshing ->
                    SkeletonColumn(
                        blockHeights = listOf(36.dp, 90.dp, 140.dp, 200.dp, 180.dp, 160.dp),
                        modifier = Modifier.fillMaxSize(),
                    )

                phase is AnalyticsPhase.Error && state.analytics == null -> {
                    val isDark = isSystemInDarkTheme()
                    ErrorState(
                        message = phase.message,
                        onRetry = { viewModel.load() },
                        title = if (state.isOffline) "You're offline" else "Couldn't load analytics",
                        icon = if (state.isOffline) Icons.Default.CloudOff else Icons.Default.ErrorOutline,
                        // Being offline isn't really an "error" — use the same warm Warning
                        // tone as OfflineBanner instead of the alarming default error-red,
                        // reserving red for genuine server failures.
                        iconTint =
                            if (state.isOffline) {
                                if (isDark) DarkColors.Warning else LightColors.Warning
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        iconBackground =
                            if (state.isOffline) {
                                if (isDark) DarkColors.WarningBg else LightColors.WarningBg
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                phase is AnalyticsPhase.Empty ->
                    EmptyState(
                        icon = Icons.Default.QueryStats,
                        title = "No analytics yet",
                        subtitle = "Add a transaction and your spending insights will show up here.",
                        modifier = Modifier.fillMaxSize().padding(Spacing.Space6.dp),
                    )

                else ->
                    AnalyticsScrollBody(
                        state = state,
                        viewModel = viewModel,
                        onRefresh = { viewModel.refresh() },
                        navController = navController,
                    )
            }
        }
    }
}

// ─── Scroll Body ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsScrollBody(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onRefresh: () -> Unit,
    navController: NavController,
) {
    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardsVisible = true }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        indicator = {},
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Header
            item {
                AnalyticsHeader(
                    state = state,
                    viewModel = viewModel,
                    modifier =
                        Modifier
                            .padding(horizontal = Spacing.Space4.dp)
                            .padding(top = 8.dp),
                )
            }

            // ── Offline banner
            if (state.isOffline) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut(),
                    ) {
                        OfflineBanner(
                            caption = viewModel.formattedLastUpdated,
                            modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                        )
                    }
                }
            }

            state.analytics?.let { a ->
                // ── Section: Transaction-based insights (work from transactions alone) ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Space4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PeriodToggle(
                            period = state.period,
                            onPeriodChange = viewModel::setPeriod,
                            isPremium = state.isPremium,
                            onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                        )
                    }
                    SectionHeader(
                        title = "Transaction-based insights",
                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                    )
                }

                // Transaction-based cards depend on state.period — while a period switch is
                // in flight, show a scoped skeleton here instead of swapping the whole screen
                // (the Budget-based insights section below is period-independent and stays put).
                if (state.isPeriodChanging) {
                    item {
                        SkeletonColumn(
                            blockHeights = listOf(90.dp, 140.dp, 160.dp, 120.dp),
                            modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                        )
                    }
                } else {
                    // Summary Metrics
                    if (a.summary != null) {
                        item {
                            StaggeredCard(index = 1, visible = cardsVisible) {
                                SummaryMetricsCard(
                                    data = a.summary,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    } else {
                        a.errors["summary"]?.let { err ->
                            item {
                                StaggeredCard(index = 1, visible = cardsVisible) {
                                    SectionErrorCard(
                                        title = "Summary unavailable",
                                        message = err,
                                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Monthly Trends
                    if (a.monthlyTrends != null) {
                        item {
                            StaggeredCard(index = 2, visible = cardsVisible) {
                                MonthlyTrendsCard(
                                    section = a.monthlyTrends,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    } else {
                        a.errors["monthly_trends"]?.let { err ->
                            item {
                                StaggeredCard(index = 2, visible = cardsVisible) {
                                    SectionErrorCard(
                                        title = "Monthly trends unavailable",
                                        message = err,
                                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Cash Flow Waterfall
                    if (a.cashFlowWaterfall != null) {
                        item {
                            StaggeredCard(index = 3, visible = cardsVisible) {
                                CashFlowCard(
                                    section = a.cashFlowWaterfall,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    } else {
                        a.errors["cash_flow_waterfall"]?.let { err ->
                            item {
                                StaggeredCard(index = 3, visible = cardsVisible) {
                                    SectionErrorCard(
                                        title = "Cash flow unavailable",
                                        message = err,
                                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Anomalies
                    val anomalyData = a.anomalies?.data
                    if (anomalyData != null && (anomalyData.anomaliesDetected ?: 0) > 0) {
                        item {
                            StaggeredCard(index = 4, visible = cardsVisible) {
                                AnomaliesCard(
                                    // Now safely smart-cast to non-null 'AnomalyData'
                                    section = a.anomalies,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    } else if (a.anomalies == null && !state.isPremium) {
                        item {
                            StaggeredCard(index = 4, visible = cardsVisible) {
                                PremiumUpsellCard(
                                    feature = "anomaly detection",
                                    onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    } else if (a.anomalies == null) {
                        a.errors["anomalies"]?.let { err ->
                            item {
                                StaggeredCard(index = 4, visible = cardsVisible) {
                                    SectionErrorCard(
                                        title = "Anomaly detection unavailable",
                                        message = err,
                                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Section: Budget-based insights (need a monthly budget set) ──────────
                item {
                    SectionHeader(
                        title = "Budget-based insights",
                        modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                    )
                }

                // Budget vs Actual
                if (a.budgetVsActual != null) {
                    item {
                        StaggeredCard(index = 5, visible = cardsVisible) {
                            BudgetVsActualCard(
                                section = a.budgetVsActual,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else if (!state.isPremium) {
                    item {
                        StaggeredCard(index = 5, visible = cardsVisible) {
                            PremiumUpsellCard(
                                feature = "budget vs. actual insights",
                                onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else {
                    // A null budget_vs_actual with no entry in `errors` is the legitimate
                    // "no monthly budget set yet" case (see comprehensive_service.go) — only
                    // show a card here when the backend actually recorded a failure.
                    a.errors["budget_vs_actual"]?.let { err ->
                        item {
                            StaggeredCard(index = 5, visible = cardsVisible) {
                                SectionErrorCard(
                                    title = "Budget vs actual unavailable",
                                    message = err,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    }
                }

                // Spending Velocity
                if (a.spendingVelocity != null) {
                    item {
                        StaggeredCard(index = 6, visible = cardsVisible) {
                            SpendingVelocityCard(
                                section = a.spendingVelocity,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else if (!state.isPremium) {
                    item {
                        StaggeredCard(index = 6, visible = cardsVisible) {
                            PremiumUpsellCard(
                                feature = "spending velocity",
                                onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else {
                    a.errors["spending_velocity"]?.let { err ->
                        item {
                            StaggeredCard(index = 6, visible = cardsVisible) {
                                SectionErrorCard(
                                    title = "Spending velocity unavailable",
                                    message = err,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    }
                }

                // Expense Forecast
                if (a.expenseForecast != null) {
                    item {
                        StaggeredCard(index = 7, visible = cardsVisible) {
                            ExpenseForecastCard(
                                section = a.expenseForecast,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else if (!state.isPremium) {
                    item {
                        StaggeredCard(index = 7, visible = cardsVisible) {
                            PremiumUpsellCard(
                                feature = "expense forecasting",
                                onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else {
                    a.errors["expense_forecast"]?.let { err ->
                        item {
                            StaggeredCard(index = 7, visible = cardsVisible) {
                                SectionErrorCard(
                                    title = "Expense forecast unavailable",
                                    message = err,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    }
                }

                // Financial Health (composite score)
                if (a.financialHealth != null) {
                    item {
                        StaggeredCard(index = 8, visible = cardsVisible) {
                            FinancialHealthCard(
                                health = a.financialHealth.data,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else if (!state.isPremium) {
                    item {
                        StaggeredCard(index = 8, visible = cardsVisible) {
                            PremiumUpsellCard(
                                feature = "financial health score",
                                onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                } else {
                    // Same "absent, no error = no budget yet" pattern as the other three
                    // budget-dependent cards above.
                    a.errors["financial_health"]?.let { err ->
                        item {
                            StaggeredCard(index = 8, visible = cardsVisible) {
                                SectionErrorCard(
                                    title = "Financial health unavailable",
                                    message = err,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Stagger wrapper ──────────────────────────────────────────────────────────

@Composable
private fun StaggeredCard(
    index: Int,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val delayMs = (index * 70).coerceAtMost(350)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(380, delayMs, FastOutSlowInEasing),
        label = "stagger_alpha_$index",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "stagger_offset_$index",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) { content() }
}

// ─── Period toggle ────────────────────────────────────────────────────────────

@Composable
private fun PeriodToggle(
    period: AnalyticsPeriod,
    onPeriodChange: (AnalyticsPeriod) -> Unit,
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(AnalyticsPeriod.MONTH to "Month", AnalyticsPeriod.LIFETIME to "Lifetime")
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = period == value,
                onClick = {
                    // Lifetime is Premium-only (it would otherwise trivially bypass the
                    // Free-tier 90-day transaction-history cap) — tapping it while on Free
                    // opens the upgrade flow instead of switching periods.
                    if (value == AnalyticsPeriod.LIFETIME && !isPremium) {
                        onUpgradeClick()
                    } else {
                        onPeriodChange(value)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsHeader(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    DashboardStyleHeader(
        currentPeriodLabel = viewModel.currentPeriodLabel,
        greetingText = viewModel.greetingText,
        isRefreshing = state.isRefreshing,
        isOffline = state.isOffline,
        streakDrawable = viewModel.streakDrawable,
        streakLabel = viewModel.streakLabel,
        modifier = modifier,
    )
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricChip(
                    label = "Income",
                    amount = d.totalIncome,
                    delta = d.totalIncome - c.totalIncome,
                    icon = Icons.Default.ArrowDownward,
                    color = LightColors.Income,
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "Expenses",
                    amount = d.totalExpense,
                    delta = d.totalExpense - c.totalExpense,
                    icon = Icons.Default.ArrowUpward,
                    color = LightColors.Expense,
                    invertDelta = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricChip(
                    label = "Savings",
                    amount = d.totalSavings,
                    delta = d.totalSavings - c.totalSavings,
                    icon = Icons.Default.Savings,
                    color = LightColors.Savings,
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "Net",
                    amount = d.netMovement,
                    delta = d.netMovement - c.netMovement,
                    icon = Icons.Default.ShowChart,
                    color = if (d.netMovement >= 0) LightColors.Income else LightColors.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    amount: Double,
    delta: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    invertDelta: Boolean = false,
) {
    val deltaPositive = if (invertDelta) delta <= 0 else delta >= 0
    val deltaColor =
        if (delta == 0.0) {
            MaterialTheme.colorScheme.onSurface
        } else if (deltaPositive) {
            LightColors.Income
        } else {
            LightColors.Expense
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier =
                Modifier
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
                text = if (amount == 0.0) "—" else amount.ugxShort(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (delta != 0.0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        if (deltaPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        null,
                        tint = deltaColor,
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
    val d = section?.data ?: return // ← Skip entirely if data is null

    val alertColor =
        when (d.alertLevel) {
            "ok" -> LightColors.Income
            "warning" -> Color(0xFFFF9500)
            else -> LightColors.Expense
        }

    var dayRingTarget by remember { mutableStateOf(0f) }
    var budgetRingTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(d) {
        dayRingTarget = d.dayFraction
        budgetRingTarget = d.budgetUsedFraction
    }
    val dayRing by animateFloatAsState(dayRingTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "day_ring")
    val budgetRing by animateFloatAsState(
        budgetRingTarget,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "budget_ring",
    )

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Spending Velocity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Day ${d.daysElapsed} of ${d.daysTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                AlertLevelBadge(level = d.alertLevel, alertColor = alertColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nested rings: outer = day progress, inner = budget used
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outerStroke = 8.dp.toPx()
                        val innerStroke = 9.dp.toPx()
                        val outerInset = outerStroke / 2f
                        val innerInset = outerStroke + 8.dp.toPx() + innerStroke / 2f
                        val outerTL = Offset(outerInset, outerInset)
                        val outerSz = Size(size.width - outerStroke, size.height - outerStroke)
                        val innerTL = Offset(innerInset, innerInset)
                        val innerSz = Size(size.width - innerInset * 2, size.height - innerInset * 2)
                        drawArc(
                            LightColors.Savings.copy(alpha = 0.10f),
                            -90f,
                            360f,
                            false,
                            outerTL,
                            outerSz,
                            style = Stroke(outerStroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            LightColors.Savings,
                            -90f,
                            360f * dayRing,
                            false,
                            outerTL,
                            outerSz,
                            style = Stroke(outerStroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            alertColor.copy(alpha = 0.10f),
                            -90f,
                            360f,
                            false,
                            innerTL,
                            innerSz,
                            style = Stroke(innerStroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            alertColor,
                            -90f,
                            360f * budgetRing,
                            false,
                            innerTL,
                            innerSz,
                            style = Stroke(innerStroke, cap = StrokeCap.Round),
                        )
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
                    VelocityRow(Icons.Default.AttachMoney, "Spent", d.totalSpent.ugxShort())
                    VelocityRow(Icons.Default.CalendarToday, "Days left", "${d.daysRemaining}d")
                    VelocityRow(Icons.Default.ShowChart, "Daily avg", d.dailyAverage.ugxShort())
                    VelocityRow(Icons.Default.TrackChanges, "Projection", d.projectedMonthEnd.ugxShort())
                    d.daysUntilBudgetExhausted?.let {
                        VelocityRow(Icons.Default.HourglassBottom, "Budget lasts", "${it.toInt()}d", valueColor = alertColor)
                    }
                }
            }

            // Pattern footer
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (d.alertLevel == "ok") Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = alertColor,
                        modifier = Modifier.size(11.dp),
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
private fun VelocityRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = LightColors.Savings, modifier = Modifier.size(10.dp).defaultMinSize(minWidth = 14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
    }
}

@Composable
private fun AlertLevelBadge(
    level: String,
    alertColor: Color,
) {
    val color =
        when (level) {
            "ok" -> LightColors.Income.copy(alpha = 0.10f)
            "warning" -> Color(0xFFFF9500).copy(alpha = 0.10f)
            else -> LightColors.Expense.copy(alpha = 0.10f)
        }
    val icon =
        when (level) {
            "ok" -> Icons.Default.CheckCircle
            "warning" -> Icons.Default.Warning
            else -> Icons.Default.Cancel
        }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = alertColor, modifier = Modifier.size(10.dp))
            Text(
                level.replaceFirstChar {
                    it.uppercase()
                },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Monthly Trends Card ──────────────────────────────────────────────────────

private enum class TrendMetric(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    SAVINGS("Savings"),
    NET("Net"),
}

// private object TrendColors {
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
// }

private fun TrendMetric.barColor(value: Double): Color =
    when (this) {
        TrendMetric.EXPENSE -> LightColors.Expense
        TrendMetric.INCOME -> LightColors.Income
        TrendMetric.SAVINGS -> LightColors.Savings
        TrendMetric.NET -> if (value >= 0) NetPos else NetNeg
    }

private fun MonthEntry.valueFor(metric: TrendMetric): Double =
    when (metric) {
        TrendMetric.EXPENSE -> expense
        TrendMetric.INCOME -> income
        TrendMetric.SAVINGS -> savings
        TrendMetric.NET -> net
    }

@Composable
private fun MonthlyTrendsCard(
    section: MonthlyTrendsSection,
    modifier: Modifier = Modifier,
) {
    val months = section.data.months
    val summary = section.data.summary

    var metric by remember { mutableStateOf(TrendMetric.EXPENSE) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Animate bars to full height on first composition and on metric change.
    var animTrigger by remember { mutableStateOf(0) }
    var animPct by remember { mutableStateOf(0f) }
    LaunchedEffect(animTrigger) {
        animPct = 1f
    }
    val springPct by animateFloatAsState(
        targetValue = animPct,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "bar_spring",
    )

    val maxVal =
        remember(months, metric) {
            months.maxOfOrNull { abs(it.valueFor(metric)) }?.takeIf { it > 0 } ?: 1.0
        }

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Header ────────────────────────────────────────────────────
            TrendsHeaderComposable(
                trend = summary.incomeTrend,
                healthScore = section.health.score,
            )

            // ── Metric chips ──────────────────────────────────────────────
            MetricChipRowComposable(
                active = metric,
                onSelect = { m ->
                    metric = m
                    selectedIndex = null
                    animTrigger++
                },
            )

            // ── Tooltip / always visible ──────────────────────────────────
            BarTooltipComposable(
                selectedIndex = selectedIndex,
                months = months,
                metric = metric,
            )

            // ── Bar chart with values ─────────────────────────────────────
            BarChartComposable(
                months = months,
                metric = metric,
                maxVal = maxVal,
                animatedPct = springPct,
                selectedIndex = selectedIndex,
                onBarClick = { idx ->
                    selectedIndex = if (selectedIndex == idx) null else idx
                },
            )

            // ── Month labels ──────────────────────────────────────────────
            MonthLabelRowComposable(months = months, selectedIndex = selectedIndex)

            // ── Summary stats ─────────────────────────────────────────────
            if (summary.avgIncome + summary.avgExpense > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    thickness = 0.5.dp,
                )
                SummaryStatsRowComposable(summary = summary)
            }
        }
    }
}

@Composable
private fun TrendsHeaderComposable(
    trend: String,
    healthScore: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Monthly Trends",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = trend.replaceFirstChar { it.uppercase() },
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
            text = "Score $score",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = IncomeText,
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
                label = m.label,
                isActive = m == active,
                onClick = { onSelect(m) },
            )
        }
    }
}

@Composable
private fun MetricChipComposable(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (isActive) ChipActive else MaterialTheme.colorScheme.surfaceVariant,
        label = "chip_bg",
    )
    val textColor by animateColorAsState(
        if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_text",
    )
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = if (!isActive) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)) else null,
        modifier =
            Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = textColor,
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
        modifier =
            Modifier
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Colored dot indicator
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(valueColor, CircleShape),
                )
                Text(
                    text = "${m.shortLabel} ${m.date.take(4)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = v.ugxShort(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = valueColor,
                )
                Text(
                    text = "· ${m.transactionCount} txns",
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(totalColor, CircleShape),
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = totalValue.ugxShort(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = totalColor,
                )
                Text(
                    text = "· ${months.sumOf { it.transactionCount }} txns",
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
        modifier =
            Modifier
                .fillMaxWidth()
                .height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEachIndexed { idx, month ->
            val value = month.valueFor(metric)
            val fraction = (abs(value) / maxVal).toFloat()
            val barHeightDp = (90f * fraction * animatedPct).coerceAtLeast(3f).dp
            val barColor = metric.barColor(value)
            val isSelected = selectedIndex == idx
            // Dim unselected bars when something is selected
            val barAlpha by animateFloatAsState(
                targetValue =
                    when {
                        !hasSelection -> 1f
                        isSelected -> 1f
                        else -> 0.25f
                    },
                animationSpec = tween(180),
                label = "bar_alpha_$idx",
            )
            // Scale selected bar slightly up for emphasis
            val barScale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                label = "bar_scale_$idx",
            )

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onBarClick(idx) },
                        ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Value text above bar
                    Text(
                        text = value.amountShort(),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = barColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(barHeightDp)
                                .graphicsLayer {
                                    alpha = barAlpha
                                    scaleX = barScale
                                    scaleY = barScale
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                }
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                                // Selected: white inner border for "lifted" feel
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            color = Color.White.copy(alpha = .55f),
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthLabelRowComposable(
    months: List<MonthEntry>,
    selectedIndex: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        months.forEachIndexed { idx, m ->
            val isSelected = selectedIndex == idx
            Text(
                text = m.shortLabel,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                color =
                    if (isSelected) {
                        ChipActive
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)
                    },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryStatsRowComposable(summary: MonthSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatPillComposable(
            label = "Avg income",
            value = summary.avgIncome.ugxShort(),
            color = LightColors.Income,
            modifier = Modifier.weight(1f),
        )
        StatPillComposable(
            label = "Avg expense",
            value = summary.avgExpense.ugxShort(),
            color = LightColors.Expense,
            modifier = Modifier.weight(1f),
        )
        StatPillComposable(
            label = "Avg savings",
            value = summary.avgSavings.ugxShort(),
            color = LightColors.Savings,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatPillComposable(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

// ─── Budget vs Actual Card ────────────────────────────────────────────────────

// ---------------------------------------------------------------------------
//  Colour tokens (match PesaMind palette already used in MonthlyTrendsCard)
// ---------------------------------------------------------------------------

private object BvaColors {
    // Status colours — intentionally reuse TrendColors semantics
    val UnderBudget = LightColors.Income // green  (income green)
    val OnBudget = LightColors.Savings // blue   (savings blue)
    val OverBudget = LightColors.Expense // red    (expense red)

    val IncomeLine = LightColors.Income
    val ExpenseLine = LightColors.Expense
    val SavingsLine = LightColors.Savings

    // Health pill backgrounds (muted)
    val ExcellentBg = LightColors.IncomeBg
    val ExcellentText = IncomeText
    val GoodBg = Color(0xFFE3F0FB)
    val GoodText = Color(0xFF1A4A7A)
    val FairBg = Color(0xFFFFF3E0)
    val FairText = Color(0xFF7A4A00)
    val PoorBg = Color(0xFFFFEBEE)
    val PoorText = Color(0xFF7A1A1A)

    // Track background
    val TrackBg = Color(0xFFEEEEEE)

    // Divider
    val Divider = Color(0xFFE0E0E0)
}

private fun statusColor(status: String) =
    when (status) {
        "under_budget" -> BvaColors.UnderBudget
        "over_budget" -> BvaColors.OverBudget
        else -> BvaColors.OnBudget
    }

private fun statusLabel(status: String) =
    when (status) {
        "under_budget" -> "Under budget"
        "over_budget" -> "Over budget"
        else -> "On budget"
    }

private data class HealthPillColors(val bg: Color, val text: Color)

private fun healthPillColors(status: String) =
    when (status) {
        "excellent" -> HealthPillColors(BvaColors.ExcellentBg, BvaColors.ExcellentText)
        "good" -> HealthPillColors(BvaColors.GoodBg, BvaColors.GoodText)
        "fair" -> HealthPillColors(BvaColors.FairBg, BvaColors.FairText)
        else -> HealthPillColors(BvaColors.PoorBg, BvaColors.PoorText)
    }

// ---------------------------------------------------------------------------
//  Top-level composable
// ---------------------------------------------------------------------------

/**
 * Usage (in your LazyColumn, identical call-site to the old card):
 *
 *   a.budgetVsActual?.let { bva ->
 *       item {
 *           StaggeredCard(index = 4, visible = cardsVisible) {
 *               BudgetVsActualCard(
 *                   section  = bva,
 *                   modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
 *               )
 *           }
 *       }
 *   }
 */
@Composable
fun BudgetVsActualCard(
    section: BudgetVsActualSection,
    modifier: Modifier = Modifier,
) {
    val d = section.data
    val health = section.health
    val comps = health.components

    val accentColor = statusColor(d.status)
    val pillColors = healthPillColors(health.status)

    // ── Usage fraction (0f–1f+) ──────────────────────────────────────────
    val rawFraction =
        if (d.budgetTotal > 0) {
            (d.actualTotal / d.budgetTotal).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

    // Animate bar fill on first composition / data change
    var barTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(d) { barTarget = rawFraction.coerceAtMost(1f) }
    val animatedBar by animateFloatAsState(
        targetValue = barTarget,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "bva_bar",
    )

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── Header ────────────────────────────────────────────────────
            BvaHeader(
                period = d.period,
                status = d.status,
                accentColor = accentColor,
                health = health,
                pillColors = pillColors,
            )

            // ── Primary progress bar ──────────────────────────────────────
            BvaBudgetBar(
                actualTotal = d.actualTotal,
                budgetTotal = d.budgetTotal,
                animatedFrac = animatedBar,
                rawFraction = rawFraction,
                variance = d.variance,
                variancePct = d.variancePercent,
                status = d.status,
                accentColor = accentColor,
            )

            HorizontalDivider(
                color = BvaColors.Divider,
                thickness = 0.5.dp,
            )

            // ── Component breakdown (Expense / Income / Savings) ──────────
            BvaComponentRows(comps = comps)

            // ── Recommendations (if any) ──────────────────────────────────
            if (section.recommendations.isNotEmpty()) {
                HorizontalDivider(color = BvaColors.Divider, thickness = 0.5.dp)
                BvaRecommendations(items = section.recommendations)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Header
// ---------------------------------------------------------------------------

@Composable
private fun BvaHeader(
    period: String,
    status: String,
    accentColor: Color,
    health: BvaHealth,
    pillColors: HealthPillColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Budget vs Actual",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Coloured dot
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape),
                )
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f),
                )
                Text(
                    text = period.toDisplayPeriod(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                )
            }
        }

        // Health score pill — benchmarked against HealthScorePill in MonthlyTrendsCard
        BvaHealthPill(
            score = health.score,
            status = health.status,
            pillColors = pillColors,
        )
    }
}

@Composable
private fun BvaHealthPill(
    score: Int,
    status: String,
    pillColors: HealthPillColors,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = pillColors.bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$score",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                color = pillColors.text,
            )
            Text(
                text = status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = pillColors.text.copy(alpha = .75f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Primary budget progress bar
// ---------------------------------------------------------------------------

@Composable
private fun BvaBudgetBar(
    actualTotal: Double,
    budgetTotal: Double,
    animatedFrac: Float,
    rawFraction: Float,
    variance: Double,
    variancePct: Double,
    status: String,
    accentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Spent / Budget labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "${actualTotal.ugxShort()} spent",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "of ${budgetTotal.ugxShort()} budget",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                )
            }
            // Variance badge
            VarianceBadge(variance = variance, variancePct = variancePct, status = status, accentColor = accentColor)
        }

        // Track + fill
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(BvaColors.TrackBg),
        ) {
            // Fill — gradient from accentColor faded → accentColor solid
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(animatedFrac)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    accentColor.copy(alpha = .65f),
                                    accentColor,
                                ),
                            ),
                        ),
            )
        }

        // Usage % label
        Text(
            text = "${(rawFraction * 100).toInt()}% utilised",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
        )
    }
}

@Composable
private fun VarianceBadge(
    variance: Double,
    variancePct: Double,
    status: String,
    accentColor: Color,
) {
    val pctStr = "${"%.0f".format(kotlin.math.abs(variancePct))}%"
    val label =
        when (status) {
            "under_budget" -> "$pctStr saved"
            "over_budget" -> "$pctStr over"
            else -> "on track"
        }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = .12f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Component breakdown rows (Expense / Income / Savings)
// ---------------------------------------------------------------------------

@Composable
private fun BvaComponentRows(comps: BvaHealthComponents) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BvaComponentRow(
            label = "Expenses",
            actual = comps.expense.actual,
            budgeted = comps.expense.budgeted,
            variance = comps.expense.variance,
            accentColor = BvaColors.ExpenseLine,
            // For expenses: positive variance = under (good); negative = over (bad)
            positiveIsGood = true,
        )
        BvaComponentRow(
            label = "Income",
            actual = comps.income.actual,
            budgeted = comps.income.budgeted,
            variance = comps.income.variance,
            accentColor = BvaColors.IncomeLine,
            // For income: negative variance means actual < budgeted (shortfall)
            positiveIsGood = false,
        )
        BvaComponentRow(
            label = "Savings",
            actual = comps.savings.actual,
            budgeted = comps.savings.budgeted,
            variance = comps.savings.variance,
            accentColor = BvaColors.SavingsLine,
            // For savings: positive variance = saved more than planned (great)
            positiveIsGood = false,
        )
    }
}

@Composable
private fun BvaComponentRow(
    label: String,
    actual: Double,
    budgeted: Double,
    variance: Double,
    accentColor: Color,
    // controls the sign-of-variance colour logic
    positiveIsGood: Boolean,
) {
    val fraction =
        if (budgeted > 0) {
            (actual / budgeted).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    // Animate mini-bar
    var barTarget by remember(actual, budgeted) { mutableStateOf(0f) }
    LaunchedEffect(actual, budgeted) { barTarget = fraction }
    val animBar by animateFloatAsState(
        targetValue = barTarget,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "comp_bar_$label",
    )

    // Variance label colour logic
    val varianceGood = if (positiveIsGood) variance >= 0 else variance <= 0
    val varianceColor = if (varianceGood) BvaColors.UnderBudget else BvaColors.OverBudget
    val variancePrefix = if (variance >= 0) "+" else ""

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Label + actual
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(7.dp)
                            .background(accentColor, CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Actual / budgeted · variance
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${actual.ugxShort()} / ${budgeted.ugxShort()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f),
                )
                Text(
                    text = "$variancePrefix${variance.ugxShort()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = varianceColor,
                )
            }
        }

        // Mini bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = .12f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(animBar)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor.copy(alpha = .6f), accentColor),
                            ),
                        ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Recommendations
// ---------------------------------------------------------------------------

@Composable
private fun BvaRecommendations(items: List<AnalyticsRecommendation>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Recommendations",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
        )
        items.forEach { rec ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f),
                )
                Text(
                    text = rec.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Extensions — identical to MonthlyTrendsCard so they can share one file
// ---------------------------------------------------------------------------

/**
 * "2026-06"  →  "June 2026"
 */
private fun String.toDisplayPeriod(): String {
    return try {
        val parts = split("-")
        val year = parts[0]
        val month =
            java.time.Month.of(parts[1].toInt())
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        "$month $year"
    } catch (_: Exception) {
        this
    }
}

@Composable
private fun BudgetCountChip(
    label: String,
    count: Int,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BudgetLineItemRow(item: BudgetLineItem) {
    val color = if (item.status == "over_budget") LightColors.Expense else LightColors.Income
    var barTarget by remember(item.category) { mutableStateOf(0f) }
    LaunchedEffect(item.category) { barTarget = item.usageFraction }
    val barW by animateFloatAsState(
        barTarget,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "item_${item.category}",
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.category,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
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
                Text(
                    "$it txn${if (it == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ─── Expense Forecast Card ────────────────────────────────────────────────────

// ---------------------------------------------------------------------------
//  Colour helpers
// ---------------------------------------------------------------------------

private object ForecastColors {
    val Exceed = LightColors.Expense // expense red  — will exceed
    val OnTrack = LightColors.Income // income green — on track
    val Actual = LightColors.Savings // savings blue — actual bar
    val BudgetBar = Color(0xFFBDBDBD) // neutral grey — budget reference bar

    // Severity → alert chip colours
    fun alertBg(severity: String) =
        when (severity) {
            "critical" -> Color(0xFFFFEBEE)
            "warning" -> Color(0xFFFFF8E1)
            "success" -> Color(0xFFE8F5E9)
            else -> Color(0xFFE3F2FD) // info / default
        }

    fun alertText(severity: String) =
        when (severity) {
            "critical" -> Color(0xFFC62828)
            "warning" -> Color(0xFFE65100)
            "success" -> Color(0xFF1B5E20)
            else -> Color(0xFF0D47A1)
        }

    fun alertIcon(severity: String) =
        when (severity) {
            "critical" -> Icons.Default.Cancel
            "warning" -> Icons.Default.Warning
            "success" -> Icons.Default.CheckCircle
            else -> Icons.Default.Info
        }
}

// ---------------------------------------------------------------------------
//  Top-level composable
// ---------------------------------------------------------------------------

@Composable
fun ExpenseForecastCard(
    section: ExpenseForecastSection,
    modifier: Modifier = Modifier,
) {
    val d = section.data
    val exceedColor = if (d.willExceedBudget) ForecastColors.Exceed else ForecastColors.OnTrack

    // Compute the common max so every bar is on the same scale
    val maxVal =
        remember(d) {
            maxOf(d.projectedTotal, d.budgetLimit, d.actualSpent, 1.0)
        }

    // Animate targets
    var ringTarget by remember { mutableStateOf(0f) }
    var actualTarget by remember { mutableStateOf(0f) }
    var projTarget by remember { mutableStateOf(0f) }
    var budgetTarget by remember { mutableStateOf(0f) }

    LaunchedEffect(d) {
        ringTarget = d.confidence.toFloat().coerceIn(0f, 1f)
        actualTarget = (d.actualSpent / maxVal).toFloat().coerceIn(0f, 1f)
        projTarget = (d.projectedTotal / maxVal).toFloat().coerceIn(0f, 1f)
        budgetTarget = (d.budgetLimit / maxVal).toFloat().coerceIn(0f, 1f)
    }

    val ringProg by animateFloatAsState(ringTarget, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow), label = "forecast_ring")
    val actualProg by animateFloatAsState(actualTarget, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow), label = "actual_bar")
    val projProg by animateFloatAsState(projTarget, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow), label = "proj_bar")
    val budgetProg by animateFloatAsState(budgetTarget, spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow), label = "budget_bar")

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── Header ────────────────────────────────────────────────────
            ForecastHeader(
                period = d.period,
                exceed = d.willExceedBudget,
                exceedColor = exceedColor,
                confidencePct = d.confidencePct,
            )

            // ── Hero: confidence ring + projection figure ─────────────────
            ForecastHero(
                d = d,
                exceedColor = exceedColor,
                ringProg = ringProg,
            )

            // ── Burn stats row ────────────────────────────────────────────
            if (d.dailyBurnRate > 0) {
                ForecastBurnRow(d = d)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f),
                thickness = 0.5.dp,
            )

            // ── Comparison bars ───────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ForecastBar(
                    label = "Actual spent",
                    value = d.actualSpent,
                    color = ForecastColors.Actual,
                    progress = actualProg,
                )
                ForecastBar(
                    label = "Projected total",
                    value = d.projectedTotal,
                    color = exceedColor,
                    progress = projProg,
                )
                ForecastBar(
                    label = "Budget limit",
                    value = d.budgetLimit,
                    color = ForecastColors.BudgetBar,
                    progress = budgetProg,
                    isStatic = true,
                )
            }

            // ── Variance callout (new field) ──────────────────────────────
            ForecastVarianceChip(
                variance = d.projectedVariance,
                exceed = d.willExceedBudget,
                exceedColor = exceedColor,
            )

            // ── Recommendations ───────────────────────────────────────────
            section.recommendations.forEach { rec ->
                ForecastAlert(rec = rec)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun ForecastHeader(
    period: String,
    exceed: Boolean,
    exceedColor: Color,
    confidencePct: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Expense Forecast",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(exceedColor, CircleShape),
                )
                Text(
                    text = if (exceed) "Over budget projected" else "On track",
                    style = MaterialTheme.typography.labelSmall,
                    color = exceedColor,
                )
                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
                Text(
                    text = period.toDisplayPeriod(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                )
            }
        }

        // Confidence pill — mirrors HealthScorePill pattern from MonthlyTrendsCard
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = exceedColor.copy(alpha = .12f),
        ) {
            Text(
                text = "$confidencePct% conf.",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    ),
                color = exceedColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ForecastHero(
    d: ForecastData,
    exceedColor: Color,
    ringProg: Float,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Confidence arc ring ──────────────────────────────────────────
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 9.dp.toPx()
                val inset = strokePx / 2f
                val arcTl = Offset(inset, inset)
                val arcSz = Size(size.width - strokePx, size.height - strokePx)

                // Track
                drawArc(
                    color = exceedColor.copy(alpha = .15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTl,
                    size = arcSz,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
                // Fill
                drawArc(
                    color = exceedColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ringProg,
                    useCenter = false,
                    topLeft = arcTl,
                    size = arcSz,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${d.confidencePct}%",
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        ),
                    color = exceedColor,
                )
                Text(
                    text = "conf.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                )
            }
        }

        // ── Projection figure ────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Month-end projection",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
            Text(
                text = d.projectedTotal.ugxFull(),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (d.willExceedBudget) Icons.Default.Cancel else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = exceedColor,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = if (d.willExceedBudget) "Will exceed budget" else "Within budget",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = exceedColor,
                )
            }
        }
    }
}

@Composable
private fun ForecastBurnRow(d: ForecastData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ForecastStat(label = "Daily burn", value = d.dailyBurnRate.ugxShort())
            VerticalDivider(modifier = Modifier.height(24.dp))
            ForecastStat(label = "Days elapsed", value = "${d.daysElapsed}")
            VerticalDivider(modifier = Modifier.height(24.dp))
            ForecastStat(label = "Budget", value = d.budgetLimit.ugxShort())
        }
    }
}

@Composable
private fun ForecastStat(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
        )
    }
}

@Composable
private fun ForecastBar(
    label: String,
    value: Double,
    color: Color,
    progress: Float,
    isStatic: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isStatic) .55f else 1f),
            )
            Text(
                text = value.ugxShort(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = .12f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isStatic) color.copy(alpha = .45f) else color,
                        ),
            )
        }
    }
}

/** Shows projected over/under variance as a compact inline chip */
@Composable
private fun ForecastVarianceChip(
    variance: Double,
    exceed: Boolean,
    exceedColor: Color,
) {
    val absVariance = kotlin.math.abs(variance)
    val label =
        if (exceed) {
            "Projected ${absVariance.ugxShort()} over budget"
        } else {
            "Projected ${absVariance.ugxShort()} under budget"
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = exceedColor.copy(alpha = .08f),
        border =
            androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = exceedColor.copy(alpha = .30f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (exceed) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = exceedColor,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = exceedColor,
            )
        }
    }
}

/** Severity-aware recommendation chip */
@Composable
private fun ForecastAlert(rec: ForecastRecommendation) {
    val bgColor = ForecastColors.alertBg(rec.severity)
    val textColor = ForecastColors.alertText(rec.severity)
    val icon = ForecastColors.alertIcon(rec.severity)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier =
                    Modifier
                        .size(13.dp)
                        .padding(top = 1.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (rec.title.isNotBlank()) {
                    Text(
                        text = rec.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                    )
                }
                Text(
                    text = rec.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = .85f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Extensions — shared with BudgetVsActualCard; move to FormatUtils.kt
// ---------------------------------------------------------------------------

// ─── Cash Flow Waterfall Card ─────────────────────────────────────────────────

@Composable
private fun CashFlowCard(
    section: CashFlowWaterfallSection,
    modifier: Modifier = Modifier,
) {
    val d = section.data
    val net = d.closingBalance - d.openingBalance

    // Scaled by magnitude (not raw max) so a negative bar (e.g. an overdrawn Open/Close
    // balance) gets the same visual weight as a positive one of equal size.
    val bars =
        listOf(
            Triple("Open", d.openingBalance, LightColors.Savings),
            Triple("+Income", d.income.total, LightColors.Income),
            Triple("-Expense", d.expenses.total, LightColors.Expense),
            Triple("-Savings", d.savingsTransfers, Color(0xFF5856D6)),
            Triple("Close", d.closingBalance, LightColors.Savings),
        )
    val maxMagnitude = bars.maxOfOrNull { abs(it.second) }?.takeIf { it > 0 } ?: 1.0

    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animated = true }

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cash Flow",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text("Waterfall", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }

            // Waterfall bars — diverging around a shared zero line: a negative value (e.g. an
            // overdrawn Open/Close balance) grows downward below the line, a positive one grows
            // upward above it, both scaled against the same halfHeight so they're comparable.
            val chartHeight = 90.dp
            val halfHeight = chartHeight / 2
            Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    thickness = 0.5.dp,
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    bars.forEach { (label, value, color) ->
                        val fraction = (value / maxMagnitude).toFloat()
                        val animFrac by animateFloatAsState(
                            targetValue = if (animated) fraction else 0f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                            label = "waterfall_$label",
                        )
                        val barHeight = (abs(animFrac) * halfHeight.value).dp.coerceAtLeast(if (value != 0.0) 4.dp else 0.dp)
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // Positive zone — bar grows up from the zero line at this Box's bottom edge.
                            Box(
                                modifier = Modifier.fillMaxWidth().height(halfHeight),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (value >= 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            value.ugxShort(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                            color = color,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(barHeight)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(color),
                                        )
                                    }
                                }
                            }
                            // Negative zone — bar grows down from the zero line at this Box's top edge.
                            Box(
                                modifier = Modifier.fillMaxWidth().height(halfHeight),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (value < 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(barHeight)
                                                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                                    .background(color),
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            value.ugxShort(),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                            color = color,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                bars.forEach { (label, _, _) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Net indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (net >= 0) Icons.Default.NorthEast else Icons.Default.SouthEast,
                    null,
                    tint = if (net >= 0) LightColors.Income else LightColors.Expense,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (net == 0.0) {
                        "No movement this period"
                    } else {
                        "Net ${if (net >= 0) "+" else ""}${net.ugxShort()} this period"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (net >= 0) LightColors.Income else LightColors.Expense,
                )
            }

            // Income sources + expense categories summary
            val incomeSources = d.income.sources
            val expenseCats = d.expenses.categories
            if (!incomeSources.isNullOrEmpty() || !expenseCats.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    incomeSources?.let { sources ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "INCOME",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            sources.forEach { src ->
                                CashFlowEntryChip(src, d.income.total, LightColors.Income)
                            }
                        }
                    }
                    expenseCats?.let { cats ->
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "EXPENSES",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
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
private fun CashFlowEntryChip(
    entry: CashFlowEntry,
    total: Double,
    color: Color,
) {
    val fraction = if (total > 0) (entry.amount / total).toFloat().coerceIn(0f, 1f) else 0f
    var barTarget by remember(entry.channel) { mutableStateOf(0f) }
    LaunchedEffect(entry.channel) { barTarget = fraction }
    val barW by animateFloatAsState(
        barTarget,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "cash_${entry.channel}",
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                entry.channel.replace("_", " ").replaceFirstChar {
                    it.uppercase()
                },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(entry.amount.ugxShort(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color)) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
        entry.percent?.let {
            Text(
                "${it.toInt()}% of total",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Anomalies Card ───────────────────────────────────────────────────────────

// ---------------------------------------------------------------------------
//  Colour helpers
// ---------------------------------------------------------------------------

private object AnomalyColors {
    // Severity — new mapping: "high" | "medium" | "low"
    val High = LightColors.Expense // expense red
    val Medium = Color(0xFFE07B00) // amber
    val Low = LightColors.Savings // savings blue (informational)

    fun severityColor(severity: String) =
        when (severity.lowercase()) {
            "high", "critical" -> High // accept both old and new strings
            "medium", "warning" -> Medium
            else -> Low
        }

    fun severityLabel(severity: String) =
        when (severity.lowercase()) {
            "high", "critical" -> "High"
            "medium", "warning" -> "Medium"
            else -> "Low"
        }

    // Sigma heat colour — the higher the sigma the redder the badge
    fun sigmaColor(sigma: Double) =
        when {
            sigma >= 10.0 -> High
            sigma >= 5.0 -> Medium
            else -> Low
        }

    // Header pill
    val CriticalBg = Color(0xFFFFEBEE)
    val CriticalText = Color(0xFFC62828)
    val WarningBg = Color(0xFFFFF3E0)
    val WarningText = Color(0xFFE65100)
    val ClearBg = Color(0xFFE8F5E9)
    val ClearText = Color(0xFF1B5E20)
}

// ---------------------------------------------------------------------------
//  Top-level composable
// ---------------------------------------------------------------------------

@Composable
fun AnomaliesCard(
    section: AnomalySection,
    modifier: Modifier = Modifier,
) {
    val d = section.data

    // Decide overall severity for header accent
    val headerColor =
        when {
            d.criticalCount > 0 -> AnomalyColors.High
            d.warningCount > 0 -> AnomalyColors.Medium
            else -> AnomalyColors.Low
        }

    AnalyticsCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── Header ────────────────────────────────────────────────────
            AnomalyHeader(
                data = d,
                headerColor = headerColor,
                period = section.metadata.period,
            )

            if (d.items.isEmpty()) {
                // ── Empty state ───────────────────────────────────────────
                AnomalyEmptyState()
            } else {
                // ── Item list ─────────────────────────────────────────────
                d.items?.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                            thickness = 0.5.dp,
                        )
                    }
                    AnomalyItemRow(item = item)
                }
            }

            // ── Recommendations ───────────────────────────────────────────
            if (section.recommendations.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                    thickness = 0.5.dp,
                )
                section.recommendations.forEach { rec ->
                    AnomalyAlert(rec = rec)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Header
// ---------------------------------------------------------------------------

@Composable
private fun AnomalyHeader(
    data: AnomalyData,
    headerColor: Color,
    period: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Anomalies",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(headerColor, CircleShape),
                )
                Text(
                    text =
                        if (data.anomaliesDetected == 0) {
                            "Nothing unusual"
                        } else {
                            "${data.anomaliesDetected} detected"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = headerColor,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .30f),
                )
                Text(
                    text = period.toDisplayPeriod(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                )
            }
        }

        // Count pills — only render non-zero counts
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (data.criticalCount > 0) {
                AnomalyCountPill(
                    label = "${data.criticalCount} High",
                    bg = AnomalyColors.CriticalBg,
                    text = AnomalyColors.CriticalText,
                )
            }
            if (data.warningCount > 0) {
                AnomalyCountPill(
                    label = "${data.warningCount} Med",
                    bg = AnomalyColors.WarningBg,
                    text = AnomalyColors.WarningText,
                )
            }
            if (data.criticalCount == 0 && data.warningCount == 0) {
                AnomalyCountPill(
                    label = "Clear",
                    bg = AnomalyColors.ClearBg,
                    text = AnomalyColors.ClearText,
                )
            }
        }
    }
}

@Composable
private fun AnomalyCountPill(
    label: String,
    bg: Color,
    text: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
            color = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Item row — signature element: severity accent bar on the left
// ---------------------------------------------------------------------------

@Composable
private fun AnomalyItemRow(item: AnomalyItem) {
    val severityColor = AnomalyColors.severityColor(item.severity)
    val sigmaColor = AnomalyColors.sigmaColor(item.sigmaMultiple)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Severity accent bar (the signature visual move) ───────────────
        Box(
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .width(3.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(severityColor),
        )

        // ── Content ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Type + category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Severity chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityColor.copy(alpha = .12f),
                    ) {
                        Text(
                            text = AnomalyColors.severityLabel(item.severity),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.sp,
                                ),
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                // Sigma badge — the "how weird is this" number
                SigmaBadge(sigma = item.sigmaMultiple, color = sigmaColor)
            }

            // Category
            Text(
                text = item.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )

            // Amount vs normal range
            AnomalyRangeRow(item = item, severityColor = severityColor)
        }
    }
}

@Composable
private fun SigmaBadge(
    sigma: Double,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = .10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "σ",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    ),
                color = color,
            )
            Text(
                text = "${"%.1f".format(sigma)}×",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                    ),
                color = color,
            )
        }
    }
}

@Composable
private fun AnomalyRangeRow(
    item: AnomalyItem,
    severityColor: Color,
) {
    // Fraction of amount within the normal range (capped at 1f for the bar)
    val rangeSpan = (item.normalMax - item.normalMin).coerceAtLeast(1.0)
    val fraction =
        ((item.amount - item.normalMin) / rangeSpan)
            .toFloat()
            .coerceAtLeast(0f)

    // Animate bar fill
    var barTarget by remember(item.amount) { mutableStateOf(0f) }
    LaunchedEffect(item.amount) { barTarget = fraction.coerceAtMost(1f) }
    val animBar by animateFloatAsState(
        targetValue = barTarget,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow),
        label = "anomaly_bar_${item.category}",
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "UGX ${item.amount.ugxShort()}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = severityColor,
            )
            Text(
                text = "normal ${item.normalMin.ugxShort()}–${item.normalMax.ugxShort()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
            )
        }

        // Range bar — normal range is the track, amount marker shows where it fell
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(severityColor.copy(alpha = .10f)),
        ) {
            // Normal range fill (full width = within normal)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(LightColors.Income.copy(alpha = .20f)),
            )
            // Actual spend marker — extends to show how far past normal it went
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(animBar.coerceAtMost(1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(severityColor),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Empty state
// ---------------------------------------------------------------------------

@Composable
private fun AnomalyEmptyState() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AnomalyColors.Low,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "No unusual activity this period",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
        )
    }
}

// ---------------------------------------------------------------------------
//  Severity-aware recommendation alert — same pattern as ForecastAlert
// ---------------------------------------------------------------------------

@Composable
private fun AnomalyAlert(rec: AnomalyRecommendation) {
    val bgColor = alertBg(rec.severity)
    val textColor = alertText(rec.severity)
    val icon = alertIcon(rec.severity)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier =
                    Modifier
                        .size(13.dp)
                        .padding(top = 1.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (rec.title.isNotBlank()) {
                    Text(
                        text = rec.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                    )
                }
                Text(
                    text = rec.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = .85f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Shared alert colour helpers (duplicate from ForecastAlert — move to
//  a shared AlertColors.kt in your common module)
// ---------------------------------------------------------------------------

private fun alertBg(severity: String) =
    when (severity.lowercase()) {
        "critical" -> Color(0xFFFFEBEE)
        "warning" -> Color(0xFFFFF8E1)
        "success" -> Color(0xFFE8F5E9)
        else -> Color(0xFFE3F2FD)
    }

private fun alertText(severity: String) =
    when (severity.lowercase()) {
        "critical" -> Color(0xFFC62828)
        "warning" -> Color(0xFFE65100)
        "success" -> Color(0xFF1B5E20)
        else -> Color(0xFF0D47A1)
    }

private fun alertIcon(severity: String) =
    when (severity.lowercase()) {
        "critical" -> Icons.Default.Cancel
        "warning" -> Icons.Default.Warning
        "success" -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }

// ---------------------------------------------------------------------------
//  Extensions — move to shared FormatUtils.kt
// ---------------------------------------------------------------------------

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun HealthScorePill(score: Int) {
    val color =
        when {
            score >= 80 -> LightColors.Income
            score >= 60 -> Color(0xFFFF9500)
            else -> LightColors.Expense
        }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            "$score",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

// ─── Shared card shell ────────────────────────────────────────────────────────

@Composable
private fun AnalyticsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/**
 * Inline "this one card failed" placeholder — the backend now computes each analytics section
 * independently and always returns 200, so one section's real failure (e.g. a transient DB
 * error) no longer blanks out the whole screen ([AnalyticsPhase.Error] is now reserved for total
 * failures with zero cached data). This surfaces that single section's error in its own slot
 * instead, leaving every other card unaffected.
 */
@Composable
private fun SectionErrorCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
