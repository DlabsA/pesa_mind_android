package cc.dlabs.pesamind.features.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.Account
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.theme.BackgroundLight
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.IncomeGreen
import cc.dlabs.pesamind.core.theme.PesaMindGreen
import cc.dlabs.pesamind.core.theme.PesaMindNavy
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// ─── Formatting helpers ───────────────────────────────────────────────────────

private val ugxFormat = NumberFormat.getNumberInstance(Locale.US)

private fun Long.toUgxString(): String = ugxFormat.format(this)

private val months = listOf(
    "January","February","March","April","May","June",
    "July","August","September","October","November","December"
)
private fun Int.toMonthName() = months.getOrElse(this - 1) { "Month $this" }

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavHostController,
    vm: BudgetViewModel= viewModel(),
    onNavigateToYearlyDetail: (String) -> Unit = {},
    onNavigateToMonthlyDetail: (String) -> Unit = {},
    onNavigateToCreateMonthlyBudget: (Int, Int) -> Unit = { _, _ -> },
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var account by remember { mutableStateOf<Account?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        try {
            val storedAccount = AccountManager.getAccount()
            if (storedAccount.username.isBlank() && storedAccount.email.isBlank()) {
                account = null
                accountError = "Account details not found. Please sign in again."
            } else {
                account = storedAccount
                accountError = null
            }
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }}
        catch (_: Exception) {
            account = null
            accountError = "Failed to load account details"
        }
    }

    val displayName = account?.username.orEmpty()
    val displayEmail = account?.email.orEmpty()
    val initial = displayName.firstOrNull()?.uppercase() ?: "U"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardHeader(
                state = state,
                onBack = { navController.popBackStack() },
                initial = initial
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

                // ── Body content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // Yearly budget
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 4 }
                    ) {
                        YearlyBudgetCard(
                            yearly = state.yearlyBudget,
                            isLoading = state.isLoadingYearly,
                            year = state.displayYear,
                            navController = navController,
                            onDetails = {
                                state.yearlyBudget?.id?.let(onNavigateToYearlyDetail)
                            }
                        )
                    }

                    // Next month prompt
                    NextMonthBudgetCard(
                        nextMonth = state.nextMonthIndex,
                        nextYear = state.nextMonthYear,
                        hasExisting = state.hasNextMonthBudget,
                        onSetBudget = {
                            onNavigateToCreateMonthlyBudget(
                                state.nextMonthIndex,
                                state.nextMonthYear
                            )
                        }
                    )

                    // Current monthly budget
                    CurrentMonthCard(
                        monthly = state.currentMonthlyBudget,
                        isLoading = state.isLoadingMonthly,
                        month = state.displayMonth,
                        year = state.displayYear,
                        balance = state.monthlyBalance,
                        isDeficit = state.isMonthlyDeficit,
                        onDetails = {
                            state.currentMonthlyBudget?.id?.let(onNavigateToMonthlyDetail)
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ─── Dashboard Header ─────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    state: DashboardUiState,
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
        // Greeting + avatar row centered lower in the hero
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
                if (state.userDisplayName.isNotBlank()) {
                    Text(
                        text = state.userDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Cache indicator pill
            if (state.isFromCache) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Cached",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}


// ─── Yearly Budget Card ───────────────────────────────────────────────────────

@Composable
private fun YearlyBudgetCard(
    yearly: YearlyBudgetResponse?,
    isLoading: Boolean,
    year: Int,
    onDetails: () -> Unit,
    navController: NavHostController
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yearly Budget",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PesaMindTeal.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = PesaMindTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (isLoading && yearly == null) {
                BudgetStatsSkeleton()
            } else if (yearly != null) {
                // Stat rows inside a soft inset container
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        YearlyStatRow(
                            label = "Expenditure",
                            value = yearly.totalExpenditures,
                            valueColor = ExpenseRed,
                            icon = Icons.Outlined.TrendingDown,
                            iconTint = ExpenseRed
                        )
                        StatDivider()
                        YearlyStatRow(
                            label = "Income",
                            value = yearly.totalIncome,
                            valueColor = PesaMindGreen,
                            icon = Icons.Outlined.TrendingUp,
                            iconTint = PesaMindGreen
                        )
                        StatDivider()
                        YearlyStatRow(
                            label = "Savings",
                            value = yearly.totalSavings,
                            valueColor = PesaMindTeal,
                            icon = Icons.Outlined.WbSunny,
                            iconTint = PesaMindTeal
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onDetails,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        "More Details",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            } else {
                // No yearly budget
                EmptyBudgetHint(text = "No yearly budget for $year yet.")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {navController.navigate(Routes.SetYearlyBudget.route)},
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
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
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.10f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = valueColor
                    )
                ) {
                    append(value.toUgxString())
                }
                withStyle(
                    SpanStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = valueColor.copy(alpha = 0.65f)
                    )
                ) {
                    append(" UGX")
                }
            }
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    )
}

