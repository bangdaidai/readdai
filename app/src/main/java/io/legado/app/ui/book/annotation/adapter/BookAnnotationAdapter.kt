package io.legado.app.ui.book.annotation.adapter

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.ItemAnnotationBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.gone
import java.util.*

class BookAnnotationAdapter(context: Context, private val callBack: Callback) :
    RecyclerAdapter<BookAnnotation, ItemAnnotationBinding>(context) {

    interface Callback {
        fun onItemClick(bookAnnotation: BookAnnotation, position: Int)
    }

    override fun getViewBinding(parent: ViewGroup): ItemAnnotationBinding {
        return ItemAnnotationBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemAnnotationBinding,
        item: BookAnnotation,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            tvChapterName.text = item.chapterName
            tvBookText.text = item.bookText
            tvContent.text = item.content
            tvBookText.gone(item.bookText.isEmpty())
            noteContainer.gone(item.content.isEmpty())
            noteLine.setBackgroundColor(ThemeStore.primaryColor(context))
            tvTime.text = formatTime(item.time)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemAnnotationBinding) {
        binding.root.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.onItemClick(it, holder.layoutPosition)
            }
        }
    }

    fun getHeaderText(item: BookAnnotation): String {
        return item.bookName
    }

    fun isItemHeader(position: Int): Boolean {
        if (position == 0) return true
        val current = getItem(position) ?: return false
        val previous = getItem(position - 1) ?: return false
        return current.bookName != previous.bookName
    }

    private fun formatTime(time: Long): String {
        val date = Date(time)
        return DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
    }
}