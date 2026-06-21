package io.legado.app.ui.main.explore

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexboxLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.ItemFilletCompleteTextBinding
import io.legado.app.databinding.ItemFilletSelectorSingleBinding
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiTitleTypeface
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.lib.theme.UiCorner
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.explore.ExploreShowAdapter
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.SearchBookOpenHelper
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.ui.widget.SourceSelectDialog
import io.legado.app.utils.applyMainBottomBarPadding
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.gone
import io.legado.app.utils.InfoMap
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.postEvent
import io.legado.app.utils.transaction
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.windowSize
import kotlinx.coroutines.CoroutineScope
import android.app.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.Window
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.homepage.SearchBookPreviewSheet

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack,
    ExploreShowAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val discoverBookAdapter by lazy { ExploreShowAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val discoverBookLayoutManager by lazy { LinearLayoutManager(requireContext()) }
    private val searchView: SearchView? by lazy {
        binding.titleBar.findViewById<SearchView?>(R.id.search_view)
    }
    
    // Modern mode title bar views
    private val modernTitleBar: io.legado.app.ui.widget.TitleBar? by lazy {
        binding.titleBarModern
    }
    private val tvDiscoverSourceSelect: TextView? by lazy {
        modernTitleBar?.findViewById<TextView?>(R.id.tv_discover_source_select)
    }
    private val llDiscoverSourceSelect: LinearLayout? by lazy {
        modernTitleBar?.findViewById<LinearLayout?>(R.id.ll_discover_source_select)
    }
    private val btnDiscoverSourceSearch: android.widget.ImageButton? by lazy {
        modernTitleBar?.findViewById<android.widget.ImageButton?>(R.id.btn_discover_source_search)
    }
    private val btnDiscoverEditSource: android.widget.ImageButton? by lazy {
        modernTitleBar?.findViewById<android.widget.ImageButton?>(R.id.btn_discover_edit_source)
    }
    private val btnDiscoverSourceLogin: android.widget.ImageButton? by lazy {
        modernTitleBar?.findViewById<android.widget.ImageButton?>(R.id.btn_discover_source_login)
    }
    private val btnDiscoverLayoutToggle: android.widget.ImageButton? by lazy {
        modernTitleBar?.findViewById<android.widget.ImageButton?>(R.id.btn_discover_layout_toggle)
    }
    private val btnDiscoverModeToggle: android.widget.ImageButton? by lazy {
        modernTitleBar?.findViewById<android.widget.ImageButton?>(R.id.btn_discover_mode_toggle)
    }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null
    private var oldModeInitialized = false
    private var modernModeInitialized = false
    private var usingModernDiscovery = false
    private var sourceMenuPopup: PopupWindow? = null

    private var discoverSourceFlowJob: Job? = null
    private var discoverBookshelfFlowJob: Job? = null
    private var discoverWarmupJob: Job? = null
    private var discoverLoadJob: Job? = null
    private var discoverActionJob: Job? = null
    private val discoverSources = mutableListOf<BookSourcePart>()
    private val discoverAllTagItems = mutableListOf<DiscoverTagItem>()
    private val discoverTagItems = mutableListOf<DiscoverTagItem>()
    private val discoverSelectItems = mutableListOf<DiscoverTagItem>()
    private val discoverMajorGroups = mutableListOf<String>()
    private val discoverBookshelf = linkedSetOf<String>()
    private val discoverBooks = linkedSetOf<SearchBook>()
    private val blockedButtonActions = hashMapOf<String, MutableSet<String>>()
    private var selectedDiscoverSourcePart: BookSourcePart? = null
    private var selectedDiscoverSource: BookSource? = null
    private var discoverCurrentUrl: String? = null
    private var discoverPage = 1
    private var discoverHasMore = true
    private var discoverLoading = false
    private var selectedDiscoverMajorGroup: String? = null
    private var selectedDiscoverTagIndex = -1
    private var selectedDiscoverUrlIndex = -1
    private var discoverRequestVersion = 0L
    private var discoverSourceVersion = 0L
    private var discoveryModeLoaded = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        usingModernDiscovery = AppConfig.modernDiscoveryPage
        discoveryModeLoaded = false
        binding.swipeRefreshLayout.setColorSchemeColors(accentColor)
        binding.swipeRefreshLayout.setProgressViewOffset(true, (-28).dpToPx(), 56.dpToPx())
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            currentDiscoverScrollTarget()?.canScrollVertically(-1) == true
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (usingModernDiscovery) {
                loadDiscoverBooks(reset = true)
            } else {
                upExploreData(searchView?.query?.toString())
            }
        }
        binding.rvFind.clipToPadding = false
        binding.rvFind.applyMainBottomBarPadding()
        binding.rvDiscoverBooks.clipToPadding = false
        binding.rvDiscoverBooks.applyMainBottomBarPadding(withInitialPadding = true)
        applyDiscoveryMode(loadData = false)
        scheduleDiscoveryWarmup()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        
        // Update menu item title based on current mode
        val modeItem = menu.findItem(R.id.menu_discovery_mode)
        modeItem?.title = if (AppConfig.modernDiscoveryPage) {
            getString(R.string.discovery_page_mode_legacy)
        } else {
            getString(R.string.discovery_page_mode_modern)
        }
        
        if (usingModernDiscovery) {
            groupsMenu = null
            return
        }
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    private fun applyDiscoveryMode(loadData: Boolean = true) {
        val modern = AppConfig.modernDiscoveryPage
        usingModernDiscovery = modern
        binding.titleBar.isGone = modern
        binding.titleBarModern.isVisible = modern
        binding.llModernDiscovery.isVisible = modern
        binding.rvFind.isGone = modern
        binding.tvEmptyMsg.isGone = modern
        searchView?.isGone = modern
        
        // ✅ 关键修复：动态更新 SwipeRefreshLayout 的顶部约束
        val constraintLayout = binding.root as androidx.constraintlayout.widget.ConstraintLayout
        val constraintSet = androidx.constraintlayout.widget.ConstraintSet()
        constraintSet.clone(constraintLayout)
        
        if (modern) {
            // 现代模式：约束到 title_bar_modern
            constraintSet.connect(
                R.id.swipe_refresh_layout,
                androidx.constraintlayout.widget.ConstraintSet.TOP,
                R.id.title_bar_modern,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM
            )
        } else {
            // 经典模式：约束到 title_bar
            constraintSet.connect(
                R.id.swipe_refresh_layout,
                androidx.constraintlayout.widget.ConstraintSet.TOP,
                R.id.title_bar,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM
            )
        }
        
        constraintSet.applyTo(constraintLayout)
        
        if (!loadData) {
            activity?.invalidateOptionsMenu()
            updateDiscoverModeToggleButtonState()
            return
        }
        if (modern) {
            exploreFlowJob?.cancel()
            initModernMode()
        } else {
            stopModernMode()
            initClassicMode()
        }
        activity?.invalidateOptionsMenu()
        updateDiscoverModeToggleButtonState()
    }

    private fun currentDiscoverScrollTarget(): View? {
        return when {
            usingModernDiscovery -> binding.rvDiscoverBooks
            else -> binding.rvFind
        }
    }

    private fun scheduleDiscoveryWarmup() {
        discoverWarmupJob?.cancel()
        if (!AppConfig.modernDiscoveryPage) return
        discoverWarmupJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(1800)
            if (!isAdded || discoveryModeLoaded || !AppConfig.modernDiscoveryPage) return@launch
            applyDiscoveryMode(loadData = true)
            discoveryModeLoaded = true
        }
    }

    private fun initClassicMode() {
        if (!oldModeInitialized) {
            oldModeInitialized = true
            initSearchView()
            initRecyclerView()
            initGroupData()
        }
        if (exploreFlowJob?.isActive != true) {
            upExploreData(searchView?.query?.toString())
        }
    }

    private fun initModernMode() {
        if (!modernModeInitialized) {
            modernModeInitialized = true
            initDiscoverRecycler()
            bindDiscoverSourceSelector()
            updateDiscoverLoginButtonState()
            updateDiscoverModeToggleButtonState()
            // Apply title bar text and icon colors from theme
            applyModernTitleBarTheme()
            // TitleBar automatically handles status bar padding and theme colors
        }
        observeDiscoverSources()
        observeDiscoverBookshelf()
    }

    private fun stopModernMode() {
        sourceMenuPopup?.dismiss()
        sourceMenuPopup = null
        discoverWarmupJob?.cancel()
        discoverWarmupJob = null
        discoverSourceFlowJob?.cancel()
        discoverSourceFlowJob = null
        discoverBookshelfFlowJob?.cancel()
        discoverBookshelfFlowJob = null
        discoverActionJob?.cancel()
        discoverActionJob = null
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverSourceVersion += 1
        discoverRequestVersion += 1
        discoverLoading = false
        binding.pbDiscoverLoading.gone()
        discoverAllTagItems.clear()
        discoverMajorGroups.clear()
        discoverTagItems.clear()
        selectedDiscoverMajorGroup = null
        selectedDiscoverTagIndex = -1
        selectedDiscoverUrlIndex = -1
    }

    private fun initSearchView() {
        val view = searchView ?: return
        view.applyTint(primaryTextColor)
        view.isSubmitButtonEnabled = true
        view.queryHint = getString(R.string.screen_find)
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initDiscoverRecycler() {
        binding.rvDiscoverTags.setOnTagClickListener { index ->
            val item = discoverTagItems.getOrNull(index) ?: return@setOnTagClickListener
            if (item.isButton) {
                handleDiscoverButtonTag(item)
                return@setOnTagClickListener
            }
            selectDiscoverTag(index, item, selectTab = true)
        }
        binding.rvDiscoverSelects.setOnTagClickListener { index ->
            val group = discoverMajorGroups.getOrNull(index) ?: return@setOnTagClickListener
            selectedDiscoverMajorGroup = group
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
        }
        applyDiscoverBookLayout()
        binding.rvDiscoverBooks.adapter = discoverBookAdapter
        binding.rvDiscoverBooks.setEdgeEffectColor(primaryColor)
        binding.rvDiscoverBooks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                    loadDiscoverBooks(reset = false)
                }
            }
        })
    }

    private fun bindDiscoverSourceSelector() {
        tvDiscoverSourceSelect?.applyUiTitleTypeface(requireContext())
        val updateSourceNameWidth = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateDiscoverSourceNameWidth()
        }
        modernTitleBar?.addOnLayoutChangeListener(updateSourceNameWidth)
        modernTitleBar?.post(::updateDiscoverSourceNameWidth)
        llDiscoverSourceSelect?.setOnClickListener {
            showDiscoverSourceMenu()
        }
        llDiscoverSourceSelect?.setOnLongClickListener {
            showDiscoverKindsDialog()
            true
        }
        btnDiscoverSourceLogin?.setOnClickListener {
            openSelectedSourceLogin()
        }
        btnDiscoverSourceSearch?.setOnClickListener {
            openDiscoverSearch()
        }
        btnDiscoverEditSource?.setOnClickListener {
            selectedDiscoverSource?.let { editSource(it.bookSourceUrl) }
        }
        btnDiscoverLayoutToggle?.setOnClickListener {
            switchDiscoverBookLayout()
        }
        btnDiscoverModeToggle?.setOnClickListener {
            toggleDiscoveryMode()
        }
        updateDiscoverSearchButtonState()
        updateDiscoverModeToggleButtonState()
    }

    private fun updateDiscoverSourceNameWidth() {
        val rowWidth = modernTitleBar?.width ?: return
        if (rowWidth <= 0) return
        val actionsWidth = listOf(
            btnDiscoverSourceSearch,
            btnDiscoverEditSource,
            btnDiscoverSourceLogin,
            btnDiscoverLayoutToggle,
            btnDiscoverModeToggle
        ).filter { it?.isVisible == true }.sumOf { it?.measuredWidth?.takeIf { width -> width > 0 } ?: (it?.layoutParams?.width ?: 0) }
        val spacing = 36.dpToPx()
        val maxWidth = (rowWidth - actionsWidth - spacing).coerceIn(96.dpToPx(), 190.dpToPx())
        tvDiscoverSourceSelect?.maxWidth = maxWidth
    }

    private fun openSelectedSourceLogin() {
        val source = selectedDiscoverSourcePart ?: return
        if (!source.hasLoginUrl) {
            context?.toastOnUi(R.string.source_no_login)
            return
        }
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    private fun updateDiscoverLoginButtonState() {
        val canLogin = selectedDiscoverSourcePart?.hasLoginUrl == true
        btnDiscoverSourceLogin?.isEnabled = canLogin
        btnDiscoverSourceLogin?.alpha = if (canLogin) 1f else 0.45f
    }

    private fun updateDiscoverSearchButtonState() {
        val canSearch = !selectedDiscoverSource?.searchUrl.isNullOrBlank()
        btnDiscoverSourceSearch?.isVisible = canSearch
        btnDiscoverSourceSearch?.isEnabled = canSearch
        btnDiscoverSourceSearch?.alpha = if (canSearch) 1f else 0.45f
        modernTitleBar?.post(::updateDiscoverSourceNameWidth)
    }

    private fun switchDiscoverBookLayout() {
        AppConfig.modernDiscoveryLayout = (AppConfig.modernDiscoveryLayout + 1) % DISCOVER_LAYOUT_COUNT
        applyDiscoverBookLayout()
    }

    private fun applyDiscoverBookLayout() {
        val style = AppConfig.modernDiscoveryLayout
        discoverBookAdapter.layoutStyle = style
        binding.rvDiscoverBooks.layoutManager = when (style) {
            1 -> GridLayoutManager(requireContext(), 2)
            2 -> GridLayoutManager(requireContext(), 3)
            else -> discoverBookLayoutManager
        }
        discoverBookAdapter.notifyDataSetChanged()
    }

    private fun updateDiscoverModeToggleButtonState() {
        btnDiscoverModeToggle?.contentDescription = if (AppConfig.modernDiscoveryPage) {
            getString(R.string.discovery_page_mode_legacy)
        } else {
            getString(R.string.discovery_page_mode_modern)
        }
    }

    private fun toggleDiscoveryMode() {
        val newMode = !AppConfig.modernDiscoveryPage
        AppConfig.modernDiscoveryPage = newMode
        applyDiscoveryMode(loadData = true)
        postEvent(EventBus.NOTIFY_MAIN, false)
    }

    private fun openDiscoverSearch() {
        val source = selectedDiscoverSource ?: return
        if (source.searchUrl.isNullOrBlank()) {
            context?.toastOnUi(R.string.search_book_key)
            return
        }
        startActivity<SearchActivity> {
            putExtra("searchScope", "${source.bookSourceName}::${source.bookSourceUrl}")
        }
    }

    private fun observeDiscoverSources() {
        if (discoverSourceFlowJob?.isActive == true) return
        discoverSourceFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExplore()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.STARTED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect { list ->
                    discoverSources.clear()
                    discoverSources.addAll(list)
                    if (discoverSources.isEmpty()) {
                        selectedDiscoverSourcePart = null
                        selectedDiscoverSource = null
                        AppConfig.modernDiscoverySourceUrl = null
                        discoverCurrentUrl = null
                        discoverAllTagItems.clear()
                        discoverMajorGroups.clear()
                        selectedDiscoverMajorGroup = null
                        clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
                        renderDiscoverTags(emptyList(), -1)
                        tvDiscoverSourceSelect?.text = getString(R.string.explore_empty)
                        updateDiscoverLoginButtonState()
                        updateDiscoverSearchButtonState()
                        binding.pbDiscoverLoading.gone()
                        return@collect
                    }
                    val keepSource = selectedDiscoverSourcePart?.bookSourceUrl
                        ?: AppConfig.modernDiscoverySourceUrl
                    val selected = discoverSources.firstOrNull { it.bookSourceUrl == keepSource }
                        ?: discoverSources.first()
                    if (selectedDiscoverSourcePart?.bookSourceUrl != selected.bookSourceUrl
                        || discoverTagItems.isEmpty()
                    ) {
                        selectDiscoverSource(selected)
                    } else {
                        updateDiscoverSourceTitle()
                        updateDiscoverLoginButtonState()
                        updateDiscoverSearchButtonState()
                    }
                }
        }
    }

    private fun observeDiscoverBookshelf() {
        if (discoverBookshelfFlowJob?.isActive == true) return
        discoverBookshelfFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowAll()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.STARTED,
                    AppDatabase.BOOK_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect { books ->
                    discoverBookshelf.clear()
                    books.filterNot { it.isNotShelf }
                        .forEach {
                            discoverBookshelf.add("${it.name}-${it.author}")
                            discoverBookshelf.add(it.name)
                            discoverBookshelf.add(it.bookUrl)
                        }
                    if (discoverBookAdapter.itemCount > 0) {
                        discoverBookAdapter.notifyItemRangeChanged(
                            0,
                            discoverBookAdapter.itemCount,
                            bundleOf("isInBookshelf" to null)
                        )
                    }
                }
        }
    }

    private fun showDiscoverSourceMenu() {
        if (discoverSources.isEmpty()) return
        SourceSelectDialog.show(
            context = requireContext(),
            title = getString(R.string.book_source),
            items = discoverSources,
            selectedKey = selectedDiscoverSourcePart?.bookSourceUrl,
            displayName = { it.getDisPlayNameGroup() },
            searchTexts = {
                listOfNotNull(it.bookSourceName, it.bookSourceUrl, it.bookSourceGroup)
            },
            itemKey = { it.bookSourceUrl },
            onSelect = {
                selectDiscoverSource(it)
            },
            showTitle = false
        )
    }

    private fun selectDiscoverSource(source: BookSourcePart) {
        selectedDiscoverSourcePart = source
        AppConfig.modernDiscoverySourceUrl = source.bookSourceUrl
        updateDiscoverLoginButtonState()
        discoverSourceVersion += 1
        val currentSourceVersion = discoverSourceVersion
        discoverRequestVersion += 1
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        binding.pbDiscoverLoading.gone()
        discoverCurrentUrl = null
        discoverBooks.clear()
        discoverBookAdapter.clearItems()
        binding.tvDiscoverEmpty.gone()
        discoverAllTagItems.clear()
        discoverMajorGroups.clear()
        discoverSelectItems.clear()
        selectedDiscoverMajorGroup = null
        renderDiscoverTags(emptyList(), -1)
        renderDiscoverSelects()
        viewLifecycleOwner.lifecycleScope.launch {
            val fullSource = withContext(IO) {
                appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
            }
            if (currentSourceVersion != discoverSourceVersion || !isAdded) {
                return@launch
            }
            selectedDiscoverSource = fullSource
            updateDiscoverSourceTitle()
            updateDiscoverSearchButtonState()
            loadDiscoverKindsAndDefault()
        }
    }

    private fun updateDiscoverSourceTitle() {
        val name = selectedDiscoverSourcePart?.bookSourceName
            ?: getString(R.string.discovery)
        tvDiscoverSourceSelect?.text = name
        modernTitleBar?.post(::updateDiscoverSourceNameWidth)
    }

    private suspend fun loadDiscoverKindsAndDefault() {
        val source = selectedDiscoverSource ?: return
        val kinds = withContext(IO) {
            source.exploreKinds()
        }
        val items = buildDiscoverTagItems(source, kinds)
        discoverAllTagItems.clear()
        discoverAllTagItems.addAll(items)
        if (items.isEmpty()) {
            discoverMajorGroups.clear()
            selectedDiscoverMajorGroup = null
            renderDiscoverTags(emptyList(), -1)
            renderDiscoverSelects()
            clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
            return
        }
        applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
    }

    private fun buildDiscoverTagItems(
        source: BookSource,
        kinds: List<ExploreKind>
    ): List<DiscoverTagItem> {
        val blocked = blockedButtonActions[source.bookSourceUrl]
        val ignoredRows = discoverRowsWithInput(kinds)
        var currentGroup: String? = null
        val result = mutableListOf<DiscoverTagItem>()
        kinds.forEachIndexed { index, kind ->
            if (index in ignoredRows) {
                return@forEachIndexed
            }
            if (isDiscoverMajorGroupKind(kind)) {
                currentGroup = resolveDiscoverGroupTitle(kind)
                return@forEachIndexed
            }
            if (isDiscoverInputKind(kind)) {
                return@forEachIndexed
            }

            val action = kind.action?.takeIf { it.isNotBlank() }
            val url = kind.url?.takeIf { it.isNotBlank() }
            val isSelect = kind.type == ExploreKind.Type.select
            val isButton = kind.type == ExploreKind.Type.button && !action.isNullOrBlank()

            if (isDiscoverSelectGroupKind(kind)) {
                currentGroup = resolveDiscoverGroupTitle(kind)
                result += DiscoverTagItem(
                    kind = kind.copy(type = ExploreKind.Type.select),
                    text = resolveDiscoverTagText(kind).limitDiscoverText(6),
                    isButton = false,
                    group = currentGroup
                )
                return@forEachIndexed
            }

            if (!url.isNullOrBlank() && !isButton && !isSelect) {
                result += DiscoverTagItem(
                    kind = kind.copy(url = url),
                    text = resolveDiscoverTagText(kind).limitDiscoverText(6),
                    isButton = false,
                    group = currentGroup
                )
                return@forEachIndexed
            }

            if (isSelect) {
                result += DiscoverTagItem(
                    kind = kind.copy(type = ExploreKind.Type.select),
                    text = resolveDiscoverTagText(kind).limitDiscoverText(6),
                    isButton = false,
                    group = currentGroup
                )
                return@forEachIndexed
            }

            if (!action.isNullOrBlank()) {
                if (blocked?.contains(action) == true) return@forEachIndexed
                result += DiscoverTagItem(
                    kind = kind.copy(type = ExploreKind.Type.button),
                    text = resolveDiscoverTagText(kind).limitDiscoverText(6),
                    isButton = true,
                    group = currentGroup
                )
            }
        }
        val hasMajorGroup = result.any { !it.group.isNullOrBlank() }
        val normalized = if (hasMajorGroup) {
            result
        } else {
            result.map { it.copy(group = getString(R.string.discover_group_other)) }
        }
        return normalized.distinctBy { "${it.group}|${it.kind.type}|${it.kind.title}|${it.kind.url}|${it.kind.action}" }
    }

    private fun discoverRowsWithInput(kinds: List<ExploreKind>): Set<Int> {
        val ignored = mutableSetOf<Int>()
        var rowStart = 0
        var rowWidth = 0f
        var rowHasInput = false
        kinds.forEachIndexed { index, kind ->
            if (kind.style().layout_wrapBefore && index > rowStart) {
                if (rowHasInput) {
                    for (i in rowStart until index) ignored += i
                }
                rowStart = index
                rowWidth = 0f
                rowHasInput = false
            }
            rowHasInput = rowHasInput || isDiscoverInputKind(kind)
            rowWidth += discoverKindWidth(kind)
            if (rowWidth >= 0.98f) {
                if (rowHasInput) {
                    for (i in rowStart..index) ignored += i
                }
                rowStart = index + 1
                rowWidth = 0f
                rowHasInput = false
            }
        }
        if (rowHasInput && rowStart < kinds.size) {
            for (i in rowStart until kinds.size) ignored += i
        }
        return ignored
    }

    private fun discoverKindWidth(kind: ExploreKind): Float {
        val width = kind.style().layout_flexBasisPercent
        return when {
            width > 0f -> width
            width >= 0.95f -> 1f
            else -> 1f
        }
    }

    private fun isDiscoverMajorGroupKind(kind: ExploreKind): Boolean {
        if (!kind.url.isNullOrBlank()) return false
        val style = kind.style()
        val isFullWidth = style.layout_flexBasisPercent >= 0.95f ||
            (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f)
        if (!isFullWidth) return false
        if (kind.type == ExploreKind.Type.toggle) return true
        return kind.action.isNullOrBlank()
    }

    private fun isDiscoverSelectGroupKind(kind: ExploreKind): Boolean {
        if (kind.type != ExploreKind.Type.select) return false
        if (!kind.url.isNullOrBlank()) return false
        val style = kind.style()
        return style.layout_flexBasisPercent >= 0.95f
    }

    private fun isDiscoverInputKind(kind: ExploreKind): Boolean {
        return kind.type == ExploreKind.Type.text || kind.type == "password"
    }

    private fun resolveDiscoverGroupTitle(kind: ExploreKind): String {
        val raw = resolveDiscoverTagText(kind).trim()
        if (raw.isBlank()) return getString(R.string.discovery)
        val normalized = raw
            .replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return normalized.ifBlank { raw }
    }

    private fun resolveDiscoverTagText(kind: ExploreKind): String {
        val viewName = kind.viewName
        if (!viewName.isNullOrBlank()
            && viewName.length in 3..28
            && viewName.first() == '\''
            && viewName.last() == '\''
        ) {
            return viewName.substring(1, viewName.length - 1)
        }
        return kind.title.ifBlank { kind.type }
    }

    private fun applyDiscoverTagFilterAndSelect(preferredUrl: String?) {
        val hasGroupedItems = discoverAllTagItems.any { !it.group.isNullOrBlank() }
        val groupList = discoverAllTagItems
            .mapNotNull { it.group?.takeIf { name -> name.isNotBlank() } }
            .filter { group ->
                discoverAllTagItems.any { it.group == group && isDiscoverVisibleGroupItem(it) }
            }
            .distinct()
        discoverMajorGroups.clear()
        discoverMajorGroups.addAll(groupList)

        // If no source-defined major groups (only default fallback group), hide the selects bar
        if (!hasGroupedItems) {
            discoverMajorGroups.clear()
        }

        if (discoverMajorGroups.isEmpty()) {
            selectedDiscoverMajorGroup = null
            if (hasGroupedItems) {
                renderDiscoverSelects()
                renderDiscoverTags(emptyList(), -1)
                clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
                return
            }
        } else {
            if (selectedDiscoverMajorGroup !in discoverMajorGroups) {
                selectedDiscoverMajorGroup = discoverMajorGroups.first()
            }
        }

        var filtered = if (discoverMajorGroups.isEmpty()) {
            discoverAllTagItems.toList()
        } else {
            discoverAllTagItems.filter { it.group == selectedDiscoverMajorGroup }
        }
        if (filtered.isEmpty() && discoverMajorGroups.isNotEmpty()) {
            val fallbackGroup = discoverMajorGroups.firstOrNull { group ->
                discoverAllTagItems.any { it.group == group && isDiscoverVisibleGroupItem(it) }
            }
            selectedDiscoverMajorGroup = fallbackGroup
            filtered = if (fallbackGroup.isNullOrBlank()) {
                discoverAllTagItems.toList()
            } else {
                discoverAllTagItems.filter { it.group == fallbackGroup }
            }
        }

        val tagItems = filtered.filter { it.kind.type != ExploreKind.Type.select }
        renderDiscoverSelects()
        val targetIndexByUrl = preferredUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                tagItems.indexOfFirst { !it.isButton && it.kind.url == url }
                    .takeIf { idx -> idx >= 0 }
            }
        val targetIndex = targetIndexByUrl
            ?: tagItems.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        renderDiscoverTags(tagItems, targetIndex)
        if (targetIndex >= 0) {
            selectDiscoverTag(targetIndex, tagItems[targetIndex], selectTab = true)
        } else {
            clearDiscoverBooksToEmpty(getString(R.string.explore_empty))
        }
    }

    private fun isDiscoverVisibleGroupItem(item: DiscoverTagItem): Boolean {
        return item.isButton || !item.kind.url.isNullOrBlank()
    }

    private fun renderDiscoverTags(items: List<DiscoverTagItem>, selectedIndex: Int) {
        discoverTagItems.clear()
        discoverTagItems.addAll(items)
        if (items.isEmpty()) {
            binding.rvDiscoverTags.gone()
            selectedDiscoverTagIndex = -1
            selectedDiscoverUrlIndex = -1
            binding.rvDiscoverTags.submitItems(emptyList(), -1)
            return
        }
        binding.rvDiscoverTags.visible()
        selectedDiscoverTagIndex = selectedIndex.coerceIn(-1, items.lastIndex)
        selectedDiscoverUrlIndex = if (selectedDiscoverTagIndex in items.indices && !items[selectedDiscoverTagIndex].isButton) {
            selectedDiscoverTagIndex
        } else {
            items.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        }
        binding.rvDiscoverTags.submitItems(
            items.map { RoundedTagBarView.Item(it.text, if (it.isButton) 0.9f else 1f) },
            selectedDiscoverTagIndex
        )
    }

    private fun renderDiscoverSelects() {
        if (discoverMajorGroups.isEmpty()) {
            binding.rvDiscoverSelects.gone()
            binding.rvDiscoverSelects.submitItems(emptyList(), -1)
            return
        }
        binding.rvDiscoverSelects.visible()
        val selectedIndex = discoverMajorGroups.indexOf(selectedDiscoverMajorGroup)
        binding.rvDiscoverSelects.submitItems(
            discoverMajorGroups.map { RoundedTagBarView.Item(it, 1f) },
            selectedIndex
        )
    }

    private fun currentDiscoverSelectValue(item: DiscoverTagItem): String {
        val source = selectedDiscoverSource ?: return item.kind.default ?: ""
        val key = item.kind.title
        if (key.isBlank()) return item.kind.default ?: ""
        val info = getDiscoverInfoMap(source.bookSourceUrl)
        return info[key]?.toString()?.takeIf { it.isNotBlank() }
            ?: item.kind.default?.takeIf { it.isNotBlank() }
            ?: item.kind.chars?.firstOrNull()?.orEmpty()
            ?: ""
    }

    private fun showDiscoverSelectDialog(item: DiscoverTagItem) {
        val source = selectedDiscoverSource ?: return
        val key = item.kind.title
        if (key.isBlank()) return
        val options = item.kind.chars?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
        if (options.isEmpty()) return
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        context?.selector(item.text, options) { _, value, _ ->
            infoMap[key] = value
            viewLifecycleOwner.lifecycleScope.launch(IO) {
                source.clearExploreKindsCache()
                val action = item.kind.action?.takeIf { it.isNotBlank() }
                if (!action.isNullOrBlank()) {
                    runScriptWithContext {
                        source.evalJS(action) {
                            put("java", SourceLoginJsExtensions(activity as? AppCompatActivity, source))
                            put("infoMap", infoMap)
                        }
                    }
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadDiscoverKindsAndDefault()
                }
            }
        }
    }

    private fun selectDiscoverTabByCode(index: Int, smooth: Boolean) {
        if (index !in discoverTagItems.indices) return
        binding.rvDiscoverTags.setSelectedIndex(index, smooth)
    }

    private fun selectDiscoverTag(index: Int, item: DiscoverTagItem, selectTab: Boolean) {
        val url = item.kind.url?.takeIf { it.isNotBlank() } ?: return
        selectedDiscoverTagIndex = index
        selectedDiscoverUrlIndex = index
        if (selectTab) {
            selectDiscoverTabByCode(index, smooth = true)
        }
        if (discoverCurrentUrl == url && discoverBooks.isNotEmpty()) {
            return
        }
        discoverCurrentUrl = url
        loadDiscoverBooks(reset = true)
    }

    private fun handleDiscoverButtonTag(item: DiscoverTagItem) {
        val source = selectedDiscoverSource ?: return
        val action = item.kind.action?.takeIf { it.isNotBlank() } ?: return
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        val actionLower = action.lowercase()
        val isNavigationAction = actionLower.contains("showbrowser(")
            || actionLower.contains("open(\"explore\"")
            || actionLower.contains("open('explore'")
        discoverActionJob?.cancel()
        discoverActionJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.pbDiscoverLoading.visible()
            val result = withContext(IO) {
                kotlin.runCatching {
                    var handledByAction = false
                    val java = SourceLoginJsExtensions(
                        activity as? AppCompatActivity,
                        source,
                        callback = object : SourceLoginJsExtensions.Callback {
                            override fun upUiData(data: Map<String, Any?>?) = Unit
                            override fun reUiView(deltaUp: Boolean) = Unit
                            override fun showBrowser(
                                url: String,
                                html: String?,
                                preloadJs: String?,
                                config: String?
                            ): Boolean {
                                return false
                            }

                            override fun open(
                                name: String,
                                url: String?,
                                title: String?,
                                origin: String?
                            ): Boolean {
                                if (!isAdded) return false
                                if (name != "explore") return false
                                handledByAction = true
                                val targetUrl = url?.takeIf { it.isNotBlank() } ?: return true
                                val targetSourceUrl = origin
                                    ?.takeIf { it.isNotBlank() }
                                    ?: selectedDiscoverSource?.bookSourceUrl
                                    ?: source.bookSourceUrl
                                val targetTitle = title ?: item.text
                                binding.root.post {
                                    openExplore(targetSourceUrl, targetTitle, targetUrl)
                                }
                                return true
                            }
                        }
                    )
                    runScriptWithContext {
                        source.evalJS(action) {
                            put("java", java)
                            put("infoMap", infoMap)
                        }
                    }
                    when {
                        handledByAction || isNavigationAction -> null
                        else -> {
                            source.clearExploreKindsCache()
                            source.exploreKinds()
                        }
                    }
                }
            }
            binding.pbDiscoverLoading.gone()
            if (!isAdded) return@launch
            result.onSuccess { kinds ->
                if (kinds == null) {
                    return@onSuccess
                }
                applyDiscoverButtonResult(source, action, kinds)
            }.onFailure {
                AppLog.put("发现标签按钮执行失败", it)
                context?.toastOnUi(it.localizedMessage ?: getString(R.string.unknown_error))
            }
        }
    }

    private fun applyDiscoverButtonResult(
        source: BookSource,
        action: String,
        kinds: List<ExploreKind>
    ) {
        val items = buildDiscoverTagItems(source, kinds)
        val firstUrlIndex = items.indexOfFirst { !it.isButton && !it.kind.url.isNullOrBlank() }
        if (firstUrlIndex >= 0) {
            discoverAllTagItems.clear()
            discoverAllTagItems.addAll(items)
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
            return
        }
        if (items.isNotEmpty()) {
            discoverAllTagItems.clear()
            discoverAllTagItems.addAll(items)
            applyDiscoverTagFilterAndSelect(preferredUrl = discoverCurrentUrl)
        }
        context?.toastOnUi("该按钮未返回可用列表，保留当前标签")
    }

    private fun getDiscoverInfoMap(sourceUrl: String): InfoMap {
        return ExploreAdapter.exploreInfoMapList[sourceUrl] ?: InfoMap(sourceUrl).also {
            ExploreAdapter.exploreInfoMapList.put(sourceUrl, it)
        }
    }

    private fun clearDiscoverBooksToEmpty(message: String) {
        discoverRequestVersion += 1
        discoverLoadJob?.cancel()
        discoverLoadJob = null
        discoverLoading = false
        binding.swipeRefreshLayout.isRefreshing = false
        binding.pbDiscoverLoading.gone()
        discoverCurrentUrl = null
        discoverHasMore = false
        discoverPage = 1
        discoverBooks.clear()
        discoverBookAdapter.clearItems()
        binding.tvDiscoverEmpty.text = message
        binding.tvDiscoverEmpty.visible()
    }

    private fun loadDiscoverBooks(reset: Boolean) {
        if (!usingModernDiscovery) return
        val source = selectedDiscoverSource ?: return
        val url = discoverCurrentUrl?.takeIf { it.isNotBlank() } ?: return
        if (!reset && !discoverHasMore) return
        if (reset) {
            discoverLoadJob?.cancel()
        } else if (discoverLoading) {
            return
        }
        val requestVersion = if (reset) {
            discoverRequestVersion += 1
            discoverRequestVersion
        } else {
            discoverRequestVersion
        }
        discoverLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (reset) {
                discoverPage = 1
                discoverHasMore = true
                discoverBooks.clear()
                discoverBookAdapter.clearItems()
                binding.tvDiscoverEmpty.gone()
            }
            discoverLoading = true
            binding.pbDiscoverLoading.visible()
            try {
                val newBooks = withContext(IO) {
                    WebBook.exploreBookAwait(source, url, discoverPage)
                }
                if (!isAdded || requestVersion != discoverRequestVersion || url != discoverCurrentUrl) {
                    return@launch
                }
                if (newBooks.isEmpty()) {
                    discoverHasMore = false
                    if (discoverBooks.isEmpty()) {
                        binding.tvDiscoverEmpty.text = getString(R.string.explore_empty)
                        binding.tvDiscoverEmpty.visible()
                    }
                } else {
                    withContext(IO) {
                        appDb.searchBookDao.insert(*newBooks.toTypedArray())
                    }
                    discoverPage += 1
                    discoverBooks.addAll(newBooks)
                    discoverBookAdapter.setItems(discoverBooks.toList())
                    binding.tvDiscoverEmpty.gone()
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                if (!isAdded || requestVersion != discoverRequestVersion || url != discoverCurrentUrl) {
                    return@launch
                }
                AppLog.put("新版发现页加载失败", e)
                if (discoverBooks.isEmpty()) {
                    binding.tvDiscoverEmpty.text = e.localizedMessage ?: getString(R.string.unknown_error)
                    binding.tvDiscoverEmpty.visible()
                }
            } finally {
                if (isAdded && requestVersion == discoverRequestVersion && url == discoverCurrentUrl) {
                    binding.pbDiscoverLoading.gone()
                    binding.swipeRefreshLayout.isRefreshing = false
                    discoverLoading = false
                }
            }
        }
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                binding.swipeRefreshLayout.isRefreshing = false
                binding.tvEmptyMsg.isGone = it.isNotEmpty() || (searchView?.query?.isNotEmpty() == true)
                adapter.setItems(it, diffItemCallBack)
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (usingModernDiscovery != AppConfig.modernDiscoveryPage || !discoveryModeLoaded) {
            applyDiscoveryMode(loadData = true)
            discoveryModeLoaded = true
        }
        // Refresh title bar theme colors
        if (usingModernDiscovery && modernModeInitialized) {
            applyModernTitleBarTheme()
        }
        if (!usingModernDiscovery) {
            adapter.upResumed(true)
        }
    }

    override fun onPause() {
        if (!usingModernDiscovery) {
            adapter.upResumed(false)
            searchView?.clearFocus()
            adapter.onPause()
        }
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时，重新应用发现页模式来更新 UI
        applyDiscoveryMode(loadData = false)
        // 更新标题栏主题
        if (usingModernDiscovery && modernModeInitialized) {
            applyModernTitleBarTheme()
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.THEME_CHANGED) {
            // 主题变化时，重新应用发现页模式来更新 UI
            applyDiscoveryMode(loadData = false)
            // 更新标题栏主题
            if (usingModernDiscovery && modernModeInitialized) {
                applyModernTitleBarTheme()
            }
        }
    }

    override fun onDestroyView() {
        stopModernMode()
        oldModeInitialized = false
        modernModeInitialized = false
        groupsMenu = null
        super.onDestroyView()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_discovery_mode -> {
                // Toggle discovery mode
                val newMode = !AppConfig.modernDiscoveryPage
                AppConfig.modernDiscoveryPage = newMode
                applyDiscoveryMode(loadData = true)
                // Notify main activity to update UI if needed
                postEvent(EventBus.NOTIFY_MAIN, false)
                return
            }
        }
        if (usingModernDiscovery) return
        if (item.groupId == R.id.menu_group_text) {
            searchView?.setQuery("group:${item.title}", true) ?: upExploreData("group:${item.title}")
        }
    }

    override fun scrollTo(pos: Int) {
        (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
        return discoverBookshelf.contains(key) || discoverBookshelf.contains(book.bookUrl)
    }

    override fun showBookInfo(book: SearchBook) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(IO) {
                appDb.searchBookDao.insert(book)
            }
            val isVideo = withContext(IO) {
                SearchBookOpenHelper.isVideoResult(
                    book,
                    selectedDiscoverSourcePart?.bookSourceType ?: selectedDiscoverSource?.bookSourceType
                )
            }
            SearchBookOpenHelper.open(requireContext(), book, isVideo)
        }
    }

    override fun onBookLongClick(book: SearchBook) {
        val shelfState = if (viewModel.isInBookShelf(book)) {
            BookShelfState.IN_SHELF
        } else {
            BookShelfState.NOT_IN_SHELF
        }
        var dialog: Dialog? = null
        val composeView = ComposeView(requireContext()).apply {
            setViewTreeLifecycleOwner(viewLifecycleOwner)
            this.setViewTreeSavedStateRegistryOwner(this@ExploreFragment)
            setContent {
                SearchBookPreviewSheet(
                    data = book,
                    shelfState = shelfState,
                    onDismissRequest = { dialog?.dismiss() },
                    onOpenDetail = { b ->
                        showBookInfo(b)
                        dialog?.dismiss()
                    },
                    onAddToShelf = { b ->
                        viewModel.addToBookshelf(b)
                        dialog?.dismiss()
                    }
                )
            }
        }
        dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(composeView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setOnShowListener {
            }
            show()
        }
    }

    fun compressExplore() {
        if (usingModernDiscovery) {
            if (binding.rvDiscoverBooks.canScrollVertically(-1)) {
                if (AppConfig.isEInkMode) {
                    binding.rvDiscoverBooks.scrollToPosition(0)
                } else {
                    binding.rvDiscoverBooks.smoothScrollToPosition(0)
                }
            }
            return
        }
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) {
                binding.rvFind.scrollToPosition(0)
            } else {
                binding.rvFind.smoothScrollToPosition(0)
            }
        }
    }

    /**
     * 应用现代发现页标题栏主题颜色
     */
    private fun applyModernTitleBarTheme() {
        val textColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(requireContext())
        
        android.util.Log.d("ExploreFragment", "applyModernTitleBarTheme: textColor=$textColor")
        
        // 设置标题文字颜色
        tvDiscoverSourceSelect?.let {
            android.util.Log.d("ExploreFragment", "tvDiscoverSourceSelect found, applying color")
            it.setTextColor(textColor)
        } ?: android.util.Log.w("ExploreFragment", "tvDiscoverSourceSelect is null!")
        
        // 设置图标颜色
        llDiscoverSourceSelect?.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iv_discover_source_arrow)?.let {
            android.util.Log.d("ExploreFragment", "iv_discover_source_arrow found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "iv_discover_source_arrow is null!")
        
        btnDiscoverSourceSearch?.let {
            android.util.Log.d("ExploreFragment", "btnDiscoverSourceSearch found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "btnDiscoverSourceSearch is null!")
        
        btnDiscoverEditSource?.let {
            android.util.Log.d("ExploreFragment", "btnDiscoverEditSource found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "btnDiscoverEditSource is null!")
        
        btnDiscoverSourceLogin?.let {
            android.util.Log.d("ExploreFragment", "btnDiscoverSourceLogin found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "btnDiscoverSourceLogin is null!")
        
        btnDiscoverLayoutToggle?.let {
            android.util.Log.d("ExploreFragment", "btnDiscoverLayoutToggle found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "btnDiscoverLayoutToggle is null!")
        
        btnDiscoverModeToggle?.let {
            android.util.Log.d("ExploreFragment", "btnDiscoverModeToggle found, applying color")
            it.setColorFilter(textColor)
        } ?: android.util.Log.w("ExploreFragment", "btnDiscoverModeToggle is null!")
    }

    /**
     * 显示发现分类完整对话框（长按书源名称触发）
     */
    private fun showDiscoverKindsDialog() {
        val context = context ?: return
        val source = selectedDiscoverSource ?: return
        var dialog: AlertDialog? = null
        val screenSize = requireActivity().windowManager.windowSize
        val dialogWidth = (screenSize.widthPixels * DISCOVER_DIALOG_WIDTH_RATIO).toInt()
        val dialogHeight = (screenSize.heightPixels * DISCOVER_DIALOG_HEIGHT_RATIO).toInt()
        val itemBinding = ItemFindBookBinding.inflate(layoutInflater, null, false)
        val flexbox = itemBinding.flexbox
        val scrollView = NestedScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFillViewport = true
            addView(
                itemBinding.root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val refreshLayout = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(accentColor)
            setOnChildScrollUpCallback { _, _ ->
                scrollView.canScrollVertically(-1)
            }
            setOnRefreshListener {
                refreshDiscoverKindsDialog(itemBinding, source, dialog, this, clearCache = true)
            }
            addView(
                scrollView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        val dialogContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(dialogWidth, dialogHeight)
            background = UiCorner.opaqueRounded(
                ContextCompat.getColor(context, R.color.background_card),
                UiCorner.panelRadius(context)
            )
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 10.dpToPx())
            addView(
                refreshLayout,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        itemBinding.apply {
            root.setPadding(0, 0, 0, 0)
            root.background = null
            llTitle.gone()
            flexbox.visible()
            rotateLoading.visible()
        }
        dialog = AlertDialog.Builder(context)
            .setView(dialogContent)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.45f)
        dialog.window?.setLayout(dialogWidth, dialogHeight)
        refreshDiscoverKindsDialog(itemBinding, source, dialog, refreshLayout, clearCache = false)
    }

    private fun refreshDiscoverKindsDialog(
        itemBinding: ItemFindBookBinding,
        source: BookSource,
        dialog: AlertDialog?,
        refreshLayout: SwipeRefreshLayout?,
        clearCache: Boolean
    ) {
        itemBinding.rotateLoading.visible()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(IO) {
                    if (clearCache) {
                        source.clearExploreKindsCache()
                    }
                    source.exploreKinds()
                }
            }
            if (!isAdded || dialog?.isShowing != true) return@launch
            refreshLayout?.isRefreshing = false
            itemBinding.rotateLoading.gone()
            result.onSuccess { kinds ->
                if (kinds.isEmpty()) {
                    context?.toastOnUi(R.string.explore_empty)
                    return@onSuccess
                }
                renderDiscoverDialogKinds(itemBinding, source, kinds, dialog)
            }.onFailure {
                AppLog.put("完整发现刷新失败", it)
                context?.toastOnUi(it.localizedMessage ?: getString(R.string.unknown_error))
            }
        }
    }

    private fun renderDiscoverDialogKinds(
        itemBinding: ItemFindBookBinding,
        source: BookSource,
        kinds: List<ExploreKind>,
        dialog: AlertDialog?
    ) {
        val flexbox = itemBinding.flexbox
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        val java = SourceLoginJsExtensions(
            activity as? AppCompatActivity,
            source,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit
                override fun reUiView(deltaUp: Boolean) {
                    refreshDiscoverKindsDialog(itemBinding, source, dialog, null, clearCache = true)
                }
            }
        )
        flexbox.removeAllViews()
        kinds.forEach { kind ->
            when (kind.type) {
                ExploreKind.Type.url -> renderDiscoverDialogUrl(flexbox, source, kind, dialog, infoMap)
                ExploreKind.Type.button -> renderDiscoverDialogButton(flexbox, source, kind, infoMap, java)
                ExploreKind.Type.toggle -> renderDiscoverDialogToggle(flexbox, source, kind, infoMap, java)
                ExploreKind.Type.select -> renderDiscoverDialogSelect(flexbox, source, kind, infoMap, java)
                ExploreKind.Type.text -> renderDiscoverDialogTextInput(flexbox, source, kind, infoMap, java)
            }
        }
    }

    private fun renderDiscoverDialogUrl(
        flexbox: FlexboxLayout,
        source: BookSource,
        kind: ExploreKind,
        dialog: AlertDialog?,
        infoMap: InfoMap
    ) {
        val tv = createDiscoverDialogTextView(flexbox)
        flexbox.addView(tv)
        applyDiscoverKindTextStyle(tv, kind)
        bindDiscoverDialogKindText(tv, kind, source, infoMap)
        tv.setOnClickListener {
            val url = kind.normalizedDiscoverUrl()?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
            dialog?.dismiss()
            openDiscoverDialogTagInCurrentPage(source, kind, url)
        }
    }

    private fun renderDiscoverDialogButton(
        flexbox: FlexboxLayout,
        source: BookSource,
        kind: ExploreKind,
        infoMap: InfoMap,
        java: SourceLoginJsExtensions
    ) {
        val tv = createDiscoverDialogTextView(flexbox)
        flexbox.addView(tv)
        applyDiscoverKindTextStyle(tv, kind)
        bindDiscoverDialogKindText(tv, kind, source, infoMap)
        tv.setOnClickListener {
            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(IO) {
                evalDiscoverDialogButtonClick(action, source, infoMap, kind.title, java)
            }
        }
    }

    private fun renderDiscoverDialogToggle(
        flexbox: FlexboxLayout,
        source: BookSource,
        kind: ExploreKind,
        infoMap: InfoMap,
        java: SourceLoginJsExtensions
    ) {
        val tv = createDiscoverDialogTextView(flexbox)
        flexbox.addView(tv)
        var left = true
        kind.style().apply {
            when (layout_justifySelf) {
                "flex_start" -> tv.gravity = Gravity.START
                "flex_end" -> tv.gravity = Gravity.END
                "right" -> left = false
                else -> tv.gravity = Gravity.CENTER
            }
            apply(tv)
        }
        val chars = kind.chars?.filterNotNull() ?: listOf("chars", "is null")
        var newName = kind.title
        var char = infoMap[kind.title] ?: (kind.default ?: chars.firstOrNull().orEmpty()).also {
            infoMap[kind.title] = it
        }
        fun updateText() {
            tv.text = if (left) char + newName else newName + char
        }
        val viewName = kind.viewName
        if (viewName != null && viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
            newName = viewName.substring(1, viewName.length - 1)
        } else if (!viewName.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                val name = withContext(IO) { evalDiscoverDialogUiJs(viewName, source, infoMap) }
                if (!name.isNullOrBlank()) {
                    newName = name
                    updateText()
                }
            }
        }
        updateText()
        tv.setOnClickListener {
            val currentIndex = chars.indexOf(char)
            char = chars.getOrNull(currentIndex + 1) ?: chars.firstOrNull().orEmpty()
            infoMap[kind.title] = char
            updateText()
            val action = kind.action?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch(IO) {
                evalDiscoverDialogButtonClick(action, source, infoMap, kind.title, java)
            }
        }
    }

    private fun renderDiscoverDialogSelect(
        flexbox: FlexboxLayout,
        source: BookSource,
        kind: ExploreKind,
        infoMap: InfoMap,
        java: SourceLoginJsExtensions
    ) {
        val binding = ItemFilletSelectorSingleBinding.inflate(layoutInflater, flexbox, false)
        val root = binding.root
        flexbox.addView(root)
        kind.style().apply {
            when (layout_justifySelf) {
                "flex_start" -> root.gravity = Gravity.START
                "flex_end" -> root.gravity = Gravity.END
                else -> root.gravity = Gravity.CENTER
            }
            apply(root)
        }
        bindDiscoverDialogKindText(binding.spName, kind, source, infoMap)
        val chars = kind.chars?.filterNotNull() ?: listOf("chars", "is null")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_text_common, chars)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spType.adapter = adapter
        val char = infoMap[kind.title] ?: (kind.default ?: chars.firstOrNull().orEmpty()).also {
            infoMap[kind.title] = it
        }
        binding.spType.setSelectionSafely(chars.indexOf(char))
        binding.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var initializing = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initializing) {
                    initializing = false
                    return
                }
                if (position !in chars.indices) return
                infoMap[kind.title] = chars[position]
                val action = kind.action?.takeIf { it.isNotBlank() } ?: return
                viewLifecycleOwner.lifecycleScope.launch(IO) {
                    evalDiscoverDialogButtonClick(action, source, infoMap, kind.title, java)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderDiscoverDialogTextInput(
        flexbox: FlexboxLayout,
        source: BookSource,
        kind: ExploreKind,
        infoMap: InfoMap,
        java: SourceLoginJsExtensions
    ) {
        val input = ItemFilletCompleteTextBinding.inflate(layoutInflater, flexbox, false).root
        flexbox.addView(input)
        kind.style().apply {
            when (layout_justifySelf) {
                "center" -> input.gravity = Gravity.CENTER
                "flex_end" -> input.gravity = Gravity.END
                else -> input.gravity = Gravity.START
            }
            apply(input)
        }
        bindDiscoverDialogKindHint(input, kind, source, infoMap)
        input.setText(infoMap[kind.title])
        var actionJob: Job? = null
        input.addTextChangedListener(object : TextWatcher {
            var content: String? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                content = s?.toString()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString().orEmpty()
                infoMap[kind.title] = value
                val action = kind.action?.takeIf { it.isNotBlank() } ?: return
                if (value == content) return
                actionJob?.cancel()
                actionJob = viewLifecycleOwner.lifecycleScope.launch(IO) {
                    delay(600)
                    evalDiscoverDialogButtonClick(action, source, infoMap, kind.title, java)
                    content = value
                }
            }
        })
    }

    private fun createDiscoverDialogTextView(flexbox: FlexboxLayout): TextView {
        return ItemFilletTextBinding.inflate(layoutInflater, flexbox, false).root
    }

    private fun applyDiscoverKindTextStyle(tv: TextView, kind: ExploreKind) {
        kind.style().apply {
            when (layout_justifySelf) {
                "flex_start" -> tv.gravity = Gravity.START
                "flex_end" -> tv.gravity = Gravity.END
                else -> tv.gravity = Gravity.CENTER
            }
            apply(tv)
        }
    }

    private fun bindDiscoverDialogKindText(
        tv: TextView,
        kind: ExploreKind,
        source: BookSource,
        infoMap: InfoMap
    ) {
        val viewName = kind.viewName
        if (viewName == null) {
            tv.text = kind.title
        } else if (viewName.length in 3..28 && viewName.first() == '\'' && viewName.last() == '\'') {
            tv.text = viewName.substring(1, viewName.length - 1)
        } else {
            tv.text = kind.title
            viewLifecycleOwner.lifecycleScope.launch {
                val name = withContext(IO) { evalDiscoverDialogUiJs(viewName, source, infoMap) }
                tv.text = name ?: "err"
            }
        }
    }

    private fun bindDiscoverDialogKindHint(
        input: AutoCompleteTextView,
        kind: ExploreKind,
        source: BookSource,
        infoMap: InfoMap
    ) {
        val viewName = kind.viewName
        if (viewName == null) {
            input.hint = kind.title
        } else if (viewName.length in 3..28 && viewName.first() == '\'' && viewName.last() == '\'') {
            input.hint = viewName.substring(1, viewName.length - 1)
        } else {
            input.hint = kind.title
            viewLifecycleOwner.lifecycleScope.launch {
                val name = withContext(IO) { evalDiscoverDialogUiJs(viewName, source, infoMap) }
                input.hint = name ?: "err"
            }
        }
    }

    private fun openDiscoverDialogTagInCurrentPage(
        source: BookSource,
        kind: ExploreKind,
        url: String
    ) {
        if (selectedDiscoverSource?.bookSourceUrl != source.bookSourceUrl) {
            val sourcePart = discoverSources.firstOrNull { it.bookSourceUrl == source.bookSourceUrl }
            if (sourcePart != null) {
                selectDiscoverSource(sourcePart)
            }
        }
        val index = discoverTagItems.indexOfFirst { !it.isButton && it.kind.url == url }
        if (index >= 0) {
            val item = discoverTagItems[index]
            selectDiscoverTag(index, item, selectTab = true)
        } else {
            discoverCurrentUrl = url
            binding.rvDiscoverTags.setSelectedIndex(-1, smooth = false)
            loadDiscoverBooks(reset = true)
        }
    }

    private suspend fun evalDiscoverDialogUiJs(
        jsStr: String,
        source: BookSource,
        infoMap: InfoMap
    ): String? {
        return try {
            runScriptWithContext {
                source.evalJS(jsStr) {
                    put("infoMap", infoMap)
                }.toString()
            }
        } catch (e: Throwable) {
            AppLog.put(source.bookSourceUrl + " exploreUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    private suspend fun evalDiscoverDialogButtonClick(
        jsStr: String,
        source: BookSource,
        infoMap: InfoMap,
        name: String,
        java: SourceLoginJsExtensions
    ) {
        try {
            runScriptWithContext {
                source.evalJS(normalizeDiscoverActionScript(jsStr)) {
                    put("java", java)
                    put("infoMap", infoMap)
                }
            }
        } catch (e: Throwable) {
            AppLog.put("ExploreUI Button $name JavaScript error", e)
        }
    }

    private fun normalizeDiscoverActionScript(action: String): String {
        var value = action.trim()
        if (value.startsWith("@js:", ignoreCase = true)) {
            return value.substring(4).trim()
        }
        if (value.startsWith("<js>", ignoreCase = true) && value.endsWith("</js>", ignoreCase = true)) {
            return value.substring(4, value.length - 5).trim()
        }
        value = when {
            value.startsWith("{\\{") && value.endsWith("}}") -> value.substring(3, value.length - 2)
            value.startsWith("{{") && value.endsWith("}}") -> value.substring(2, value.length - 2)
            value.startsWith("{") && value.endsWith("}") && isDiscoverExecutableObjectScript(value) ->
                value.substring(1, value.length - 1)
            else -> value
        }
        return value.trim()
    }

    private fun isDiscoverExecutableObjectScript(value: String): Boolean {
        return value.contains("java.", ignoreCase = true) ||
            value.contains("source.", ignoreCase = true) ||
            value.contains("infoMap", ignoreCase = true)
    }

    private fun resolveDiscoverToggleText(kind: ExploreKind, value: String): String {
        val label = resolveDiscoverTagText(kind)
        return when {
            label.isBlank() -> value
            value.isBlank() -> label
            else -> "$value$label"
        }
    }

    private fun resolveDiscoverControlText(kind: ExploreKind): String {
        if (kind.type != ExploreKind.Type.toggle) {
            return resolveDiscoverTagText(kind)
        }
        val source = selectedDiscoverSource ?: return resolveDiscoverTagText(kind)
        val key = kind.title
        val value = if (key.isBlank()) {
            kind.default ?: kind.chars?.firstOrNull().orEmpty()
        } else {
            val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
            infoMap[key] ?: (kind.default ?: kind.chars?.firstOrNull().orEmpty()).also {
                infoMap[key] = it
            }
        }
        return resolveDiscoverToggleText(kind, value)
    }

    private fun handleDiscoverToggleTag(index: Int, item: DiscoverTagItem) {
        val source = selectedDiscoverSource ?: return
        val key = item.kind.title
        if (key.isBlank()) return
        val chars = item.kind.chars?.filterNotNull() ?: return
        if (chars.isEmpty()) return
        val infoMap = getDiscoverInfoMap(source.bookSourceUrl)
        val current = infoMap[key] ?: item.kind.default ?: chars.first()
        val currentIndex = chars.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = chars.getOrNull(currentIndex + 1) ?: chars.first()
        infoMap[key] = next
        val updated = item.copy(text = resolveDiscoverToggleText(item.kind, next).limitDiscoverText(8))
        if (index in discoverTagItems.indices) {
            discoverTagItems[index] = updated
            binding.rvDiscoverTags.submitItems(
                discoverTagItems.map { RoundedTagBarView.Item(it.text, if (it.isButton) 0.9f else 1f) },
                selectedDiscoverTagIndex
            )
        }
        handleDiscoverButtonTag(updated)
    }

    private fun ExploreKind.normalizedDiscoverUrl(): String? {
        return url?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }
    }

    private companion object {
        private const val DISCOVER_DIALOG_WIDTH_RATIO = 0.90f
        private const val DISCOVER_DIALOG_HEIGHT_RATIO = 0.72f
        const val DISCOVER_LAYOUT_COUNT = 3
    }

}

private fun String.limitDiscoverText(max: Int): String {
    return if (length <= max) this else "${take(max.coerceAtLeast(2) - 1)}…"
}
