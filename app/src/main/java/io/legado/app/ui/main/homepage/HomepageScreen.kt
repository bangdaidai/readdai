package io.legado.app.ui.main.homepage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomepageScreen(
    viewModel: HomepageViewModel = viewModel(),
    onBookClick: (name: String?, author: String?, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onModuleHeaderClick: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewBook by remember { mutableStateOf<SearchBook?>(null) }
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }

    val layoutMode = HomepageConfig.homepageLayoutMode

    val selectedSets = remember(uiState.manageState.sets) {
        uiState.manageState.sets.filter { it.isSelected }
    }
    val pagerState = rememberPagerState(pageCount = { selectedSets.size.coerceAtLeast(1) })

    val homeString = "首页"
    val currentTitle by remember(layoutMode, selectedSets) {
        derivedStateOf {
            if (layoutMode == 1) homeString
            else selectedSets.getOrNull(pagerState.currentPage)?.sourceName ?: homeString
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomepageEffect.NavigateToBookInfo ->
                    onBookClick(effect.name, effect.author, effect.bookUrl, effect.origin, effect.coverPath, effect.sharedCoverKey)
                is HomepageEffect.NavigateToExploreShow ->
                    onModuleHeaderClick(effect.title, effect.sourceUrl, effect.exploreUrl)
                is HomepageEffect.ShowSnackbar -> {}
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showConfigSheet = !showConfigSheet }) {
                        Icon(Icons.Outlined.Info, contentDescription = "布局设置")
                    }
                    IconButton(onClick = { showManageSheet = !showManageSheet }) {
                        Icon(Icons.Default.Settings, contentDescription = "管理模块")
                    }
                }
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selectedSets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("未选择任何书源集合，请点击右上角设置管理模块", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (layoutMode == 1 && selectedSets.isNotEmpty()) {
                        TabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, selectedSets.size - 1)) {
                            selectedSets.forEachIndexed { index, set ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(set.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { index -> selectedSets.getOrNull(index)?.sourceUrl ?: index }
                    ) { pageIndex ->
                        val source = selectedSets.getOrNull(pageIndex)
                        val sourceModules = remember(uiState.modules, source) {
                            uiState.modules.filter { module ->
                                if (source?.isCustomSet == true) {
                                    val setId = HomepageViewModel.customSetIdFromUrl(source.sourceUrl)
                                    module.customSetId == setId
                                } else {
                                    module.sourceUrl == source?.sourceUrl
                                }
                            }
                        }
                        ModuleList(
                            modules = sourceModules,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onErrorClick = { errorMsg = it },
                            onBookLongClick = { book, _ -> previewBook = book },
                        )
                    }
                }
            }
        }

        if (errorMsg != null) {
            AlertDialog(
                message = errorMsg!!,
                onDismiss = { errorMsg = null },
                onCopy = { errorMsg = null },
            )
        }

        if (showManageSheet) {
            HomepageModuleManageSheet(
                state = uiState.manageState,
                viewModel = viewModel,
                onDismiss = { showManageSheet = false },
            )
        }

        if (showConfigSheet) {
            HomepageLayoutSheet(
                layoutMode = layoutMode,
                onLayoutModeChange = { viewModel.setLayoutMode(it) },
                onDismiss = { showConfigSheet = false },
            )
        }
    }
}

