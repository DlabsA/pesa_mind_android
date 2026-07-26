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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.SkeletonCard
import cc.dlabs.pesamind.core.ui.SyncStatusBadge

// ─── Filter state enum ───────────────────────────────────────────────────────

private enum class FilterType { ALL, ACTIVE, INACTIVE, BY_TYPE }

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    navController: NavHostController,
    vm: ChannelViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showTypeFilterDialog by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(FilterType.ALL) }
    var currentTypeFilter by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ChannelDetails?>(null) }
    var editingChannel by remember { mutableStateOf<ChannelDetails?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val visibleChannels =
        remember(state.channels, searchQuery) {
            if (searchQuery.isBlank()) {
                state.channels
            } else {
                state.channels.filter { channel ->
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                        channel.description.contains(searchQuery, ignoreCase = true) ||
                        displayChannelType(channel.channelType).contains(searchQuery, ignoreCase = true)
                }
            }
        }

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
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Channels",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                ),
                        )
                        if (state.channels.isNotEmpty()) {
                            Text(
                                text = "${state.channels.size} channel${if (state.channels.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadChannels() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = getPrimaryColor(),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = {
                    Text(
                        "Add Channel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                modifier =
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = getPrimaryColor().copy(alpha = 0.25f),
                        spotColor = getPrimaryColor().copy(alpha = 0.35f),
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // ── Divider under top bar
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            )

            // ── Search field
            if (!state.isLoading && state.channels.isNotEmpty()) {
                ChannelSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

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
                onType = { showTypeFilterDialog = true },
            )

            // ── Content area
            when {
                state.isLoading -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerOffset by infiniteTransition.animateFloat(
                        initialValue = -1f,
                        targetValue = 2f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                        label = "shimmerOffset",
                    )
                    val shimmerBrush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                ),
                            start = Offset(shimmerOffset * 800f, 0f),
                            end = Offset((shimmerOffset + 1f) * 800f, 0f),
                        )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(3) {
                            SkeletonCard(
                                shape = RoundedCornerShape(18.dp),
                                accentBrush = shimmerBrush,
                                modifier =
                                    Modifier.shadow(
                                        2.dp,
                                        RoundedCornerShape(18.dp),
                                        ambientColor = getPrimaryColor().copy(0.05f),
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth(0.5f)
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(shimmerBrush),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(60.dp)
                                                .height(24.dp)
                                                .clip(CircleShape)
                                                .background(shimmerBrush),
                                    )
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(0.35f)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(shimmerBrush),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(shimmerBrush),
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
                                            .background(shimmerBrush),
                                    )
                                    Box(
                                        Modifier
                                            .width(80.dp)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(shimmerBrush),
                                    )
                                }
                            }
                        }
                    }
                }
                state.channels.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = "No channels yet",
                        subtitle = "Add your first financial channel to start\ntracking payments and transfers.",
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        iconShape = RoundedCornerShape(24.dp),
                        iconTint = getPrimaryColor(),
                        iconBackground = getPrimaryColor().copy(alpha = 0.10f),
                        subtitleStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        textSpacing = 6.dp,
                        actionSpacing = 24.dp,
                        action = {
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Add Channel",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        },
                    )
                visibleChannels.isEmpty() -> ChannelNoMatchesState(onClear = { searchQuery = "" })
                else -> {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(start = Spacing.Space4.dp, top = Spacing.Space2.dp, end = Spacing.Space4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleChannels, key = { it.id }) { channel ->
                            ChannelCard(
                                item = channel,
                                onEdit = { editingChannel = channel },
                                onDelete = { pendingDelete = channel },
                                onToggleSms = { vm.toggleSmsNotification(channel.id) },
                            )
                        }
                        item { Spacer(Modifier.height(96.dp)) }
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
            },
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
                    status = status,
                )
                editingChannel = null
            },
            isTypeEditable = false,
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
            onDismiss = { pendingDelete = null },
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
            },
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
    onType: () -> Unit,
) {
    val chipColors =
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = getPrimaryColor(),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        )
    Row(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = activeFilter == FilterType.ALL,
            onClick = onAll,
            label = { Text("All") },
            colors = chipColors,
        )
        FilterChip(
            selected = activeFilter == FilterType.ACTIVE,
            onClick = onActive,
            label = { Text("Active") },
            colors = chipColors,
        )
        FilterChip(
            selected = activeFilter == FilterType.INACTIVE,
            onClick = onInactive,
            label = { Text("Inactive") },
            colors = chipColors,
        )
        FilterChip(
            selected = activeFilter == FilterType.BY_TYPE,
            onClick = onType,
            label = {
                Text(
                    if (currentTypeFilter.isBlank()) {
                        "By Type"
                    } else {
                        displayChannelType(currentTypeFilter)
                    },
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = chipColors,
        )
    }
}

