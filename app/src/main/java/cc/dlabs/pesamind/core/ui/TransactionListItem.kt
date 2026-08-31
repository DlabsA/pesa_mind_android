package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.dlabs.pesamind.core.data.DebtCreditRepository
import cc.dlabs.pesamind.core.data.SavingGoalRepository
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.features.lentborrowed.DebtCreditPicker
import cc.dlabs.pesamind.features.lentborrowed.DebtCreditViewModel
import cc.dlabs.pesamind.features.savinggoals.SavingGoalPicker
import cc.dlabs.pesamind.features.savinggoals.SavingGoalViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private enum class TransactionPurpose { NONE, LENT, BORROWED, SAVING_GOAL }

private val ugxFmt = NumberFormat.getNumberInstance(Locale.US)

private fun Double.toUgx() = ugxFmt.format(this)

/** Row for a single transaction — tap opens a read-only detail sheet, no inline delete
 * affordance. Shared between `TransactionListScreen` and `ChannelDetailScreen`. */
@Composable
fun TransactionCard(
    tx: TransactionDetails,
    onClick: () -> Unit,
) {
    val isIncome = tx.type.equals("income", ignoreCase = true)
    val accentColor =
        if (isIncome) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        }
    val accentBg =
        if (isIncome) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentBg,
                modifier = Modifier.size(46.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tx.channelDetailsName.ifBlank { "Transaction" },
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SyncStatusBadge(status = tx.syncStatus)
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = tx.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (tx.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = tx.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    letterSpacing = (-0.2).sp,
                                ),
                            ) {
                                append(if (isIncome) "+" else "−")
                                append(tx.amount.toUgx())
                            }
                        },
                )
                Text(
                    text = "UGX",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/** Transaction detail sheet, shown in a `ModalBottomSheet` on row tap. Shared between
 * `TransactionListScreen`, `ChannelDetailScreen`, and `LentBorrowedDetailScreen`. Premium users
 * can also change the transaction's Purpose (which debt/goal it's linked to, if any) here —
 * e.g. to fix a digital transaction that synced in with no purpose, or the wrong one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: TransactionDetails,
    onClose: () -> Unit,
    debtCreditViewModel: DebtCreditViewModel = viewModel(),
    savingGoalViewModel: SavingGoalViewModel = viewModel(),
) {
    val isIncome = tx.type.equals("income", ignoreCase = true)
    val accentColor = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val accentBg = if (isIncome) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val typeLabel = tx.type.replaceFirstChar { it.uppercase() }.ifBlank { "Transaction" }

    val debtCreditState by debtCreditViewModel.state.collectAsStateWithLifecycle()
    val savingGoalState by savingGoalViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var isPremium by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isPremium = AccountManager.isPremium() }

    // Optimistic local state — [tx] is a snapshot passed in at sheet-open time, not
    // live-observed, so a successful purpose change is reflected here directly rather than
    // relying on the caller to recompose this sheet with fresh data.
    var currentDebtCreditId by remember(tx.id) { mutableStateOf(tx.debtCreditId) }
    var currentSavingGoalId by remember(tx.id) { mutableStateOf(tx.savingGoalId) }
    var purpose by
        remember(tx.id) {
            mutableStateOf(
                when {
                    tx.debtCreditId != null -> TransactionPurpose.LENT // direction refined below once the debt loads
                    tx.savingGoalId != null -> TransactionPurpose.SAVING_GOAL
                    else -> TransactionPurpose.NONE
                },
            )
        }
    LaunchedEffect(currentDebtCreditId, debtCreditState.debts) {
        val debt = debtCreditState.debts.find { it.id == currentDebtCreditId }
        if (debt != null) purpose = if (debt.direction == "borrowed") TransactionPurpose.BORROWED else TransactionPurpose.LENT
    }

    var purposeDropdownExpanded by remember { mutableStateOf(false) }
    var showDebtPicker by remember { mutableStateOf(false) }
    var showGoalPicker by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    // Orchestrates a purpose change against the backend's link/unlink-only API (there is no
    // transaction-PATCH endpoint) — an old link must be explicitly unlinked before a new one is
    // attempted, since the backend rejects linking a transaction that's still linked elsewhere.
    fun applyPurposeChange(
        newDebtCreditId: String?,
        newSavingGoalId: String?,
    ) {
        val oldDebtCreditId = currentDebtCreditId
        val oldSavingGoalId = currentSavingGoalId
        scope.launch {
            isUpdating = true
            updateError = null
            try {
                if (oldDebtCreditId != null && oldDebtCreditId != newDebtCreditId) {
                    DebtCreditRepository.unlinkTransaction(oldDebtCreditId, tx.id)
                }
                if (oldSavingGoalId != null && oldSavingGoalId != newSavingGoalId) {
                    SavingGoalRepository.unlinkTransaction(oldSavingGoalId, tx.id)
                }
                if (newDebtCreditId != null && newDebtCreditId != oldDebtCreditId) {
                    DebtCreditRepository.linkTransaction(newDebtCreditId, tx.id)
                }
                if (newSavingGoalId != null && newSavingGoalId != oldSavingGoalId) {
                    SavingGoalRepository.linkTransaction(newSavingGoalId, tx.id)
                }
                currentDebtCreditId = newDebtCreditId
                currentSavingGoalId = newSavingGoalId
            } catch (e: Exception) {
                updateError = "Could not update purpose: ${e.message}"
            } finally {
                isUpdating = false
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = accentBg, modifier = Modifier.size(56.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text =
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                letterSpacing = (-0.3).sp,
                            ),
                        ) {
                            append(if (isIncome) "+" else "−")
                            append(tx.amount.toUgx())
                        }
                        withStyle(SpanStyle(fontSize = 14.sp, color = accentColor.copy(alpha = 0.6f))) {
                            append(" UGX")
                        }
                    },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            Surface(shape = RoundedCornerShape(999.dp), color = accentBg) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(Modifier.height(8.dp))

        DetailRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "Channel",
            value = tx.channelDetailsName.ifBlank { "—" },
        )
        DetailRow(
            icon = Icons.Outlined.Person,
            label = "From / Sender",
            value = tx.username.ifBlank { "—" },
        )
        if (tx.note.isNotBlank()) {
            DetailRow(
                icon = Icons.Outlined.Notes,
                label = "Note",
                value = tx.note,
            )
        }

        if (isPremium) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.dp))

            Text(
                text = "Purpose",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            val purposeOptions =
                listOf(
                    TransactionPurpose.NONE to "Normal",
                    TransactionPurpose.LENT to PesaMindStrings.DebtCredit.LENT_LABEL,
                    TransactionPurpose.BORROWED to PesaMindStrings.DebtCredit.BORROWED_LABEL,
                    TransactionPurpose.SAVING_GOAL to PesaMindStrings.SavingGoal.FEATURE_NAME,
                )
            val selectedLabel = purposeOptions.find { it.first == purpose }?.second ?: "Normal"

            ExposedDropdownMenuBox(
                expanded = purposeDropdownExpanded,
                onExpandedChange = { if (!isUpdating) purposeDropdownExpanded = !purposeDropdownExpanded },
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isUpdating,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                ExposedDropdownMenu(
                    expanded = purposeDropdownExpanded,
                    onDismissRequest = { purposeDropdownExpanded = false },
                ) {
                    purposeOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                purposeDropdownExpanded = false
                                when (value) {
                                    TransactionPurpose.NONE -> {
                                        purpose = TransactionPurpose.NONE
                                        applyPurposeChange(null, null)
                                    }
                                    TransactionPurpose.LENT, TransactionPurpose.BORROWED -> {
                                        purpose = value
                                        showDebtPicker = true
                                    }
                                    TransactionPurpose.SAVING_GOAL -> {
                                        purpose = value
                                        showGoalPicker = true
                                    }
                                }
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            if (purpose == TransactionPurpose.LENT || purpose == TransactionPurpose.BORROWED) {
                Spacer(Modifier.height(8.dp))
                val selectedName = debtCreditState.debts.find { it.id == currentDebtCreditId }?.counterpartyName
                OutlinedButton(onClick = { showDebtPicker = true }, enabled = !isUpdating, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedName ?: "Choose who this is with")
                }
            }
            if (purpose == TransactionPurpose.SAVING_GOAL) {
                Spacer(Modifier.height(8.dp))
                val selectedName = savingGoalState.goals.find { it.id == currentSavingGoalId }?.name
                OutlinedButton(onClick = { showGoalPicker = true }, enabled = !isUpdating, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedName ?: "Choose a goal")
                }
            }

            if (isUpdating) {
                Spacer(Modifier.height(6.dp))
                Text("Updating…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            updateError?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Close")
        }
    }

    if (showDebtPicker) {
        val direction = if (purpose == TransactionPurpose.BORROWED) "borrowed" else "lent"
        DebtCreditPicker(
            direction = direction,
            debts = debtCreditState.debts.filter { it.direction == direction },
            onSelect = { id ->
                showDebtPicker = false
                applyPurposeChange(id, null)
            },
            onDismiss = {
                showDebtPicker = false
                if (currentDebtCreditId == null) purpose = TransactionPurpose.NONE
            },
            onCreated = { newId ->
                showDebtPicker = false
                applyPurposeChange(newId, null)
            },
        )
    }
    if (showGoalPicker) {
        SavingGoalPicker(
            goals = savingGoalState.goals,
            onSelect = { id ->
                showGoalPicker = false
                applyPurposeChange(null, id)
            },
            onDismiss = {
                showGoalPicker = false
                if (currentSavingGoalId == null) purpose = TransactionPurpose.NONE
            },
            onCreated = { newId ->
                showGoalPicker = false
                applyPurposeChange(null, newId)
            },
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
