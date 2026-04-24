package cc.dlabs.pesamind.features.settings.channels

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.theme.ExpenseRed
import cc.dlabs.pesamind.core.theme.PesaMindGreen
import cc.dlabs.pesamind.core.theme.PesaMindTeal
import cc.dlabs.pesamind.core.theme.TextSecondary

// ─── Filter state enum ───────────────────────────────────────────────────────

private enum class FilterType { ALL, ACTIVE, INACTIVE, BY_TYPE }

// ─── Main Screen ─────────────────────────────────────────────────────────────

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
    var activeFilter by remember { mutableStateOf(FilterType.ALL) }
    var currentTypeFilter by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ChannelDetails?>(null) }
    var editingChannel by remember { mutableStateOf<ChannelDetails?>(null) }

    LaunchedEffect(state.message, state.error) {
        state.message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
        state.error?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Channels",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        if (state.channels.isNotEmpty()) {
                            Text(
                                text = "${state.channels.size} channel${if (state.channels.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Subtle inline loading indicator when refreshing a non-empty list
                    if (state.isLoading && state.channels.isNotEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 2.dp),
                            strokeWidth = 2.dp,
                            color = PesaMindTeal
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { vm.loadChannels() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = PesaMindTeal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = PesaMindTeal.copy(alpha = 0.25f),
                    spotColor = PesaMindTeal.copy(alpha = 0.35f)
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Channel")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Divider under top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            )

            // ── Filter chips row
            ChannelFilterRow(
                activeFilter = activeFilter,
                currentTypeFilter = currentTypeFilter,
                onAll = {
                    activeFilter = FilterType.ALL
                    currentTypeFilter = ""
                    vm.loadChannels()
                },
                onActive = {
                    activeFilter = FilterType.ACTIVE
                    currentTypeFilter = ""
                    vm.loadChannelsByStatus(true)
                },
                onInactive = {
                    activeFilter = FilterType.INACTIVE
                    currentTypeFilter = ""
                    vm.loadChannelsByStatus(false)
                },
                onType = { showTypeFilterDialog = true }
            )

            // ── Content area
            when {
                state.isLoading && state.channels.isEmpty() -> ChannelListSkeleton()
                state.channels.isEmpty() -> ChannelEmptyState(onAddClick = { showCreateDialog = true })
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp // FAB clearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.channels, key = { it.id }) { channel ->
                            ChannelCard(
                                item = channel,
                                onEdit = { editingChannel = channel },
                                onDelete = { pendingDelete = channel }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showCreateDialog) {
        ChannelFormDialog(
            title = "New Channel",
            confirmLabel = "Create",
            initialStatus = true,
            showStatusField = false,
            isSaving = state.isSaving,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, type, channelDescription, status ->
                vm.createChannel(name, description, type, channelDescription, status)
                showCreateDialog = false
            }
        )
    }

    editingChannel?.let { channel ->
        ChannelFormDialog(
            title = "Edit Channel",
            confirmLabel = "Save Changes",
            initialName = channel.name,
            initialDescription = channel.description,
            initialType = channel.channelType,
            initialStatus = channel.status,
            isSaving = state.isSaving,
            onDismiss = { editingChannel = null },
            onConfirm = { name, description, _, channelDescription, status ->
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
        DeleteConfirmDialog(
            channelName = channel.name,
            isDeleting = state.isDeleting,
            onConfirm = {
                vm.deleteChannel(channel.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (showTypeFilterDialog) {
        TypeFilterDialog(
            initialValue = currentTypeFilter,
            onDismiss = { showTypeFilterDialog = false },
            onApply = { type ->
                currentTypeFilter = type
                activeFilter = FilterType.BY_TYPE
                vm.loadChannelsByType(type)
                showTypeFilterDialog = false
            }
        )
    }
}

// ─── Filter Row ───────────────────────────────────────────────────────────────

@Composable
private fun ChannelFilterRow(
    activeFilter: FilterType,
    currentTypeFilter: String,
    onAll: () -> Unit,
    onActive: () -> Unit,
    onInactive: () -> Unit,
    onType: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = PesaMindTeal,
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White
    )
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = activeFilter == FilterType.ALL,
            onClick = onAll,
            label = { Text("All") },
            colors = chipColors
        )
        FilterChip(
            selected = activeFilter == FilterType.ACTIVE,
            onClick = onActive,
            label = { Text("Active") },
            colors = chipColors
        )
        FilterChip(
            selected = activeFilter == FilterType.INACTIVE,
            onClick = onInactive,
            label = { Text("Inactive") },
            colors = chipColors
        )
        FilterChip(
            selected = activeFilter == FilterType.BY_TYPE,
            onClick = onType,
            label = {
                Text(
                    if (currentTypeFilter.isBlank()) "By Type"
                    else displayChannelType(currentTypeFilter)
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = chipColors
        )
    }
}

// ─── Skeleton / Loading ───────────────────────────────────────────────────────

@Composable
private fun ChannelListSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset(shimmerOffset * 800f, 0f),
        end = Offset((shimmerOffset + 1f) * 800f, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) { SkeletonCard(shimmerBrush) }
    }
}

@Composable
private fun SkeletonCard(brush: Brush) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp), ambientColor = PesaMindTeal.copy(0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(brush, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(brush)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        Modifier
                            .width(72.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                    Box(
                        Modifier
                            .width(80.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun ChannelEmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon in a soft teal pill
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PesaMindTeal.copy(alpha = 0.10f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = PesaMindTeal,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "No channels yet",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Add your first financial channel to start\ntracking payments and transfers.",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Add Channel",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

// ─── Channel Card ─────────────────────────────────────────────────────────────

@Composable
fun ChannelCard(
    item: ChannelDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusDotColor = if (item.status) PesaMindGreen else ExpenseRed
    val statusChipBg = if (item.status) PesaMindGreen.copy(alpha = 0.10f)
    else ExpenseRed.copy(alpha = 0.09f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = PesaMindTeal.copy(alpha = 0.07f),
                spotColor = PesaMindTeal.copy(alpha = 0.13f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Teal gradient accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            ) {
                // Name + status chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(shape = CircleShape, color = statusChipBg) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .alpha(if (item.status) pulseAlpha else 0.55f)
                                    .background(statusDotColor, CircleShape)
                            )
                            Text(
                                text = if (item.status) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = statusDotColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))

                // Channel type metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = displayChannelType(item.channelType),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            letterSpacing = 0.25.sp
                        )
                    )
                }

                // Description inset block
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PesaMindTeal.copy(alpha = 0.55f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PesaMindTeal)
                    ) {
                        Icon(Icons.Outlined.Edit, "Edit", Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExpenseRed.copy(alpha = 0.10f),
                            contentColor = ExpenseRed
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete", Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── Delete Confirm Dialog ────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    channelName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ExpenseRed.copy(alpha = 0.10f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ExpenseRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        title = {
            Text(
                "Delete Channel",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "\"$channelName\" will be permanently removed. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
            ) {
                Text(if (isDeleting) "Deleting…" else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Channel Form Dialog ──────────────────────────────────────────────────────

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
    onConfirm: (String, String, String, String, Boolean) -> Unit,
    isTypeEditable: Boolean = true,
    showStatusField: Boolean = true
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var channelDescription by remember(title) { mutableStateOf("") }
    var type by remember(title, initialType) {
        mutableStateOf(ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH)
    }
    var status by remember(title, initialStatus) { mutableStateOf(initialStatus) }
    var typeMenuExpanded by remember(title) { mutableStateOf(false) }
    var descMenuExpanded by remember(title) { mutableStateOf(false) }

    val isFormValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )

                // Channel type
                FormSectionLabel("Channel Type")
                if (isTypeEditable) {
                    Box {
                        OutlinedButton(
                            onClick = { typeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                displayChannelType(type),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = TextSecondary
                            )
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
                                        channelDescription = ""
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayChannelType(type),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }

                // Channel sub-description (only for non-cash with editable type)
                if (type != ChannelTypes.CASH && type.isNotBlank()) {
                    FormSectionLabel("Provider")
                    if (isTypeEditable) {
                        Box {
                            OutlinedButton(
                                onClick = { descMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = displayChannelTypeDescription(type, channelDescription)
                                        .ifBlank { "Select provider" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (channelDescription.isBlank()) TextSecondary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                            DropdownMenu(
                                expanded = descMenuExpanded,
                                onDismissRequest = { descMenuExpanded = false }
                            ) {
                                getDescriptionOptionsForType(type).forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            channelDescription = option
                                            descMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Status toggle (Switch instead of dropdown)
                if (showStatusField) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Status",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = if (status) "Channel is active" else "Channel is inactive",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status) PesaMindGreen else TextSecondary
                            )
                        }
                        Switch(
                            checked = status,
                            onCheckedChange = { status = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PesaMindTeal,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        description.trim(),
                        type.trim(),
                        channelDescription.trim(),
                        status
                    )
                },
                enabled = isFormValid && !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal)
            ) {
                Text(if (isSaving) "Saving…" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Type Filter Dialog ───────────────────────────────────────────────────────

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
        title = {
            Text(
                "Filter by Type",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Show only channels of a specific type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = if (type.isBlank()) "Choose a type" else displayChannelType(type),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (type.isBlank()) TextSecondary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ChannelTypes.valid.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(displayChannelType(option)) },
                                onClick = { type = option; typeMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(type.trim()) },
                enabled = type.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PesaMindTeal)
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

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        ),
        color = TextSecondary
    )
}


private fun displayChannelType(type: String): String = when (type) {
    ChannelTypes.MOBILE_MONEY -> "Mobile Money"
    ChannelTypes.CASH         -> "Cash"
    ChannelTypes.BANK         -> "Bank"
    else                      -> type
}

private fun displayChannelTypeDescription(type: String, desc: String): String = when (type) {
    ChannelTypes.MOBILE_MONEY -> ChannelDescMobileMoney.normalizeOrNull(desc) ?: desc
    ChannelTypes.BANK         -> ChannelDescBank.normalizeOrNull(desc) ?: desc
    else                      -> desc
}

private fun getDescriptionOptionsForType(type: String): List<String> = when (type) {
    ChannelTypes.MOBILE_MONEY -> ChannelDescMobileMoney.valid
    ChannelTypes.BANK         -> ChannelDescBank.valid
    else                      -> emptyList()
}