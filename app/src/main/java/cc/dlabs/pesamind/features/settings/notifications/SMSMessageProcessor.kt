package cc.dlabs.pesamind.features.settings.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.network.models.SMSMessage
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import cc.dlabs.pesamind.features.home.TYPE_EXPENSE
import cc.dlabs.pesamind.features.home.TYPE_INCOME
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class SMSMessageProcessor(
    private val context: Context,
    private val viewModel: TransactionViewModel
) {

    // ── Notification channel constants ────────────────────────────────────────

    companion object {
        private const val TAG = "SMSMessageProcessor"

        // One channel per notification category — required on Android 8+
        const val CHANNEL_ID_TRANSACTIONS = "pesamind_transactions"
        const val CHANNEL_ID_ALERTS       = "pesamind_alerts"

        // Stable IDs prevent notification flooding; derive from senderId so
        // MTN and Airtel each have their own slot that gets replaced, not stacked.
        private fun notificationId(senderId: String): Int = senderId.hashCode()
    }

    // ── Public entry point ────────────────────────────────────────────────────

    suspend fun processMessage(
        senderId: String,
        content: String,
        timestamp: Long,
        simInfo: Int,
        receivingSimNumber: String
    ) = withContext(Dispatchers.IO) {
        try {
            if (senderId.isBlank() || content.isBlank()) {
                Log.w(TAG, "Empty sender or content")
                return@withContext
            }

            val normalizedSender = MessageSender.normalizeOrNull(senderId)
            if (normalizedSender == null) {
                Log.w(TAG, "Unknown sender: $senderId")
                return@withContext
            }

            ChannelManager.init(context)
            val channelInfo = ChannelManager.isSmsAllowedForSender(receivingSimNumber, simInfo, normalizedSender)
            if (channelInfo == null || !channelInfo.enabled) {
                Log.d(TAG, "Message blocked: notifications disabled or channel creation failed")
                return@withContext
            }

            val (amount, txType, parsedNote) = when (normalizedSender) {
                MessageSender.MTNMobMoney -> parseMTNMessage(content)
                MessageSender.airtelmoney -> parseAirtelMessage(content)
                else -> null
            } ?: run {
                Log.w(TAG, "Could not parse message content: $content")
                return@withContext
            }

            val channelId = channelInfo.channel.id
            val finalNote = parsedNote.ifEmpty { content.take(255) }

            viewModel.CreateTransaction(
                channelID = channelId,
                amount    = amount,
                type      = txType,
                note      = finalNote
            )

            val smsMessage = SMSMessage(
                id         = generateMessageId(senderId, timestamp),
                senderId   = senderId,
                senderName = extractSenderName(senderId),
                content    = content,
                timestamp  = timestamp,
                isRead     = false
            )
            NotificationStorage.savePendingMessage(Gson().toJson(smsMessage))

            // Pass the parsed values so the notification can show a rich summary
            showLocalNotification(smsMessage, amount, txType)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}", e)
        }
    }

    // ── Notification implementation ───────────────────────────────────────────

    /**
     * Shows a rich Android notification for the processed transaction.
     *
     * Design decisions:
     * - Creates notification channels lazily here (safe to call repeatedly —
     *   the system ignores duplicate channel registrations).
     * - Uses a deterministic notification ID derived from the senderId so that
     *   back-to-back messages from the same sender update the existing
     *   notification rather than stacking indefinitely.
     * - Respects POST_NOTIFICATIONS permission gate on Android 13+.
     * - Tapping the notification deep-links to the main Activity; swap the
     *   Intent target for a dedicated TransactionDetailActivity if you add one.
     */
    private fun showLocalNotification(
        message: SMSMessage,
        amount: Double,
        txType: String
    ) {
        ensureNotificationChannels()

        // Guard: POST_NOTIFICATIONS is a runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted — skipping notification")
                return
            }
        }

        val isExpense   = txType == TYPE_EXPENSE
        val amountLabel = formatUgx(amount)
        val emoji       = if (isExpense) "💸" else "💰"
        val verb        = if (isExpense) "Spent" else "Received"

        // Title: "💸 Spent 45,000 UGX"  or  "💰 Received 120,000 UGX"
        val title = "$emoji $verb $amountLabel UGX"

        // Body: sender name + truncated raw SMS for context
        val body = "${message.senderName}: ${message.content.take(100)}"

        // Tap action — opens the app's main launcher Activity
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId(message.senderId),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_notification)   // provide a 24dp white-on-transparent icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))  // expand for long SMS
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)        // dismiss on tap
            .setContentIntent(pendingIntent)
            // Colour-code the notification LED / accent by transaction type
            .setColor(
                ContextCompat.getColor(
                    context,
                    if (isExpense) R.color.expense_red else R.color.income_green
                )
            )
            .build()

        NotificationManagerCompat.from(context)
            .notify(notificationId(message.senderId), notification)

        Log.d(TAG, "Notification posted: $title")
    }

    /**
     * Creates the notification channels required on Android 8+.
     * Safe to call multiple times — the OS ignores re-registration of
     * an already-existing channel with the same ID.
     */
    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Primary channel: every MTN / Airtel transaction
        if (manager.getNotificationChannel(CHANNEL_ID_TRANSACTIONS) == null) {
            NotificationChannel(
                CHANNEL_ID_TRANSACTIONS,
                "Transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming and outgoing mobile money transactions"
                enableLights(true)
                enableVibration(true)
            }.also { manager.createNotificationChannel(it) }
        }

        // Secondary channel: budget alerts, anomalies, recommendations
        if (manager.getNotificationChannel(CHANNEL_ID_ALERTS) == null) {
            NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Budget alerts and financial health warnings"
            }.also { manager.createNotificationChannel(it) }
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun parseMTNMessage(content: String): Triple<Double, String, String>? {
        val expensePatterns = listOf(
            Regex("has deducted UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("You have paid .+? UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("You have withdrawn UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("You have sent UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE)
        )
        for (pattern in expensePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return Triple(amount, TYPE_EXPENSE, content)
            }
        }

        val incomePatterns = listOf(
            Regex("You have received UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE)
        )
        for (pattern in incomePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return Triple(amount, TYPE_INCOME, content)
            }
        }
        return null
    }

    private fun parseAirtelMessage(content: String): Triple<Double, String, String>? {
        val expensePatterns = listOf(
            Regex("SENT UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("WITHDRAWN\\..*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("You have been debited UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE)
        )
        for (pattern in expensePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return Triple(amount, TYPE_EXPENSE, content)
            }
        }

        val incomePatterns = listOf(
            Regex("CASH DEPOSIT of UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("RECEIVED UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            Regex("RECEIVED\\..*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE)
        )
        for (pattern in incomePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return Triple(amount, TYPE_INCOME, content)
            }
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateMessageId(senderId: String, timestamp: Long): String =
        "${senderId}_${timestamp}_${System.nanoTime()}"

    private fun extractSenderName(senderId: String): String =
        if (senderId.contains("@")) senderId.substringBefore("@") else senderId

    private fun formatUgx(amount: Double): String =
        NumberFormat.getNumberInstance(Locale.US).format(amount.toLong())
}