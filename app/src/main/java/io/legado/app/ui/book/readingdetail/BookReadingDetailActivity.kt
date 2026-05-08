/*
 * BookReadingDetailActivity 已被 BookReadingMemory 模块取代
 * 保留此文件仅作为备份，实际功能已迁移到 ReadingMemoryDetailActivity
 */
package io.legado.app.ui.book.readingdetail

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ActivityBookReadingDetailBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.readingdetail.adapter.AnnotationAdapter
import io.legado.app.ui.book.readingdetail.adapter.ReviewAdapter
import io.legado.app.ui.book.annotation.BookAnnotationDialog
import io.legado.app.ui.book.review.BookReviewDialog
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.data.entities.BookReview
import io.legado.app.data.entities.BookTag
import io.legado.app.ui.widget.dialog.BookTagEditDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import androidx.appcompat.app.AlertDialog
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.book.BookInfoSyncHelper
import io.legado.app.model.BookCover

import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.utils.toastOnUi
import io.legado.app.constant.EventBus
import io.legado.app.utils.postEvent
import io.legado.app.utils.observeEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import io.legado.app.ui.widget.anima.explosion_field.ExplosionField
import io.legado.app.ui.widget.anima.explosion_field.ExplosionView

class BookReadingDetailActivity : VMBaseActivity<ActivityBookReadingDetailBinding, BookReadingDetailViewModel>(), BookReviewDialog.OnReviewSavedListener {

    override val binding by viewBinding(ActivityBookReadingDetailBinding::inflate)
    override val viewModel by viewModels<BookReadingDetailViewModel>()
    
    private var book: Book? = null
    private lateinit var annotationAdapter: AnnotationAdapter
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var readingSessionAdapter: ReadingSessionAdapter
    private var isManualRefresh = false // 标记是否为手动刷新
    private var isSourceChanged = false // 标记是否是更换书源后的更新
    private var isTagUpdating = false // 标记是否正在更新标签
    private val explosionField by lazy { ExplosionField.attach2Window(this) }

