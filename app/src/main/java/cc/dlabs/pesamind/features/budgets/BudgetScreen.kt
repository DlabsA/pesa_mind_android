package cc.dlabs.pesamind.features.budgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.DashboardStyleHeader
import cc.dlabs.pesamind.core.ui.ShimmerBox
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import cc.dlabs.pesamind.core.ui.StatsRowsSkeleton
import java.text.NumberFormat
import java.util.Locale

// ─── Formatting helpers ───────────────────────────────────────────────────────

private val ugxFormat = NumberFormat.getNumberInstance(Locale.US)

private fun Long.toUgxString(): String = ugxFormat.format(this)

private val months =
    listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

private fun Int.toMonthName() = months.getOrElse(this - 1) { "Month $this" }

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavHostController,
    vm: BudgetViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cardsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cardsVisible = true
        try {
            val storedAccount = AccountManager.getAccount()
            if (storedAccount.username.isBlank() && storedAccount.email.isBlank()) {
                snackbarHostState.showSnackbar("Account details not found. Please sign in again.")
            }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("Failed to load account details")
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BudgetHeader(
                state = state,
                viewModel = vm,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = 8.dp),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            indicator = {},
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            val showInitialSkeleton =
                state.isRefreshing ||
                    (
                        state.isLoading &&
                            state.yearlyBudget == null &&
                            state.currentMonthlyBudget == null &&
                            !state.isOffline
                    )

            if (showInitialSkeleton) {
                BudgetSkeletonView()
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    if (state.isOffline) {
                        item {
                            BudgetOfflineBanner(modifier = Modifier.padding(horizontal = Spacing.Space4.dp))
                        }
                    }

                    item {
                        StaggeredCard(index = 0, visible = cardsVisible) {
                            YearlyBudgetCard(
                                yearly = state.yearlyBudget,
                                isLoading = state.isLoadingYearly,
                                year = state.displayYear,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                navController = navController,
                            )
                        }
                    }

                    item {
                        StaggeredCard(index = 1, visible = cardsVisible) {
                            if (state.isPremium) {
                                NextMonthBudgetCard(
                                    nextMonth = state.nextMonthIndex,
                                    nextYear = state.nextMonthYear,
                                    hasExisting = state.hasNextMonthBudget,
                                    modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                    onSetBudget = {
                                        navController.navigate(
                                            Routes.SetMonthlyBudget.createRoute(
                                                state.nextMonthIndex,
                                                state.nextMonthYear,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    item {
                        StaggeredCard(index = 2, visible = cardsVisible) {
                            CurrentMonthCard(
                                yearly = state.yearlyBudget,
                                monthly = state.currentMonthlyBudget,
                                isLoading = state.isLoadingMonthly,
                                month = state.displayMonth,
                                year = state.displayYear,
                                balance = state.monthlyBalance,
                                isDeficit = state.isMonthlyDeficit,
                                modifier = Modifier.padding(horizontal = Spacing.Space4.dp),
                                onDetails = {
                                    if (state.yearlyBudget == null) {
                                        navController.navigate(Routes.SetYearlyBudget.route)
                                    } else {
                                        navController.navigate(
                                            Routes.SetMonthlyBudget.createRoute(
                                                state.displayMonth,
                                                state.displayYear,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

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
        label = "budget_stagger_alpha_$index",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "budget_stagger_offset_$index",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) { content() }
}

@Composable
private fun BudgetOfflineBanner(modifier: Modifier = Modifier) {
    val bannerText = MaterialTheme.colorScheme.onErrorContainer
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    brush =
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.AccessTime, null, tint = bannerText, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    "Offline - showing cached data",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = bannerText,
                )
                Text(
                    "Pull to refresh when connection is restored.",
                    style = MaterialTheme.typography.labelSmall,
                    color = bannerText.copy(alpha = 0.80f),
                )
            }
        }
    }
}

@Composable
private fun BudgetSkeletonView() {
    SkeletonColumn(
        blockHeights = listOf(4.dp, 210.dp, 92.dp, 260.dp),
        modifier = Modifier.fillMaxSize(),
    )
}

// ─── Dashboard Header ─────────────────────────────────────────────────────────
@Composable
private fun BudgetHeader(
    state: BudgetUiState,
    viewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
) {
    DashboardStyleHeader(
        currentPeriodLabel = state.currentPeriodLabel,
        greetingText = state.greetingText,
        isRefreshing = state.isRefreshing,
        isOffline = state.isOffline,
        streakDrawable = viewModel.streakDrawable,
        streakLabel = viewModel.streakLabel,
        modifier = modifier,
    )
}

// ─── Yearly Budget Card ───────────────────────────────────────────────────────

@Composable
private fun BudgetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        content()
    }
}

@Composable
private fun YearlyBudgetCard(
    yearly: YearlyBudgetResponse?,
    isLoading: Boolean,
    year: Int,
    modifier: Modifier = Modifier,
    navController: NavHostController,
) {
    BudgetCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Yearly Budget",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(14.dp))

            if (isLoading && yearly == null) {
                BudgetStatsSkeleton()
            } else if (yearly != null) {
                // Stat rows inside a soft inset container
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        YearlyStatRow(
                            label = "Expenditure",
                            value = yearly.totalExpenditures,
                            valueColor = MaterialTheme.colorScheme.error,
                            icon = Icons.AutoMirrored.Outlined.TrendingDown,
                            iconTint = MaterialTheme.colorScheme.error,
                        )
                        StatDivider()
                        YearlyStatRow(
                            label = "Income",
                            value = yearly.totalIncome,
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                        )
                        StatDivider()
                        YearlyStatRow(
                            label = "Savings",
                            value = yearly.totalSavings,
                            valueColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.WbSunny,
                            iconTint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { navController.navigate(Routes.SetYearlyBudget.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text(
                        "More Details",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            } else {
                // No yearly budget
                EmptyBudgetHint(text = "No yearly budget for $year yet.")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { navController.navigate(Routes.SetYearlyBudget.route) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text("Create Budget")
                }
            }
        }
    }
}

@Composable
private fun YearlyStatRow(
    label: String,
    value: Long,
    valueColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.10f),
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text =
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = valueColor,
                        ),
                    ) {
                        append(value.toUgxString())
                    }
                    withStyle(
                        SpanStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = valueColor.copy(alpha = 0.65f),
                        ),
                    ) {
                        append(" UGX")
                    }
                },
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
    )
}

// ─── Next Month Budget Card ───────────────────────────────────────────────────

@Composable
private fun NextMonthBudgetCard(
    nextMonth: Int,
    nextYear: Int,
    hasExisting: Boolean,
    modifier: Modifier = Modifier,
    onSetBudget: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Next month's budget",
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSetBudget),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Calendar icon badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (hasExisting) {
                                "${nextMonth.toMonthName()} Budget"
                            } else {
                                "Set ${nextMonth.toMonthName()} Budget"
                            },
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            if (hasExisting) {
                                "View & edit $nextYear plan"
                            } else {
                                "Plan ahead for $nextYear"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── Current Month Card ───────────────────────────────────────────────────────

@Composable
private fun CurrentMonthCard(
    yearly: YearlyBudgetResponse?,
    monthly: MonthlyBudgetResponse?,
    isLoading: Boolean,
    month: Int,
    year: Int,
    balance: Long,
    isDeficit: Boolean,
    modifier: Modifier = Modifier,
    onDetails: () -> Unit,
) {
    val isYearlyMissing = yearly == null
    val statusBg =
        if (isDeficit) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
        }
    val statusColor = if (isDeficit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val statusText = if (isDeficit) "Deficit" else "Surplus"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The card itself — slight negative top offset to overlap the icon
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp, start = 18.dp, end = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${month.toMonthName()} Budget",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(10.dp))

                if (isYearlyMissing) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked until yearly budget is created",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Monthly budget is locked",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Create a yearly budget first to unlock ${month.toMonthName()} planning.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onDetails,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(vertical = 13.dp),
                    ) {
                        Text(
                            text = "Create Yearly Budget",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                } else if (isLoading && monthly == null) {
                    ShimmerBox(Modifier.width(140.dp).height(24.dp), cornerRadius = Radius.Medium.dp)
                    Spacer(Modifier.height(Spacing.Space4.dp))
                    ShimmerBox(Modifier.width(180.dp).height(36.dp), cornerRadius = Radius.Medium.dp)
                    Spacer(Modifier.height(Spacing.Space5.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(84.dp), cornerRadius = 14.dp)
                } else if (monthly != null) {
                    // Status chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusBg,
                    ) {
                        Text(
                            text = "Transactions Status: $statusText",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Hero balance figure
                    Text(
                        text =
                            buildAnnotatedString {
                                if (isDeficit) {
                                    withStyle(
                                        SpanStyle(
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        ),
                                    ) {
                                        append("−")
                                    }
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-1).sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) { append(Math.abs(balance).toUgxString()) }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) { append(" UGX") }
                            },
                    )

                    Spacer(Modifier.height(20.dp))

                    // Income / Expenditure / Total breakdown
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            MonthlyStatRow("Income", monthly.totalIncome, MaterialTheme.colorScheme.tertiary)
                            StatDivider()
                            MonthlyStatRow("Expenditure", monthly.totalExpenditures, MaterialTheme.colorScheme.error)
                            StatDivider()
                            MonthlyStatRow(
                                label = "Balance",
                                value = Math.abs(balance),
                                valueColor = if (isDeficit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                prefix = if (isDeficit) "−" else "+",
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onDetails,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text(
                            "More Details",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp,
                                ),
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    EmptyBudgetHint(text = "No budget set for ${month.toMonthName()} $year.")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDetails,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text("Create Budget")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyStatRow(
    label: String,
    value: Long,
    valueColor: Color,
    prefix: String = "",
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                buildAnnotatedString {
                    if (prefix.isNotEmpty()) {
                        withStyle(
                            SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor),
                        ) { append(prefix) }
                    }
                    withStyle(
                        SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor),
                    ) { append(value.toUgxString()) }
                    withStyle(
                        SpanStyle(fontSize = 10.sp, color = valueColor.copy(alpha = 0.6f)),
                    ) { append(" UGX") }
                },
        )
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BudgetStatsSkeleton() {
    StatsRowsSkeleton()
}
