package io.legado.app.ui.main.homepage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.HomepageModule
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.domain.model.ModuleItem
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class HomepageViewModel(application: Application) : BaseViewModel(application) {

    private val gateway: HomepageModulesGateway = HomepageModulesRepository(
        moduleDao = appDb.homepageModuleDao,
        customSetDao = appDb.homepageCustomSetDao,
    )

    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())

    companion object {
        private const val CUSTOM_SET_URL_PREFIX = "custom://"
        private const val HOMEPAGE_MAX_BUTTON_GROUP_KINDS = 5

        fun customSetUrl(id: String) = "$CUSTOM_SET_URL_PREFIX$id"
        fun isCustomSetUrl(url: String) = url.startsWith(CUSTOM_SET_URL_PREFIX)
        fun customSetIdFromUrl(url: String): String = url.removePrefix(CUSTOM_SET_URL_PREFIX)

        fun isInfinite(type: String?, layoutConfig: String?): Boolean {
            return type == HomepageModuleType.Waterfall.key
                    || type == HomepageModuleType.InfiniteGrid.key
        }

        private fun parseModuleDefs(source: BookSource, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = source.bookSourceUrl) }

        private fun jsonHash(json: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(json.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun List<ModuleItem>.groupBySourceOrdered(): Map<String, List<ModuleItem>> {
            val result = linkedMapOf<String, MutableList<ModuleItem>>()
            for (module in this) {
                val key = module.customSetId?.let { customSetUrl(it) } ?: module.sourceUrl
                result.getOrPut(key) { mutableListOf() }.add(module)
            }
            return result
        }
    }

    private val _effects = MutableSharedFlow<HomepageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()
    private val exploreSourcesFlow = appDb.bookSourceDao.flowExploreSources()

    private val _isRefreshing = MutableStateFlow(false)
    private val _isManageMode = MutableStateFlow(false)
    private val _isConfigMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _moduleContentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSource>>(emptyMap())
    private val _layoutConfigCache = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    private val _pendingEnabled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _pendingUserModules = MutableStateFlow<List<ModuleItem>>(emptyList())
    private val _exploreKindsCache = MutableStateFlow<Map<String, List<ExploreKind>>>(emptyMap())

    private val hiddenSetsFlow = _configVersion.map {
        GSON.fromJsonArray<String>(HomepageConfig.homepageSourceHidden)
            .getOrDefault(emptyList()).toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val localModulesFlow = gateway.flowEnabled()
    val allModulesCache =
        gateway.flowAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customSetsFlow = gateway.flowCustomSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val orderedModuleDefsFlow = combine(localModulesFlow, _configVersion) { modules, _ ->
        modules.groupBySourceOrdered()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val setsFlow = combine(
        localModulesFlow,
        allModulesCache,
        customSetsFlow,
        _configVersion
    ) { _, allModules, customSets, _ ->
        val hiddenSourceUrls = GSON.fromJsonArray<String>(HomepageConfig.homepageSourceHidden)
            .getOrDefault(emptyList()).toSet()
        val moduleCountsBySet =
            allModules.mapNotNull { it.customSetId }.groupBy { it }.mapValues { it.value.size }

        customSets.sortedBy { it.sortOrder }.map { set ->
            HomepageSourceManageUi(
                sourceUrl = customSetUrl(set.id),
                sourceName = set.name,
                sourceGroup = null,
                isSelected = customSetUrl(set.id) !in hiddenSourceUrls,
                moduleCount = moduleCountsBySet[set.id] ?: 0,
                isCustomSet = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseSourcesFlow = exploreSourcesFlow.map { sources ->
        sources.map { source ->
            HomepageSourceManageUi(
                sourceUrl = source.bookSourceUrl,
                sourceName = source.bookSourceName,
                sourceGroup = source.bookSourceGroup,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browseGroupsFlow = browseSourcesFlow.map { sources ->
        val groups = mutableSetOf<String>()
        sources.forEach { source ->
            source.sourceGroup?.split(",")?.forEach { g ->
                val trimmed = g.trim()
                if (trimmed.isNotBlank()) groups.add(trimmed)
            }
        }
        groups.toList().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _browseGroupFilter = MutableStateFlow("")

    val browseFilteredSourcesFlow = combine(browseSourcesFlow, _browseGroupFilter) { sources, group ->
        if (group.isBlank()) sources
        else sources.filter { source ->
            source.sourceGroup?.split(",")?.any { it.trim() == group } == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val uiFlagsFlow =
        combine(_isRefreshing, _isManageMode, _isConfigMode) { refreshing, manage, config ->
            HomepageUiFlags(refreshing, manage, config)
        }

    private data class ManageModulesTuple(
        val allModules: List<ModuleItem>,
        val sourcesCache: Map<String, BookSource>,
        val pendingEnabled: Map<String, Boolean>
    )

    private val manageStateFlow = combine(
        setsFlow,
        browseFilteredSourcesFlow,
        browseGroupsFlow,
        _browseGroupFilter
    ) { sets, browseSources, browseGroups, browseGroupFilter ->
        ManageBrowseTuple(sets, browseSources, browseGroups, browseGroupFilter)
    }.combine(
        combine(allModulesCache, _bookSourcesCache, _pendingEnabled) { allModules, sourcesCache, pendingEnabled ->
            ManageModulesTuple(allModules, sourcesCache, pendingEnabled)
        }
    ) { browseTuple, modulesTuple ->
        HomepageManageUiState(
            sets = browseTuple.sets,
            browseSources = browseTuple.browseSources,
            browseGroups = browseTuple.browseGroups,
            browseGroupFilter = browseTuple.browseGroupFilter,
            allJoinedModules = modulesTuple.allModules.map { module ->
                HomepageModuleManageUi(
                    id = module.id,
                    sourceUrl = module.sourceUrl,
                    moduleKey = module.moduleKey,
                    title = module.displayTitle,
                    customSetTitle = module.customSetTitle,
                    customSetId = module.customSetId,
                    isVisible = modulesTuple.pendingEnabled[module.id] ?: module.isEnabled,
                    type = module.type,
                    url = module.url,
                    args = module.args,
                    layoutConfig = module.layoutConfig,
                    originalTitle = module.title,
                )
            },
            sourceNames = modulesTuple.sourcesCache.mapValues { it.value.bookSourceName }
        )
    }

    private data class ManageBrowseTuple(
        val sets: List<HomepageSourceManageUi>,
        val browseSources: List<HomepageSourceManageUi>,
        val browseGroups: List<String>,
        val browseGroupFilter: String
    )

    private val rawModulesFlow = combine(
        orderedModuleDefsFlow,
        _moduleContentStates,
        _bookSourcesCache,
        customSetsFlow,
        _layoutConfigCache
    ) { grouped, contentStates, sourcesCache, customSets, configCache ->
        val setNames = customSets.associate { it.id to it.name }
        val sortedSetIds = customSets.sortedBy { it.sortOrder }.map { it.id }

        sortedSetIds.flatMap { setId ->
            val setUrl = customSetUrl(setId)
            val mods = grouped[setUrl] ?: emptyList()
            mods.map { module ->
                val source = sourcesCache[module.sourceUrl]
                val sourceName = source?.bookSourceName ?: module.sourceUrl
                val setName = module.customSetId?.let { setNames[it] } ?: sourceName
                val exploreUrl = module.url ?: source?.exploreUrl
                val configMap = configCache[module.id] ?: emptyMap()

                HomepageModuleUi(
                    sourceUrl = module.sourceUrl,
                    setName = setName,
                    globalId = module.id,
                    type = HomepageModuleType.fromKey(module.type),
                    title = module.displayTitle,
                    exploreUrl = exploreUrl,
                    customSetId = module.customSetId,
                    layoutConfig = module.layoutConfig,
                    state = contentStates[module.id] ?: ModuleLoadState.Loading,
                    config = configMap
                )
            }
        }
    }

    private val displayModulesFlow = combine(
        rawModulesFlow,
        _bookshelf
    ) { modules, bookshelf ->
        if (bookshelf.isEmpty()) {
            modules.map { module ->
                val state = module.state
                if (state is ModuleLoadState.Loaded) {
                    module.copy(state = state.copy(
                        books = state.books.map { item ->
                            if (item.shelfState == BookShelfState.NOT_IN_SHELF) item
                            else item.copy(shelfState = BookShelfState.NOT_IN_SHELF)
                        }
                    ))
                } else module
            }
        } else {
            val exactKeys = HashSet<Triple<String, String, String?>>(bookshelf.size)
            val nameAuthorKeys = HashSet<Pair<String, String>>(bookshelf.size)
            for (key in bookshelf) {
                exactKeys.add(Triple(key.name, key.author, key.url))
                nameAuthorKeys.add(key.name to key.author)
            }
            modules.map { module ->
                val state = module.state
                if (state is ModuleLoadState.Loaded) {
                    module.copy(state = state.copy(
                        books = state.books.map { item ->
                            val bookTriple = Triple(item.book.name, item.book.author, item.book.bookUrl)
                            val newShelfState = when {
                                bookTriple in exactKeys -> BookShelfState.IN_SHELF
                                (item.book.name to item.book.author) in nameAuthorKeys ->
                                    BookShelfState.SAME_NAME_AUTHOR
                                else -> BookShelfState.NOT_IN_SHELF
                            }
                            if (item.shelfState == newShelfState) item
                            else item.copy(shelfState = newShelfState)
                        }
                    ))
                } else module
            }
        }
    }

    val uiState: StateFlow<HomepageUiState> = combine(
        displayModulesFlow,
        uiFlagsFlow,
        manageStateFlow
    ) { modules, flags, manageState ->
        HomepageUiState(
            modules = modules,
            isRefreshing = flags.isRefreshing,
            isManageMode = flags.isManageMode,
            isConfigMode = flags.isConfigMode,
            manageState = manageState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageUiState())

    init {
        viewModelScope.launch {
            localModulesFlow.collect { modules ->
                val cache = mutableMapOf<String, Map<String, String>>()
                for (module in modules) {
                    val configStr = module.layoutConfig ?: continue
                    try {
                        val json = GSON.fromJson(configStr, Map::class.java)
                        if (json != null) {
                            val map = mutableMapOf<String, String>()
                            json.forEach { (k, v) -> map["layout_$k"] = v.toString() }
                            cache[module.id] = map
                        }
                    } catch (_: Exception) {
                    }
                }
                _layoutConfigCache.value = cache
            }
        }

        viewModelScope.launch {
            exploreSourcesFlow.collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        viewModelScope.launch {
            uiState.map { it.modules }.collect { modules ->
                modules.forEach { ui ->
                    if (ui.state is ModuleLoadState.Loading && loadJobs[ui.globalId]?.isActive != true) {
                        val module = gateway.getById(ui.globalId)
                        if (module != null) loadModule(module)
                    }
                }
            }
        }

        viewModelScope.launch {
            allModulesCache.collect { modules ->
                val dbIds = modules.map { it.id }.toSet()
                _pendingUserModules.update { pending -> pending.filter { it.id !in dbIds } }
                _moduleContentStates.update { states ->
                    if (states.keys.any { it !in dbIds }) states.filterKeys { it in dbIds }
                    else states
                }
            }
        }

        viewModelScope.launch {
            val allModules = allModulesCache.first()
            val orphans = allModules.filter { it.customSetId == null }
            if (orphans.isNotEmpty()) {
                orphans.groupBy { it.sourceUrl }.forEach { (sourceUrl, modules) ->
                    val source = appDb.bookSourceDao.getBookSource(sourceUrl) ?: return@forEach
                    ensureSetForSource(sourceUrl, source.bookSourceName)
                    modules.forEach { m -> gateway.setCustomSetId(m.id, "src_$sourceUrl") }
                }
            }
            allModules.mapNotNull { it.customSetId }.distinct().forEach { setId ->
                val isSrcSet = setId.startsWith("src_")
                if (isSrcSet && gateway.getCustomSetById(setId) == null) {
                    val sourceUrl = setId.removePrefix("src_")
                    val source = appDb.bookSourceDao.getBookSource(sourceUrl)
                    if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
                }
            }
        }

        viewModelScope.launch {
            appDb.bookDao.flowAll().collect { books ->
                _bookshelf.value = books.map { BookShelfKey(it.name, it.author, it.bookUrl) }.toSet()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    private suspend fun syncModulesFromSource(source: BookSource) {
        val json = source.homepageModules ?: return
        ensureSetForSource(source.bookSourceUrl, source.bookSourceName)
        val parsedDefs = parseModuleDefs(source, json)
        val newHash = jsonHash(json)

        val existingModules = gateway.flowBySource(source.bookSourceUrl).first()
        val existingById = existingModules.associateBy { it.id }
        val parsedIds = parsedDefs.map { it.globalId }.toSet()

        val toUpsert = mutableListOf<ModuleItem>()
        for (i in parsedDefs.indices) {
            val def = parsedDefs[i]
            val existing = existingById[def.globalId]
            if (existing != null) {
                if (existing.isUserCreated) continue
                if (existing.sourceJsonHash == newHash) continue
                toUpsert.add(
                    existing.copy(
                        type = def.type, title = def.title, args = def.args, url = def.url,
                        sourceJsonHash = newHash, syncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                toUpsert.add(
                    ModuleItem(
                        id = def.globalId,
                        sourceUrl = source.bookSourceUrl,
                        moduleKey = def.key,
                        type = def.type,
                        title = def.title,
                        args = def.args,
                        url = def.url,
                        isEnabled = true,
                        customSetId = "src_${source.bookSourceUrl}",
                        sortOrder = i,
                        sourceJsonHash = newHash,
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        if (toUpsert.isNotEmpty()) gateway.upsertAll(toUpsert)
        if (parsedIds.isNotEmpty()) gateway.deleteStale(source.bookSourceUrl, parsedIds.toList())
    }

    private fun loadModule(module: ModuleItem) {
        loadJobs[module.id]?.cancel()
        if (module.type == HomepageModuleType.ButtonGroup.key) {
            loadJobs[module.id] = viewModelScope.launch {
                kotlin.runCatching {
                    val source = appDb.bookSourceDao.getBookSource(module.sourceUrl)
                        ?: throw Exception("Source not found")
                    val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                    val selectedTitles =
                        module.args?.let { GSON.fromJsonArray<String>(it).getOrNull() }
                    if (selectedTitles.isNullOrEmpty()) allKinds.take(HOMEPAGE_MAX_BUTTON_GROUP_KINDS)
                    else selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                }.onSuccess { kinds ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Buttons(kinds)) }
                }.onFailure { e ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceToString())) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        loadJobs[module.id] = viewModelScope.launch {
            kotlin.runCatching {
                val source = appDb.bookSourceDao.getBookSource(module.sourceUrl)
                    ?: throw Exception("Source not found: ${module.sourceUrl}")
                val isRanking =
                    module.type == HomepageModuleType.Ranking.key || module.type == HomepageModuleType.GridRanking.key
                val exploreUrl = module.url ?: source.exploreUrl
                if (exploreUrl.isNullOrBlank()) throw Exception("No explore URL for module ${module.title}")

                val books = withContext(Dispatchers.IO) {
                    if (isRanking) {
                        val allBooks = mutableListOf<SearchBook>()
                        var page = 1
                        while (allBooks.size < 20 && page <= 3) {
                            val pageBooks = WebBook.exploreBookAwait(source, exploreUrl, page)
                            if (pageBooks.isEmpty()) break
                            allBooks.addAll(pageBooks)
                            page++
                        }
                        allBooks.take(20)
                    } else {
                        WebBook.exploreBookAwait(source, exploreUrl, 1)
                    }
                }

                val hasMore = isInfinite(module.type, module.layoutConfig) && books.isNotEmpty()
                books to hasMore
            }.onSuccess { (books, hasMore) ->
                val shelf = _bookshelf.value
                _moduleContentStates.update {
                    it + (module.id to ModuleLoadState.Loaded(
                        books.map { book ->
                            HomepageBookItemUi(
                                book = book,
                                shelfState = resolveBookShelfState(book.name, book.author, book.bookUrl, shelf)
                            )
                        },
                        hasMore = hasMore
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceToString())) }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
    }

    fun loadMoreModule(globalId: String) {
        val currentState = _moduleContentStates.value[globalId] as? ModuleLoadState.Loaded ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return
        val nextPage = currentState.page + 1
        _moduleContentStates.update { it + (globalId to currentState.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            kotlin.runCatching {
                val module = gateway.getById(globalId) ?: throw Exception("Module not found")
                val source = appDb.bookSourceDao.getBookSource(module.sourceUrl)
                    ?: throw Exception("Source not found")
                val exploreUrl = module.url ?: source.exploreUrl ?: throw Exception("No URL")
                val exploreKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                val kind = if (module.args.isNullOrBlank()) exploreKinds.firstOrNull()
                else exploreKinds.find { it.title == module.args }
                val books = if (kind != null) {
                    WebBook.exploreBookAwait(source, kind.url ?: exploreUrl, nextPage)
                } else {
                    arrayListOf<SearchBook>()
                }
                books
            }.onSuccess { result ->
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    val existingUrls = lastState.books.map { it.book.bookUrl }.toSet()
                    val shelf = _bookshelf.value
                    val deduped = result.filter { it.bookUrl !in existingUrls }.map { book ->
                        HomepageBookItemUi(
                            book = book,
                            shelfState = resolveBookShelfState(book.name, book.author, book.bookUrl, shelf)
                        )
                    }
                    states + (globalId to ModuleLoadState.Loaded(
                        books = lastState.books + deduped,
                        hasMore = deduped.isNotEmpty(), isLoadingMore = false, page = nextPage
                    ))
                }
            }.onFailure {
                _moduleContentStates.update { states ->
                    val lastState = states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    states + (globalId to lastState.copy(isLoadingMore = false))
                }
            }
        }
    }

    fun refreshButtonGroup(globalId: String) {
        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            loadModule(module)
        }
    }

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                loadJobs.values.forEach { it.cancel() }
                loadJobs.clear()
                val existingSourceUrls = uiState.value.modules.map { it.sourceUrl }.toSet()
                val sourcesToSync = if (existingSourceUrls.isEmpty()) {
                    appDb.bookSourceDao.flowHomepageModules().first().map { it.bookSourceUrl }
                } else {
                    existingSourceUrls
                }
                sourcesToSync.forEach { url ->
                    resolveBookSource(url)?.let { syncModulesFromSource(it) }
                }
                _moduleContentStates.value = emptyMap()
                kotlinx.coroutines.delay(1500)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retryModule(globalId: String) {
        _moduleContentStates.update { it + (globalId to ModuleLoadState.Loading) }
    }

    fun toggleManageMode() = _isManageMode.update { !it }
    fun toggleConfigMode() = _isConfigMode.update { !it }

    fun setBrowseGroupFilter(group: String) {
        _browseGroupFilter.value = group
    }

    fun setModuleVisible(id: String, visible: Boolean) {
        _pendingEnabled.update { it + (id to visible) }
        viewModelScope.launch {
            if (gateway.getById(id) != null) gateway.setEnabled(id, visible)
            else {
                val parts = id.split("::")
                if (parts.size >= 3) {
                    val setId = parts[0]; val sourceUrl = parts[1]
                    val key = parts.subList(2, parts.size).joinToString("::")
                    ensureModuleInDb(sourceUrl, key, id, setId)
                    gateway.setEnabled(id, visible)
                }
            }
            _pendingEnabled.update { it - id }
            notifyConfigChanged()
        }
    }

    fun toggleSourceFilter(sourceUrl: String, isEnabled: Boolean) {
        val hidden = GSON.fromJsonArray<String>(HomepageConfig.homepageSourceHidden)
            .getOrDefault(emptyList()).toMutableSet()
        if (isEnabled) hidden.remove(sourceUrl) else hidden.add(sourceUrl)
        HomepageConfig.homepageSourceHidden = GSON.toJson(hidden.toList())
        notifyConfigChanged()
    }

    fun setLayoutMode(mode: Int) {
        HomepageConfig.homepageLayoutMode = mode
        notifyConfigChanged()
    }

    private suspend fun ensureSetForSource(sourceUrl: String, sourceName: String): String {
        val setId = "src_$sourceUrl"
        if (gateway.getCustomSetById(setId) == null) gateway.upsertCustomSet(
            CustomSetItem(id = setId, name = sourceName)
        )
        return setId
    }

    fun addCustomModule(sourceUrl: String, targetSetId: String?, def: ModuleDef) {
        val key = def.key.ifBlank { def.title }
        val setId = targetSetId ?: "src_$sourceUrl"
        if (isInfinite(def.type, def.layoutConfig)) {
            if (allModulesCache.value.any {
                    it.customSetId == setId && isInfinite(it.type, it.layoutConfig)
                }) {
                viewModelScope.launch {
                    _effects.emit(HomepageEffect.ShowSnackbar("该集合已存在无限流模块"))
                }
                return
            }
        }
        val id = ModuleDef.globalIdOf(sourceUrl, key, setId)
        val module = ModuleItem(
            id = id, sourceUrl = sourceUrl, moduleKey = key, type = def.type, title = def.title,
            args = def.args, layoutConfig = def.layoutConfig, url = def.url, isEnabled = true,
            isUserCreated = true, customSetId = setId, syncedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
            gateway.upsertAll(listOf(module))
            _pendingUserModules.update { list -> if (list.any { it.id == id }) list else list + module }
            notifyConfigChanged()
        }
    }

    fun updateModule(globalId: String, def: ModuleDef) {
        viewModelScope.launch {
            val existing = gateway.getById(globalId) ?: return@launch
            if (isInfinite(def.type, def.layoutConfig)) {
                val hasOtherInfinite = allModulesCache.value.any {
                    it.customSetId == existing.customSetId && it.id != globalId && isInfinite(it.type, it.layoutConfig)
                }
                if (hasOtherInfinite) {
                    _effects.emit(HomepageEffect.ShowSnackbar("该集合已存在无限流模块"))
                    return@launch
                }
            }
            gateway.upsertAll(
                listOf(
                    existing.copy(
                        customTitle = def.title.takeIf { it != existing.title }, type = def.type,
                        url = def.url, args = def.args, layoutConfig = def.layoutConfig,
                        isUserCreated = true, syncedAt = System.currentTimeMillis()
                    )
                )
            )
            notifyConfigChanged()
        }
    }

    fun setModuleCustomSetTitle(globalId: String, customSetTitle: String?) {
        viewModelScope.launch { gateway.setCustomSetTitle(globalId, customSetTitle); notifyConfigChanged() }
    }

    fun deleteModule(globalId: String) {
        viewModelScope.launch {
            gateway.delete(globalId); _moduleContentStates.update { it - globalId }
            loadJobs.remove(globalId)?.cancel(); _pendingEnabled.update { it - globalId }
            _pendingUserModules.update { it.filter { m -> m.id != globalId } }
            notifyConfigChanged()
        }
    }

    fun reorderJoinedModules(orderedIds: List<String>) {
        viewModelScope.launch {
            val orders = orderedIds.mapIndexed { index, id -> id to index }.toMap()
            gateway.batchSetSortOrders(orders); notifyConfigChanged()
        }
    }

    fun reorderCustomSets(orderedUrls: List<String>) {
        viewModelScope.launch {
            val orders = orderedUrls.mapIndexed { index, url -> customSetIdFromUrl(url) to index }.toMap()
            gateway.batchSetCustomSetSortOrders(orders); notifyConfigChanged()
        }
    }

    fun getSourceModules(sourceUrl: String, targetSetId: String? = null): List<HomepageModuleManageUi> {
        val source = resolveBookSource(sourceUrl) ?: return emptyList()
        val json = source.homepageModules ?: return emptyList()
        val jsonDefs = parseModuleDefs(source, json)
        val effectiveSetId = targetSetId ?: "src_$sourceUrl"
        val joinedKeys = allModulesCache.value
            .filter { it.sourceUrl == sourceUrl && it.customSetId == effectiveSetId }
            .map { it.moduleKey }.toSet()
        return jsonDefs.map { def ->
            HomepageModuleManageUi(
                id = ModuleDef.globalIdOf(sourceUrl, def.key, effectiveSetId),
                sourceUrl = def.sourceUrl,
                moduleKey = def.key,
                title = def.title,
                isVisible = joinedKeys.contains(def.key),
                customSetId = if (joinedKeys.contains(def.key)) effectiveSetId else null,
                originalTitle = def.title,
                type = def.type,
                url = def.url,
                args = def.args,
            )
        }
    }

    fun joinModule(sourceUrl: String, targetSetId: String?, def: ModuleDef) =
        addCustomModule(sourceUrl, targetSetId, def)

    fun syncSourceModules(sourceUrl: String) {
        viewModelScope.launch { resolveBookSource(sourceUrl)?.let { syncModulesFromSource(it) } }
    }

    fun addButtonGroupFromKinds(sourceUrl: String, targetSetId: String?, title: String, kindTitles: List<String>) {
        val key = kindTitles.firstOrNull() ?: title
        val setId = targetSetId ?: "src_$sourceUrl"
        val id = ModuleDef.globalIdOf(sourceUrl, key, setId)
        val module = ModuleItem(
            id = id, sourceUrl = sourceUrl, moduleKey = key, type = "buttonGroup",
            title = title, args = GSON.toJson(kindTitles), isEnabled = true,
            isUserCreated = true, customSetId = setId, syncedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
            gateway.upsertAll(listOf(module))
            _pendingUserModules.update { list -> if (list.any { it.id == id }) list else list + module }
            notifyConfigChanged()
        }
    }

    fun assignModuleToCustomSet(moduleId: String, customSetId: String?) {
        viewModelScope.launch {
            val existing = gateway.getById(moduleId) ?: return@launch
            if (customSetId == null) {
                if (existing.customSetId != "src_${existing.sourceUrl}") gateway.delete(moduleId)
            } else {
                if (isInfinite(existing.type, existing.layoutConfig)) {
                    val hasInfinite = allModulesCache.value.any {
                        it.customSetId == customSetId && isInfinite(it.type, it.layoutConfig)
                    }
                    if (hasInfinite) {
                        _effects.emit(HomepageEffect.ShowSnackbar("该集合已存在无限流模块"))
                        return@launch
                    }
                }
                val newId = ModuleDef.globalIdOf(existing.sourceUrl, existing.moduleKey, customSetId)
                gateway.upsertAll(
                    listOf(existing.copy(id = newId, customSetId = customSetId, isEnabled = true, isUserCreated = true))
                )
            }
            notifyConfigChanged()
        }
    }

    fun createCustomSet(name: String) {
        viewModelScope.launch { gateway.createCustomSet(name); notifyConfigChanged() }
    }

    fun renameCustomSet(id: String, name: String) {
        viewModelScope.launch { gateway.renameCustomSet(id, name); notifyConfigChanged() }
    }

    fun deleteCustomSet(id: String) {
        viewModelScope.launch {
            val ids = allModulesCache.value.filter { it.customSetId == id }.map { it.id }
            gateway.deleteCustomSet(id)
            ids.forEach { mid ->
                _moduleContentStates.update { it - mid }; loadJobs.remove(mid)?.cancel()
                _pendingEnabled.update { it - mid }
            }
            notifyConfigChanged()
        }
    }

    fun getCurrentBookShelfState(book: SearchBook): BookShelfState {
        return resolveBookShelfState(book.name, book.author, book.bookUrl, _bookshelf.value)
    }

    fun onAddToShelf(book: SearchBook) {
        execute { appDb.bookDao.insert(book.toBook()) }
    }

    fun onBookClick(book: SearchBook, sharedCoverKey: String?) {
        viewModelScope.launch {
            appDb.searchBookDao.insert(book)
            _effects.emit(
                HomepageEffect.NavigateToBookInfo(
                    book.name, book.author, book.bookUrl, book.origin, book.coverUrl, sharedCoverKey
                )
            )
        }
    }

    fun onModuleHeaderClick(sourceUrl: String, exploreUrl: String?, title: String?) {
        viewModelScope.launch {
            _effects.emit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, exploreUrl))
        }
    }

    fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> {
        return _exploreKindsCache.value[sourceUrl] ?: emptyList()
    }

    fun loadExploreKinds(sourceUrl: String) {
        viewModelScope.launch {
            val source = resolveBookSource(sourceUrl) ?: return@launch
            val kinds = withContext(Dispatchers.IO) { source.exploreKinds() }
            _exploreKindsCache.update { it + (sourceUrl to kinds) }
        }
    }

    private fun resolveBookSource(sourceUrl: String): BookSource? =
        _bookSourcesCache.value[sourceUrl] ?: appDb.bookSourceDao.getBookSource(sourceUrl)

    private suspend fun ensureModuleInDb(sourceUrl: String, moduleKey: String, id: String, setId: String) {
        if (gateway.getById(id) != null) return
        val source = resolveBookSource(sourceUrl) ?: return
        val defs = parseModuleDefs(source, source.homepageModules ?: return)
        val def = defs.find { it.key == moduleKey } ?: return
        gateway.upsertAll(
            listOf(
                ModuleItem(
                    id = id, sourceUrl = sourceUrl, moduleKey = moduleKey, type = def.type,
                    title = def.title, args = def.args, url = def.url, isEnabled = true,
                    customSetId = setId,
                )
            )
        )
    }

    private fun notifyConfigChanged() {
        _configVersion.update { it + 1 }
    }

    private fun resolveBookShelfState(name: String, author: String, url: String, shelf: Set<BookShelfKey>): BookShelfState {
        val exactMatch = shelf.any { it.name == name && it.author == author && it.url == url }
        if (exactMatch) return BookShelfState.IN_SHELF
        val nameAuthorMatch = shelf.any { it.name == name && it.author == author }
        if (nameAuthorMatch) return BookShelfState.SAME_NAME_AUTHOR
        return BookShelfState.NOT_IN_SHELF
    }
}

private data class BookShelfKey(val name: String, val author: String, val url: String)

private data class HomepageUiFlags(
    val isRefreshing: Boolean,
    val isManageMode: Boolean,
    val isConfigMode: Boolean
)
