package cc.dlabs.pesamind.core.storage

data class StreakSnapshot(
    val count: Int,
    val lastActiveDate: String?,
)

object StreakSessionCache {
    @Volatile
    private var snapshot: StreakSnapshot? = null

    fun get(): StreakSnapshot? = snapshot

    fun set(count: Int, lastActiveDate: String?) {
        snapshot = StreakSnapshot(count = count, lastActiveDate = lastActiveDate)
    }

    fun clear() {
        snapshot = null
    }
}

