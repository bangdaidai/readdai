package io.legado.app.ui.main.homepage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.main.homepage.manage.AddCustomModuleDialog
import io.legado.app.ui.main.homepage.manage.BrowseSourcesPage
import io.legado.app.ui.main.homepage.manage.CustomSetAddModulesPage
import io.legado.app.ui.main.homepage.manage.SetDetailPage
import io.legado.app.ui.main.homepage.manage.SetListPage
import io.legado.app.ui.main.homepage.manage.SourceBrowseDetailPage

private sealed interface ManagePage {
    data object SetList : ManagePage
    data class SetDetail(val setUrl: String) : ManagePage
    data object BrowseSources : ManagePage
    data class SourceDetail(val sourceUrl: String) : ManagePage
    data class AddModulesToSet(val setUrl: String) : ManagePage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageModuleManageSheet(
    state: HomepageManageUiState,
    viewModel: HomepageViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember { mutableStateOf<ManagePage>(ManagePage.SetList) }
    var showCreateSetDialog by remember { mutableStateOf(false) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var deleteModuleConfirmId by remember { mutableStateOf<String?>(null) }
    var renameSetId by remember { mutableStateOf<String?>(null) }
    var editingModule by remember { mutableStateOf<HomepageModuleManageUi?>(null) }

    val effectiveTargetSetId = remember(currentPage, state.allJoinedModules) {
        when (val page = currentPage) {
            is ManagePage.SetDetail -> HomepageViewModel.customSetIdFromUrl(page.setUrl)
            is ManagePage.SourceDetail -> {
                val setUrl = HomepageViewModel.customSetUrl("src_${page.sourceUrl}")
                HomepageViewModel.customSetIdFromUrl(setUrl)
            }
            else -> null
        }
    }

    val canSelectInfiniteGlobal = remember(state.allJoinedModules, effectiveTargetSetId) {
        effectiveTargetSetId == null || !state.allJoinedModules.any {
            it.customSetId == effectiveTargetSetId && HomepageViewModel.isInfinite(it.type, it.layoutConfig)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                if (currentPage !is ManagePage.SetList) {
                    IconButton(onClick = { currentPage = ManagePage.SetList }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Text(
                    when (currentPage) {
                        is ManagePage.SetList -> "模块管理"
                        is ManagePage.SetDetail -> "集合详情"
                        is ManagePage.BrowseSources -> "浏览书源"
                        is ManagePage.SourceDetail -> "书源详情"
                        is ManagePage.AddModulesToSet -> "添加模块"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = if (currentPage is ManagePage.SetList) 0.dp else 0.dp, top = 12.dp, bottom = 8.dp),
                )
            }

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState is ManagePage.SetList) {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    } else {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    }
                },
                label = "manage_page",
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(450.dp)
                ) {
                    when (page) {
                        is ManagePage.SetList -> SetListPage(
                            sets = state.sets,
                            onToggleSet = { url, enabled -> viewModel.toggleSourceFilter(url, enabled) },
                            onReorderSets = { urls -> viewModel.reorderCustomSets(urls) },
                            onSelectSet = { setUrl -> currentPage = ManagePage.SetDetail(setUrl) },
                            onRenameSet = { setUrl -> renameSetId = setUrl },
                            onDeleteSet = { setUrl -> deleteConfirmId = setUrl },
                            onCreateSet = { showCreateSetDialog = true },
                            onBrowseSources = { currentPage = ManagePage.BrowseSources },
                        )

                        is ManagePage.SetDetail -> SetDetailPage(
                            setUrl = page.setUrl,
                            allJoinedModules = state.allJoinedModules,
                            onToggleModule = { id, visible -> viewModel.setModuleVisible(id, visible) },
                            onReorderModules = { ids -> viewModel.reorderJoinedModules(ids) },
                            onEditModule = { module -> editingModule = module },
                            onRequestDeleteModule = { id -> deleteModuleConfirmId = id },
                            onBrowseSourceModules = {
                                val setId = HomepageViewModel.customSetIdFromUrl(page.setUrl)
                                if (setId.startsWith("src_")) {
                                    val sourceUrl = setId.removePrefix("src_")
                                    currentPage = ManagePage.SourceDetail(sourceUrl)
                                }
                            },
                            onAddModules = { currentPage = ManagePage.AddModulesToSet(page.setUrl) },
                        )

                        is ManagePage.BrowseSources -> BrowseSourcesPage(
                            browseSources = state.browseSources,
                            getSourceModules = { url, setId -> viewModel.getSourceModules(url, setId) },
                            onSelectSource = { sourceUrl -> currentPage = ManagePage.SourceDetail(sourceUrl) },
                        )

                        is ManagePage.SourceDetail -> SourceBrowseDetailPage(
                            browseUrl = page.sourceUrl,
                            selectingSetUrl = null,
                            allJoinedModules = state.allJoinedModules,
                            canSelectInfiniteGlobal = canSelectInfiniteGlobal,
                            onGetSourceModules = { url, setId -> viewModel.getSourceModules(url, setId) },
                            onGetExploreKinds = { url -> viewModel.getSourceExploreKinds(url) },
                            onLoadExploreKinds = { url -> viewModel.loadExploreKinds(url) },
                            onToggleModule = { id, visible -> viewModel.setModuleVisible(id, visible) },
                            onJoinModule = { sourceUrl, setId, def -> viewModel.joinModule(sourceUrl, setId, def) },
                            onRequestDeleteModule = { id -> deleteModuleConfirmId = id },
                            onReorderModules = { ids -> viewModel.reorderJoinedModules(ids) },
                            onEditModule = { module -> editingModule = module },
                            onAddCustomModule = { sourceUrl, setId, def -> viewModel.addCustomModule(sourceUrl, setId, def) },
                            onAddButtonGroupFromKinds = { sourceUrl, setId, title, kinds ->
                                viewModel.addButtonGroupFromKinds(sourceUrl, setId, title, kinds)
                            },
                        )

                        is ManagePage.AddModulesToSet -> CustomSetAddModulesPage(
                            setUrl = page.setUrl,
                            allJoinedModules = state.allJoinedModules,
                            sourceNames = state.sourceNames,
                            onToggleModuleToSet = { module, inCurrentSet, isBlocked ->
                                if (inCurrentSet) {
                                    viewModel.deleteModule(module.id)
                                } else if (!isBlocked) {
                                    viewModel.assignModuleToCustomSet(module.id, HomepageViewModel.customSetIdFromUrl(page.setUrl))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreateSetDialog) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateSetDialog = false },
            title = { Text("创建新集合") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) viewModel.createCustomSet(name); showCreateSetDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showCreateSetDialog = false }) { Text("取消") } },
        )
    }

