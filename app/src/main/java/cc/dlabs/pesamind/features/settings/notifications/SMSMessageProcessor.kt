package cc.dlabs.pesamind.features.settings.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.data.ProcessedMessageRepository
import cc.dlabs.pesamind.core.network.models.SMSMessage
import cc.dlabs.pesamind.core.storage.ChannelManager
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.utils.TransactionCreationResult
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
    private val viewModel: TransactionViewModel,
) {
    // ── Notification channel constants ────────────────────────────────────────

    companion object {
        private const val TAG = "SMSMessageProcessor"

        // One channel per notification category — required on Android 8+
        const val CHANNEL_ID_TRANSACTIONS = "pesamind_transactions"
        const val CHANNEL_ID_ALERTS = "pesamind_alerts"

        // Stable IDs prevent notification flooding; derive from senderId so
        // MTN and Airtel each have their own slot that gets replaced, not stacked.
        private fun notificationId(senderId: String): Int = senderId.hashCode()
    }

    // ── Public entry point ────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun processMessage(
        senderId: String,
        content: String,
        timestamp: Long,
        simInfo: Int,
        receivingSimNumber: String,
    ) = withContext(Dispatchers.IO) {
        // Backend audit trail (offline-first, background-only — see ProcessedMessageRepository)
        // of every message this pipeline sees, regardless of what happens below. Own try/catch:
        // a failure recording it must never block the transaction-creation logic that follows.
        try {
            ProcessedMessageRepository.init(context)
            ProcessedMessageRepository.record(
                senderId = senderId,
                content = content,
                timestamp = timestamp,
                simInfo = simInfo,
                receivingSimNumber = receivingSimNumber,
                dedupeKey = "$senderId:$timestamp:${content.trim().hashCode()}",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record processed message (non-fatal)", e)
        }

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

            val parsed =
                when (normalizedSender) {
                    MessageSender.MTN_MOB_MONEY -> parseMTNMessage(content)
                    MessageSender.AIRTEL_MONEY -> parseAirtelMessage(content)
                    MessageSender.STANBIC_BANK -> parseStanbicMessage(content)
                    MessageSender.CENTENARY_BANK -> parseCentenaryMessage(content)
                    else -> null
                } ?: run {
                    Log.w(TAG, "Could not parse message content: $content")
                    return@withContext
                }
            val (amount, txType, parsedNote, providerTransactionId) = parsed

            val channelId = channelInfo.channel.id
            val finalNote = parsedNote.ifEmpty { content.take(255) }

            // Redelivery/reprocessing safety net for every sender, TID or not — two *different*
            // SMS bodies sharing one real transaction (confirmed: Airtel Uganda) are NOT caught
            // by this key, since it's derived from this exact message's own content; that's what
            // [providerTransactionId] exists to catch instead (see TransactionEntity's doc
            // comment). Redelivery of the *same* message (e.g. a service restart reprocessing a
            // queued broadcast) IS caught by this, since its inputs are identical both times.
            val smsSourceKey = "$normalizedSender:$timestamp:${content.trim().hashCode()}"

            // Awaited, not fire-and-forget: we must know the real outcome before telling
            // the user anything, and before persisting the pending-message record — both
            // used to happen unconditionally, which meant a user could get a "Spent X UGX"
            // notification for a transaction that never made it past a network exception.
            val result =
                viewModel.createTransactionAwaited(
                    channelID = channelId,
                    amount = amount,
                    type = txType,
                    note = finalNote,
                    smsSourceKey = smsSourceKey,
                    providerTransactionId = providerTransactionId,
                )

            when (result) {
                is TransactionCreationResult.Failure -> {
                    Log.w(TAG, "Transaction creation failed for SMS from $senderId: ${result.message}")
                    return@withContext
                }
                is TransactionCreationResult.Success -> {
                    if (result.wasDuplicate) {
                        // Real duplicate discarded by the atomic (channelId, providerTransactionId)
                        // or smsSourceKey unique index — logged with both raw bodies (this
                        // message's, and the already-recorded transaction's, which for
                        // MTN/Airtel is always its full raw SMS — see parseMTNMessage/
                        // parseAirtelMessage) so a discarded duplicate is diagnosable in
                        // production instead of silently invisible.
                        Log.i(
                            TAG,
                            "Discarded duplicate transaction from $senderId " +
                                "(providerTransactionId=$providerTransactionId, smsSourceKey=$smsSourceKey). " +
                                "This message: \"$content\" | Already-recorded message: " +
                                "\"${result.transaction?.note}\"",
                        )
                        return@withContext
                    }
                }
            }

            val smsMessage =
                SMSMessage(
                    id = generateMessageId(senderId, timestamp),
                    senderId = senderId,
                    senderName = extractSenderName(senderId),
                    content = content,
                    timestamp = timestamp,
                    isRead = false,
                )
            NotificationStorage.savePendingMessage(Gson().toJson(smsMessage))

            // Pass the parsed values so the notification can show a rich summary
            // Check POST_NOTIFICATIONS permission before calling showLocalNotification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    showLocalNotificationSafe(smsMessage, amount, txType)
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted — skipping notification")
                }
            } else {
                // On Android < 13, POST_NOTIFICATIONS is not required
                showLocalNotificationSafe(smsMessage, amount, txType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}", e)
        }
    }

    // ── Notification implementation ───────────────────────────────────────────

    /**
     * Wrapper for showLocalNotification that suppresses the lint warning.
     * This should only be called after verifying POST_NOTIFICATIONS permission.
     */
    @SuppressLint("MissingPermission")
    private fun showLocalNotificationSafe(
        message: SMSMessage,
        amount: Double,
        txType: String,
    ) = showLocalNotification(message, amount, txType)

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
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showLocalNotification(
        message: SMSMessage,
        amount: Double,
        txType: String,
    ) {
        ensureNotificationChannels()

        // Guard: POST_NOTIFICATIONS is a runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted — skipping notification")
                return
            }
        }

        val isExpense = txType == TYPE_EXPENSE
        val amountLabel = formatUgx(amount)
        val emoji = if (isExpense) "💸" else "💰"
        val verb = if (isExpense) "Spent" else "Received"

        // Title: "💸 Spent 45,000 UGX"  or  "💰 Received 120,000 UGX"
        val title = "$emoji $verb $amountLabel UGX"

        // Body: sender name + truncated raw SMS for context
        val body = "${message.senderName}: ${message.content.take(100)}"

        // Tap action — opens the app's main launcher Activity
        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    notificationId(message.senderId),
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
                .setSmallIcon(R.drawable.ic_notification) // provide a 24dp white-on-transparent icon
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // expand for long SMS
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true) // dismiss on tap
                .setContentIntent(pendingIntent)
                // Colour-code the notification LED / accent by transaction type
                .setColor(
                    ContextCompat.getColor(
                        context,
                        if (isExpense) R.color.expense_red else R.color.income_green,
                    ),
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
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Primary channel: every MTN / Airtel transaction
        if (manager.getNotificationChannel(CHANNEL_ID_TRANSACTIONS) == null) {
            NotificationChannel(
                CHANNEL_ID_TRANSACTIONS,
                "Transactions",
                NotificationManager.IMPORTANCE_HIGH,
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
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Budget alerts and financial health warnings"
            }.also { manager.createNotificationChannel(it) }
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    /** [providerTransactionId] is null unless a provider-specific TID pattern matched —
     * see [extractAirtelTid]'s doc comment for why MTN doesn't have one yet. */
    internal data class ParsedSms(
        val amount: Double,
        val type: String,
        val note: String,
        val providerTransactionId: String? = null,
    )

    internal fun parseMTNMessage(content: String): ParsedSms? {
        // No providerTransactionId extraction here: MTN's transaction-reference format hasn't
        // been confirmed against a real sample message (ADR-0004 Phase 0 measurement found
        // zero MTN SMS samples anywhere in this repo) — falling back to smsSourceKey-only
        // dedup for MTN rather than guessing a pattern, per this task's own instruction.
        val expensePatterns =
            listOf(
                Regex("has deducted UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("You have paid .+? UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("You have withdrawn UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("You have sent UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            )
        for (pattern in expensePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return ParsedSms(amount, TYPE_EXPENSE, content)
            }
        }

        val incomePatterns =
            listOf(
                Regex("You have received UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
            )
        for (pattern in incomePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return ParsedSms(amount, TYPE_INCOME, content)
            }
        }
        return null
    }

    internal fun parseAirtelMessage(content: String): ParsedSms? {
        val tid = extractAirtelTid(content)

        val expensePatterns =
            listOf(
                Regex("SENT\\.TID.*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("SENT UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("WITHDRAWN\\..*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("has collected UGX\\s*([\\d,]+(?:\\.[\\d]+)?)\\s*from your account", RegexOption.IGNORE_CASE),
                Regex("You have been debited UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("PAID\\.TID.*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)\\s*to", RegexOption.IGNORE_CASE),
            )
        for (pattern in expensePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return ParsedSms(amount, TYPE_EXPENSE, content, tid)
            }
        }

        val incomePatterns =
            listOf(
                Regex("CASH DEPOSIT of UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("RECEIVED UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("RECEIVED\\..*?UGX\\s*([\\d,]+(?:\\.[\\d]+)?)", RegexOption.IGNORE_CASE),
                Regex("Quickloan UGX\\s*([\\d,]+(?:\\.[\\d]+)?)\\s*deposited", RegexOption.IGNORE_CASE),
            )
        for (pattern in incomePatterns) {
            pattern.find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return ParsedSms(amount, TYPE_INCOME, content, tid)
            }
        }
        return null
    }

    /**
     * Parses Stanbic Bank Uganda transaction alerts, e.g. "Stanbic Bank Uganda : A transaction
     * of UGX 3,000,000.00, AccNr : XX5285 has been completed via HEAD OFFICE on 04/08/26
     * 08:47:56. IMMEDIATELY CALL 0800150150 TO REPORT PHONE THEFT!" (credit, no sign) or
     * "...UGX -480.00,..." (debit, leading '-'). The sign is the only type discriminator — no
     * separate credit/debit keyword is present in the message. No providerTransactionId: no
     * reference/TID field has been observed in any real Stanbic sample seen so far, same
     * limitation as [parseMTNMessage]. Other Stanbic SMS shapes (phone-theft-report footers,
     * CCN/OTP messages) contain no "UGX" amount and simply fail to match, same drop-and-log
     * behavior as any other unparseable message.
     */
    internal fun parseStanbicMessage(content: String): ParsedSms? {
        val match =
            Regex("A transaction of UGX\\s*(-?[\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE)
                .find(content) ?: return null
        val raw = match.groupValues[1].replace(",", "")
        val amount = raw.removePrefix("-").toDoubleOrNull() ?: return null
        val type = if (raw.startsWith("-")) TYPE_EXPENSE else TYPE_INCOME
        return ParsedSms(amount, type, content)
    }

    /**
     * Parses Centenary Bank transaction alerts, e.g. "CENTENARY: Dear REBECCA, a trxn of
     * -210,000 on your A/C **663 on 20-04-2026 at 17:43. Bal:296,349 (ATM WITHDRAWAL VISA
     * /Ebanking). Call 0800200555" (debit, leading '-') or "...a trxn of 471,500 on your A/C
     * ...(APRIL 2026 END OF MONTH PAY/Finance)..." (credit, no sign) — same sign-is-the-only-
     * discriminator shape as [parseStanbicMessage]. Also tolerates the "a Debit trxn of" variant
     * seen on some messages, and messages missing the "Bal:" field entirely (the amount capture
     * doesn't depend on it). No providerTransactionId — no reference/TID field observed in any
     * real Centenary sample seen so far, same limitation as [parseMTNMessage].
     *
     * Separately handles reversal messages, e.g. "Centenary. Dear MS. NAKINTU REBECCA, your last
     * transaction of 210,000 has been reversed successfully." — these don't match the "on your
     * A/C" shape above at all (no account/date/balance fields). Recorded as income for the
     * reversed amount rather than attempting to find-and-negate the original debit transaction:
     * matching two separate messages by amount alone would be fragile (nothing ties them
     * together but a coincidentally-equal figure), whereas recording the reversal as its own
     * income event keeps the net balance correct with no cross-message linking.
     */
    internal fun parseCentenaryMessage(content: String): ParsedSms? {
        Regex("a\\s+(?:Debit\\s+)?trxn of\\s*(-?[\\d,]+(?:\\.\\d+)?)\\s*on your A/C", RegexOption.IGNORE_CASE)
            .find(content)?.let { match ->
                val raw = match.groupValues[1].replace(",", "")
                val amount = raw.removePrefix("-").toDoubleOrNull() ?: return@let
                val type = if (raw.startsWith("-")) TYPE_EXPENSE else TYPE_INCOME
                return ParsedSms(amount, type, content)
            }

        Regex("your last transaction of\\s*([\\d,]+(?:\\.\\d+)?)\\s*has been reversed", RegexOption.IGNORE_CASE)
            .find(content)?.let { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@let
                return ParsedSms(amount, TYPE_INCOME, content)
            }

        return null
    }

    /**
     * Extracts Airtel Uganda's `TID` transaction reference, e.g. "...SENT.TID 123456.UGX..."
     * or "...TID: 123456...". Also matches the `Trans ID:` variant seen on some Airtel message
     * shapes (e.g. "...Trans ID:153569568145.") — same provider, inconsistent field label.
     * Tolerant of a colon or bare whitespace before the value, since
     * the only confirmed real-world evidence of this field's shape in this repo (the
     * pre-existing `SENT\.TID.*?UGX` amount regex above) shows `TID` and `UGX` co-occurring
     * in expense messages but doesn't itself capture the value or its exact delimiter — this
     * pattern is a reasonable first cut, not verified against a real Airtel sample message
     * (ADR-0004 Phase 0 measurement found none in this repo). Flagged in the ADR for
     * confirmation once real discarded-duplicate log lines are observed in production (see
     * the logging in [processMessage]).
     *
     * `\b` on both sides of `TID` and a `{4,}` minimum on the captured value are deliberate,
     * unverified-pattern safeguards, not evidence-backed specifics: this key feeds
     * [TransactionEntity.providerTransactionId]'s dedup, which *discards* an insert on a match
     * (`offline-sync-reviewer` flagged this) — a false extraction that happens to be constant
     * across genuinely different messages would silently and permanently discard every
     * subsequent real transaction on that channel, which is a worse failure mode than simply
     * failing to extract (which only falls back to the existing `smsSourceKey` dedup). The
     * `\b`s avoid matching `TID` as a substring of an unrelated word; the length floor avoids
     * treating a stray 1-3 character token as a transaction id.
     */
    internal fun extractAirtelTid(content: String): String? =
        Regex("\\b(?:TID|Trans\\s*ID)\\b[:\\s]+(\\w{4,})", RegexOption.IGNORE_CASE).find(content)?.groupValues?.get(1)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateMessageId(
        senderId: String,
        timestamp: Long,
    ): String = "${senderId}_${timestamp}_${System.nanoTime()}"

    private fun extractSenderName(senderId: String): String = if (senderId.contains("@")) senderId.substringBefore("@") else senderId

    private fun formatUgx(amount: Double): String = NumberFormat.getNumberInstance(Locale.US).format(amount.toLong())
}
