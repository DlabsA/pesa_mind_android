package cc.dlabs.pesamind.core.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Screen-local handle on the digital-channel tracing permissions: whether they're granted, and
 * the disclosure-then-prompt sequence that asks for them.
 *
 * This is transient UI state (a dialog's visibility, a snapshot of a system-owned flag), not
 * server/business state, so it lives in Compose state rather than a `StateFlow` on a ViewModel —
 * the ViewModel could not observe a grant made in system Settings anyway. The grant is re-read on
 * every `ON_RESUME` precisely so that returning from the Settings page updates the UI.
 */
@Stable
class SmsTracingPermissionState internal constructor(
    private val context: Context,
    private val launch: (Array<String>) -> Unit,
) {
    /** True once tracing can run. Re-read on resume, so it reflects changes made outside the app. */
    var isGranted by mutableStateOf(SmsTracingPermissions.isGranted(context))
        internal set

    /** Whether the prominent disclosure is currently on screen. */
    var isDisclosureVisible by mutableStateOf(false)
        private set

    /** Set once the system prompt has come back at least once, so denial can be interpreted. */
    var hasBeenAsked by mutableStateOf(false)
        internal set

    /**
     * True when the system will no longer show a prompt and the user has to grant from Settings.
     * Meaningless before the first ask, hence the [hasBeenAsked] gate — a never-asked permission
     * also reports "no rationale needed".
     */
    val isPermanentlyDenied: Boolean
        get() =
            hasBeenAsked && !isGranted &&
                context.findActivity()?.let { SmsTracingPermissions.isPermanentlyDenied(it) } == true

    /**
     * Entry point for every caller. Shows the prominent disclosure first — Play's Permissions
     * policy requires the explanation to precede the system prompt — or jumps straight to Settings
     * when the prompt has been permanently suppressed.
     */
    fun request() {
        when {
            isGranted -> Unit
            isPermanentlyDenied -> SmsTracingPermissions.openAppSettings(context)
            else -> isDisclosureVisible = true
        }
    }

    /** "Allow" on the disclosure — the only path that launches the system prompt. */
    fun onDisclosureAccepted() {
        isDisclosureVisible = false
        launch(SmsTracingPermissions.all().toTypedArray())
    }

    /** "Not now" on the disclosure. Declining here never launches a system prompt. */
    fun onDisclosureDismissed() {
        isDisclosureVisible = false
    }

    internal fun refresh() {
        isGranted = SmsTracingPermissions.isGranted(context)
    }
}

/**
 * Remembers a [SmsTracingPermissionState] for the calling screen and keeps its `isGranted`
 * snapshot honest across app resumes.
 */
@Composable
fun rememberSmsTracingPermissionState(): SmsTracingPermissionState {
    val context = LocalContext.current

    // The launcher and the state each need the other, so the state is handed an indirection it
    // can call once the launcher exists rather than a forward reference to it.
    val launchRef = remember { mutableStateOf<((Array<String>) -> Unit)?>(null) }
    val state = remember(context) { SmsTracingPermissionState(context) { launchRef.value?.invoke(it) } }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            state.hasBeenAsked = true
            state.refresh()
        }
    SideEffect { launchRef.value = { permissions -> launcher.launch(permissions) } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) state.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
