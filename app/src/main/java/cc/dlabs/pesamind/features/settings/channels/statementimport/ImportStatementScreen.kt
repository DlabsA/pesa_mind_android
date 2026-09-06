package cc.dlabs.pesamind.features.settings.channels.statementimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.network.models.StatementImportSummary
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getErrorColor
import cc.dlabs.pesamind.core.theme.getPrimaryColor
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.core.ui.ConfirmDialog
import cc.dlabs.pesamind.core.ui.asUgx
import cc.dlabs.pesamind.features.settings.channels.StatementImportSupport

/**
 * Upload one bank / mobile-money statement for a channel. The server does the parsing and
 * creates the transactions; this screen only picks the file, reports the summary, and resolves
 * the one case the server can't decide alone — a statement whose account number doesn't match
 * the channel's.
 */
@Composable
fun ImportStatementScreen(
    navController: NavHostController,
    channelId: String,
    vm: ImportStatementViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val channelDesc = state.channel?.channelDesc

    LaunchedEffect(channelId) { vm.load(channelId) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::onFilePicked)
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
            BackStyleHeader(
                title = "Import statement",
                onBack = { navController.popBackStack() },
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = Spacing.Space2.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.Space3.dp, vertical = Spacing.Space3.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            val summary = state.result
            if (summary != null) {
                item(key = "summary") { ImportSummaryCard(summary) }
                item(key = "done") {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            } else {
                item(key = "explainer") {
                    ExplainerCard(
                        provider = channelDesc.orEmpty(),
                        formats = StatementImportSupport.acceptedFormatsLabel(channelDesc),
                    )
                }
                item(key = "picker") {
                    PickFileSection(
                        pickedName = state.pickedName,
                        isUploading = state.isUploading,
                        // Both the picker's filter and the accepted-extension check key off the
                        // channel's provider, so don't let the user pick before it has loaded —
                        // a Stanbic CSV would otherwise be rejected as PDF-only on the first frame.
                        enabled = state.channel != null,
                        onChooseFile = { picker.launch(StatementImportSupport.pickerMimeTypes(channelDesc)) },
                        onImport = { vm.upload() },
                    )
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(Spacing.Space6.dp)) }
        }
    }

    state.pendingSync?.accountMismatch?.let { mismatch ->
        ConfirmDialog(
            title = "Different account number",
            message =
                "This statement is for account ${mismatch.statementAccount}, but this account is " +
                    "set to ${mismatch.channelAccount}. Update it to match and finish the import?",
            confirmLabel = "Update and finish",
            confirmingLabel = "Finishing…",
            isConfirming = state.isUploading,
            onConfirm = { vm.confirmAccountSync() },
            onDismiss = { vm.dismissSync() },
            icon = Icons.Outlined.UploadFile,
            tint = getPrimaryColor(),
        )
    }
}

@Composable
private fun ExplainerCard(
    provider: String,
    formats: String,
) {
    OutlinedCardShell {
        Column(Modifier.padding(Spacing.Space4.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp)) {
            Text(
                text = "Add your history in one go",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    if (provider.isBlank()) {
                        "Upload a statement file and we'll read the transactions out of it."
                    } else {
                        "Upload a $provider statement and we'll read the transactions out of it — " +
                            "no typing. Accepted format: $formats."
                    },
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Anything already recorded is matched and skipped, so importing twice is safe.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickFileSection(
    pickedName: String?,
    isUploading: Boolean,
    enabled: Boolean,
    onChooseFile: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp)) {
        if (pickedName != null) {
            OutlinedCardShell {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.Space3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = getPrimaryColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.Space2.dp))
                    Text(
                        text = pickedName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onChooseFile,
            enabled = enabled && !isUploading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
        ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.Space2.dp - 2.dp))
            Text(
                text = if (pickedName == null) "Choose file" else "Choose a different file",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        if (pickedName != null) {
            Button(
                onClick = onImport,
                enabled = enabled && !isUploading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.Medium.dp + 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = getPrimaryColor()),
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Spacing.Space2.dp))
                    Text("Reading your statement…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(
                        "Import",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSummaryCard(summary: StatementImportSummary) {
    OutlinedCardShell {
        Column(Modifier.padding(Spacing.Space4.dp)) {
            Text(
                text = "Imported",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${summary.importedCount}",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1).sp,
                    ),
                color = getTertiaryColor(),
            )
            Text(
                text = if (summary.importedCount == 1) "transaction added" else "transactions added",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.Space3.dp))

            SummaryRow("Already recorded", "${summary.duplicateCount}")
            if (summary.unresolvedCount > 0) {
                SummaryRow("Need review", "${summary.unresolvedCount}")
            }
            summary.channelBalanceUpdatedTo?.let { SummaryRow("Balance set to", it.asUgx()) }
            summary.balanceUpdateSkipped?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.Space2.dp))
                Text(
                    text = "Balance left unchanged: $it",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (summary.categoryBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.Space3.dp))
                Text(
                    text = "What was in it",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.Space1.dp))
                summary.categoryBreakdown.entries
                    .sortedByDescending { it.value }
                    .forEach { (category, count) ->
                        SummaryRow(category.replace('_', ' ').replaceFirstChar { it.uppercase() }, "$count")
                    }
            }

            if (summary.reconciliationWarnings.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.Space3.dp))
                WarningsSection(summary)
            }
        }
    }
}

@Composable
private fun WarningsSection(summary: StatementImportSummary) {
    var expanded by remember { mutableStateOf(false) }
    val count = summary.reconciliationWarnings.size

    Surface(
        shape = RoundedCornerShape(Radius.Medium.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(Spacing.Space3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = getErrorColor(),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.Space2.dp))
                Text(
                    text = if (count == 1) "1 balance check didn't line up" else "$count balance checks didn't line up",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = Spacing.Space2.dp)) {
                    summary.reconciliationWarnings.forEach { warning ->
                        Text(
                            text = "Row ${warning.rowIndex}: ${warning.message}",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.Space1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OutlinedCardShell(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
