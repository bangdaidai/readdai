package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfList2Binding
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.getDisplayDurChapterTitle
import io.legado.app.help.book.getDisplayLatestChapterTitle
import io.legado.app.help.book.getDisplayName
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.invisible
import io.legado.app.utils.toTimeAgo
import splitties.views.onLongClick
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.R

/**
紧凑列表布局
*/
class BooksAdapterList2(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfList2Binding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfList2Binding {
        return ItemBookshelfList2Binding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfList2Binding,
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
                // 设置边框
                if (AppConfig.showCardBorder) {
                    val dividerColor = ThemeStore.dividerColor(context)
                    background.setStroke(2, dividerColor)
                } else {
                    background.setStroke(0, 0)
                }
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
            tvAuthor.text = "${item.author}·${item.getDisplayDurChapterTitle()}"
            tvLast.text = item.getDisplayLatestChapterTitle()
            ivCover.load(item, false)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
            // 加载简介
            upIntro(binding, item)
            // 加载书评
            upReview(binding, item)
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
                            false,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                        "intro" -> upIntro(binding, item)
                        "review" -> upReview(binding, item)
                    }
                }
            }
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

    private fun upLastUpdateTime(binding: ItemBookshelfList2Binding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
            }
        } else {
            binding.tvLastUpdateTime.text = ""
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfList2Binding) {
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

    private fun upIntro(binding: ItemBookshelfList2Binding, item: Book) {
        val showIntro = AppConfig.showBookIntro
        val introContent = item.getDisplayIntro()
        if (showIntro && !introContent.isNullOrBlank()) {
            binding.layoutIntro.visibility = android.view.View.VISIBLE
            binding.tvIntro.text = introContent
        } else {
            binding.layoutIntro.visibility = android.view.View.GONE
        }
    }

    private fun upReview(binding: ItemBookshelfList2Binding, item: Book) {
        // 书评已迁移到 BookReview 表，书架列表不再显示
        binding.layoutReview.visibility = android.view.View.GONE
    }
}
