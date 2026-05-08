package io.legado.app.ui.book.tag

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.ItemBookTagSelectBinding
import io.legado.app.databinding.ItemTagGroupHeaderBinding

/**
 * 书籍标签选择适配器
 */
class BookTagSelectAdapter(
    private val onTagClick: (BookTag) -> Unit
) : ListAdapter<BookTagSelectAdapter.Item, BookTagSelectAdapter.ViewHolder>(DiffCallback) {

    sealed class Item {
        data class Header(val groupId: Long, val groupName: String) : Item()
        data class Tag(val tag: BookTag) : Item()
    }

    class ViewHolder private constructor(private val binding: Any) :
        RecyclerView.ViewHolder((binding as? ItemBookTagSelectBinding)?.root ?: (binding as ItemTagGroupHeaderBinding).root) {
        
        fun bind(item: Item, onTagClick: (BookTag) -> Unit) {
            when (item) {
                is Item.Header -> {
                    val headerBinding = binding as ItemTagGroupHeaderBinding
                    headerBinding.tvGroupName.text = item.groupName
                }
                is Item.Tag -> {
                    val tagBinding = binding as ItemBookTagSelectBinding
                    tagBinding.tvTagName.text = item.tag.name
                    
                    // 创建圆角背景并设置颜色
                    val drawable = tagBinding.root.context.getDrawable(R.drawable.bg_tag_rounded)
                    drawable?.setTint(item.tag.color)
                    tagBinding.tvTagName.background = drawable
                    
                    // 根据背景颜色亮度设置文字颜色
                    tagBinding.tvTagName.setTextColor(
                        if (calculateLuminance(item.tag.color) > 0.5) {
                            android.graphics.Color.BLACK
                        } else {
                            android.graphics.Color.WHITE
                        }
                    )
                    
                    tagBinding.root.setOnClickListener {
                        onTagClick(item.tag)
                    }
                }
            }
        }
        
        /**
         * 计算颜色亮度，替代ColorUtils.calculateLuminance
         */
        private fun calculateLuminance(color: Int): Float {
            val r = android.graphics.Color.red(color) / 255.0f
            val g = android.graphics.Color.green(color) / 255.0f
            val b = android.graphics.Color.blue(color) / 255.0f
            return (0.2126f * r + 0.7152f * g + 0.0722f * b)
        }

        companion object {
            fun create(parent: ViewGroup, viewType: Int): ViewHolder {
                return when (viewType) {
                    0 -> {
                        val binding = ItemBookTagSelectBinding.inflate(
                            LayoutInflater.from(parent.context), parent, false
                        )
                        ViewHolder(binding)
                    }
                    1 -> {
                        val binding = ItemTagGroupHeaderBinding.inflate(
                            LayoutInflater.from(parent.context), parent, false
                        )
                        ViewHolder(binding)
                    }
                    else -> throw IllegalArgumentException("Unknown view type")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.create(parent, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onTagClick)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Item.Header -> 1
            is Item.Tag -> 0
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when {
                oldItem is Item.Header && newItem is Item.Header -> 
                    oldItem.groupId == newItem.groupId
                oldItem is Item.Tag && newItem is Item.Tag -> 
                    oldItem.tag.id == newItem.tag.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when {
                oldItem is Item.Header && newItem is Item.Header -> 
                    oldItem.groupName == newItem.groupName
                oldItem is Item.Tag && newItem is Item.Tag -> 
                    oldItem.tag.name == newItem.tag.name && oldItem.tag.color == newItem.tag.color
                else -> false
            }
        }
    }
}