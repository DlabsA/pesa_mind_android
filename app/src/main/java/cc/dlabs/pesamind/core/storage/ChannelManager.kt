package cc.dlabs.pesamind.core.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.features.settings.channels.ChannelDescMobileMoney
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import cc.dlabs.pesamind.features.settings.notifications.MessageSender
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.channelDataStore by preferencesDataStore("pesamind_channels")

/**
 * Vestigial as of ADR-0004 Slice A1: `ChannelViewModel` now reads/writes exclusively through
 * [ChannelRepository] (Room), so every DataStore-blob method below is dead from that side —
 * left `@Deprecated` rather than deleted pending Slice C's cleanup pass, per
 * `.claude/CLAUDE.md`'s "confirm with the user before deleting" rule. [isSmsAllowedForSender]
 * is the one method still genuinely called (from SMS ingestion, `SMSMessageProcessor` — Slice
 * A3's territory, not touched by A1 otherwise); its channel lookup/persistence were redirected
 * to [ChannelRepository] here so it doesn't see an increasingly stale channel list now that
 * nothing writes to this object's DataStore blob anymore. Its own network auto-create call is
 * unchanged — full "auto-create via Room, zero network" is Slice A3's job, not this fix's.
 */
object ChannelManager {
    private val CHANNELS_KEY = stringPreferencesKey("cached_channels")
    private val SMS_NOTIFICATION_FLAGS = stringPreferencesKey("sms_notification_flags")
    private val LAST_SYNC = stringPreferencesKey("channels_last_sync")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Save channels locally with SMS notification flags
     * For non-CASH channels, default SMS notifications to enabled
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — ChannelViewModel writes through ChannelRepository (Room) now.")
    suspend fun saveChannels(channels: List<ChannelDetails>) {
        if (!isInitialized()) return
        appContext.channelDataStore.edit { prefs ->
            // Store channels
            val channelsJson = Gson().toJson(channels)
            prefs[CHANNELS_KEY] = channelsJson

            // Store SMS notification flags for non-CASH channels
            val flags = mutableMapOf<String, Boolean>()
            channels.forEach { channel ->
                if (channel.channelType != "CASH") {
                    flags[channel.id] = channel.smsNotificationEnabled
                }
            }
            val flagsJson = Gson().toJson(flags)
            prefs[SMS_NOTIFICATION_FLAGS] = flagsJson

            // Update sync timestamp
            prefs[LAST_SYNC] = System.currentTimeMillis().toString()
        }
    }

    /**
     * Get all cached channels with their SMS notification settings
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — reads go through ChannelRepository (Room) now.")
    suspend fun getChannels(): List<ChannelDetails> {
        if (!isInitialized()) return emptyList()
        try {
            val data = appContext.channelDataStore.data.first()
            val channelsJson = data[CHANNELS_KEY] ?: return emptyList()
            val flagsJson = data[SMS_NOTIFICATION_FLAGS] ?: "{}"

            val channels =
                Gson().fromJson<List<ChannelDetails>>(
                    channelsJson,
                    object : TypeToken<List<ChannelDetails>>() {}.type,
                )

            @Suppress("UNCHECKED_CAST")
            val flags =
                Gson().fromJson<Map<String, Boolean>>(
                    flagsJson,
                    object : TypeToken<Map<String, Boolean>>() {}.type,
                ) as? Map<String, Boolean> ?: emptyMap()

            // Merge SMS notification flags back to channels
            return channels.map { channel ->
                if (channel.channelType != "CASH") {
                    channel.copy(smsNotificationEnabled = flags[channel.id] ?: true)
                } else {
                    channel
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Update SMS notification flag for a specific channel (non-CASH only)
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — ChannelViewModel.toggleSmsNotification writes through ChannelRepository (Room) now.")
    suspend fun updateChannelSmsNotification(
        channelId: String,
        enabled: Boolean,
    ) {
        if (!isInitialized()) return
        appContext.channelDataStore.edit { prefs ->
            val flagsJson = prefs[SMS_NOTIFICATION_FLAGS] ?: "{}"

            @Suppress("UNCHECKED_CAST")
            val flags =
                (
                    Gson().fromJson<Map<String, Boolean>>(
                        flagsJson,
                        object : TypeToken<Map<String, Boolean>>() {}.type,
                    ) as? Map<String, Boolean> ?: emptyMap()
                ).toMutableMap()

            flags[channelId] = enabled
            prefs[SMS_NOTIFICATION_FLAGS] = Gson().toJson(flags)
        }
    }

    /**
     * Get SMS notification flag for a specific channel
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — ChannelEntity.smsNotificationEnabled (Room) is the source of truth now.")
    suspend fun isSmsNotificationEnabled(channelId: String): Boolean {
        if (!isInitialized()) return false
        try {
            val data = appContext.channelDataStore.data.first()
            val flagsJson = data[SMS_NOTIFICATION_FLAGS] ?: "{}"

            @Suppress("UNCHECKED_CAST")
            val flags =
                Gson().fromJson<Map<String, Boolean>>(
                    flagsJson,
                    object : TypeToken<Map<String, Boolean>>() {}.type,
                ) as? Map<String, Boolean> ?: emptyMap()

            return flags[channelId] ?: true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Check if any channel with this sender ID has SMS notifications enabled
     */
    data class ChannelInfo(val channel: ChannelDetails, val enabled: Boolean)

