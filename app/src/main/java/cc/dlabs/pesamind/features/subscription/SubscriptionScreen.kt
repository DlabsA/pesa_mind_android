package cc.dlabs.pesamind.features.subscription

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.PaymentDeepLink
import cc.dlabs.pesamind.core.network.models.PlanResponse
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.ui.DetailScreenTopBar
import cc.dlabs.pesamind.core.ui.ErrorState
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import cc.dlabs.pesamind.core.ui.asUgx
import java.time.OffsetDateTime

/**
 * What Premium costs and what it gets you, on one card.
 *
 * Replaces the old two-step Upgrade -> Checkout funnel. The interstitial showed
 * seven bullets and no price, so every entry point in the app asked the user to
 * commit before telling them the amount; it was also tier-blind, which meant a
 * paying customer opening Settings -> Subscription was shown a pitch to upgrade.
 *
 * One card, one price per interval, and payment collected in a sheet on top of it.
 */
private val benefits =
    listOf(
        "Unlimited channels (cash, mobile money, bank)",
        "Automatic SMS capture",
        "Manual entries for mobile money channels",
        "Full transaction history",
        "Advanced budgeting (next-month planning)",
        "Complete analytics suite",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    navController: NavHostController,
    vm: SubscriptionViewModel = viewModel(),
    onOpenRedirect: (String) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val returnedInvoiceId by PaymentDeepLink.returnedInvoiceId.collectAsState()
    // Google Play Billing's launchBillingFlow needs the hosting Activity, not just
    // a Context — LocalContext.current is the Activity itself here since this
    // screen is always composed inside one (see PesaMindNavGraph).
    val activity = LocalContext.current as? Activity

    // Sheet visibility is explicit local UI state, not derived from `stage`.
    // Deriving it would make dismissing during the wait reopen the sheet on the
    // very next recomposition, trapping the user in it.
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Pick up a payment left in flight by a previous visit or an earlier process.
    // Without this the only recovery is the app-start path in SubscriptionResumer,
    // so returning to this screen mid-payment showed a plain plan card as though
    // nothing were happening.
    LaunchedEffect(Unit) { vm.resumePendingInvoice() }

    // The customer came back from the 3DS page. The deep link's `status` parameter
    // is ignored — only the invoice id is trusted, and we re-read it authenticated.
    LaunchedEffect(returnedInvoiceId) {
        returnedInvoiceId?.let { invoiceId ->
            vm.onRedirectReturned(invoiceId)
            PaymentDeepLink.consume()
            sheetOpen = true
        }
    }

    // A payment that resumed on its own still needs the sheet in front of it.
    LaunchedEffect(state.stage) {
        if (state.stage != CheckoutStage.FORM) sheetOpen = true
    }

    Scaffold(
        topBar = {
            DetailScreenTopBar(
                title = "Premium",
                subtitle = headerSubtitle(state),
                badge = tierBadge(state),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoadingPlans ->
                    SkeletonColumn(
                        blockHeights = listOf(360.dp, 56.dp),
                        modifier = Modifier.padding(Spacing.Space4.dp),
                    )

                state.plans.isEmpty() ->
                    ErrorState(
                        message = state.error ?: "No plans available right now.",
                        onRetry = { vm.loadPlans() },
                    )

                else ->
                    PlanContent(
                        state = state,
                        onChoosePlan = { code ->
                            // A finished attempt, or one the user gave up waiting on,
                            // must not put its old result in front of a new one — the
                            // sheet renders by stage, so without this a second tap
                            // reopened on "Still waiting" instead of the form. A
                            // payment genuinely still in flight is left alone and
                            // simply reattached.
                            if (state.stage == CheckoutStage.SETTLED || state.stoppedWaiting) {
                                vm.retry()
                            }
                            vm.selectPlan(code)
                            sheetOpen = true
                        },
                    )
            }
        }
    }

    if (sheetOpen) {
        PaymentSheet(
            state = state,
            sheetState = sheetState,
            // Dismissing only closes the sheet; the stage is left as-is so a payment
            // still in flight keeps polling. Resetting is the job of the next plan
            // tap, which is the only place that knows a fresh attempt is starting.
            onDismiss = { sheetOpen = false },
            onSelectMethod = vm::selectMethod,
            onSelectNetwork = vm::selectNetwork,
            onPhoneChange = vm::updatePhoneNumber,
            onSubmit = vm::submit,
            onRetry = vm::retry,
            onOpenRedirect = onOpenRedirect,
            onLaunchPlayPurchase = { activity?.let(vm::launchPlayPurchase) },
        )
    }
}

