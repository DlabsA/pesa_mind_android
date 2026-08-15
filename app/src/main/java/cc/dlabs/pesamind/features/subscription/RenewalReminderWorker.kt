package cc.dlabs.pesamind.features.subscription

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.storage.AccountManager
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Posts a local "your plan ends tomorrow" notification.
 *
 * Local rather than push: the app already knows the expiry date from the cached
 * profile, so a device-local WorkManager job needs no FCM setup, no device-token
 * storage, and no backend send path. The backend sends the matching *email*
 * (see the Go side's renewal sweep) — the two are deliberately belt and braces,
 * since this one still fires if SMTP is misconfigured, and the email still lands
 * if the user has notifications silenced.
 *
 * Not a `@HiltWorker`: it has nothing to inject, reaching `AccountManager` as a
 * singleton object exactly like the rest of this codebase.
 */
class RenewalReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Re-check on the way out. Renewing reschedules with REPLACE, which cancels
        // this job, so the case left to catch is a user who is no longer Premium at
        // all — logged out, or downgraded. Reminding them would be pure noise.
        val stillPremium =
            try {
                AccountManager.isPremium()
            } catch (e: Exception) {
                Log.w(TAG, "No account available; skipping renewal reminder", e)
                return Result.success()
            }
        if (!stillPremium) return Result.success()

        postReminder(applicationContext, inputData.getBoolean(KEY_IS_TRIAL, false))
        return Result.success()
    }

    private fun postReminder(
        context: Context,
        isTrial: Boolean,
    ) {
        // POST_NOTIFICATIONS is runtime-granted on API 33+; a denied permission is a
        // normal state, not an error.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification permission not granted; skipping renewal reminder")
            return
        }

        createChannel(context)

        val title =
            if (isTrial) "Your free trial ends tomorrow" else "Your Premium plan ends tomorrow"
        // No price here. Every other amount in the app comes from `getPlans()`, so a
        // literal in this string goes stale the moment the catalogue is edited — and
        // it already had: it promised UGX 4,000/month against a live monthly plan at
        // a different price. The subscription screen this deep-links to shows the
        // real figure one tap away.
        val body =
            if (isTrial) {
                "Automatic SMS capture switches off when your trial ends. Tap to keep it running."
            } else {
                "Renew to keep automatic SMS capture, full history and the complete analytics suite."
            }

        // Deep-links straight to the upgrade screen, so the notification is one tap
        // from the thing it's asking for.
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("pesamind://payment/upgrade")).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification blocked", e)
        }
    }

    private fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Subscription",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders before your plan ends"
            }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "RenewalReminderWorker"
        private const val CHANNEL_ID = "pesamind_subscription"
        private const val NOTIFICATION_ID = 4001
        private const val WORK_NAME = "pesamind_renewal_reminder"

        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_IS_TRIAL = "is_trial"

        /** How far ahead of expiry to warn. Matches the backend email sweep. */
        private val LEAD_TIME: Duration = Duration.ofHours(24)

        /**
         * Schedules (or reschedules) the reminder for [expiresAtIso].
         *
         * REPLACE, not KEEP: renewing moves the expiry, and the previously scheduled
         * reminder would otherwise fire against the old date. Re-scheduling on every
         * relevant change — login, JWT refresh, subscription activation — is what
         * keeps the pending job in step with reality.
         */
        fun schedule(
            context: Context,
            expiresAtIso: String?,
            isTrial: Boolean,
        ) {
            if (expiresAtIso.isNullOrBlank()) {
                cancel(context)
                return
            }

            val expiry =
                try {
                    OffsetDateTime.parse(expiresAtIso).toInstant()
                } catch (e: Exception) {
                    Log.w(TAG, "Unparseable expiry '$expiresAtIso'; not scheduling", e)
                    return
                }

            val fireAt = expiry.minus(LEAD_TIME)
            val delayMs = Duration.between(Instant.now(), fireAt).toMillis()
            if (delayMs <= 0) {
                // Already inside the last day (or past it) — a reminder now would be
                // noise, not help.
                cancel(context)
                return
            }

            val request =
                OneTimeWorkRequestBuilder<RenewalReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(
                        Data.Builder()
                            .putString(KEY_EXPIRES_AT, expiresAtIso)
                            .putBoolean(KEY_IS_TRIAL, isTrial)
                            .build(),
                    )
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
