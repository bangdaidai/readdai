package io.legado.app.ui.book.readingmemory

import android.graphics.PorterDuff
import android.os.Bundle
import io.legado.app.utils.ColorUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityReadingMemoryBinding
import io.legado.app.constant.Theme
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.data.entities.Book
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.dialogs.alert

import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import io.legado.app.data.appDb
import io.legado.app.utils.toastOnUi
import io.legado.app.constant.EventBus
import io.legado.app.utils.observeEvent

class ReadingMemoryActivity : VMBaseActivity<ActivityReadingMemoryBinding, ReadingMemoryViewModel>(
    theme = Theme.Auto,
    toolBarTheme = Theme.Auto,
    transparent = true,
    imageBg = true
) {

    override val binding by viewBinding(ActivityReadingMemoryBinding::inflate)
    override val viewModel by viewModels<ReadingMemoryViewModel>()

    // 将viewModel设为public，供Adapter访问
    val publicViewModel: ReadingMemoryViewModel get() = viewModel

    private lateinit var adapter: ReadingMemoryAdapter
    private lateinit var searchView: io.legado.app.ui.widget.SearchView

    private lateinit var titleBar: io.legado.app.ui.widget.TitleBar

    // 阅读状态标签
    private lateinit var tabAll: TextView
    private lateinit var tabPending: TextView
    private lateinit var tabReading: TextView
    private lateinit var tabFinished: TextView
    private lateinit var tabAbandoned: TextView
    private var currentFilterStatus: String? = null

    // 底部控制按钮
    private lateinit var btnTimeline: View
    private lateinit var btnGroup: View
    private lateinit var btnRating: View
    private lateinit var btnReadType: View

    // 当前选择状态
    private var currentSortBy: String = "lastRead_desc" // 最近阅读在前
    private var currentGroupBy: String = "none" // 不分组
    private var currentRatingFilter: String = "all" // 全部评分
    private var currentRatingSort: String = "high_to_low" // 高分在前

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 确保状态栏颜色跟随主题
        setupSystemBar()
        // 设置筛选标签栏的胶囊形状背景为主题卡片背景色，保持胶囊形状
        val cardColor = ThemeStore.backgroundCard(this)
        binding.filterTabLayout?.let {
            val bgDrawable = it.background
            if (bgDrawable is android.graphics.drawable.GradientDrawable) {
                bgDrawable.setColor(cardColor)
            }
        }

        initView()
        initSearchView()
        initData()
    }

    override fun onResume() {
        super.onResume()
        // 状态栏颜色已在onActivityCreated中设置，主题变化时会自动更新
    }

    override fun setupSystemBar() {
        super.setupSystemBar()
    }

    /**
     * 设置顶部图标按钮的颜色
     */
    private fun setTopBarIconColor(color: Int) {
        // 设置时间轴按钮的图标颜色
        titleBar.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iconTimeline)?.let {
            it.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }

        // 设置分组按钮的图标颜色
        titleBar.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iconGroup)?.let {
            it.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }

        // 设置评分按钮的图标颜色
        titleBar.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iconRating)?.let {
            it.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }

        // 设置阅读类型按钮的图标颜色
        titleBar.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.iconReadType)?.let {
            it.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun initView() {
        titleBar = binding.titleBar
        setSupportActionBar(titleBar.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }

        // 初始化 searchView
        searchView = titleBar.findViewById(R.id.search_view)

        // 设置顶部图标按钮的颜色
        val titleBarTextIconColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this)
        setTopBarIconColor(titleBarTextIconColor)

        // 初始化阅读状态标签
        initReadingStatusTabs()

        // 初始化顶部控制按钮
        initTopControlButtons()

        // 设置RecyclerView
        adapter = ReadingMemoryAdapter(this, viewModel)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        // 添加ItemDecoration来设置卡片之间的间距
        binding.recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                super.getItemOffsets(outRect, view, parent, state)
                // 设置顶部和底部间距各为4dp，这样两个卡片之间就有8dp的间距
                outRect.top = 4 * resources.displayMetrics.density.toInt()
                outRect.bottom = 4 * resources.displayMetrics.density.toInt()
            }
        })
        binding.recyclerView.adapter = adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>

        // 设置空状态视图
        binding.emptyView.setTitle(getString(R.string.no_reading_memory))
        binding.emptyView.setSubTitle(getString(R.string.reading_memory_empty_desc))
        // 设置空状态视图的文字颜色为次要文字颜色
        val secondaryTextColor = io.legado.app.lib.theme.ThemeStore.textColorSecondary(this)
        binding.emptyView.setTitleColor(secondaryTextColor)
        binding.emptyView.setSubTitleColor(secondaryTextColor)
    }

    /**
     * 初始化顶部控制按钮
     */
    private fun initTopControlButtons() {
        // 从 titleBar 中获取按钮视图
        btnTimeline = titleBar.findViewById(R.id.btnTimeline)
        btnGroup = titleBar.findViewById(R.id.btnGroup)
        btnRating = titleBar.findViewById(R.id.btnRating)
        btnReadType = titleBar.findViewById(R.id.btnReadType)

        // 设置点击事件
        btnTimeline.setOnClickListener {
            // 时间轴按钮点击事件
            showTimelineDialog()
        }

        btnGroup.setOnClickListener {
            // 分组按钮点击事件
            showGroupDialog()
        }

        btnRating.setOnClickListener {
            // 评分按钮点击事件
            showRatingDialog()
        }

        btnReadType.setOnClickListener {
            // 阅读类型按钮点击事件
            showReadTypeFilterDialog()
        }

        // 长按评分按钮显示快速菜单
        btnRating.setOnLongClickListener {
            showRatingQuickMenu()
            true
        }
    }

    /**
     * 初始化阅读状态标签
     */
    private fun initReadingStatusTabs() {
        // 获取标签视图
        tabAll = binding.tabAll
        tabPending = binding.tabPending
        tabReading = binding.tabReading
        tabFinished = binding.tabFinished
        tabAbandoned = binding.tabAbandoned

        // 设置标签文本
        tabAll.text = "全部"
        tabPending.text = "待看"
        tabReading.text = "在看"
        tabFinished.text = "看完"
        tabAbandoned.text = "弃"

        // 设置点击事件
        tabAll.setOnClickListener {
            selectTab(tabAll)
            currentFilterStatus = null
            updateDisplay()
        }

        tabPending.setOnClickListener {
            selectTab(tabPending)
            currentFilterStatus = "待看"
            updateDisplay()
        }

        tabReading.setOnClickListener {
            selectTab(tabReading)
            currentFilterStatus = "在看"
            updateDisplay()
        }

        tabFinished.setOnClickListener {
            selectTab(tabFinished)
            currentFilterStatus = "看完"
            updateDisplay()
        }

        tabAbandoned.setOnClickListener {
            selectTab(tabAbandoned)
            currentFilterStatus = "弃"
            updateDisplay()
        }

        // 默认选中"全部"标签
        selectTab(tabAll)
    }

    /**
     * 选中指定的标签
     */
    private fun selectTab(selectedTab: TextView) {
        // 重置所有标签样式
        resetTabStyles()

        // 设置选中标签样式，使用accentColor作为选中背景，与background_card形成对比
        val selectedColor = io.legado.app.lib.theme.ThemeStore.accentColor
        selectedTab.setTextColor(android.graphics.Color.WHITE) // 选中状态文字设为白色，确保在彩色背景上可见
        // 使用XML中定义的胶囊形状drawable并设置颜色，保持胶囊形状
        val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
        bgDrawable.setTint(selectedColor)
        selectedTab.background = bgDrawable
    }

    /**
     * 重置所有标签样式
     */
    private fun resetTabStyles() {
        val tabs = listOf(tabAll, tabPending, tabReading, tabFinished, tabAbandoned)
        tabs.forEach {
            tab ->
            // 文字使用次要文字颜色
            tab.setTextColor(io.legado.app.lib.theme.ThemeStore.textColorSecondary(this))
            tab.setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }
    }

    /**
     * 初始化标题栏搜索框
     */
    private fun initSearchView() {
        try {
            // 直接使用searchView，因为它是lateinit变量
            // 设置搜索框颜色为标题栏文字图标颜色
            val titleBarTextIconColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this)
            searchView.applyTint(titleBarTextIconColor)
            
            // 手动设置搜索框内的EditText颜色，确保颜色正确应用
            val searchEditText = searchView.findViewById<android.widget.EditText>(androidx.appcompat.R.id.search_src_text)
            if (searchEditText != null) {
                searchEditText.setTextColor(titleBarTextIconColor)
                searchEditText.setHintTextColor(android.graphics.Color.argb(128, android.graphics.Color.red(titleBarTextIconColor), android.graphics.Color.green(titleBarTextIconColor), android.graphics.Color.blue(titleBarTextIconColor)))
            }
            
            searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        viewModel.searchReadingMemories(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        viewModel.searchReadingMemories(it)
                    }
                    return true
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 判断当前是否为夜间模式
     */
    private fun isNightMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun initData() {
        // 检查是否有bookUrl参数
        val bookUrl = intent.getStringExtra("bookUrl")
        if (!bookUrl.isNullOrEmpty()) {
            // 如果有bookUrl参数，直接跳转到阅读记录详情页面
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    // 查找对应书籍的阅读记录
                    val memory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                    if (memory != null) {
                        // 跳转到阅读记录详情页面
                        runOnUiThread {
                            openReadingMemoryDetail(memory)
                        }
                    } else {
                        // 如果没有阅读记录，先创建一个
                        val book = appDb.bookDao.getBook(bookUrl)
                        if (book != null) {
                            val newMemory = viewModel.createReadingMemoryFromBook(book)
                            if (newMemory != null) {
                                runOnUiThread {
                                    openReadingMemoryDetail(newMemory)
                                }
                            } else {
                                runOnUiThread {
                                    toastOnUi("无法创建阅读记录")
                                    finish()
                                }
                            }
                        } else {
                            runOnUiThread {
                                toastOnUi("书籍不存在")
                                finish()
                            }
                        }
                    }
                }
            }
        } else {
            // 检查是否有tagName参数
            val tagName = intent.getStringExtra("tagName")
            if (!tagName.isNullOrEmpty()) {
                // 有tagName参数，根据标签搜索书籍
                lifecycleScope.launch {
                    viewModel.readingMemories.collectLatest { memories: List<io.legado.app.data.entities.ReadingMemory> ->
                        adapter.submitReadingMemoriesList(memories.toList())

                        // 根据数据状态显示/隐藏空视图
                        if (memories.isEmpty()) {
                            binding.recyclerView.visibility = android.view.View.GONE
                            binding.emptyView.visibility = android.view.View.VISIBLE
                        } else {
                            binding.recyclerView.visibility = android.view.View.VISIBLE
                            binding.emptyView.visibility = android.view.View.GONE
                        }
                    }
                }

                lifecycleScope.launch {
                    viewModel.isLoading.collectLatest { isLoading: Boolean ->
                        if (isLoading) {
                            binding.progressBar.visibility = android.view.View.VISIBLE
                        } else {
                            binding.progressBar.visibility = android.view.View.GONE
                        }
                    }
                }

                lifecycleScope.launch {
                    viewModel.statusCounts.collectLatest { counts: Map<String, Int> ->
                        // 更新标签文本，显示"标签名称+数量"的形式
                        tabAll.text = "全部${counts["全部"] ?: 0}"
                        tabPending.text = "待看${counts["待看"] ?: 0}"
                        tabReading.text = "在看${counts["在看"] ?: 0}"
                        tabFinished.text = "看完${counts["看完"] ?: 0}"
                        tabAbandoned.text = "弃${counts["弃"] ?: 0}"  // 显示弃状态的数量
                    }
                }

                // 加载数据
                viewModel.loadReadingMemories()
                // 延迟执行搜索，确保数据已加载完成
                lifecycleScope.launch {
                    // 等待一小段时间，确保数据加载完成
                    delay(100)
                    // 根据标签名称搜索
                    viewModel.searchReadingMemories(tagName)
                    // 设置搜索框文本为标签名称，让用户看到搜索正在执行
                    searchView.setQuery(tagName, false)
                }
            } else {
                // 没有参数，显示所有阅读记录
                lifecycleScope.launch {
                    viewModel.readingMemories.collectLatest { memories: List<io.legado.app.data.entities.ReadingMemory> ->
                        adapter.submitReadingMemoriesList(memories.toList())

                        // 根据数据状态显示/隐藏空视图
                        if (memories.isEmpty()) {
                            binding.recyclerView.visibility = android.view.View.GONE
                            binding.emptyView.visibility = android.view.View.VISIBLE
                        } else {
                            binding.recyclerView.visibility = android.view.View.VISIBLE
                            binding.emptyView.visibility = android.view.View.GONE
                        }
                    }
                }

                lifecycleScope.launch {
                    viewModel.isLoading.collectLatest { isLoading: Boolean ->
                        if (isLoading) {
                            binding.progressBar.visibility = android.view.View.VISIBLE
                        } else {
                            binding.progressBar.visibility = android.view.View.GONE
                        }
                    }
                }

                lifecycleScope.launch {
                    viewModel.statusCounts.collectLatest { counts: Map<String, Int> ->
                        // 更新标签文本，显示"标签名称+数量"的形式
                        tabAll.text = "全部${counts["全部"] ?: 0}"
                        tabPending.text = "待看${counts["待看"] ?: 0}"
                        tabReading.text = "在看${counts["在看"] ?: 0}"
                        tabFinished.text = "看完${counts["看完"] ?: 0}"
                        tabAbandoned.text = "弃${counts["弃"] ?: 0}"  // 显示弃状态的数量
                    }
                }

                // 加载数据
                viewModel.loadReadingMemories()

                // 更新所有我的阅读记录的封面信息
                viewModel.updateAllMemoryCovers()

                // 更新所有我的阅读记录的阅读状态
                viewModel.updateAllReadingStatus()
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reading_memory, menu)
        // 设置三个点菜单图标为标题栏文字图标颜色
        titleBar.toolbar.overflowIcon?.setColorFilter(io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this), PorterDuff.Mode.SRC_ATOP)
        // 设置卡片显示开关的状态
        menu.findItem(R.id.menu_show_card)?.isChecked = io.legado.app.help.config.AppConfig.showBookCard
        // 设置简介显示开关的状态
        menu.findItem(R.id.menu_show_intro)?.isChecked = io.legado.app.help.config.AppConfig.showReadingMemoryIntro
        // 设置书评显示开关的状态
        menu.findItem(R.id.menu_show_review)?.isChecked = io.legado.app.help.config.AppConfig.showBookReview
        return true
    }

    // 当前阅读类型筛选
    private var currentReadType: String? = null

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.menu_show_card -> {
                // 切换卡片显示状态
                item.isChecked = !item.isChecked
                io.legado.app.help.config.AppConfig.showBookCard = item.isChecked
                // 刷新列表以应用卡片背景变化
                adapter.notifyDataSetChanged()
            }

            R.id.menu_show_intro -> {
                // 切换简介显示状态
                item.isChecked = !item.isChecked
                io.legado.app.help.config.AppConfig.showReadingMemoryIntro = item.isChecked
                // 刷新列表以应用简介显示变化
                adapter.notifyDataSetChanged()
            }

            R.id.menu_show_review -> {
                // 切换书评显示状态
                item.isChecked = !item.isChecked
                io.legado.app.help.config.AppConfig.showBookReview = item.isChecked
                // 刷新列表以应用书评显示变化
                adapter.notifyDataSetChanged()
            }

            R.id.menu_clear_all -> {
                // 清空所有我的阅读记录
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.clear_all_reading_memories)
                    .setMessage(R.string.clear_all_reading_memories_confirm)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewModel.clearAllReadingMemories()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }

            106 -> {
                // 阅读类型筛选
                showReadTypeFilterDialog()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 显示阅读类型筛选对话框
     */
    private fun showReadTypeFilterDialog() {
        val items = listOf(
            "全部" to null,
            "阅读" to "text",
            "听书" to "audio",
            "看剧" to "video"
        )
        alert("选择阅读类型") {
            items(items.map { it.first }) { _, index: Int ->
                val (_, type) = items[index]
                currentReadType = type
                updateDisplay()
            }
        }
    }

    /**
     * 打开我的阅读详情页面
     */
    fun openReadingMemoryDetail(memory: io.legado.app.data.entities.ReadingMemory) {
        val intent = android.content.Intent(this, ReadingMemoryDetailActivity::class.java)
        intent.putExtra("memoryId", memory.id)
        startActivity(intent)
    }

    /**
     * 显示时间轴排序对话框
     */
    private fun showTimelineDialog() {
        val dialog = TimelineSortDialog(currentSortBy) {
            selectedSortBy ->
            currentSortBy = selectedSortBy
            updateDisplay()
        }
        dialog.show(supportFragmentManager, "TimelineSortDialog")
    }

    /**
     * 显示分组方式对话框
     */
    private fun showGroupDialog() {
        val dialog = GroupSortDialog(currentGroupBy) {
            selectedGroupBy ->
            currentGroupBy = selectedGroupBy
            updateDisplay()
        }
        dialog.show(supportFragmentManager, "GroupSortDialog")
    }

    /**
     * 显示评分筛选和排序对话框
     */
    private fun showRatingDialog() {
        val dialog = RatingSettingsDialog(currentRatingFilter, currentRatingSort) {
            selectedRatingFilter, selectedRatingSort ->
            currentRatingFilter = selectedRatingFilter
            currentRatingSort = selectedRatingSort
            updateDisplay()
        }
        dialog.show(supportFragmentManager, "RatingSettingsDialog")
    }

    /**
     * 显示评分快速菜单
     */
    private fun showRatingQuickMenu() {
        val items = arrayOf("★★★★★ 只看5星", "★★★★☆ 只看4星", "全部评分")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("快速选择")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> currentRatingFilter = "5"
                    1 -> currentRatingFilter = "4"
                    2 -> currentRatingFilter = "all"
                }
                updateDisplay()
            }
            .show()
    }

    /**
     * 更新显示
     */
    private fun updateDisplay() {
        // 设置适配器的分组方式
        adapter.setGroupBy(currentGroupBy)
        // 将字符串类型的阅读类型转换为对应的Int类型
        val readTypeInt = when (currentReadType) {
            "text" -> io.legado.app.constant.BookType.text
            "audio" -> io.legado.app.constant.BookType.audio
            "video" -> io.legado.app.constant.BookType.video
            else -> null
        }
        // 调用ViewModel的方法来获取筛选、排序和分组后的数据
        viewModel.getFilteredAndSortedMemories(
            currentFilterStatus,
            currentSortBy,
            currentGroupBy,
            currentRatingFilter,
            currentRatingSort,
            readTypeInt
        )
    }

    /**
     * 显示Toast提示
     */
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 加载封面图片
     */
    fun loadCover(imageView: android.widget.ImageView, coverUrl: String?) {
        if (!coverUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(coverUrl)
                .placeholder(R.drawable.ic_image_default)
                .error(R.drawable.ic_image_default)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_image_default)
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            viewModel.loadReadingMemories()
        }
        observeEvent<String>(EventBus.TAGS_UPDATED) {
            // 标签更新时，重新加载数据并强制刷新适配器
            viewModel.loadReadingMemories()
            // 延迟执行，确保数据已加载完成
            lifecycleScope.launch {
                delay(100)
                adapter.notifyDataSetChanged()
            }
        }
        observeEvent<String>(EventBus.BOOK_INTRO_UPDATED) { bookUrl: String ->
            // 简介更新时，刷新阅读记忆列表
            viewModel.loadReadingMemories()
            lifecycleScope.launch {
                delay(100)
                adapter.notifyDataSetChanged()
            }
        }
        observeEvent<String>(EventBus.BOOK_REVIEW_UPDATED) { bookUrl: String ->
            // 书评更新时，刷新阅读记忆列表
            viewModel.loadReadingMemories()
            lifecycleScope.launch {
                delay(100)
                adapter.notifyDataSetChanged()
            }
        }
    }
}