package io.legado.app.ui.book.annotation

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.ItemAnnotationBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import splitties.views.onClick
import java.util.*

class AnnotationAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<BookAnnotation, ItemAnnotationBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemAnnotationBinding {
        return ItemAnnotationBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAnnotationBinding,
        item: BookAnnotation,
        payloads: MutableList<Any>
    ) {
        // 显示书名（仅在分组的第一个item显示）
        val position = holder.layoutPosition
        if (isItemHeader(position)) {
            binding.tvBookName.text = "${item.bookName}(${item.bookAuthor})"
            binding.tvBookName.visible()
        } else {
            binding.tvBookName.gone()
        }

        binding.tvChapterName.text = item.chapterName
        binding.tvBookText.gone(item.bookText.isEmpty())
        binding.tvBookText.text = item.bookText
        binding.noteContainer.gone(item.content.isEmpty())
        binding.noteLine.setBackgroundColor(ThemeStore.primaryColor(context))
        binding.tvContent.text = item.content
        binding.tvTime.text = formatTime(item.time)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAnnotationBinding) {
        binding.root.onClick {
            getItemByLayoutPosition(holder.layoutPosition)?.let {
                callback.onItemClick(it, holder.layoutPosition)
            }
        }
    }

    fun getHeaderText(position: Int): String {
        return with(getItem(position)) {
            "${this?.bookName ?: ""}(${this?.bookAuthor ?: ""})"
        }
    }

    fun isItemHeader(position: Int): Boolean {
        if (position == 0) return true
        val lastItem = getItem(position - 1)
        val curItem = getItem(position)
        return !(lastItem?.bookName == curItem?.bookName
                && lastItem?.bookAuthor == curItem?.bookAuthor)
    }

    interface Callback {

        fun onItemClick(annotation: BookAnnotation, position: Int)

    }

    private fun formatTime(time: Long): String {
        val date = Date(time)
        return DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
    }

}