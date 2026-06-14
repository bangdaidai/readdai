package io.legado.app.ui.main.homepage.manage

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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SetDetailPage(
    setUrl: String,
    allJoinedModules: List<HomepageModuleManageUi>,
    onToggleModule: (String, Boolean) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onEditModule: (HomepageModuleManageUi) -> Unit,
    onRequestDeleteModule: (String) -> Unit,
    onBrowseSourceModules: () -> Unit,
    onAddModules: () -> Unit,
) {
    val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
    val modules = remember(setId, allJoinedModules) {
        allJoinedModules.filter { it.customSetId == setId }.distinctBy { it.id }
    }

    val standardModules = remember(modules) {
        modules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val infiniteModules = remember(modules) {
        modules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }

    if (modules.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.hp_no_modules), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = {
                if (setId.startsWith("src_")) onBrowseSourceModules()
                else onAddModules()
            }) {
                Text(stringResource(R.string.hp_browse_add))
            }
        }
    } else {
        val lazyListState = remember {
            androidx.compose.foundation.lazy.LazyListState(0)
        }

        val allModuleIds = remember(standardModules, infiniteModules) {
            standardModules.map { it.id } + infiniteModules.map { it.id }
        }

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val mutableList = allModuleIds.toMutableList()
            if (from.index < mutableList.size && to.index < mutableList.size) {
                val item = mutableList.removeAt(from.index)
                mutableList.add(to.index, item)
                onReorderModules(mutableList)
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (standardModules.isNotEmpty()) {
                item(key = "header_std_detail") {
                    Text(
                        stringResource(R.string.hp_standard_modules),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(standardModules, key = { it.id }) { module ->
                    ReorderableItem(reorderableState, key = module.id) { isDragging ->
                        ModuleCard(
                            module = module,
                            isDragging = isDragging,
                            onToggle = { onToggleModule(module.id, it) },
                            onEdit = { onEditModule(module) },
                            onDelete = { onRequestDeleteModule(module.id) },
                        )
                    }
                }
            }

            if (infiniteModules.isNotEmpty()) {
                item(key = "header_inf_detail") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.hp_infinite_modules),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(infiniteModules, key = { it.id }) { module ->
                    ReorderableItem(reorderableState, key = module.id) { isDragging ->
                        ModuleCard(
                            module = module,
                            isDragging = isDragging,
                            onToggle = { onToggleModule(module.id, it) },
                            onEdit = { onEditModule(module) },
                            onDelete = { onRequestDeleteModule(module.id) },
                            containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                }
            }

            item(key = "browse_from_set") {
                TextButton(
                    onClick = {
                        if (setId.startsWith("src_")) onBrowseSourceModules()
                        else onAddModules()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hp_source_modules))
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: HomepageModuleManageUi,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = if (isDragging) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier.draggableHandle(),
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    module.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    HomepageModuleType.fromKey(module.type).title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.hp_edit_module), modifier = Modifier.height(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.hp_delete), modifier = Modifier.height(20.dp))
            }
            Switch(
                checked = module.isVisible,
                onCheckedChange = onToggle,
            )
        }
    }
}