// ─── Search Field ─────────────────────────────────────────────────────────────

@Composable
private fun ChannelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Search channels", style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = getPrimaryColor(),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
    )
}

// ─── No Matches State ─────────────────────────────────────────────────────────

@Composable
private fun ChannelNoMatchesState(onClear: () -> Unit) {
    EmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "No matching channels",
        subtitle = "Try a different search term",
        modifier = Modifier.fillMaxSize().padding(32.dp),
        iconSize = 36.dp,
        iconContentSize = 36.dp,
        iconBackground = Color.Transparent,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        subtitleStyle = MaterialTheme.typography.bodySmall,
        iconSpacing = 14.dp,
        textSpacing = 6.dp,
        actionSpacing = 16.dp,
        action = {
            TextButton(onClick = onClear) {
                Text("Clear search")
            }
        },
    )
}

// ─── Channel type → icon / color ──────────────────────────────────────────────

@Composable
private fun channelTypeColor(type: String): Color =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> getTertiaryColor()
        ChannelTypes.BANK -> MaterialTheme.colorScheme.secondary
        else -> getPrimaryColor()
    }

private fun channelTypeIcon(type: String): ImageVector =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> Icons.Outlined.PhoneAndroid
        ChannelTypes.BANK -> Icons.Outlined.AccountBalance
        else -> Icons.Outlined.Payments
    }

// ─── Channel Card ─────────────────────────────────────────────────────────────

@Composable
fun ChannelCard(
    item: ChannelDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSms: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    val statusDotColor = if (item.status) getTertiaryColor() else getErrorColor()
    val statusChipBg =
        if (item.status) {
            getTertiaryColor().copy(alpha = 0.10f)
        } else {
            getErrorColor().copy(alpha = 0.09f)
        }
    val typeColor = channelTypeColor(item.channelType)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = getPrimaryColor().copy(alpha = 0.07f),
                    spotColor = getPrimaryColor().copy(alpha = 0.13f),
                ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
        ) {
            // Type-colored accent strip
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                        .background(typeColor),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
            ) {
                // Type icon + name + status chip + overflow menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = typeColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = channelTypeIcon(item.channelType),
                                contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = displayChannelType(item.channelType),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.25.sp,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Sync-status badge (ADR-0004 Slice A3) + status chip share one
                    // `spacedBy` Row — SyncStatusBadge renders nothing once synced, and
                    // `spacedBy` only adds space *between* children that actually exist, so a
                    // fully-synced channel's card keeps the original single 8dp gap here
                    // instead of accumulating a second fixed Spacer's worth of dead space.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SyncStatusBadge(status = item.syncStatus)
                        Surface(shape = CircleShape, color = statusChipBg) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .alpha(if (item.status) pulseAlpha else 0.55f)
                                            .background(statusDotColor, CircleShape),
                                )
                                Text(
                                    text = if (item.status) "Active" else "Inactive",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    color = statusDotColor,
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = getErrorColor()) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = getErrorColor(),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }

                // Description inset block
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = item.description,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // SMS Notification Toggle (only for non-cash channels)
                if (item.channelType != ChannelTypes.CASH) {
                    Spacer(Modifier.height(12.dp))
                    SmsToggleRow(
                        enabled = item.smsNotificationEnabled,
                        onToggle = onToggleSms,
                    )
                }
            }
        }
    }
}

