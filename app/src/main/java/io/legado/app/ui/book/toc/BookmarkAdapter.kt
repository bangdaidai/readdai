package io.legado.app.ui.book.toc

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemBookmarkBinding
import io.legado.app.utils.gone
import splitties.views.onLongClick
import androidx.core.content.ContextCompat
import java.util.*

class BookmarkAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<Bookmark, ItemBookmarkBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookmarkBinding {
        return ItemBookmarkBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookmarkBinding,
        item: Bookmark,
        payloads: MutableList<Any>
    ) {
        binding.tvChapterName.text = item.chapterName
        // 隐藏书籍文本和内容，只显示章节名和时间
        binding.tvBookText.gone()
        binding.tvContent.gone()
        binding.tvTime.text = formatTime(item.time)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookmarkBinding) {
        binding.root.setOnClickListener {
            getItem(holder.layoutPosition)?.let { bookmark ->
                callback.onClick(bookmark)
            }
        }
        binding.root.onLongClick {
            getItem(holder.layoutPosition)?.let { bookmark ->
                callback.onLongClick(bookmark, holder.layoutPosition)
            }
        }

    }

    interface Callback {
        fun onClick(bookmark: Bookmark)
        fun onLongClick(bookmark: Bookmark, pos: Int)
    }

    private fun formatTime(time: Long): String {
        val date = Date(time)
        return DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
    }

}