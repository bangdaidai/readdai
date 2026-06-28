package io.legado.app.ui.book.changesource

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.DialogBookChangeSourceBinding
import io.legado.app.help.book.isWebFile
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatDrawable
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 换源界面
 */
class ChangeBookSourceDialog() : BaseDialogFragment(R.layout.dialog_book_change_source),
    Toolbar.OnMenuItemClickListener,
    ChangeBookSourceAdapter.CallBack {

    constructor(name: String, author: String, bookType: Int = 0) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
            putInt("bookType", bookType)
        }
    }

    private val binding by viewBinding(DialogBookChangeSourceBinding::bind)
    private val groups = linkedSetOf<String>()
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeBookSourceViewModel by viewModels()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val adapter by lazy { ChangeBookSourceAdapter(requireContext(), viewModel, this) }
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            val origin = it.data?.getStringExtra("origin") ?: return@registerForActivityResult
            viewModel.startSearch(origin)
        }
    private val pickLocalFileResult =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            importLocalFile(uri)
        }
    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = {
        if (it) {
            val searchGroup = AppConfig.searchGroup
            if (searchGroup.isNotEmpty()) {
                lifecycleScope.launch {
                    context?.alert("搜索结果为空") {
                        setMessage("${searchGroup}分组搜索结果为空,是否切换到全部分组")
                        cancelButton()
                        okButton {
                            AppConfig.searchGroup = ""
                            upGroupMenuName()
                            viewModel.startSearch()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f) // 保留高度设置
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        // 设置标题栏文字颜色为主题自定义的标题栏文字图标颜色
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.setTitleTextColor(titleBarTextIconColor)
        binding.toolBar.setSubtitleTextColor(titleBarTextIconColor)
        // 为对话框内容区域设置背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        // 为 RecyclerView 设置背景色，确保没有书源的地方也显示主题背景色
        binding.recyclerView.setBackgroundColor(backgroundColor)
        // 为底部栏设置背景色
        binding.llBottomBar.setBackgroundColor(backgroundColor)
        // 为刷新进度条设置背景色
        binding.refreshProgressBar.setBackgroundColor(backgroundColor)
        viewModel.initData(arguments, callBack?.oldBook, activity is ReadBookActivity)
        showTitle()
        initMenu()
        initRecyclerView()
        initNavigationView()
        initSearchView()
        initBottomBar()
        initLiveData()
        viewModel.searchFinishCallback = searchFinishCallback
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.searchFinishCallback = null
    }

    private fun showTitle() {
        binding.toolBar.title = viewModel.name
        binding.toolBar.subtitle = viewModel.author
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.navigationIcon =
            getCompatDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)?.apply { setTint(titleBarTextIconColor) }
        binding.toolBar.elevation = requireContext().elevation
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.change_source)
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.menu.findItem(R.id.menu_check_author)
            ?.isChecked = AppConfig.changeSourceCheckAuthor
        binding.toolBar.menu.findItem(R.id.menu_load_info)
            ?.isChecked = AppConfig.changeSourceLoadInfo
        binding.toolBar.menu.findItem(R.id.menu_load_toc)
            ?.isChecked = AppConfig.changeSourceLoadToc
        binding.toolBar.menu.findItem(R.id.menu_load_word_count)
            ?.isChecked = AppConfig.changeSourceLoadWordCount
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                if (toPosition == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })
    }

    private fun initSearchView() {
        val searchView = binding.toolBar.menu.findItem(R.id.menu_screen).actionView as SearchView
        // 设置搜索图标的颜色为主题自定义的标题栏文字图标颜色
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        searchView.applyTint(titleBarTextIconColor)
        searchView.setOnCloseListener {
            showTitle()
            false
        }
        searchView.setOnSearchClickListener {
            binding.toolBar.title = ""
            binding.toolBar.subtitle = ""
            binding.toolBar.navigationIcon = null
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.screen(newText)
                return false
            }

        })
    }

    private fun initNavigationView() {
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.navigationIcon =
            getCompatDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)?.apply { setTint(titleBarTextIconColor) }
        binding.toolBar.setNavigationContentDescription(
            androidx.appcompat.R.string.abc_action_bar_up_description
        )
        binding.toolBar.setNavigationOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initBottomBar() {
        binding.tvDur.text = callBack?.oldBook?.originName
        binding.tvDur.setOnClickListener {
            scrollToDurSource()
        }
        binding.ivTop.setOnClickListener {
            binding.recyclerView.scrollToPosition(0)
        }
        binding.ivBottom.setOnClickListener {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun initLiveData() {
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            binding.refreshProgressBar.isAutoLoading = it
            if (it) {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_stop_black_24dp)
                    item.icon?.setTint(titleBarTextIconColor)
                    item.setTitle(R.string.stop)
                }
            } else {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_refresh_black_24dp)
                    item.icon?.setTint(titleBarTextIconColor)
                    item.setTitle(R.string.refresh)
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.currentStateFlow.first { it.isAtLeast(STARTED) }
            viewModel.searchDataFlow.conflate().collect {
                adapter.setItems(it)
                delay(1000)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.changeSourceProgress
                    .drop(1)
                    .collect { (count, name) ->
                        binding.tvDur.text =
                            getString(
                                R.string.change_source_progress,
                                adapter.itemCount,
                                count,
                                viewModel.totalSourceCount,
                                name
                            )
                        delay(500)
                    }
            }
        }

        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    private val startStopMenuItem: MenuItem?
        get() = binding.toolBar.menu.findItem(R.id.menu_start_stop)

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_check_author -> {
                AppConfig.changeSourceCheckAuthor = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.refresh()
            }

            R.id.menu_load_info -> {
                AppConfig.changeSourceLoadInfo = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_toc -> {
                AppConfig.changeSourceLoadToc = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_word_count -> {
                AppConfig.changeSourceLoadWordCount = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.onLoadWordCountChecked(item.isChecked)
            }

            R.id.menu_start_stop -> viewModel.startOrStopSearch()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            R.id.menu_add_local -> pickLocalFile()
            R.id.menu_close -> dismissAllowingStateLoss()
            R.id.menu_refresh_list -> viewModel.startRefreshList()
            else -> if (item?.groupId == R.id.source_group && !item.isChecked) {
                item.isChecked = true
                if (item.title.toString() == getString(R.string.all_source)) {
                    AppConfig.searchGroup = ""
                } else {
                    AppConfig.searchGroup = item.title.toString()
                }
                upGroupMenuName()
                lifecycleScope.launch(IO) {
                    viewModel.stopSearch()
                    if (viewModel.refresh()) {
                        viewModel.startSearch()
                    }
                }
            }
        }
        return false
    }

    private fun scrollToDurSource() {
        adapter.getItems().forEachIndexed { index, searchBook ->
            if (searchBook.bookUrl == oldBookUrl) {
                (binding.recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(index, 60.dpToPx())
                return
            }
        }
    }

    override fun changeTo(searchBook: SearchBook) {
        val oldBookType = callBack?.oldBook?.type ?: 0
        if (searchBook.sameBookTypeLocal(oldBookType)) {
            changeSource(searchBook) {
                dismissAllowingStateLoss()
            }
        } else {
            alert(
                titleResource = R.string.book_type_different,
                messageResource = R.string.soure_change_source
            ) {
                okButton {
                    changeSource(searchBook) {
                        dismissAllowingStateLoss()
                    }
                }
                cancelButton()
            }
        }
    }

    override val oldBookUrl: String?
        get() = callBack?.oldBook?.bookUrl

    override fun topSource(searchBook: SearchBook) {
        viewModel.topSource(searchBook)
    }

    override fun bottomSource(searchBook: SearchBook) {
        viewModel.bottomSource(searchBook)
    }

    override fun editSource(searchBook: SearchBook) {
        editSourceResult.launch {
            putExtra("sourceUrl", searchBook.origin)
        }
    }

    override fun disableSource(searchBook: SearchBook) {
        viewModel.disableSource(searchBook)
    }

    override fun deleteSource(searchBook: SearchBook) {
        viewModel.del(searchBook)
        if (oldBookUrl == searchBook.bookUrl) {
            viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                callBack?.changeTo(source, book, toc)
            }
        }
    }

    override fun setBookScore(searchBook: SearchBook, score: Int) {
        viewModel.setBookScore(searchBook, score)
    }

    override fun getBookScore(searchBook: SearchBook): Int {
        return viewModel.getBookScore(searchBook)
    }

    private fun changeSource(searchBook: SearchBook, onSuccess: (() -> Unit)? = null) {
        waitDialog.setText(R.string.load_toc)
        waitDialog.show()
        val book = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        if (book.isWebFile) { //文件类书源不解析目录
            val source = appDb.bookSourceDao.getBookSource(book.origin)
            if (source == null) {
                AppLog.put("书源不存在", null, true)
                return
            }
            waitDialog.dismiss()
            callBack?.changeTo(source, book, emptyList())
            onSuccess?.invoke()
            return
        }
        val coroutine = viewModel.getToc(book, { toc, source ->
            waitDialog.dismiss()
            callBack?.changeTo(source, book, toc)
            onSuccess?.invoke()
        }, {
            waitDialog.dismiss()
            AppLog.put("换源获取目录出错\n$it", it, true)
        })
        waitDialog.setOnCancelListener {
            coroutine.cancel()
        }
    }

    /**
     * 更新分组菜单
     */
    private fun upGroupMenu() {
        binding.toolBar.menu.findItem(R.id.menu_group)?.run {
            subMenu?.transaction { menu ->
                val selectedGroup = AppConfig.searchGroup
                menu.removeGroup(R.id.source_group)
                val allItem = menu.add(R.id.source_group, Menu.NONE, Menu.NONE, R.string.all_source)
                var hasSelectedGroup = false
                groups.forEach { group ->
                    menu.add(R.id.source_group, Menu.NONE, Menu.NONE, group)?.let {
                        if (group == selectedGroup) {
                            it.isChecked = true
                            hasSelectedGroup = true
                        }
                    }
                }
                menu.setGroupCheckable(R.id.source_group, true, true)
                if (hasSelectedGroup) {
                    title = getString(R.string.group) + "(" + AppConfig.searchGroup + ")"
                } else {
                    allItem.isChecked = true
                    title = getString(R.string.group)
                }
            }
        }
    }

    /**
     * 更新分组菜单名
     */
    private fun upGroupMenuName() {
        val menuGroup = binding.toolBar.menu.findItem(R.id.menu_group)
        val searchGroup = AppConfig.searchGroup
        if (searchGroup.isEmpty()) {
            menuGroup?.title = getString(R.string.group)
        } else {
            menuGroup?.title = getString(R.string.group) + "($searchGroup)"
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("upCurSource", oldBookUrl))
            )
        }
    }

    private fun pickLocalFile() {
        pickLocalFileResult.launch(arrayOf("*/*"))
    }

    private fun importLocalFile(uri: Uri) {
        waitDialog.setText(R.string.loading)
        waitDialog.show()
        lifecycleScope.launch {
            try {
                val localBook = withContext(IO) {
                    LocalBook.importFile(uri)
                }
                val toc = withContext(IO) {
                    LocalBook.getChapterList(localBook)
                }
                waitDialog.dismiss()
                callBack?.changeToLocal(localBook, toc)
                dismissAllowingStateLoss()
            } catch (e: Exception) {
                waitDialog.dismiss()
                AppLog.put("导入本地文件出错\n${e.localizedMessage}", e)
                context?.toastOnUi("导入本地文件出错\n${e.localizedMessage}")
            }
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
        fun changeToLocal(book: Book, toc: List<BookChapter>)
    }

}