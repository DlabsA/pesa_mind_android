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
 * Entry point for scheduling [SyncWorker] runs (ADR-0004 Slice A2). Triggers: periodic
 * background sync, an expedited one-shot on connectivity regain, login, JWT refresh, and
 * every screen's manual "refresh" action — see [triggerSyncNow]'s call sites.
 *
 * Caches its own [Context] via [init], the same pattern every other manager `object` in
 * this codebase uses ([cc.dlabs.pesamind.core.storage.ChannelManager],
 * [cc.dlabs.pesamind.core.storage.TokenManager], ...) — this is what lets plain
 * `ViewModel()`s (not `hiltViewModel()`-injected) and [cc.dlabs.pesamind.features.auth.AuthViewModel]
 * call [triggerSyncNow] without any DI wiring.
 */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "pesamind_sync_periodic"
    private const val ONE_SHOT_WORK_NAME = "pesamind_sync_one_shot"
    private const val PERIODIC_INTERVAL_MINUTES = 30L

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context = appContext) {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
        // KEEP: re-enqueuing this on every app launch must not reset an already-scheduled cadence.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Connectivity regain, login, JWT refresh, or a manual "refresh" tap. Coalesces
     * concurrent triggers into one run via [ExistingWorkPolicy.KEEP]. */
    fun triggerSyncNow(context: Context = appContext) {
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
