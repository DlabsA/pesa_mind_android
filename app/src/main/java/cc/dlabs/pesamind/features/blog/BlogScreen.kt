package cc.dlabs.pesamind.features.blog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import cc.dlabs.pesamind.core.database.entity.BlogPostEntity
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.DetailScreenTopBar
import cc.dlabs.pesamind.core.ui.EmptyState
import cc.dlabs.pesamind.core.ui.SkeletonColumn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val postDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogScreen(
    navController: NavController,
    viewModel: BlogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DetailScreenTopBar(
            title = "Finance Blog",
            subtitle = "Short write-ups on managing your money",
            badge = "${state.posts.size}",
            onBack = { navController.popBackStack() },
        )

        when {
            state.isLoading && state.posts.isEmpty() ->
                SkeletonColumn(
                    blockHeights = listOf(90.dp, 90.dp, 90.dp, 90.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Space4.dp),
                )

            state.posts.isEmpty() ->
                EmptyState(
                    icon = Icons.Default.Article,
                    title = "No posts yet",
                    subtitle = "New finance write-ups will show up here as soon as they're published.",
                    modifier = Modifier.fillMaxSize().padding(Spacing.Space6.dp),
                )

            else ->
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = Spacing.Space4.dp, vertical = Spacing.Space3.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.posts, key = { it.id }) { post ->
                            BlogPostCard(post = post, onOpen = { viewModel.markRead(post.id) })
                        }
                    }
                }
        }
    }
}

@Composable
private fun BlogPostCard(
    post: BlogPostEntity,
    onOpen: () -> Unit,
) {
    var expanded by remember(post.id) { mutableStateOf(false) }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (!expanded) onOpen()
                    expanded = !expanded
                },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.Space4.dp)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = postDateFormat.format(Date(post.publishedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.padding(top = Spacing.Space2.dp)) {
                Text(
                    text = post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
