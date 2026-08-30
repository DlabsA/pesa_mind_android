package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.ui.ReminderOffsetsInput
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add a saving goal", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Goal name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = targetAmountText,
                onValueChange = { raw -> if (raw.isEmpty() || raw.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) targetAmountText = raw },
                label = { Text("Target amount") },
                prefix = { Text("UGX ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    targetAtMillis?.let { "Target " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
                        ?: "Set target date (optional)",
                )
            }

            ReminderOffsetsInput(offsets = reminderOffsets, onOffsetsChange = { reminderOffsets = it }, hasDate = targetAtMillis != null)

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 280) note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onCreate(name.trim(), targetAmount!!, targetAtMillis, note.trim(), reminderOffsets) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add goal")
            }
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
