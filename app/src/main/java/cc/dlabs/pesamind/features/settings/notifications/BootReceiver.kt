package cc.dlabs.pesamind.features.settings.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver listens for the BOOT_COMPLETED broadcast and starts the app
 * along with the MessageMonitoringService to ensure SMS monitoring runs
 * even when the app is not actively open.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        if (context == null) {
            Log.e(TAG, "Context is null in onReceive")
            return
        }

        Log.d(TAG, "Boot completed broadcast received — starting app and message monitoring service")

        try {
            // Start the message monitoring service to ensure continuous SMS monitoring
            val serviceIntent = Intent(context, MessageMonitoringService::class.java)
            context.startForegroundService(serviceIntent)

            // Also launch the main activity so the app is ready to use
            val launchIntent =
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }

            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Log.d(TAG, "Main activity started")
            } else {
                Log.w(TAG, "Could not create launch intent for package")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling boot completion: ${e.message}", e)
        }
    }
}
