package io.legado.app.ui.book.readingmemory

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
import java.util.Random
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.BookReview
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.databinding.ActivityBookReadingDetailBinding
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.annotation.BookAnnotationDialog
import io.legado.app.ui.book.readingdetail.adapter.AnnotationAdapter
import androidx.appcompat.app.AlertDialog
import io.legado.app.ui.book.readingdetail.adapter.ReviewAdapter
import io.legado.app.ui.book.review.BookReviewDialog
import io.legado.app.ui.book.read.page.provider.BookplateDrawer
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import splitties.init.appCtx
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.utils.TagColorUtils
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.observeEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 我的阅读详情页面
 * 复用BookReadingDetailActivity的布局和部分逻辑，但数据来源为我的阅读记录
 */
class ReadingMemoryDetailActivity : VMBaseActivity<ActivityBookReadingDetailBinding, ReadingMemoryDetailViewModel>() {

    override val binding by viewBinding(ActivityBookReadingDetailBinding::inflate)
    override val viewModel by viewModels<ReadingMemoryDetailViewModel>()
    
    private var memory: io.legado.app.data.entities.ReadingMemory? = null
    private lateinit var annotationAdapter: AnnotationAdapter
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var readingSessionAdapter: ReadingSessionAdapter
    private var isManualRefresh = false // 标记是否为手动刷新
    private var isSourceChanged = false // 标记是否为书源变更
    
    // 阅读会话适配器
    inner class ReadingSessionAdapter(private val sessions: List<ReadingMemoryDetailViewModel.MonthReadingSession>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        // 展开状态记录
        private val expandedPositions = mutableSetOf<Int>()
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val monthHeader: LinearLayout = itemView.findViewById(R.id.month_header)
            val tvMonthTitle: TextView = itemView.findViewById(R.id.tv_month_title)
            val tvMonthTotalTime: TextView = itemView.findViewById(R.id.tv_month_total_time)
            val ivExpandIcon: ImageView = itemView.findViewById(R.id.iv_expand_icon)
            val dailyList: LinearLayout = itemView.findViewById(R.id.daily_list)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reading_session_month, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ViewHolder) {
                val session = sessions[position]
                
                // 设置月份标题
                holder.tvMonthTitle.text = session.monthTitle
                
                // 设置月份总阅读时间
                holder.tvMonthTotalTime.text = "总时长: ${formatDuring(session.totalReadTime)}"
                
                // 设置展开状态
                val isExpanded = expandedPositions.contains(position)
                holder.dailyList.visibility = if (isExpanded) View.VISIBLE else View.GONE
                holder.ivExpandIcon.setImageResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
                
                // 清空每日列表并重新添加
                holder.dailyList.removeAllViews()
                session.dailySessions.forEach {
                    val dayView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.item_reading_session_day, holder.dailyList, false)
                    val tvDate = dayView.findViewById<TextView>(R.id.tv_date)
                    val tvReadTime = dayView.findViewById<TextView>(R.id.tv_read_time)
                    tvDate.text = it.date
                    tvReadTime.text = formatDuring(it.readTime)
                    holder.dailyList.addView(dayView)
                }
                
