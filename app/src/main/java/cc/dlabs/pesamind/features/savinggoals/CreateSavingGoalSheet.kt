package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.ReminderOffsetsInput
import cc.dlabs.pesamind.core.ui.asUgxAmount
import java.text.SimpleDateFormat
import java.util.Locale

/** Create-goal sheet — name, target amount, target date (optional), reminder offsets (same
 * default pattern as [cc.dlabs.pesamind.features.lentborrowed.CreateDebtCreditSheet]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSavingGoalSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, targetAmount: Double, targetAtMillis: Long?, note: String, reminderOffsets: List<Int>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var targetAmountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var targetAtMillis by remember { mutableStateOf<Long?>(null) }
    var reminderOffsets by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(targetAtMillis) {
        if (targetAtMillis != null && reminderOffsets.isEmpty()) {
            reminderOffsets = listOf(7, 3, 1)
        }
    }

    val targetAmount = targetAmountText.toDoubleOrNull()
    val canSubmit = name.isNotBlank() && targetAmount != null && targetAmount > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.Space5.dp, vertical = Spacing.Space2.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIconTile(achieved = false, size = 44.dp)
                Spacer(Modifier.width(Spacing.Space3.dp))
                Column {
                    Text(
                        text = "New saving goal",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "Name it, set a target, track it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal name") },
                placeholder = { Text("Emergency fund, new laptop, trip…") },
                singleLine = true,
                shape = RoundedCornerShape(Radius.Medium.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = targetAmountText,
                onValueChange = { raw -> if (raw.isEmpty() || raw.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) targetAmountText = raw },
                label = { Text("Target amount") },
                prefix = { Text("UGX ") },
                supportingText =
                    targetAmount?.takeIf { it > 0 }?.let {
                        { Text("Target ${it.asUgxAmount()}") }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.Medium.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Target date as a tappable row rather than a bare OutlinedButton, so the chosen
            // date can be cleared again without reopening the picker.
            Surface(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(Radius.Medium.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Space4.dp, vertical = Spacing.Space3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.Space3.dp))
                    Text(
                        text =
                            targetAtMillis?.let {
                                "Target by " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it)
                            } ?: "Set target date (optional)",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (targetAtMillis != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.weight(1f),
                    )
                    if (targetAtMillis != null) {
                        IconButton(
                            onClick = {
                                targetAtMillis = null
                                reminderOffsets = emptyList()
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear target date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            ReminderOffsetsInput(offsets = reminderOffsets, onOffsetsChange = { reminderOffsets = it }, hasDate = targetAtMillis != null)

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 280) note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(Radius.Medium.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onCreate(name.trim(), targetAmount!!, targetAtMillis, note.trim(), reminderOffsets) },
                enabled = canSubmit,
                shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Add goal",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(Modifier.height(Spacing.Space4.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetAtMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
