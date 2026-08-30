package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
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
import cc.dlabs.pesamind.core.data.SavingGoalRepository
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.ui.EmptyState
import kotlinx.coroutines.launch

/** Picker sheet for the "choose a purpose" flow's Saving Goal step — mirrors
 * [cc.dlabs.pesamind.features.lentborrowed.DebtCreditPicker] exactly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalPicker(
    goals: List<SavingGoalResponse>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Choose a goal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (goals.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    EmptyState(
                        icon = Icons.Filled.Savings,
                        title = "No saving goals yet",
                        subtitle = "Set a target to start tracking progress.",
                    )
                }
            } else {
                LazyColumn {
                    items(goals, key = { it.id }) { goal ->
                        ListItem(
                            headlineContent = { Text(goal.name) },
                            supportingContent = { Text("${goal.progress} of ${goal.targetAmount}") },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(goal.id) },
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
        CreateSavingGoalSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, targetAmount, targetAt, note, offsets ->
                showCreateSheet = false
                scope.launch {
                    val created = SavingGoalRepository.createSavingGoal(name, targetAmount, targetAt, note, offsets)
                    if (created != null) onCreated(created.id)
                }
            },
        )
    }
}
