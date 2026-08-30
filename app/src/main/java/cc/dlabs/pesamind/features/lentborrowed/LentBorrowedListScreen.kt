package cc.dlabs.pesamind.features.lentborrowed

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.DebtCreditResponse
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import cc.dlabs.pesamind.core.ui.PremiumUpsellCard
import cc.dlabs.pesamind.core.ui.asUgxAmount
import java.text.SimpleDateFormat
import java.util.Locale as JavaLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LentBorrowedListScreen(
    navController: NavHostController,
    viewModel: DebtCreditViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(PesaMindStrings.DebtCredit.FEATURE_NAME) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isPremium) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add debt")
                }
            }
        },
    ) { padding ->
        if (!state.isPremium) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                PremiumUpsellCard(
                    feature = PesaMindStrings.DebtCredit.FEATURE_NAME,
                    onUpgradeClick = { navController.navigate(Routes.Upgrade.route) },
                    description = "Track money you've lent or borrowed, with due-date reminders.",
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Debt filter options with counts ──────────────────────────────
            val lentCount = state.debts.count { it.direction == "lent" }
            val borrowedCount = state.debts.count { it.direction == "borrowed" }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DebtFilterOption(
                    label = PesaMindStrings.DebtCredit.LENT_LABEL,
                    count = lentCount,
                    isSelected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    modifier = Modifier.weight(1f),
                )
                DebtFilterOption(
                    label = PesaMindStrings.DebtCredit.BORROWED_LABEL,
                    count = borrowedCount,
                    isSelected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            val direction = if (tabIndex == 0) "lent" else "borrowed"
            val filtered = state.debts.filter { it.direction == direction }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    EmptyState(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = if (tabIndex == 0) "Nothing owed to you yet" else "You don't owe anyone yet",
                        subtitle = "Tap + to add someone you've lent to or borrowed from.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { debt ->
                        DebtCreditCard(debt = debt, onClick = { navController.navigate(Routes.LentBorrowedDetail.createRoute(debt.id)) })
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateDebtCreditSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { direction, name, phone, amount, dueAt, note, offsets ->
                viewModel.createDebtCredit(direction, name, phone, amount, dueAt, note, offsets)
                showCreateSheet = false
            },
        )
    }
}

@Composable
private fun DebtCreditCard(
    debt: DebtCreditResponse,
    onClick: () -> Unit,
) {
    val isLent = debt.direction == "lent"
    val accentColor = if (isLent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val accentBg = if (isLent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val isOverdue =
        debt.dueDate?.let {
            try {
                java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() < System.currentTimeMillis() && debt.status != "settled"
            } catch (_: Exception) {
                false
            }
        } ?: false

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", JavaLocale.getDefault()) }
    val dueDateFormatted =
        remember(debt.dueDate) {
            debt.dueDate?.let {
                try {
                    dateFormatter.format(java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli())
                } catch (_: Exception) {
                    null
                }
            }
        }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon background
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentBg,
                modifier = Modifier.size(46.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = debt.counterpartyName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (isLent) "You lent money" else "You owe money",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (dueDateFormatted != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color =
                            if (isOverdue) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.55f,
                                )
                            },
                    ) {
                        Text(
                            text = "Due $dueDateFormatted",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = debt.outstanding.asUgxAmount(),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.2).sp,
                        ),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UGX",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun DebtFilterOption(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(80.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        border =
            if (isSelected) {
                androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                )
            } else {
                null
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
