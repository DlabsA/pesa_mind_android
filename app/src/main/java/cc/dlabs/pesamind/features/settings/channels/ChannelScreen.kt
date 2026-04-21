package cc.dlabs.pesamind.features.settings.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.ChannelDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    navController: NavHostController,
    vm: ChannelViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showTypeFilterDialog by remember { mutableStateOf(false) }
    var currentTypeFilter by remember { mutableStateOf("") }
    var currentStatusFilter by remember { mutableStateOf(ChannelStatusFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<ChannelDetails?>(null) }
    var editingChannel by remember { mutableStateOf<ChannelDetails?>(null) }

    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Financial Channels") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadChannels() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Channel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ChannelFilters(
                currentTypeFilter = currentTypeFilter,
                currentStatusFilter = currentStatusFilter,
                onAll = {
                    currentTypeFilter = ""
                    currentStatusFilter = ChannelStatusFilter.ALL
                    vm.loadChannels()
                },
                onActive = {
                    currentTypeFilter = ""
                    currentStatusFilter = ChannelStatusFilter.ACTIVE
                    vm.loadChannelsByStatus(true)
                },
                onInactive = {
                    currentTypeFilter = ""
                    currentStatusFilter = ChannelStatusFilter.INACTIVE
                    vm.loadChannelsByStatus(false)
                },
                onType = { showTypeFilterDialog = true }
            )

            if (currentTypeFilter.isNotBlank() || currentStatusFilter != ChannelStatusFilter.ALL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeFilterLabel(currentTypeFilter, currentStatusFilter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        currentTypeFilter = ""
                        currentStatusFilter = ChannelStatusFilter.ALL
                        vm.loadChannels()
                    }) {
                        Text("Clear filters")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading && state.channels.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            } else if (state.channels.isEmpty()) {
                Text(
                    text = "No channels found. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelCard(
                            item = channel,
                            onEdit = { editingChannel = channel },
                            onDelete = { pendingDelete = channel }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        ChannelFormDialog(
            title = "Create Channel",
            confirmLabel = "Create",
            initialStatus = true,
            showStatusField = false,
            isSaving = state.isSaving,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, type, status ->
                vm.createChannel(name, description, type, status)
                showCreateDialog = false
            }
        )
    }

    editingChannel?.let { channel ->
        ChannelFormDialog(
            title = "Edit Channel",
            confirmLabel = "Update",
            initialName = channel.name,
            initialDescription = channel.description,
            initialType = channel.channelType,
            initialStatus = channel.status,
            isSaving = state.isSaving,
            onDismiss = { editingChannel = null },
            onConfirm = { name, description, _, status ->
                vm.updateChannel(
                    id = channel.id,
                    name = name,
                    description = description,
                    status = status
                )
                editingChannel = null
            },
            isTypeEditable = false
        )
    }

    pendingDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Channel") },
            text = { Text("Delete ${channel.name}? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isDeleting,
                    onClick = {
                        vm.deleteChannel(channel.id)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTypeFilterDialog) {
        TypeFilterDialog(
            initialValue = currentTypeFilter,
            onDismiss = { showTypeFilterDialog = false },
            onApply = { type ->
                currentTypeFilter = type
                vm.loadChannelsByType(type)
                showTypeFilterDialog = false
            }
        )
    }
}

@Composable
private fun ChannelFilters(
    currentTypeFilter: String,
    currentStatusFilter: ChannelStatusFilter,
    onAll: () -> Unit,
    onActive: () -> Unit,
    onInactive: () -> Unit,
    onType: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        item {
            FilterChip(
                selected = currentStatusFilter == ChannelStatusFilter.ALL && currentTypeFilter.isBlank(),
                onClick = onAll,
                label = { Text("All") }
            )
        }
        item {
            FilterChip(
                selected = currentStatusFilter == ChannelStatusFilter.ACTIVE,
                onClick = onActive,
                label = { Text("Active") }
            )
        }
        item {
            FilterChip(
                selected = currentStatusFilter == ChannelStatusFilter.INACTIVE,
                onClick = onInactive,
                label = { Text("Inactive") }
            )
        }
        item {
            FilterChip(
                selected = currentTypeFilter.isNotBlank(),
                onClick = onType,
                label = {
                    val label = if (currentTypeFilter.isBlank()) "By Type" else "Type: ${displayChannelType(currentTypeFilter)}"
                    Text(label)
                }
            )
        }
    }
}

@Composable
private fun ChannelCard(
    item: ChannelDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (item.status) "Active" else "Inactive") }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Type: ${displayChannelType(item.channelType)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No description",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

private enum class ChannelStatusFilter {
    ALL,
    ACTIVE,
    INACTIVE
}

private fun activeFilterLabel(typeFilter: String, statusFilter: ChannelStatusFilter): String {
    val statusLabel = when (statusFilter) {
        ChannelStatusFilter.ALL -> "All statuses"
        ChannelStatusFilter.ACTIVE -> "Active"
        ChannelStatusFilter.INACTIVE -> "Inactive"
    }

    return if (typeFilter.isBlank()) {
        "Showing: $statusLabel"
    } else {
        "Showing: $statusLabel, ${displayChannelType(typeFilter)}"
    }
}

@Composable
private fun ChannelFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialDescription: String = "",
    initialType: String = "",
    initialStatus: Boolean = true,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit,
    isTypeEditable: Boolean = true,
    showStatusField: Boolean = true
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var type by remember(title, initialType) {
        mutableStateOf(ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH)
    }
    var status by remember(title, initialStatus) { mutableStateOf(initialStatus) }
    var typeMenuExpanded by remember(title) { mutableStateOf(false) }
    var statusMenuExpanded by remember(title, initialStatus) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") }
                )
                Text("Channel Type", style = MaterialTheme.typography.labelLarge)
                if (isTypeEditable) {
                    Box {
                        OutlinedButton(
                            onClick = { typeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(displayChannelType(type))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            ChannelTypes.valid.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(displayChannelType(option)) },
                                    onClick = {
                                        type = option
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = displayChannelType(type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showStatusField) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(
                            onClick = { statusMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (status) "Active" else "Inactive")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = statusMenuExpanded,
                            onDismissRequest = { statusMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Active") },
                                onClick = {
                                    status = true
                                    statusMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Inactive") },
                                onClick = {
                                    status = false
                                    statusMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    onConfirm(name.trim(), description.trim(), type.trim(), status)
                }
            ) {
                Text(if (isSaving) "Saving..." else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TypeFilterDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var type by remember(initialValue) {
        mutableStateOf(ChannelTypes.normalizeOrNull(initialValue) ?: "")
    }
    var typeMenuExpanded by remember(initialValue) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter By Type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select one type")
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (type.isBlank()) "Choose type" else displayChannelType(type))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ChannelTypes.valid.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(displayChannelType(option)) },
                                onClick = {
                                    type = option
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = type.isNotBlank(),
                onClick = { onApply(type.trim()) }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun displayChannelType(type: String): String {
    return when (type) {
        ChannelTypes.MOBILE_MONEY -> "Mobile Money"
        ChannelTypes.CASH -> "Cash"
        ChannelTypes.BANK -> "Bank"
        else -> type
    }
}
