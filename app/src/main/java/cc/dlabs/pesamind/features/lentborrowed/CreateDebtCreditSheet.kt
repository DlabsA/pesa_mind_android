package cc.dlabs.pesamind.features.lentborrowed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import cc.dlabs.pesamind.core.ui.ReminderOffsetsInput
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Create-debt sheet — direction (the "I gave money" / "I received money" two-card pattern maps
 * to lent/borrowed under the hood, never shown as raw copy), counterparty name/phone, amount,
 * due date (optional), reminder offsets (optional, auto-seeded to [7,3,1] once a due date is
 * set, fully editable after).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDebtCreditSheet(
    onDismiss: () -> Unit,
    onCreate: (
        direction: String,
        counterpartyName: String,
        counterpartyPhone: String?,
        amount: Double,
        dueAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var direction by remember { mutableStateOf("lent") }
    var counterpartyName by remember { mutableStateOf("") }
    var counterpartyPhone by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueAtMillis by remember { mutableStateOf<Long?>(null) }
    var reminderOffsets by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(dueAtMillis) {
        if (dueAtMillis != null && reminderOffsets.isEmpty()) {
            reminderOffsets = listOf(7, 3, 1)
        }
    }

    val amount = amountText.toDoubleOrNull()
    val canSubmit = counterpartyName.isNotBlank() && amount != null && amount > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New Debt", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))

            // Direction selector - enhanced two-card layout
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DirectionCard(
                    isSelected = direction == "lent",
                    onClick = { direction = "lent" },
                    icon = Icons.Filled.ArrowUpward,
                    label = "I gave money",
                    subLabel = "(${PesaMindStrings.DebtCredit.LENT_LABEL})",
                    modifier = Modifier.weight(1f),
                    color = getTertiaryColor(),
                )
                DirectionCard(
                    isSelected = direction == "borrowed",
                    onClick = { direction = "borrowed" },
                    icon = Icons.Filled.ArrowDownward,
                    label = "I received money",
                    subLabel = "(${PesaMindStrings.DebtCredit.BORROWED_LABEL})",
                    modifier = Modifier.weight(1f),
                    color = getErrorColor(),
                )
            }

            OutlinedTextField(
                value = counterpartyName,
                onValueChange = { counterpartyName = it },
                label = { Text("Name") },
                placeholder = { Text("Who are you dealing with?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = counterpartyPhone,
                onValueChange = { counterpartyPhone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { raw -> if (raw.isEmpty() || raw.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) amountText = raw },
                label = { Text("Amount") },
                prefix = { Text("UGX ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                val dueAtLabel =
                    dueAtMillis?.let { "Due " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
                        ?: "Set due date (optional)"
                Text(dueAtLabel)
            }

            ReminderOffsetsInput(offsets = reminderOffsets, onOffsetsChange = { reminderOffsets = it }, hasDate = dueAtMillis != null)

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 280) note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onCreate(
                        direction,
                        counterpartyName.trim(),
                        counterpartyPhone.trim().ifBlank { null },
                        amount!!,
                        dueAtMillis,
                        note.trim(),
                        reminderOffsets,
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create debt")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueAtMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DirectionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subLabel: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(Radius.Medium.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            ),
        border = if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