                // 设置点击事件
                holder.monthHeader.setOnClickListener {
                    if (expandedPositions.contains(position)) {
                        expandedPositions.remove(position)
                    } else {
                        expandedPositions.add(position)
                    }
                    notifyItemChanged(position)
                }
            }
        }
        
        override fun getItemCount(): Int {
            return sessions.size
        }
    }
    

    
    // 重写finish方法，禁用转换动画以避免TransitionChain错误
    override fun finish() {
        super.finish()
        @Suppress("Deprecated")
        overridePendingTransition(0, 0)
    }
    


    override fun onDestroy() {
        super.onDestroy()
        // 取消所有正在进行的协程任务，避免退出页面时显示评分更新取消提示
        viewModel.cancelAllJobs()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 设置主题颜色，与阅读详情页保持一致
        val cardColor = ThemeStore.backgroundCard(this)
        
        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent)
        )
        binding.vwBg.applyNavigationBarPadding()
        
        // 设置卡片背景色为background_card，与阅读详情页保持一致
        for (i in 0 until binding.contentLayout.childCount) {
            val child = binding.contentLayout.getChildAt(i)
            if (child is LinearLayout || child is androidx.constraintlayout.widget.ConstraintLayout) {
                child.setBackgroundColor(cardColor)
            } else if (child is androidx.cardview.widget.CardView) {
                child.setCardBackgroundColor(cardColor)
            }
        }
        
        // 初始化适配器
        annotationAdapter = AnnotationAdapter()
        reviewAdapter = ReviewAdapter()
        
        viewModel.memoryData.observe(this) { memory ->
            // 总是更新UI，确保显示最新数据
            this.memory = memory
            showMemoryInfo(memory)
        }
        
        viewModel.totalReadingTime.observe(this) { time ->
            // 转换时间字符串为Long
            val minutes = time.toLongOrNull() ?: 0
            val hours = minutes / 60
            val mins = minutes % 60
            
            // 更新UI
            if (hours > 0) {
                binding.tvTotalReadingHours.text = "$hours"
                binding.tvTotalReadingHours.visibility = View.VISIBLE
                binding.tvHoursUnit.text = "小时"
                binding.tvHoursUnit.visibility = View.VISIBLE
                binding.tvTotalReadingMinutes.text = "$mins"
                binding.tvMinutesUnit.text = "分钟"
                // 当显示小时时，分钟部分有4dp的左边距
                val layoutParams = binding.tvTotalReadingMinutes.layoutParams as LinearLayout.LayoutParams
                layoutParams.marginStart = 4 * resources.displayMetrics.density.toInt()
                binding.tvTotalReadingMinutes.layoutParams = layoutParams
            } else {
                binding.tvTotalReadingHours.visibility = View.GONE
                binding.tvHoursUnit.visibility = View.GONE
                binding.tvTotalReadingMinutes.text = "$mins"
                binding.tvMinutesUnit.text = "分钟"
                // 当不显示小时时，分钟部分左边距为0dp
                val layoutParams = binding.tvTotalReadingMinutes.layoutParams as LinearLayout.LayoutParams
                layoutParams.marginStart = 0
                binding.tvTotalReadingMinutes.layoutParams = layoutParams
            }
        }
        
        viewModel.readingDays.observe(this) { days ->
            binding.tvReadingDaysValue.text = days
        }
        
        viewModel.readingProgress.observe(this) { progress ->
            binding.tvReadingProgressValue.text = progress
            // 更新书籍信息部分的进度条
            val progressValue = progress.toIntOrNull() ?: 0
            binding.pbReadingProgress.progress = progressValue
        }
        
        viewModel.lastReadTime.observe(this) { lastTime ->
            binding.tvLastReadingTime.text = "上次${lastTime}"
        }
        
        viewModel.startReadingTime.observe(this) { startTime ->
            binding.tvStartReadingTime.text = startTime
        }
        
        viewModel.readChapters.observe(this) {
            binding.tvReadChapters.text = "已读章节 ${it}"
        }
        
        viewModel.annotationCount.observe(this) {
            binding.tvAnnotationCount.text = "${it}条书摘"
        }
        
        viewModel.reviewCount.observe(this) {
            binding.tvReviewCountValue.text = "${it}"
        }
        
        // 单日阅读最久观察者
        viewModel.maxDayReadHours.observe(this) { hours ->
            binding.tvMaxDayReadHours.text = "${hours}"
        }
        
        viewModel.maxDayReadMinutes.observe(this) { minutes ->
            binding.tvMaxDayReadMinutes.text = "${minutes}"
        }
        
        viewModel.maxDayReadDate.observe(this) { date ->
            binding.tvMaxDayReadDate.text = date
        }
        
        // 阅读总字数观察者
        viewModel.totalReadWords.observe(this) { words ->
            binding.tvTotalReadWordsValue.text = words
        }
        
        viewModel.remainingWords.observe(this) { remaining ->
            binding.tvRemainingWords.text = remaining
        }
        
        // 合并观察字数和分类信息，组合显示
        viewModel.wordCount.observe(this) { wordCount ->
            updateWordCountAndStatus(wordCount, viewModel.bookKind.value)
        }
        
        viewModel.bookKind.observe(this) {
            // 更新字数和状态显示
            updateWordCountAndStatus(viewModel.wordCount.value, it)
        }
        
        viewModel.annotations.observe(this) { annotations ->
            showAnnotations(annotations)
        }
        
        viewModel.reviews.observe(this) { reviews ->
            showReviews(reviews)
        }
        
        // 阅读会话数据观察者
        viewModel.readingSessions.observe(this) { sessions ->
            showReadingSessions(sessions)
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            // 只有在手动刷新时才显示刷新标志
            if (isManualRefresh) {
                binding.swipeRefreshLayout.isRefreshing = isLoading
                // 刷新完成后重置标志
                if (!isLoading) {
                    isManualRefresh = false
                }
            }
        }
        

        
        // 观察标签数据变化
        viewModel.bookTags.observe(this) { tags ->
            showTags(tags)
            // 如果是书源变更操作，在标签更新后延迟重置标志
            if (isSourceChanged) {
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(500)
                    isSourceChanged = false
                }
            }
        }
        
        // 观察主角名数据变化
        viewModel.protagonists.observe(this) { protagonists ->
            showProtagonists(protagonists)
        }
        
        // 观察操作结果
        viewModel.operationResult.observe(this) { result ->
            if (result.isNotEmpty()) {
                toastOnUi(result)
            }
        }
        
        // 监听书源变更事件，根据书源分组决定是否更新字数和分类
        observeEvent<String>(EventBus.SOURCE_CHANGED) { bookUrl: String ->
            // 只有当事件中的书籍URL与当前记忆的书籍相同时才更新
            memory?.let { currentMemory ->
                if (currentMemory.bookUrl == bookUrl) {
                    // 设置更换书源标志
                    isSourceChanged = true
                    // 重新加载书籍基本信息，ViewModel会根据书源分组决定是否更新字数和分类
                    viewModel.refreshBookDataOnly()
                }
            }
        }
        
        // 监听标签更新事件，确保标签变化时能及时刷新
        observeEvent<String>(EventBus.TAGS_UPDATED) { bookUrl: String ->
            // 只有当事件中的书籍URL与当前记忆的书籍相同时才更新
            memory?.let { currentMemory ->
                if (currentMemory.bookUrl == bookUrl) {
                    // 重新加载书籍标签
                    viewModel.loadBookTags()
                }
            }
        }
        
        // 监听书籍信息更新事件，刷新页面数据
        observeEvent<String>(EventBus.MEMORY_BOOK_INFO_UPDATED) { bookUrl: String ->
            // 只有当事件中的书籍URL与当前记忆的书籍相同时才更新
            memory?.let { currentMemory ->
                if (currentMemory.bookUrl == bookUrl) {
                    // 刷新阅读详情页面数据
                    viewModel.refresh()
                }
            }
        }

        // 监听简介更新事件，当在BookInfoEditActivity中更新简介后刷新页面
        observeEvent<String>(EventBus.BOOK_INTRO_UPDATED) { bookUrl: String ->
            memory?.let { currentMemory ->
                if (currentMemory.bookUrl == bookUrl) {
                    viewModel.refresh()
                }
            }
        }

        val memoryId = intent.getStringExtra("memoryId") ?: ""
        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        viewModel.initData(memoryId, bookUrl, isSourceChanged)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reading_memory_detail, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_bookplate -> {
                memory?.let { memory ->
                    lifecycleScope.launch {
                        val book = withContext(Dispatchers.IO) {
                            appDb.bookDao.getBook(memory.bookUrl)
                        }
                        if (book != null) {
                            showBookplate(book)
                        } else {
                            toastOnUi("无法获取书籍信息")
                        }
                    }
                }
                return true
            }
            R.id.menu_edit -> {
                memory?.let { memory ->
                    val intent = android.content.Intent(this, BookInfoEditActivity::class.java)
                    intent.putExtra("bookUrl", memory.bookUrl)
                    startActivity(intent)
                }
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initViewEvent() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            isManualRefresh = true // 标记为手动刷新
            // 重新加载所有数据
            viewModel.refresh()
        }
        
        // 手动刷新书籍信息按钮 - 注释掉，因为布局中没有对应的按钮
        // binding.btnRefreshBookInfo.setOnClickListener {
        //     viewModel.refreshBookInfo()
        // }
        
        // 允许修改评分
        binding.ratingView.setOnRatingChangeListener { rating ->
            memory?.let { memory ->
                viewModel.updateMemoryRating(memory.id, rating)
            }
        }
        
        binding.tvIntro.setOnClickListener {
            // 跳转到书籍信息编辑页面统一编辑简介
            memory?.let { memory ->
                val intent = android.content.Intent(this, BookInfoEditActivity::class.java)
                intent.putExtra("bookUrl", memory.bookUrl)
                startActivity(intent)
            }
        }
        
        binding.ivAddReview.setOnClickListener {
            // 允许编辑书评
            memory?.let { memory ->
                showEditReviewDialog(memory)
            }
        }
        
        // 设置标签相关点击事件
        binding.bookTagsView?.setOnTagClickListener { tagName ->
            // 根据标签名称查找对应的 BookTag 对象
            val tag = viewModel.bookTags.value?.find { it.name == tagName }
            if (tag != null) {
                // 显示简单的选择对话框
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("标签操作")
                    .setMessage("是否移除标签\"${tag.name}\"？")
                    .setPositiveButton("移除") { _, _ ->
                        memory?.let { memory ->
                            viewModel.removeBookTag(memory.bookUrl, tag.id)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        
        // 设置右上角添加标签按钮的点击事件
        binding.ivAddTag?.setOnClickListener {
            // 显示添加标签对话框
            showAddTagDialog()
        }
        
        // 展开/收起按钮点击事件
        binding.tvExpandCollapse.setOnClickListener {
            toggleIntroExpandCollapse()
        }
        
        // 添加主角名按钮点击事件
        binding.ivAddProtagonist?.setOnClickListener {
            showAddProtagonistDialog()
        }
    }
    
    /**
     * 切换简介的展开/收起状态
     */
    private fun toggleIntroExpandCollapse() {
        val tvIntro = binding.tvIntro
        val tvExpandCollapse = binding.tvExpandCollapse
        
        if (tvIntro.maxLines == 5) {
            // 当前是收起状态，展开
            tvIntro.maxLines = Integer.MAX_VALUE
            tvExpandCollapse.text = getString(R.string.collapse)
        } else {
            // 当前是展开状态，收起
            tvIntro.maxLines = 5
            tvExpandCollapse.text = getString(R.string.expand_more)
        }
    }

    private fun showMemoryInfo(memory: io.legado.app.data.entities.ReadingMemory) {
        // 总是更新 UI，确保显示最新数据
        binding.tvBookName.text = memory.bookName
        binding.tvAuthor.text = memory.bookAuthor
        
        // 设置阅读状态
        val statusText = when (memory.readingStatus) {
            io.legado.app.constant.ReadingStatus.FINISHED -> "看完"
            io.legado.app.constant.ReadingStatus.READING -> "在看"
            io.legado.app.constant.ReadingStatus.ABANDONED -> "弃"
            else -> "待看"
        }
        binding.tvReadingStatus.text = statusText
        
        // 添加阅读状态点击事件
        binding.tvReadingStatus.setOnClickListener {
            showReadingStatusDialog(memory)
        }
        
        // 根据阅读状态和进度决定显示进度条还是读完时间
        if (memory.readingStatus == io.legado.app.constant.ReadingStatus.FINISHED && memory.finishReadTime > 0L) {
            // 隐藏进度条
            binding.pbReadingProgress.visibility = android.view.View.GONE
            // 显示读完时间
            binding.tvFinishReadTime.visibility = android.view.View.VISIBLE
            val finishTimeStr = java.text.SimpleDateFormat("yyyy 年 MM 月 dd 日", java.util.Locale.getDefault()).format(java.util.Date(memory.finishReadTime))
            binding.tvFinishReadTime.text = "读完·$finishTimeStr"
        } else {
            // 显示进度条
            binding.pbReadingProgress.visibility = android.view.View.VISIBLE
            // 隐藏读完时间
            binding.tvFinishReadTime.visibility = android.view.View.GONE
        }
        
        // 设置封面 - 使用 CoverImageView 的 load 方法
        // 首先尝试从数据库获取书籍信息，以便获取作者信息
        lifecycleScope.launch {
            try {
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(memory.bookUrl)
                }
                
                // 获取封面路径
                val coverUrl = memory.coverUrl?.takeIf { it.isNotEmpty() }
                    ?: book?.getDisplayCover()
                
                // 使用 CoverImageView 的 load 方法加载封面
                // 这样会自动使用自定义主题封面并在封面上显示书名和作者
                binding.ivCover.load(coverUrl, memory.bookName, memory.bookAuthor)
            } catch (e: Exception) {
                // 出错时使用基本参数加载
                binding.ivCover.load(memory.coverUrl, memory.bookName, memory.bookAuthor)
            }
        }
        
        // 设置评分
        binding.ratingView.setRating(memory.rating)
        
        // 设置书籍介绍 - 优先使用用户自定义的简介
        // 如果用户修改过简介，则使用阅读记录中的简介，否则使用书籍的简介
        lifecycleScope.launch {
            try {
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(memory.bookUrl)
                }
                val intro = if (memory.userModifiedIntro) {
                    // 用户修改过简介，使用阅读记录中的简介
                    memory.intro ?: getString(R.string.not_available)
                } else {
                    // 用户未修改过简介，使用书籍的简介
                    if (book != null) {
                        book.getDisplayIntro() ?: memory.intro ?: getString(R.string.not_available)
                    } else {
                        memory.intro ?: getString(R.string.not_available)
                    }
                }
                binding.tvIntro.text = intro
            } catch (e: Exception) {
                // 出错时使用我的阅读记录中的简介
                binding.tvIntro.text = memory.intro ?: getString(R.string.not_available)
            }
        }
    }

    private fun showAnnotations(annotations: List<Bookmark>) {
        // 总是更新UI，确保显示最新数据
        if (annotations.isEmpty()) {
            // 如果没有书摘，显示空状态
            binding.recyclerAnnotations.visibility = View.GONE
            // 可以添加一个空状态视图
        } else {
            binding.recyclerAnnotations.visibility = View.VISIBLE
            if (binding.recyclerAnnotations.adapter == null) {
                binding.recyclerAnnotations.layoutManager = LinearLayoutManager(this)
                binding.recyclerAnnotations.adapter = annotationAdapter
                
                // 设置点击事件，点击书摘项可以查看详情
                annotationAdapter.setOnItemClickListener { bookMark ->
                    showAnnotationDetailDialog(bookMark)
                }
            }
            
            annotationAdapter.submitList(annotations)
        }
    }

    private fun showReviews(reviews: List<BookReview>) {
        // 总是更新UI，确保显示最新数据
        if (reviews.isEmpty()) {
            // 如果没有书评，隐藏recyclerView
            binding.recyclerReviews.visibility = View.GONE
        } else {
            binding.recyclerReviews.visibility = View.VISIBLE
            
            if (binding.recyclerReviews.adapter == null) {
                binding.recyclerReviews.layoutManager = LinearLayoutManager(this)
                binding.recyclerReviews.adapter = reviewAdapter
                
                // 设置点击事件，点击书评项可以查看详情
                reviewAdapter.setOnItemClickListener { review, position ->
                    showReviewDetailDialog(review)
                }
            }
            
            reviewAdapter.submitList(reviews)
        }
    }
    
    private fun showReadingSessions(sessions: List<ReadingMemoryDetailViewModel.MonthReadingSession>) {
        val sessionCard = binding.contentLayout.findViewById<CardView>(R.id.reading_session_card)
        if (sessions.isEmpty()) {
            // 如果没有阅读会话，隐藏会话卡片
            sessionCard?.visibility = View.GONE
        } else {
            // 如果有阅读会话，显示会话卡片
            sessionCard?.visibility = View.VISIBLE
            
            if (binding.recyclerReadingSessions.adapter == null) {
                binding.recyclerReadingSessions.layoutManager = LinearLayoutManager(this)
                readingSessionAdapter = ReadingSessionAdapter(sessions)
                binding.recyclerReadingSessions.adapter = readingSessionAdapter
            } else {
                // 更新适配器数据
                // 注意：这里我们创建了新的适配器实例，因为当前适配器没有提供数据更新方法
                // 在实际应用中，建议为适配器添加setSessions方法以提高性能
                readingSessionAdapter = ReadingSessionAdapter(sessions)
                binding.recyclerReadingSessions.adapter = readingSessionAdapter
            }
        }
    }
    
    private fun showEditReviewDialog(memory: io.legado.app.data.entities.ReadingMemory) {
        // 使用与阅读详情页面相同的书评编辑对话框
        lifecycleScope.launch {
            try {
                // 获取当前书评内容
                val reviews = viewModel.getReviews(memory.bookUrl)
                val reviewContent = if (reviews.isNotEmpty()) {
                    reviews.first().reviewContent
                } else {
                    memory.reviewContent ?: ""
                }
                
                // 创建书评对象
                val bookReview = io.legado.app.data.entities.BookReview(
                    bookUrl = memory.bookUrl,
                    bookName = memory.bookName,
                    bookAuthor = memory.bookAuthor,
                    reviewContent = reviewContent,
                    createTime = System.currentTimeMillis(),
                    updateTime = System.currentTimeMillis()
                )
                
                // 创建书评编辑对话框
                val reviewDialog = BookReviewDialog()
                reviewDialog.arguments = Bundle().apply {
                    putInt("editPos", -1)
                    putParcelable("bookReview", bookReview)
                }
                reviewDialog.setOnReviewSavedListener(object : BookReviewDialog.OnReviewSavedListener {
                    override fun onReviewSaved() {
                        // 重新加载所有数据，确保显示最新状态
                        viewModel.refresh()
                    }
                })
                reviewDialog.show(supportFragmentManager, "bookReviewDialog")
            } catch (e: Exception) {
                // 出错时使用我的阅读记录中的书评
                val bookReview = io.legado.app.data.entities.BookReview(
                    bookUrl = memory.bookUrl,
                    bookName = memory.bookName,
                    bookAuthor = memory.bookAuthor,
                    reviewContent = memory.reviewContent ?: "",
                    createTime = System.currentTimeMillis(),
                    updateTime = System.currentTimeMillis()
                )
                
                val reviewDialog = BookReviewDialog(bookReview, -1)
                reviewDialog.setOnReviewSavedListener(object : BookReviewDialog.OnReviewSavedListener {
                    override fun onReviewSaved() {
                        // 重新加载所有数据，确保显示最新状态
                        viewModel.refresh()
                    }
                })
                reviewDialog.show(supportFragmentManager, "bookReviewDialog")
            }
        }
    }
    
    private fun showAnnotationDetailDialog(bookMark: Bookmark) {
        // 将Bookmark转换为BookAnnotation
        val annotation = BookAnnotation(
            time = bookMark.time,
            bookName = bookMark.bookName,
            bookAuthor = bookMark.bookAuthor,
            chapterIndex = bookMark.chapterIndex,
            chapterPos = bookMark.chapterPos,
            chapterName = bookMark.chapterName,
            bookText = bookMark.bookText,
            content = bookMark.content
        )
        
        // 使用BookAnnotationDialog显示书摘详情
        val dialog = BookAnnotationDialog(annotation, 0)
        dialog.show(supportFragmentManager, "bookAnnotationDialog")
    }
    
    private fun showReviewDetailDialog(review: BookReview) {
        // 显示书评编辑对话框，与点击加号的行为保持一致
        val reviewDialog = BookReviewDialog(review, 0)
        reviewDialog.setOnReviewSavedListener(object : BookReviewDialog.OnReviewSavedListener {
            override fun onReviewSaved() {
                // 重新加载所有数据，确保显示最新状态
                viewModel.refresh()
            }
        })
        reviewDialog.show(supportFragmentManager, "bookReviewDialog")
    }
    
    private fun showTags(tags: List<BookTag>) {
        binding.bookTagsView?.let { bookTagsView ->
            bookTagsView.setTags(tags)
            // 与阅读详情页保持一致，隐藏删除按钮
            bookTagsView.setDeleteButtonVisible(false)
            bookTagsView.setDeleteEnabled(false)
            // 不显示BookTagsView内置的添加按钮，使用右上角的添加按钮
            bookTagsView.setShowAddButton(false)
        }
    }
    
    /**
     * 显示主角名标签
     */
    private fun showProtagonists(protagonists: List<String>) {
        val container = binding.protagonistContainer
        container.removeAllViews()
        
        if (protagonists.isEmpty()) {
            // 如果没有主角名，显示提示文字
            val hintText = TextView(this)
            hintText.text = "未提取到主角名"
            hintText.textSize = 12f
            hintText.setTextColor(ContextCompat.getColor(this, R.color.secondaryText))
            container.addView(hintText)
        } else {
            // 直接在容器中添加标签
            for (protagonist in protagonists) {
                val tagView = createProtagonistTag(protagonist)
                container.addView(tagView)
            }
        }
    }
    
    /**
     * 创建主角名标签视图
     */
    private fun createProtagonistTag(name: String): View {
        val tagView = LinearLayout(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 8, 8)
        tagView.layoutParams = params
        tagView.orientation = LinearLayout.HORIZONTAL
        tagView.gravity = Gravity.CENTER_VERTICAL
        tagView.background = ContextCompat.getDrawable(this, R.drawable.selectable_item_background)
        
        // 设置标签背景
        val drawable = GradientDrawable()
        // 使用 ThemeStore 获取当前主题的强调色，支持自定义主题变化
        val accentColor = io.legado.app.lib.theme.ThemeStore.accentColor(this)
        drawable.setColor(accentColor)
        drawable.cornerRadius = 0f
        tagView.background = drawable
        
        // 设置内边距
        tagView.setPadding(12, 6, 12, 6)
        
        // 添加文字
        val textView = TextView(this)
        textView.text = name
        textView.textSize = 12f
        textView.setTextColor(Color.WHITE)
        textView.typeface = Typeface.DEFAULT_BOLD
        
        tagView.addView(textView)
        
        // 添加点击事件，用于删除主角名
        tagView.setOnClickListener {
            showDeleteProtagonistDialog(name)
        }
        
        return tagView
    }
    
    /**
     * 显示删除主角名对话框
     */
    private fun showDeleteProtagonistDialog(name: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("移除主角名")
            .setMessage("确定要移除主角名\"${name}\"吗？")
            .setPositiveButton("确定") { _, _ ->
                memory?.let { memory ->
                    viewModel.deleteProtagonist(memory.bookUrl, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 更新字数和状态显示
     */
    private fun updateWordCountAndStatus(wordCount: String?, kind: String?) {
        val displayText = when {
            wordCount.isNullOrEmpty() && (kind.isNullOrEmpty() || kind == "无") -> "状态未知"
            wordCount.isNullOrEmpty() -> when {
                kind.isNullOrEmpty() || kind == "无" -> "状态未知"
                kind.contains("完结") -> "完结"
                kind.contains("连载") -> "连载"
                else -> "状态未知"
            }
            kind.isNullOrEmpty() || kind == "无" -> "字数  $wordCount"
            kind.contains("完结") -> "完结 $wordCount"
            kind.contains("连载") -> "连载 $wordCount"
            else -> "字数  $wordCount"
        }
        binding.tvWordCount.text = displayText
    }
    
    /**
     * 将分类信息添加为标签
     * 只有当书源分组包含"正版"时才自动添加标签
     */
    private fun addKindAsTag(kind: String) {
        memory?.let { memory ->
            // 使用与阅读详情页面相同的processKindTags方法处理分类信息
            val processedTags = viewModel.processKindTags(kind)
            
            if (processedTags.isEmpty()) {
                return@let
            }
            
            // 在协程中处理所有标签操作，确保原子性
            lifecycleScope.launch {
                try {
                    // 获取书籍信息
                    val book = withContext(Dispatchers.IO) {
                        appDb.bookDao.getBook(memory.bookUrl)
                    }
                    
                    // 获取书源信息
                    val bookSource = book?.let { 
                        withContext(Dispatchers.IO) {
                            appDb.bookSourceDao.getBookSource(it.origin)
                        }
                    }
                    
                    // 判断书源是否属于"正版"分组
                    val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                    
                    // 如果不是正版书源，则不自动添加标签
                    if (!isOfficialSource) {
                        return@launch
                    }
                    
                    // 获取当前书籍已关联的标签
                    val existingTags = withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.getRelationsByBook(memory.bookUrl)
                    }
                    
                    // 获取已关联的标签ID集合
                    val existingTagIds = existingTags.map { it.tagId }.toSet()
                    
                    // 获取所有已存在的标签
                    val allTags = withContext(Dispatchers.IO) {
                        appDb.bookTagDao.getAll()
                    }
                    
                    // 创建标签名称到标签对象的映射
                    val tagMap = allTags.associateBy { it.name }
                    
                    // 检查标签是否已被用户移除过
                    val removedTags = withContext(Dispatchers.IO) {
                        appDb.removedAutoTagDao.getRemovedTagsByBook(memory.bookUrl)
                    }
                    val removedTagNames = removedTags.map { it.tagName }.toSet()
                    
                    // 为每个处理后的标签创建并添加到书籍
                    processedTags.forEach { (tagName, tagColor) ->
                        // 如果标签已被用户移除，跳过添加
                        if (removedTagNames.contains(tagName)) {
                            return@forEach
                        }
                        
                        // 查找或创建标签
                        var tag = tagMap[tagName]
                        
                        if (tag == null) {
                            // 检查标签是否已存在（可能在其他地方创建）
                            tag = withContext(Dispatchers.IO) {
                                appDb.bookTagDao.getTagByName(tagName)
                            }
                        }
                        
                        if (tag == null) {
                            // 创建新标签前再次检查，避免并发创建重复标签
                            val existingTag = withContext(Dispatchers.IO) {
                                appDb.bookTagDao.getTagByName(tagName)
                            }
                            
                            if (existingTag != null) {
                                tag = existingTag
                            } else {
                                // 创建新标签
                                val newTag = io.legado.app.data.entities.BookTag(
                                    name = tagName,
                                    color = tagColor,
                                    createTime = System.currentTimeMillis()
                                )
                                val tagId = withContext(Dispatchers.IO) {
                                    appDb.bookTagDao.insert(newTag)
                                }
                                tag = newTag.copy(id = tagId)
                            }
                        }
                        
                        // 检查是否已关联到书籍，避免重复关联
                        if (!existingTagIds.contains(tag!!.id)) {
                            // 再次检查关联关系是否存在，避免并发插入重复关联
                            val existingRelation = withContext(Dispatchers.IO) {
                                appDb.bookTagRelationDao.getRelation(memory.bookUrl, tag.id)
                            }
                            
                            if (existingRelation == null) {
                                // 创建关联关系
                                val relation = io.legado.app.data.entities.BookTagRelation(
                                    id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                                    bookUrl = memory.bookUrl,
                                    tagId = tag.id,
                                    createTime = System.currentTimeMillis()
                                )
                                
                                withContext(Dispatchers.IO) {
                                    appDb.bookTagRelationDao.insert(relation)
                                }
                            }
                        }
                    }
                    
                    // 重新加载书籍标签
                    viewModel.loadBookTags()
                } catch (e: Exception) {
                    toastOnUi("添加标签失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    private fun showAddTagDialog() {
        // 获取当前书籍URL
        memory?.bookUrl?.let { bookUrl ->
            io.legado.app.ui.widget.dialog.BookTagSelectDialog.show(
                fragmentManager = supportFragmentManager,
                bookUrl = bookUrl,
                callback = { bookTag ->
                    // 直接使用回调中的标签对象，添加到书籍
                    lifecycleScope.launch {
                        try {
                            // 与阅读详情页保持一致，传递bookUrl参数
                            viewModel.addBookTag(bookUrl, bookTag.id)
                        } catch (e: Exception) {
                            toastOnUi("添加标签失败: ${e.localizedMessage}")
                        }
                    }
                }
            )
        }
    }
    
    /**
     * 显示删除标签对话框
     */
    private fun showDeleteTagDialog(tag: io.legado.app.data.entities.BookTag) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("移除标签")
            .setMessage("确定要从本书中移除标签\"${tag.name}\"吗？\n\n注意：这只会移除本书与该标签的关联，不会删除标签本身。")
            .setPositiveButton("确定") { _, _ ->
                memory?.let { memory ->
                    viewModel.removeBookTag(memory.bookUrl, tag.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示添加主角名对话框
     */
    private fun showAddProtagonistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_protagonist, null)
        val editText = dialogView.findViewById<io.legado.app.lib.theme.view.ThemeEditText>(R.id.edit_protagonist_name)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        // 设置按钮点击事件
        dialog.setOnShowListener {
            // 获取主题主色
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            val colorPrimary = typedValue.data
            
            // 设置确认按钮文字颜色为主题主色
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
            // 设置取消按钮文字颜色为主题主色
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colorPrimary)
            
            // 设置确认按钮点击事件
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val protagonistName = editText.text.toString().trim()
                if (protagonistName.isNotEmpty()) {
                    // 保存用户自定义的主角名
                    memory?.let { memory ->
                        viewModel.addProtagonist(memory.bookUrl, protagonistName)
                    }
                }
                dialog.dismiss()
            }
            
            // 设置取消按钮点击事件
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dialog.dismiss()
            }
        }
        
        // 设置对话框背景色为自定义主题的背景色
        dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        dialog.show()
        
        // 获取对话框的根视图并设置背景色
        dialog.findViewById<View>(android.R.id.content)?.setBackgroundColor(backgroundColor)
    }
    
    /**
     * 显示阅读状态选择对话框
     */
    private fun showReadingStatusDialog(memory: io.legado.app.data.entities.ReadingMemory) {
        val currentStatus = memory.readingStatus
        val statusNames = arrayOf("待看", "在看", "看完", "弃")
        val statusValues = arrayOf(
            io.legado.app.constant.ReadingStatus.PENDING,
            io.legado.app.constant.ReadingStatus.READING,
            io.legado.app.constant.ReadingStatus.FINISHED,
            io.legado.app.constant.ReadingStatus.ABANDONED
        )
        
        val currentIndex = statusValues.indexOf(currentStatus)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择阅读状态")
            .setSingleChoiceItems(statusNames, currentIndex) { dialog, which ->
                val selectedStatus = statusValues[which]
                viewModel.updateReadingStatus(memory.id, selectedStatus)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 更新阅读状态
     */
    private fun updateReadingStatus(memoryId: String, status: io.legado.app.constant.ReadingStatus) {
        lifecycleScope.launch {
            try {
                // 更新阅读记忆的阅读状态
                viewModel.updateReadingStatus(memoryId, status)
                // 刷新数据
                viewModel.refresh()
            } catch (e: Exception) {
                toastOnUi("更新阅读状态失败: ${e.localizedMessage}")
            }
        }
    }
    
    private fun showBookplate(book: Book) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                createBookplateBitmap(book)
            }
            val imageView = android.widget.ImageView(this@ReadingMemoryDetailActivity)
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val padding = 16.dpToPx()
            imageView.setPadding(padding, padding, padding, padding)
            AlertDialog.Builder(this@ReadingMemoryDetailActivity)
                .setTitle("藏书票")
                .setView(imageView)
                .setPositiveButton("确定") { _, _ -> }
                .setNeutralButton("保存图片") { _, _ ->
                    saveBookplateAsImage(book)
                }
                .show()
        }
    }
    
    private fun saveBookplateAsImage(book: Book) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    createBookplateBitmap(book)
                }
                
                val savedPath = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap, "藏书票_${book.name}")
                }
                
                if (savedPath != null) {
                    toastOnUi("已保存到: $savedPath")
                } else {
                    toastOnUi("保存失败")
                }
            } catch (e: Exception) {
                toastOnUi("保存失败: ${e.localizedMessage}")
            }
        }
    }

    private fun createBookplateBitmap(book: Book): android.graphics.Bitmap {
        val bpWidth = 320.dpToPx()
        
        // 先测量内容高度，自适应
        var contentHeight = 0f
        contentHeight += 40.dpToPx() // 顶部留白
        contentHeight += 25.dpToPx() // 标题
        contentHeight += 22.dpToPx() // 开始时间
        contentHeight += 22.dpToPx() // 结束时间
        contentHeight += 12.dpToPx() // 虚线
        contentHeight += 22.dpToPx() // 书名
        contentHeight += 22.dpToPx() // 书摘条数
        contentHeight += 22.dpToPx() // 阅读时间
        contentHeight += 12.dpToPx() // 虚线
        contentHeight += 24.dpToPx() // 阅读评分
        
        if (!book.reviewContent.isNullOrBlank()) {
            contentHeight += 20.dpToPx() // 书评标题
            val maxWidth = bpWidth.toFloat() - 40.dpToPx()
            val measurePaint = android.graphics.Paint()
            measurePaint.textSize = 11.dpToPx().toFloat()
            val paragraphs = book.reviewContent!!.split("\n")
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    contentHeight += 10.dpToPx()
                    continue
                }
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    if (measurePaint.measureText(remainingText) <= maxWidth) {
                        contentHeight += 18.dpToPx()
                        break
                    } else {
                        var cutIndex = remainingText.length
                        while (cutIndex > 0 && measurePaint.measureText(remainingText.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        if (cutIndex == 0) cutIndex = 1
                        contentHeight += 18.dpToPx()
                        remainingText = remainingText.substring(cutIndex)
                    }
                }
            }
            contentHeight += 10.dpToPx() // 书评底部间距
            contentHeight += 12.dpToPx() // 虚线
        }
        
        contentHeight += 16.dpToPx() // 底部标语1
        contentHeight += 16.dpToPx() // 底部标语2
        contentHeight += 24.dpToPx() // 底部留白
        
        val bpHeight = contentHeight.toInt()
        
        val bitmap = android.graphics.Bitmap.createBitmap(bpWidth, bpHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val context = appCtx
        val primaryColor = io.legado.app.lib.theme.ThemeStore.primaryColor(context)
        val dividerColor = io.legado.app.lib.theme.ThemeStore.dividerColor(context)
        val textColor = io.legado.app.lib.theme.ThemeStore.colorSurface(context)
        
        val paint = io.legado.app.help.PaintPool.obtain()
        paint.isAntiAlias = true
        
        val left = 0f
        val top = 0f
        val right = bpWidth.toFloat()
        val bottom = bpHeight.toFloat()
        
        // 阴影
        paint.color = android.graphics.Color.parseColor("#22000000")
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx().toFloat(), top + 4.dpToPx().toFloat(), right + 4.dpToPx().toFloat(), bottom + 4.dpToPx().toFloat(), paint)
        
        // 背景（使用主题主色调）
        paint.color = primaryColor
        canvas.drawRect(left, top, right, bottom, paint)
        
        // 顶部和底部虚线边框
        paint.color = dividerColor
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3.dpToPx().toFloat()
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
        canvas.drawLine(left, top, right, top, paint)
        canvas.drawLine(left, bottom, right, bottom, paint)
        paint.pathEffect = null
        
        // 内容
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = textColor
        paint.typeface = android.graphics.Typeface.MONOSPACE
        
        var currentY = top + 40.dpToPx()
        
        // 标题：阅 读 凭 证
        paint.textSize = 18.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val titleText = "阅 读 凭 证"
        val titleWidth = paint.measureText(titleText)
        canvas.drawText(titleText, left + (right - left - titleWidth) / 2f, currentY, paint)
        
        currentY += 25.dpToPx()
        paint.textSize = 12.dpToPx().toFloat()
        paint.isFakeBoldText = false
        
        // 辅助函数：绘制一行（左右对齐）
        val drawRow = { rowTitle: String, value: String, y: Float ->
            paint.isFakeBoldText = false
            paint.color = textColor
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawText(rowTitle, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            canvas.drawText(value, right - 20.dpToPx() - valWidth, y, paint)
        }
        
        // 辅助函数：绘制虚线分隔线
        val drawDivider = { y: Float ->
            paint.color = dividerColor
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
            canvas.drawLine(left + 20.dpToPx(), y, right - 20.dpToPx(), y, paint)
            paint.pathEffect = null
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = textColor
        }
        
        // 辅助函数：绘制清单一行（带虚线连接）
        val drawListRow = { rowTitle: String, value: String, y: Float ->
            paint.color = textColor
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawText(rowTitle, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            val valueX = right - 20.dpToPx() - valWidth
            canvas.drawText(value, valueX, y, paint)
            val titleW = paint.measureText(rowTitle)
            val dashStartX = left + 20.dpToPx() + titleW + 5.dpToPx()
            val dashEndX = valueX - 5.dpToPx()
            if (dashEndX > dashStartX) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 1.dpToPx().toFloat()
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
                val textMiddleY = y - paint.textSize / 3f
                canvas.drawLine(dashStartX, textMiddleY, dashEndX, textMiddleY, paint)
                paint.pathEffect = null
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = textColor
            }
        }
        
        // 开始时间
        val startTime = if (book.firstReadTime > 0) book.firstReadTime else book.durChapterTime
        val addTimeStr = if (startTime > 0) java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(startTime)) else "____/__/__"
        drawRow("开始时间", addTimeStr, currentY)
        
        currentY += 22.dpToPx()
        
        // 结束时间
        val finishTimeStr = if (book.finishReadTime > 0) java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(book.finishReadTime)) else "____/__/__"
        drawRow("结束时间", finishTimeStr, currentY)
        
        currentY += 18.dpToPx()
        drawDivider(currentY)
        currentY += 18.dpToPx()
        
        // 书名（过长时截断）
        var displayBookName = book.name
        val maxNameWidth = right - left - 40.dpToPx() - paint.measureText("书名") - 25.dpToPx()
        if (paint.measureText(displayBookName) > maxNameWidth) {
            val ellipsizeWidth = paint.measureText("...")
            while (displayBookName.isNotEmpty() && paint.measureText(displayBookName) + ellipsizeWidth > maxNameWidth) {
                displayBookName = displayBookName.substring(0, displayBookName.length - 1)
            }
            displayBookName += "..."
        }
        drawListRow("书名", displayBookName, currentY)
        currentY += 22.dpToPx()
        
        // 书摘数量
        val noteCount = appDb.bookAnnotationDao.getByBook(book.name, book.author).size
        val noteStr = if (noteCount > 0) "$noteCount" else "?"
        drawListRow("书摘条数", noteStr, currentY)
        currentY += 22.dpToPx()
        
        // 阅读时间（实际累计阅读时长，不到一天换算成小时/分钟）
        val totalReadMillis = appDb.readSessionDao.getTotalReadTimeByUrlSync(book.bookUrl) ?: 0L
        val readingTimeStr = if (totalReadMillis > 0) {
            val totalMinutes = totalReadMillis / (60 * 1000L)
            val days = totalMinutes / (24 * 60)
            val hours = totalMinutes / 60
            when {
                days > 0 -> "${days} 天"
                hours > 0 -> "${hours} 小时"
                else -> "${totalMinutes} 分钟"
            }
        } else {
            "?"
        }
        drawListRow("阅读时间", readingTimeStr, currentY)
        
        currentY += 18.dpToPx()
        drawDivider(currentY)
        currentY += 18.dpToPx()
        
        // 阅读评分
        paint.isFakeBoldText = false
        paint.textSize = 12.dpToPx().toFloat()
        paint.color = textColor
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawText("阅读评分", left + 20.dpToPx(), currentY, paint)
        
        // 绘制星星：统一文字颜色，选中的实心★，未选中的空心☆，大小一致
        val starSize = 14.dpToPx().toFloat()
        val starGap = 4.dpToPx().toFloat()
        paint.textSize = starSize
        val totalStarWidth = starSize * 5 + starGap * 4
        var starDrawX = right - 20.dpToPx() - totalStarWidth
        
        for (i in 0 until 5) {
            paint.color = textColor
            paint.isFakeBoldText = false
            val starChar = if (book.rating >= i + 1) "★" else "☆"
            canvas.drawText(starChar, starDrawX, currentY, paint)
            starDrawX += starSize + starGap
        }
        paint.color = textColor
        paint.textSize = 12.dpToPx().toFloat()
        paint.isFakeBoldText = false
        
        currentY += 24.dpToPx()
        
        // 书评内容区域
        if (!book.reviewContent.isNullOrBlank()) {
            paint.isFakeBoldText = true
            paint.textSize = 12.dpToPx().toFloat()
            paint.color = textColor
            canvas.drawText("我的书评", left + 20.dpToPx(), currentY, paint)
            currentY += 20.dpToPx()
            
            paint.isFakeBoldText = false
            paint.textSize = 11.dpToPx().toFloat()
            paint.color = textColor
            val maxWidth = right - left - 40.dpToPx()
            val paragraphs = book.reviewContent!!.split("\n")
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    currentY += 10.dpToPx()
                    continue
                }
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    if (paint.measureText(remainingText) <= maxWidth) {
                        canvas.drawText(remainingText, left + 20.dpToPx(), currentY, paint)
                        currentY += 18.dpToPx()
                        break
                    } else {
                        var cutIndex = remainingText.length
                        while (cutIndex > 0 && paint.measureText(remainingText.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        if (cutIndex == 0) cutIndex = 1
                        canvas.drawText(remainingText.substring(0, cutIndex), left + 20.dpToPx(), currentY, paint)
                        currentY += 18.dpToPx()
                        remainingText = remainingText.substring(cutIndex)
                    }
                }
            }
            currentY += 10.dpToPx()
            drawDivider(currentY)
            currentY += 12.dpToPx()
        }
        
        // 底部标语
        paint.color = io.legado.app.utils.ColorUtils.withAlpha(textColor, 0.7f)
        paint.textSize = 9.dpToPx().toFloat()
        paint.isFakeBoldText = false
        val footer1 = "BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT."
        val footer2 = "烂书不退款，好书请多读。"
        val f1Width = paint.measureText(footer1)
        val f2Width = paint.measureText(footer2)
        canvas.drawText(footer1, left + (right - left - f1Width) / 2f, currentY, paint)
        currentY += 16.dpToPx()
        canvas.drawText(footer2, left + (right - left - f2Width) / 2f, currentY, paint)
        
        io.legado.app.help.PaintPool.recycle(paint)

        return bitmap
    }

    private fun wrapTextForBookplate(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }
            
            val words = paragraph.toCharArray()
            var currentLine = StringBuilder()
            
            for (char in words) {
                val testLine = currentLine.toString() + char
                val testWidth = paint.measureText(testLine)
                
                if (testWidth > maxWidth) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder()
                    }
                    if (paint.measureText(char.toString()) > maxWidth) {
                        lines.add(char.toString())
                    } else {
                        currentLine.append(char)
                    }
                } else {
                    currentLine.append(char)
                }
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        
        return lines
    }
    
    private fun saveBitmapToGallery(bitmap: android.graphics.Bitmap, fileName: String): String? {
        return try {
            val context = appCtx
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val legReadDir = java.io.File(imagesDir, "LegRead")
            if (!legReadDir.exists()) {
                legReadDir.mkdirs()
            }
            
            val safeFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = java.io.File(legReadDir, "${safeFileName}_${System.currentTimeMillis()}.png")
            
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(file)
            context.sendBroadcast(mediaScanIntent)
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 格式化阅读时长
     */
    private fun formatDuring(mss: Long): String {
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

}