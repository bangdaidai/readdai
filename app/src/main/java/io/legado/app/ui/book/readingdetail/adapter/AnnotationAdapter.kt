package io.legado.app.ui.book.readingdetail.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemAnnotationBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.gone
import java.text.SimpleDateFormat
import java.util.*

class AnnotationAdapter : ListAdapter<Bookmark, AnnotationAdapter.ViewHolder>(DiffCallback()) {

    private var onItemClickListener: ((Bookmark) -> Unit)? = null
    
    fun setOnItemClickListener(listener: (Bookmark) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnnotationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAnnotationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: Bookmark) {
            binding.tvChapterName.text = bookmark.chapterName
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.tvTime.text = dateFormat.format(Date(bookmark.time))
            binding.tvBookText.text = bookmark.bookText
            binding.tvContent.text = bookmark.content
            binding.noteContainer.gone(bookmark.content.isEmpty())
            binding.noteLine.setBackgroundColor(ThemeStore.primaryColor(binding.root.context))
            
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(bookmark)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Bookmark>() {
        override fun areItemsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
            return oldItem.time == newItem.time
        }

        override fun areContentsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean {
            return oldItem == newItem
        }
    }
}