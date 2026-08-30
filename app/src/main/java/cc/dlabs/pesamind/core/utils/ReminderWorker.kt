package cc.dlabs.pesamind.core.utils

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
import androidx.work.WorkerParameters
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.data.DebtCreditRepository
import cc.dlabs.pesamind.core.data.SavingGoalRepository
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import cc.dlabs.pesamind.core.storage.AccountManager
import dagger.hilt.EntryPoints

private const val TAG = "ReminderWorker"
private const val CHANNEL_ID = "pesamind_lent_borrowed_goals"
private const val NOTIFICATION_ID_BASE = 5000

/**
 * Posts a single "due soon" notification for one debt/goal reminder offset — see
 * [ReminderScheduler] for the scheduling side. Not a `@HiltWorker`: reaches
 * [DebtCreditRepository]/[SavingGoalRepository]/[AccountManager] as plain singleton objects,
 * exactly like [cc.dlabs.pesamind.features.subscription.RenewalReminderWorker].
 *
 * Re-validates everything fresh from Room on the way out, never trusting [inputData]'s
 * snapshot — a lapsed subscription or a settlement/achievement that happened between
 * scheduling and fire time must suppress the notification. A reminder firing after
 * settlement/achievement is a trust-breaking bug, not a cosmetic one.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(ReminderScheduler.KEY_KIND) ?: return Result.failure()
        val entityId = inputData.getString(ReminderScheduler.KEY_ENTITY_ID) ?: return Result.failure()
        val offsetDays = inputData.getInt(ReminderScheduler.KEY_OFFSET_DAYS, -1)
        val deepLink = inputData.getString(ReminderScheduler.KEY_DEEP_LINK) ?: return Result.failure()

        val stillPremium =
            try {
                AccountManager.isPremium()
            } catch (e: Exception) {
                Log.w(TAG, "No account available; skipping reminder", e)
                return Result.success()
            }
        if (!stillPremium) return Result.success()

        val database = EntryPoints.get(applicationContext, DatabaseEntryPoint::class.java).database()

        when (kind) {
            ReminderScheduler.KIND_DEBT -> {
                val debt = database.debtCreditDao().getById(entityId) ?: return Result.success()
                if (debt.settledAt != null || debt.deletedAt != null) return Result.success()
                postReminder(
                    title = "Payment due soon",
                    body = "${debt.counterpartyName}'s ${directionNoun(
                        debt.direction,
                    )} is due in $offsetDays day${if (offsetDays != 1) "s" else ""}.",
                    deepLink = deepLink,
                    notificationId = NOTIFICATION_ID_BASE + entityId.hashCode(),
                )
            }
            ReminderScheduler.KIND_GOAL -> {
                val goal = database.savingGoalDao().getById(entityId) ?: return Result.success()
                if (goal.achievedAt != null || goal.deletedAt != null) return Result.success()
                postReminder(
                    title = "Saving goal due soon",
                    body = "\"${goal.name}\" is due in $offsetDays day${if (offsetDays != 1) "s" else ""}.",
                    deepLink = deepLink,
                    notificationId = NOTIFICATION_ID_BASE + entityId.hashCode(),
                )
            }
        }
        return Result.success()
    }

    private fun directionNoun(direction: String) = if (direction == "lent") "loan" else "repayment"

    private fun postReminder(
        title: String,
        body: String,
        deepLink: String,
        notificationId: Int,
    ) {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification permission not granted; skipping reminder")
            return
        }

        val channel =
            NotificationChannel(CHANNEL_ID, "Lent & Borrowed / Saving Goals", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for upcoming debt due dates and saving goal targets"
            }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification blocked", e)
        }
    }
}
