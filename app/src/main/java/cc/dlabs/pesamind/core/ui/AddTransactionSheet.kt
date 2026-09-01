package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dlabs.pesamind.core.theme.DarkColors
import cc.dlabs.pesamind.core.theme.LightColors
import cc.dlabs.pesamind.core.utils.TransactionTypes
import cc.dlabs.pesamind.features.home.TYPE_INCOME
import cc.dlabs.pesamind.features.home.TYPE_SAVING

@Composable
fun typeColor(type: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (type) {
        TYPE_INCOME -> if (isDark) DarkColors.Income else LightColors.Income
        TYPE_SAVING -> if (isDark) DarkColors.Savings else LightColors.Savings
        else -> if (isDark) DarkColors.Expense else LightColors.Expense
    }
}

fun typeIcon(type: String): ImageVector =
    when (type) {
        TransactionTypes.INCOME -> Icons.AutoMirrored.Outlined.TrendingUp
        TransactionTypes.EXPENSE -> Icons.AutoMirrored.Outlined.TrendingDown
        TransactionTypes.SAVINGS -> Icons.Outlined.Savings
        else -> Icons.Outlined.Payments
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    name: String,
    amount: String,
    type: String,
    nameError: String?,
    amountError: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add a Transaction",
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Monthly Salary", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            )

            // Amount field
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount (UGX)") },
                placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                prefix = {
                    Text(
                        "UGX  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
            )

            // Type chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Type",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionTypes.valid.forEach { t ->
                        val selected = type == t
                        val color = typeColor(t)
                        FilterChip(
                            selected = selected,
                            onClick = { onTypeChange(t) },
                            label = {
                                Text(
                                    TransactionTypes.displayName(t),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    typeIcon(t),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
                }
            }

            // Add button
            Button(
                onClick = onAdd,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(vertical = 13.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Adding…",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add Transaction",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }
        }
    }
}
