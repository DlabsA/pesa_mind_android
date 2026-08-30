package cc.dlabs.pesamind.features.lentborrowed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.data.DebtCreditRepository
import cc.dlabs.pesamind.core.network.models.DebtCreditResponse
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.PesaMindStrings
import kotlinx.coroutines.launch

/**
 * Picker sheet for the "choose a purpose" flow's Lent/Borrowed step — lists active debts for
 * [direction], with an always-present "+ New" row (not just an empty-state fallback) so a new
 * debt can be created inline without leaving [cc.dlabs.pesamind.features.home.AddTransactionScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtCreditPicker(
    direction: String,
    debts: List<DebtCreditResponse>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateSheet by remember { mutableStateOf(false) }
    val directionLabel = PesaMindStrings.DebtCredit.directionLabel(direction)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Choose who this is with",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (debts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    EmptyState(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = "No debts yet",
                        subtitle = "Add someone you've $directionLabel money with.",
                    )
                }
            } else {
                LazyColumn {
                    items(debts, key = { it.id }) { debt ->
                        ListItem(
                            headlineContent = { Text(debt.counterpartyName) },
                            supportingContent = { Text("Outstanding: ${debt.outstanding}") },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(debt.id) },
                        )
                    }
                }
                HorizontalDivider()
            }

            Button(
                onClick = { showCreateSheet = true },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            ) {
                Text("+ New")
            }
        }
    }

    if (showCreateSheet) {
        CreateDebtCreditSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { newDirection, name, phone, amount, dueAt, note, offsets ->
                showCreateSheet = false
                // Called directly against the repository (not the ViewModel's fire-and-forget
                // wrapper) so the newly created debt's id is available synchronously to
                // pre-select it and close this picker — "+ New" must return to the caller with
                // the new debt already chosen, not just leave it sitting in the observed list.
                scope.launch {
                    val created = DebtCreditRepository.createDebtCredit(newDirection, name, phone, amount, dueAt, note, offsets)
                    if (created != null) onCreated(created.id)
                }
            },
        )
    }
}
