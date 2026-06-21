package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.data.appDb
import io.legado.app.databinding.ItemBookshelfListNewBinding
import io.legado.app.help.book.ReadingProgressHelper
import io.legado.app.help.book.TagManager
import io.legado.app.help.book.getDisplayName
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore

class BooksAdapterList3(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfListNewBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfListNewBinding {
        return ItemBookshelfListNewBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfListNewBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
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
        
        if (payloads.isEmpty()) {
            tvName.text = item.getDisplayName()
            tvAuthor.text = item.author
            ivCover.load(item, false)
            upRefresh(binding, item)
            upRating(binding, item)
            upProgress(binding, item)
            upTags(binding, item)
            upIntro(binding, item)
            upReview(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.getDisplayName()
                        "author" -> tvAuthor.text = item.author
                        "cover" -> ivCover.load(
                            item,
                            false,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "rating" -> upRating(binding, item)
                        "tags" -> upTags(binding, item)
                        "lastUpdateTime" -> {}
                        "durChapterIndex" -> upProgress(binding, item)
                        "intro" -> upIntro(binding, item)
                        "review" -> upReview(binding, item)

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

        // 关键修复：立即清空标签容器，防止ViewHolder被复用时显示上一本书的标签
        binding.tagContainer.removeAllViews()

        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 先获取当前缓存的标签
                val cachedTags = tagCache[item.bookUrl]

                // 使用TagManager.loadBookTags()方法获取标签，确保与阅读详情页面使用相同的逻辑
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

                // 更新缓存
                tagCache[item.bookUrl] = tags

                // 如果缓存未变化且标签为空，无需刷新UI
                if (cachedTags != null && cachedTags == tags && tags.isEmpty()) {
                    return@launch
                }

                // 在UI线程中设置标签
                withContext(Dispatchers.Main) {
                    if (binding.tagContainer.tag != item.bookUrl) return@withContext

                    val currentHeight = binding.tagContainer.height

                    // 清空标签容器（防止延迟回调残留）
                    binding.tagContainer.removeAllViews()

                    if (tags.isNotEmpty()) {
                        // 计算容器宽度，只添加能容纳的标签
                        val containerWidth = binding.tagContainer.width
                        if (containerWidth > 0) {
                            var currentWidth = 0
                            for (tag in tags) {
                                val bookTagView = android.widget.TextView(context)
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
                                val bookTagBg = context.resources.getDrawable(R.drawable.bg_tag_rectangle)
                                val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24)
                                bookTagBg?.setTint(bookTagBackgroundColor)
                                bookTagView.background = bookTagBg
                                bookTagView.setTextColor(tag.color)

                                val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                layoutParams.setMarginStart(10)
                                bookTagView.layoutParams = layoutParams

                                bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                val tagWidth = bookTagView.measuredWidth + 10

                                if (currentWidth + tagWidth <= containerWidth) {
                                    binding.tagContainer.addView(bookTagView)
                                    currentWidth += tagWidth
                                } else {
                                    break
                                }
                            }
                        } else {
                            // 容器宽度为0，添加布局监听器
                            binding.tagContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    binding.tagContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)

                                    // 校验ViewHolder是否仍然有效
                                    if (binding.tagContainer.tag != item.bookUrl) return

                                    binding.tagContainer.removeAllViews()

                                    val containerWidth = binding.tagContainer.width
                                    if (containerWidth > 0) {
                                        var currentWidth = 0
                                        for (tag in tags) {
                                            val bookTagView = android.widget.TextView(context)
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
                                            val bookTagBg = context.resources.getDrawable(R.drawable.bg_tag_rectangle)
                                            val bookTagBackgroundColor = tag.color and 0x00FFFFFF or (0x1A shl 24)
                                            bookTagBg?.setTint(bookTagBackgroundColor)
                                            bookTagView.background = bookTagBg
                                            bookTagView.setTextColor(tag.color)

                                            val layoutParams = bookTagView.layoutParams as com.google.android.flexbox.FlexboxLayout.LayoutParams
                                            layoutParams.setMarginStart(10)
                                            bookTagView.layoutParams = layoutParams

                                            bookTagView.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
                                            val tagWidth = bookTagView.measuredWidth + 10

                                            if (currentWidth + tagWidth <= containerWidth) {
                                                binding.tagContainer.addView(bookTagView)
                                                currentWidth += tagWidth
                                            } else {
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
    


    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfListNewBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it)
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it)
                }
            }
        }
    }
}