@Composable
private fun PlanContent(
    state: SubscriptionUiState,
    onChoosePlan: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.Space4.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
    ) {
        item { PremiumCard(state = state, onChoosePlan = onChoosePlan) }

        item {
            Text(
                text =
                    "You'll be charged once. Your plan does not renew automatically — " +
                        "we'll remind you before it ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.Space1.dp),
            )
        }

        item { Spacer(Modifier.height(Spacing.Space6.dp)) }
    }
}

@Composable
private fun PremiumCard(
    state: SubscriptionUiState,
    onChoosePlan: (String) -> Unit,
) {
    val primary = getPrimaryColor()
    val periodEnd = state.currentPeriodEnd
    val now = OffsetDateTime.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Space4.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            // Header: icon chip, and a status pill only when there is a status to show.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    color = primary.copy(alpha = 0.16f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.padding(Spacing.Space3.dp).size(Spacing.Space5.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                statusPillLabel(state)?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(Radius.Full.dp),
                        color = primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            modifier =
                                Modifier.padding(
                                    horizontal = Spacing.Space3.dp,
                                    vertical = Spacing.Space1.dp,
                                ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space1.dp)) {
                Text(
                    text = "Premium",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Everything Pesa Mind can do",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp)) {
                benefits.forEach { benefit -> BenefitRow(benefit, primary) }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.Space1.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // One button per plan the backend actually returned — never assume the
            // catalogue is exactly monthly + yearly.
            state.orderedPlans.forEachIndexed { index, plan ->
                PlanButton(
                    plan = plan,
                    isPrimary = index == 0,
                    savingPercent = SubscriptionPeriod.savingPercent(plan, state.plans),
                    coversUntil = SubscriptionPeriod.coverageEnd(periodEnd, now, plan.interval),
                    onClick = { onChoosePlan(plan.code) },
                )
            }

            // Said once, under the buttons whose dates already demonstrate it.
            if (SubscriptionPeriod.isActive(periodEnd, now.toInstant())) {
                val remaining = SubscriptionPeriod.daysRemaining(periodEnd, now.toInstant())
                Text(
                    text =
                        "Paying now doesn't restart your plan — your remaining " +
                            "$remaining ${if (remaining == 1) "day" else "days"} are added on top.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(
    label: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Spacing.Space5.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlanButton(
    plan: PlanResponse,
    isPrimary: Boolean,
    savingPercent: Int?,
    coversUntil: OffsetDateTime,
    onClick: () -> Unit,
) {
    val label = "Pay ${intervalWord(plan.interval)} · ${plan.amount.asUgx()}"

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space1.dp)) {
        if (isPrimary) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(Spacing.Space12.dp),
                shape = RoundedCornerShape(Radius.Medium.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(Spacing.Space12.dp),
                shape = RoundedCornerShape(Radius.Medium.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                savingPercent?.let {
                    Spacer(Modifier.width(Spacing.Space2.dp))
                    Surface(
                        shape = RoundedCornerShape(Radius.Full.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    ) {
                        Text(
                            text = "Save $it%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier =
                                Modifier.padding(
                                    horizontal = Spacing.Space2.dp,
                                    vertical = Spacing.Space1.dp,
                                ),
                        )
                    }
                }
            }
        }

        // The whole early-payment story, stated per option rather than explained:
        // for an active subscriber this date is period-end + interval, not today +
        // interval, because that is exactly what the backend will grant.
        Text(
            text = "Covers you to ${SubscriptionPeriod.formatDate(coversUntil)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.Space1.dp),
        )
    }
}

// ─── Copy helpers ─────────────────────────────────────────────────────────────

private fun intervalWord(interval: String): String =
    when (interval) {
        SubscriptionPeriod.INTERVAL_MONTH -> "monthly"
        SubscriptionPeriod.INTERVAL_YEAR -> "yearly"
        else -> interval
    }

/** Right-hand pill on the card. Null for Free — there is no status worth a pill. */
private fun statusPillLabel(state: SubscriptionUiState): String? {
    val end = state.currentPeriodEnd ?: return null
    if (!SubscriptionPeriod.isActive(end, OffsetDateTime.now().toInstant())) return null
    return when {
        state.isOnTrial -> "Trial · ${SubscriptionPeriod.daysRemaining(end, OffsetDateTime.now().toInstant())}d left"
        state.isOnPaidPlan -> "Active to ${SubscriptionPeriod.formatDate(end)}"
        else -> null
    }
}

private fun headerSubtitle(state: SubscriptionUiState): String =
    when {
        state.isOnTrial -> "You're on the free trial"
        state.isOnPaidPlan -> "Your plan is active"
        else -> "Unlock everything Pesa Mind can do"
    }

private fun tierBadge(state: SubscriptionUiState): String =
    when {
        state.isOnTrial -> "Trial"
        state.isOnPaidPlan -> "Premium"
        else -> "Free"
    }