// ─── SMS Toggle Row ───────────────────────────────────────────────────────────

@Composable
private fun SmsToggleRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val tint = if (enabled) getPrimaryColor() else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor =
        if (enabled) {
            getPrimaryColor().copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SMS Notifications",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enabled) "Auto-detecting transactions" else "Manual entry only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = getPrimaryColor(),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        uncheckedBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}

// ─── Delete Confirm Dialog ────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    channelName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = getErrorColor().copy(alpha = 0.10f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = getErrorColor(),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        title = {
            Text(
                "Delete Channel",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Text(
                text = "\"$channelName\" will be permanently removed. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getErrorColor()),
            ) {
                Text(if (isDeleting) "Deleting…" else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// New imports for this version:
//   androidx.compose.ui.window.Dialog
//   androidx.compose.ui.window.DialogProperties
//   androidx.compose.foundation.layout.FlowRow
//   androidx.compose.foundation.layout.ExperimentalLayoutApi
//   androidx.compose.foundation.layout.heightIn
//   androidx.compose.foundation.layout.size
//   androidx.compose.material3.FilterChip
//   androidx.compose.material3.FilterChipDefaults
//   androidx.compose.material3.HorizontalDivider
//   androidx.compose.material3.IconButton
//   androidx.compose.material.icons.rounded.Check
//   androidx.compose.material.icons.rounded.Close

@OptIn(ExperimentalLayoutApi::class)
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
    showStatusField: Boolean = true,
    subtitle: String? = null,
) {
    val normalizedInitialType =
        remember(initialType) {
            ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH
        }

    // Rehydrate country + number from the stored description when editing mobile money.
    val (initialCountry, initialNumber) =
        remember(title, initialDescription, normalizedInitialType) {
            if (normalizedInitialType == ChannelTypes.MOBILE_MONEY && initialDescription.isNotBlank()) {
                val match = COUNTRY_CODES.firstOrNull { initialDescription.startsWith(it.code) }
                if (match != null) {
                    match to initialDescription.removePrefix(match.code).filter { it.isDigit() }
                } else {
                    COUNTRY_CODES[0] to initialDescription.filter { it.isDigit() }
                }
            } else {
                COUNTRY_CODES[0] to ""
            }
        }

    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var channelDescription by remember(title, initialDescription) { mutableStateOf("") }
    var type by remember(title, normalizedInitialType) { mutableStateOf(normalizedInitialType) }
    var status by remember(title, initialStatus) { mutableStateOf(initialStatus) }
    var mobileNumber by remember(title, initialDescription) { mutableStateOf(initialNumber) }
    var selectedCountry by remember(title, initialDescription) { mutableStateOf(initialCountry) }
    var attemptedSave by remember(title) { mutableStateOf(false) }

    // ---- Validation ------------------------------------------------------
    val trimmedName = name.trim()
    val providerShown = type != ChannelTypes.CASH && type.isNotBlank()
    val digits = mobileNumber.filter { it.isDigit() }
    val nameMissing = trimmedName.isBlank()
    val providerMissing = providerShown && channelDescription.isBlank()
    val mobileMissing = type == ChannelTypes.MOBILE_MONEY && digits.isBlank()
    val isFormValid = !nameMissing && !providerMissing && !mobileMissing

    // ---- Derived values (computed, never written to state in composition) -
    val effectiveDescription =
        when (type) {
            ChannelTypes.MOBILE_MONEY -> "${selectedCountry.code}$digits"
            else -> description
        }
    val effectiveProvider = if (type == ChannelTypes.CASH) "" else channelDescription

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(max = 640.dp),
        ) {
            Column {
                // ---- Header (sticky) ----------------------------------------
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // ---- Body (scrollable) --------------------------------------
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space5.dp),
                ) {
                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Channel name") },
                        singleLine = true,
                        isError = attemptedSave && nameMissing,
                        supportingText =
                            if (attemptedSave && nameMissing) {
                                { Text("Name is required") }
                            } else {
                                null
                            },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )

                    // Channel type
                    FieldSection(label = "Channel type") {
                        if (isTypeEditable) {
                            ChoiceChipGroup(
                                options = ChannelTypes.valid,
                                selected = type,
                                optionLabel = { displayChannelType(it) },
                                onSelect = {
                                    if (it != type) {
                                        type = it
                                        channelDescription = ""
                                    }
                                },
                            )
                        } else {
                            ReadOnlyPill(text = displayChannelType(type))
                        }
                    }

                    // Provider (non-cash only)
                    if (providerShown) {
                        FieldSection(
                            label = "Provider",
                            errorText = if (attemptedSave && providerMissing) "Select a provider" else null,
                        ) {
                            if (isTypeEditable) {
                                ProviderDropdown(
                                    options = getDescriptionOptionsForType(type),
                                    selected = channelDescription,
                                    onSelect = { channelDescription = it },
                                    isError = attemptedSave && providerMissing,
                                )
                            } else {
                                ReadOnlyPill(
                                    text = displayChannelTypeDescription(type, channelDescription),
                                )
                            }
                        }
                    }

                    // Number / free-text description
                    if (type == ChannelTypes.MOBILE_MONEY) {
                        FieldSection(
                            label = "Mobile money number",
                            errorText =
                                if (attemptedSave && mobileMissing) {
                                    "Enter a valid number"
                                } else {
                                    null
                                },
                        ) {
                            MobileMoneyNumberField(
                                phoneNumber = mobileNumber,
                                onPhoneNumberChange = { mobileNumber = it },
                                selectedCountry = selectedCountry,
                                onCountryChange = { selectedCountry = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }

                    // Status
                    if (showStatusField) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (status) {
                                    getPrimaryColor().copy(alpha = 0.06f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (status) {
                                        getPrimaryColor().copy(alpha = 0.30f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                                    },
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Status",
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                            ),
                                    )
                                    Text(
                                        text = if (status) "Channel is active" else "Channel is inactive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (status) {
                                                getTertiaryColor()
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                                Switch(
                                    checked = status,
                                    onCheckedChange = { status = it },
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = getPrimaryColor(),
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // ---- Footer (sticky) ----------------------------------------
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            attemptedSave = true
                            if (isFormValid) {
                                onConfirm(
                                    trimmedName,
                                    effectiveDescription.trim(),
                                    type.trim(),
                                    effectiveProvider.trim(),
                                    status,
                                )
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
                    ) {
                        Text(if (isSaving) "Saving…" else confirmLabel)
                    }
                }
            }
        }
    }
}

/** Small labelled section wrapper: muted label above, optional error line below. */
@Composable
private fun FieldSection(
    label: String,
    errorText: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        if (!errorText.isNullOrBlank()) {
            Text(
                errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Wrapping row of selectable chips, used for both channel type and provider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChipGroup(
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

/** Dropdown menu for selecting a provider (bank or mobile money). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
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

/** Static tinted pill for non-editable type/provider values. */
@Composable
private fun ReadOnlyPill(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = getPrimaryColor().copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = getPrimaryColor(),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

// ─── Type Filter Dialog ───────────────────────────────────────────────────────

@Composable
private fun TypeFilterDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Show only channels of a specific type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Text(
                            text = if (type.isBlank()) "Choose a type" else displayChannelType(type),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (type.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        ChannelTypes.valid.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(displayChannelType(option)) },
                                onClick = {
                                    type = option
                                    typeMenuExpanded = false
                                },
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
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun displayChannelType(type: String): String =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> "Mobile Money"
        ChannelTypes.CASH -> "Cash"
        ChannelTypes.BANK -> "Bank"
        else -> type
    }

private fun displayChannelTypeDescription(
    type: String,
    desc: String,
): String =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> ChannelDescMobileMoney.normalizeOrNull(desc) ?: desc
        ChannelTypes.BANK -> ChannelDescBank.normalizeOrNull(desc) ?: desc
        else -> desc
    }

private fun getDescriptionOptionsForType(type: String): List<String> =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> ChannelDescMobileMoney.valid
        ChannelTypes.BANK -> ChannelDescBank.valid
        else -> emptyList()
    }
