package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnifiedScreenHeader(
    title: String,
    topLabel: String,
    bottomLabel: String? = null,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    isOffline: Boolean,
    streakDrawable: Int?,
    streakLabel: String,
    // When set, a back arrow renders in place of [topLabel]/greeting-style top text — the
    // sub-screen-with-back-button variant (BackStyleHeader) vs. the bottom-nav-tab-with-greeting
    // variant (DashboardStyleHeader) used by Dashboard/Budget/Analytics.
    onBack: (() -> Unit)? = null,
    // Extra trailing content (e.g. a delete/refresh IconButton, a tier-badge pill) rendered
    // after the refresh-spinner/offline-icon/streak slot above — same role as
    // DetailScreenTopBar's own trailingContent param, for screens migrating off that component.
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (onBack == null && topLabel.isNotBlank()) {
                Text(
                    text = topLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
            )
            if (!bottomLabel.isNullOrBlank()) {
                Text(
                    text = bottomLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                isRefreshing ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )

                isOffline ->
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )

                streakDrawable != null ->
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = streakDrawable),
                                contentDescription = "Streak",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified,
                            )
                            Text(
                                text = streakLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
            }
            trailingContent?.invoke()
        }
    }
}

/** Same visual shape as [DashboardStyleHeader] (used by Dashboard/Budget/Analytics) but for a
 * sub-screen reached via back-stack navigation: a back arrow instead of a greeting/top label.
 * Unlike Dashboard/Budget/Analytics — which render inside `MainScreen`'s content area, itself
 * already `.statusBarsPadding()`-wrapped once for every bottom-nav tab — a screen using this
 * header is pushed as its own full-screen destination outside that host, so it applies the inset
 * itself here rather than relying on a caller to remember to. */
@Composable
fun BackStyleHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomLabel: String? = null,
    isRefreshing: Boolean = false,
    isOffline: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    UnifiedScreenHeader(
        title = title,
        topLabel = "",
        bottomLabel = bottomLabel,
        onBack = onBack,
        isRefreshing = isRefreshing,
        isOffline = isOffline,
        streakDrawable = null,
        streakLabel = "",
        trailingContent = trailingContent,
        modifier = modifier.statusBarsPadding(),
    )
}

@Composable
fun DashboardStyleHeader(
    currentPeriodLabel: String,
    greetingText: String,
    isRefreshing: Boolean,
    isOffline: Boolean,
    streakDrawable: Int?,
    streakLabel: String,
    modifier: Modifier = Modifier,
) {
    UnifiedScreenHeader(
        title = greetingText,
        topLabel = currentPeriodLabel,
        isRefreshing = isRefreshing,
        isOffline = isOffline,
        streakDrawable = streakDrawable,
        streakLabel = streakLabel,
        modifier = modifier,
    )
}
