package cc.dlabs.pesamind.features.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.asUgx

/**
 * Collects payment details as an overlay on the subscription card.
 *
 * A sheet rather than a second screen: the customer has already chosen what they
 * are buying by tapping a priced button, so everything left is a phone number or a
 * card. Keeping the card visible behind the sheet also means dismissing lands back
 * on the plan rather than on a sales pitch they have already read.
 *
 * Uganda mobile money is entered here directly and authorises with a PIN prompt
 * on the customer's handset, so it never leaves the app. Google Play Billing
 * has no fields here at all — tapping its option hands off to Play's own
 * purchase UI via [onLaunchPlayPurchase].
 *
 * @param onOpenRedirect invoked with an authorisation URL when the provider asks
 *   for a browser redirect. Hoisted so this stays free of Custom Tabs plumbing.
 * @param onLaunchPlayPurchase invoked when the customer chooses to subscribe via
 *   Google Play. Hoisted so this stays free of BillingClient/Activity plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSheet(
    state: SubscriptionUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectMethod: (PaymentMethod) -> Unit,
    onSelectNetwork: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onOpenRedirect: (String) -> Unit,
    onLaunchPlayPurchase: () -> Unit,
) {
    // Hand off to the browser exactly once per redirect URL.
    LaunchedEffect(state.redirectUrl) {
        if (state.redirectUrl.isNotBlank()) {
            onOpenRedirect(state.redirectUrl)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(
                        start = Spacing.Space4.dp,
                        end = Spacing.Space4.dp,
                        bottom = Spacing.Space6.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            when (state.stage) {
                CheckoutStage.FORM ->
                    PaymentForm(
                        state = state,
                        onSelectMethod = onSelectMethod,
                        onSelectNetwork = onSelectNetwork,
                        onPhoneChange = onPhoneChange,
                        onSubmit = onSubmit,
                        onLaunchPlayPurchase = onLaunchPlayPurchase,
                    )

                CheckoutStage.AWAITING_AUTHORIZATION,
                CheckoutStage.AWAITING_REDIRECT,
                -> AwaitingAuthorization(state = state, onClose = onDismiss)

                CheckoutStage.SETTLED ->
                    SettledResult(state = state, onDone = onDismiss, onRetry = onRetry)
            }
        }
    }
}

// ─── Form ─────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.PaymentForm(
    state: SubscriptionUiState,
    onSelectMethod: (PaymentMethod) -> Unit,
    onSelectNetwork: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onLaunchPlayPurchase: () -> Unit,
) {
    val plan = state.selectedPlan

    Text(
        text = plan?.let { "Pay ${it.amount.asUgx()}" } ?: "Pay",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )
    plan?.let {
        Text(
            text = "Premium · ${intervalLabel(it.interval)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        text = "How would you like to pay?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Spacing.Space2.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Space2.dp)) {
//        FilterChip(
//            selected = state.method == PaymentMethod.MOBILE_MONEY,
//            onClick = { onSelectMethod(PaymentMethod.MOBILE_MONEY) },
//            label = { Text("Mobile Money") },
//            leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
//        )
        FilterChip(
            selected = state.method == PaymentMethod.GOOGLE_PLAY,
            onClick = { onSelectMethod(PaymentMethod.GOOGLE_PLAY) },
            label = { Text("Google Play") },
            leadingIcon = { Icon(Icons.Outlined.Shop, contentDescription = null) },
        )
    }

    when (state.method) {
        PaymentMethod.GOOGLE_PLAY ->
            GooglePlayNotice()
        PaymentMethod.MOBILE_MONEY ->
            MobileMoneyFields(state, onSelectNetwork, onPhoneChange)
    }

    state.error?.let { message ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Button(
        onClick = if (state.method == PaymentMethod.GOOGLE_PLAY) onLaunchPlayPurchase else onSubmit,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Space2.dp),
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(Spacing.Space5.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(plan?.let { "Pay ${it.amount.asUgx()}" } ?: "Pay")
        }
    }
}

@Composable
private fun ColumnScope.MobileMoneyFields(
    state: SubscriptionUiState,
    onSelectNetwork: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Space2.dp)) {
        FilterChip(
            selected = state.network == CheckoutValidator.NETWORK_MTN,
            onClick = { onSelectNetwork(CheckoutValidator.NETWORK_MTN) },
            label = { Text("MTN") },
        )
        FilterChip(
            selected = state.network == CheckoutValidator.NETWORK_AIRTEL,
            onClick = { onSelectNetwork(CheckoutValidator.NETWORK_AIRTEL) },
            label = { Text("Airtel") },
        )
    }
    OutlinedTextField(
        value = state.phoneNumber,
        onValueChange = onPhoneChange,
        label = { Text("Mobile money number") },
        placeholder = { Text("0772 123 456") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Google Play has no fields of its own here — tapping "Pay" hands off straight
 * to Play's purchase UI (card entry, saved payment methods, etc. all live
 * there). This is just the explanation shown before that handoff.
 */
@Composable
private fun ColumnScope.GooglePlayNotice() {
    Text(
        text = "You'll complete this purchase through Google Play using your Play Store payment method.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.Space1.dp),
    )
}

// ─── Waiting and result ───────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AwaitingAuthorization(
    state: SubscriptionUiState,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Space6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
    ) {
        if (!state.stoppedWaiting) {
            CircularProgressIndicator()
        }
        Text(
            text =
                when {
                    state.stoppedWaiting -> "Still waiting for your payment"
                    state.stage == CheckoutStage.AWAITING_REDIRECT ->
                        "Complete the payment in your browser"
                    else -> "Check your phone"
                },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Only once we have stopped polling. Before that the sheet is still
        // actively working and a button would invite the user to abandon a
        // payment that is about to land — but after it, the copy tells them they
        // can close this and previously gave them nothing to close it with.
        if (state.stoppedWaiting) {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun ColumnScope.SettledResult(
    state: SubscriptionUiState,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Space6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
    ) {
        Icon(
            imageVector = if (state.isPaid) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (state.isPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Spacing.Space12.dp),
        )
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.Space1.dp))

        if (state.isPaid) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Try again")
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
    }
}

// ─── Shared copy ──────────────────────────────────────────────────────────────

/** `month` -> `1 month`, matching what the plan buttons say. */
internal fun intervalLabel(interval: String): String =
    when (interval) {
        SubscriptionPeriod.INTERVAL_MONTH -> "1 month"
        SubscriptionPeriod.INTERVAL_YEAR -> "1 year"
        else -> interval
    }
