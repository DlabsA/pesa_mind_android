package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.getPrimaryColor

/**
 * Wrapping row of selectable chips, used for both channel type and provider. Extracted from
 * `ChannelScreen.kt` (originally `private`) when the channel-onboarding flow became a second
 * caller — `.claude/CLAUDE.md` requires shared form composables to live in `core/ui/` once
 * they cross that reuse threshold rather than staying duplicated per screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChipGroup(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    optionLabel: (String) -> String = { it },
    isError: Boolean = false,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(optionLabel(option)) },
                leadingIcon =
                    if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                shape = RoundedCornerShape(10.dp),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = getPrimaryColor().copy(alpha = 0.14f),
                        selectedLabelColor = getPrimaryColor(),
                        selectedLeadingIconColor = getPrimaryColor(),
                    ),
                // Material3 < 1.2: filterChipBorder takes different params — drop enabled/selected.
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor =
                            if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                        selectedBorderColor = getPrimaryColor().copy(alpha = 0.5f),
                    ),
            )
        }
    }
}

/**
 * Dropdown menu for selecting a provider (bank or mobile money). See [ChoiceChipGroup]'s doc
 * comment for why this lives here instead of `ChannelScreen.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDropdown(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            placeholder = { Text("Select a provider") },
            isError = isError,
            shape = RoundedCornerShape(14.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                        if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            getPrimaryColor()
                        },
                    unfocusedBorderColor =
                        if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                    errorBorderColor = MaterialTheme.colorScheme.error,
                ),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .fillMaxWidth(0.93f)
                    .heightIn(max = 300.dp),
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No options available", style = MaterialTheme.typography.bodySmall) },
                    onClick = {},
                    enabled = false,
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (option == selected) {
                                        getPrimaryColor().copy(alpha = 0.08f)
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                    )
                }
            }
        }
    }
}
