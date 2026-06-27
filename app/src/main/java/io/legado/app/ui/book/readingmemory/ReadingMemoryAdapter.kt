package io.legado.app.ui.book.readingmemory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.data.entities.Book
import io.legado.app.data.appDb
import io.legado.app.databinding.ItemReadingMemoryBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.readingmemory.ReadingMemoryActivity
import io.legado.app.utils.*
import io.legado.app.help.book.ReadingProgressHelper
import io.legado.app.utils.DashedDividerDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class ReadingMemoryAdapter(
    private val activity: VMBaseActivity<*, *>,
    private val viewModel: ReadingMemoryViewModel
) : ListAdapter<Any, RecyclerView.ViewHolder>(diffCallback) {

    // 定义两种ViewType：分组头部和书籍项
    private val VIEW_TYPE_GROUP_HEADER = 0
    private val VIEW_TYPE_BOOK_ITEM = 1

    // 用于存储分组数据
    private var groupedData: MutableList<Any> = mutableListOf()

    // 存储每个分组的折叠状态，true表示折叠，false表示展开
    private val collapsedGroups = mutableMapOf<String, Boolean>()

    // 原始书籍数据，用于在折叠状态变化时重新分组
    private var originalBooks: List<ReadingMemory> = emptyList()

    // 当前分组方式
    private var currentGroupBy: String = "none"

    // 设置当前分组方式
    fun setGroupBy(groupBy: String) {
        currentGroupBy = groupBy
        // 重置折叠状态
        collapsedGroups.clear()
        // 重新分组
        if (originalBooks.isNotEmpty()) {
            submitReadingMemoriesList(originalBooks)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GROUP_HEADER -> {
                val binding = io.legado.app.databinding.ItemReadingMemoryGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupHeaderViewHolder(binding)
            }
            VIEW_TYPE_BOOK_ITEM -> {
                val binding = io.legado.app.databinding.ItemReadingMemoryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                BookItemViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupHeaderViewHolder -> {
                val header = getItem(position) as GroupHeader
                holder.bind(header)
            }
            is BookItemViewHolder -> {
                val book = getItem(position) as io.legado.app.data.entities.ReadingMemory
                holder.bind(book)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupHeader -> VIEW_TYPE_GROUP_HEADER
            is io.legado.app.data.entities.ReadingMemory -> VIEW_TYPE_BOOK_ITEM
            else -> throw IllegalArgumentException("Invalid item type")
        }
    }

    /**
     * 将书籍列表转换为分组列表
     */
    private fun groupBooks(books: List<io.legado.app.data.entities.ReadingMemory>): List<Any> {
        val result = mutableListOf<Any>()

        when (currentGroupBy) {
            "year" -> {
        // 按年份分组
        val groupedBooks = books.groupBy {
            val calendar = java.util.Calendar.getInstance()
            // 所有状态的书籍：优先使用首次阅读时间的年份
            // 如果首次阅读时间为0，则使用创建时间
            if (it.firstReadTime > 0L) {
                calendar.timeInMillis = it.firstReadTime
            } else {
                calendar.timeInMillis = it.createTime
            }
                    calendar.get(java.util.Calendar.YEAR).toString()
        }

        // 按年份降序排列分组
                val sortedGroups = groupedBooks.keys.sortedDescending()

                // 构建分组列表
                for (group in sortedGroups) {
                    val groupBooks = groupedBooks[group] ?: continue
                    val bookCount = groupBooks.size
                    // 添加分组头部
                    result.add(GroupHeader(group, bookCount, "year"))
                    // 根据折叠状态决定是否添加书籍项
                    if (!collapsedGroups.getOrDefault(group, false)) {
                        result.addAll(groupBooks)
                    }
                }
            }
            "rating" -> {
                // 按评分等级分组
                val groupedBooks = books.groupBy {
                    when {
                        it.rating >= 5 -> "★★★★★"
                        it.rating >= 4 -> "★★★★☆"
                        it.rating >= 3 -> "★★★☆☆"
                        it.rating >= 2 -> "★★☆☆☆"
                        it.rating >= 1 -> "★☆☆☆☆"
                        else -> "未评分"
        }
                }

                // 按评分从高到低排列分组
                val ratingOrder = listOf("★★★★★", "★★★★☆", "★★★☆☆", "★★☆☆☆", "★☆☆☆☆", "未评分")
                val sortedGroups = groupedBooks.keys.sortedBy { ratingOrder.indexOf(it) }

                // 构建分组列表
                for (group in sortedGroups) {
                    val groupBooks = groupedBooks[group] ?: continue
                    val bookCount = groupBooks.size
                    // 添加分组头部
                    result.add(GroupHeader(group, bookCount, "rating"))
                    // 根据折叠状态决定是否添加书籍项
                    if (!collapsedGroups.getOrDefault(group, false)) {
                        result.addAll(groupBooks)
                    }
                }
            }
            "status" -> {
                // 按阅读状态分组
                val groupedBooks = books.groupBy {
                    it.readingStatus.displayName
                }

                // 按状态顺序排列分组：在看 -> 待看 -> 看完 -> 弃
                val statusOrder = listOf("在看", "待看", "看完", "弃")
                val sortedGroups = groupedBooks.keys.sortedBy { statusOrder.indexOf(it) }

        // 构建分组列表
                for (group in sortedGroups) {
                    val groupBooks = groupedBooks[group] ?: continue
                    val bookCount = groupBooks.size
                    // 添加分组头部
                    result.add(GroupHeader(group, bookCount, "status"))
            // 根据折叠状态决定是否添加书籍项
                    if (!collapsedGroups.getOrDefault(group, false)) {
                        result.addAll(groupBooks)
                    }
                }
            }
            else -> {
                // 不分组，直接添加所有书籍
                result.addAll(books)
            }
        }

        return result
    }

    /**
     * 重写submitList方法，将单一的ReadingMemory列表转换为包含分组头部和书籍项的混合列表
     */
    override fun submitList(list: List<Any>?) {
        super.submitList(list)
    }

    /**
     * 自定义submitList方法，接受ReadingMemory列表并转换为分组列表
     */
    fun submitReadingMemoriesList(books: List<io.legado.app.data.entities.ReadingMemory>) {
        originalBooks = books
        val groupedList = groupBooks(books)
        super.submitList(groupedList.toList())
        groupedData = groupedList.toMutableList()
    }

    /**
     * 切换分组的折叠状态
     */
    fun toggleGroupCollapse(groupKey: String) {
        collapsedGroups[groupKey] = !collapsedGroups.getOrDefault(groupKey, false)
        val groupedList = groupBooks(originalBooks)
        super.submitList(groupedList.toList())
        groupedData = groupedList.toMutableList()
    }



    // 分组头部ViewHolder
    inner class GroupHeaderViewHolder(private val binding: io.legado.app.databinding.ItemReadingMemoryGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: GroupHeader) = binding.run {
            val groupKey = header.groupKey
            val bookCount = header.bookCount

            // 根据分组类型设置不同的显示文本
            when (header.groupType) {
                "year" -> {
                    // 年份分组：2025年 (8本)
                    tvGroupYear.text = "${groupKey}年 (${bookCount}本)"
                }
                "rating" -> {
                    // 评分分组：★★★★★ (15本)
                    tvGroupYear.text = "${groupKey} (${bookCount}本)"
                }
                "status" -> {
                    // 状态分组：在读 (5本)
                    tvGroupYear.text = "${groupKey} (${bookCount}本)"
                }
                else -> {
                    tvGroupYear.text = "${groupKey} (${bookCount}本)"
                }
            }

            // 隐藏数量文本框，因为已经包含在分组文本中
            tvBookCount.visibility = android.view.View.GONE

            // 清空右侧的drawable，使用自定义的聚焦按钮
            tvGroupYear.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)

            // 检查是否已经有聚焦按钮，如果有则移除
            if (tvGroupYear.parent is android.widget.LinearLayout) {
                val parentLayout = tvGroupYear.parent as android.widget.LinearLayout
                // 移除已有的聚焦按钮（如果存在）
                val childCount = parentLayout.childCount
                for (i in childCount - 1 downTo 0) {
                    val child = parentLayout.getChildAt(i)
                    if (child is android.widget.ImageView) {
                        parentLayout.removeView(child)
                        break
                    }
                }
            }

            // 设置点击事件，切换折叠状态
            root.setOnClickListener {
                toggleGroupCollapse(groupKey)
            }
        }
    }

    // 书籍项ViewHolder
    inner class BookItemViewHolder(private val binding: io.legado.app.databinding.ItemReadingMemoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: io.legado.app.data.entities.ReadingMemory) = binding.run {
            // 读取是否开启卡片背景
            val useCard = AppConfig.showBookCard

            if (useCard) {
                // 1. 显示卡片 + 上下占位
                cardBg.visibility = android.view.View.VISIBLE
                spaceTop.visibility = android.view.View.VISIBLE
                spaceBottom.visibility = android.view.View.VISIBLE

                // 2. 统一以屏幕边缘为参照
                val margin24 = 24 * activity.resources.displayMetrics.density.toInt() // 内容距离屏幕边缘24dp
                val margin16 = 16 * activity.resources.displayMetrics.density.toInt() // 卡片距离屏幕边缘16dp
                
                // 重置所有边距和padding
                cardBg.setPadding(0, 0, 0, 0)
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置卡片背景颜色
                cardBg.setBackgroundResource(R.drawable.bg_card_rounded)
                val cardColor = io.legado.app.lib.theme.ThemeStore.backgroundCard(activity)
                val background = cardBg.background
                if (background is android.graphics.drawable.GradientDrawable) {
                    background.setColor(cardColor)
                }
                
                // 设置卡片左右边距为16dp，让卡片距离屏幕边缘16dp
                val cardLayoutParams = cardBg.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                cardLayoutParams.leftMargin = margin16
                cardLayoutParams.rightMargin = margin16
                cardBg.layoutParams = cardLayoutParams
                
                // 设置内容容器左右边距为24dp，确保内容距离屏幕边缘24dp
                val layoutParams = layoutContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.leftMargin = margin24
                layoutParams.rightMargin = margin24
                layoutContent.layoutParams = layoutParams
            } else {
                // 1. 隐藏卡片 + 占位
                cardBg.visibility = android.view.View.GONE
                spaceTop.visibility = android.view.View.GONE
                spaceBottom.visibility = android.view.View.GONE

                // 2. 内容距离屏幕边缘16dp
                val margin16 = 16 * activity.resources.displayMetrics.density.toInt()
                
                // 重置所有边距和padding
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置内容容器左右边距为16dp
                val layoutParams = layoutContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.leftMargin = margin16
                layoutParams.rightMargin = margin16
                layoutContent.layoutParams = layoutParams
            }

            // 设置封面 - 使用 CoverImageView 的 load 方法
            // 首先尝试从数据库获取书籍信息，以便获取作者信息
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val book: Book? = withContext(Dispatchers.IO) {
                        appDb.bookDao.getBook(item.bookUrl)
                    }
                    
                    // 获取封面路径
                    val coverUrl = item.coverUrl?.takeIf { it.isNotEmpty() }
                        ?: book?.getDisplayCover()
                    
                    // 使用 CoverImageView 的 load 方法加载封面
                    // 这样会自动使用自定义主题封面并在封面上显示书名和作者
                    coverView.load(coverUrl, item.bookName, item.bookAuthor)
                } catch (e: Exception) {
                    // 出错时使用基本参数加载
                    coverView.load(item.coverUrl, item.bookName, item.bookAuthor)
                }
            }

            // 设置书名
            titleView.text = item.bookName

            // 设置作者
            authorView.text = item.bookAuthor

            // 设置阅读状态标签
            val statusTag = when (item.readingStatus) {
                io.legado.app.constant.ReadingStatus.FINISHED -> "看完"
                io.legado.app.constant.ReadingStatus.READING -> "在看"
                io.legado.app.constant.ReadingStatus.ABANDONED -> "弃"
                else -> "待看"
            }

            // 清空标签容器
            tagContainer.removeAllViews()

            // 设置状态颜色，与阅读详情页保持一致
            val color = when (item.readingStatus) {
                io.legado.app.constant.ReadingStatus.PENDING -> activity.getCompatColor(R.color.md_grey_600)
                io.legado.app.constant.ReadingStatus.READING -> activity.getCompatColor(R.color.md_blue_600)
                io.legado.app.constant.ReadingStatus.FINISHED -> activity.getCompatColor(R.color.md_green_600)
                io.legado.app.constant.ReadingStatus.ABANDONED -> activity.getCompatColor(R.color.md_red_600)
            }

            // 添加书籍标签 - 使用TagManager确保与详情页逻辑一致
            // 使用activity的lifecycleScope确保在Activity生命周期内执行
            activity.lifecycleScope.launch {
                try {
                    // 使用TagManager.loadBookTags()方法获取标签，确保与阅读详情页面使用相同的逻辑
                    var tags = withContext(Dispatchers.IO) {
                        io.legado.app.help.book.TagManager.loadBookTags(item.bookUrl)
                    }

                    // 如果没有标签，检查是否为正版书源，如果是则生成标签
                    if (tags.isEmpty()) {
                        val book = withContext(Dispatchers.IO) {
                            io.legado.app.data.appDb.bookDao.getBook(item.bookUrl)
                        }
                        if (book != null) {
                            val isOfficialSource = withContext(Dispatchers.IO) {
                                val bookSource = io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin)
                                bookSource?.bookSourceGroup?.contains("正版") == true
                            }

                            if (isOfficialSource && !book.kind.isNullOrBlank()) {
                                // 生成标签
                                val generatedTags = withContext(Dispatchers.IO) {
                                    io.legado.app.help.book.TagManager.generateTagsFromKind(book)
                                }
                                if (generatedTags.isNotEmpty()) {
                                    // 重新加载标签，确保过滤被排除的标签
                                    tags = withContext(Dispatchers.IO) {
                                        io.legado.app.help.book.TagManager.loadBookTags(item.bookUrl)
                                    }
                                }
                            }
                        }
                    }

                    // 清空标签容器
                    tagContainer.removeAllViews()

                    // 重新添加状态标签
                    val statusTagView = android.widget.TextView(activity)
                    statusTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                    )
                    statusTagView.setText(statusTag)
                    statusTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    statusTagView.setPadding(8, 0, 8, 0) // 与其他标签保持一致的内边距
                    statusTagView.setGravity(android.view.Gravity.CENTER) // 与其他标签保持一致的居中对齐
                    statusTagView.setSingleLine(true)
                    statusTagView.setEllipsize(android.text.TextUtils.TruncateAt.END)
                    statusTagView.setMaxLines(1)
                    // 使用矩形背景，与其他标签保持一致
                    val statusBg = activity.resources.getDrawable(R.drawable.bg_tag_rectangle)
                    val statusBackgroundColor = color and 0x00FFFFFF or (0x1A000000) // 10%透明度
                    statusBg?.setTint(statusBackgroundColor)
                    statusTagView.background = statusBg
                    statusTagView.setTextColor(color)
                    // 检查状态标签是否会超出容器
                    statusTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                    val statusTagWidth = statusTagView.measuredWidth
                    val containerWidth = tagContainer.width
                    if (statusTagWidth <= containerWidth) {
                        tagContainer.addView(statusTagView)
                    }

                    // 添加书籍标签，显示所有标签但只在一行内
                    var currentWidth = 0

                    // 计算已添加的状态标签宽度
                    if (tagContainer.childCount > 0) {
                        val statusTag = tagContainer.getChildAt(0)
                        statusTag.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                        currentWidth += statusTag.measuredWidth
                    }

                    for (tag in tags) {
                        val bookTagView = android.widget.TextView(activity)
                        bookTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                            com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                        )
                        bookTagView.setText(tag.name)
                        bookTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                        bookTagView.setPadding(8, 0, 8, 0) // 增加左右内边距
                        bookTagView.setGravity(android.view.Gravity.CENTER)
                        bookTagView.setSingleLine(true)
                        bookTagView.setEllipsize(android.text.TextUtils.TruncateAt.END)
                        bookTagView.setMaxLines(1)
                        // 使用矩形背景，无圆角
                        val bookTagBg = activity.resources.getDrawable(R.drawable.bg_tag_rectangle)
                        val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A000000) // 10%透明度
                        bookTagBg?.setTint(bookTagBackgroundColor)
                        bookTagView.background = bookTagBg
                        bookTagView.setTextColor(tag.color)

                        // 添加间距
                        val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                        layoutParams.setMarginStart(10)
                        bookTagView.layoutParams = layoutParams

                        // 检查标签是否会超出容器
                        bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                        val tagWidth = bookTagView.measuredWidth + 10 // 加上边距

                        if (currentWidth + tagWidth <= containerWidth) {
                            tagContainer.addView(bookTagView)
                            currentWidth += tagWidth
                        } else {
                            // 超出容器，不显示该标签
                            continue
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 设置评分
            ratingView.setRating(item.rating)

            // 设置简介显示
            val showIntro = AppConfig.showReadingMemoryIntro
            val introContent = item.intro
            if (showIntro && !introContent.isNullOrBlank()) {
                layoutIntro.visibility = android.view.View.VISIBLE
                introView.text = introContent
            } else {
                layoutIntro.visibility = android.view.View.GONE
            }

            // 设置书评显示（统一从 BookReview 表读取）
            val dividerColor = io.legado.app.lib.theme.ThemeStore.dividerColor(activity)
            val strokeWidth = 0.5f * activity.resources.displayMetrics.density
            view_divider.background = DashedDividerDrawable(
                dividerColor, strokeWidth,
                8f * activity.resources.displayMetrics.density,
                4f * activity.resources.displayMetrics.density
            )
            view_divider.layoutParams = view_divider.layoutParams.apply {
                height = strokeWidth.toInt().coerceAtLeast(1)
            }
            val showReview = AppConfig.showBookReview
            CoroutineScope(Dispatchers.Main).launch {
                val reviews = withContext(Dispatchers.IO) {
                    io.legado.app.data.appDb.bookReviewDao.getReviewByBookUrl(item.bookUrl)
                }
                val reviewContent = reviews.firstOrNull()?.reviewContent
                if (showReview && !reviewContent.isNullOrBlank()) {
                    layoutReview.visibility = android.view.View.VISIBLE
                    reviewView.text = reviewContent
                } else {
                    layoutReview.visibility = android.view.View.GONE
                }
            }

            // 添加阅读状态点击事件
            statusTagView.setOnClickListener {
                showReadingStatusDialog(item)
            }



            // 设置封面点击事件 - 进入阅读页面
            coverView.setOnClickListener { view ->
                // 阻止事件冒泡，避免触发列表项的点击事件
                view.isClickable = true
                view.isFocusable = true
                // 参考阅读记录的处理方式，先检查书籍是否在书架上
                CoroutineScope(Dispatchers.Main).launch {
                    val book = withContext(Dispatchers.IO) {
                        io.legado.app.data.appDb.bookDao.findByName(item.bookName).firstOrNull()
                    }
                    if (book == null) {
                        // 书籍不在书架上，启动搜索
                        io.legado.app.ui.book.search.SearchActivity.start(activity, item.bookName)
                    } else {
                        // 书籍在书架上，直接进入阅读页面
                        val intent = android.content.Intent(activity, ReadBookActivity::class.java)
                        intent.putExtra("bookUrl", book.bookUrl)
                        activity.startActivity(intent)
                    }
                }
            }

            // 设置列表项点击事件 - 进入阅读记忆详情（排除封面区域）
            root.setOnClickListener {
                // 调用Activity中的方法打开阅读记忆详情页面
                if (activity is ReadingMemoryActivity) {
                    activity.openReadingMemoryDetail(item)
                }
            }

            // 设置长按事件
            root.setOnLongClickListener {
                activity.alert(
                    title = "操作选项",
                    message = "请选择对《${item.bookName}》的操作"
                ) {
                    if (item.readingStatus != io.legado.app.constant.ReadingStatus.ABANDONED) {
                        positiveButton("标记为弃") {
                            // 使用 activity 的 lifecycleScope
                            activity.lifecycleScope.launch {
                                try {
                                    // 直接使用ReadingMemory的ID，不需要类型转换
                                    val success = viewModel.setBookAsAbandoned(item.id)
                                    if (success) {
                                        // 重新加载数据而不是使用notifyDataSetChanged
                                        viewModel.loadReadingMemories()
                                    } else {
                                        activity.toastOnUi("标记弃失败")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    activity.toastOnUi("标记弃失败: ${e.localizedMessage}")
                                }
                            }
                        }
                    } else {
                        positiveButton("取消弃标记") {
                            // 使用 activity 的 lifecycleScope
                            activity.lifecycleScope.launch {
                                try {
                                    // 取消弃标记
                                    val success = viewModel.removeAbandonedStatus(item.id)
                                    if (success) {
                                        // 重新加载数据而不是使用notifyDataSetChanged
                                        viewModel.loadReadingMemories()
                                    } else {
                                        activity.toastOnUi("取消弃标记失败")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    activity.toastOnUi("取消弃标记失败: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                    negativeButton("删除阅读记录") {
                        // 使用 activity 的 lifecycleScope
                        activity.runOnUiThread {
                            viewModel.deleteReadingMemory(item.bookUrl)
                        }
                    }
                    neutralButton("取消") {}
                }.show()
                true
            }
        }
    }

    /**
     * 显示阅读状态选择对话框
     */
    private fun showReadingStatusDialog(item: io.legado.app.data.entities.ReadingMemory) {
        val currentStatus = item.readingStatus
        val statusNames = arrayOf("待看", "在看", "看完", "弃")
        val statusValues = arrayOf(
            io.legado.app.constant.ReadingStatus.PENDING,
            io.legado.app.constant.ReadingStatus.READING,
            io.legado.app.constant.ReadingStatus.FINISHED,
            io.legado.app.constant.ReadingStatus.ABANDONED
        )

        val currentIndex = statusValues.indexOf(currentStatus)

        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("选择阅读状态")
            .setSingleChoiceItems(statusNames, currentIndex) { dialog, which ->
                val selectedStatus = statusValues[which]
                viewModel.updateReadingStatus(item.id, selectedStatus)
                // 状态更新后重新加载数据，确保标签同步
                viewModel.loadReadingMemories()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 定义分组头部数据类，包含分组键、书籍数量和分组类型，用于正确的DiffUtil比较
    data class GroupHeader(val groupKey: String, val bookCount: Int, val groupType: String)

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<Any>() {
            override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
                return when {
                    oldItem is GroupHeader && newItem is GroupHeader ->
                        oldItem.groupKey == newItem.groupKey && oldItem.groupType == newItem.groupType // 分组头部
                    oldItem is io.legado.app.data.entities.ReadingMemory &&
                        newItem is io.legado.app.data.entities.ReadingMemory ->
                        oldItem.id == newItem.id // 书籍项
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
                return when {
                    oldItem is GroupHeader && newItem is GroupHeader ->
                        oldItem.groupKey == newItem.groupKey &&
                        oldItem.bookCount == newItem.bookCount &&
                        oldItem.groupType == newItem.groupType // 比较分组键、书籍数量和分组类型
                    oldItem is io.legado.app.data.entities.ReadingMemory &&
                        newItem is io.legado.app.data.entities.ReadingMemory -> {
                        // 比较关键字段，确保标签相关变化能被检测到
                        oldItem.id == newItem.id &&
                        oldItem.bookUrl == newItem.bookUrl &&
                        oldItem.kind == newItem.kind &&
                        oldItem.updateTime == newItem.updateTime &&
                        oldItem.readingStatus == newItem.readingStatus
                    }
                    else -> false
                }
            }
        }
    }
}