    if (deleteConfirmId != null) {
        val id = deleteConfirmId!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除集合") },
            text = { Text("确定要删除此集合及其所有模块吗？") },
            confirmButton = { TextButton(onClick = { viewModel.deleteCustomSet(HomepageViewModel.customSetIdFromUrl(id)); deleteConfirmId = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("取消") } },
        )
    }

    if (deleteModuleConfirmId != null) {
        val id = deleteModuleConfirmId!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteModuleConfirmId = null },
            title = { Text("删除模块") },
            text = { Text("确定要删除此模块吗？") },
            confirmButton = { TextButton(onClick = { viewModel.deleteModule(id); deleteModuleConfirmId = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteModuleConfirmId = null }) { Text("取消") } },
        )
    }

    if (renameSetId != null) {
        val id = renameSetId!!
        val setId = HomepageViewModel.customSetIdFromUrl(id)
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameSetId = null },
            title = { Text("重命名集合") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("新名称") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) viewModel.renameCustomSet(setId, name); renameSetId = null }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { renameSetId = null }) { Text("取消") } },
        )
    }

    if (editingModule != null) {
        val module = editingModule!!
        AddCustomModuleDialog(
            sourceUrl = module.sourceUrl,
            targetSetId = module.customSetId ?: "",
            prefillTitle = module.title,
            prefillUrl = module.url ?: "",
            prefillType = module.type,
            prefillArgs = module.args ?: "",
            prefillLayoutConfig = module.layoutConfig ?: "",
            canSelectInfinite = canSelectInfiniteGlobal,
            onDismissRequest = { editingModule = null },
            onConfirm = { def -> viewModel.updateModule(module.id, def); editingModule = null },
        )
    }
}
