package io.legado.app.ui.main.bookshelf.style2

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.ItemBookshelfList2Binding
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.databinding.ItemBookshelfListGroupBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import splitties.views.onLongClick
import io.legado.app.help.book.ReadingProgressHelper
import io.legado.app.help.book.TagManager
import io.legado.app.help.book.getDisplayDurChapterTitle
import io.legado.app.help.book.getDisplayLatestChapterTitle
import io.legado.app.help.book.getDisplayName
import io.legado.app.utils.toTimeAgo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ItemBookshelfListNewBinding

@Suppress("UNUSED_PARAMETER")
class BooksAdapterList(context: Context, callBack: CallBack) :
    BaseBooksAdapter<RecyclerView.ViewHolder>(context, callBack) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            1 -> GroupViewHolder(ItemBookshelfListGroupBinding.inflate(inflater, parent, false))
            else -> {
                when (AppConfig.bookshelfLayout) {
                    0 -> BookViewHolder(ItemBookshelfListBinding.inflate(inflater, parent, false))
                    1 -> BookViewHolder2(ItemBookshelfList2Binding.inflate(inflater, parent, false))
                    2 -> BookViewHolder3(ItemBookshelfListNewBinding.inflate(inflater, parent, false))
                    else -> BookViewHolder(ItemBookshelfListBinding.inflate(inflater, parent, false))
                }
            }

        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        when (holder) {
            is BookViewHolder -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is BookViewHolder2 -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is BookViewHolder3 -> (getItem(position) as? Book)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }

            is GroupViewHolder -> (getItem(position) as? BookGroup)?.let {
                holder.registerListener(it)
                holder.onBind(it, position, payloads)
            }
        }
    }

    inner class BookViewHolder(val binding: ItemBookshelfListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book, position: Int) = binding.run {
            // 读取是否开启卡片背景
            val useCard = AppConfig.showBookCard

            if (useCard) {
                // 1. 显示卡片 + 上下占位
                cardBg.visibility = android.view.View.VISIBLE
                spaceTop.visibility = android.view.View.VISIBLE
                spaceBottom.visibility = android.view.View.VISIBLE

                // 2. 统一以屏幕边缘为参照
                val margin24 = 24 * context.resources.displayMetrics.density.toInt() // 内容距离屏幕边缘24dp
                val margin16 = 16 * context.resources.displayMetrics.density.toInt() // 卡片距离屏幕边缘16dp
                
                // 重置所有边距和padding
                cardBg.setPadding(0, 0, 0, 0)
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置卡片背景颜色
                cardBg.setBackgroundResource(R.drawable.bg_card_rounded)
                val cardColor = ThemeStore.backgroundCard(context)
                val background = cardBg.background
                if (background is android.graphics.drawable.GradientDrawable) {
                    background.setColor(cardColor)
                    val dividerColor = ThemeStore.dividerColor(context)
                    background.setStroke((AppConfig.bookshelfCardBorderWidth * 0.5f).dpToPx(), dividerColor)
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
                val margin16 = 16 * context.resources.displayMetrics.density.toInt()
                
                // 重置所有边距和padding
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置内容容器左右边距为16dp
                val layoutParams = layoutContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.leftMargin = margin16
                layoutParams.rightMargin = margin16
                layoutContent.layoutParams = layoutParams
            }
            
            tvName.text = item.getDisplayName()
            tvAuthor.text = item.author
            tvRead.text = "${item.getDisplayDurChapterTitle()} · ${item.getDisplayLatestChapterTitle()}"
            ivCover.load(item, false)
            flHasNew.visible()
            ivAuthor.visible()
            ivRead.visible()
            upRefresh(this, item)
            upLastUpdateTime(this, item)
            // 设置评分
            ratingView.setRating(item.rating)
            // 计算并显示阅读进度
            val progress = io.legado.app.help.book.ReadingProgressHelper.calculateReadingProgress(item).toInt()
            tvProgress.text = "${progress}%"
            // 加载标签
            upTags(this, item)
        }

        fun onBind(item: Book, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvName.text = item.getDisplayName()
                            "author" -> tvAuthor.text = item.author
                            "dur" -> tvRead.text = "${item.getDisplayDurChapterTitle()} · ${item.getDisplayLatestChapterTitle()}"
                            "last" -> tvRead.text = "${item.getDisplayDurChapterTitle()} · ${item.getDisplayLatestChapterTitle()}"
                            "cover" -> ivCover.load(
                                item,
                                false
                            )

                            "refresh" -> upRefresh(this, item)
                            "lastUpdateTime" -> upLastUpdateTime(this, item)
                            "rating" -> ratingView.setRating(item.rating)
                            "durChapterIndex" -> {
                                val progress = ReadingProgressHelper.calculateReadingProgress(item).toInt()
                                tvProgress.text = "${progress}%"
                            }
                            "tags" -> upTags(this, item)
                        }
                    }
                }
            }
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener {
                callBack.onItemClick(item)
            }
            binding.root.onLongClick {
                callBack.onItemLongClick(item)
            }
        }

        private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.bvUnread.invisible()
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
                if (AppConfig.showUnread) {
                    binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                    binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
                } else {
                    binding.bvUnread.invisible()
                }
            }
        }

        private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
            if (AppConfig.showLastUpdateTime && !item.isLocal) {
                val time = item.latestChapterTime.toTimeAgo()
                if (binding.tvLastUpdateTime.text != time) {
                    binding.tvLastUpdateTime.text = time
                }
            } else {
                binding.tvLastUpdateTime.text = ""
            }
        }

        // 缓存书籍标签信息，避免重复刷新
        private val tagCache = mutableMapOf<String, List<io.legado.app.data.entities.BookTag>>()

        private fun upTags(binding: ItemBookshelfListBinding, item: Book) {
            // 保存当前书籍的URL，用于后续校验，防止ViewHolder被复用后显示错误的标签
            binding.root.tag = item.bookUrl
            
            // 使用协程在后台线程加载标签，避免阻塞UI
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    // 先获取当前缓存的标签
                    val cachedTags = tagCache[item.bookUrl]
                    
                    // 使用TagManager.loadBookTags()方法获取标签，确保与阅读详情页面使用相同的逻辑
                    var tags = io.legado.app.help.book.TagManager.loadBookTags(item.bookUrl)
                    
                    // 如果没有标签，检查是否为正版书源，如果是则生成标签
                    if (tags.isEmpty()) {
                        val isOfficialSource = withContext(Dispatchers.IO) {
                                val bookSource = io.legado.app.data.appDb.bookSourceDao.getBookSource(item.origin)
                                bookSource?.bookSourceGroup?.contains("正版") == true
                            }
                        
                        if (isOfficialSource && !item.kind.isNullOrBlank()) {
                            // 生成标签
                            val generatedTags = withContext(Dispatchers.IO) {
                                io.legado.app.help.book.TagManager.generateTagsFromKind(item)
                            }
                            if (generatedTags.isNotEmpty()) {
                                // 重新加载标签，确保过滤被排除的标签
                                tags = io.legado.app.help.book.TagManager.loadBookTags(item.bookUrl)
                            }
                        }
                    }
                    
                    // 无论是否有标签，都需要更新缓存并刷新UI
                    // 使用 == 比较列表内容（空列表也相等）
                    if (cachedTags == null || cachedTags != tags) {
                        // 标签发生变化（或缓存为空），更新缓存
                        tagCache[item.bookUrl] = tags
                        
                        // 在UI线程中设置标签
                        withContext<Unit>(Dispatchers.Main) {
                            // 校验：确保当前ViewHolder仍然绑定到同一本书，防止竞态条件
                            if (binding.root.tag != item.bookUrl) {
                                return@withContext
                            }
                            
                            // 保存当前标签容器的高度，避免跳动
                            val currentHeight = binding.tvTags.height
                            
                            // 清空标签容器
                            binding.tvTags.removeAllViews()
                            
                            if (tags.isNotEmpty()) {
                            // 计算容器宽度，只添加能容纳的标签
                            val containerWidth = binding.tvTags.width
                            if (containerWidth > 0) {
                                var currentWidth = 0
                                for (tag in tags) {
                                    val bookTagView = android.widget.TextView(binding.root.context)
                                    bookTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    bookTagView.setText(tag.name)
                                    bookTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                                    bookTagView.setPadding(8, 0, 8, 0) // 增加左右内边距
                                    bookTagView.setGravity(android.view.Gravity.CENTER)
                                    bookTagView.setSingleLine(true)
                                    bookTagView.setEllipsize(null) // 不显示省略号，确保标签完整显示
                                    bookTagView.setMaxLines(1)
                                    // 使用矩形背景，有圆角和边框
                                    val bookTagBg = binding.root.context.resources.getDrawable(io.legado.app.R.drawable.bg_tag_rectangle)
                                    val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24) // 10%透明度
                                    bookTagBg?.setTint(bookTagBackgroundColor)
                                    // 设置边框颜色为标签文字颜色
                                    if (bookTagBg is android.graphics.drawable.GradientDrawable) {
                                        bookTagBg.setStroke(1, tag.color)
                                    }
                                    bookTagView.background = bookTagBg
                                    bookTagView.setTextColor(tag.color)
                                    
                                    // 添加右侧间距
                                    val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                    layoutParams.setMarginEnd(8)
                                    bookTagView.layoutParams = layoutParams
                                    
                                    // 测量标签宽度
                                    bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                    val tagWidth = bookTagView.measuredWidth + 8 // 加上右边距
                                    
                                    if (currentWidth + tagWidth <= containerWidth) {
                                        // 标签能容纳，添加到容器
                                        binding.tvTags.addView(bookTagView)
                                        currentWidth += tagWidth
                                    } else {
                                        // 标签超出容器，停止添加
                                        break
                                    }
                                }
                            } else {
                                // 容器宽度为0，添加布局监听器
                                binding.tvTags.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                    override fun onGlobalLayout() {
                                        // 移除监听器，避免重复调用
                                        binding.tvTags.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        
                                        // 清空标签容器
                                        binding.tvTags.removeAllViews()
                                        
                                        // 重新计算标签显示
                                        val containerWidth = binding.tvTags.width
                                        if (containerWidth > 0) {
                                            var currentWidth = 0
                                            for (tag in tags) {
                                                val bookTagView = android.widget.TextView(binding.root.context)
                                                bookTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                                                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                                                )
                                                bookTagView.setText(tag.name)
                                                bookTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                                                bookTagView.setPadding(8, 0, 8, 0)
                                                bookTagView.setGravity(android.view.Gravity.CENTER)
                                                bookTagView.setSingleLine(true)
                                                bookTagView.setEllipsize(null)
                                                bookTagView.setMaxLines(1)
                                                val bookTagBg = binding.root.context.resources.getDrawable(io.legado.app.R.drawable.bg_tag_rectangle)
                                                val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24)
                                                bookTagBg?.setTint(bookTagBackgroundColor)
                                                if (bookTagBg is android.graphics.drawable.GradientDrawable) {
                                                    bookTagBg.setStroke(1, tag.color)
                                                }
                                                bookTagView.background = bookTagBg
                                                bookTagView.setTextColor(tag.color)
                                                
                                                val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                                layoutParams.setMarginEnd(8)
                                                bookTagView.layoutParams = layoutParams
                                                
                                                // 测量标签宽度
                                                bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                                val tagWidth = bookTagView.measuredWidth + 8
                                                
                                                if (currentWidth + tagWidth <= containerWidth) {
                                                    binding.tvTags.addView(bookTagView)
                                                    currentWidth += tagWidth
                                                } else {
                                                    // 超出容器，停止添加
                                                    break
                                                }
                                            }
                                        }
                                    }
                                })
                            }
                        }
                            
                            // 如果之前有标签，保持高度不变，避免跳动
                            if (currentHeight > 0) {
                                binding.tvTags.layoutParams = binding.tvTags.layoutParams.apply {
                                    height = currentHeight
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 发生异常时，清空标签容器
                    withContext<Unit>(Dispatchers.Main) {
                        binding.tvTags.removeAllViews()
                    }
                }
            }
        }

        /**
         * 清除指定书籍的标签缓存
         */
        fun clearTagCache(bookUrl: String) {
            tagCache.remove(bookUrl)
        }

    }

    /**
    紧凑列表布局
     */
    inner class BookViewHolder2(val binding: ItemBookshelfList2Binding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book, position: Int) = binding.run {
            // 读取是否开启卡片背景
            val useCard = AppConfig.showBookCard

            if (useCard) {
                // 1. 显示卡片 + 上下占位
                cardBg.visibility = android.view.View.VISIBLE
                spaceTop.visibility = android.view.View.VISIBLE
                spaceBottom.visibility = android.view.View.VISIBLE

                // 2. 统一以屏幕边缘为参照
                val margin24 = 24 * context.resources.displayMetrics.density.toInt() // 内容距离屏幕边缘24dp
                val margin16 = 16 * context.resources.displayMetrics.density.toInt() // 卡片距离屏幕边缘16dp
                
                // 重置所有边距和padding
                cardBg.setPadding(0, 0, 0, 0)
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置卡片背景颜色
                cardBg.setBackgroundResource(R.drawable.bg_card_rounded)
                val cardColor = ThemeStore.backgroundCard(context)
                val background = cardBg.background
                if (background is android.graphics.drawable.GradientDrawable) {
                    background.setColor(cardColor)
                    val dividerColor = ThemeStore.dividerColor(context)
                    background.setStroke((AppConfig.bookshelfCardBorderWidth * 0.5f).dpToPx(), dividerColor)
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
                val margin16 = 16 * context.resources.displayMetrics.density.toInt()
                
                // 重置所有边距和padding
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置内容容器左右边距为16dp
                val layoutParams = layoutContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.leftMargin = margin16
                layoutParams.rightMargin = margin16
                layoutContent.layoutParams = layoutParams
            }
            
            tvName.text = item.getDisplayName()
            tvAuthor.text = "${item.author}·${item.getDisplayDurChapterTitle()}"
            tvLast.text = item.getDisplayLatestChapterTitle()
            ivCover.load(item, false)
            flHasNew.visible()
            ivAuthor.visible()
            upRefresh(this, item)
        }

        fun onBind(item: Book, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvName.text = item.getDisplayName()
                        "author" -> tvAuthor.text = "${item.author}·${item.getDisplayDurChapterTitle()}"
                        "dur" -> tvAuthor.text = "${item.author}·${item.getDisplayDurChapterTitle()}"
                        "last" -> tvLast.text = item.getDisplayLatestChapterTitle()
                            "cover" -> ivCover.load(
                                item,
                                false
                            )

                            "refresh" -> upRefresh(this, item)
                        }
                    }
                }
            }
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener {
                callBack.onItemClick(item)
            }
            binding.root.onLongClick {
                callBack.onItemLongClick(item)
            }
        }

        private fun upRefresh(binding: ItemBookshelfList2Binding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.bvUnread.invisible()
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
                if (AppConfig.showUnread) {
                    binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                    binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
                } else {
                    binding.bvUnread.invisible()
                }
            }
        }

    }

    inner class GroupViewHolder(val binding: ItemBookshelfListGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: BookGroup, position: Int) = binding.run {
            tvName.text = item.groupName
            tvName.setTextColor(ThemeStore.titleBarTextIconColor(context))
            ivCover.load(item.cover)
            flHasNew.gone()
            ivAuthor.gone()
            ivLast.gone()
            ivRead.gone()
            tvAuthor.gone()
            tvLast.gone()
            tvRead.gone()
        }

        fun onBind(item: BookGroup, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "groupName" -> tvName.text = item.groupName
                            "cover" -> ivCover.load(item.cover)
                        }
                    }
                }
            }
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener {
                callBack.onItemClick(item)
            }
            binding.root.onLongClick {
                callBack.onItemLongClick(item)
            }
        }

    }

    /**
     * 标签列表布局
     */
    inner class BookViewHolder3(val binding: ItemBookshelfListNewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: Book, position: Int) = binding.run {
            // 读取是否开启卡片背景
            val useCard = AppConfig.showBookCard

            if (useCard) {
                // 1. 显示卡片 + 上下占位
                cardBg.visibility = android.view.View.VISIBLE
                spaceTop.visibility = android.view.View.VISIBLE
                spaceBottom.visibility = android.view.View.VISIBLE

                // 2. 统一以屏幕边缘为参照
                val margin24 = 24 * context.resources.displayMetrics.density.toInt() // 内容距离屏幕边缘24dp
                val margin16 = 16 * context.resources.displayMetrics.density.toInt() // 卡片距离屏幕边缘16dp
                
                // 重置所有边距和padding
                cardBg.setPadding(0, 0, 0, 0)
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置卡片背景颜色
                cardBg.setBackgroundResource(R.drawable.bg_card_rounded)
                val cardColor = ThemeStore.backgroundCard(context)
                val background = cardBg.background
                if (background is android.graphics.drawable.GradientDrawable) {
                    background.setColor(cardColor)
                    val dividerColor = ThemeStore.dividerColor(context)
                    background.setStroke((AppConfig.bookshelfCardBorderWidth * 0.5f).dpToPx(), dividerColor)
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
                val margin16 = 16 * context.resources.displayMetrics.density.toInt()
                
                // 重置所有边距和padding
                layoutContent.setPadding(0, 0, 0, 0)
                
                // 设置内容容器左右边距为16dp
                val layoutParams = layoutContent.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                layoutParams.leftMargin = margin16
                layoutParams.rightMargin = margin16
                layoutContent.layoutParams = layoutParams
            }
            
            tvName.text = item.getDisplayName()
            tvAuthor.text = item.author
            ivCover.load(item, false)
            upRefresh(this, item)
            upRating(this, item)
            upProgress(this, item)
            upTags(this, item)
            upIntro(this, item)
            upReview(this, item)
        }

        fun onBind(item: Book, position: Int, payloads: MutableList<Any>) = binding.run {
            if (payloads.isEmpty()) {
                onBind(item, position)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "name" -> tvName.text = item.getDisplayName()
                            "author" -> tvAuthor.text = item.author
                            "cover" -> ivCover.load(
                                item,
                                false
                            )

                            "refresh" -> upRefresh(this, item)
                            "rating" -> upRating(this, item)
                            "tags" -> upTags(this, item)
                            "durChapterIndex" -> upProgress(this, item)
                            "intro" -> upIntro(this, item)
                            "review" -> upReview(this, item)
                        }
                    }
                }
            }
        }

        private fun upIntro(binding: ItemBookshelfListNewBinding, item: Book) {
            val showIntro = AppConfig.showBookIntro
            val introContent = item.getDisplayIntro()
            if (showIntro && !introContent.isNullOrBlank()) {
                binding.layoutIntro.visibility = android.view.View.VISIBLE
                binding.tvIntro.text = introContent
            } else {
                binding.layoutIntro.visibility = android.view.View.GONE
            }
        }

        private fun upReview(binding: ItemBookshelfListNewBinding, item: Book) {
            // 书评已迁移到 BookReview 表，书架列表不再显示
            binding.layoutReview.visibility = android.view.View.GONE
        }

        fun registerListener(item: Any) {
            binding.root.setOnClickListener {
                callBack.onItemClick(item)
            }
            binding.root.onLongClick {
                callBack.onItemLongClick(item)
            }
        }

        private fun upRefresh(binding: ItemBookshelfListNewBinding, item: Book) {
            if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
                binding.bvUnread.invisible()
                binding.rlLoading.visible()
            } else {
                binding.rlLoading.gone()
                if (AppConfig.showUnread) {
                    binding.bvUnread.setHighlight(item.lastCheckCount > 0)
                    binding.bvUnread.setBadgeCount(item.getUnreadChapterNum())
                } else {
                    binding.bvUnread.invisible()
                }
            }
        }

        private fun upRating(binding: ItemBookshelfListNewBinding, item: Book) {
            // 设置星级评分
            try {
                binding.ratingView.setRating(item.rating)
                // 禁用点击功能，只作为展示
                binding.ratingView.setClickableView(false)
            } catch (e: Exception) {
                // 处理可能的异常
            }
        }

        private fun upProgress(binding: ItemBookshelfListNewBinding, item: Book) {
            // 计算并显示阅读进度
            try {
                val progress = ReadingProgressHelper.calculateReadingProgress(item).toInt()
                binding.tvProgress.text = "${progress}%"
            } catch (e: Exception) {
                // 处理可能的异常
                binding.tvProgress.text = "0%"
            }
        }

        // 缓存书籍标签信息，避免重复刷新
        private val tagCache = mutableMapOf<String, List<BookTag>>()

        private fun upTags(binding: ItemBookshelfListNewBinding, item: Book) {
            binding.tagContainer.tag = item.bookUrl
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    // 先获取当前缓存的标签
                    val cachedTags = tagCache[item.bookUrl]
                    
                    // 使用TagManager.loadBookTags()方法获取标签
                    var tags = TagManager.loadBookTags(item.bookUrl)
                    
                    // 如果没有标签，检查是否为正版书源，如果是则生成标签
                    if (tags.isEmpty()) {
                        val isOfficialSource = withContext(Dispatchers.IO) {
                            val bookSource = appDb.bookSourceDao.getBookSource(item.origin)
                            bookSource?.bookSourceGroup?.contains("正版") == true
                        }
                        
                        if (isOfficialSource && !item.kind.isNullOrBlank()) {
                            // 生成标签
                            val generatedTags = withContext(Dispatchers.IO) {
                                TagManager.generateTagsFromKind(item)
                            }
                            if (generatedTags.isNotEmpty()) {
                                // 重新加载标签，确保过滤被排除的标签
                                tags = TagManager.loadBookTags(item.bookUrl)
                            }
                        }
                    }
                    
                    // 无论是否有标签，都需要更新缓存并刷新UI
                    // 使用 == 比较列表内容（空列表也相等）
                    if (cachedTags == null || cachedTags != tags) {
                        // 标签发生变化（或缓存为空），更新缓存
                        tagCache[item.bookUrl] = tags
                        
                        // 在UI线程中设置标签
                        withContext(Dispatchers.Main) {
                            if (binding.tagContainer.tag != item.bookUrl) return@withContext
                            
                            val currentHeight = binding.tagContainer.height
                            
                            binding.tagContainer.removeAllViews()
                            
                            if (tags.isNotEmpty()) {
                            // 计算容器宽度，只添加能容纳的标签
                            val containerWidth = binding.tagContainer.width
                            if (containerWidth > 0) {
                                var currentWidth = 0
                                for (tag in tags) {
                                    val bookTagView = android.widget.TextView(binding.root.context)
                                    bookTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    bookTagView.setText(tag.name)
                                    bookTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                                    bookTagView.setPadding(8, 0, 8, 0) // 增加左右内边距
                                    bookTagView.setGravity(android.view.Gravity.CENTER)
                                    bookTagView.setSingleLine(true)
                                    bookTagView.setEllipsize(null) // 不显示省略号，确保标签完整显示
                                    bookTagView.setMaxLines(1)
                                    // 使用矩形背景
                                    val bookTagBg = binding.root.context.resources.getDrawable(R.drawable.bg_tag_rectangle)
                                    val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24) // 10%透明度
                                    bookTagBg?.setTint(bookTagBackgroundColor)
                                    bookTagView.background = bookTagBg
                                    bookTagView.setTextColor(tag.color)
                                    
                                    // 添加间距
                                    val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                    layoutParams.setMarginStart(10)
                                    bookTagView.layoutParams = layoutParams
                                    
                                    // 测量标签宽度
                                    bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                    val tagWidth = bookTagView.measuredWidth + 10 // 加上左边距
                                    
                                    if (currentWidth + tagWidth <= containerWidth) {
                                        // 标签能容纳，添加到容器
                                        binding.tagContainer.addView(bookTagView)
                                        currentWidth += tagWidth
                                    } else {
                                        // 标签超出容器，停止添加
                                        break
                                    }
                                }
                            } else {
                                // 容器宽度为0，添加布局监听器
                                binding.tagContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                    override fun onGlobalLayout() {
                                        // 移除监听器，避免重复调用
                                        binding.tagContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                        
                                        // 清空标签容器
                                        binding.tagContainer.removeAllViews()
                                        
                                        // 重新计算标签显示
                                        val containerWidth = binding.tagContainer.width
                                        if (containerWidth > 0) {
                                            var currentWidth = 0
                                            for (tag in tags) {
                                                val bookTagView = android.widget.TextView(binding.root.context)
                                                bookTagView.layoutParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                                                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                                                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                                                )
                                                bookTagView.setText(tag.name)
                                                bookTagView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                                                bookTagView.setPadding(8, 0, 8, 0)
                                                bookTagView.setGravity(android.view.Gravity.CENTER)
                                                bookTagView.setSingleLine(true)
                                                bookTagView.setEllipsize(null)
                                                bookTagView.setMaxLines(1)
                                                val bookTagBg = binding.root.context.resources.getDrawable(R.drawable.bg_tag_rectangle)
                                                val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24)
                                                bookTagBg?.setTint(bookTagBackgroundColor)
                                                bookTagView.background = bookTagBg
                                                bookTagView.setTextColor(tag.color)
                                                
                                                val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                                layoutParams.setMarginStart(10)
                                                bookTagView.layoutParams = layoutParams
                                                
                                                // 测量标签宽度
                                                bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                                val tagWidth = bookTagView.measuredWidth + 10
                                                
                                                if (currentWidth + tagWidth <= containerWidth) {
                                                    binding.tagContainer.addView(bookTagView)
                                                    currentWidth += tagWidth
                                                } else {
                                                    // 超出容器，停止添加
                                                    break
                                                }
                                            }
                                        }
                                    }
                                })
                            }
                        }
                            
                            // 如果之前有标签，保持高度不变，避免跳动
                            if (currentHeight > 0) {
                                binding.tagContainer.layoutParams = binding.tagContainer.layoutParams.apply {
                                    height = currentHeight
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 发生异常时，清空标签容器
                    withContext(Dispatchers.Main) {
                        binding.tagContainer.removeAllViews()
                    }
                }
            }
        }

        /**
         * 清除指定书籍的标签缓存
         */
        fun clearTagCache(bookUrl: String) {
            tagCache.remove(bookUrl)
        }
    }



}