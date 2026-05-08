package io.legado.app.ui.widget.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ItemBookTagSelectBinding

class BookTagSelectAdapter : ListAdapter<BookTag, BookTagSelectAdapter.ViewHolder>(DiffCallback) {

    var onItemClickListener: ((BookTag) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookTagSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBookTagSelectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tag: BookTag) {
            binding.tvTagName.text = tag.name
            
            // 创建圆角背景并设置颜色（使用25%透明度）
            val drawable = binding.root.context.getDrawable(io.legado.app.R.drawable.bg_tag_rounded)
            val backgroundColor = tag.color and 0x00FFFFFF or (0x40000000) // 25%透明度
            drawable?.setTint(backgroundColor)
            binding.tvTagName.background = drawable
            
            // 字体颜色使用选择颜色本身
            binding.tvTagName.setTextColor(tag.color)
            
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(tag)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BookTag>() {
        override fun areItemsTheSame(oldItem: BookTag, newItem: BookTag): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookTag, newItem: BookTag): Boolean {
            return oldItem.name == newItem.name
        }
    }
}