    // 阅读会话适配器
    inner class ReadingSessionAdapter(private val sessions: List<BookReadingDetailViewModel.MonthReadingSession>) : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        
        // 展开状态记录
        private val expandedPositions = mutableSetOf<Int>()
        
        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val monthHeader: LinearLayout = itemView.findViewById(R.id.month_header)
            val tvMonthTitle: TextView = itemView.findViewById(R.id.tv_month_title)
            val tvMonthTotalTime: TextView = itemView.findViewById(R.id.tv_month_total_time)
            val ivExpandIcon: ImageView = itemView.findViewById(R.id.iv_expand_icon)
            val dailyList: LinearLayout = itemView.findViewById(R.id.daily_list)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_reading_session_month, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
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
                session.dailySessions.forEach { dailySession ->
                    val dayView = layoutInflater.inflate(R.layout.item_reading_session_day, holder.dailyList, false)
                    val tvDate = dayView.findViewById<TextView>(R.id.tv_date)
                    val tvReadTime = dayView.findViewById<TextView>(R.id.tv_read_time)
                    tvDate.text = dailySession.date
                    tvReadTime.text = formatDuring(dailySession.readTime)
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

    // 展开/收起状态管理
    private val expandedMonths = mutableSetOf<String>()
    
    // 重写finish方法，禁用转换动画以避免TransitionChain错误
    override fun finish() {
        super.finish()
        @Suppress("Deprecated")
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        // 每次回到该页面时，重新加载书籍数据，确保显示最新信息
        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        if (bookUrl.isNotBlank()) {
            viewModel.initData(bookUrl)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 设置主题颜色
        val cardColor = ThemeStore.backgroundCard(this)
        
        // 设置卡片背景色
        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent)
        )
        
        // 设置各个卡片的背景色
        // 遍历contentLayout的所有子视图，设置卡片背景色
        for (i in 0 until binding.contentLayout.childCount) {
            val child = binding.contentLayout.getChildAt(i)
            if (child is LinearLayout || child is androidx.constraintlayout.widget.ConstraintLayout) {
                child.setBackgroundColor(cardColor)
            } else if (child is androidx.cardview.widget.CardView) {
                child.setCardBackgroundColor(cardColor)
            }
        }
        
        binding.vwBg.applyNavigationBarPadding()
        
        // 初始化适配器
        annotationAdapter = AnnotationAdapter()
        reviewAdapter = ReviewAdapter()
        
        viewModel.bookData.observe(this) { book ->
            // 总是更新UI，确保显示最新数据
            this.book = book
            showBookInfo(book)
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
        
        viewModel.lastReadTime.observe(this) { it ->
            binding.tvLastReadingTime.text = "上次${it}"
        }
        
        viewModel.startReadingTime.observe(this) { startTime ->
            binding.tvStartReadingTime.text = startTime
        }

        viewModel.readChapters.observe(this) { chapters ->
            binding.tvReadChapters.text = "已读章节 $chapters"
        }
        
        // 合并观察字数和分类信息，组合显示
        viewModel.wordCount.observe(this) { wordCount ->
            updateWordCountAndStatus(wordCount, viewModel.bookKind.value)
        }
        
        viewModel.bookKind.observe(this) { kind ->
            // 更新字数和状态显示
            updateWordCountAndStatus(viewModel.wordCount.value, kind)
        }
        
        viewModel.annotations.observe(this) { annotations ->
            showAnnotations(annotations)
        }
        
        viewModel.reviewCount.observe(this) {
            binding.tvReviewCountValue.text = "$it"
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
        
        viewModel.reviews.observe(this) {
            showReviews(it)
        }
        
        viewModel.annotationCount.observe(this) {
            binding.tvAnnotationCount.text = "${it}条书摘"
        }
        
        viewModel.bookTags.observe(this) { tags ->
            showTags(tags)
        }
        
        // 阅读会话数据观察者
        viewModel.monthlyReadingSessions.observe(this) { sessions ->
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
        

        
        // 监听书源变更事件，根据书源分组决定是否更新字数和分类
        observeEvent<String>(EventBus.SOURCE_CHANGED) { bookUrl ->
            // 只有当事件中的书籍URL与当前书籍相同时才更新
            book?.let { currentBook ->
                if (currentBook.bookUrl == bookUrl) {
                    // 设置更换书源标志
                    isSourceChanged = true
                    
                    // 检查书源分组，决定是否更新字数和分类
                    lifecycleScope.launch {
                        val updatedBook = withContext(Dispatchers.IO) {
                            appDb.bookDao.getBook(bookUrl)
                        }
                        if (updatedBook != null) {
                            val bookSource = withContext(Dispatchers.IO) {
                                appDb.bookSourceDao.getBookSource(updatedBook.origin)
                            }
                            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                            
                            // 只有正版书源才更新字数和分类，非正版书源保留原有信息
                            viewModel.refreshBookDataOnly(updateWordCountAndKind = isOfficialSource)
                            
                            // 无论是否是正版书源，都需要加载标签，否则标签不会显示
                            viewModel.loadBookTags()
                            
                            // 如果是正版书源，自动同步标签，添加状态标志防止重复更新
                            if (isOfficialSource && !isTagUpdating) {
                                try {
                                    isTagUpdating = true
                                    if (!updatedBook.kind.isNullOrBlank()) {
                                        // 同步Book实体的kind字段到标签系统
                                        BookInfoSyncHelper.syncBookKindToTags(
                                            bookUrl = updatedBook.bookUrl,
                                            preserveBookInfo = true,
                                            bookOrigin = updatedBook.origin
                                        )
                                        // 不需要将标签信息同步回Book实体的kind字段，因为标签信息本身就是来自于Book的kind字段
                                        // 同步回kind字段可能导致数据不一致，所以移除这一步
                                    }
                                } finally {
                                    // 无论成功失败都重置标志
                                    isTagUpdating = false
                                }
                            }
                            
                            // 发送书架更新事件，通知书架列表刷新标签
                            postEvent(EventBus.BOOKSHELF_REFRESH, "")
                        }
                        
                        // 延迟重置标志，确保所有观察者都处理完成
                        delay(500)
                        isSourceChanged = false
                    }
                }
            }
        }
        
        // 监听标签更新事件
        observeEvent<String>(EventBus.TAGS_UPDATED) { bookUrl ->
            // 只有当事件中的书籍URL与当前书籍相同时才更新
            book?.let { currentBook ->
                if (currentBook.bookUrl == bookUrl) {
                    // 标签更新时重新加载标签
                    viewModel.loadBookTags()
                }
            }
        }
        
        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        viewModel.initData(bookUrl)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        // 隐藏右上角的编辑分享更多选项
        return false
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        // 隐藏右上角的编辑分享更多选项
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initViewEvent() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            isManualRefresh = true // 标记为手动刷新
            // 重新加载所有数据，包括字数和分类
            viewModel.refresh()
        }
        
        binding.ratingView.setOnRatingChangeListener { rating ->
            book?.let { currentBook -> 
                // 避免重复设置相同的评分
                if (currentBook.rating != rating) {
                    viewModel.updateBookRating(rating)
                }
            }
        }
        
        // 启用阅读状态点击修改
        binding.tvReadingStatus.setOnClickListener {
            book?.let { currentBook ->
                showReadingStatusDialog(currentBook)
            }
        }
        
        binding.tvIntro.setOnClickListener {
            book?.let { currentBook ->
                val intent = android.content.Intent(this, BookInfoEditActivity::class.java)
                intent.putExtra("bookUrl", currentBook.bookUrl)
                startActivity(intent)
            }
        }
        
        // 添加书评点击事件
        binding.ivAddReview.setOnClickListener {
            book?.let { currentBook ->
                showEditReviewsDialog(currentBook)
            }
        }
        
        // 添加标签点击事件
        binding.ivAddTag.setOnClickListener {
            book?.let { currentBook ->
                showAddTagDialog(currentBook)
            }
        }
        
        // 移除标签点击事件，改为在 BookTagsAdapter 中处理长按事件
        binding.bookTagsView.setOnTagClickListener { tagName ->
            // 点击标签不做任何操作
        }
        
        // 添加标签删除监听器，用于处理长按删除
        binding.bookTagsView.setOnTagDeleteListener { tagName ->
            book?.let { currentBook ->
                // 根据标签名称查找对应的 BookTag 对象
                val tag = viewModel.bookTags.value?.find { it.name == tagName }
                if (tag != null) {
                    // 直接删除标签，不显示对话框
                    viewModel.removeTagFromBook(tag.id)
                }
            }
        }
        
        // 展开/收起按钮点击事件
        binding.tvExpandCollapse.setOnClickListener {
            toggleIntroExpandCollapse()
        }
        
        // 设置爆炸效果实例到标签视图
        binding.bookTagsView.setExplosionField(explosionField)
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
    
    /**
     * 显示标签
     */
    private fun showTags(tags: List<BookTag>) {
        // 确保标签列表去重，避免重复显示
        val uniqueTags = tags.distinctBy { it.id }
        // 始终显示标签卡片，只更新标签内容
        binding.bookTagsView.setTags(uniqueTags)
        // 移除删除按钮和添加按钮，因为右上角已有添加按钮
        binding.bookTagsView.setDeleteButtonVisible(false)
        binding.bookTagsView.setDeleteEnabled(true) // 启用删除功能，用于长按删除
        binding.bookTagsView.setShowAddButton(false)
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
            kind.isNullOrEmpty() || kind == "无" -> "字数 $wordCount"
            kind.contains("完结") -> "完结  $wordCount"
            kind.contains("连载") -> "连载  $wordCount"
            else -> "字数  $wordCount"
        }
        binding.tvWordCount.text = displayText
    }
    
    /**
     * 将分类信息添加为标签
     * 只有当书源分组包含"正版"时才自动添加标签
     */
    private fun addKindAsTag(kind: String) {
        viewModel.bookData.value?.let { book ->
            // 使用processKindTags方法处理分类信息
            val processedTags = viewModel.processKindTags(kind)
            
            if (processedTags.isEmpty()) {
                return
            }
            
            // 在协程中处理所有标签操作，确保原子性
            lifecycleScope.launch {
                try {
                    // 获取书源信息
                    val bookSource = withContext(Dispatchers.IO) {
                        appDb.bookSourceDao.getBookSource(book.origin)
                    }
                    
                    // 判断书源是否属于"正版"分组
                    val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                    
                    // 如果不是正版书源，则不自动添加标签
                    if (!isOfficialSource) {
                        return@launch
                    }
                    
                    // 获取当前书籍已关联的标签
                    val existingTags = withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.getRelationsByBook(book.bookUrl)
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
                        appDb.removedAutoTagDao.getRemovedTagsByBook(book.bookUrl)
                    }
                    val removedTagNames = removedTags.map { it.tagName }.toSet()
                    
                    // 为每个处理后的标签创建并添加到书籍
                    for ((tagName, tagColor) in processedTags) {
                        // 如果标签已被用户移除，跳过添加
                        if (removedTagNames.contains(tagName)) {
                            continue
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
                                appDb.bookTagRelationDao.getRelation(book.bookUrl, tag.id)
                            }
                            
                            if (existingRelation == null) {
                                // 创建关联关系
                                val relation = io.legado.app.data.entities.BookTagRelation(
                                    id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                                    bookUrl = book.bookUrl,
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
    
    /**
     * 显示添加标签对话框
     */
    private fun showAddTagDialog(book: Book) {
        io.legado.app.ui.widget.dialog.BookTagSelectDialog.show(
            fragmentManager = supportFragmentManager,
            bookUrl = book.bookUrl,
            callback = { bookTag ->
                // 直接使用回调中的标签对象，添加到书籍
                lifecycleScope.launch {
                    try {
                        // 标签已存在，直接添加关联
                        viewModel.addTagToBook(book.bookUrl, bookTag.id)
                    } catch (e: Exception) {
                        toastOnUi("添加标签失败: ${e.localizedMessage}")
                    }
                }
            }
        )
    }
    
    /**
     * 显示编辑标签对话框
     */
    private fun showEditTagDialog(book: Book, tag: BookTag) {
        io.legado.app.ui.widget.dialog.BookTagEditDialog.show(
            fragmentManager = supportFragmentManager,
            bookUrl = book.bookUrl,
            oldTagName = tag.name,
            callback = { tagInfo ->
                // 创建更新后的标签对象
                val updatedTag = tag.copy(name = tagInfo.name, color = tagInfo.color)
                viewModel.updateTag(updatedTag)
            }
        )
    }
    
    /**
     * 显示删除标签对话框
     */
    private fun showDeleteTagDialog(book: Book, tag: BookTag) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("移除标签")
            .setMessage("确定要从本书中移除标签\"${tag.name}\"吗？\n\n注意：这只会移除本书与该标签的关联，不会删除标签本身。")
            .setPositiveButton("确定") { _, _ ->
                viewModel.removeTagFromBook(tag.id)
                // 不需要在这里调用loadBookTags()，因为removeTagFromBook方法内部已经调用了
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 实现OnReviewSavedListener接口，当书评保存后刷新书评列表
    override fun onReviewSaved() {
        book?.let { currentBook ->
            // 重新加载所有数据，确保显示最新状态
            viewModel.refresh()
        }
    }

    private fun showBookInfo(book: Book) {
        // 总是更新 UI，确保显示最新数据
        binding.tvBookName.text = book.name
        binding.tvAuthor.text = book.author
        
        // 设置封面 - 使用 CoverImageView 的 load 方法
        binding.ivCover.load(book)
        
        // 设置评分
        binding.ratingView.setRating(book.rating)
        
        // 设置阅读状态
        updateReadingStatusDisplay(book)
        
        // 设置书籍介绍
        val intro = book.getDisplayIntro() ?: getString(R.string.not_available)
        binding.tvIntro.text = intro
        
        // 设置读完时间
        updateFinishReadTimeDisplay(book)
    }

    /**
     * 更新阅读状态显示
     */
    private fun updateReadingStatusDisplay(book: Book) {
        val statusText = when (book.getReadingStatusEnum()) {
            io.legado.app.constant.ReadingStatus.PENDING -> "待读"
            io.legado.app.constant.ReadingStatus.READING -> "在读"
            io.legado.app.constant.ReadingStatus.FINISHED -> "读完"
            io.legado.app.constant.ReadingStatus.ABANDONED -> "弃读"
        }
        binding.tvReadingStatus.text = statusText
        
        // 根据状态设置不同的颜色
        val statusColor = when (book.getReadingStatusEnum()) {
            io.legado.app.constant.ReadingStatus.PENDING -> ContextCompat.getColor(this, R.color.md_grey_600)
            io.legado.app.constant.ReadingStatus.READING -> ContextCompat.getColor(this, R.color.md_blue_600)
            io.legado.app.constant.ReadingStatus.FINISHED -> ContextCompat.getColor(this, R.color.md_green_600)
            io.legado.app.constant.ReadingStatus.ABANDONED -> ContextCompat.getColor(this, R.color.md_red_600)
        }
        binding.tvReadingStatus.setTextColor(statusColor)
    }
    
    /**
     * 更新读完时间显示
     */
    private fun updateFinishReadTimeDisplay(book: Book) {
        if (book.getReadingStatusEnum() == io.legado.app.constant.ReadingStatus.FINISHED && book.finishReadTime > 0L) {
            val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            val finishTimeStr = sdf.format(Date(book.finishReadTime))
            binding.tvFinishReadTime.text = "$finishTimeStr 读完"
            binding.tvFinishReadTime.visibility = View.VISIBLE
            // 当书籍读完时，隐藏进度条
            binding.pbReadingProgress.visibility = View.GONE
        } else {
            binding.tvFinishReadTime.visibility = View.GONE
            // 当书籍未读完时，显示进度条
            binding.pbReadingProgress.visibility = View.VISIBLE
        }
    }
    
    /**
     * 显示阅读状态选择对话框
     */
    private fun showReadingStatusDialog(book: Book) {
        val currentStatus = book.getReadingStatusEnum()
        val statusNames = arrayOf("待读", "在读", "读完", "弃读")
        val statusValues = arrayOf(
            io.legado.app.constant.ReadingStatus.PENDING,
            io.legado.app.constant.ReadingStatus.READING,
            io.legado.app.constant.ReadingStatus.FINISHED,
            io.legado.app.constant.ReadingStatus.ABANDONED
        )
        
        val currentIndex = statusValues.indexOf(currentStatus)
        
        AlertDialog.Builder(this)
            .setTitle("选择阅读状态")
            .setSingleChoiceItems(statusNames, currentIndex) { dialog, which ->
                val selectedStatus = statusValues[which]
                viewModel.updateBookReadingStatus(book, selectedStatus)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
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
                
                // 设置点击事件，点击书摘项可以编辑
                annotationAdapter.setOnItemClickListener { bookmark ->
                    book?.let { book ->
                        showEditAnnotationDialog(book, bookmark)
                    }
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
                
                // 设置点击事件，点击书评项可以编辑
                reviewAdapter.setOnItemClickListener { review, position ->
                    book?.let { book ->
                        val dialog = BookReviewDialog(review, position)
                        dialog.setOnReviewSavedListener(this)
                        dialog.show(supportFragmentManager, "bookReviewDialog")
                    }
                }
            }
            
            reviewAdapter.submitList(reviews)
        }
    }
    
    private fun showEditAnnotationDialog(book: Book) {
        // 显示已有书摘内容，而不是弹出输入对话框
        // 从viewModel获取已有的书摘数据
        lifecycleScope.launch {
            try {
                // 在IO线程中执行数据库查询
                val annotations = withContext(Dispatchers.IO) {
                    appDb.bookAnnotationDao.getByBook(book.name, book.author)
                }
                
                // 确保在主线程中执行UI操作
                withContext(Dispatchers.Main) {
                    if (annotations.isEmpty()) {
                        // 如果没有书摘，显示提示
                        androidx.appcompat.app.AlertDialog.Builder(this@BookReadingDetailActivity)
                            .setTitle(getString(R.string.book_annotations))
                            .setMessage(getString(R.string.no_annotations_available))
                            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    } else {
                        // 创建一个对话框显示所有书摘
                        val annotationsText = annotations.joinToString("\n\n---\n\n") { annotation: BookAnnotation ->
                            "${annotation.chapterName}\n${annotation.bookText}\n${annotation.content}"
                        }
                        
                        val scrollView = android.widget.ScrollView(this@BookReadingDetailActivity)
                        val textView = android.widget.TextView(this@BookReadingDetailActivity)
                        textView.text = annotationsText
                        textView.setPadding(50, 50, 50, 50)
                        scrollView.addView(textView)
                        
                        androidx.appcompat.app.AlertDialog.Builder(this@BookReadingDetailActivity)
                            .setTitle(getString(R.string.book_annotations))
                            .setView(scrollView)
                            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                }
            } catch (e: CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                // 使用更安全的错误处理方式
                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@BookReadingDetailActivity)
                        .setTitle(getString(R.string.book_annotations))
                        .setMessage(getString(R.string.error_loading_annotations))
                        .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }
    
    private fun showEditAnnotationDialog(book: Book, bookmark: Bookmark) {
        // 将Bookmark转换为BookAnnotation
        val bookAnnotation = BookAnnotation(
            bookName = book.name,
            bookAuthor = book.author,
            chapterName = bookmark.chapterName,
            chapterIndex = bookmark.chapterIndex,
            chapterPos = bookmark.chapterPos,
            bookText = bookmark.bookText,
            content = bookmark.content,
            time = bookmark.time
        )
        
        // 使用BookAnnotationDialog显示书摘编辑对话框
        // 设置editPos为0表示编辑模式
        val dialog = BookAnnotationDialog(bookAnnotation, 0)
        dialog.show(supportFragmentManager, "bookAnnotationDialog")
    }
    
    private fun showEditReviewsDialog(book: Book) {
        lifecycleScope.launch {
            try {
                // 在IO线程中执行数据库查询
                val reviews = withContext(Dispatchers.IO) {
                    appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
                }
                
                // 确保在主线程中执行UI操作
                withContext(Dispatchers.Main) {
                    if (reviews.isNotEmpty()) {
                        // 如果有书评，编辑第一个书评
                        val review = reviews[0]
                        val dialog = BookReviewDialog(review, 0)
                        dialog.setOnReviewSavedListener(this@BookReadingDetailActivity)
                        dialog.show(supportFragmentManager, "bookReviewDialog")
                    } else {
                        // 如果没有书评，创建新书评
                        val newReview = BookReview(
                            bookUrl = book.bookUrl,
                            bookName = book.name,
                            bookAuthor = book.author,
                            reviewContent = "",
                            createTime = System.currentTimeMillis(),
                            updateTime = System.currentTimeMillis()
                        )
                        val dialog = BookReviewDialog(newReview, -1)
                        dialog.setOnReviewSavedListener(this@BookReadingDetailActivity)
                        dialog.show(supportFragmentManager, "bookReviewDialog")
                    }
                }
            } catch (e: CancellationException) {
                // 协程被取消，不需要处理
                throw e
            } catch (e: Exception) {
                // 使用更安全的错误处理方式
                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@BookReadingDetailActivity)
                        .setTitle(getString(R.string.book_reviews))
                        .setMessage(getString(R.string.error_loading_reviews))
                        .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }







    }
    

    
    /**
     * 格式化阅读时间（分钟转换为小时分钟格式）
     */
    private fun formatReadingTime(minutes: Long): String {
        if (minutes < 60) {
            return "${minutes}分钟"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            return "${hours}小时${mins}分钟"
        }
    }
    
    /**
     * 格式化阅读时长，与阅读记录页面保持一致
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
    
    /**
     * 显示阅读会话
     */
    private fun showReadingSessions(sessions: List<BookReadingDetailViewModel.MonthReadingSession>) {
        val sessionCard = binding.contentLayout.findViewById<androidx.cardview.widget.CardView>(R.id.reading_session_card)
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
                readingSessionAdapter = ReadingSessionAdapter(sessions)
                binding.recyclerReadingSessions.adapter = readingSessionAdapter
            }
        }
    }
}