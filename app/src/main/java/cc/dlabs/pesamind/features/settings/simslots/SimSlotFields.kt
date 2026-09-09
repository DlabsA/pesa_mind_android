package cc.dlabs.pesamind.features.settings.simslots

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.features.settings.channels.COUNTRY_CODES
import cc.dlabs.pesamind.features.settings.channels.CountryCode
import cc.dlabs.pesamind.features.settings.channels.MobileMoneyNumberField

/**
 * Explanation text + drift-warning banner + per-slot [MobileMoneyNumberField]s + Save button —
 * the SIM-slot form itself, with no navigation/scaffold of its own so [SimSlotsScreen] (a
 * regular Settings screen) and `PesaMindNavGraph`'s drift-blocking overlay (a non-dismissible
 * `Dialog`) can both host it without duplicating the field-rendering/validation code.
 */
@Composable
fun SimSlotFields(
    state: SimSlotsState,
    onNumberChange: (Int, String) -> Unit,
    onCountryChange: (Int, CountryCode) -> Unit,
    onSave: () -> Unit,
    // The drift dialog states the same thing in its own title/body, so it turns the explanation
    // and the warning banner off and shows nothing above the number fields.
    showIntro: Boolean = true,
) {
    val teal = getPrimaryColor()

    if (showIntro) {
        Text(
            "Some phones can't tell us a SIM's own number automatically. If a mobile money SMS " +
                "shows an unresolved number, enter it here so we know which SIM slot it belongs to.",
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    if (showIntro && state.driftDetected) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "We noticed a SIM card change. Please confirm the numbers below.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    state.displaySlots.forEach { slot ->
        Text(
            text =
                if (slot.carrierName.isBlank()) {
                    "SIM ${slot.slotIndex + 1}"
                } else {
                    "SIM ${slot.slotIndex + 1} — ${slot.carrierName}"
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MobileMoneyNumberField(
            phoneNumber = state.simSlotNumbers[slot.slotIndex] ?: "",
            onPhoneNumberChange = { onNumberChange(slot.slotIndex, it) },
            selectedCountry = state.simSlotCountries[slot.slotIndex] ?: COUNTRY_CODES[0],
            onCountryChange = { onCountryChange(slot.slotIndex, it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = teal),
        enabled = !state.isSaving,
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text("Save SIM Numbers", fontWeight = FontWeight.SemiBold)
        }
    }
}
