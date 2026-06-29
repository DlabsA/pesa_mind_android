package cc.dlabs.pesamind.features.settings.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import cc.dlabs.pesamind.R

/**
 * MessageMonitoringService runs as a foreground service to ensure the app
 * continues to monitor and handle SMS messages even when the main app UI
 * is closed. This is crucial for background SMS processing.
 *
 * The service:
 * - Starts on boot (via BootReceiver)
 * - Runs as a foreground service on Android 8+ (required to avoid being killed)
 * - Displays a persistent notification informing the user of active monitoring
 * - Allows the SmsReceiver to function reliably in the background
 */
class MessageMonitoringService : Service() {

    companion object {
        private const val TAG = "MessageMonitoringService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "pesamind_monitoring"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MessageMonitoringService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MessageMonitoringService started")
        
        // Start as foreground service (required on Android 8+)
        startForeground(NOTIFICATION_ID, buildNotification())

        // Return START_STICKY to ensure the service restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        Log.d(TAG, "MessageMonitoringService destroyed")
        super.onDestroy()
    }

    /**
     * Creates a notification channel for Android 8+ (required for foreground services).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Message Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors incoming SMS transactions"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent notification displayed while the service is running.
     * This informs the user that the app is actively monitoring SMS messages.
     */
    private fun buildNotification(): android.app.Notification {
        // Intent to open the app when notification is tapped
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PesaMind Active")
            .setContentText("Monitoring SMS transactions...")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Cannot be dismissed by user
            .setContentIntent(pendingIntent)
            .build()
    }
}




