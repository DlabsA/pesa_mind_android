package cc.dlabs.pesamind.core.utils

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Schedules local "due soon" notifications for Lent & Borrowed debts and Saving Goals, one job
 * per (kind, entityId, offsetDays) triple — not one shared work name like
 * [cc.dlabs.pesamind.features.subscription.RenewalReminderWorker]'s single subscription, since
 * a debt/goal can have several user-customizable reminder offsets and there are many
 * concurrently-open debts/goals.
 *
 * Every enqueued request is tagged `"pesamind_${kind}_${entityId}"` so [cancelAll] can clear
 * the whole set for one entity without needing to know which offsets were previously
 * scheduled — critical for [scheduleAll] itself, which always cancels-then-re-adds, and for
 * settle/achieve transitions, which only have the entity id on hand.
 */
object ReminderScheduler {
    const val KIND_DEBT = "debt"
    const val KIND_GOAL = "goal"

    // internal, not private: ReminderWorker (same package, different file — Kotlin's top-level
    // `private` is file-scoped, not package-scoped) reads these to parse its inputData.
    internal const val KEY_KIND = "kind"
    internal const val KEY_ENTITY_ID = "entity_id"
    internal const val KEY_OFFSET_DAYS = "offset_days"
    internal const val KEY_DEEP_LINK = "deep_link"

    private fun tag(
        kind: String,
        entityId: String,
    ) = "pesamind_${kind}_$entityId"

    private fun workName(
        kind: String,
        entityId: String,
        offsetDays: Int,
    ) = "pesamind_${kind}_reminder_${entityId}_$offsetDays"

    /**
     * Schedules one job per offset in [offsets], each firing [offsetDays] before
     * [dueOrTargetAtMillis]. Always cancels every previously-scheduled job for this
     * (kind, entityId) first — safe (and required) to call unconditionally on every
     * create/update, since a shrunk offsets list must not leave orphaned jobs behind.
     * A null [dueOrTargetAtMillis] (no due/target date set) or a past offset is simply
     * not scheduled, never fired immediately.
     */
    fun scheduleAll(
        context: Context,
        kind: String,
        entityId: String,
        dueOrTargetAtMillis: Long?,
        offsets: List<Int>,
        deepLink: String,
    ) {
        cancelAll(context, kind, entityId)
        if (dueOrTargetAtMillis == null) return
        val dueOrTargetAt = Instant.ofEpochMilli(dueOrTargetAtMillis)
        val workManager = WorkManager.getInstance(context)
        offsets.forEach { days ->
            val fireAt = dueOrTargetAt.minus(Duration.ofDays(days.toLong()))
            val delayMs = Duration.between(Instant.now(), fireAt).toMillis()
            if (delayMs <= 0) return@forEach
            val request =
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(
                        Data.Builder()
                            .putString(KEY_KIND, kind)
                            .putString(KEY_ENTITY_ID, entityId)
                            .putInt(KEY_OFFSET_DAYS, days)
                            .putString(KEY_DEEP_LINK, deepLink)
                            .build(),
                    )
                    .addTag(tag(kind, entityId))
                    .build()
            workManager.enqueueUniqueWork(workName(kind, entityId, days), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelAll(
        context: Context,
        kind: String,
        entityId: String,
    ) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(kind, entityId))
    }
}
