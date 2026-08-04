package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val label: @Composable () -> Unit = {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }

    if (trailingContent == null) {
        Box(modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) {
            label()
        }
    } else {
        // Stacked, not side-by-side: a long uppercase title (e.g. "TRANSACTION-BASED
        // INSIGHTS") plus a segmented toggle rarely both fit on one line without crowding or
        // overlapping on narrower screens, so the toggle gets its own row with clear spacing
        // below the label instead of fighting it for horizontal room.
        Column(modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
            label()
            Box(modifier = Modifier.padding(top = 10.dp)) {
                trailingContent()
            }
        }
    }
}
