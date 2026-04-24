package cc.dlabs.pesamind.features.settings.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import cc.dlabs.pesamind.core.storage.NotificationStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle SMS_RECEIVED action
        val smsReceivedAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        } else {
            "android.provider.Telephony.SMS_RECEIVED"
        }
        
        if (intent?.action != smsReceivedAction) return
        if (context == null) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (message in messages) {
                val senderNumber = message.originatingAddress ?: "Unknown"
                val messageBody = message.messageBody
                val timestamp = message.timestampMillis

                Log.d("SmsReceiver", "SMS received from: $senderNumber at $timestamp")

                // Process SMS locally
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NotificationStorage.init(context)

                        val processor = SMSMessageProcessor(context)
                        processor.processMessage(
                            senderId = senderNumber,
                            content = messageBody,
                            timestamp = timestamp
                        )
                        Log.d("SmsReceiver", "SMS processing completed for: $senderNumber")
                        Log.d("SmsReceiver", "SMS processing completed for: $messageBody")
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error processing SMS: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error in onReceive: ${e.message}", e)
        }
    }
}