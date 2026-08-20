package cc.dlabs.pesamind.features.settings.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.storage.SimSlotManager
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val scope = CoroutineScope(Dispatchers.IO) // ✅ Single reusable scope
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (context == null) return

        // ✅ Call goAsync() so Android keeps the process alive while we do async work
        val pendingResult = goAsync()

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                pendingResult.finish()
                return
            }

            val receivingSimInfo = getReceivingSimInfo(context, intent)

            // Track how many coroutines are running to know when to finish
            val pendingJobs = AtomicInteger(messages.size)
            val jobs = mutableListOf<Job>()

            for (message in messages) {
                val senderNumber = message.originatingAddress ?: "Unknown"
                val messageBody = message.messageBody
                val timestamp = message.timestampMillis

                val job =
                    scope.launch {
                        try {
                            NotificationStorage.init(context)

                            // The OS couldn't resolve this slot's own MSISDN (carrier never
                            // provisioned EF_MSISDN — see SimInfo.NOT_PROVISIONED). Fall back to
                            // the user-declared slot mapping (Account Settings > SIM Slots),
                            // unless a SIM swap was detected since that mapping was set — a
                            // stale mapping must never be trusted, so it's left unresolved
                            // instead until the user re-confirms it.
                            val resolvedSimNumber =
                                if (SimInfo.isPlaceholderNumber(receivingSimInfo.phoneNumber) &&
                                    !SimSlotManager.isDriftDetected()
                                ) {
                                    SimSlotManager.getNumberForSlot(receivingSimInfo.slotIndex)
                                        ?: receivingSimInfo.phoneNumber
                                } else {
                                    receivingSimInfo.phoneNumber
                                }

                            val processor = SMSMessageProcessor(context, TransactionViewModel())
                            processor.processMessage(
                                senderId = senderNumber,
                                content = messageBody,
                                timestamp = timestamp,
                                simInfo = receivingSimInfo.slotIndex,
                                receivingSimNumber = resolvedSimNumber,
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing SMS: ${e.message}", e)
                        } finally {
                            // Only finish when all jobs are complete
                            if (pendingJobs.decrementAndGet() == 0) {
                                pendingResult.finish()
                            }
                        }
                    }
                jobs.add(job)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
            pendingResult.finish()
        }
    }

    /**
     * Resolves the SIM card that received the incoming SMS.
     *
     * Android delivers the subscription ID in the intent extras.
     * We use [SubscriptionManager] to look up the human-readable
     * slot index and phone number for that subscription.
     *
     * ⚠️ Requires: READ_PHONE_STATE (API < 31) or READ_PHONE_NUMBERS (API 31+)
     * ⚠️ Some carriers do NOT provision the number on the SIM — phoneNumber may be empty.
     */
    @SuppressLint("MissingPermission")
    private fun getReceivingSimInfo(
        context: Context,
        intent: Intent,
    ): SimInfo {
        return try {
            // Extract subscription ID from the SMS intent
            val subscriptionId: Int =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    intent.getIntExtra(
                        SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                }

            if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                Log.w(TAG, "Could not determine subscription ID from intent")
                return SimInfo.UNKNOWN
            }

            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager

            val subscriptionInfo =
                subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                    ?: return SimInfo(slotIndex = -1, phoneNumber = "Unknown (sub: $subscriptionId)")

            val subscriptionManagerNumber =
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // API 33+: use the new permission-scoped method
                        subscriptionManager.getPhoneNumber(subscriptionId)
                    } else {
                        subscriptionInfo.number ?: ""
                    }
                } catch (e: SecurityException) {
                    // Logged (not silenced) so a real permission gap on a specific device is
                    // visible in logcat instead of looking identical to a genuine
                    // not-provisioned-by-carrier case.
                    Log.w(TAG, "SubscriptionManager number lookup denied for sub=$subscriptionId: ${e.message}")
                    ""
                }

            val phoneNumber =
                subscriptionManagerNumber.ifBlank {
                    // Second attempt: SubscriptionManager and TelephonyManager read from the
                    // same carrier-provisioned record on most devices, so this is not
                    // guaranteed to succeed where the first call didn't — but it's a free
                    // extra chance (no new permission; READ_PHONE_STATE already covers
                    // getLine1Number pre-29, and it's carrier/OEM-privilege gated on 29+
                    // regardless) before we give up and show the "not provisioned" fallback.
                    try {
                        val fromTelephonyManager =
                            context.getSystemService(TelephonyManager::class.java)
                                ?.createForSubscriptionId(subscriptionId)
                                ?.line1Number
                                .orEmpty()
                        if (fromTelephonyManager.isBlank()) {
                            Log.w(
                                TAG,
                                "TelephonyManager.line1Number also blank for sub=$subscriptionId " +
                                    "— carrier has not provisioned an MSISDN for this SIM",
                            )
                        }
                        fromTelephonyManager
                    } catch (e: SecurityException) {
                        Log.w(TAG, "TelephonyManager.line1Number denied for sub=$subscriptionId: ${e.message}")
                        ""
                    }
                }

            SimInfo(
                // 0 = SIM 1, 1 = SIM 2
                slotIndex = subscriptionInfo.simSlotIndex,
                phoneNumber = phoneNumber.ifBlank { SimInfo.NOT_PROVISIONED },
            )
        } catch (e: SecurityException) {
            // READ_PHONE_STATE / READ_PHONE_NUMBERS permission not granted
            Log.w(TAG, "Missing permission to read SIM info: ${e.message}")
            SimInfo.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve SIM info: ${e.message}", e)
            SimInfo.UNKNOWN
        }
    }

    /**
     * Lightweight data holder for the receiving SIM card's identity.
     * @param slotIndex  Physical SIM slot: 0-based (0 = SIM 1, 1 = SIM 2).
     * @param phoneNumber The MSISDN of the SIM, if provisioned by the carrier.
     */
    data class SimInfo(
        val slotIndex: Int,
        val phoneNumber: String,
    ) {
        companion object {
            const val NOT_PROVISIONED = "Not provisioned by carrier"
            val UNKNOWN = SimInfo(slotIndex = -1, phoneNumber = "Unknown")

            /** True for any [SimInfo.phoneNumber] value that isn't a real MSISDN — used by
             * [cc.dlabs.pesamind.core.storage.ChannelManager] to know when a channel's stored
             * number is safe to overwrite once a later message resolves a real one. */
            fun isPlaceholderNumber(value: String): Boolean =
                value.isBlank() || value == UNKNOWN.phoneNumber || value == NOT_PROVISIONED ||
                    value.startsWith("Unknown (sub:")
        }
    }
}
