package cc.dlabs.pesamind.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The single place the app asks for — and reasons about — the permissions that make automatic
 * digital-channel tracing work.
 *
 * This is the `SmsTracingPermissions` that `MainActivity` has referred to in a comment since
 * v37 without it ever existing. When the blanket request was pulled out of `MainActivity.onCreate`
 * (commit 75d0f4d) the replacement was never written, so from v37 onward the app declared
 * `RECEIVE_SMS` in the manifest and then **never requested it at runtime** — `SmsReceiver` could
 * therefore never fire on a fresh install. That is precisely the "requested permissions do not
 * match core functionality / unable to verify core functionality" pair that Google Play rejected
 * the release for: a reviewer installing the app was never prompted, so the declared core feature
 * was unreachable.
 *
 * Two rules this file exists to keep:
 *  1. **In context, never on launch.** The request is made from the screen that explains it
 *     (the onboarding SMS-access step, or the dashboard banner), not from a cold `onCreate` —
 *     Play's Permissions policy asks for incremental, in-context requests.
 *  2. **Prominent disclosure first.** [SmsTracingDisclosureDialog] must be shown and accepted
 *     before the system prompt is launched. Do not call the launcher directly; drive it through
 *     [SmsTracingPermissionState.request].
 */
object SmsTracingPermissions {
    /**
     * The one permission the transaction pipeline genuinely cannot work without. `READ_SMS` is
     * deliberately absent — the pipeline only ever reads the body of a broadcast as it arrives,
     * never the inbox (see the manifest comment).
     */
    const val CORE: String = Manifest.permission.RECEIVE_SMS

    /**
     * Everything requested alongside [CORE] in the same system prompt. All optional: denying any
     * of them degrades a detail (which SIM an alert arrived on, or the local notification) but
     * still leaves tracing working.
     */
    fun supporting(): List<String> =
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_PHONE_NUMBERS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    fun all(): List<String> = listOf(CORE) + supporting()

    /** True once tracing can actually run — i.e. [CORE] is granted. */
    fun isGranted(context: Context): Boolean = isGranted(context, CORE)

    fun missing(context: Context): List<String> = all().filterNot { isGranted(context, it) }

    private fun isGranted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Once the user has ticked "Don't ask again", the system prompt is a no-op and the only way
     * back is the app's own settings page.
     */
    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /**
     * Whether the system will still show a prompt for [CORE], or has permanently suppressed it.
     * Only meaningful after at least one denial, which is why callers gate on
     * [SmsTracingPermissionState.hasBeenAsked] before treating `true` as "go to Settings".
     */
    fun isPermanentlyDenied(activity: Activity): Boolean = !isGranted(activity) && !activity.shouldShowRequestPermissionRationale(CORE)
}
