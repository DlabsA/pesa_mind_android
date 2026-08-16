package cc.dlabs.pesamind.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** Diameter of an unselected item; also the shared height of the whole item row. */
private val UnselectedCircleSize = 60.dp

/** Accent circle diameter once an item is selected (smaller, so the pill has breathing room). */
private val SelectedCircleSize = 44.dp

/**
 * Bar is 80dp + 20dp top/bottom margin + navigationBarsPadding(); screens rendered behind this
 * overlay bar (see [GlassBottomBar]'s doc comment) need at least this much bottom clearance in
 * their scrollable content so the last row isn't hidden behind the floating bar.
 */
val GlassBottomBarClearance = 120.dp

/**
 * Glass, expanding-pill bottom bar. Matches your existing nav item shape (route / icon / label).
 *
 * The selected item grows to a pill showing a filled (primary) icon circle + label;
 * unselected items stay as compact tinted circles. Uses theme primary/onPrimary so the
 * forest/lime light-dark flip is respected automatically.
 *
 * NOTE ON THE "GLASS": a translucent surface only reads as glass if there is content
 * *behind* it. Overlay this over your content (Box) rather than the Scaffold `bottomBar`
 * slot, which reserves space and leaves nothing behind the bar to show through.
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
        // Tinted from onSurface (not a fixed White) so the sheen/border stay visible against a
        // light surface too — mirrors the same fix applied to GlassNavItem's pillColor.
        val glassTint = MaterialTheme.colorScheme.onSurface
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .shadow(24.dp, RoundedCornerShape(36.dp), clip = false)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    glassTint.copy(alpha = 0.03f),
                                    glassTint.copy(alpha = 0.01f),
                                    Color.Transparent,
                                ),
                        ),
                    )
                    .border(
                        width = 1.5.dp,
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        glassTint.copy(alpha = 0.32f),
                                        glassTint.copy(alpha = 0.12f),
                                    ),
                            ),
                        shape = RoundedCornerShape(36.dp),
                    )
                    .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
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
private fun GlassNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    // One transition drives every animated value off the same `selected` flag, so the circle,
    // icon, padding and colours all move in lockstep instead of some snapping and others tweening.
    val transition = updateTransition(targetState = selected, label = "navItemSelection")

    // Size/shape use a gentle spring for a little life; colours use a plain tween (springing a
    // colour looks muddy). Tune stiffness/damping to taste — higher stiffness = snappier.
    val circleSize by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) },
        label = "circleSize",
    ) { sel -> if (sel) SelectedCircleSize else UnselectedCircleSize }

    val iconSize by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) },
        label = "iconSize",
    ) { sel -> if (sel) 20.dp else 22.dp }

    val startPad by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) },
        label = "startPad",
    ) { sel -> if (sel) 6.dp else 0.dp }

    val pillColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 250, easing = FastOutSlowInEasing) },
        label = "pillColor",
    ) { sel -> if (sel) onSurface.copy(alpha = 0.16f) else onSurface.copy(alpha = 0.09f) }

    val circleColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 250, easing = FastOutSlowInEasing) },
        label = "circleColor",
    ) { sel -> if (sel) MaterialTheme.colorScheme.primary else Color.Transparent }

    val iconTint by transition.animateColor(
        transitionSpec = { tween(durationMillis = 250, easing = FastOutSlowInEasing) },
        label = "iconTint",
    ) { sel ->
        if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Single continuous modifier chain (no per-state branch): fixed height, 50%-rounded so it
    // reads as a circle when narrow and a pill when the label expands it. Width is wrap-content,
    // so it grows/shrinks purely from the animating circle + the AnimatedVisibility label.
    Row(
        modifier =
            Modifier
                .height(UnselectedCircleSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(pillColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                // Springs can overshoot past their target (including below 0dp mid-flight
                // between the 0dp/6dp targets below); padding() throws on negative values.
                .padding(horizontal = startPad.coerceAtLeast(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .background(circleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }

        AnimatedVisibility(
            visible = selected,
            enter =
                expandHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    clip = false,
                ) + fadeIn(tween(220, delayMillis = 60)),
            exit =
                shrinkHorizontally(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    clip = false,
                ) + fadeOut(tween(120)),
        ) {
            Text(
                text = item.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
            )
        }
    }
}
