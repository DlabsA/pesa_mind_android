package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Fully user-customizable "remind me N days before" offsets — a chip per current offset with a
 * remove (x) button, plus a numeric add field. No fixed presets: any positive day count can be
 * added or removed at will (per the product decision that reminder offsets are entirely
 * user-customizable, not chosen from a preset list).
 *
 * [hasDate] gates the whole control: with no due/target date set yet, there's nothing to offset
 * from, so this renders nothing. The caller is expected to auto-seed [offsets] to a sensible
 * default (e.g. `[7, 3, 1]`) the first time a date is set — see the create sheets' own
 * `LaunchedEffect` — since that's a one-time UX nicety, not something this reusable input owns.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderOffsetsInput(
    offsets: List<Int>,
    onOffsetsChange: (List<Int>) -> Unit,
    hasDate: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!hasDate) return

    var newValue by remember { mutableStateOf("") }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        offsets.sortedDescending().forEach { days ->
            InputChip(
                selected = false,
                onClick = { onOffsetsChange(offsets - days) },
                label = { Text("$days day${if (days != 1) "s" else ""} before") },
                trailingIcon = { Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.width(16.dp)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = newValue,
                onValueChange = { raw -> if (raw.length <= 3 && raw.all(Char::isDigit)) newValue = raw },
                placeholder = { Text("Days") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp),
            )
            IconButton(onClick = {
                val n = newValue.toIntOrNull()
                if (n != null && n > 0 && n !in offsets) {
                    onOffsetsChange(offsets + n)
                    newValue = ""
                }
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add reminder")
            }
        }
    }
}