// ─── Next Month Budget Card ───────────────────────────────────────────────────

@Composable
private fun NextMonthBudgetCard(
    nextMonth: Int,
    nextYear: Int,
    hasExisting: Boolean,
    onSetBudget: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Next month's budget",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSetBudget),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Calendar icon badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PesaMindTeal.copy(alpha = 0.10f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = PesaMindTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasExisting) "${nextMonth.toMonthName()} Budget"
                        else "Set ${nextMonth.toMonthName()} Budget",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (hasExisting) "View & edit $nextYear plan"
                        else "Plan ahead for $nextYear",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = PesaMindTeal.copy(alpha = 0.10f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Go",
                            tint = PesaMindTeal,
                            modifier = Modifier.size(13.dp)
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
    monthly: MonthlyBudgetResponse?,
    isLoading: Boolean,
    month: Int,
    year: Int,
    balance: Long,
    isDeficit: Boolean,
    onDetails: () -> Unit
) {
    val statusBg = if (isDeficit) ExpenseRed.copy(alpha = 0.08f) else IncomeGreen.copy(alpha = 0.08f)
    val statusColor = if (isDeficit) ExpenseRed else IncomeGreen
    val statusText = if (isDeficit) "Deficit" else "Surplus"

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

        // Floating light-bulb hint icon above the card
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFF3CD),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "💡", fontSize = 22.sp)
                }
            }
        }

        // The card itself — slight negative top offset to overlap the icon
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-14).dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, start = 18.dp, end = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${month.toMonthName()} Budget",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(10.dp))

                if (isLoading && monthly == null) {
                    CircularProgressIndicator(
                        color = PesaMindTeal,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (monthly != null) {

                    // Status chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = "Transactions Status: $statusText",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Hero balance figure
                    Text(
                        text = buildAnnotatedString {
                            if (isDeficit) withStyle(SpanStyle(color = ExpenseRed, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)) {
                                append("−")
                            }
                            withStyle(
                                SpanStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) { append(Math.abs(balance).toUgxString()) }
                            withStyle(
                                SpanStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            ) { append(" UGX") }
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Income / Expenditure / Total breakdown
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            MonthlyStatRow("Income", monthly.totalIncome, PesaMindGreen)
                            StatDivider()
                            MonthlyStatRow("Expenditure", monthly.totalExpenditures, ExpenseRed)
                            StatDivider()
                            MonthlyStatRow(
                                label = "Balance",
                                value = Math.abs(balance),
                                valueColor = if (isDeficit) ExpenseRed else IncomeGreen,
                                prefix = if (isDeficit) "−" else "+"
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onDetails,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            "More Details",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        )
                    }

                } else {
                    Spacer(Modifier.height(8.dp))
                    EmptyBudgetHint(text = "No budget set for ${month.toMonthName()} $year.")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDetails,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
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
    prefix: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = buildAnnotatedString {
                if (prefix.isNotEmpty()) withStyle(
                    SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
                ) { append(prefix) }
                withStyle(
                    SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
                ) { append(value.toUgxString()) }
                withStyle(
                    SpanStyle(fontSize = 10.sp, color = valueColor.copy(alpha = 0.6f))
                ) { append(" UGX") }
            }
        )
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BudgetStatsSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            tween(950, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.25f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.20f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }
    }
}