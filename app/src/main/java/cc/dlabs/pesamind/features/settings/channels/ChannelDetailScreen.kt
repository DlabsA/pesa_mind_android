package cc.dlabs.pesamind.features.settings.channels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.ConfirmDialog
import cc.dlabs.pesamind.core.ui.DetailScreenTopBar
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.ShimmerBox
import cc.dlabs.pesamind.core.ui.SkeletonCard
import cc.dlabs.pesamind.core.ui.TransactionCard
import cc.dlabs.pesamind.core.ui.TransactionDetailSheet
import kotlinx.coroutines.launch

/**
 * One channel's card plus its transaction history — replaces the channels list's old
 * per-row Delete pill (`ChannelCard`'s "View" pill / card tap now lands here instead; delete
 * lives on this screen, behind a confirm dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    navController: NavHostController,
    channelId: String,
    vm: ChannelDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf(false) }
    var selectedTx by remember { mutableStateOf<TransactionDetails?>(null) }

    LaunchedEffect(channelId) { vm.load(channelId) }

    LaunchedEffect(state.channelDeleted) {
        if (state.channelDeleted) navController.popBackStack()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val channel = state.channel
            DetailScreenTopBar(
                title = channel?.name ?: "Channel",
                subtitle = channel?.let { displayChannelType(it.channelType) } ?: "",
                badge = "",
                onBack = { navController.popBackStack() },
                trailingContent = {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = getErrorColor().copy(alpha = 0.10f),
                    ) {
                        IconButton(onClick = { pendingDelete = true }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete channel",
                                tint = getErrorColor(),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.Space4.dp, vertical = Spacing.Space3.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.channel?.let { channel ->
                item(key = "summary") {
                    ChannelSummaryCard(
                        channel = channel,
                        onAddTransaction = {
                            navController.navigate(Routes.AddTransaction.createRoute(channel.id))
                        },
                    )
                }
            }

            when {
                state.isLoading && state.transactions.isEmpty() -> {
                    items(3, key = { "skeleton_$it" }) {
                        SkeletonCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShimmerBox(Modifier.size(46.dp), cornerRadius = 12.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    ShimmerBox(Modifier.width(130.dp).height(13.dp))
                                    Spacer(Modifier.height(6.dp))
                                    ShimmerBox(Modifier.width(80.dp).height(11.dp))
                                }
                                ShimmerBox(Modifier.width(70.dp).height(15.dp), cornerRadius = 6.dp)
                            }
                        }
                    }
                }

                state.transactions.isEmpty() -> {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.SwapVert,
                            title = "No transactions yet",
                            subtitle = "Transactions on this channel will appear here",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        )
                    }
                }

                else -> {
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionCard(tx = tx, onClick = { selectedTx = tx })
                    }
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (pendingDelete) {
        ConfirmDialog(
            title = "Delete Channel",
            message = "\"${state.channel?.name}\" will be permanently removed. This cannot be undone.",
            confirmLabel = "Delete",
            confirmingLabel = "Deleting…",
            isConfirming = state.isDeleting,
            onConfirm = {
                vm.deleteChannel()
                pendingDelete = false
            },
            onDismiss = { pendingDelete = false },
        )
    }

    selectedTx?.let { tx ->
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { selectedTx = null },
            sheetState = sheetState,
        ) {
            TransactionDetailSheet(
                tx = tx,
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) selectedTx = null
                    }
                },
            )
        }
    }
}

/** Read-only channel header — mirrors `ChannelCard`'s icon/name/balance/status presentation
 * (reusing its `channelTypeColor`/`channelTypeIcon`/`displayChannelType`/`StatusChip`/`asUgx`
 * helpers, made `internal` for this), minus the Edit/Delete pills: editing stays reachable
 * from the channels list, and delete lives in this screen's top bar instead. */
@Composable
private fun ChannelSummaryCard(
    channel: ChannelDetails,
    onAddTransaction: () -> Unit,
) {
    val typeColor = channelTypeColor(channel.channelType)
    val statusDotColor = if (channel.status) getTertiaryColor() else getErrorColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = typeColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ChannelTypeIconView(
                            icon = channelTypeIcon(channel.channelType, channel.channelDesc),
                            tint = typeColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
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
                        text = displayChannelType(channel.channelType),
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

                StatusChip(active = channel.status, dotColor = statusDotColor, pulseAlpha = 1f)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Available balance",
                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.availableBalance.asUgx(),
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (channel.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = channel.description,
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

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Add Transaction",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
