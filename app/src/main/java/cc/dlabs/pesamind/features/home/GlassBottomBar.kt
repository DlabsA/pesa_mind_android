package cc.dlabs.pesamind.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Matches your existing nav item shape (route / icon / label). */

/**
 * Glass, expanding-pill bottom bar.
 *
 * The selected item grows to a pill showing a filled (primary) icon circle + label;
 * unselected items stay as compact tinted circles. Uses theme primary/onPrimary so the
 * forest/lime light-dark flip is respected automatically.
 *
 * NOTE ON THE "GLASS": a translucent surface only reads as glass if there is content
 * *behind* it. See the integration note — prefer overlaying this over your content
 * (Box) rather than the Scaffold `bottomBar` slot, which reserves space and leaves
 * nothing behind the bar to show through.
 */
@Composable
fun GlassBottomBar(
    navController: NavController,
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .shadow(24.dp, RoundedCornerShape(36.dp), clip = false)
                    .clip(RoundedCornerShape(36.dp))
                    // base translucent tint (the "glass" body)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                    // top sheen so the edge catches light like frosted glass
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.White.copy(alpha = 0.04f),
                                    ),
                            ),
                        shape = RoundedCornerShape(36.dp),
                    )
                    .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { item ->
                GlassNavItem(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.GlassNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val anim = tween<Color>(durationMillis = 250)

    val pillColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.05f) else Color.Transparent,
        animationSpec = anim,
        label = "pillColor",
    )
    val circleColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.05f)
            },
        animationSpec = anim,
        label = "circleColor",
    )
    val iconTint by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = anim,
        label = "iconTint",
    )

    Row(
        modifier =
            Modifier
                // selected pill grows to fill; unselected stay compact circles
                .then(if (selected) Modifier.weight(1f) else Modifier.width(52.dp))
                .fillMaxHeight()
                .clip(RoundedCornerShape(30.dp))
                .background(pillColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (selected) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Text(
                text = item.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
            )
        }
    }
}