    suspend fun isSmsAllowedForSender(
        receivingSimNumber: String,
        simInfo: Int,
        senderID: String,
    ): ChannelInfo? {
        if (!isInitialized()) return null

        val channelTypeMatched =
            when (senderID) {
                MessageSender.MTN_MOB_MONEY -> ChannelDescMobileMoney.MTNMOBILEMONEY
                MessageSender.AIRTEL_MONEY -> ChannelDescMobileMoney.AIRTELMONEY
                MessageSender.STANBIC_BANK -> ChannelTypes.BANK
                MessageSender.CENTENARY_BANK -> ChannelTypes.BANK
                else -> null // Return null if no match
            }

        // Room is the channel source of truth now (ADR-0004 Slice A1) — the old
        // getChannels()/saveChannels() DataStore round-trip here would see an increasingly
        // stale list, since ChannelViewModel no longer writes through it at all.
        val matchingChannel = channelTypeMatched?.let { ChannelRepository.findByChannelDesc(it) }

        // If found, return it
        if (matchingChannel != null) {
            return ChannelInfo(matchingChannel, true)
        }

        // Otherwise, create a new channel
        val (channelType, channelDesc) = determineChannelTypeAndDesc(senderID)
        if (channelType == null || channelDesc == null) {
            // Unrecognized sender – cannot auto‑create
            return null
        }

        return try {
            val request =
                CreateChannelRequest(
                    name = "Auto‑created $channelDesc",
                    description = receivingSimNumber,
                    channelType = channelType,
                    channelDesc = channelDesc,
                    status = true,
                )
            val response = ApiClient.api.createChannel(request)
            val createdBody = response.body()
            if (response.isSuccessful && createdBody != null) {
                // Reconcile into Room so this channel is visible to future Room-based
                // lookups (both this method's and ChannelViewModel's).
                ChannelInfo(ChannelRepository.reconcileFromServer(createdBody), true)
            } else {
                if (!response.isSuccessful) {
                    Log.e("ChannelManager", "Error creating channel for sender $senderID: ${response.errorBody()?.string()}")
                }
                null
            }
        } catch (e: Exception) {
            Log.e("ChannelManager", "Error creating channel for sender $senderID: ${e.message}", e)
            null
        }
    }

    private fun determineChannelTypeAndDesc(senderID: String): Pair<String?, String?> {
        val normalized = MessageSender.normalizeOrNull(senderID) ?: return Pair(null, null)
        return when (normalized) {
            MessageSender.MTN_MOB_MONEY -> Pair(ChannelTypes.MOBILE_MONEY, MessageSender.MTN_MOB_MONEY)
            MessageSender.AIRTEL_MONEY -> Pair(ChannelTypes.MOBILE_MONEY, MessageSender.AIRTEL_MONEY)
            MessageSender.STANBIC_BANK -> Pair(ChannelTypes.BANK, MessageSender.STANBIC_BANK)
            MessageSender.CENTENARY_BANK -> Pair(ChannelTypes.BANK, MessageSender.CENTENARY_BANK)
            else -> Pair(null, null)
        }
    }

    /**
     * Get last sync time
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — nothing writes LAST_SYNC anymore.")
    suspend fun getLastSyncTime(): Long {
        if (!isInitialized()) return 0
        try {
            val data = appContext.channelDataStore.data.first()
            return data[LAST_SYNC]?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            return 0
        }
    }

    @Deprecated("Dead since ADR-0004 Slice A1 — Room has no staleness concept; it's always current.")
    @Suppress("DEPRECATION")
    suspend fun isCacheStale(): Boolean {
        return SyncPolicy.isStale(getLastSyncTime())
    }

    /**
     * Clear all cached channels
     */
    @Deprecated("Dead since ADR-0004 Slice A1 — nothing reads this DataStore blob anymore.")
    suspend fun clearChannels() {
        if (!isInitialized()) return
        appContext.channelDataStore.edit {
            it.remove(CHANNELS_KEY)
            it.remove(SMS_NOTIFICATION_FLAGS)
            it.remove(LAST_SYNC)
        }
    }
}
