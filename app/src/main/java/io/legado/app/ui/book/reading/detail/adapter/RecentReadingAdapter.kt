package io.legado.app.ui.book.reading.detail.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.databinding.ItemRecentReadingBinding
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.visible

class RecentReadingAdapter :
    ListAdapter<ReadSession, RecentReadingAdapter.ViewHolder>(DiffCallback) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentReadingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(it)
        }
    }
    
    inner class ViewHolder(private val binding: ItemRecentReadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(session: ReadSession) {
            binding.apply {
                // 显示书籍名称
                tvChapterTitle.text = session.bookName
                
                // 显示阅读时间
                tvReadTime.text = formatTime(session.duration)
                
                // 显示最后阅读日期
                tvReadDate.text = formatDate(session.endTime)
                
                // 隐藏进度和字数，因为ReadSession类中没有这些属性
                tvReadProgress.visibility = View.GONE
                tvWordCount.visibility = View.GONE
            }
        }
        
        private fun formatTime(milliseconds: Long): String {
            val minutes = milliseconds / (60 * 1000)
            return when {
                minutes < 60 -> "$minutes 分钟"
                minutes < 1440 -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
                else -> {
                    val days = minutes / 1440
                    val hours = (minutes % 1440) / 60
                    val mins = minutes % 60
                    "$days 天 $hours 小时 $mins 分钟"
                }
            }
        }
        
        private fun formatDate(timestamp: Long): String {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            return format.format(date)
        }
    }
    
    private object DiffCallback : DiffUtil.ItemCallback<ReadSession>() {
        override fun areItemsTheSame(oldItem: ReadSession, newItem: ReadSession): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: ReadSession, newItem: ReadSession): Boolean {
            return oldItem == newItem
        }
    }
}