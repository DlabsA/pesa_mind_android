package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.channelDataStore by preferencesDataStore("pesamind_channels")

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
    suspend fun getChannels(): List<ChannelDetails> {
        if (!isInitialized()) return emptyList()
        try {
            val data = appContext.channelDataStore.data.first()
            val channelsJson = data[CHANNELS_KEY] ?: return emptyList()
            val flagsJson = data[SMS_NOTIFICATION_FLAGS] ?: "{}"

            val channels = Gson().fromJson<List<ChannelDetails>>(
                channelsJson,
                object : TypeToken<List<ChannelDetails>>() {}.type
            )

            @Suppress("UNCHECKED_CAST")
            val flags = Gson().fromJson<Map<String, Boolean>>(
                flagsJson,
                object : TypeToken<Map<String, Boolean>>() {}.type
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
    suspend fun updateChannelSmsNotification(channelId: String, enabled: Boolean) {
        if (!isInitialized()) return
        appContext.channelDataStore.edit { prefs ->
            val flagsJson = prefs[SMS_NOTIFICATION_FLAGS] ?: "{}"

            @Suppress("UNCHECKED_CAST")
            val flags = (Gson().fromJson<Map<String, Boolean>>(
                flagsJson,
                object : TypeToken<Map<String, Boolean>>() {}.type
            ) as? Map<String, Boolean> ?: emptyMap()).toMutableMap()

            flags[channelId] = enabled
            prefs[SMS_NOTIFICATION_FLAGS] = Gson().toJson(flags)
        }
    }

    /**
     * Get SMS notification flag for a specific channel
     */
    suspend fun isSmsNotificationEnabled(channelId: String): Boolean {
        if (!isInitialized()) return false
        try {
            val data = appContext.channelDataStore.data.first()
            val flagsJson = data[SMS_NOTIFICATION_FLAGS] ?: "{}"

            @Suppress("UNCHECKED_CAST")
            val flags = Gson().fromJson<Map<String, Boolean>>(
                flagsJson,
                object : TypeToken<Map<String, Boolean>>() {}.type
            ) as? Map<String, Boolean> ?: emptyMap()

            return flags[channelId] ?: true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Check if any channel with this sender ID has SMS notifications enabled
     */
    suspend fun isSmsAllowedForSender(senderId: String): Boolean {
        if (!isInitialized()) return false
        try {
            val channels = getChannels()
            val matchingChannel = channels.find { 
                it.channelType != "CASH" && 
                (it.name.equals(senderId, ignoreCase = true) || 
                 it.description.contains(senderId, ignoreCase = true))
            }
            return matchingChannel?.smsNotificationEnabled ?: false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get last sync time
     */
    suspend fun getLastSyncTime(): Long {
        if (!isInitialized()) return 0
        try {
            val data = appContext.channelDataStore.data.first()
            return data[LAST_SYNC]?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            return 0
        }
    }

    /**
     * Clear all cached channels
     */
    suspend fun clearChannels() {
        if (!isInitialized()) return
        appContext.channelDataStore.edit {
            it.remove(CHANNELS_KEY)
            it.remove(SMS_NOTIFICATION_FLAGS)
            it.remove(LAST_SYNC)
        }
    }
}