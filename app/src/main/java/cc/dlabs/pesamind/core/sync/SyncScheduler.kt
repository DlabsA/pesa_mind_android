package cc.dlabs.pesamind.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Entry point for scheduling [SyncWorker] runs (ADR-0004 Slice A2). Three triggers:
 * periodic background sync, an expedited one-shot on connectivity regain
 * ([cc.dlabs.pesamind.PesaMindApp.onCreate]), and [triggerSyncNow] itself — exposed here as
 * the hook Slice A3's manual "refresh" UI action will call; no UI wiring in this slice.
 */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "pesamind_sync_periodic"
    private const val ONE_SHOT_WORK_NAME = "pesamind_sync_one_shot"
    private const val PERIODIC_INTERVAL_MINUTES = 30L

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        // KEEP: re-enqueuing this on every app launch must not reset an already-scheduled cadence.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Connectivity regain, or a future manual "refresh" tap (Slice A3). Coalesces
     * concurrent triggers into one run via [ExistingWorkPolicy.KEEP]. */
    fun triggerSyncNow(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
