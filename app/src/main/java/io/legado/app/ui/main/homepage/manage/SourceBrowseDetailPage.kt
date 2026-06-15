package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SourceBrowseDetailPage(
    browseUrl: String,
    selectingSetUrl: String?,
    allJoinedModules: List<HomepageModuleManageUi>,
    canSelectInfiniteGlobal: Boolean,
    onGetSourceModules: (String, String?) -> List<HomepageModuleManageUi>,
    onGetExploreKinds: (String) -> List<ExploreKind>,
    onLoadExploreKinds: (String) -> Unit,
    onToggleModule: (String, Boolean) -> Unit,
    onJoinModule: (String, String?, ModuleDef) -> Unit,
    onRequestDeleteModule: (String) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onEditModule: (HomepageModuleManageUi) -> Unit,
    onAddCustomModule: (String, String?, ModuleDef) -> Unit,
    onAddButtonGroupFromKinds: (String, String?, String, List<String>) -> Unit,
) {
    var browseTab by remember { mutableIntStateOf(0) }
    var browseModuleType by remember { mutableStateOf("card") }
    val isButtonGroup = browseModuleType == "buttonGroup"
    var selectedKindTitles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showKindSelect by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddButtonGroupDialog by remember { mutableStateOf(false) }
    val defaultButtonGroupTitle = stringResource(R.string.hp_create_button_group_title)
    var tempButtonGroupTitle by remember { mutableStateOf(defaultButtonGroupTitle) }

    val displaySetUrl = selectingSetUrl ?: HomepageViewModel.customSetUrl("src_$browseUrl")
    val currentSetId = HomepageViewModel.customSetIdFromUrl(displaySetUrl)

    val joinedModules = remember(displaySetUrl, allJoinedModules) {
        allJoinedModules.filter { it.customSetId == currentSetId }
    }

    val standardModules = remember(joinedModules) {
        joinedModules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val infiniteModules = remember(joinedModules) {
        joinedModules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
    }
    val hasInfiniteInSet = infiniteModules.isNotEmpty()

    val joinedKeys = joinedModules.map { it.moduleKey }.toSet()
    val sourceModules = onGetSourceModules(browseUrl, currentSetId)

    val tabTitles = listOf(stringResource(R.string.hp_joined), stringResource(R.string.hp_source_modules), stringResource(R.string.hp_explore))

    Column {
        TabRow(selectedTabIndex = browseTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = browseTab == index,
                    onClick = {
                        browseTab = index
                        if (index == 2) onLoadExploreKinds(browseUrl)
                    },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        when (browseTab) {
            0 -> {
                if (joinedModules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.hp_no_joined_modules), style = MaterialTheme.typography.bodyMedium)
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (standardModules.isNotEmpty()) {
                            item(key = "header_standard") {
                                Text(
                                    stringResource(R.string.hp_standard_modules),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(standardModules, key = { it.id }) { module ->
                                ReorderableItem(reorderableState, key = module.id) { isDragging ->
                                    val dragModifier = Modifier.draggableHandle()
                                    SourceModuleCard(
                                        dragModifier = dragModifier,
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
                            item(key = "header_infinite") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.hp_infinite_modules), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                            items(infiniteModules, key = { it.id }) { module ->
                                ReorderableItem(reorderableState, key = module.id) { isDragging ->
                                    val dragModifier = Modifier.draggableHandle()
                                    SourceModuleCard(
                                        dragModifier = dragModifier,
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
                    }
                }
            }

            1 -> {
                if (sourceModules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.hp_no_source_modules), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sourceModules.distinctBy { it.id }, key = { it.id }) { module ->
                            val isJoined = joinedKeys.contains(module.moduleKey)
                            val isInfinite = HomepageViewModel.isInfinite(module.type, module.layoutConfig)
                            val isBlocked = !isJoined && isInfinite && hasInfiniteInSet

                            Card(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (!isBlocked) Modifier.clickable {
                                        if (!isJoined) {
                                            onJoinModule(
                                                browseUrl, currentSetId, ModuleDef(
                                                    key = module.moduleKey,
                                                    type = module.type,
                                                    title = module.title,
                                                    sourceUrl = browseUrl,
                                                )
                                            )
                                        }
                                    } else Modifier
                                ),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(module.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            module.moduleKey + if (isJoined) " (${stringResource(R.string.hp_joined)})" else if (isBlocked) " (${stringResource(R.string.hp_infinite_conflict)})" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isJoined) {
                                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.hp_joined), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                val typeList = remember {
                    HomepageModuleType.entries.filter { it != HomepageModuleType.Unknown }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.hp_module_type), style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                typeList.forEach { moduleType ->
                                    TextButton(
                                        onClick = {
                                            browseModuleType = moduleType.key
                                            selectedKindTitles = emptySet()
                                        },
                                    ) {
                                        Text(
                                            moduleType.title,
                                            color = if (browseModuleType == moduleType.key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val exploreKinds = onGetExploreKinds(browseUrl)
                    if (exploreKinds.isNotEmpty()) {
                        TextButton(
                            onClick = { showKindSelect = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.hp_select_category))
                        }

                        if (selectedKindTitles.isNotEmpty()) {
                            Text(
                                stringResource(R.string.hp_selected_count, selectedKindTitles.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(stringResource(R.string.hp_no_explore_kinds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.hp_manual_add))
                    }
                }
            }
        }
    }

    if (showKindSelect) {
        ExploreKindSelectSheet(
            show = true,
            onDismissRequest = { showKindSelect = false },
            kinds = onGetExploreKinds(browseUrl),
            onSelected = { selectedKinds ->
                if (isButtonGroup) {
                    selectedKindTitles = selectedKinds.map { it.title }.toSet()
                    if (selectedKinds.size >= 2) {
                        showAddButtonGroupDialog = true
                    }
                } else {
                    selectedKinds.firstOrNull()?.let { kind ->
                        onAddCustomModule(
                            browseUrl, currentSetId, ModuleDef(
                                title = kind.title,
                                url = kind.url,
                                type = browseModuleType,
                                args = kind.title,
                                sourceUrl = browseUrl,
                            )
                        )
                    }
                }
            },
            multiple = isButtonGroup,
            initialSelectedTitles = selectedKindTitles,
        )
    }

    if (showAddDialog) {
        AddCustomModuleDialog(
            sourceUrl = browseUrl,
            targetSetId = currentSetId,
            prefillType = browseModuleType,
            canSelectInfinite = canSelectInfiniteGlobal,
            onDismissRequest = { showAddDialog = false },
            onConfirm = { def -> onAddCustomModule(browseUrl, currentSetId, def); showAddDialog = false },
        )
    }

    if (showAddButtonGroupDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddButtonGroupDialog = false },
            title = { Text(stringResource(R.string.hp_create_button_group_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.hp_create_button_group_desc, selectedKindTitles.size))
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = tempButtonGroupTitle,
                        onValueChange = { tempButtonGroupTitle = it },
                        label = { Text(stringResource(R.string.hp_title)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddButtonGroupFromKinds(browseUrl, currentSetId, tempButtonGroupTitle, selectedKindTitles.toList())
                    showAddButtonGroupDialog = false
                    selectedKindTitles = emptySet()
                }) { Text(stringResource(R.string.hp_determine)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddButtonGroupDialog = false }) { Text(stringResource(R.string.hp_cancel)) }
            },
        )
    }
}

@Composable
private fun SourceModuleCard(
    dragModifier: Modifier,
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
                modifier = dragModifier,
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(HomepageModuleType.fromKey(module.type).title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.hp_edit_module), modifier = Modifier.height(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.hp_delete), modifier = Modifier.height(20.dp))
            }
            Switch(checked = module.isVisible, onCheckedChange = onToggle)
        }
    }
}
