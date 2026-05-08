package io.legado.app.ui.widget.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ViewBookTagBinding
import io.legado.app.databinding.ViewBookTagAddBinding
import io.legado.app.ui.widget.anima.explosion_field.ExplosionView

/**
 * 书籍标签适配器
 */
class BookTagsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_TAG = 0
        private const val TYPE_ADD = 1
    }

    private var tags = mutableListOf<Pair<String, Int>>() // 标签名称和颜色的配对
    private var showDeleteButton = false
    private var enableDelete = false
    private var showAddButton = false
    private var onTagClickListener: ((String) -> Unit)? = null
    private var onTagDeleteListener: ((String) -> Unit)? = null
    private var onAddTagClickListener: (() -> Unit)? = null
    private var explosionField: ExplosionView? = null

    /**
     * 设置标签数据
     */
    fun setTags(newTags: List<Pair<String, Int>>) {
        // 确保标签列表去重，避免重复显示
        val uniqueTags = newTags.distinctBy { it.first }
        tags.clear()
        tags.addAll(uniqueTags)
        notifyDataSetChanged()
    }

    /**
     * 设置删除按钮是否可见
     */
    fun setShowDeleteButton(show: Boolean) {
        if (showDeleteButton != show) {
            showDeleteButton = show
            notifyDataSetChanged()
        }
    }

    /**
     * 设置是否启用删除功能
     */
    fun setEnableDelete(enable: Boolean) {
        if (enableDelete != enable) {
            enableDelete = enable
            notifyDataSetChanged()
        }
    }

    /**
     * 设置是否显示添加按钮
     */
    fun setShowAddButton(show: Boolean) {
        if (showAddButton != show) {
            showAddButton = show
            notifyDataSetChanged()
        }
    }

    /**
     * 设置标签点击监听器
     */
    fun setOnTagClickListener(listener: (String) -> Unit) {
        this.onTagClickListener = listener
    }

    /**
     * 设置标签删除监听器
     */
    fun setOnTagDeleteListener(listener: (String) -> Unit) {
        this.onTagDeleteListener = listener
    }

    /**
     * 直接删除标签（用于长按删除，不显示对话框）
     */
    fun deleteTagDirectly(tagName: String) {
        onTagDeleteListener?.invoke(tagName)
    }

    /**
     * 设置添加标签点击监听器
     */
    fun setOnAddTagClickListener(listener: () -> Unit) {
        this.onAddTagClickListener = listener
    }

    /**
     * 设置爆炸效果实例
     */
    fun setExplosionField(explosionField: ExplosionView) {
        this.explosionField = explosionField
    }

    override fun getItemViewType(position: Int): Int {
        return if (showAddButton && position == tags.size) {
            TYPE_ADD
        } else {
            TYPE_TAG
        }
    }

    override fun getItemCount(): Int {
        // 当没有标签时，不显示添加按钮
        return tags.size + if (showAddButton && tags.isNotEmpty()) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ADD -> {
                val binding = ViewBookTagAddBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                AddTagViewHolder(binding)
            }
            else -> {
                val binding = ViewBookTagBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TagViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TagViewHolder -> {
                val tag = tags[position]
                holder.bind(tag)
            }
            is AddTagViewHolder -> {
                holder.bind()
            }
        }
    }

    inner class TagViewHolder(private val binding: ViewBookTagBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tag: Pair<String, Int>) {
            binding.tvTagName.text = tag.first

            // 创建圆角背景并设置颜色（使用10%透明度）
            val drawable = binding.root.context.getDrawable(R.drawable.bg_tag_rounded)
            val backgroundColor = tag.second and 0x00FFFFFF or (0x1A000000) // 10%透明度
            drawable?.setTint(backgroundColor)
            binding.tvTagName.background = drawable

            // 字体颜色使用选择颜色本身
            binding.tvTagName.setTextColor(tag.second)

            // 设置删除按钮可见性
            binding.btnDelete.isVisible = showDeleteButton

            // 设置点击监听器
            binding.root.setOnClickListener {
                onTagClickListener?.invoke(tag.first)
            }

            // 设置删除按钮点击监听器
            binding.btnDelete.setOnClickListener {
                if (enableDelete) {
                    onTagDeleteListener?.invoke(tag.first)
                }
            }

            // 设置长按监听器，用于删除标签
            binding.root.setOnLongClickListener {
                if (enableDelete) {
                    // 执行爆炸动画
                    explosionField?.explode(it, true)
                    // 延迟执行删除操作，等待动画完成
                    binding.root.postDelayed({
                        deleteTagDirectly(tag.first)
                    }, 200)
                    true
                } else {
                    false
                }
            }
        }
    }

    inner class AddTagViewHolder(private val binding: ViewBookTagAddBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            // 设置点击监听器
            binding.root.setOnClickListener {
                onAddTagClickListener?.invoke()
            }
        }
    }
}