package cc.dlabs.pesamind.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

private const val TAG = "PaymentAuthorization"

/**
 * Opens a payment authorisation URL for the card 3DS branch.
 *
 * A Custom Tab rather than a WebView, deliberately: the customer is about to
 * authenticate with their bank, and a Custom Tab shows the real address bar and
 * padlock so they can see who they're talking to. A WebView shows neither, which
 * is exactly the shape of a credential-phishing screen — and it wouldn't reliably
 * render bank 3DS pages either.
 *
 * Uganda mobile money never gets here: it authorises with a PIN prompt pushed to
 * the customer's handset, so the app stays in the foreground and just polls.
 *
 * Falls back to whatever browser is installed if Custom Tabs is unavailable. The
 * manifest declares a `<queries>` intent for `https` VIEW so both can resolve a
 * handler on API 30+.
 */
fun openPaymentAuthorization(
    context: Context,
    url: String,
) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No Custom Tabs provider, falling back to a browser intent", e)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (fallback: ActivityNotFoundException) {
            // Nothing on the device can open a URL. The payment isn't lost — the
            // invoice keeps reconciling server-side and settles on next launch.
            Log.e(TAG, "No browser available to complete payment authorization", fallback)
        }
    }
}
