package io.legado.app.ui.about

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookReadTimeRank
import io.legado.app.data.entities.DailyReadTime
import io.legado.app.data.entities.HeatmapDayData
import io.legado.app.data.entities.HourReadTime
import io.legado.app.data.entities.AuthorReadTime
import io.legado.app.data.entities.TagReadCount
import io.legado.app.data.entities.TimeDistribution
import io.legado.app.data.entities.ReadStatistics
import io.legado.app.service.StatisticsService
import io.legado.app.databinding.ActivityReadStatisticsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.local.ReadStatisticsViewModel
import io.legado.app.ui.widget.MonthlyHeatmapView
import io.legado.app.ui.widget.YearlyHeatmapView
import io.legado.app.ui.book.readRecord.component.BookRankingData
import io.legado.app.lib.dialogs.alert

import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.HeatmapCacheManager
import io.legado.app.utils.StatisticsCacheManager
import io.legado.app.utils.dpToPx
import io.legado.app.help.book.BookplateHtmlRenderer
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.ui.widget.dialog.BookplateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReadStatisticsActivity : VMBaseActivity<ActivityReadStatisticsBinding, ReadStatisticsViewModel>() {

    override val binding by viewBinding(ActivityReadStatisticsBinding::inflate)

    override val viewModel = ReadStatisticsViewModel()

    private val statisticsAdapter by lazy { ReadStatisticsAdapter(this) }
    private var currentType = 0 // 0:总计, 1:每日, 2:每月, 3:每年, 4:每周
    // 为每种统计类型维护独立的日期变量，避免日期联动
    private var currentDate: Calendar = Calendar.getInstance()
    private var dailyDate: Calendar = Calendar.getInstance()
    private var monthlyDate: Calendar = Calendar.getInstance()
    private var yearlyDate: Calendar = Calendar.getInstance()
    private var weeklyDate: Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 1
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val weekRangeFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

    // 阅读类型筛选
    private var currentReadType: Int? = null

    // 热力图相关
    private lateinit var monthlyHeatmapView: MonthlyHeatmapView
    private lateinit var yearlyHeatmapView: YearlyHeatmapView
    private lateinit var heatmapContainer: LinearLayout
    private var currentTop10Data: List<BookReadTimeRank> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.titleBar.title = getString(R.string.read_statistics)
        initView()
        initComposeTop10()
        initComposeDistributionChart()
        // 确保 RecyclerView 的布局管理器已正确设置
        binding.recyclerView!!.layoutManager = LinearLayoutManager(this)
        // 确保初始状态下导航可见性正确（总计模式应隐藏导航）
        currentType = 0
        currentReadType = null
        statisticsAdapter.currentReadType = currentReadType
        statisticsAdapter.currentType = currentType
        updateNavigationVisibility()
        // 确保初始状态下热力图可见性正确
        updateHeatmapVisibility()
        loadData()
        // 监听 ReadSession 数据变化，实时更新统计数据
        observeReadSessionChanges()
    }

    /**
     * 监听 ReadSession 数据变化，实时更新统计数据
     */
    private fun observeReadSessionChanges() {
        lifecycleScope.launch {
            appDb.readSessionDao.flowGetAll().collect {
                // 当 ReadSession 数据变化时，清除缓存并重新加载数据
                StatisticsCacheManager.clearAllCache()
                // 根据当前统计类型选择正确的加载方法
                lifecycleScope.launch {
                    when (currentType) {
                        0 -> loadTotalStatistics()
                        1 -> loadDailyStatisticsByDate()
                        2 -> loadMonthlyStatisticsByMonth()
                        3 -> loadYearlyStatisticsByYear()
                        4 -> loadWeeklyStatisticsByWeek()
                    }
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 基类要求的抽象方法，可以在这里进行一些初始化操作
        // 目前不需要特殊处理
    }

    private fun initComposeTop10() {
        binding.composeTop10Container?.apply {
            setViewTreeLifecycleOwner(this@ReadStatisticsActivity)
            this.setViewTreeSavedStateRegistryOwner(this@ReadStatisticsActivity)
            setContent {
            io.legado.app.ui.book.readRecord.component.TopReadingListCard(
                topBooks = currentTop10Data.map { rank ->
                    BookRankingData(
                        bookName = rank.bookName,
                        bookAuthor = "",
                        readTime = rank.readTime,
                        coverUrl = rank.coverUrl
                    )
                }.take(10),
                onBookClick = { bookName, bookAuthor ->
                    // 点击书籍可以跳转到对应阅读记录或详情页
                }
            )
        }
        }
    }

    private fun initComposeDistributionChart() {
        binding.composeDistributionChartContainer?.apply {
            setViewTreeLifecycleOwner(this@ReadStatisticsActivity)
            setViewTreeSavedStateRegistryOwner(this@ReadStatisticsActivity)
        }
    }

    private fun initView() {
        binding.recyclerView?.setEdgeEffectColor(primaryColor)
        binding.recyclerView?.layoutManager = LinearLayoutManager(this)
        binding.recyclerView?.adapter = statisticsAdapter
        binding.recyclerView?.itemAnimator = null
        // 禁用RecyclerView的嵌套滚动，使其在NestedScrollView中正常滚动
        binding.recyclerView?.isNestedScrollingEnabled = false
        // 禁用RecyclerView的滚动条，避免与NestedScrollView冲突
        binding.recyclerView?.isVerticalScrollBarEnabled = false
        // 设置RecyclerView的高度为wrap_content，确保内容完全显示
        binding.recyclerView?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT

        // 设置统计类型选择容器的胶囊形状背景为主题卡片背景色，保持胶囊形状
        val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(this)
        binding.statisticsTypeContainer?.let {
            val bgDrawable = it.background
            if (bgDrawable is android.graphics.drawable.GradientDrawable) {
                bgDrawable.setColor(cardColor)
                val dividerColor = io.legado.app.lib.theme.ThemeStore.dividerColor(this)
                bgDrawable.setStroke((AppConfig.cardBorderWidth * 0.5f).dpToPx().toInt(), dividerColor)
            }
        }

        // 为阅读总览添加行间距
        val spacing = resources.getDimensionPixelSize(R.dimen.dimen_spacing_5dp) // 5dp的行间距
        binding.recyclerView?.addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: android.view.View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                val itemCount = parent.adapter?.itemCount ?: 0

                // 为第一项添加顶部间距
                if (position == 0) {
                    outRect.top = spacing
                }

                // 为所有项添加底部间距，包括最后一项
                outRect.bottom = spacing
            }
        })

        // 初始化热力图组件
        monthlyHeatmapView = binding.monthlyHeatmapView
        yearlyHeatmapView = binding.yearlyHeatmapView
        heatmapContainer = binding.heatmapContainer

        // 初始化统计类型卡片按钮
        setupStatisticsTypeCards()

        // 设置导航按钮点击事件
        binding.btnPrev?.setOnClickListener {
            navigatePrevious()
        }

        binding.btnNext?.setOnClickListener {
            navigateNext()
        }

        // 设置时间文本点击事件，用于弹出日期选择器
        binding.tvCurrentPeriod?.setOnClickListener {
            showDatePickerDialog()
        }

        // 动态设置时间栏按钮的背景颜色，使用主题色
        updateNavigationButtonColors()

        // 设置卡片背景色为主题设置的背景色
        binding.navigationCard?.setCardBackgroundColor(cardColor)
        binding.heatmapCard?.setCardBackgroundColor(cardColor)
        
        // 设置卡片边框
        val dividerColor = io.legado.app.lib.theme.ThemeStore.dividerColor(this)
        binding.navigationCard?.strokeWidth = (AppConfig.cardBorderWidth * 0.5f).dpToPx().toInt()
        binding.navigationCard?.setStrokeColor(android.content.res.ColorStateList.valueOf(dividerColor))
        binding.heatmapCard?.strokeWidth = (AppConfig.cardBorderWidth * 0.5f).dpToPx().toInt()
        binding.heatmapCard?.setStrokeColor(android.content.res.ColorStateList.valueOf(dividerColor))
        binding.ivHeatmapIcon?.setColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN)
    }

    private fun updateNavigationButtonColors() {
        // 使用主题色设置按钮背景
        val buttonColor = accentColor

        // 创建圆形形状drawable
        val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape())
        shapeDrawable.paint.color = buttonColor

        // 设置按钮背景
        binding.btnPrev?.background = shapeDrawable
        binding.btnNext?.background = shapeDrawable

        // 设置按钮图标颜色为标题栏文字图标颜色，确保在彩色背景上可见
        val titleBarTextIconColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this)
        binding.btnPrev?.setColorFilter(titleBarTextIconColor)
        binding.btnNext?.setColorFilter(titleBarTextIconColor)
    }

    private fun setupStatisticsTypeCards() {
        // 设置总计按钮点击事件
        binding.cardTotal?.setOnClickListener {
            currentType = 0
            statisticsAdapter.currentType = currentType
            updateNavigationVisibility()
            updateHeatmapVisibility()
            updateCardSelection()
            updateCurrentPeriodText() // 更新时间栏文本
            loadData() // 总计统计加载所有数据
        }

        // 设置每日按钮点击事件
        binding.cardDaily?.setOnClickListener {
            currentType = 1
            statisticsAdapter.currentType = currentType
            // 注释掉清除缓存的代码，保留缓存以避免重新加载
            // HeatmapCacheManager.clearMonthlyCache()
            // HeatmapCacheManager.clearYearlyCache()
            updateNavigationVisibility()
            updateHeatmapVisibility()
            updateCardSelection()
            updateCurrentPeriodText() // 更新时间栏文本
            loadDataForSpecificPeriod() // 加载特定时期的数据
        }

        // 设置每月按钮点击事件
        binding.cardMonthly?.setOnClickListener {
            currentType = 2
            statisticsAdapter.currentType = currentType
            // 注释掉清除缓存的代码，保留缓存以避免重新加载
            // HeatmapCacheManager.clearYearlyCache()
            updateNavigationVisibility()
            updateHeatmapVisibility()
            updateCardSelection()
            updateCurrentPeriodText() // 更新时间栏文本
            loadDataForSpecificPeriod() // 加载特定时期的数据

            // 确保月度热力图正确显示
            monthlyHeatmapView.post {
                monthlyHeatmapView.requestLayout()
            }
        }

        // 设置每年按钮点击事件
        binding.cardYearly?.setOnClickListener {
            currentType = 3
            statisticsAdapter.currentType = currentType
            // 注释掉清除缓存的代码，保留缓存以避免重新加载
            // HeatmapCacheManager.clearMonthlyCache()
            updateNavigationVisibility()
            updateHeatmapVisibility()
            updateCardSelection()
            updateCurrentPeriodText() // 更新时间栏文本
            loadDataForSpecificPeriod() // 加载特定时期的数据

            // 确保年度热力图正确显示 - 添加延迟确保布局完成
            yearlyHeatmapView.post {
                yearlyHeatmapView.post {
                    yearlyHeatmapView.forceRefresh()

                    // 再次延迟刷新，确保数据加载完成
                    yearlyHeatmapView.postDelayed({
                        yearlyHeatmapView.forceRefresh()

                        // 第三次延迟刷新，确保布局完全完成
                        yearlyHeatmapView.postDelayed({
                            yearlyHeatmapView.forceRefresh()
                        }, 100)
                    }, 200) // 增加延迟时间确保布局完成
                }
            }
        }

        // 设置每周按钮点击事件
        binding.cardWeekly?.setOnClickListener {
            currentType = 4
            statisticsAdapter.currentType = currentType
            updateNavigationVisibility()
            updateHeatmapVisibility()
            updateCardSelection()
            updateCurrentPeriodText()
            loadDataForSpecificPeriod()
        }

        // 初始化选中状态
        updateCardSelection()
    }

    private fun updateCardSelection() {
        // 更新导航按钮颜色，确保与主题保持一致
        updateNavigationButtonColors()

        // 重置所有卡片样式
        val secondaryTextColor = io.legado.app.lib.theme.ThemeStore.textColorSecondary(this)
        val transparentColor = androidx.core.content.ContextCompat.getColor(this, android.R.color.transparent)

        binding.cardTotal?.apply {
            setBackgroundColor(transparentColor)
            setTextColor(secondaryTextColor)
            setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }
        binding.cardDaily?.apply {
            setBackgroundColor(transparentColor)
            setTextColor(secondaryTextColor)
            setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }
        binding.cardMonthly?.apply {
            setBackgroundColor(transparentColor)
            setTextColor(secondaryTextColor)
            setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }
        binding.cardYearly?.apply {
            setBackgroundColor(transparentColor)
            setTextColor(secondaryTextColor)
            setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }
        binding.cardWeekly?.apply {
            setBackgroundColor(transparentColor)
            setTextColor(secondaryTextColor)
            setBackgroundResource(R.drawable.bg_reading_tab_unselected)
        }

        // 设置当前选中卡片的样式
        val selectedColor = accentColor
        when (currentType) {
            0 -> {
                binding.cardTotal?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    background = bgDrawable
                }
            }
            1 -> {
                binding.cardDaily?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    background = bgDrawable
                }
            }
            2 -> {
                binding.cardMonthly?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    background = bgDrawable
                }
            }
            3 -> {
                binding.cardYearly?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    background = bgDrawable
                }
            }
            4 -> {
                binding.cardWeekly?.apply {
                    setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    background = bgDrawable
                }
            }
        }
    }

    // 判断当前是否为夜间模式
    private fun isNightMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun updateNavigationVisibility() {
        // 总计统计不显示导航卡片
        binding.navigationCard?.visibility = if (currentType == 0) View.GONE else View.VISIBLE

        // 总计统计不显示导航按钮
        binding.btnPrev?.visibility = if (currentType == 0) View.GONE else View.VISIBLE
        binding.btnNext?.visibility = if (currentType == 0) View.GONE else View.VISIBLE

        // 总计统计不显示当前时期文本
        binding.tvCurrentPeriod?.visibility = if (currentType == 0) View.GONE else View.VISIBLE
    }

    // 更新热力图可见性
    private fun updateHeatmapVisibility() {
        when (currentType) {
            2 -> { // 月度统计
                binding.heatmapCard?.visibility = View.VISIBLE
                binding.monthlyHeatmapView?.visibility = View.VISIBLE
                binding.yearlyHeatmapView?.visibility = View.GONE
                binding.tvHeatmapTitle?.text = "月度热力图"
            }
            3 -> { // 年度统计
                binding.heatmapCard?.visibility = View.VISIBLE
                binding.monthlyHeatmapView?.visibility = View.GONE
                binding.yearlyHeatmapView?.visibility = View.VISIBLE
                binding.tvHeatmapTitle?.text = "年度热力图"
            }
            0, 1, 4 -> { // 总计统计, 每日统计, 每周统计
                binding.heatmapCard?.visibility = View.GONE
                binding.monthlyHeatmapView?.visibility = View.GONE
                binding.yearlyHeatmapView?.visibility = View.GONE
            }
        }
    }

    private fun updateNavigationButtonText() {
        // ImageButtons don't need text updates as they use icons
        // The navigation functionality is handled by the click listeners
    }

    private fun updateCurrentPeriodText() {
        when (currentType) {
            1 -> { // 每日
                binding.tvCurrentPeriod?.text = dateFormat.format(dailyDate.time)
            }
            2 -> { // 每月
                binding.tvCurrentPeriod?.text = monthFormat.format(monthlyDate.time)
            }
            3 -> { // 每年
                binding.tvCurrentPeriod?.text = yearFormat.format(yearlyDate.time)
            }
            4 -> { // 每周
                binding.tvCurrentPeriod?.text = getWeekRangeDisplay(weeklyDate)
            }
        }
    }

    private fun navigatePrevious() {
        when (currentType) {
            1 -> { // 每日
                dailyDate.add(Calendar.DAY_OF_MONTH, -1)
            }
            2 -> { // 每月
                monthlyDate.add(Calendar.MONTH, -1)
            }
            3 -> { // 每年
                yearlyDate.add(Calendar.YEAR, -1)
            }
            4 -> { // 每周
                weeklyDate.add(Calendar.DAY_OF_MONTH, -7)
            }
        }
        updateCurrentPeriodText()
        loadDataForSpecificPeriod()
    }

    private fun navigateNext() {
        when (currentType) {
            1 -> { // 每日
                dailyDate.add(Calendar.DAY_OF_MONTH, 1)
            }
            2 -> { // 每月
                monthlyDate.add(Calendar.MONTH, 1)
            }
            3 -> { // 每年
                yearlyDate.add(Calendar.YEAR, 1)
            }
            4 -> { // 每周
                weeklyDate.add(Calendar.DAY_OF_MONTH, 7)
            }
        }
        updateCurrentPeriodText()
        loadDataForSpecificPeriod()
    }

    private fun initData() {
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            when (currentType) {
                0 -> loadTotalStatistics()
                1 -> loadDailyStatistics()
                2 -> loadMonthlyStatistics()
                3 -> loadYearlyStatistics()
                4 -> loadWeeklyStatistics()
            }
        }
    }

    private fun loadDataForSpecificPeriod() {
        lifecycleScope.launch {
            when (currentType) {
                1 -> loadDailyStatisticsByDate()
                2 -> loadMonthlyStatisticsByMonth()
                3 -> loadYearlyStatisticsByYear()
                4 -> loadWeeklyStatisticsByWeek()
            }
        }
    }

    private suspend fun loadTotalStatistics() {
        // 使用StatisticsService获取总计统计数据
        val totalStatistics = if (currentReadType != null) {
            StatisticsService.getTotalStatisticsByType(currentReadType!!)
        } else {
            StatisticsService.getTotalStatistics()
        }
        // 检查是否有数据，如果没有数据则创建一个空的ReadStatistics对象
        if (totalStatistics.bookCount == 0 && totalStatistics.totalTime == 0L) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = ""
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(listOf(totalStatistics))
        }

        // 加载总阅读时间排行TOP10
        loadTop10Data()

        // 加载分析数据
        loadAnalysis(null, null)

        // 加载分布图
        loadDistributionChart()

        // 确保RecyclerView正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadTop10Data() {
        currentTop10Data = if (currentReadType != null) {
            StatisticsService.getTotalReadTimeTop10ByType(currentReadType!!)
        } else {
            StatisticsService.getTotalReadTimeTop10()
        }
        updateComposeTop10()
    }

    private suspend fun loadTop10DataForDate(dateStr: String) {
        currentTop10Data = if (currentReadType != null) {
            StatisticsService.getDailyReadTimeTop10ByType(dateStr, currentReadType!!)
        } else {
            StatisticsService.getDailyReadTimeTop10(dateStr)
        }
        updateComposeTop10()
    }

    private suspend fun loadTop10DataForMonth(monthStr: String) {
        currentTop10Data = if (currentReadType != null) {
            StatisticsService.getMonthlyReadTimeTop10ByType(monthStr, currentReadType!!)
        } else {
            StatisticsService.getMonthlyReadTimeTop10(monthStr)
        }
        updateComposeTop10()
    }

    private suspend fun loadTop10DataForYear(yearStr: String) {
        currentTop10Data = if (currentReadType != null) {
            StatisticsService.getYearlyReadTimeTop10ByType(yearStr, currentReadType!!)
        } else {
            StatisticsService.getYearlyReadTimeTop10(yearStr)
        }
        updateComposeTop10()
    }

    private suspend fun getBookCover(bookName: String): String? {
        val coverFromSession = withContext(Dispatchers.IO) {
            appDb.readSessionDao.getLatestByBookName(bookName)?.coverUrl?.ifEmpty { null }
        }
        if (coverFromSession != null) return coverFromSession
        val coverFromBook = withContext(Dispatchers.IO) {
            appDb.bookDao.findByName(bookName).firstOrNull()?.getDisplayCover()
        }
        if (coverFromBook != null) return coverFromBook
        return withContext(Dispatchers.IO) {
            appDb.readingMemoryDao.getByBookName(bookName).firstOrNull()?.coverUrl
        }
    }

    private suspend fun updateComposeTop10() {
        val rankingData = withContext(Dispatchers.IO) {
            currentTop10Data.map { rank ->
                val book = appDb.bookDao.findByName(rank.bookName).firstOrNull()
                val coverUrl = rank.coverUrl.ifEmpty { getBookCover(rank.bookName) } ?: ""
                BookRankingData(
                    bookName = rank.bookName,
                    bookAuthor = book?.author ?: "",
                    readTime = rank.readTime,
                    coverUrl = coverUrl
                )
            }.take(10)
        }
        binding.composeTop10Container?.setContent {
            io.legado.app.ui.book.readRecord.component.TopReadingListCard(
                topBooks = rankingData,
                onBookClick = { bookName, bookAuthor ->
                }
            )
        }
    }

    private suspend fun loadDistributionChart() {
        try {
            val data: List<Pair<String, Long>> = when (currentType) {
                0 -> emptyList()
                1 -> {
                    val startTime = getDayStart(dailyDate)
                    val endTime = getDayEnd(dailyDate)
                    val dist = if (currentReadType != null)
                        StatisticsService.getHourlyDistributionInRangeByType(startTime, endTime, currentReadType!!)
                    else StatisticsService.getHourlyDistributionInRange(startTime, endTime)
                    val hourMap = dist.associate { it.hour to it.totalTime }
                    (0..23).map { hour -> "$hour" to (hourMap[hour] ?: 0L) }
                }
                2 -> {
                    val dist = if (currentReadType != null)
                        StatisticsService.getDayOfMonthDistributionInRangeByType(
                            getMonthStart(monthlyDate), getMonthEnd(monthlyDate), currentReadType!!
                        )
                    else StatisticsService.getDayOfMonthDistributionInRange(
                        getMonthStart(monthlyDate), getMonthEnd(monthlyDate)
                    )
                    val dayMap = dist.associate { it.sortKey to it.totalTime }
                    val daysInMonth = monthlyDate.getActualMaximum(Calendar.DAY_OF_MONTH)
                    (1..daysInMonth).map { day -> "$day" to (dayMap[day] ?: 0L) }
                }
                3 -> {
                    val dist = if (currentReadType != null)
                        StatisticsService.getMonthDistributionInRangeByType(
                            getYearStart(yearlyDate), getYearEnd(yearlyDate), currentReadType!!
                        )
                    else StatisticsService.getMonthDistributionInRange(
                        getYearStart(yearlyDate), getYearEnd(yearlyDate)
                    )
                    val monthMap = dist.associate { it.sortKey to it.totalTime }
                    (1..12).map { month -> "$month" to (monthMap[month] ?: 0L) }
                }
                4 -> {
                    val dist = if (currentReadType != null)
                        StatisticsService.getDayOfWeekDistributionInRangeByType(
                            getWeekStart(weeklyDate), getWeekEnd(weeklyDate), currentReadType!!
                        )
                    else StatisticsService.getDayOfWeekDistributionInRange(
                        getWeekStart(weeklyDate), getWeekEnd(weeklyDate)
                    )
                    val dayMap = dist.associate { it.sortKey to it.totalTime }
                    (0..6).map { day -> dayOfWeekLabel(day) to (dayMap[day] ?: 0L) }
                }
                else -> emptyList()
            }

            updateDistributionChart("阅读时长分布", data)
        } catch (_: Exception) {
            binding.composeDistributionChartContainer?.visibility = View.GONE
        }
    }

    private fun dayOfWeekLabel(day: Int): String = when (day) {
        0 -> "周日"
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> ""
    }

    private fun updateDistributionChart(title: String, data: List<Pair<String, Long>>) {
        if (data.isEmpty() || data.all { it.second == 0L }) {
            binding.composeDistributionChartContainer?.visibility = View.GONE
            return
        }
        binding.composeDistributionChartContainer?.visibility = View.VISIBLE
        binding.composeDistributionChartContainer?.setContent {
            io.legado.app.ui.book.readRecord.component.TimeDistributionChart(
                title = title,
                data = data
            )
        }
    }

    private suspend fun loadDailyStatistics() {
        val dailyStatistics = if (currentReadType != null) {
            StatisticsService.getDailyStatisticsByType(currentReadType!!)
        } else {
            StatisticsService.getDailyStatistics()
        }
        // 检查是否有数据，如果没有数据则创建一个空的ReadStatistics对象
        if (dailyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = ""
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(dailyStatistics)
        }

        // 加载总阅读时间排行TOP10
        loadTop10Data()

        // 加载分布图
        loadDistributionChart()

        // 确保RecyclerView正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadDailyStatisticsByDate() {
        val dateStr = dateFormat.format(dailyDate.time)
        val dailyStatistics = if (currentReadType != null) {
            StatisticsService.getDailyStatisticsByDateAndType(dateStr, currentReadType!!)
        } else {
            StatisticsService.getDailyStatisticsByDate(dateStr)
        }
        // 检查是否有数据，如果没有数据则创建一个空的 ReadStatistics 对象
        if (dailyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = dateStr
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(dailyStatistics)
        }

        // 加载特定日期的阅读时间排行 TOP10
        loadTop10DataForDate(dateStr)

        // 加载分析数据
        loadAnalysis(getDayStart(dailyDate), getDayEnd(dailyDate))

        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadMonthlyStatistics() {
        val monthlyStatistics = if (currentReadType != null) {
            StatisticsService.getMonthlyStatisticsByType(currentReadType!!)
        } else {
            StatisticsService.getMonthlyStatistics()
        }
        // 检查是否有数据，如果没有数据则创建一个空的 ReadStatistics 对象
        if (monthlyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = ""
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(monthlyStatistics)
        }

        // 加载当前月份的月度热力图数据
        val currentMonth = monthFormat.format(monthlyDate.time)
        loadMonthlyHeatmapData(currentMonth)
        
        // 加载当前月份的阅读时间排行 TOP10
        loadTop10DataForMonth(currentMonth)
        
        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadMonthlyStatisticsByMonth() {
        val monthStr = monthFormat.format(monthlyDate.time)
        val monthlyStatistics = if (currentReadType != null) {
            StatisticsService.getMonthlyStatisticsByMonthAndType(monthStr, currentReadType!!)
        } else {
            StatisticsService.getMonthlyStatisticsByMonth(monthStr)
        }
        // 检查是否有数据，如果没有数据则创建一个空的 ReadStatistics 对象
        if (monthlyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = monthStr
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(monthlyStatistics)
        }

        // 加载月度热力图数据
        loadMonthlyHeatmapData(monthStr)

        // 加载特定月份的阅读时间排行 TOP10
        loadTop10DataForMonth(monthStr)

        // 加载分析数据
        loadAnalysis(getMonthStart(monthlyDate), getMonthEnd(monthlyDate))

        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadYearlyStatistics() {
        val yearlyStatistics = if (currentReadType != null) {
            StatisticsService.getYearlyStatisticsByType(currentReadType!!)
        } else {
            StatisticsService.getYearlyStatistics()
        }
        // 检查是否有数据，如果没有数据则创建一个空的 ReadStatistics 对象
        if (yearlyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = ""
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(yearlyStatistics)
        }
        
        // 加载当前年份的阅读时间排行 TOP10
        val currentYear = yearFormat.format(yearlyDate.time)
        loadTop10DataForYear(currentYear)
        
        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadYearlyStatisticsByYear() {
        val yearStr = yearFormat.format(yearlyDate.time)
        val yearlyStatistics = if (currentReadType != null) {
            StatisticsService.getYearlyStatisticsByYearAndType(yearStr, currentReadType!!)
        } else {
            StatisticsService.getYearlyStatisticsByYear(yearStr)
        }
        // 检查是否有数据，如果没有数据则创建一个空的 ReadStatistics 对象
        if (yearlyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = yearStr
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(yearlyStatistics)
        }

        // 加载年度热力图数据
        loadYearlyHeatmapData(yearStr)

        // 加载特定年份的阅读时间排行 TOP10
        loadTop10DataForYear(yearStr)

        // 加载分析数据
        loadAnalysis(getYearStart(yearlyDate), getYearEnd(yearlyDate))

        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    /**
     * 获取自然周的标识字符串，格式 yyyy-WW，与 SQLite strftime('%Y-%W') 一致
     */
    private fun getWeekStr(cal: Calendar): String {
        val clone = cal.clone() as Calendar
        clone.firstDayOfWeek = Calendar.MONDAY
        clone.minimalDaysInFirstWeek = 1
        clone.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val year = clone.get(Calendar.YEAR)
        val week = clone.get(Calendar.WEEK_OF_YEAR) - 1
        return "$year-${String.format("%02d", week)}"
    }

    /**
     * 获取自然周的显示文本，显示该周的周一~周日范围
     */
    private fun getWeekRangeDisplay(cal: Calendar): String {
        val clone = cal.clone() as Calendar
        clone.firstDayOfWeek = Calendar.MONDAY
        clone.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val mondayStr = weekRangeFormat.format(clone.time)
        clone.add(Calendar.DAY_OF_MONTH, 6)
        val sundayStr = weekRangeFormat.format(clone.time)
        return "$mondayStr ~ $sundayStr"
    }

    private suspend fun loadWeeklyStatistics() {
        val weekStr = getWeekStr(weeklyDate)
        val weeklyStatistics = if (currentReadType != null) {
            StatisticsService.getWeeklyStatisticsByWeekAndType(weekStr, currentReadType!!)
        } else {
            StatisticsService.getWeeklyStatisticsByWeek(weekStr)
        }
        if (weeklyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = ""
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(weeklyStatistics)
        }
        loadTop10DataForWeek(weekStr)

        // 加载分析数据
        loadAnalysis(getWeekStart(weeklyDate), getWeekEnd(weeklyDate))

        // 加载分布图
        loadDistributionChart()

        refreshRecyclerViewHeight()
    }

    private suspend fun loadWeeklyStatisticsByWeek() {
        val weekStr = getWeekStr(weeklyDate)
        val weeklyStatistics = if (currentReadType != null) {
            StatisticsService.getWeeklyStatisticsByWeekAndType(weekStr, currentReadType!!)
        } else {
            StatisticsService.getWeeklyStatisticsByWeek(weekStr)
        }
        if (weeklyStatistics.isEmpty()) {
            val emptyStatistics = ReadStatistics(
                bookCount = 0,
                finishedBookCount = 0,
                reviewCount = 0,
                totalTime = 0L,
                date = weekStr
            )
            statisticsAdapter.setItems(listOf(emptyStatistics))
        } else {
            statisticsAdapter.setItems(weeklyStatistics)
        }
        loadTop10DataForWeek(weekStr)

        // 加载分析数据
        loadAnalysis(getWeekStart(weeklyDate), getWeekEnd(weeklyDate))

        // 加载分布图
        loadDistributionChart()

        // 确保 RecyclerView 正确显示所有内容
        refreshRecyclerViewHeight()
    }

    private suspend fun loadTop10DataForWeek(weekStr: String) {
        currentTop10Data = if (currentReadType != null) {
            StatisticsService.getWeeklyReadTimeTop10ByType(weekStr, currentReadType!!)
        } else {
            StatisticsService.getWeeklyReadTimeTop10(weekStr)
        }
        updateComposeTop10()
    }

    private fun getDayStart(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getDayEnd(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }

    private fun getWeekStart(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return getDayStart(c)
    }

    private fun getWeekEnd(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        return getDayEnd(c)
    }

    private fun getMonthStart(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        return getDayStart(c)
    }

    private fun getMonthEnd(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        return getDayEnd(c)
    }

    private fun getYearStart(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_YEAR, 1)
        return getDayStart(c)
    }

    private fun getYearEnd(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_YEAR, c.getActualMaximum(Calendar.DAY_OF_YEAR))
        return getDayEnd(c)
    }

    private suspend fun loadAnalysis(startTime: Long?, endTime: Long?) {
        try {
            // 时段偏好
            statisticsAdapter.hourlyData = if (startTime != null && endTime != null) {
                if (currentReadType != null)
                    StatisticsService.getHourlyDistributionInRangeByType(startTime, endTime, currentReadType!!)
                else
                    StatisticsService.getHourlyDistributionInRange(startTime, endTime)
            } else {
                if (currentReadType != null)
                    StatisticsService.getHourlyDistributionByType(currentReadType!!)
                else
                    StatisticsService.getHourlyDistribution()
            }

            // 连续阅读天数（总计和年度展示）
            statisticsAdapter.continuousDays = if (startTime != null && endTime != null) {
                StatisticsService.getContinuousReadingDaysInRange(startTime, endTime)
            } else {
                StatisticsService.getContinuousReadingDays()
            }

            // 最爱作者（日不展示，其余展示）
            val showAuthor = currentType != 1
            statisticsAdapter.authorTop5 = if (showAuthor) {
                if (startTime != null && endTime != null) {
                    if (currentReadType != null)
                        StatisticsService.getAuthorTop5InRangeByType(startTime, endTime, currentReadType!!)
                    else
                        StatisticsService.getAuthorTop5InRange(startTime, endTime)
                } else {
                    if (currentReadType != null)
                        StatisticsService.getAuthorTop5ByType(currentReadType!!)
                    else
                        StatisticsService.getAuthorTop5()
                }
            } else {
                emptyList()
            }

            // 最爱标签（日不展示，其余展示）
            statisticsAdapter.tagTop5 = if (showAuthor) {
                if (startTime != null && endTime != null) {
                    StatisticsService.getTagTop5InRange(startTime, endTime)
                } else {
                    StatisticsService.getTagTop5()
                }
            } else {
                emptyList()
            }

            statisticsAdapter.showAnalysis = true
        } catch (_: Exception) {
            statisticsAdapter.showAnalysis = false
        }
    }


    // 加载月度热力图数据
    private suspend fun loadMonthlyHeatmapData(month: String) {
        try {
            // 使用StatisticsService获取热力图数据
            val dailyReadTimes = StatisticsService.getMonthlyReadHeatmapData(month)

            // 转换DailyReadTime到HeatmapDayData
            val heatmapData = dailyReadTimes.map { dailyReadTime ->
                val date = dailyReadTime.date
                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = sdf.parse(date) ?: Date()

                HeatmapDayData(
                    date = date,
                    readMinutes = dailyReadTime.readMinutes,
                    dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                    weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH),
                    isCurrentMonth = true
                )
            }

            // 解析月份字符串获取年份和月份
            val monthParts = month.split("-")
            val year = monthParts[0].toInt()
            val monthValue = monthParts[1].toInt()

            monthlyHeatmapView.setMonthData(year, monthValue, heatmapData)

            // 确保月度热力图在设置数据后正确布局
            monthlyHeatmapView.post {
                monthlyHeatmapView.requestLayout()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 加载年度热力图数据
    private suspend fun loadYearlyHeatmapData(year: String) {
        try {
            // 使用StatisticsService获取热力图数据
            val dailyReadTimes = StatisticsService.getYearlyReadHeatmapData(year)

            // 转换DailyReadTime到HeatmapDayData
            val heatmapData = dailyReadTimes.map { dailyReadTime ->
                val date = dailyReadTime.date
                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = sdf.parse(date) ?: Date()

                HeatmapDayData(
                    date = date,
                    readMinutes = dailyReadTime.readMinutes,
                    dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                    weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH),
                    isCurrentMonth = true
                )
            }

            // 解析年份字符串获取年份
            val yearValue = year.toInt()

            yearlyHeatmapView.setYearData(yearValue, heatmapData)

            // 确保年度热力图在设置数据后正确布局
            yearlyHeatmapView.post {
                yearlyHeatmapView.forceRefresh()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.read_statistics, menu)
        // 添加生成按钮
        val generateMenuItem = menu.add(0, 106, 0, "生成")
        generateMenuItem.setIcon(R.drawable.ic_bookplate)
        generateMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        // 添加阅读类型过滤按钮
        val filterMenuItem = menu.add(0, 105, 0, "阅读类型")
        filterMenuItem.setIcon(R.drawable.ic_sort)
        filterMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export -> {
                exportStatistics()
                return true
            }
            106 -> {
                generateStatisticsCard()
                return true
            }
            105 -> {
                showReadTypeFilterDialog()
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun showDatePickerDialog() {
        when (currentType) {
            1 -> { // 每日统计
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        dailyDate.set(year, month, dayOfMonth)
                        updateCurrentPeriodText()
                        lifecycleScope.launch {
                            loadDailyStatisticsByDate()
                        }
                    },
                    dailyDate.get(Calendar.YEAR),
                    dailyDate.get(Calendar.MONTH),
                    dailyDate.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.show()
            }
            2 -> { // 每月统计
                // 使用月份选择器
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, month, _ ->
                        monthlyDate.set(year, month, 1)
                        updateCurrentPeriodText()
                        lifecycleScope.launch {
                            loadMonthlyStatisticsByMonth()
                        }
                    },
                    monthlyDate.get(Calendar.YEAR),
                    monthlyDate.get(Calendar.MONTH),
                    1
                )
                // 隐藏日期选择，只显示年月
                datePickerDialog.datePicker.findViewById<View>(
                    resources.getIdentifier("day", "id", "android")
                )?.visibility = View.GONE
                datePickerDialog.show()
            }
            3 -> { // 年度统计
                // 使用年份选择器
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, _, _ ->
                        yearlyDate.set(year, 0, 1)
                        updateCurrentPeriodText()
                        lifecycleScope.launch {
                            loadYearlyStatisticsByYear()
                        }
                    },
                    yearlyDate.get(Calendar.YEAR),
                    0,
                    1
                )
                // 隐藏日期和月份选择，只显示年份
                datePickerDialog.datePicker.findViewById<View>(
                    resources.getIdentifier("day", "id", "android")
                )?.visibility = View.GONE
                datePickerDialog.datePicker.findViewById<View>(
                    resources.getIdentifier("month", "id", "android")
                )?.visibility = View.GONE
                datePickerDialog.show()
            }
            4 -> { // 每周统计
                val datePickerDialog = DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        weeklyDate.set(year, month, dayOfMonth)
                        weeklyDate.firstDayOfWeek = Calendar.MONDAY
                        weeklyDate.minimalDaysInFirstWeek = 1
                        updateCurrentPeriodText()
                        lifecycleScope.launch {
                            loadWeeklyStatisticsByWeek()
                        }
                    },
                    weeklyDate.get(Calendar.YEAR),
                    weeklyDate.get(Calendar.MONTH),
                    weeklyDate.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.show()
            }
        }
    }

    private fun exportStatistics() {
        // 实现统计数据导出功能
        // 这里可以添加导出到CSV或其他格式的代码
    }

    private fun showReadTypeFilterDialog() {
        val items = listOf(
            "全部" to null,
            "阅读" to io.legado.app.constant.BookType.text,
            "听书" to io.legado.app.constant.BookType.audio,
            "看剧" to io.legado.app.constant.BookType.video
        )
        alert("选择阅读类型") {
            items(items.map { it.first }) { _, index: Int ->
                val (_, type) = items[index]
                currentReadType = type
                statisticsAdapter.currentReadType = type
                statisticsAdapter.currentType = currentType
                StatisticsCacheManager.clearAllCache()
                // 根据当前统计类型选择正确的加载方法
                // 切换类型时，确保数据立即刷新，不依赖缓存
                lifecycleScope.launch {
                    when (currentType) {
                        0 -> loadTotalStatistics()
                        1 -> loadDailyStatisticsByDate()
                        2 -> loadMonthlyStatisticsByMonth()
                        3 -> loadYearlyStatisticsByYear()
                        4 -> loadWeeklyStatisticsByWeek()
                    }
                }
            }
        }
    }

    /**
     * 确保RecyclerView在数据更新后重新计算其高度
     * 同时确保NestedScrollView始终可以滚动，不因内容高度而禁用滚动
     */
    private fun refreshRecyclerViewHeight() {
        // 刷新阅读总览RecyclerView
        binding.recyclerView?.post {
            val recyclerView = binding.recyclerView ?: return@post
            val adapter = recyclerView.adapter ?: return@post

            if (adapter.itemCount == 0) {
                recyclerView.layoutParams?.height = 0
                recyclerView.requestLayout()
                return@post
            }

            // 确保RecyclerView已经布局完成
            recyclerView.measure(
                View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
            )

            // 使用ViewTreeObserver确保在布局完成后计算高度
            recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    // 计算实际高度
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    var totalHeight = 0

                    for (i in 0 until adapter.itemCount) {
                        val view = layoutManager.findViewByPosition(i)
                        if (view != null) {
                            totalHeight += view.height
                        } else {
                            // 如果视图还没有创建，使用测量方法
                            val holder = recyclerView.adapter!!.createViewHolder(recyclerView, recyclerView.adapter!!.getItemViewType(i))
                            holder.itemView.measure(
                                View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.UNSPECIFIED
                            )
                            totalHeight += holder.itemView.measuredHeight
                        }
                    }

                    // 添加间距
                    val spacing = resources.getDimensionPixelSize(R.dimen.dimen_spacing_5dp)
                    totalHeight += spacing * (adapter.itemCount + 1) // 顶部和每个项目的底部间距

                    // 设置RecyclerView的高度为wrap_content，让它根据内容自动调整
                    recyclerView.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    recyclerView.requestLayout()
                }
            })
        }
    }

    private fun generateStatisticsCard() {
        lifecycleScope.launch {
            val statsList = statisticsAdapter.getItems()
            if (statsList.isEmpty()) {
                Toast.makeText(this@ReadStatisticsActivity, "暂无统计数据", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val aggregated = aggregateStatistics(statsList)
            val variables = buildStatisticsVariables(
                aggregated, currentTop10Data,
                statisticsAdapter.hourlyData,
                statisticsAdapter.continuousDays,
                statisticsAdapter.authorTop5,
                statisticsAdapter.tagTop5
            )

            val bitmap = withContext(Dispatchers.IO) {
                BookplateGenerator.generateStatistics(this@ReadStatisticsActivity, variables)
            }

            if (bitmap != null) {
                val periodName = when (currentType) {
                    0 -> "总计"
                    1 -> "每日_${dateFormat.format(dailyDate.time)}"
                    2 -> "每月_${monthFormat.format(monthlyDate.time)}"
                    3 -> "每年_${yearFormat.format(yearlyDate.time)}"
                    4 -> "每周_${getWeekStr(weeklyDate)}"
                    else -> "总计"
                }
                withContext(Dispatchers.Main) {
                    BookplateDialog.show(this@ReadStatisticsActivity, bitmap, "阅读统计_$periodName")
                }
            } else {
                Toast.makeText(this@ReadStatisticsActivity, "生成失败: ${BookplateHtmlRenderer.lastError ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun aggregateStatistics(stats: List<ReadStatistics>): ReadStatistics {
        if (stats.size == 1) return stats.first()
        var totalBookCount = 0
        var totalFinished = 0
        var totalAbandoned = 0
        var totalReviews = 0
        var totalWords = 0L
        var totalTime = 0L
        var totalReadDays = 0
        for (s in stats) {
            totalBookCount += s.bookCount
            totalFinished += s.finishedBookCount
            totalAbandoned += s.abandonedBookCount
            totalReviews += s.reviewCount
            totalWords += s.totalWords
            totalTime += s.totalTime
            totalReadDays += s.readDays
        }
        return ReadStatistics(
            bookCount = totalBookCount,
            finishedBookCount = totalFinished,
            abandonedBookCount = totalAbandoned,
            totalWords = totalWords,
            reviewCount = totalReviews,
            totalTime = totalTime,
            date = "",
            readDays = totalReadDays
        )
    }

    private fun buildStatisticsVariables(
        stats: ReadStatistics,
        top10: List<BookReadTimeRank>,
        hourlyData: List<HourReadTime>,
        continuousDays: Int,
        authorTop5: List<AuthorReadTime>,
        tagTop5: List<TagReadCount>
    ): Map<String, String> {
        val periodLabel = when (currentType) {
            0 -> "全部时间"
            1 -> dateFormat.format(dailyDate.time)
            2 -> monthFormat.format(monthlyDate.time)
            3 -> yearFormat.format(yearlyDate.time)
            4 -> getWeekRangeDisplay(weeklyDate)
            else -> "全部时间"
        }
        val periodTitle = when (currentType) {
            0 -> "阅读统计"
            1 -> "每日统计"
            2 -> "每月统计"
            3 -> "每年统计"
            4 -> "每周统计"
            else -> "阅读统计"
        }

        val days = stats.totalTime / (1000 * 60 * 60 * 24)
        val hours = stats.totalTime % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = stats.totalTime % (1000 * 60 * 60) / (1000 * 60)

        val pageTitle = when (currentType) {
            0 -> "阅读统计"
            else -> "$periodTitle · $periodLabel"
        }

        val wordsInWan = if (stats.totalWords >= 10000) {
            String.format("%.1f", stats.totalWords / 10000.0)
        } else {
            (stats.totalWords / 10000).toString()
        }

        val vars = mutableMapOf(
            "pageTitle" to pageTitle,
            "periodLabel" to periodLabel,
            "bookCount" to stats.bookCount.toString(),
            "finishedBookCount" to stats.finishedBookCount.toString(),
            "abandonedBookCount" to stats.abandonedBookCount.toString(),
            "reviewCount" to stats.reviewCount.toString(),
            "readDays" to stats.readDays.toString(),
            "totalWords" to wordsInWan,
            "timeDays" to days.toString(),
            "timeHours" to hours.toString(),
            "timeMinutes" to minutes.toString()
        )

        val top5 = top10.take(5)
        top5.forEachIndexed { i, rank ->
            val n = i + 1
            vars["top${n}Name"] = rank.bookName
            vars["top${n}Cover"] = rank.coverUrl ?: ""
            val h = rank.readTime / (1000 * 60 * 60)
            val m = rank.readTime % (1000 * 60 * 60) / (1000 * 60)
            vars["top${n}Time"] = if (h > 0) "${h}h${m}m" else "${m}m"
        }

        // 分析数据：时段偏好
        val periodSummary = StatisticsService.summarizeHourlyDistribution(hourlyData)
        if (periodSummary != null) {
            vars["peakPeriod"] = periodSummary.first
            vars["peakPeriodPct"] = periodSummary.second.toString()
        }

        // 分析数据：连续阅读天数
        vars["continuousDays"] = continuousDays.toString()

        // 分析数据：最爱作者 TOP5
        authorTop5.take(5).forEachIndexed { i, author ->
            val n = i + 1
            vars["authorTop${n}Name"] = author.author
            val h = author.totalTime / (1000 * 60 * 60)
            val m = author.totalTime % (1000 * 60 * 60) / (1000 * 60)
            vars["authorTop${n}Time"] = if (h > 0) "${h}h${m}m" else "${m}m"
        }

        // 分析数据：最爱类型 TOP5
        tagTop5.take(5).forEachIndexed { i, tag ->
            val n = i + 1
            vars["tagTop${n}Name"] = tag.tag
            vars["tagTop${n}Count"] = tag.bookCount.toString()
        }

        return vars
    }
}