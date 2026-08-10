package cc.dlabs.pesamind.features.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.theme.Spacing
import kotlinx.coroutines.launch

/** One row of the tier-comparison table: an area of the app, and how it differs Free vs Premium. */
private data class PlanComparisonRow(
    val area: String,
    val free: String,
    val premium: String,
)

private val comparisonRows =
    listOf(
        PlanComparisonRow(
            "SMS auto-capture",
            "Off once your trial ends — add transactions manually",
            "Automatic for every channel, always",
        ),
        PlanComparisonRow("Manual transactions", "Unlimited, any channel", "Unlimited, any channel"),
        PlanComparisonRow("Transaction history", "Last 90 days", "Full history + search/filter"),
        PlanComparisonRow("Budgets", "1 active monthly budget, current month only", "Yearly budgets + next-month planning"),
        PlanComparisonRow(
            "Analytics",
            "Summary, monthly trends, cash flow",
            "Full suite: budget vs. actual, spending velocity, expense forecast, anomalies, financial health score",
        ),
        PlanComparisonRow("Security (PIN/pattern lock)", "Included", "Included"),
        PlanComparisonRow("Notifications", "Transaction confirmations", "Proactive budget & anomaly alerts"),
    )

/**
 * Tier-comparison + upgrade entry point, reached from [AccountSettingsScreen]'s Plan row and
 * every [cc.dlabs.pesamind.core.ui.PremiumUpsellCard] across the app. Billing is scaffolding-only
 * for now — the "Upgrade" action is a "Coming soon" placeholder, not a real purchase flow; see
 * the project plan for why real Play Billing integration is a separate follow-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(navController: NavHostController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val teal = MaterialTheme.colorScheme.primary

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Upgrade to Premium") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.Space4.dp, vertical = Spacing.Space4.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.Space4.dp),
                    colors = CardDefaults.cardColors(containerColor = teal.copy(alpha = 0.1f)),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Space4.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = teal)
                            Spacer(Modifier.width(Spacing.Space1.dp))
                            Text(
                                "Pesa Mind Premium",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "Unlock automatic transaction capture for every channel, full transaction history, " +
                                "and the complete analytics suite.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(comparisonRows) { row -> PlanComparisonRowCard(row) }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Upgrading is coming soon — stay tuned!")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = teal),
                ) {
                    Text("Upgrade — Coming soon")
                }
            }
        }
    }
}

@Composable
private fun PlanComparisonRowCard(row: PlanComparisonRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.Space3.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Space3.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            Text(row.area, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.free, style = MaterialTheme.typography.bodySmall)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            "Premium",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(row.premium, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
