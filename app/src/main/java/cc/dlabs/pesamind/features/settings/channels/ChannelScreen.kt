package cc.dlabs.pesamind.features.settings.channels

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.R
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.ChoiceChipGroup
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.ProviderDropdown
import cc.dlabs.pesamind.core.ui.SkeletonCard
import cc.dlabs.pesamind.core.ui.SyncStatusBadge
import cc.dlabs.pesamind.core.ui.asUgx

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
                            text = "Accounts",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                ),
                        )
                        if (state.channels.isNotEmpty()) {
                            Text(
                                text = "${state.channels.size} account${if (state.channels.size != 1) "s" else ""}",
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
                        "Add Account",
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
                    .padding(padding)
                    .imePadding(),
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
                                smsCaptureLocked = !state.isPremium,
                                onEdit = { editingChannel = channel },
                                onView = {
                                    navController.navigate(Routes.ChannelDetail.createRoute(channel.id))
                                },
                                onAddTransaction = {
                                    navController.navigate(Routes.AddTransaction.createRoute(channel.id))
                                },
                                onToggleSmsCapture = { vm.toggleSmsNotification(channel.id) },
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
            channelKey = null,
            title = "New Account",
            confirmLabel = "Create",
            initialStatus = true,
            showStatusField = false,
            isSaving = state.isSaving,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, type, channelDescription, status, openingBalance, accountNumber ->
                vm.createChannel(name, description, type, channelDescription, status, openingBalance, accountNumber)
                showCreateDialog = false
            },
        )
    }

    editingChannel?.let { channel ->
        ChannelFormDialog(
            channelKey = channel.id,
            title = "Edit Account",
            confirmLabel = "Save Changes",
            initialName = channel.name,
            initialDescription = channel.description,
            initialType = channel.channelType,
            initialProvider = channel.channelDesc,
            initialStatus = channel.status,
            isSaving = state.isSaving,
            onDismiss = { editingChannel = null },
            onConfirm = { name, description, _, channelDescription, status, _, accountNumber ->
                vm.updateChannel(
                    id = channel.id,
                    name = name,
                    description = description,
                    channelDescription = channelDescription,
                    status = status,
                    accountNumber = accountNumber,
                )
                editingChannel = null
            },
            isTypeEditable = false,
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
            Text("Search accounts", style = MaterialTheme.typography.bodyMedium)
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
        title = "No matching accounts",
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
internal fun channelTypeColor(type: String): Color =
    when (type) {
        ChannelTypes.MOBILE_MONEY -> getTertiaryColor()
        ChannelTypes.BANK -> MaterialTheme.colorScheme.secondary
        else -> getPrimaryColor()
    }

internal sealed class ChannelIcon {
    data class Vector(val icon: ImageVector) : ChannelIcon()

    data class Drawable(
        @DrawableRes val resId: Int,
    ) : ChannelIcon()
}

internal fun channelTypeIcon(
    type: String,
    subtype: String,
): ChannelIcon =
    when (type) {
        ChannelTypes.MOBILE_MONEY ->
            when (subtype) {
                ChannelDescMobileMoney.AIRTELMONEY -> ChannelIcon.Drawable(R.drawable.airtel)
                ChannelDescMobileMoney.MTNMOBILEMONEY -> ChannelIcon.Drawable(R.drawable.momo)
                else -> ChannelIcon.Vector(Icons.Outlined.Payments)
            }
        ChannelTypes.BANK -> ChannelIcon.Vector(Icons.Outlined.AccountBalance)
        else -> ChannelIcon.Vector(Icons.Outlined.Payments)
    }

/**
 * Renders whichever icon kind [channelTypeIcon] returned — a drawable resource (ID cast to
 * `ImageVector` would crash; it needs the `painter` `Icon` overload instead) or a vector.
 * [modifier] sizes the badge itself (e.g. `Modifier.fillMaxSize()` to match its containing
 * `Surface`) — only brand-logo [ChannelIcon.Drawable] images actually fill that bounds; the
 * generic outlined [ChannelIcon.Vector] fallback icons stay their original small, centered
 * size regardless, matching how they always looked before this badge got resized for logos.
 */
@Composable
internal fun ChannelTypeIconView(
    icon: ChannelIcon,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (icon) {
        is ChannelIcon.Vector ->
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(imageVector = icon.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        is ChannelIcon.Drawable ->
            // Brand logos (Airtel/MTN) render in their own colors, not typeColor-tinted.
            Icon(
                painter = painterResource(id = icon.resId),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = modifier,
            )
    }
}

// ─── Balance formatting ───────────────────────────────────────────────────────
// Moved to core/ui/CurrencyFormat.kt — a formatter imported by onboarding,
// channel detail and subscription checkout does not belong in a feature package.
// Imported above as cc.dlabs.pesamind.core.ui.asUgx.

// ─── Channel Card ─────────────────────────────────────────────────────────────
@Composable
fun ChannelCard(
    item: ChannelDetails,
    smsCaptureLocked: Boolean,
    onEdit: () -> Unit,
    onView: () -> Unit,
    onAddTransaction: () -> Unit,
    onToggleSmsCapture: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    val statusDotColor = if (item.status) getTertiaryColor() else getErrorColor()
    val typeColor = channelTypeColor(item.channelType)

    Card(
        onClick = onView,
        modifier =
            Modifier
                .fillMaxWidth()
                // Soft neutral lift instead of the old primary-tinted glow — the
                // reference separates the card from the canvas with shadow + a
                // hairline, not colour.
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        // No more left accent strip — channel type now lives entirely in the
        // icon-circle tint, keeping the surface clean like the reference.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // ── Header: type icon + name + muted status chip ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.Medium.dp),
                    color = typeColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(46.dp),
                ) {
                    ChannelTypeIconView(
                        icon = channelTypeIcon(item.channelType, item.channelDesc),
                        tint = typeColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
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
                    if (!item.accountNumber.isNullOrBlank()) {
                        Text(
                            text = item.accountNumber,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SyncStatusBadge(status = item.syncStatus)
                    StatusChip(
                        active = item.status,
                        dotColor = statusDotColor,
                        pulseAlpha = pulseAlpha,
                    )
                }
            }

            // ── Balance (the hero) ───────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Available balance",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.availableBalance.asUgx(),
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // ── SMS auto-capture: real per-channel control, not a CASH concept ──
            if (item.channelType != ChannelTypes.CASH) {
                Spacer(Modifier.height(10.dp))
                SmsCaptureRow(
                    checked = item.smsNotificationEnabled && !smsCaptureLocked,
                    locked = smsCaptureLocked,
                    onToggle = onToggleSmsCapture,
                )
            }

            // ── Action row: solid neutral pills ──────────────────────────────
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelActionPill(
                    icon = Icons.Filled.Add,
                    label = "Add",
                    onClick = onAddTransaction,
                    modifier = Modifier.weight(1f),
                )
                ChannelActionPill(
                    icon = Icons.Outlined.Edit,
                    label = "Edit",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                )
                ChannelActionPill(
                    icon = Icons.Outlined.Visibility,
                    label = "View",
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Description inset ────────────────────────────────────────────
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
        }
    }
}

/**
 * The real control behind whether this channel's incoming SMS get auto-captured into
 * transactions — [checked] mirrors [cc.dlabs.pesamind.core.network.models.ChannelDetails
 * .smsNotificationEnabled], the exact field [cc.dlabs.pesamind.core.storage.ChannelManager
 * .isSmsAllowedForSender] reads at parse time. [locked] means the account isn't currently
 * Premium/on an active trial: the switch always renders off and non-interactive in that state
 * (regardless of the channel's own stored preference, which is left untouched in the database
 * so it's restored automatically on upgrade — see [ChannelViewModel.toggleSmsNotification]).
 * Never shown for CASH channels, which have no SMS source to capture.
 */
@Composable
private fun SmsCaptureRow(
    checked: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.Notifications,
                contentDescription = null,
                tint =
                    if (locked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        getPrimaryColor()
                    },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Account auto tracking",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                )
                if (locked) {
                    Text(
                        "Requires Premium",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = !locked,
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

/**
 * Muted status chip — neutral raised background so the only colour is the dot,
 * matching the monochrome feel of the reference. Active dots pulse.
 */
@Composable
internal fun StatusChip(
    active: Boolean,
    dotColor: Color,
    pulseAlpha: Float,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .alpha(if (active) pulseAlpha else 0.55f)
                        .background(dotColor, CircleShape),
            )
            Text(
                text = if (active) "Active" else "Inactive",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Solid neutral pill used in the channel card's action row (Add / Edit / Delete). */
@Composable
private fun ChannelActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Form State Holder ────────────────────────────────────────────────────────

/**
 * Consolidated form state for ChannelFormDialog, enabling testable rehydration,
 * type-switching, and round-trip fidelity. Stores original values to restore
 * when switching back to the initial type.
 */
private data class ChannelFormState(
    val name: String = "",
    val type: String = ChannelTypes.CASH,
    // Free-text for bank/cash
    val description: String = "",
    // Provider selection
    val channelDescription: String = "",
    // Digits only for mobile money
    val mobileNumber: String = "",
    val selectedCountry: CountryCode = COUNTRY_CODES[0],
    val status: Boolean = true,
    // Only used/shown on "Add channel" (channelKey == null) — editing an existing channel
    // has no code path to adjust availableBalance, only to set it at creation time.
    val openingBalanceText: String = "",
    val attemptedSave: Boolean = false,
    // ---- Originals (for restoration on type switch back) ----
    val originalType: String = ChannelTypes.CASH,
    val originalDescription: String = "",
    val originalChannelDescription: String = "",
    val originalMobileNumber: String = "",
    val originalSelectedCountry: CountryCode = COUNTRY_CODES[0],
)

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
    channelKey: String?,
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialDescription: String = "",
    initialType: String = "",
    initialProvider: String = "",
    initialStatus: Boolean = true,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    // Trailing String? is the account/receiving number — for MOBILE_MONEY this is the same
    // value as `effectiveDescription` (the number the user just typed in this form), so
    // ChannelRepository can also populate `accountNumber`/`receivingNumber` for SMS-matching
    // disambiguation without a second input field. Null for CASH/BANK (no dedicated number
    // field in this form today).
    onConfirm: (String, String, String, String, Boolean, Double, String?) -> Unit,
    isTypeEditable: Boolean = true,
    showStatusField: Boolean = true,
    subtitle: String? = null,
) {
    var formState by remember(channelKey) {
        mutableStateOf(
            ChannelFormState(
                name = initialName,
                type = ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH,
                description = initialDescription,
                channelDescription =
                    displayChannelTypeDescription(
                        ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH,
                        initialProvider,
                    ),
                mobileNumber = "",
                selectedCountry = COUNTRY_CODES[0],
                status = initialStatus,
                attemptedSave = false,
                originalType = ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH,
                originalDescription = initialDescription,
                originalChannelDescription =
                    displayChannelTypeDescription(
                        ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH,
                        initialProvider,
                    ),
                originalMobileNumber = "",
                originalSelectedCountry = COUNTRY_CODES[0],
            ),
        )
    }

    // Parse mobile-money fields from initialDescription on mount.
    LaunchedEffect(initialType, initialDescription) {
        val normalizedType = ChannelTypes.normalizeOrNull(initialType) ?: ChannelTypes.CASH
        if (normalizedType == ChannelTypes.MOBILE_MONEY && initialDescription.isNotBlank()) {
            val match = COUNTRY_CODES.firstOrNull { initialDescription.startsWith(it.code) }
            if (match != null) {
                val digits = initialDescription.removePrefix(match.code).filter { it.isDigit() }
                formState =
                    formState.copy(
                        mobileNumber = digits,
                        selectedCountry = match,
                        originalMobileNumber = digits,
                        originalSelectedCountry = match,
                    )
            } else {
                val digits = initialDescription.filter { it.isDigit() }
                formState =
                    formState.copy(
                        mobileNumber = digits,
                        originalMobileNumber = digits,
                    )
            }
        }
    }

    val handleTypeChange: (String) -> Unit = { newType ->
        if (newType != formState.type) {
            formState =
                if (newType == formState.originalType) {
                    // Switching back to original type — restore all originals.
                    formState.copy(
                        type = newType,
                        description = formState.originalDescription,
                        channelDescription = formState.originalChannelDescription,
                        mobileNumber = formState.originalMobileNumber,
                        selectedCountry = formState.originalSelectedCountry,
                        attemptedSave = false,
                    )
                } else {
                    // Switching to a different type — reset irrelevant fields.
                    formState.copy(
                        type = newType,
                        description =
                            if (newType == ChannelTypes.CASH || newType == ChannelTypes.BANK) {
                                ""
                            } else {
                                formState.description
                            },
                        mobileNumber =
                            if (newType != ChannelTypes.MOBILE_MONEY) {
                                ""
                            } else {
                                formState.mobileNumber
                            },
                        selectedCountry =
                            if (newType != ChannelTypes.MOBILE_MONEY) {
                                COUNTRY_CODES[0]
                            } else {
                                formState.selectedCountry
                            },
                        channelDescription = "",
                        attemptedSave = false,
                    )
                }
        }
    }

    // ---- Validation (computed, never written to state in composition) ----
    val trimmedName = formState.name.trim()
    val providerShown = formState.type != ChannelTypes.CASH && formState.type.isNotBlank()
    val providerOptions = getDescriptionOptionsForType(formState.type)
    val providerRequired = providerShown && providerOptions.isNotEmpty()
    val digits = formState.mobileNumber.filter { it.isDigit() }
    val nameMissing = trimmedName.isBlank()
    val providerMissing = providerRequired && formState.channelDescription.isBlank()
    val mobileMissing = formState.type == ChannelTypes.MOBILE_MONEY && digits.isBlank()
    val isFormValid = !nameMissing && !providerMissing && !mobileMissing

    // ---- Derived values (computed, never written to state in composition) -
    val effectiveDescription =
        when (formState.type) {
            ChannelTypes.MOBILE_MONEY -> "${formState.selectedCountry.code}$digits"
            else -> formState.description
        }
    val effectiveProvider = if (formState.type == ChannelTypes.CASH) "" else formState.channelDescription
    // Blank/unparseable text is treated as "no opening balance entered" (0.0), same as
    // ChannelOnboardingViewModel.finish's draft.openingBalanceText.toDoubleOrNull() ?: 0.0 —
    // never shown/settable outside "Add channel" (channelKey == null), so it's always 0.0 here
    // for edits regardless of what's typed.
    val effectiveOpeningBalance = formState.openingBalanceText.toDoubleOrNull() ?: 0.0

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
                    .heightIn(max = 640.dp)
                    .imePadding(),
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
                        value = formState.name,
                        onValueChange = { formState = formState.copy(name = it) },
                        label = { Text("Channel name") },
                        singleLine = true,
                        isError = formState.attemptedSave && nameMissing,
                        supportingText =
                            if (formState.attemptedSave && nameMissing) {
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
                                selected = formState.type,
                                optionLabel = { displayChannelType(it) },
                                onSelect = handleTypeChange,
                            )
                        } else {
                            ReadOnlyPill(text = displayChannelType(formState.type))
                        }
                    }

                    // Provider (non-cash only, shown only if options exist) — always editable,
                    // even when the channel type itself (isTypeEditable) is locked on edit.
                    if (providerShown && providerOptions.isNotEmpty()) {
                        FieldSection(
                            label = "Provider",
                            errorText = if (formState.attemptedSave && providerMissing) "Select a provider" else null,
                        ) {
                            ProviderDropdown(
                                options = providerOptions,
                                selected = formState.channelDescription,
                                onSelect = { formState = formState.copy(channelDescription = it) },
                                isError = formState.attemptedSave && providerMissing,
                            )
                        }
                    }

                    // Number / free-text description
                    if (formState.type == ChannelTypes.MOBILE_MONEY) {
                        FieldSection(
                            label = "Mobile money number",
                            errorText =
                                if (formState.attemptedSave && mobileMissing) {
                                    "Enter a valid number"
                                } else {
                                    null
                                },
                        ) {
                            MobileMoneyNumberField(
                                phoneNumber = formState.mobileNumber,
                                onPhoneNumberChange = {
                                    formState = formState.copy(mobileNumber = it)
                                },
                                selectedCountry = formState.selectedCountry,
                                onCountryChange = {
                                    formState = formState.copy(selectedCountry = it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = formState.description,
                            onValueChange = { formState = formState.copy(description = it) },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }

                    // Opening balance — creation-only: ChannelRepository.updateChannel has no
                    // code path to adjust availableBalance, so editing an existing channel would
                    // be misleading here.
                    if (channelKey == null) {
                        OutlinedTextField(
                            value = formState.openingBalanceText,
                            onValueChange = { formState = formState.copy(openingBalanceText = it) },
                            label = { Text("Opening balance (optional)") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }

                    // Status
                    if (showStatusField) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (formState.status) {
                                    getPrimaryColor().copy(alpha = 0.06f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (formState.status) {
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
                                        text = if (formState.status) "Channel is active" else "Channel is inactive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (formState.status) {
                                                getTertiaryColor()
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                                Switch(
                                    checked = formState.status,
                                    onCheckedChange = { formState = formState.copy(status = it) },
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
                            formState = formState.copy(attemptedSave = true)
                            if (isFormValid) {
                                onConfirm(
                                    trimmedName,
                                    effectiveDescription.trim(),
                                    formState.type.trim(),
                                    effectiveProvider.trim(),
                                    formState.status,
                                    effectiveOpeningBalance,
                                    if (formState.type == ChannelTypes.MOBILE_MONEY) effectiveDescription.trim() else null,
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

internal fun displayChannelType(type: String): String =
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
