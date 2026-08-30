package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.ui.EmptyState
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingGoalDetailScreen(
    navController: NavHostController,
    savingGoalId: String,
    viewModel: SavingGoalViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val goal = state.goals.find { it.id == savingGoalId }

    var transactions by remember { mutableStateOf<List<TransactionDetails>>(emptyList()) }
    LaunchedEffect(savingGoalId) {
        TransactionRepository.observeBySavingGoal(savingGoalId).collect { transactions = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goal?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (goal == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fmt = NumberFormat.getNumberInstance(Locale.getDefault())
                    val fraction = if (goal.targetAmount > 0) (goal.progress / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${fmt.format(goal.progress)} / ${fmt.format(goal.targetAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (goal.status == "achieved") {
                        Text("Achieved!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Button(
                onClick = {
                    navController.navigate(Routes.AddTransaction.createRoute(savingGoalId = savingGoalId))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text("Add contribution")
            }

            if (transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "No contributions yet",
                        subtitle = "Contributions linked to this goal will show up here.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(transactions, key = { it.id }) { tx ->
                        ListItem(
                            headlineContent = { Text(tx.type.replaceFirstChar { it.uppercase() }) },
                            supportingContent = { Text(tx.note.ifBlank { tx.channelDetailsName }) },
                            trailingContent = { Text(NumberFormat.getNumberInstance(Locale.getDefault()).format(tx.amount)) },
                        )
                    }
                }
            }
        }
    }
}
