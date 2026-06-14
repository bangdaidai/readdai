package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.HomepageSourceManageUi
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.draggableHandle
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetListPage(
    sets: List<HomepageSourceManageUi>,
    onToggleSet: (String, Boolean) -> Unit,
    onReorderSets: (List<String>) -> Unit,
    onSelectSet: (String) -> Unit,
    onRenameSet: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onCreateSet: () -> Unit,
    onBrowseSources: () -> Unit,
) {
    val distinctSets = remember(sets) { sets.distinctBy { it.sourceUrl } }

    val lazyListState = remember {
        androidx.compose.foundation.lazy.LazyListState(0)
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val mutableList = distinctSets.map { it.sourceUrl }.toMutableList()
        if (from.index < mutableList.size && to.index < mutableList.size) {
            val item = mutableList.removeAt(from.index)
            mutableList.add(to.index, item)
            onReorderSets(mutableList)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(distinctSets.size, key = { index -> distinctSets[index].sourceUrl }) { index ->
            val set = distinctSets[index]
            ReorderableItem(reorderableState, key = set.sourceUrl) { isDragging ->
                val dragModifier = this.draggableHandle()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = dragModifier,
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.drag_handle),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).clickable { onSelectSet(set.sourceUrl) },
                        ) {
                            Text(
                                set.sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${set.moduleCount} ${stringResource(R.string.hp_modules_count)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRenameSet(set.sourceUrl) }) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.rename), modifier = Modifier.height(20.dp))
                        }
                        IconButton(onClick = { onDeleteSet(set.sourceUrl) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.hp_delete), modifier = Modifier.height(20.dp))
                        }
                        Switch(
                            checked = set.isSelected,
                            onCheckedChange = { enabled -> onToggleSet(set.sourceUrl, enabled) },
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item(key = "create_set") {
            TextButton(onClick = onCreateSet, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hp_create_set))
            }
        }

        item(key = "browse_sources") {
            TextButton(onClick = onBrowseSources, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hp_browse_source_modules))
            }
        }
    }
}
