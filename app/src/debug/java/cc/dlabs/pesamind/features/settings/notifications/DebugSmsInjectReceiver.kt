package cc.dlabs.pesamind.features.settings.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.dlabs.pesamind.core.storage.NotificationStorage
import cc.dlabs.pesamind.core.utils.TransactionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-build-only entry point for manually exercising the SMS ingestion pipeline
 * ([SMSMessageProcessor.processMessage]) without a real `SMS_RECEIVED` broadcast — that
 * broadcast is protected (signature-permission) and can't be spoofed via `adb shell am
 * broadcast` on a non-rooted, production-signed device. Trigger with:
 *
 * ```
 * adb shell am broadcast -a cc.dlabs.pesamind.debug.INJECT_SMS \
 *   -n cc.dlabs.pesamind/cc.dlabs.pesamind.features.settings.notifications.DebugSmsInjectReceiver \
 *   --es sender "AirtelMoney" --es body "PAID.TID 1. UGX 100 to X" --el timestamp 1700000000000
 * ```
 *
 * Lives entirely under `src/debug` — never compiled into a release build.
 */
class DebugSmsInjectReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DebugSmsInjectReceiver"
        private val scope = CoroutineScope(Dispatchers.IO)
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (context == null || intent == null) return

        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        val pendingResult = goAsync()
        scope.launch {
            try {
                NotificationStorage.init(context)
                val processor = SMSMessageProcessor(context, TransactionViewModel())
                processor.processMessage(
                    senderId = sender,
                    content = body,
                    timestamp = timestamp,
                    simInfo = 0,
                    receivingSimNumber = "debug-inject",
                )
                Log.i(TAG, "Injected SMS from \"$sender\" processed")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing injected SMS: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