@Composable
private fun ModuleList(
    modules: List<HomepageModuleUi>,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
    onBookLongClick: (SearchBook, String?) -> Unit = { _, _ -> },
    onErrorClick: (String) -> Unit
) {
    if (modules.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("请添加模块定义到书源", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val processedModules = remember(modules) {
            fun isInfinite(m: HomepageModuleUi): Boolean {
                return m.type == HomepageModuleType.Waterfall || m.type == HomepageModuleType.InfiniteGrid
            }
            val infinite = modules.firstOrNull { isInfinite(it) }
            val others = modules.filter { !isInfinite(it) }
            if (infinite != null) others + infinite else others
        }

        val gridColumns = remember(processedModules) {
            val infiniteModule = processedModules.find { m ->
                m.type == HomepageModuleType.Waterfall || m.type == HomepageModuleType.InfiniteGrid
            }
            infiniteModule?.config?.get("layout_columns")?.toIntOrNull() ?: 2
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(gridColumns),
            modifier = modifier,
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        ) {
            processedModules.forEach { moduleUi ->
                item(key = "header_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                    ModuleHeader(
                        title = moduleUi.title,
                        onNavigate = if (moduleUi.type == HomepageModuleType.ButtonGroup) null else {
                            { viewModel.onModuleHeaderClick(moduleUi.sourceUrl, moduleUi.exploreUrl, moduleUi.title) }
                        },
                    )
                }

                when (val state = moduleUi.state) {
                    is ModuleLoadState.Loading -> {
                        item(key = "loading_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is ModuleLoadState.Error -> {
                        item(key = "error_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onErrorClick(state.message) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.retryModule(moduleUi.globalId) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("重试", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    is ModuleLoadState.Buttons -> {
                        item(key = "buttons_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                            ButtonGroupModule(
                                kinds = state.kinds,
                                sourceUrl = moduleUi.sourceUrl,
                                globalId = moduleUi.globalId,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth(),
                                layoutConfig = moduleUi.layoutConfig,
                            )
                        }
                    }
                    is ModuleLoadState.Loaded -> {
                        val config = moduleUi.config
                        when (moduleUi.type) {
                            HomepageModuleType.Waterfall -> {
                                itemsIndexed(state.books, key = { index, item -> "wf_${moduleUi.globalId}_${item.book.bookUrl}_$index" }) { index, item ->
                                    WaterfallItem(
                                        item = item,
                                        onClick = { viewModel.onBookClick(item.book, "home:${moduleUi.globalId}:waterfall:$index") },
                                        onLongClick = onBookLongClick,
                                    )
                                }
                                item(key = "wf_more_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        isEnd = !state.hasMore,
                                        onRetry = { viewModel.loadMoreModule(moduleUi.globalId) }
                                    )
                                }
                            }
                            HomepageModuleType.InfiniteGrid -> {
                                itemsIndexed(state.books, key = { index, item -> "inf_${moduleUi.globalId}_${item.book.bookUrl}_$index" }) { index, item ->
                                    GridBookItem(
                                        book = item.book,
                                        shelfState = item.shelfState,
                                        onClick = { viewModel.onBookClick(item.book, "home:${moduleUi.globalId}:infinite:$index") },
                                        onLongClick = onBookLongClick,
                                    )
                                }
                                item(key = "inf_more_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        isEnd = !state.hasMore,
                                        onRetry = { viewModel.loadMoreModule(moduleUi.globalId) }
                                    )
                                }
                            }
                            HomepageModuleType.Grid -> {
                                val rows = config["layout_rows"]?.toIntOrNull() ?: 2
                                val columns = config["layout_columns"]?.toIntOrNull() ?: 3
                                item(key = "content_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    GridModule(
                                        books = state.books,
                                        onClick = { book, _ -> viewModel.onBookClick(book, "home:${moduleUi.globalId}:grid") },
                                        onLongClick = onBookLongClick,
                                        columns = columns,
                                        maxRows = rows,
                                    )
                                }
                            }
                            HomepageModuleType.Banner -> {
                                item(key = "content_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    BannerModule(
                                        books = state.books,
                                        onClick = { book, _ -> viewModel.onBookClick(book, "home:${moduleUi.globalId}:banner") },
                                        onLongClick = onBookLongClick,
                                    )
                                }
                            }
                            HomepageModuleType.Ranking -> {
                                item(key = "content_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    RankingModule(
                                        books = state.books,
                                        onClick = { book, _ -> viewModel.onBookClick(book, "home:${moduleUi.globalId}:ranking") },
                                        onLongClick = onBookLongClick,
                                    )
                                }
                            }
                            HomepageModuleType.GridRanking -> {
                                item(key = "content_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    GridRankingModule(
                                        books = state.books,
                                        onClick = { book, _ -> viewModel.onBookClick(book, "home:${moduleUi.globalId}:grid-ranking") },
                                        onLongClick = onBookLongClick,
                                        rows = config["layout_rows"]?.toIntOrNull() ?: 4,
                                    )
                                }
                            }
                            HomepageModuleType.Card -> {
                                item(key = "content_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                                    CardModule(
                                        books = state.books,
                                        onClick = { book, _ -> viewModel.onBookClick(book, "home:${moduleUi.globalId}:card") },
                                        onLongClick = onBookLongClick,
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleHeader(title: String, onNavigate: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onNavigate != null) {
            TextButton(onClick = onNavigate, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
            }
        }
    }
}

@Composable
fun GridBookItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    onLongClick: (SearchBook, String?) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick, onLongClick = { onLongClick(book, null) }),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            model = book.coverUrl,
            contentDescription = book.name,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop,
        )
        Text(book.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (shelfState == BookShelfState.IN_SHELF) {
            Text("已在书架", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LoadMoreFooter(isLoading: Boolean, isEnd: Boolean, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator()
            isEnd -> Text("没有更多了", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            else -> TextButton(onClick = onRetry) { Text("加载更多") }
        }
    }
}

@Composable
private fun AlertDialog(message: String, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模块错误") },
        text = {
            SelectionContainer {
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onCopy(message) }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Modifier.heightIn(max: Dp) = this.then(androidx.compose.foundation.layout.heightIn(max = max))
