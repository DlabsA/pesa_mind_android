package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * Standard "this is a Premium feature" placeholder for a gated section (an Analytics card,
 * a Budgets planning slot, ...) shown to a Free-tier user instead of hiding it or surfacing a
 * raw 403. Portrait, centered upsell layout. Every gated-feature call site should reuse this
 * rather than a bespoke locked-state composable, per the reuse-first rule.
 *
 * @param feature   Headline naming what's gated, e.g. "Analytics & budget insights".
 * @param benefits  Optional short bullet points shown with check marks under the description.
 * @param icon      Defaults to a lock (core icons). Pass Icons.Filled.WorkspacePremium if you
 *                  depend on material-icons-extended.
 */
@Composable
fun PremiumUpsellCard(
    feature: String,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "Upgrade to Premium to unlock $feature.",
    benefits: List<String> = emptyList(),
    eyebrow: String = "PREMIUM",
    ctaText: String = "Upgrade to Premium",
    icon: ImageVector = Icons.Filled.Lock,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.Space4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                            scheme.surfaceVariant.copy(alpha = 0.30f),

                )
                .padding(Spacing.Space4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = Spacing.Space3.dp, vertical = 6.dp),
                )
            }

            Surface(
                shape = CircleShape,
                color = scheme.primary.copy(alpha = 0.15f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier
                        .padding(Spacing.Space3.dp)
                        .size(28.dp),
                )
            }

            Text(
                text = feature,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (benefits.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
                ) {
                    benefits.forEach { benefit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
            ) {
                Text(ctaText)
            }
        }
    }
}
