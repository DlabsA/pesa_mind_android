package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

private val AMOUNT_PATTERN = Regex("^\\d{0,10}(\\.\\d{0,2})?$")
private const val MAX_NOTE_LENGTH = 200

/**
 * Minimal "log a payment against this thing" dialog: account + amount, with the note pre-filled
 * and optional. The transaction *type* and *purpose* are not asked for — the caller already
 * knows both from the screen the user is standing on (a lent/borrowed debt, a saving goal), and
 * either can still be corrected afterwards from [TransactionDetailSheet]'s Purpose dropdown.
 *
 * [onConfirm] receives a note that is never blank — the [defaultNote] is substituted back in if
 * the user clears the field, since `TransactionViewModel.createTransaction` rejects a blank one.
 */
@Composable
fun QuickPaymentDialog(
    title: String,
    subtitle: String,
    channels: List<ChannelDetails>,
    defaultNote: String,
    isSaving: Boolean,
    accentColor: Color,
    onConfirm: (channelId: String, amount: Double, note: String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Add",
    channelSupportingText: String? = null,
) {
    var channelId by remember { mutableStateOf(channels.singleOrNull()?.id ?: "") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(defaultNote) }

    val amount = amountText.toDoubleOrNull()
    val amountError = amountText.isNotEmpty() && (amount == null || amount <= 0)
    val canSubmit = channelId.isNotBlank() && amount != null && amount > 0 && !isSaving

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = {
            Surface(
                shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
                color = accentColor.copy(alpha = 0.10f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ChannelDropdown(
                    channels = channels,
                    selectedId = channelId,
                    onSelect = { channelId = it },
                    supportingText = channelSupportingText,
                    accentColor = accentColor,
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw -> if (raw.isEmpty() || raw.matches(AMOUNT_PATTERN)) amountText = raw },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("UGX  ", fontWeight = FontWeight.SemiBold) },
                    isError = amountError,
                    supportingText =
                        if (amountError) {
                            { Text("Enter an amount greater than zero") }
                        } else {
                            null
                        },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            cursorColor = accentColor,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= MAX_NOTE_LENGTH) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            cursorColor = accentColor,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        channelId.trim(),
                        amount ?: return@Button,
                        note.trim().ifBlank { defaultNote },
                    )
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(Radius.Medium.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
