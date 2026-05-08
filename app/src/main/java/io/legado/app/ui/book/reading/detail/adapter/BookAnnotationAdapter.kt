package io.legado.app.ui.book.reading.detail.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.ItemAnnotationBinding
import io.legado.app.ui.book.annotation.BookAnnotationDialog
import java.text.SimpleDateFormat
import java.util.*

class BookAnnotationAdapter : ListAdapter<BookAnnotation, BookAnnotationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnnotationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAnnotationBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(annotation: BookAnnotation) {
            binding.tvChapterName.text = annotation.chapterName
            binding.tvTime.text = dateFormat.format(Date(annotation.time))
            
            // 显示原文
            if (annotation.bookText.isNotEmpty()) {
                binding.tvBookText.text = annotation.bookText
                binding.tvBookText.visibility = View.VISIBLE
            } else {
                binding.tvBookText.visibility = View.GONE
            }
            
            // 显示书摘内容
            if (annotation.content.isNotEmpty()) {
                binding.tvContent.text = annotation.content
                binding.tvContent.visibility = View.VISIBLE
            } else {
                binding.tvContent.visibility = View.GONE
            }
            
            // 设置点击事件
            binding.root.setOnClickListener {
                // 打开书摘编辑对话框
                val context = binding.root.context
                if (context is FragmentActivity) {
                    val dialog = BookAnnotationDialog(annotation)
                    dialog.show(context.supportFragmentManager, "bookAnnotationDialog")
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BookAnnotation>() {
        override fun areItemsTheSame(oldItem: BookAnnotation, newItem: BookAnnotation): Boolean {
            return oldItem.time == newItem.time
        }

        override fun areContentsTheSame(oldItem: BookAnnotation, newItem: BookAnnotation): Boolean {
            return oldItem == newItem
        }
    }
}