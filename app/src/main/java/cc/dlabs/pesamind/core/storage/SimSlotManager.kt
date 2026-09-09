package cc.dlabs.pesamind.core.storage

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.simSlotDataStore by preferencesDataStore("pesamind_sim_slots")

/**
 * User-declared "SIM slot N holds this phone number" mapping, purely local to this device.
 *
 * Exists only as a fallback for [cc.dlabs.pesamind.features.settings.notifications.SmsReceiver]
 * when the OS itself can't resolve a SIM's own MSISDN (some carriers never provision
 * `EF_MSISDN` on the SIM — see [cc.dlabs.pesamind.features.settings.notifications.SmsReceiver.SimInfo.NOT_PROVISIONED]).
 * Nothing else in the app reads this mapping. Never synced to the backend: which physical SIM
 * tray holds which number is a property of this phone, not the user's account.
 */
object SimSlotManager {
    private const val TAG = "SimSlotManager"

    private fun numberKey(slotIndex: Int) = stringPreferencesKey("slot_${slotIndex}_number")

    private fun carrierKey(slotIndex: Int) = stringPreferencesKey("slot_${slotIndex}_carrier")

    private fun subscriptionIdKey(slotIndex: Int) = intPreferencesKey("slot_${slotIndex}_subscription_id")

    private val DriftDetected = booleanPreferencesKey("drift_detected")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * True while a detected-but-unresolved SIM change should block normal app use — read by
     * `PesaMindNavGraph`'s app-wide non-dismissible overlay. Set by [refreshDriftBlockingState]
     * (called from `MainActivity` on every launch/resume, not just cold start — Android 14+
     * only allows a SIM-swap check while the app is actively open, never in the background), and
     * cleared by [saveSlotNumbers] the moment the user resolves it.
     */
    private val _driftBlocking = MutableStateFlow(false)
    val driftBlocking: StateFlow<Boolean> = _driftBlocking.asStateFlow()

    /**
     * The single entry point `MainActivity` calls on every launch/resume: runs [checkForDrift],
     * fires the notification only when drift is *newly* found this call (matching
     * [checkForDrift]'s own already-flagged short-circuit, so re-opening the app while an
     * earlier drift is still unresolved doesn't re-spam the notification), then syncs
     * [driftBlocking] to the persisted flag — which re-arms the block on every resume for as
     * long as it stays unresolved, independent of whether this particular call found anything
     * new.
     */
    suspend fun refreshDriftBlockingState(context: Context) {
        val newlyDetected = checkForDrift(context)
        if (newlyDetected) postDriftAlert(context)
        _driftBlocking.value = isDriftDetected()
    }

    /**
     * @param subscriptionId The platform's per-SIM subscription ID, or `null` when the OS reports
     *   no valid one. This — not [carrierName] — is what makes a SIM *identifiable*: the platform
     *   keys it off the SIM's ICCID and re-issues the same value for the same physical SIM across
     *   reboots and re-insertions, so it changes exactly when the SIM in a tray is a different
     *   card. ICCID itself is unavailable here: `SubscriptionInfo.getIccId()` returns an empty
     *   string on API 30+ without carrier privileges or `READ_PRIVILEGED_PHONE_STATE`, neither of
     *   which a normal app can hold.
     */
    data class SimSlot(
        val slotIndex: Int,
        val carrierName: String,
        val subscriptionId: Int? = null,
    )

    /**
     * The persisted "this is the SIM that was in this tray when the user confirmed the mapping"
     * snapshot, compared against the live slots by [detectDrift].
     *
     * [subscriptionId] is nullable because installs predating identity-based drift detection only
     * stored a carrier name — see [detectDrift] for how that legacy state is handled.
     */
    internal data class SlotBaseline(
        val carrierName: String,
        val subscriptionId: Int?,
    )

