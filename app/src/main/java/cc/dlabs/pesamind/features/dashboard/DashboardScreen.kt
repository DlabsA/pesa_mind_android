package cc.dlabs.pesamind.features.dashboard

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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.analytics.*
import cc.dlabs.pesamind.core.theme.*
import cc.dlabs.pesamind.core.theme.DarkColors
import cc.dlabs.pesamind.core.theme.LightColors
import cc.dlabs.pesamind.core.ui.AppCard
import cc.dlabs.pesamind.core.ui.DashboardStyleHeader
import cc.dlabs.pesamind.core.ui.ErrorState
import cc.dlabs.pesamind.core.ui.FinancialHealthCard
import cc.dlabs.pesamind.core.ui.OfflineBanner
import cc.dlabs.pesamind.core.ui.PremiumUpsellCard
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import cc.dlabs.pesamind.core.ui.StaggeredCard
import cc.dlabs.pesamind.features.analytics.AnomaliesCard
import java.text.NumberFormat
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

// ─── Unavailable Feature Overlay ───────────────────────────────────────────────

/**
 * Shows a card with an overlay indicating that a feature is not yet set up.
 * User can tap to navigate to the setup page.
 */
@Composable
private fun UnavailableFeatureOverlay(
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            modifier.clickable(
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() },
            ) { onNavigate() },
    ) {
        // Show faded content behind
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .alpha(0.3f),
        ) {
            content()
        }

        // Overlay with message
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Budgets not yet set",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to set up budgets",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─── Root Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
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
            label = "phase_switch",
        ) { phase ->
            when {
                (phase is DashboardPhase.Loading && state.dashboard == null && state.localSummary == null) ||
                    state.isRefreshing ->
                    SkeletonColumn(
                        blockHeights = listOf(36.dp, 130.dp, 52.dp, 170.dp, 140.dp, 120.dp),
                        modifier = Modifier.fillMaxSize(),
                    )

                phase is DashboardPhase.Error && state.dashboard == null ->
                    ErrorState(
                        message = phase.message,
                        onRetry = { viewModel.load() },
                        title = "Couldn't load dashboard",
                        icon = Icons.Default.CloudOff,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    DashboardScrollBody(
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
private fun DashboardScrollBody(
    state: DashboardUiState,
    viewModel: DashboardViewModel,
    onRefresh: () -> Unit,
    navController: NavController? = null,
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
            contentPadding = PaddingValues(bottom = Spacing.Space2.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Header
            item {
                DashboardHeader(
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

            // ── Net Movement Hero — computed live from local Room transactions
            // (TransactionRepository.observeMonthlySummary), independent of whether the
            // network dashboard fetch below has ever succeeded.
            state.localSummary?.let { summary ->
                item {
                    StaggeredCard(index = 0, visible = cardsVisible) {
                        NetMovementCard(
                            data = summary,
                            modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                        )
                    }
                }
            }

            state.dashboard?.let { d ->

                // ── Quick Stats
                item {
                    StaggeredCard(index = 1, visible = cardsVisible) {
                        QuickStatsRow(
                            data = d.summary.data,
                            health = d.summary.health,
                            modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                        )
                    }
                }

                // ── Premium upsell — replaces the Financial Health / Spending Velocity /
                // Anomalies analytics and the budget-based insights below for a Free-tier
                // account, instead of leaving those slots blank.
                if (!state.isPremium) {
                    item {
                        StaggeredCard(index = 2, visible = cardsVisible) {
                            PremiumUpsellCard(
                                feature = "Analytics & budget insights",
                                description =
                                    "Upgrade to access Premium Features.",
                                onUpgradeClick = { navController?.navigate(Routes.Upgrade.route) },
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                }

                // ── Financial Health
                item {
                    StaggeredCard(index = 2, visible = cardsVisible) {
                        val financialHealth = state.dashboard?.financialHealth
                        if (financialHealth != null) {
                            FinancialHealthCard(
                                health = financialHealth.data,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        } else if (state.isPremium) {
                            UnavailableFeatureOverlay(
                                onNavigate = {
                                    navController?.navigate(Routes.SetYearlyBudget.route)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                }

                // ── Spending Velocity
                item {
                    StaggeredCard(index = 3, visible = cardsVisible) {
                        val spendingVelocity = state.dashboard?.spendingVelocity
                        if (spendingVelocity != null) {
                            DashboardVelocityCard(
                                data = spendingVelocity.data,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        } else if (state.isPremium) {
                            UnavailableFeatureOverlay(
                                onNavigate = {
                                    navController?.navigate(Routes.SetYearlyBudget.route)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                }

                // ── Budget Utilization
                item {
                    d.budgetUtilization?.data?.let { budgetActualData ->
                        StaggeredCard(index = 4, visible = cardsVisible) {
                            DashboardBudgetCard(
                                data = budgetActualData,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                }

                // ── Anomalies (only when present)
                //
                // `anomalies` is null for a Free-tier account (Premium-gated
                // server-side), so this must be a safe call — dereferencing it
                // unconditionally crashed the dashboard for every Free user.
                val anomalies = d.anomalies
                if (anomalies != null && anomalies.data.anomaliesDetected > 0) {
                    item {
                        StaggeredCard(index = 5, visible = cardsVisible) {
                            AnomaliesCard(
                                section = anomalies,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    state: DashboardUiState,
    viewModel: DashboardViewModel,
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

// ─── Net Movement Hero Card ───────────────────────────────────────────────────
// Uses SummaryData and DashboardHealth directly (from DashboardResponse.summary)

@Composable
private fun NetMovementCard(
    data: SummaryData,
    modifier: Modifier = Modifier,
) {
    val net = data.netMovement
    val isPositive = net >= 0
    val isDark = isSystemInDarkTheme()
    val netColor =
        if (isPositive) {
            if (isDark) DarkColors.Income else LightColors.Income
        } else {
            if (isDark) DarkColors.Expense else LightColors.Expense
        }

    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }
    val numAlpha by animateFloatAsState(if (animateIn) 1f else 0f, tween(500, 200), label = "net_alpha")
    val numOffY by animateFloatAsState(if (animateIn) 0f else 12f, spring(Spring.DampingRatioMediumBouncy), label = "net_y")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Column {
            // Top gradient strip
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(netColor.copy(alpha = 0.55f), netColor.copy(alpha = 0.08f)),
                            ),
                        ),
            )

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Label row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.NorthEast else Icons.Default.SouthEast,
                            contentDescription = null,
                            tint = netColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Net Movement",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    Text(
                        data.currentMonth,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }

                // Big net figure
                Row(
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = numAlpha
                            translationY = numOffY
                        },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (isPositive) "+" else "−",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = netColor,
                    )
                    Text(
                        text = abs(net).toDouble().ugxFull(),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                            ),
                        color = netColor,
                        maxLines = 1,
                    )
                }

                // Income / Expense / Savings split
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NetSplitItem(
                        Icons.Default.ArrowDownward,
                        "Income",
                        data.totalIncome.toDouble().ugxShort(),
                        if (isDark) DarkColors.Income else LightColors.Income,
                    )
                    NetDivider()
                    NetSplitItem(
                        Icons.Default.ArrowUpward,
                        "Expenses",
                        data.totalExpense.toDouble().ugxShort(),
                        if (isDark) DarkColors.Expense else LightColors.Expense,
                    )
                    NetDivider()
                    NetSplitItem(
                        Icons.Default.Savings,
                        "Savings",
                        data.totalSavings.toDouble().ugxShort(),
                        if (isDark) DarkColors.Savings else LightColors.Savings,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun NetSplitItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ─── Quick Stats Row ──────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(
    data: SummaryData,
    health: Health,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val healthColor =
        when {
            health.score >= 80 -> if (isDark) DarkColors.Income else LightColors.Income
            health.score >= 60 -> Color(0xFFFF9500)
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickStatChip(
            "${data.transactionCount}",
            "Transactions",
            Icons.Default.CompareArrows,
            if (isDark) DarkColors.Savings else LightColors.Savings,
            Modifier.weight(1f),
        )
        QuickStatChip("${data.activeCategories}", "Categories", Icons.Default.Tag, Color(0xFF5856D6), Modifier.weight(1f))
        QuickStatChip(health.status.replaceFirstChar { it.uppercase() }, "Status", Icons.Default.Favorite, healthColor, Modifier.weight(1f))
    }
}

@Composable
private fun QuickStatChip(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

// ─── Spending Velocity Card ───────────────────────────────────────────────────
// Receives VelocityData directly (from DashboardResponse.spendingVelocity.data)

@Composable
private fun DashboardVelocityCard(
    data: VelocityData,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val alertColor =
        when (data.alertLevel) {
            "ok" -> if (isDark) DarkColors.Income else LightColors.Income
            "warning" -> Color(0xFFFF9500)
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        }
    val daysTotal = data.daysElapsed + data.daysRemaining
    val dayFrac = if (daysTotal > 0) data.daysElapsed.toFloat() / daysTotal else 0f
    val budgetFrac = if (data.budgetLimit > 0) (data.totalSpent / data.budgetLimit).toFloat().coerceIn(0f, 1f) else 0f

    var dayRingTarget by remember { mutableStateOf(0f) }
    var budgetRingTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(data) {
        dayRingTarget = dayFrac
        budgetRingTarget = budgetFrac
    }
    val dayRing by animateFloatAsState(dayRingTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "day_ring")
    val budgetRing by animateFloatAsState(
        budgetRingTarget,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "budget_ring",
    )

    AppCard(modifier = modifier) {
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
                        "Day ${data.daysElapsed} of $daysTotal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                AlertLevelBadge(level = data.alertLevel)
            }

            // Nested rings + metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nested rings: outer = day progress, inner = budget used
                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outerStroke = 7.dp.toPx()
                        val innerStroke = 8.dp.toPx()
                        val outerInset = outerStroke / 2f
                        val innerInset = outerStroke + 8.dp.toPx() + innerStroke / 2f
                        val outerTL = Offset(outerInset, outerInset)
                        val outerSz = Size(size.width - outerStroke, size.height - outerStroke)
                        val innerTL = Offset(innerInset, innerInset)
                        val innerSz = Size(size.width - innerInset * 2, size.height - innerInset * 2)
                        val dayColor = if (isDark) DarkColors.Savings else LightColors.Savings
                        // Outer track + fill (day progress)
                        drawArc(
                            dayColor.copy(alpha = 0.10f),
                            -90f,
                            360f,
                            false,
                            outerTL,
                            outerSz,
                            style = Stroke(outerStroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            dayColor.copy(alpha = 0.45f),
                            -90f,
                            360f * dayRing,
                            false,
                            outerTL,
                            outerSz,
                            style = Stroke(outerStroke, cap = StrokeCap.Round),
                        )
                        // Inner track + fill (budget used)
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
                            "${(budgetFrac * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = alertColor,
                        )
                        Text(
                            "used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }

                // Metrics list
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VelocityRow(Icons.Default.AttachMoney, "Spent", data.totalSpent.toDouble().ugxShort())
                    VelocityRow(Icons.Default.CalendarToday, "Days left", "${data.daysRemaining}d")
                    VelocityRow(Icons.Default.BarChart, "Daily avg", data.dailyAverage.ugxShort())
                    VelocityRow(Icons.Default.TrackChanges, "Projection", data.projectedMonthEnd.ugxShort())
                    data.daysUntilBudgetExhausted?.let { days ->
                        VelocityRow(Icons.Default.HourglassBottom, "Budget lasts", "${days.toInt()}d", valueColor = alertColor)
                    }
                }
            }

            // Pattern footer chip
            val patternLabel = data.spendingPattern.replace("_", " ").replaceFirstChar { it.uppercase() }
            Surface(shape = RoundedCornerShape(8.dp), color = alertColor.copy(alpha = 0.08f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (data.alertLevel == "ok") Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = alertColor,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        patternLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = alertColor,
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text(
                        "Limit: ${data.budgetLimit.ugxShort()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
    val isDark = isSystemInDarkTheme()
    val dayColor = if (isDark) DarkColors.Savings else LightColors.Savings
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = dayColor, modifier = Modifier.size(10.dp).defaultMinSize(minWidth = 14.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
private fun AlertLevelBadge(level: String) {
    val isDark = isSystemInDarkTheme()
    val color =
        when (level) {
            "ok" -> if (isDark) DarkColors.Income else LightColors.Income
            "warning" -> Color(0xFFFF9500)
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        }
    val icon =
        when (level) {
            "ok" -> Icons.Default.CheckCircle
            "warning" -> Icons.Default.Warning
            else -> Icons.Default.Cancel
        }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(10.dp))
            Text(
                level.replaceFirstChar {
                    it.uppercase()
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

// ─── Budget Utilization Card ──────────────────────────────────────────────────
// Receives BudgetActualData (from DashboardResponse.budgetUtilization?.data)

@Composable
private fun DashboardBudgetCard(
    data: BudgetActualData,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val statusColor =
        when (data.status) {
            "on_budget" -> if (isDark) DarkColors.Income else LightColors.Income
            "over_budget" -> if (isDark) DarkColors.Expense else LightColors.Expense
            else -> Color(0xFFFF9500)
        }
    // Must divide as Double — Long/Long integer division truncates to 0 whenever actualTotal
    // is under 100% of budgetTotal (the normal case), which is why the bar never filled.
    val usageFraction =
        if (data.budgetTotal > 0) (data.actualTotal.toDouble() / data.budgetTotal.toDouble()).coerceIn(0.0, 1.0) else 0.0

    var barTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(data) { barTarget = usageFraction.toFloat() }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "budget_bar")

    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Budget Utilization",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        data.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetCountChip("On Track", data.categoriesOnTrack, if (isDark) DarkColors.Income else LightColors.Income)
                    BudgetCountChip("Over", data.categoriesOverBudget, if (isDark) DarkColors.Expense else LightColors.Expense)
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (data.actualTotal.toDouble() == 0.0) "Nothing spent yet" else "${data.actualTotal.toDouble().ugxShort()} spent",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (data.budgetTotal > 0) "of ${data.budgetTotal.toDouble().ugxShort()}" else "No budget set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxWidth().height(
                            10.dp,
                        ).clip(RoundedCornerShape(5.dp)).background(statusColor.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(barW)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(Brush.horizontalGradient(listOf(statusColor, statusColor.copy(alpha = 0.70f)))),
                    )
                }
                if (data.budgetTotal > 0) {
                    Text(
                        "${(usageFraction.toDouble() * 100).toInt()}% utilised",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
            }

            // Category rows
            val items = data.items
            if (!items.isNullOrEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.take(3).forEach { item -> BudgetItemRow(item) }
                    if (items.size > 3) {
                        Text(
                            "+ ${items.size - 3} more categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "No budget categories configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCountChip(
    label: String,
    count: Int,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun BudgetItemRow(item: BudgetActualItem) {
    val isDark = isSystemInDarkTheme()
    val color =
        if (item.budget.toDouble() == 0.0) {
            Color(0xFFFF9500)
        } else if (item.actual <= item.budget) {
            (if (isDark) DarkColors.Income else LightColors.Income)
        } else {
            (if (isDark) DarkColors.Expense else LightColors.Expense)
        }
    // Same Long/Long-truncates-to-0 fix as DashboardBudgetCard's usageFraction above.
    val usageFrac =
        if (item.budget > 0) (item.actual.toDouble() / item.budget.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
    var barTarget by remember(item.category) { mutableStateOf(0f) }
    LaunchedEffect(item.category) { barTarget = usageFrac }
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
                if (item.budget > 0) {
                    "${item.actual.toDouble().ugxShort()} / ${item.budget.toDouble().ugxShort()}"
                } else {
                    item.actual.toDouble().ugxShort()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        item.transactions?.let { txCount ->
            Text(
                "$txCount transaction${if (txCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }
    }
}

// ─── Anomalies Card ───────────────────────────────────────────────────────────
// Receives AnomaliesData directly (from DashboardResponse.anomalies.data)

@Composable
private fun AnomalyBadge(
    text: String,
    color: Color,
) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
