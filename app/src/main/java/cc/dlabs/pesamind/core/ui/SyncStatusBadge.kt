package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.database.SyncStatus
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * Compact pill surfacing a Room row's [SyncStatus] on a list card (transactions/channels) —
 * ADR-0004 Slice A3's sync-status UI. Renders nothing for [SyncStatus.SYNCED] (the common case;
 * a fully-synced row shouldn't add visual noise to every card), so callers can place it
 * unconditionally next to a title without an extra `if` at each call site.
 *
 * [SyncStatus.FAILED] (a terminal 4xx per `SyncWorker` — never auto-retried, see ADR-0004's
 * Slice A2 section) is deliberately given the error color, distinct from
 * [SyncStatus.PENDING]/[SyncStatus.SYNCING] (both just "hasn't reached the server yet, but
 * will"), so a user can tell "this needs my attention" from "this is still catching up."
 */
@Composable
fun SyncStatusBadge(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    if (status == SyncStatus.SYNCED) return

    // Three independent `when`s instead of destructuring a Triple<ImageVector, String, Color> —
    // Triple's generic type parameters box the inline-value-class Color into a plain Any on
    // every non-SYNCED recomposition (`compose-perf` flagged this); assigning each val directly
    // avoids that allocation entirely.
    val icon =
        when (status) {
            SyncStatus.PENDING -> Icons.Filled.CloudQueue
            SyncStatus.SYNCING -> Icons.Filled.Sync
            SyncStatus.FAILED -> Icons.Filled.CloudOff
            SyncStatus.SYNCED -> return
        }
    val label =
        when (status) {
            SyncStatus.PENDING -> "Pending sync"
            SyncStatus.SYNCING -> "Syncing"
            SyncStatus.FAILED -> "Sync failed"
            SyncStatus.SYNCED -> return
        }
    val color =
        when (status) {
            SyncStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            SyncStatus.SYNCING -> MaterialTheme.colorScheme.tertiary
            SyncStatus.FAILED -> MaterialTheme.colorScheme.error
            SyncStatus.SYNCED -> return
        }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Space2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space1.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
