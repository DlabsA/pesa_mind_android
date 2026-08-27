package cc.dlabs.pesamind.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps Google Play Billing Library for subscription purchases.
 *
 * A plain singleton `object` with an `init(context)` call, matching this
 * codebase's existing pattern for cross-cutting managers ([cc.dlabs.pesamind
 * .core.storage.PaymentManager], [cc.dlabs.pesamind.features.subscription
 * .SubscriptionResumer]) rather than Hilt injection — [cc.dlabs.pesamind
 * .features.subscription.SubscriptionViewModel] is a plain `UnifiedViewModel`
 * built with `viewModel()`, not `@HiltViewModel`.
 *
 * This is the Play Store policy path: digital subscriptions sold inside this
 * (Play-distributed) app must go through Play's own billing, not Flutterwave.
 * Mobile money is unaffected and keeps talking to the backend directly.
 *
 * Every purchase this produces still has to be verified server-side — see
 * [cc.dlabs.pesamind.features.subscription.SubscriptionViewModel.launchPlayPurchase]
 * — a purchase token is never, on its own, evidence of entitlement.
 */
object PlayBillingManager {
    private const val TAG = "PlayBillingManager"

    sealed class PurchaseResult {
        data class Success(val productId: String, val purchaseToken: String) : PurchaseResult()

        data object UserCancelled : PurchaseResult()

        data class Error(val message: String) : PurchaseResult()
    }

    private lateinit var billingClient: BillingClient

    // Purchases delivered by the PurchasesUpdatedListener are matched back to the
    // in-flight launchPurchase() call via this single-slot deferred. Billing Library
    // only supports one purchase flow at a time per Activity, so a single slot
    // (rather than a per-call registry) is sufficient and simpler.
    private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            val deferred = pendingPurchase
            pendingPurchase = null
            if (deferred == null) return@PurchasesUpdatedListener

            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val purchase = purchases?.firstOrNull()
                    if (purchase == null) {
                        deferred.complete(PurchaseResult.Error("No purchase was returned"))
                    } else {
                        deferred.complete(
                            PurchaseResult.Success(
                                productId = purchase.products.firstOrNull().orEmpty(),
                                purchaseToken = purchase.purchaseToken,
                            ),
                        )
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    deferred.complete(PurchaseResult.UserCancelled)
                else ->
                    deferred.complete(PurchaseResult.Error(billingResult.debugMessage.ifBlank { "Purchase failed" }))
            }
        }

    fun init(context: Context) {
        if (::billingClient.isInitialized) return
        billingClient =
            BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesUpdatedListener)
                // Subscriptions only — no one-time or prepaid products are actually
                // sold. But Billing Library 8.x's PendingPurchasesParams.Builder throws
                // IllegalArgumentException ("Pending purchases for one-time products
                // must be supported") if enableOneTimeProducts() isn't called, even
                // though this app never queries/launches a one-time purchase — 8.x
                // requires it to be explicitly acknowledged regardless of product mix.
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                )
                .build()
    }

    /** Connects if needed, then returns once ready (or throws on failure to connect). */
    private suspend fun ensureConnected() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine<Unit> { cont ->
            billingClient.startConnection(
                object : com.android.billingclient.api.BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (cont.isActive) cont.resume(Unit)
                        } else if (cont.isActive) {
                            cont.cancel(
                                IllegalStateException(
                                    "Billing setup failed: ${billingResult.debugMessage}",
                                ),
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // launchPurchase/queryOfferings will retry the connection on
                        // their next call; nothing to do here.
                    }
                },
            )
        }
    }

    /** Looks up Play Console product details for the given subscription product IDs. */
    suspend fun queryOfferings(productIds: List<String>): List<com.android.billingclient.api.ProductDetails> {
        if (productIds.isEmpty()) return emptyList()
        ensureConnected()

        val products =
            productIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
            return emptyList()
        }
        return result.productDetailsList.orEmpty()
    }

    /**
     * Launches the Play purchase UI and suspends until the result comes back via
     * [PurchasesUpdatedListener]. Only one purchase can be in flight at a time.
     */
    suspend fun launchPurchase(
        activity: Activity,
        productDetails: com.android.billingclient.api.ProductDetails,
    ): PurchaseResult {
        ensureConnected()

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            return PurchaseResult.Error("No subscription offer is available for this plan")
        }

        val deferred = CompletableDeferred<PurchaseResult>()
        pendingPurchase = deferred

        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()

        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return PurchaseResult.Error(launchResult.debugMessage.ifBlank { "Couldn't start Play checkout" })
        }

        return deferred.await()
    }

    /**
     * Acknowledges a purchase after — and only after — the backend has confirmed it
     * settled the invoice. Play auto-refunds any subscription purchase left
     * unacknowledged for more than 3 days; acknowledging before backend confirmation
     * would let a purchase the backend never recorded still count as "handled."
     */
    suspend fun acknowledge(purchaseToken: String): Boolean {
        ensureConnected()
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        val result = billingClient.acknowledgePurchase(params)
        val ok = result.responseCode == BillingClient.BillingResponseCode.OK
        if (!ok) Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
        return ok
    }

    /**
     * Finds subscription purchases Play already recorded but this app never
     * finished handling — e.g. the process died between [launchPurchase] returning
     * and the backend verify call completing. Callers should re-run verification
     * (never just acknowledge) for anything returned here, the same as a fresh
     * purchase: an unverified token is not yet entitlement.
     */
    suspend fun unverifiedPurchases(): List<Purchase> {
        if (!::billingClient.isInitialized) return emptyList()
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryPurchasesAsync failed: ${result.billingResult.debugMessage}")
            return emptyList()
        }
        return result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
    }
}
