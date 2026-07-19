package io.legado.app.ui.book.toc.rule.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.main.homepage.ReaddaiTheme
import io.legado.app.utils.toastOnUi

@Composable
fun TocRulePreviewRouteScreen(
    bookUrl: String,
    viewModel: TocRulePreviewViewModel,
    onBack: () -> Unit,
    onApplyRule: (String) -> Unit,
    onOpenManagePage: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 编辑替换规则（网络书籍目录规则预览用），编辑返回后重新统计命中数
    val editReplaceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onIntent(TocRulePreviewIntent.Refresh)
        }
    }

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TocRulePreviewEffect.ShowToast -> context.toastOnUi(effect.message)
                is TocRulePreviewEffect.ApplyRule -> onApplyRule(effect.tocRegex)
                is TocRulePreviewEffect.OpenManagePage -> onOpenManagePage()
                is TocRulePreviewEffect.OpenReplaceRuleEditor -> {
                    editReplaceLauncher.launch(
                        io.legado.app.ui.replace.edit.ReplaceEditActivity.startIntent(context, effect.ruleId)
                    )
                }
            }
        }
    }

    ReaddaiTheme {
        TocRulePreviewScreen(
            state = uiState,
            onIntent = viewModel::onIntent,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocRulePreviewScreen(
    state: TocRulePreviewUiState,
    onIntent: (TocRulePreviewIntent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isTxt) stringResource(R.string.select_toc_rule)
                        else stringResource(R.string.toc_rule_preview)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(TocRulePreviewIntent.ToggleSearch) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    if (state.isTxt) {
                        IconButton(onClick = { onIntent(TocRulePreviewIntent.OpenManagePage) }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.manage))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isTxt && state.hasSelection) {
                FloatingActionButton(
                    onClick = { onIntent(TocRulePreviewIntent.ApplyRule) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.ok))
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.emptyHint.isNotEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.emptyHint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.isTxt -> TxtRulePreviewList(state, onIntent)
                else -> NetworkRulePreviewList(state, onIntent)
            }
        }

        when (val sheet = state.activeSheet) {
            is TocRulePreviewSheet.ChapterList -> {
                TxtRuleChapterSheet(
                    item = sheet.item,
                    onDismiss = { onIntent(TocRulePreviewIntent.DismissSheet) },
                    onEditRule = { onIntent(TocRulePreviewIntent.EditRule(it)) },
                )
            }
            is TocRulePreviewSheet.NetworkRuleChapters -> {
                NetworkRuleChapterSheet(
                    item = sheet.item,
                    onDismiss = { onIntent(TocRulePreviewIntent.DismissSheet) },
                    onEditRule = { onIntent(TocRulePreviewIntent.EditNetworkRule(sheet.item.rule.id)) },
                )
            }
            null -> { /* no sheet */ }
        }

        state.editingRule?.let { rule ->
            RuleEditSheet(
                rule = rule,
                onDismissRequest = { onIntent(TocRulePreviewIntent.DismissEditDialog) },
                onSave = { updated -> onIntent(TocRulePreviewIntent.SaveRule(updated)) },
            )
        }
    }
}

// ===================== 网络书籍目录规则预览 =====================

@Composable
private fun NetworkRulePreviewList(
    state: TocRulePreviewUiState,
    onIntent: (TocRulePreviewIntent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.preview_chapter_count, state.chapterTotal),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.useReplace) {
                        stringResource(R.string.use_replace_purify_format, state.titleReplaceRules.size)
                    } else {
                        stringResource(R.string.replace_rule_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.network_rule_preview_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = state.showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onIntent(TocRulePreviewIntent.UpdateSearchQuery(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
            )
        }

        val rules = state.filteredNetworkRules
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (state.titleReplaceRules.isEmpty())
                        stringResource(R.string.no_title_replace_rule)
                    else
                        stringResource(R.string.no_match),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(rules) { _, item ->
                    NetworkRuleCard(
                        item = item,
                        onClick = { onIntent(TocRulePreviewIntent.ShowNetworkRuleChapters(item)) },
                        onLongClick = { onIntent(TocRulePreviewIntent.ShowNetworkRuleChapters(item)) },
                        onEdit = { onIntent(TocRulePreviewIntent.EditNetworkRule(item.rule.id)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkRuleCard(
    item: NetworkRulePreviewItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.example?.let { example ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!item.computed) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.matchCount > 0)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (item.matchCount > 0)
                            stringResource(R.string.rule_matched_count, item.matchCount)
                        else
                            stringResource(R.string.rule_no_match),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.matchCount > 0)
                            Color.White
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkRuleChapterSheet(
    item: NetworkRulePreviewItem,
    onDismiss: () -> Unit,
    onEditRule: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEditRule) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.rule_matched_count, item.matchCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (item.chapters.isEmpty()) {
                Text(
                    stringResource(R.string.toc_preview_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    itemsIndexed(item.chapters.toList()) { _, (origin, display) ->
                        NetworkChapterRow(origin = origin, display = display, changed = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkChapterRow(origin: String, display: String, changed: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                display,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (changed) {
                Spacer(Modifier.height(2.dp))
                Text(
                    origin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ===================== TXT 目录规则预览 =====================

@Composable
private fun TxtRulePreviewList(
    state: TocRulePreviewUiState,
    onIntent: (TocRulePreviewIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.filteredTxtRules, key = { _, item -> item.rule.id }) { _, item ->
            TxtRuleCard(
                item = item,
                isSelected = item.rule.rule == state.selectedRule,
                onClick = {
                    onIntent(TocRulePreviewIntent.SelectRule(item.rule.rule))
                    if (item.computed && item.matchCountResolved > 0) {
                        onIntent(TocRulePreviewIntent.ShowChapterList(item))
                    }
                },
            )
        }
    }
}

@Composable
private fun TxtRuleCard(item: TxtRulePreviewItem, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.rule.example?.let { example ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            ) {
                if (!item.computed) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(R.string.preview_chapter_count, item.matchCountResolved),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxtRuleChapterSheet(
    item: TxtRulePreviewItem,
    onDismiss: () -> Unit,
    onEditRule: (TxtTocRule) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onEditRule(item.rule) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.preview_chapter_count, item.matchCountResolved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                itemsIndexed(item.chapters.toList()) { _, chapter ->
                    Text(
                        chapter,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.chapters.size >= 200) {
                    item {
                        Text(
                            stringResource(R.string.chapter_list_preview_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ===================== TXT 规则编辑抽屉 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditSheet(
    rule: TxtTocRule,
    onDismissRequest: () -> Unit,
    onSave: (TxtTocRule) -> Unit,
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var regex by remember(rule.id) { mutableStateOf(rule.rule) }
    var example by remember(rule.id) { mutableStateOf(rule.example ?: "") }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.txt_toc_rule),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.txt_toc_rule)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = regex,
                onValueChange = { regex = it },
                label = { Text(stringResource(R.string.regex)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = example,
                onValueChange = { example = it },
                label = { Text(stringResource(R.string.example)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            rule.copy(
                                name = name,
                                rule = regex,
                                example = example.ifBlank { null },
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
