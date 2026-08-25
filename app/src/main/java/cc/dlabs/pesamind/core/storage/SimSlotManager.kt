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

    data class SimSlot(val slotIndex: Int, val carrierName: String)

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
                SimSlot(slotIndex = info.simSlotIndex, carrierName = carrierName)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read active SIM slots: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read active SIM slots: ${e.message}", e)
            emptyList()
        }
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
     * Persists the user-entered number for each active slot and (re)captures that slot's
     * current carrier name as the drift-detection baseline. Only writes entries present in
     * [numbersBySlot] — a slot the device doesn't currently have is never written.
     */
    suspend fun saveSlotNumbers(
        context: Context,
        numbersBySlot: Map<Int, String>,
    ) {
        if (!isInitialized()) return
        val activeSlots = getActiveSlots(context).associateBy { it.slotIndex }
        appContext.simSlotDataStore.edit { prefs ->
            numbersBySlot.forEach { (slotIndex, number) ->
                prefs[numberKey(slotIndex)] = number
                activeSlots[slotIndex]?.let { prefs[carrierKey(slotIndex)] = it.carrierName }
            }
            prefs[DriftDetected] = false
        }
        _driftBlocking.value = false
    }

    /**
     * Compares each stored slot's carrier snapshot against the live carrier reported for that
     * slot right now. Any mismatch means the physical SIM in that tray changed since the mapping
     * was last saved. Sets (and leaves set) [DriftDetected] until the user re-saves via
     * [saveSlotNumbers] — this is also the flag `SmsReceiver` checks before trusting the mapping,
     * so a detected drift immediately stops the fallback from being used.
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

        val driftFound =
            data.asMap().keys
                .mapNotNull { key ->
                    val name = key.name
                    if (!name.startsWith("slot_") || !name.endsWith("_carrier")) return@mapNotNull null
                    name.removePrefix("slot_").removeSuffix("_carrier").toIntOrNull()
                }
                .any { slotIndex ->
                    // liveCarrier == null covers the SIM having been removed entirely from
                    // that slot, not just swapped for a different carrier — either way the
                    // stored number is no longer trustworthy.
                    val storedCarrier = data[carrierKey(slotIndex)] ?: return@any false
                    val liveCarrier = liveSlots[slotIndex]?.carrierName
                    storedCarrier != liveCarrier
                }

        if (driftFound) {
            appContext.simSlotDataStore.edit { it[DriftDetected] = true }
        }
        return driftFound
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
