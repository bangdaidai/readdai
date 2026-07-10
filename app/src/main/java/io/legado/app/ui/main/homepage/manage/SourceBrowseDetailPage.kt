package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
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
    onAddRankingFromKinds: (String, String?, String, List<String>) -> Unit = { _, _, _, _ -> },
    // 多选状态回调
    onMultiSelectChanged: (Boolean, List<ExploreKind>) -> Unit = { _, _ -> },
    onSelectedKindsChanged: (Set<String>) -> Unit = {},
) {
    var browseTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                    val standardSectionStart = 0
                    val standardSectionEnd = standardModules.size
                    val infiniteSectionStart = if (standardModules.isEmpty()) 1 else standardModules.size + 2

                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        val fromModuleIndex = when {
                            standardModules.isEmpty() -> {
                                if (from.index >= 1 && from.index < infiniteSectionStart) from.index - 1
                                else return@rememberReorderableLazyListState
                            }
                            from.index < standardSectionEnd -> from.index
                            from.index >= infiniteSectionStart -> from.index - infiniteSectionStart + infiniteModules.size
                            else -> return@rememberReorderableLazyListState
                        }
                        val toModuleIndex = when {
                            standardModules.isEmpty() -> {
                                if (to.index >= 1 && to.index < infiniteSectionStart) to.index - 1
                                else return@rememberReorderableLazyListState
                            }
                            to.index < standardSectionEnd -> to.index
                            to.index >= infiniteSectionStart -> to.index - infiniteSectionStart + infiniteModules.size
                            else -> return@rememberReorderableLazyListState
                        }
                        if (fromModuleIndex < 0 || fromModuleIndex >= allModuleIds.size ||
                            toModuleIndex < 0 || toModuleIndex >= allModuleIds.size) {
                            return@rememberReorderableLazyListState
                        }
                        val mutableList = allModuleIds.toMutableList()
                        val item = mutableList.removeAt(fromModuleIndex)
                        mutableList.add(toModuleIndex, item)
                        onReorderModules(mutableList)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (standardModules.isNotEmpty()) {
                            item(key = "header_standard") {
                                Text(
                                    stringResource(R.string.hp_standard_modules),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp).padding(top = 12.dp)
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
                                Text(stringResource(R.string.hp_infinite_modules), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
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
                val exploreKinds = onGetExploreKinds(browseUrl)
                var query by remember { mutableStateOf("") }
                var selectedKinds by remember { mutableStateOf<Set<String>>(emptySet()) }
                var showCreateModuleDialog by remember { mutableStateOf(false) }

                val filteredKinds = remember(query, exploreKinds) {
                    if (query.isBlank()) exploreKinds
                    else exploreKinds.filter { kind ->
                        kind.title.contains(query, ignoreCase = true) ||
                                (kind.url?.contains(query, ignoreCase = true) == true)
                    }
                }

                if (exploreKinds.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.hp_no_explore_kinds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CompactSearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = stringResource(R.string.hp_search_category),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                )
                            }

                            val kindRows = remember(filteredKinds) {
                                calculateExploreKindRows(filteredKinds, 12)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                kindRows.forEach { rowItems ->
                                    val totalSpan = rowItems.sumOf { it.second }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        rowItems.forEach { (kind, span) ->
                                            val isSelected = kind.title in selectedKinds
                                            val style = kind.style()
                                            val hasUrl = !kind.url.isNullOrBlank()
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .weight(span.toFloat())
                                                    .then(if (hasUrl) Modifier.clickable {
                                                        selectedKinds = if (isSelected) {
                                                            selectedKinds - kind.title
                                                        } else {
                                                            selectedKinds + kind.title
                                                        }
                                                        onSelectedKindsChanged(selectedKinds)
                                                    } else Modifier),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp,
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = when (style.layout_justifySelf) {
                                                        "flex_start" -> Arrangement.Start
                                                        "flex_end" -> Arrangement.End
                                                        else -> Arrangement.Center
                                                    },
                                                ) {
                                                    Text(
                                                        kind.title,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 13.sp,
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp).padding(start = 2.dp),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
            }
        }
                                            }
                                        }
                                        if (totalSpan < 12) {
                                            Spacer(modifier = Modifier.weight((12 - totalSpan).toFloat()))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.hp_manual_add))
                            }
                        }

                        // 选中分类后显示悬浮确认按钮
                        if (selectedKinds.isNotEmpty()) {
                            androidx.compose.material3.FloatingActionButton(
                                onClick = { showCreateModuleDialog = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp)
                                    .size(40.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                if (showCreateModuleDialog) {
                    val selectedKindObjects = exploreKinds.filter { it.title in selectedKinds }
                    val prefillUrl = selectedKindObjects.firstOrNull()?.url ?: ""
                    AddCustomModuleDialog(
                        sourceUrl = browseUrl,
                        targetSetId = currentSetId,
                        prefillType = "ranking",
                        prefillUrl = prefillUrl,
                        canSelectInfinite = canSelectInfiniteGlobal,
                        allKinds = exploreKinds,
                        prefillKindTitles = selectedKinds.toList(),
                        onDismissRequest = { showCreateModuleDialog = false },
                        onConfirm = { def ->
                            val kindTitles = selectedKinds.toList()
                            val resolvedUrl = def.url.takeIf { it.isNotBlank() }
                                ?: selectedKindObjects.firstOrNull()?.url ?: ""
                            val resolvedDef = if (resolvedUrl != def.url) def.copy(url = resolvedUrl) else def
                            if (resolvedDef.type == HomepageModuleType.Ranking.key && kindTitles.size >= 2) {
                                val title = resolvedDef.title.ifBlank { kindTitles.joinToString("·") }
                                onAddRankingFromKinds(browseUrl, currentSetId, title, kindTitles)
                            } else if (resolvedDef.type == HomepageModuleType.ButtonGroup.key) {
                                val title = resolvedDef.title.ifBlank { kindTitles.joinToString("·") }
                                onAddButtonGroupFromKinds(browseUrl, currentSetId, title, kindTitles)
                            } else {
                                onAddCustomModule(browseUrl, currentSetId, resolvedDef)
                            }
                            showCreateModuleDialog = false
                            selectedKinds = emptySet()
                        },
                    )
                }

            }
        }
    }

    if (showAddDialog) {
        AddCustomModuleDialog(
            sourceUrl = browseUrl,
            targetSetId = currentSetId,
            prefillType = "card",
            canSelectInfinite = canSelectInfiniteGlobal,
            allKinds = emptyList(),
            onDismissRequest = { showAddDialog = false },
            onConfirm = { def -> onAddCustomModule(browseUrl, currentSetId, def); showAddDialog = false },
        )
    }
}

fun calculateExploreKindRows(
    kinds: List<ExploreKind>,
    maxSpan: Int = 12,
): List<List<Pair<ExploreKind, Int>>> {
    val rows = mutableListOf<List<Pair<ExploreKind, Int>>>()
    var currentRow = mutableListOf<Pair<ExploreKind, Int>>()
    var currentSpan = 0

    kinds.forEach { kind ->
        val style = kind.style()
        val span = when {
            style.layout_wrapBefore || style.layout_flexBasisPercent >= 1.0f -> maxSpan
            style.layout_flexBasisPercent > 0 -> (maxSpan * style.layout_flexBasisPercent).roundToInt()
                .coerceIn(3, maxSpan)
            else -> 3
        }
        if ((style.layout_wrapBefore && currentRow.isNotEmpty()) || (currentSpan + span > maxSpan)) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
        currentRow.add(kind to span)
        currentSpan += span
        if (currentSpan >= maxSpan) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
    }
    if (currentRow.isNotEmpty()) {
        val minSpan = currentRow.minOf { it.second }
        rows.add(currentRow.map { it.first to minSpan })
    }
    return rows
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
            Switch(
                modifier = Modifier.scale(0.8f),
                checked = module.isVisible,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}
