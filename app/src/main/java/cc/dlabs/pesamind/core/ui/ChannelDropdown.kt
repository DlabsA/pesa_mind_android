package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.features.settings.channels.ChannelState
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes

/**
 * Mobile money accounts (M-Pesa, Airtel Money, …) are a Premium feature — free-tier users can
 * still log transactions against Cash/Bank accounts, just not mobile money ones. Both helpers
 * below exist so this rule is derived in exactly one place; every channel picker in the app
 * feeds off them.
 */
fun visibleChannels(state: ChannelState): List<ChannelDetails> =
    if (state.isPremium) {
        state.channels
    } else {
        state.channels.filterNot { it.channelType == ChannelTypes.MOBILE_MONEY }
    }

fun hasHiddenMobileMoneyChannels(state: ChannelState): Boolean =
    !state.isPremium && state.channels.any { it.channelType == ChannelTypes.MOBILE_MONEY }

/** Supporting hint to show under a channel picker when [hasHiddenMobileMoneyChannels] is true. */
const val HIDDEN_MOBILE_MONEY_HINT = "Mobile money accounts require Premium"

/**
 * Shared read-only dropdown for picking one of the user's channels ("accounts"). Extracted from
 * `AddTransactionScreen`'s inline `ExposedDropdownMenuBox` so the quick add-payment dialog and
 * the full add-transaction screen can't drift apart.
 *
 * [channels] is expected to already be filtered through [visibleChannels].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDropdown(
    channels: List<ChannelDetails>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select an account",
    isError: Boolean = false,
    supportingText: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = channels.find { it.id == selectedId }?.name ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(placeholder) },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = {
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            shape = RoundedCornerShape(Radius.Medium.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    cursorColor = accentColor,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            channels.forEach { channel ->
                DropdownMenuItem(
                    text = { Text(channel.name) },
                    onClick = {
                        onSelect(channel.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
