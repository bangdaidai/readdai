package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup

import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.databinding.ActivityReadRecordBinding
import io.legado.app.databinding.ItemReadRecordBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.readRecord.DisplayMode
import io.legado.app.ui.book.readRecord.ReadRecordUiState
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.utils.applyNavigationBarPadding
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.widget.CoverCalendarView
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.StringUtils
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

class ReadRecordActivity : BaseActivity<ActivityReadRecordBinding>() {

    private val viewModel by viewModels<ReadRecordViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val readRecordRepository = ReadRecordRepository(appDb.readSessionDao)
                val bookRepository = BookRepository()
                return modelClass.cast(ReadRecordViewModel(readRecordRepository, bookRepository))!!
            }
        }
    }

    private val adapter by lazy { RecordAdapter(this) }
    private var displayMode
        get() = try {
            LocalConfig.getInt("readRecordDisplayMode", DisplayMode.AGGREGATE.ordinal)
        } catch (e: Exception) {
            DisplayMode.AGGREGATE.ordinal
        }
        set(value) {
            LocalConfig.edit().putInt("readRecordDisplayMode", value).apply()
        }
    private val searchView: io.legado.app.ui.widget.SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    override val binding by viewBinding(ActivityReadRecordBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        // 初始化视图模式
        viewModel.setDisplayMode(DisplayMode.values()[displayMode])
        // 监听 UI 状态变化
        lifecycleScope.launch {
            viewModel.uiState.collectLatest {
                updateUI(it)
            }
        }
        
        // 监听阅读记录更新事件（当封面更新时刷新）
        observeEvent<String>(EventBus.READ_SESSION_UPDATED) { bookName ->
            // 刷新阅读记录页面，以显示最新的封面
            refreshCoverCalendar()
            // 立即刷新数据，无需等待轮询（5 秒间隔）
            viewModel.refreshData()
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_enable_record)?.isChecked = AppConfig.enableReadRecord
        // 移除视图切换按钮的颜色设置，由TitleBar统一管理
        // 移除日历图标的颜色设置，由TitleBar统一管理
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read_record, menu)
        // 添加清除选项
        menu.add(0, 103, 0, "清除所有阅读记录")
        // 添加阅读类型过滤按钮
        val filterMenuItem = menu.add(0, 105, 0, "阅读类型")
        filterMenuItem.setIcon(R.drawable.ic_sort)
        filterMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        // 移除视图切换按钮
        menu.removeItem(R.id.menu_switch_view)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            103 -> {
                // 清除所有阅读记录
                alert(R.string.delete, R.string.sure_del) {
                    yesButton {
                        lifecycleScope.launch {
                            appDb.readSessionDao.clear()
                            appDb.readRecordDao.clear()
                        }
                    }
                    noButton()
                }
            }
            105 -> {
                // 阅读类型过滤
                showReadTypeFilterDialog()
            }
            R.id.menu_enable_record -> {
                AppConfig.enableReadRecord = !item.isChecked
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }



    private fun initView() {
        initSearchView()
        val coverCalendarView = binding.root.findViewById<CoverCalendarView>(R.id.cover_calendar_view)
        if (coverCalendarView != null) {
            coverCalendarView.onDateClickListener = { year, month, dayOfMonth ->
                val selectedDate = java.time.LocalDate.of(year, month, dayOfMonth)
                viewModel.setSelectedDate(selectedDate)
            }
            coverCalendarView.onMonthClickListener = {
                viewModel.setSelectedDate(null)
            }
            coverCalendarView.onMonthChanged = { year, month ->
                loadCoverCalendarData(year, month)
            }
            val now = java.time.LocalDate.now()
            coverCalendarView.goToMonth(now.year, now.monthValue)
            loadCoverCalendarData(now.year, now.monthValue)
        }
        binding.recyclerView.adapter = adapter
        adapter.setupSwipeToDelete(binding.recyclerView)
        binding.recyclerView.applyNavigationBarPadding()

        val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(this)
        val calendarSection = binding.root.findViewById<androidx.cardview.widget.CardView>(R.id.calendar_section)
        calendarSection?.setCardBackgroundColor(cardColor)
    }

    private fun loadCoverCalendarData(year: Int, month: Int) {
        val coverCalendarView = binding.root.findViewById<CoverCalendarView>(R.id.cover_calendar_view) ?: return
        lifecycleScope.launch(IO) {
            val dataList = viewModel.getCoverCalendarData(year, month)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                // setMonthData 内部已调用 updateCellSize()，无需额外刷新布局
                coverCalendarView.setMonthData(year, month, dataList)
            }
        }
    }

    private fun initSearchView() {
        searchView?.let {
            // 设置搜索框颜色为标题栏文字图标颜色
            val titleBarTextIconColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this)
            it.applyTint(titleBarTextIconColor)
            
            it.isSubmitButtonEnabled = true
            it.queryHint = getString(R.string.search)
            it.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    it.clearFocus()
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    viewModel.setSearchKey(newText)
                    return false
                }
            })
        }
    }

    private fun showReadTypeFilterDialog() {
        val items = listOf(
            "全部" to null,
            "阅读" to BookType.text,
            "听书" to BookType.audio,
            "看剧" to BookType.video
        )
        alert("选择阅读类型") {
            items(items.map { it.first }) { dialog, index ->
                val (_, type) = items[index]
                viewModel.setReadType(type)
                refreshCoverCalendar()
            }
        }
    }

    private fun refreshCoverCalendar() {
        val coverCalendarView = binding.root.findViewById<CoverCalendarView>(R.id.cover_calendar_view)
        if (coverCalendarView != null) {
            loadCoverCalendarData(coverCalendarView.getCurrentYear(), coverCalendarView.getCurrentMonth())
        }
    }



    inner class RecordAdapter(context: Context) :
        RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

        private val items: MutableList<Any> = mutableListOf()

        fun setupSwipeToDelete(recyclerView: RecyclerView) {
            val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                0,
                androidx.recyclerview.widget.ItemTouchHelper.LEFT
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    val item = items.getOrNull(position) ?: return
                    when (item) {
                        is ReadRecordDetail -> {
                            sureDelAlert(item, viewHolder)
                        }
                        is ReadSession -> {
                            sureDelAlert(item, viewHolder)
                        }
                        is ReadRecord -> {
                            sureDelAlert(item, viewHolder)
                        }
                    }
                }
            })
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        override fun getItemViewType(position: Int): Int {
            val item = items.getOrNull(position) ?: return 0
            return when (item) {
                is String -> {
                    if (item == "summary_card") {
                        1 // 阅读概览卡片
                    } else if (item == "view_switch_card") {
                        7 // 视图切换胶囊卡片
                    } else if (item == "无阅读记录") {
                        2 // 空状态
                    } else {
                        3 // 日期标题
                    }
                }
                is ReadRecordDetail -> 4 // 汇总视图项
                is ReadSession -> 5 // 时间线视图项
                is ReadRecord -> 6 // 最后阅读视图项
                else -> 0
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = when (viewType) {
                1 -> inflater.inflate(R.layout.item_reading_summary, parent, false)
                2 -> inflater.inflate(R.layout.item_empty_read_time_top10, parent, false)
                3 -> inflater.inflate(R.layout.item_date_header, parent, false)
                4 -> inflater.inflate(R.layout.item_read_record_detail, parent, false)
                5 -> inflater.inflate(R.layout.item_timeline_session, parent, false)
                6 -> inflater.inflate(R.layout.item_latest_read, parent, false)
                7 -> inflater.inflate(R.layout.view_mode_switch, parent, false)
                else -> inflater.inflate(R.layout.item_empty_read_time_top10, parent, false)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items.getOrNull(position) ?: return
            when (item) {
                is String -> {
                    if (item == "summary_card") {
                        bindSummaryCard(holder.itemView)
                    } else if (item == "view_switch_card") {
                        bindViewSwitchCard(holder.itemView)
                    } else if (item == "无阅读记录") {
                        bindEmpty(holder.itemView)
                    } else {
                        bindDateHeader(holder.itemView, item)
                    }
                }
                is ReadRecordDetail -> bindDetail(holder.itemView, item)
                is ReadSession -> bindTimeline(holder.itemView, item)
                is ReadRecord -> bindLatest(holder.itemView, item)
            }
            holder.itemView.setOnClickListener {
                when (item) {
                    is ReadRecordDetail, is ReadSession, is ReadRecord -> {
                        val bookName = when (item) {
                            is ReadRecordDetail -> item.bookName
                            is ReadSession -> item.bookName
                            is ReadRecord -> item.bookName
                            else -> return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            val book = withContext(IO) {
                                appDb.bookDao.findByName(bookName).firstOrNull()
                            }
                            if (book == null) {
                                SearchActivity.start(this@ReadRecordActivity, bookName)
                            } else {
                                startActivityForBook(book)
                            }
                        }
                    }
                }
            }

            // 长按编辑功能
            holder.itemView.setOnLongClickListener {
                when (item) {
                    is ReadRecordDetail -> showEditDialog(item)
                    is ReadSession -> showEditDialog(item)
                    is ReadRecord -> showEditDialog(item)
                }
                true
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        fun setItems(newItems: List<Any>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        private fun bindSummaryCard(itemView: View) {
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
            val tvBookCountPrefix = itemView.findViewById<TextView>(R.id.tv_book_count_prefix)
            val tvBookCount = itemView.findViewById<TextView>(R.id.tv_book_count)
            val tvBookCountSuffix = itemView.findViewById<TextView>(R.id.tv_book_count_suffix)
            val tvTotalTime = itemView.findViewById<TextView>(R.id.tv_total_time)
            if (tvTitle == null || tvBookCount == null || tvTotalTime == null) return

            // 设置卡片背景色为主题设置的背景色
            val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(itemView.context)
            if (itemView is com.google.android.material.card.MaterialCardView) {
                itemView.setCardBackgroundColor(cardColor)
            }

            val selectedDate = viewModel.uiState.value.selectedDate
            val readType = viewModel.uiState.value.readType
            
            if (selectedDate != null) {
                val dateKey = selectedDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val dailyDetails = viewModel.uiState.value.groupedRecords[dateKey] ?: emptyList()
                if (dailyDetails.isNotEmpty()) {
                    val distinctBooks = dailyDetails.map { it.bookName }.distinct()
                    val dailyTime = dailyDetails.sumOf { it.readTime }
                    when (readType) {
                        BookType.text -> {
                            tvTitle?.text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日阅读概览"))
                            tvBookCountPrefix?.text = "已读"
                            tvBookCount?.text = distinctBooks.size.toString()
                            tvBookCountSuffix?.text = "本书"
                            tvTotalTime?.text = "共阅读 ${formatDuring(dailyTime)}"
                        }
                        BookType.audio -> {
                            tvTitle?.text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日听书概览"))
                            tvBookCountPrefix?.text = "已听"
                            tvBookCount?.text = distinctBooks.size.toString()
                            tvBookCountSuffix?.text = "本书"
                            tvTotalTime?.text = "共听 ${formatDuring(dailyTime)}"
                        }
                        BookType.video -> {
                            tvTitle?.text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日看剧概览"))
                            tvBookCountPrefix?.text = "已看"
                            tvBookCount?.text = distinctBooks.size.toString()
                            tvBookCountSuffix?.text = "部剧"
                            tvTotalTime?.text = "共观看 ${formatDuring(dailyTime)}"
                        }
                        else -> {
                            tvTitle?.text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("M月d日阅读概览"))
                            tvBookCountPrefix?.text = "已读"
                            tvBookCount?.text = distinctBooks.size.toString()
                            tvBookCountSuffix?.text = "本书"
                            tvTotalTime?.text = "共阅读 ${formatDuring(dailyTime)}"
                        }
                    }
                }
            } else {
                val allBooksCount = viewModel.uiState.value.latestRecords.size
                val totalTime = viewModel.uiState.value.totalReadTime
                when (readType) {
                BookType.text -> {
                    tvTitle?.text = "累计阅读成就"
                    tvBookCountPrefix?.text = "已读"
                    tvBookCount?.text = allBooksCount.toString()
                    tvBookCountSuffix?.text = "本书"
                    tvTotalTime?.text = "共阅读 ${formatDuring(totalTime)}"
                }
                BookType.audio -> {
                    tvTitle?.text = "累计听书成就"
                    tvBookCountPrefix?.text = "已听"
                    tvBookCount?.text = allBooksCount.toString()
                    tvBookCountSuffix?.text = "本书"
                    tvTotalTime?.text = "共听 ${formatDuring(totalTime)}"
                }
                BookType.video -> {
                    tvTitle?.text = "累计观看成就"
                    tvBookCountPrefix?.text = "已看"
                    tvBookCount?.text = allBooksCount.toString()
                    tvBookCountSuffix?.text = "部剧"
                    tvTotalTime?.text = "共观看 ${formatDuring(totalTime)}"
                }
                    else -> {
                        tvTitle?.text = "累计阅读成就"
                        tvBookCountPrefix?.text = "已读"
                        tvBookCount?.text = allBooksCount.toString()
                        tvBookCountSuffix?.text = "本书"
                        tvTotalTime?.text = "共阅读 ${formatDuring(totalTime)}"
                    }
                }
            }

        }

        private fun bindViewSwitchCard(itemView: View) {
            val btnAggregate = itemView.findViewById<TextView>(R.id.btn_aggregate)
            val btnTimeline = itemView.findViewById<TextView>(R.id.btn_timeline)
            val btnLatest = itemView.findViewById<TextView>(R.id.btn_latest)

            val currentMode = viewModel.uiState.value.displayMode
            val selectedColor = itemView.context.accentColor
            val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(itemView.context)

            // 设置大胶囊容器背景色，保持胶囊形状
            val bgDrawable = itemView.background
            if (bgDrawable is android.graphics.drawable.GradientDrawable) {
                bgDrawable.setColor(cardColor)
            }

            // 设置汇总按钮
            btnAggregate?.let {
                if (currentMode == DisplayMode.AGGREGATE) {
                    it.setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    it.background = bgDrawable
                } else {
                    it.setTextColor(io.legado.app.lib.theme.ThemeStore.textColorSecondary(itemView.context))
                    it.setBackgroundResource(R.drawable.bg_reading_tab_unselected)
                }
                it.setOnClickListener {
                    viewModel.setDisplayMode(DisplayMode.AGGREGATE)
                    displayMode = DisplayMode.AGGREGATE.ordinal
                }
            }

            // 设置时间线按钮
            btnTimeline?.let {
                if (currentMode == DisplayMode.TIMELINE) {
                    it.setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    it.background = bgDrawable
                } else {
                    it.setTextColor(io.legado.app.lib.theme.ThemeStore.textColorSecondary(itemView.context))
                    it.setBackgroundResource(R.drawable.bg_reading_tab_unselected)
                }
                it.setOnClickListener {
                    viewModel.setDisplayMode(DisplayMode.TIMELINE)
                    displayMode = DisplayMode.TIMELINE.ordinal
                }
            }

            // 设置最后阅读按钮
            btnLatest?.let {
                if (currentMode == DisplayMode.LATEST) {
                    it.setTextColor(android.graphics.Color.WHITE)
                    val bgDrawable = resources.getDrawable(R.drawable.bg_reading_tab_selected, null).mutate()
                    bgDrawable.setTint(selectedColor)
                    it.background = bgDrawable
                } else {
                    it.setTextColor(io.legado.app.lib.theme.ThemeStore.textColorSecondary(itemView.context))
                    it.setBackgroundResource(R.drawable.bg_reading_tab_unselected)
                }
                it.setOnClickListener {
                    viewModel.setDisplayMode(DisplayMode.LATEST)
                    displayMode = DisplayMode.LATEST.ordinal
                }
            }
        }



        private fun bindEmpty(itemView: View) {
            val tvEmpty = itemView.findViewById<TextView>(R.id.tv_empty)
            tvEmpty?.text = "没有记录"
        }

        private fun bindDateHeader(itemView: View, date: String) {
            val tvDate = itemView.findViewById<TextView>(R.id.tv_date)
            val tvDailyTotal = itemView.findViewById<TextView>(R.id.tv_daily_total)
            if (tvDate == null || tvDailyTotal == null) return
            // 使用更正确的方式转换日期格式
            val dateText = try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateObj = format.parse(date) ?: return
                val inputDate = dateObj.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(inputDate, today)
                when (daysBetween) {
                    0L -> "今天"
                    1L -> "昨天"
                    in 2L..5L -> "${daysBetween}天前"
                    else -> date
                }
            } catch (e: Exception) {
                date
            }
            // 再次检查tvDate是否为null，确保在调用setText()方法之前不会发生NullPointerException
            tvDate?.let {
                it.text = dateText
            }
            // 计算当日总阅读时间
            val state = viewModel.uiState.value
            val dailyDetails = state.groupedRecords[date]
            val dailySessions = state.timelineRecords[date]
            val totalTime = when (viewModel.displayMode.value) {
                DisplayMode.AGGREGATE -> dailyDetails?.sumOf { it.readTime } ?: 0
                DisplayMode.TIMELINE -> dailySessions?.sumOf { it.duration } ?: 0
                else -> 0
            }
            // 再次检查tvDailyTotal是否为null，确保在调用setText()方法之前不会发生NullPointerException
            tvDailyTotal?.let {
                it.text = "已读 ${formatDuring(totalTime)}"
            }
        }

        private fun bindDetail(itemView: View, detail: ReadRecordDetail) {
            val ivCover = itemView.findViewById<CoverImageView>(R.id.iv_cover)
            val tvBookName = itemView.findViewById<TextView>(R.id.tv_book_name)
            val tvReadTime = itemView.findViewById<TextView>(R.id.tv_read_time)
            val tvLastRead = itemView.findViewById<TextView>(R.id.tv_last_read)
            if (tvBookName == null || tvReadTime == null || tvLastRead == null) return
            // 优先显示 displayName，如果没有则显示 bookName
            val displayName = detail.displayName.ifEmpty { detail.bookName }
            tvBookName?.let {
                it.text = displayName
            }
            tvReadTime?.let {
                it.text = "阅读时长: ${formatDuring(detail.readTime)}"
            }
            tvLastRead?.let {
                it.text = "最后阅读: ${formatDate(detail.lastReadTime)}"
            }
            ivCover?.tag = detail.bookName
            lifecycleScope.launch {
                val coverPath = detail.coverUrl.ifEmpty { viewModel.getBookCover(detail.bookName) }
                // 从数据库获取书籍信息以获取作者
                val book = withContext(IO) {
                    appDb.bookDao.findByName(detail.bookName).firstOrNull()
                }
                val author = book?.author
                ivCover?.takeIf { it.tag == detail.bookName }?.let {
                    it.load(coverPath, displayName, author)
                }
            }
        }

        private fun bindTimeline(itemView: View, session: ReadSession) {
            val tvTime = itemView.findViewById<TextView>(R.id.tv_time)
            val ivCover = itemView.findViewById<CoverImageView>(R.id.iv_cover)
            val tvBookName = itemView.findViewById<TextView>(R.id.tv_book_name)
            val tvChapter = itemView.findViewById<TextView>(R.id.tv_chapter)
            val tvDuration = itemView.findViewById<TextView>(R.id.tv_duration)
            val timelineLine = itemView.findViewById<View>(R.id.timeline_line)
            val timelineDot = itemView.findViewById<View>(R.id.timeline_dot)
            if (tvTime == null || tvBookName == null || tvChapter == null || tvDuration == null) return
            // 优先显示 displayName，如果没有则显示 bookName
            val displayName = session.displayName.ifEmpty { session.bookName }
            tvTime?.let {
                it.text = "${formatTime(session.startTime)}\n${formatTime(session.endTime)}"
            }
            tvBookName?.let {
                it.text = displayName
            }
            tvDuration?.let {
                it.text = "阅读时长: ${formatDuring(session.duration)}"
            }
            // 控制时间线显示
            val position = items.indexOf(session)
            val previousItem = items.getOrNull(position - 1)
            val nextItem = items.getOrNull(position + 1)

            // 检查是否是当天的第一个项目
            val isFirstOfDay = previousItem !is ReadSession
            // 检查是否是当天的最后一个项目
            val isLastOfDay = nextItem !is ReadSession

            // 获取时间线容器
            val timelineContainer = itemView.findViewById<View>(R.id.timeline_container)

            // 设置时间线颜色为主题强调色
            val accentColor = try {
                io.legado.app.lib.theme.ThemeStore.accentColor
            } catch (e: Exception) {
                null
            }

            // 设置时间线的线的颜色（带透明度）
            accentColor?.let {
                // 在主题强调色的基础上加60%的透明度
                val transparentColor = android.graphics.Color.argb(102, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
                timelineLine?.setBackgroundColor(transparentColor)
            }

            // 设置时间线圆点颜色为主题强调色
            timelineDot?.let {
                accentColor?.let { color ->
                    // 创建圆形drawable并设置颜色，保持圆形形状
                    val circleDrawable = android.graphics.drawable.GradientDrawable()
                    circleDrawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                    circleDrawable.setColor(color)
                    circleDrawable.setSize(8, 8)
                    it.background = circleDrawable
                } ?: run {
                    it.setBackgroundResource(R.drawable.timeline_dot)
                }
            }

            // 设置时间线容器的布局参数，添加负margin使其延伸
            if (timelineContainer != null) {
                val containerParams = timelineContainer.layoutParams as LinearLayout.LayoutParams

                // 设置顶部负margin，使时间线向上延伸
                if (!isFirstOfDay) {
                    containerParams.setMargins(0, -12, 0, 0) // 负margin向上延伸，12dp是item的paddingVertical
                } else {
                    containerParams.setMargins(0, 0, 0, 0)
                }

                // 设置底部负margin，使时间线向下延伸
                if (!isLastOfDay) {
                    containerParams.bottomMargin = -12 // 负margin向下延伸，12dp是item的paddingVertical
                } else {
                    containerParams.bottomMargin = 0
                }

                timelineContainer.layoutParams = containerParams
            }

            ivCover?.tag = session.bookName
            tvChapter?.tag = session.bookName
            lifecycleScope.launch {
                val coverPath = session.coverUrl.ifEmpty { viewModel.getBookCover(session.bookName) }
                ivCover?.takeIf { it.tag == session.bookName }?.let {
                    it.load(coverPath, displayName, session.author)
                }
                tvChapter?.takeIf { it.tag == session.bookName }?.let {
                    it.text = session.durChapterTitle.ifEmpty { "未记录章节" }
                }
            }
        }

        private fun bindLatest(itemView: View, record: ReadRecord) {
            val ivCover = itemView.findViewById<CoverImageView>(R.id.iv_cover)
            val tvBookName = itemView.findViewById<TextView>(R.id.tv_book_name)
            val tvTotalTime = itemView.findViewById<TextView>(R.id.tv_total_time)
            val tvLastRead = itemView.findViewById<TextView>(R.id.tv_last_read)
            if (tvBookName == null || tvTotalTime == null || tvLastRead == null) return
            // 优先显示 displayName，如果没有则显示 bookName
            val displayName = record.displayName.ifEmpty { record.bookName }
            tvBookName?.let {
                it.text = displayName
            }
            tvTotalTime?.let {
                it.text = "总时长: ${formatDuring(record.readTime)}"
            }
            tvLastRead?.let {
                it.text = "最后阅读: ${formatDateTime(record.lastRead)}"
            }
            ivCover?.tag = record.bookName
            lifecycleScope.launch {
                val coverPath = record.coverUrl.ifEmpty { viewModel.getBookCover(record.bookName) }
                // 从数据库获取书籍信息以获取作者
                val book = withContext(IO) {
                    appDb.bookDao.findByName(record.bookName).firstOrNull()
                }
                val author = book?.author
                ivCover?.takeIf { it.tag == record.bookName }?.let {
                    it.load(coverPath, displayName, author)
                }
            }
        }

        private fun bindBookCovers(container: LinearLayout, bookNames: List<String>) {
            container.removeAllViews()
            bookNames.forEachIndexed { index, bookName ->
                lifecycleScope.launch {
                    val coverPath = viewModel.getBookCover(bookName)
                    val cardView = androidx.cardview.widget.CardView(this@ReadRecordActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            48,
                            72
                        ).apply {
                            setMargins(-12 * index, 0, 0, 0)
                        }
                        radius = 4f
                        cardElevation = 0f
                        preventCornerOverlap = false
                        rotation = if (index % 2 == 0) 3f else -3f
                        elevation = (bookNames.size - index).toFloat()
                    }
                    val coverView = ImageView(this@ReadRecordActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = resources.getDrawable(android.R.color.transparent, null)
                    }
                    cardView.addView(coverView)
                    ImageLoader.load(this@ReadRecordActivity, coverPath ?: "")
                        .placeholder(R.drawable.ic_book)
                        .error(R.drawable.ic_book)
                        .into(coverView)
                    container.addView(cardView)
                }
            }
        }

        private fun sureDelAlert(item: ReadRecordDetail, viewHolder: RecyclerView.ViewHolder) {
            alert(R.string.delete) {
                setMessage(getString(R.string.sure_del_any, item.bookName))
                yesButton {
                    lifecycleScope.launch {
                        viewModel.deleteDetail(item)
                    }
                }
                noButton {
                    binding.recyclerView.adapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                }
            }
        }

        private fun sureDelAlert(item: ReadSession, viewHolder: RecyclerView.ViewHolder) {
            alert(R.string.delete) {
                setMessage(getString(R.string.sure_del_merged_session))
                yesButton {
                    lifecycleScope.launch {
                        viewModel.deleteMergedSession(item)
                    }
                }
                noButton {
                    binding.recyclerView.adapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                }
            }
        }

        private fun sureDelAlert(item: ReadRecord, viewHolder: RecyclerView.ViewHolder) {
            alert(R.string.delete) {
                setMessage(getString(R.string.sure_del_any, item.bookName))
                yesButton {
                    lifecycleScope.launch {
                        viewModel.deleteReadRecord(item)
                    }
                }
                noButton {
                    binding.recyclerView.adapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                }
            }
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    }

    /**
     * 显示编辑对话框 - 汇总视图 (ReadRecordDetail)
     * 可编辑：显示名称、封面
     */
    private fun showEditDialog(detail: ReadRecordDetail) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etDisplayName = EditText(this).apply {
            hint = "显示名称"
            setText(detail.displayName.ifEmpty { detail.bookName })
        }

        val etCoverUrl = EditText(this).apply {
            hint = "封面URL"
            setText(detail.coverUrl)
        }

        layout.addView(etDisplayName)
        layout.addView(etCoverUrl)

        AlertDialog.Builder(this)
            .setTitle("编辑阅读记录")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val newDisplayName = etDisplayName.text.toString().trim()
                val newCoverUrl = etCoverUrl.text.toString().trim()
                val bookName = detail.bookName

                // 更新封面
                if (newCoverUrl != detail.coverUrl) {
                    viewModel.updateCoverUrl(bookName, newCoverUrl)
                }
                // 更新显示名称
                if (newDisplayName != detail.displayName) {
                    viewModel.updateDisplayName(bookName, newDisplayName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示编辑对话框 - 时间线视图 (ReadSession)
     * 可编辑：显示名称、章节名、封面
     */
    private fun showEditDialog(session: ReadSession) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etDisplayName = EditText(this).apply {
            hint = "显示名称"
            setText(session.displayName.ifEmpty { session.bookName })
        }

        val etChapterTitle = EditText(this).apply {
            hint = "章节名"
            setText(session.durChapterTitle)
        }

        val etCoverUrl = EditText(this).apply {
            hint = "封面URL"
            setText(session.coverUrl)
        }

        layout.addView(etDisplayName)
        layout.addView(etChapterTitle)
        layout.addView(etCoverUrl)

        AlertDialog.Builder(this)
            .setTitle("编辑阅读记录")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val newDisplayName = etDisplayName.text.toString().trim()
                val newChapterTitle = etChapterTitle.text.toString().trim()
                val newCoverUrl = etCoverUrl.text.toString().trim()
                val bookName = session.bookName

                // 更新章节
                if (newChapterTitle != session.durChapterTitle) {
                    viewModel.updateChapterTitle(bookName, newChapterTitle, session.startTime, session.endTime)
                }
                // 更新封面
                if (newCoverUrl != session.coverUrl) {
                    viewModel.updateCoverUrl(bookName, newCoverUrl)
                }
                // 更新显示名称
                if (newDisplayName != session.displayName) {
                    viewModel.updateDisplayName(bookName, newDisplayName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示编辑对话框 - 最后阅读视图 (ReadRecord)
     * 可编辑：显示名称、封面
     */
    private fun showEditDialog(record: ReadRecord) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etDisplayName = EditText(this).apply {
            hint = "显示名称"
            setText(record.displayName.ifEmpty { record.bookName })
        }

        val etCoverUrl = EditText(this).apply {
            hint = "封面URL"
            setText(record.coverUrl)
        }

        layout.addView(etDisplayName)
        layout.addView(etCoverUrl)

        AlertDialog.Builder(this)
            .setTitle("编辑阅读记录")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val newDisplayName = etDisplayName.text.toString().trim()
                val newCoverUrl = etCoverUrl.text.toString().trim()
                val bookName = record.bookName

                // 更新封面
                if (newCoverUrl != record.coverUrl) {
                    viewModel.updateCoverUrl(bookName, newCoverUrl)
                }
                // 更新显示名称
                if (newDisplayName != record.displayName) {
                    viewModel.updateDisplayName(bookName, newDisplayName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedDate = java.time.LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
                viewModel.setSelectedDate(selectedDate)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    private fun updateUI(state: ReadRecordUiState) {
        // 显示加载状态
        if (state.isLoading) {
            binding.recyclerView.visibility = View.GONE
            return
        } else {
            binding.recyclerView.visibility = View.VISIBLE
        }

        // 根据当前显示模式更新列表
        val items = mutableListOf<Any>()

        // 添加阅读概览卡片
        if (state.latestRecords.isNotEmpty()) {
            items.add("summary_card")
            // 添加视图切换胶囊卡片
            items.add("view_switch_card")
        }

        when (state.displayMode) {
            DisplayMode.AGGREGATE -> {
                // 显示汇总视图
                if (state.groupedRecords.isEmpty()) {
                    items.add("无阅读记录")
                } else {
                    state.groupedRecords.forEach { (date, details) ->
                        items.add(date) // 添加日期作为分组标题
                        items.addAll(details)
                    }
                }
            }
            DisplayMode.TIMELINE -> {
                // 显示时间线视图
                if (state.timelineRecords.isEmpty()) {
                    items.add("无阅读记录")
                } else {
                    state.timelineRecords.forEach { (date, sessions) ->
                        items.add(date) // 添加日期作为分组标题
                        items.addAll(sessions)
                    }
                }
            }
            DisplayMode.LATEST -> {
                // 显示最后阅读视图
                if (state.latestRecords.isEmpty()) {
                    items.add("无阅读记录")
                } else {
                    items.addAll(state.latestRecords)
                }
            }
        }

        // 添加平滑过渡动画
        binding.recyclerView.apply {
            alpha = 0.5f
            animate().alpha(1.0f).duration = 300
        }

        adapter.setItems(items)
    }

    fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val d = if (days > 0) "${days}天" else ""
        val h = if (hours > 0) "${hours}小时" else ""
        val m = if (minutes > 0) "${minutes}分钟" else ""
        val s = if (seconds > 0) "${seconds}秒" else ""
        var time = "$d$h$m$s"
        if (time.isBlank()) {
            time = "0秒"
        }
        return time
    }

    fun formatTime(time: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }

    fun formatDate(time: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(time))
    }

    fun formatDateTime(time: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }

}