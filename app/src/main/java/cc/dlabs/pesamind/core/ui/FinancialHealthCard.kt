package cc.dlabs.pesamind.core.ui

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dlabs.pesamind.core.network.analytics.Health
import cc.dlabs.pesamind.core.theme.DarkColors
import cc.dlabs.pesamind.core.theme.LightColors

// ─── Financial Health Card ────────────────────────────────────────────────────
// Receives Health directly (the real Android model from FinancialHealthResponse.data).
// Shared between the Dashboard and Analytics screens.

@Composable
fun FinancialHealthCard(
    health: Health,
    modifier: Modifier = Modifier,
) {
    Log.d("FinancialHealthCard", "health: $health")
    val isDark = isSystemInDarkTheme()
    val score = health.score
    val ringColor =
        when {
            score >= 80 -> if (isDark) DarkColors.Income else LightColors.Income
            score >= 60 -> Color(0xFFFF9500)
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        }
    val statusLabel =
        when {
            score >= 90 -> "Excellent 🎯"
            score >= 80 -> "Good 👍"
            score >= 70 -> "Fair"
            score >= 60 -> "Needs Work ⚠️"
            else -> "Needs Attention ⛔"
        }

    var ringTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(score) { ringTarget = score / 100f }
    val ringProgress by animateFloatAsState(
        targetValue = ringTarget,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow),
        label = "health_ring",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Ring + labels
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2f
                        val tl = Offset(inset, inset)
                        val sz = Size(size.width - stroke, size.height - stroke)
                        drawArc(ringColor.copy(alpha = 0.12f), -90f, 360f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(ringColor, -90f, 360f * ringProgress, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${health.score}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = ringColor,
                        )
                        Text(
                            "/ 100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Financial Health",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(shape = RoundedCornerShape(50), color = ringColor.copy(alpha = 0.10f)) {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ringColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val trendIcon =
                            when (health.trend.lowercase()) {
                                "improving" -> Icons.Default.TrendingUp
                                "declining" -> Icons.Default.TrendingDown
                                else -> Icons.Default.TrendingFlat
                            }
                        Icon(
                            trendIcon,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            "Trend: ${health.trend.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            // Component breakdown — health.components is a Map<String, ComponentScore>
            val components = health.components
            if (components.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    components.forEach { (name, comp) ->
                        // Gson deserializes a JSON `null` component value (e.g. "budget_adherence"
                        // when there's no budget to compare against yet) straight into this map
                        // despite its declared type being non-null Component — skip rendering a
                        // row for it rather than crashing on comp.score.
                        if (comp == null) return@forEach
                        HealthComponentRow(
                            label = name.replace("_", " ").replaceFirstChar { it.uppercase() },
                            score = comp.score,
                            description = comp.description,
                        )
                    }
                }
            }

            // Strengths & Weaknesses
            val strengths = health.strengths
            val weaknesses = health.weaknesses
            if (strengths.isNotEmpty() || weaknesses.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (strengths.isNotEmpty()) {
                        StrengthWeaknessColumn(
                            "Strengths",
                            strengths,
                            Icons.Default.CheckCircle,
                            if (isDark) DarkColors.Income else LightColors.Income,
                            Modifier.weight(1f),
                        )
                    }
                    if (weaknesses.isNotEmpty()) {
                        StrengthWeaknessColumn(
                            "Weaknesses",
                            weaknesses,
                            Icons.Default.ErrorOutline,
                            if (isDark) DarkColors.Expense else LightColors.Expense,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthComponentRow(
    label: String,
    score: Int,
    description: String,
) {
    val isDark = isSystemInDarkTheme()
    val color =
        when {
            score >= 75 -> if (isDark) DarkColors.Income else LightColors.Income
            score >= 50 -> Color(0xFFFF9500)
            else -> if (isDark) DarkColors.Expense else LightColors.Expense
        }
    var barTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(score) { barTarget = score / 100f }
    val barW by animateFloatAsState(barTarget, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "comp_bar_$label")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Row {
                Text("$score", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
                Text(" / 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.10f)),
        ) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun StrengthWeaknessColumn(
    title: String,
    items: List<String>,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(10.dp).padding(top = 1.dp))
                Text(item, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
