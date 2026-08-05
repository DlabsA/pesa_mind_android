package cc.dlabs.pesamind.features.blog

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedStateCoordinator
import cc.dlabs.pesamind.core.data.BlogRepository
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.RegisterDeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BlogMessagingService"
private const val CHANNEL_ID_BLOG = "blog_posts"

/**
 * Always-on listener for the backend's "new finance post published" push (see
 * [cc.dlabs.pesamind.core.network.models.BlogPostResponse]'s doc comment for the expected
 * backend contract this depends on) — same "always listening, parse the incoming message"
 * shape as [cc.dlabs.pesamind.features.settings.notifications.SmsReceiver], except the
 * transport here is FCM rather than the SMS broadcast.
 *
 * Deliberately doesn't try to render the post content from the push payload itself: the
 * payload is just a signal, [BlogRepository.refreshPosts] is the one path that ever writes a
 * [cc.dlabs.pesamind.core.database.entity.BlogPostEntity] row, so a push and a manual
 * pull-to-refresh can never disagree about what's cached.
 */
class BlogMessagingService : FirebaseMessagingService() {
    /** Sent whenever FCM (re)issues this device a token — must reach the backend for it to be
     * able to target this device at all, so this fires on every app install/restore/token
     * rotation, not just once. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.api.registerDeviceToken(RegisterDeviceTokenRequest(token = token))
                if (!response.isSuccessful) {
                    Log.w(TAG, "registerDeviceToken failed: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "registerDeviceToken failed", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BlogRepository.refreshPosts()
                UnifiedStateCoordinator.publishEvent(StateEvent.BlogPostsRefreshed)
            } catch (e: Exception) {
                Log.w(TAG, "refreshPosts after push failed", e)
            }
        }
        showLocalNotification(
            title = message.notification?.title ?: message.data["title"] ?: "New finance post",
            body = message.notification?.body ?: message.data["body"].orEmpty(),
        )
    }

    /** Mirrors [cc.dlabs.pesamind.features.settings.notifications.SMSMessageProcessor]'s
     * `showLocalNotification`/`ensureNotificationChannels` pair — same permission gate, same
     * tap-into-launcher-Activity behavior, separate channel since this isn't a transaction alert. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showLocalNotification(
        title: String,
        body: String,
    ) {
        ensureNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted — skipping notification")
                return
            }
        }

        val launchIntent =
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }

        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID_BLOG)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        NotificationManagerCompat.from(this).notify(CHANNEL_ID_BLOG.hashCode(), notification)
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID_BLOG) == null) {
            NotificationChannel(
                CHANNEL_ID_BLOG,
                "Finance Blog",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New finance write-ups"
            }.also { manager.createNotificationChannel(it) }
        }
    }
}
