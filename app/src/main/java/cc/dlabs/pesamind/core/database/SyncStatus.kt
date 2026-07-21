package cc.dlabs.pesamind.core.database

/**
 * Where a syncable row stands relative to the server. See
 * docs/decisions/ADR-0004-offline-first.md for the full conflict policy this drives.
 *
 * PENDING -> SYNCING -> SYNCED on a successful push.
 * PENDING -> SYNCING -> FAILED on a 4xx (permanent, needs user action via [dirty]/retry).
 * SYNCING -> PENDING on a 5xx/timeout (worker will retry with backoff).
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
}
