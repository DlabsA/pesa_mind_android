package cc.dlabs.pesamind.features.settings.notifications

import android.content.Context
import android.util.Log
import cc.dlabs.pesamind.core.network.models.SMSMessage
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SMSMessageProcessor(private val context: Context) {

    /**
     * Process incoming SMS message locally
     * 1. Validates message
     * 2. Checks channel notification settings
     * 3. Stores message locally
     * 4. Marks for sync with backend
     */
    suspend fun processMessage(
        senderId: String,
        content: String,
        timestamp: Long
    ) = withContext(Dispatchers.IO) {
        try {
            // Step 1: Validate sender and content
            if (senderId.isBlank() || content.isBlank()) {
                Log.w("SMSMessageProcessor", "Invalid message: sender=$senderId, content length=${content.length}")
                return@withContext
            }

            Log.d("SMSMessageProcessor", "Processing SMS from $senderId")

            // Step 2: Check if this sender/channel has SMS notifications enabled
            ChannelManager.init(context)
            val isAllowed = ChannelManager.isSmsAllowedForSender(senderId)
            
            if (!isAllowed) {
                Log.d("SMSMessageProcessor", "Message from $senderId blocked - SMS notifications disabled for this channel")
                return@withContext
            }

            // Step 3: Create local SMS message
            val smsMessage = SMSMessage(
                id = generateMessageId(senderId, timestamp),
                senderId = senderId,
                senderName = extractSenderName(senderId),
                content = content,
                timestamp = timestamp,
                isRead = false
            )

            // Step 4: Save locally
            val messageJson = Gson().toJson(smsMessage)
            NotificationStorage.savePendingMessage(messageJson)

            Log.d("SMSMessageProcessor", "Message saved locally: ${smsMessage.id}")

            // Step 5: Optional - Show local notification to user
            showLocalNotification(smsMessage)

        } catch (e: Exception) {
            Log.e("SMSMessageProcessor", "Error processing message: ${e.message}", e)
        }
    }

    private fun generateMessageId(senderId: String, timestamp: Long): String {
        return "${senderId}_${timestamp}_${System.nanoTime()}"
    }

    private fun extractSenderName(senderId: String): String {
        // Try to extract readable name from phone number or ID
        return if (senderId.contains("@")) {
            senderId.substringBefore("@")
        } else {
            senderId
        }
    }

    private fun showLocalNotification(message: SMSMessage) {
        // TODO: Implement local notification using NotificationManager
        // This shows a visual notification to the user immediately
        Log.d("SMSMessageProcessor", "Would show notification for: ${message.senderId}")
    }
}