    /**
     * Live read of the device's currently active SIM slots. Empty on a single-SIM device with
     * no second slot, on permission denial, or on any other failure — mirrors
     * `SmsReceiver.getReceivingSimInfo`'s defensive `SecurityException` handling exactly, since
     * this is the same OS surface (`SubscriptionManager`) queried the same permission-guarded
     * way (`READ_PHONE_STATE`/`READ_PHONE_NUMBERS`, already declared and requested app-wide).
     */
    @SuppressLint("MissingPermission")
    fun getActiveSlots(context: Context): List<SimSlot> {
        return try {
            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return emptyList()
            subscriptionManager.activeSubscriptionInfoList.orEmpty().map { info ->
                val carrierName =
                    info.carrierName?.toString()?.ifBlank { null }
                        ?: info.displayName?.toString()?.ifBlank { null }
                        ?: "SIM ${info.simSlotIndex + 1}"
                SimSlot(
                    slotIndex = info.simSlotIndex,
                    carrierName = carrierName,
                    // Normalised to null here so nothing downstream has to know the platform's
                    // sentinel — an unusable ID and an absent one are the same thing to the
                    // drift comparison.
                    subscriptionId =
                        info.subscriptionId
                            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read active SIM slots: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read active SIM slots: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Slot indices this device has ever persisted a mapping for, regardless of whether the OS
     * currently reports that slot as active. The SIM-slot form falls back to these when
     * [getActiveSlots] comes back empty — a removed/swapped SIM or a denied `READ_PHONE_STATE`
     * would otherwise leave the drift dialog with no number fields at all, i.e. no way to resolve
     * the very block it puts up.
     */
    suspend fun getStoredSlotIndices(): List<Int> {
        if (!isInitialized()) return emptyList()
        return appContext.simSlotDataStore.data.first().asMap().keys
            .mapNotNull { key ->
                val name = key.name
                if (!name.startsWith("slot_")) return@mapNotNull null
                name.removePrefix("slot_").substringBefore('_').toIntOrNull()
            }
            .distinct()
            .sorted()
    }

    /** The carrier name captured for [slotIndex] the last time its mapping was saved. */
    suspend fun getCarrierForSlot(slotIndex: Int): String? {
        if (!isInitialized()) return null
        return appContext.simSlotDataStore.data.first()[carrierKey(slotIndex)]?.ifBlank { null }
    }

    /** The fallback lookup `SmsReceiver` uses when the OS-resolved number is a placeholder. */
    suspend fun getNumberForSlot(slotIndex: Int): String? {
        if (!isInitialized()) return null
        return appContext.simSlotDataStore.data.first()[numberKey(slotIndex)]?.ifBlank { null }
    }

    /** Test-only reset — production code never needs to wipe this mapping wholesale. */
    suspend fun clearAll() {
        if (!isInitialized()) return
        appContext.simSlotDataStore.edit { it.clear() }
    }

    suspend fun isDriftDetected(): Boolean {
        if (!isInitialized()) return false
        return appContext.simSlotDataStore.data.first()[DriftDetected] ?: false
    }

    /**
     * Persists the user-entered number for each slot in [numbersBySlot] and re-captures the
     * drift-detection baseline as *the whole live device state right now* — saving is the user
     * declaring "this is the current truth", so afterwards the stored baseline must describe
     * exactly the SIMs sitting in the trays at this moment, with nothing left over.
     *
     * That means two things beyond writing numbers:
     * - every currently-active slot gets its carrier/subscription baseline rewritten, whether or
     *   not the user typed a number for it;
     * - every *stored* slot the device no longer reports gets its baseline dropped.
     *
     * The second is what stops the app-wide drift block from becoming unresolvable. [detectDrift]
     * treats a baselined slot that's missing from the live list as drift, so a removed SIM (or a
     * denied `READ_PHONE_STATE`, which makes [getActiveSlots] come back empty) used to leave that
     * slot's stale baseline in place through every save — `MainActivity`'s next resume re-ran
     * [checkForDrift], found the same missing slot, and re-raised the block. Forever. The saved
     * *number* is kept either way; only the identity snapshot behind it is cleared.
     */
    suspend fun saveSlotNumbers(
        context: Context,
        numbersBySlot: Map<Int, String>,
    ) {
        if (!isInitialized()) return
        val activeSlots = getActiveSlots(context).associateBy { it.slotIndex }
        appContext.simSlotDataStore.edit { prefs ->
            numbersBySlot.forEach { (slotIndex, number) -> prefs[numberKey(slotIndex)] = number }

            // Snapshot before mutating — the key set is live over the edit block.
            val storedSlotIndices =
                prefs.asMap().keys
                    .mapNotNull { key ->
                        val name = key.name
                        if (!name.startsWith("slot_")) return@mapNotNull null
                        name.removePrefix("slot_").substringBefore('_').toIntOrNull()
                    }
                    .distinct()

            (storedSlotIndices + activeSlots.keys).distinct().forEach { slotIndex ->
                val slot = activeSlots[slotIndex]
                if (slot == null) {
                    // Not in any tray right now: drop the identity snapshot so it can't keep
                    // re-triggering drift against a slot we can no longer see.
                    prefs.remove(carrierKey(slotIndex))
                    prefs.remove(subscriptionIdKey(slotIndex))
                } else {
                    prefs[carrierKey(slotIndex)] = slot.carrierName
                    // A slot whose subscription ID the OS won't report keeps no stale ID from a
                    // previous save — that would compare a fresh carrier snapshot against an old
                    // identity and flag drift that didn't happen.
                    val subscriptionId = slot.subscriptionId
                    if (subscriptionId != null) {
                        prefs[subscriptionIdKey(slotIndex)] = subscriptionId
                    } else {
                        prefs.remove(subscriptionIdKey(slotIndex))
                    }
                }
            }
            prefs[DriftDetected] = false
        }
        _driftBlocking.value = false
    }

    /**
     * Compares each stored slot's baseline against the SIM sitting in that tray right now. Any
     * mismatch means the physical SIM changed since the mapping was last saved. Sets (and leaves
     * set) [DriftDetected] until the user re-saves via [saveSlotNumbers] — this is also the flag
     * `SmsReceiver` checks before trusting the mapping, so a detected drift immediately stops the
     * fallback from being used.
     *
     * Returns whether drift was newly found this call, so the caller (an app-startup check) knows
     * whether to fire a fresh alert rather than re-notifying every launch.
     */
    suspend fun checkForDrift(context: Context): Boolean {
        if (!isInitialized()) return false
        val liveSlots = getActiveSlots(context).associateBy { it.slotIndex }
        val data = appContext.simSlotDataStore.data.first()

        val alreadyFlagged = data[DriftDetected] ?: false
        if (alreadyFlagged) return false

        val baselines =
            data.asMap().keys
                .mapNotNull { key ->
                    val name = key.name
                    if (!name.startsWith("slot_") || !name.endsWith("_carrier")) return@mapNotNull null
                    name.removePrefix("slot_").removeSuffix("_carrier").toIntOrNull()
                }
                .mapNotNull { slotIndex ->
                    val carrierName = data[carrierKey(slotIndex)] ?: return@mapNotNull null
                    slotIndex to
                        SlotBaseline(
                            carrierName = carrierName,
                            subscriptionId = data[subscriptionIdKey(slotIndex)],
                        )
                }
                .toMap()

        val driftFound = detectDrift(baselines, liveSlots)

        if (driftFound) {
            appContext.simSlotDataStore.edit { it[DriftDetected] = true }
            return true
        }

        // Backfill the identity baseline for installs that saved their mapping before this check
        // existed (carrier name only). Safe precisely because drift was not found: the carrier
        // still matches, so this captures the SIM the user already confirmed rather than silently
        // blessing a swapped one. Without it those installs would stay identity-blind until the
        // user happened to re-save.
        //
        // Computed before opening `edit` so the common case — every baseline already carries an
        // ID — skips the write entirely: this runs on every launch *and* resume, and DataStore
        // rewrites the file even for an edit block that changes nothing.
        val backfill =
            baselines
                .filterValues { it.subscriptionId == null }
                .mapNotNull { (slotIndex, _) ->
                    liveSlots[slotIndex]?.subscriptionId?.let { slotIndex to it }
                }
        if (backfill.isNotEmpty()) {
            appContext.simSlotDataStore.edit { prefs ->
                backfill.forEach { (slotIndex, subscriptionId) ->
                    prefs[subscriptionIdKey(slotIndex)] = subscriptionId
                }
            }
        }
        return false
    }

    /**
     * The pure comparison behind [checkForDrift], split out so the swap cases can be tested
     * without standing up a live `SubscriptionManager`.
     *
     * A slot drifts when *either* signal changes:
     * - **Subscription ID** — the real identity check. Catches a same-carrier swap (two Safaricom
     *   lines trading trays), which a carrier-name comparison alone cannot see: both slots still
     *   report "Safaricom" while their numbers have silently traded places. That case matters most
     *   here, because the mapping this guards is only ever *used* when the carrier didn't
     *   provision an MSISDN — and a carrier that doesn't provision one for the first SIM won't for
     *   the second either.
     * - **Carrier name** — the fallback for the legacy/unavailable-ID cases below, and a
     *   belt-and-braces check if the platform ever reissues an ID across different SIMs.
     *
     * A missing ID on *either* side degrades to carrier-only comparison rather than counting as a
     * mismatch: a `null` baseline ID means an install that predates this check, and a `null` live
     * ID means the OS won't tell us. Treating either as drift would block every upgrading user on
     * first launch. That leaves one uncovered window — a same-carrier swap performed *before*
     * upgrading, whose baseline is backfilled as though nothing moved — which is the pre-existing
     * behaviour, not a regression, and closes as soon as [checkForDrift] backfills the ID.
     */
    internal fun detectDrift(
        baselines: Map<Int, SlotBaseline>,
        liveSlots: Map<Int, SimSlot>,
    ): Boolean =
        baselines.any { (slotIndex, baseline) ->
            // A slot that's gone entirely (SIM removed, not swapped) is drift too — the stored
            // number is no longer backed by anything in that tray.
            val live = liveSlots[slotIndex] ?: return@any true

            val baselineId = baseline.subscriptionId
            val liveId = live.subscriptionId
            if (baselineId != null && liveId != null && baselineId != liveId) return@any true

            baseline.carrierName != live.carrierName
        }

    /**
     * Local "your SIM cards may have changed" alert, fired once per newly-detected drift.
     * Mirrors `RenewalReminderWorker`'s notification pattern (same channel-creation/
     * `NotificationCompat`/deep-link shape) — this codebase's one existing precedent for a
     * "come back into the app and fix something" alert.
     */
    fun postDriftAlert(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification permission not granted; skipping SIM slot drift alert")
            return
        }

        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SIM slots",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Alerts when a SIM card in a tracked slot changes" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("pesamind://account/simslots")).apply {
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

        val body = "We noticed a SIM card change. Please confirm which number is on which SIM slot."
        val notification =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SIM cards may have changed")
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

    private const val NOTIFICATION_CHANNEL_ID = "pesamind_sim_slots"
    private const val NOTIFICATION_ID = 4101
}
