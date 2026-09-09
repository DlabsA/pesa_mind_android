package cc.dlabs.pesamind.features.subscription

import android.app.Activity
import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.billing.PlayBillingManager
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.CheckoutRequest
import cc.dlabs.pesamind.core.network.models.InvoiceResponse
import cc.dlabs.pesamind.core.network.models.PlanResponse
import cc.dlabs.pesamind.core.network.models.SubscriptionResponse
import cc.dlabs.pesamind.core.network.models.VerifyPlayPurchaseRequest
import cc.dlabs.pesamind.core.storage.PaymentManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Which authorisation branch the checkout is in. */
enum class CheckoutStage {
    /** Choosing a plan and entering payment details. */
    FORM,

    /**
     * Mobile money: the customer has a PIN prompt on their handset and we are
     * polling. They never leave the app.
     */
    AWAITING_AUTHORIZATION,

    /** Card 3DS: the customer is in a browser tab and we are waiting for them back. */
    AWAITING_REDIRECT,

    /** A terminal invoice status arrived. */
    SETTLED,
}

/**
 * Payment methods offered on the checkout form. GOOGLE_PLAY routes through
 * [PlayBillingManager] rather than the checkout form fields below — Play Store
 * policy requires digital subscriptions sold in-app to go through Play's own
 * billing, so this replaced the previous card-via-Flutterwave option.
 */
enum class PaymentMethod { MOBILE_MONEY, GOOGLE_PLAY }

/**
 * Single UiState for the whole subscription flow, per the one-state-per-screen rule
 * in `.claude/CLAUDE.md`.
 */
data class SubscriptionUiState(
    val isLoadingPlans: Boolean = false,
    val plans: List<PlanResponse> = emptyList(),
    /**
     * The server's view of what the user already has. Null until loaded, or when
     * the read failed — the screen degrades to the Free presentation rather than
     * blocking the sale on it.
     */
    val subscription: SubscriptionResponse? = null,
    val selectedPlanCode: String = "",
    val stage: CheckoutStage = CheckoutStage.FORM,
    val method: PaymentMethod = PaymentMethod.GOOGLE_PLAY,
    // Mobile money form
    val phoneNumber: String = "",
    val network: String = CheckoutValidator.NETWORK_MTN,
    val isSubmitting: Boolean = false,
    val invoice: InvoiceResponse? = null,
    /** Customer-facing note from the provider, e.g. "authorise on 256772…". */
    val instruction: String = "",
    /** Set only on the card 3DS branch; the URL a browser tab should open. */
    val redirectUrl: String = "",
    val statusMessage: String = "",
    val error: String? = null,
    /** True once the invoice reached `paid` — the screen shows success and exits. */
    val isPaid: Boolean = false,
    /**
     * We stopped polling but the payment may still land. Distinct from failure:
     * telling a user their payment failed when it is merely slow is the worst
     * thing this screen can do.
     */
    val stoppedWaiting: Boolean = false,
) {
    val selectedPlan: PlanResponse?
        get() = plans.firstOrNull { it.code == selectedPlanCode }

    /** Plans in the order the card renders them: monthly first, then yearly. */
    val orderedPlans: List<PlanResponse>
        get() = SubscriptionPeriod.forDisplay(plans)

    /**
     * End of whatever period is already running — paid or trial — or null when the
     * user has nothing. This is the value an early payment stacks on top of.
     */
    val currentPeriodEnd: OffsetDateTime?
        get() = SubscriptionPeriod.parse(subscription?.expiresAt)

    val isOnPaidPlan: Boolean
        get() = subscription?.isPremium == true && subscription.isTrial.not()

    val isOnTrial: Boolean
        get() = subscription?.isTrial == true
}

/**
 * Drives plan selection, checkout, and payment confirmation.
 *
 * Calls `ApiClient` directly rather than through a repository, per
 * `.claude/CLAUDE.md`'s rule against introducing a repository layer for a single
 * feature. The decision logic that would otherwise be untestable here lives in
 * [PaymentStatusResolver] and [CheckoutValidator].
 */
class SubscriptionViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadPlans()
        loadSubscription()
    }

    /**
     * Reads what the user already has, so each plan's button can state the date it
     * would cover them to.
     *
     * Deliberately separate from [loadPlans] and deliberately silent on failure:
     * without it the card falls back to the Free presentation, which is a worse
     * message but still a working one. Blocking the sale because a status read
     * failed would be the wrong trade.
     */
    fun loadSubscription() {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getSubscription()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _state.value = _state.value.copy(subscription = body)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read current subscription; showing plans without it", e)
            }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingPlans = true, error = null)
            try {
                val response = ApiClient.api.getPlans()
                if (response.isSuccessful) {
                    val plans = response.body().orEmpty()
                    _state.value =
                        _state.value.copy(
                            isLoadingPlans = false,
                            plans = plans,
                            // Default to the cheapest plan — the backend already
                            // orders by amount ascending.
                            selectedPlanCode =
                                _state.value.selectedPlanCode.ifBlank {
                                    plans.firstOrNull()?.code.orEmpty()
                                },
                        )
                } else {
                    _state.value =
                        _state.value.copy(
                            isLoadingPlans = false,
                            error = "Couldn't load plans (${response.code()})",
                        )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load plans", e)
                _state.value =
                    _state.value.copy(
                        isLoadingPlans = false,
                        error = "Couldn't load plans. Check your connection and try again.",
                    )
            }
        }
    }

    // ─── Form editing ─────────────────────────────────────────────────────────

    fun selectPlan(code: String) {
        _state.value = _state.value.copy(selectedPlanCode = code, error = null)
    }

    fun selectMethod(method: PaymentMethod) {
        _state.value = _state.value.copy(method = method, error = null)
    }

    fun updatePhoneNumber(raw: String) {
        // Preselect the network from the prefix as a convenience. Never enforced —
        // numbers get ported, and a wrong guess must not block a payment.
        val inferred = CheckoutValidator.normalizeMsisdn(raw)?.let(CheckoutValidator::inferNetwork)
        _state.value =
            _state.value.copy(
                phoneNumber = raw,
                network = inferred ?: _state.value.network,
                error = null,
            )
    }

    fun selectNetwork(network: String) {
        _state.value = _state.value.copy(network = network, error = null)
    }

    // ─── Checkout (mobile money) ────────────────────────────────────────────────

    /**
     * Submits the mobile money checkout form. The GOOGLE_PLAY method does not use
     * this — see [launchPlayPurchase] — since a Play purchase happens first, on
     * Play's own UI, rather than being built from form fields and posted.
     */
    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val plan = current.selectedPlan
        if (plan == null) {
            _state.value = current.copy(error = "Choose a plan first")
            return
        }

        val request =
            when (val result = CheckoutValidator.validateMobileMoney(current.phoneNumber, current.network)) {
                is CheckoutValidator.MobileMoneyResult.Invalid -> {
                    _state.value = current.copy(error = result.message)
                    return
                }
                is CheckoutValidator.MobileMoneyResult.Valid ->
                    CheckoutRequest(
                        planCode = plan.code,
                        method = METHOD_MOBILE_MONEY,
                        network = result.network,
                        phoneNumber = result.msisdn,
                    )
            }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                val response = ApiClient.api.checkout(request)
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    _state.value =
                        _state.value.copy(
                            isSubmitting = false,
                            error = checkoutErrorFor(response.code()),
                        )
                    return@launch
                }

                // Remember the invoice before anything else can fail. This is what
                // lets the app settle a payment the user walked away from — the
                // stand-in for a webhook.
                PaymentManager.setPendingInvoiceId(body.invoice.id)

                val stage =
                    if (body.nextAction == NEXT_ACTION_REDIRECT && body.redirectUrl.isNotBlank()) {
                        CheckoutStage.AWAITING_REDIRECT
                    } else {
                        CheckoutStage.AWAITING_AUTHORIZATION
                    }

                _state.value =
                    _state.value.copy(
                        isSubmitting = false,
                        stage = stage,
                        invoice = body.invoice,
                        instruction = body.instruction,
                        redirectUrl = body.redirectUrl,
                        statusMessage = body.instruction.ifBlank { "Waiting for your confirmation…" },
                    )

                startPolling(body.invoice.id)
            } catch (e: Exception) {
                Log.e(TAG, "Checkout failed", e)
                _state.value =
                    _state.value.copy(
                        isSubmitting = false,
                        error = "Couldn't start the payment. Check your connection and try again.",
                    )
            }
        }
    }

    // ─── Checkout (Google Play Billing) ─────────────────────────────────────────

    /**
     * Launches the Play purchase UI for the selected plan and, on success, sends
     * the resulting purchase token to the backend for verification.
     *
     * Unlike [submit], nothing is posted first — Play's own UI runs the purchase,
     * and only the result is reported to the backend. There is also nothing to
     * poll: [PlayBillingManager.launchPurchase] suspends until Play itself
     * resolves the purchase, and [verifyPlayPurchase] settles synchronously.
     */
    fun launchPlayPurchase(activity: Activity) {
        val current = _state.value
        if (current.isSubmitting) return
        val plan = current.selectedPlan
        if (plan == null) {
            _state.value = current.copy(error = "Choose a plan first")
            return
        }
        if (plan.playProductId.isBlank()) {
            _state.value = current.copy(error = "This plan isn't available for purchase yet.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)

            val offerings = PlayBillingManager.queryOfferings(listOf(plan.playProductId))
            val productDetails = offerings.firstOrNull { it.productId == plan.playProductId }
            if (productDetails == null) {
                _state.value =
                    _state.value.copy(isSubmitting = false, error = "This plan isn't available for purchase right now.")
                return@launch
            }

            when (val result = PlayBillingManager.launchPurchase(activity, productDetails)) {
                is PlayBillingManager.PurchaseResult.Success ->
                    verifyPlayPurchase(result.productId, result.purchaseToken)
                PlayBillingManager.PurchaseResult.UserCancelled ->
                    _state.value = _state.value.copy(isSubmitting = false)
                is PlayBillingManager.PurchaseResult.Error ->
                    _state.value = _state.value.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    private suspend fun verifyPlayPurchase(
        productId: String,
        purchaseToken: String,
    ) {
        try {
            val response =
                ApiClient.api.verifyPlayPurchase(
                    VerifyPlayPurchaseRequest(productId = productId, purchaseToken = purchaseToken),
                )
            val invoice = response.body()
            if (!response.isSuccessful || invoice == null) {
                _state.value =
                    _state.value.copy(
                        isSubmitting = false,
                        error = "Couldn't confirm your purchase (${response.code()}). It will be retried automatically.",
                    )
                return
            }

            // Acknowledge only now — after the backend has recorded the purchase.
            // Acknowledging first and having the verify call fail would leave a
            // purchase Play considers handled but the backend never granted.
            PlayBillingManager.acknowledge(purchaseToken)

            _state.value = _state.value.copy(isSubmitting = false)
            onSettled(invoice)
        } catch (e: Exception) {
            Log.e(TAG, "Play purchase verification failed", e)
            _state.value =
                _state.value.copy(
                    isSubmitting = false,
                    error = "Couldn't confirm your purchase. Check your connection — it will be retried automatically.",
                )
        }
    }

    /**
     * Polls the invoice until it settles or we stop waiting.
     *
     * The backend reconciles against the payment provider on each read, so this is
     * what makes a payment settle without webhooks. On the redirect branch the
     * invoice is usually already settled by the time we get here.
     */
    fun startPolling(invoiceId: String) {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                val startedAt = System.currentTimeMillis()
                while (true) {
                    val invoice = fetchInvoice(invoiceId)
                    val status = invoice?.status ?: _state.value.invoice?.status ?: PaymentStatusResolver.PROCESSING
                    val elapsed = System.currentTimeMillis() - startedAt

                    when (PaymentStatusResolver.decide(status, elapsed)) {
                        is PaymentStatusResolver.Decision.Settled -> {
                            onSettled(invoice ?: _state.value.invoice)
                            return@launch
                        }
                        PaymentStatusResolver.Decision.StoppedWaiting -> {
                            _state.value =
                                _state.value.copy(
                                    stoppedWaiting = true,
                                    statusMessage =
                                        "This is taking longer than usual. We'll confirm it as soon as " +
                                            "it comes through — you can safely close this screen.",
                                )
                            return@launch
                        }
                        PaymentStatusResolver.Decision.KeepPolling -> delay(PaymentStatusResolver.POLL_INTERVAL_MS)
                    }
                }
            }
    }

    private suspend fun fetchInvoice(invoiceId: String): InvoiceResponse? =
        try {
            val response = ApiClient.api.getInvoice(invoiceId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            // A dropped poll is not a failure — the loop simply tries again.
            Log.w(TAG, "Invoice poll failed, will retry", e)
            null
        }

    private suspend fun onSettled(invoice: InvoiceResponse?) {
        val status = invoice?.status.orEmpty()
        PaymentManager.clearPendingInvoiceId()

        val paid = status == PaymentStatusResolver.PAID
        if (paid) {
            // Refresh the cached tier and tell the rest of the app, or the user
            // would pay and see nothing change until their JWT next refreshed —
            // every isPremium-holding ViewModel reads tier once in init.
            refreshEntitlement()
            // Re-read the period too, so closing the sheet reveals a card that
            // already shows the new expiry and the extended coverage dates.
            loadSubscription()
        }

        _state.value =
            _state.value.copy(
                stage = CheckoutStage.SETTLED,
                invoice = invoice,
                isPaid = paid,
                statusMessage = PaymentStatusResolver.messageFor(status, invoice?.failureReason.orEmpty()),
            )
    }

    /**
     * Re-reads entitlement and notifies the rest of the app. Shared with
     * [SubscriptionResumer], which runs the same refresh at launch for a payment
     * completed while the app was closed.
     */
    private suspend fun refreshEntitlement() {
        SubscriptionResumer.refreshEntitlement()
    }

    /**
     * Picks up an invoice left in flight by a previous session and, if it is still
     * unsettled, resumes watching it on this screen.
     */
    fun resumePendingInvoice() {
        viewModelScope.launch {
            val pending = PaymentManager.getPendingInvoiceId() ?: return@launch
            val invoice = fetchInvoice(pending) ?: return@launch
            if (PaymentStatusResolver.isTerminal(invoice.status)) {
                onSettled(invoice)
            } else {
                // Still in flight — keep watching rather than dropping it.
                _state.value =
                    _state.value.copy(
                        stage = CheckoutStage.AWAITING_AUTHORIZATION,
                        invoice = invoice,
                    )
                startPolling(pending)
            }
        }
    }

    /** Called when the browser tab hands the customer back via the deep link. */
    fun onRedirectReturned(invoiceId: String) {
        _state.value = _state.value.copy(stage = CheckoutStage.AWAITING_AUTHORIZATION)
        startPolling(invoiceId)
    }

    fun retry() {
        pollJob?.cancel()
        _state.value =
            _state.value.copy(
                stage = CheckoutStage.FORM,
                invoice = null,
                instruction = "",
                redirectUrl = "",
                statusMessage = "",
                error = null,
                isPaid = false,
                stoppedWaiting = false,
            )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.UserLoggedOut -> {
                pollJob?.cancel()
                _state.value = SubscriptionUiState()
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    private fun checkoutErrorFor(code: Int): String =
        when (code) {
            403 -> "Card payments aren't enabled yet — please pay with mobile money."
            422 -> "Check your payment details and try again."
            503 -> "Payments are temporarily unavailable. Please try again shortly."
            else -> "Couldn't start the payment ($code)."
        }

    companion object {
        private const val TAG = "SubscriptionViewModel"
        private const val METHOD_MOBILE_MONEY = "mobile_money"
        private const val NEXT_ACTION_REDIRECT = "redirect_url"
    }
}
