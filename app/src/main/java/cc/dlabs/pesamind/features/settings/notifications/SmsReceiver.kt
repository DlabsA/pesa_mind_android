package cc.dlabs.pesamind.features.settings.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import cc.dlabs.pesamind.core.storage.NotificationStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private val scope = CoroutineScope(Dispatchers.IO) // ✅ Single reusable scope
    }

    override fun onReceive(context: Context?, intent: Intent?) {
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

            for (message in messages) {
                val senderNumber = message.originatingAddress ?: "Unknown"
                val messageBody = message.messageBody
                val timestamp = message.timestampMillis

                scope.launch {
                    try {
                        NotificationStorage.init(context)

                        val processor = SMSMessageProcessor(context)
                        processor.processMessage(
                            senderId = senderNumber,
                            content = messageBody,
                            timestamp = timestamp,
                            receivingSimSlot = receivingSimInfo.slotIndex,
                            receivingSimNumber = receivingSimInfo.phoneNumber
                        )

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
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
    private fun getReceivingSimInfo(context: Context, intent: Intent): SimInfo {
        return try {
            // Extract subscription ID from the SMS intent
            val subscriptionId: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID)
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

            val subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                ?: return SimInfo(slotIndex = -1, phoneNumber = "Unknown (sub: $subscriptionId)")

            val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: use the new permission-scoped method
                subscriptionManager.getPhoneNumber(subscriptionId)
            } else {
                subscriptionInfo.number ?: ""
            }

            SimInfo(
                slotIndex = subscriptionInfo.simSlotIndex,    // 0 = SIM 1, 1 = SIM 2
                phoneNumber = phoneNumber.ifBlank { "Not provisioned by carrier" }
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
        val phoneNumber: String
    ) {
        companion object {
            val UNKNOWN = SimInfo(slotIndex = -1, phoneNumber = "Unknown")
        }
    }
}