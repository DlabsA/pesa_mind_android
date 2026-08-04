package cc.dlabs.pesamind.core.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine

private val ALLOWED_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

sealed class BiometricAuthResult {
    data object Success : BiometricAuthResult()

    data class Error(val message: String) : BiometricAuthResult()

    data object Cancelled : BiometricAuthResult()
}

/** Gates whether a "Use biometrics" affordance should even be shown — hardware absent or
 * nothing enrolled both mean the OS prompt would just fail immediately. */
fun isBiometricAvailable(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * [BIOMETRIC_STRONG] only, deliberately no `DEVICE_CREDENTIAL` fallback — this app's own
 * PIN/pattern (see [cc.dlabs.pesamind.core.storage.TokenManager]) are a separate secret store
 * from the OS device-lock credential, and offering "enter your device PIN" here would
 * conflate two different PINs. The prompt's negative button routes the user back to this
 * app's own PIN/pattern entry, which is already on screen.
 */
suspend fun authenticateWithBiometrics(
    activity: FragmentActivity,
    title: String = "Unlock Pesa Mind",
    subtitle: String = "Use your fingerprint or face to continue",
): BiometricAuthResult =
    suspendCancellableCoroutine { continuation ->
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(BiometricAuthResult.Success) {}
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (!continuation.isActive) return
                        val result =
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            ) {
                                BiometricAuthResult.Cancelled
                            } else {
                                BiometricAuthResult.Error(errString.toString())
                            }
                        continuation.resume(result) {}
                    }

                    override fun onAuthenticationFailed() {
                        // A single bad scan — not terminal; the system prompt stays open and
                        // lets the user retry. Only onAuthenticationError ends the flow.
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use PIN/Pattern instead")
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
        prompt.authenticate(promptInfo)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
