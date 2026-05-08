package io.legado.app.ui.book.vector

import android.content.Context
import android.text.format.DateFormat
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.VectorizedBookEntity
import io.legado.app.databinding.ItemVectorBookBinding
import splitties.views.onClick
import splitties.views.onLongClick
import java.util.*

class VectorBooksAdapter(context: Context, private val callback: Callback) :
    RecyclerAdapter<VectorizedBookEntity, ItemVectorBookBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemVectorBookBinding {
        return ItemVectorBookBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemVectorBookBinding,
        item: VectorizedBookEntity,
        payloads: MutableList<Any>
    ) {
        binding.tvBookTitle.text = item.bookTitle
        binding.tvStats.text = buildString {
            append("文本块: ${item.totalChunks}")
            append(" | ")
            append("向量: ${item.totalVectors}")
            append(" | ")
            append("分块大小: ${item.chunkSize}")
        }
        binding.tvModel.text = "${item.modelProvider} / ${item.modelName}"
        binding.tvDate.text = "创建于: ${formatDate(item.createdAt)}"
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemVectorBookBinding) {
        binding.root.onClick {
            getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                callback.onItemClick(item)
            }
        }
        binding.root.onLongClick {
            getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                callback.onItemLongClick(item)
            }
            true
        }
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        return DateFormat.format("yyyy-MM-dd HH:mm", date).toString()
    }

    interface Callback {
        fun onItemClick(book: VectorizedBookEntity)
        fun onItemLongClick(book: VectorizedBookEntity)
    }
}