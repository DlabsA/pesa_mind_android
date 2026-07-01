package cc.dlabs.pesamind.core.storage

object SyncPolicy {
    const val AUTO_REFRESH_WINDOW_MS: Long = 5 * 60 * 1000L

    fun isStale(lastSyncMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastSyncMs <= 0L) return true
        return (nowMs - lastSyncMs) > AUTO_REFRESH_WINDOW_MS
    }
}

