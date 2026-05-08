package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ViewBookTagBinding

/**
 * 书籍标签视图
 */
class BookTagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBookTagBinding.inflate(LayoutInflater.from(context), this, true)
    
    private var tag: BookTag? = null
    private var onTagClickListener: ((BookTag) -> Unit)? = null
    private var onTagDeleteListener: ((BookTag) -> Unit)? = null
    private var showDeleteButton = false

    init {
        initClickListener()
    }

    private fun initClickListener() {
        binding.root.setOnClickListener {
            tag?.let { tag ->
                onTagClickListener?.invoke(tag)
            }
        }
        
        binding.btnDelete.setOnClickListener {
            tag?.let { tag ->
                onTagDeleteListener?.invoke(tag)
            }
        }
    }

    /**
     * 设置标签数据
     */
    fun setTag(tag: BookTag) {
        this.tag = tag
        binding.tvTagName.text = tag.name
        
        // 设置标签颜色
        binding.tagContainer.setBackgroundColor(tag.color)
        
        // 设置删除按钮可见性
        binding.btnDelete.isVisible = showDeleteButton
    }

    /**
     * 设置删除按钮是否可见
     */
    fun setShowDeleteButton(show: Boolean) {
        this.showDeleteButton = show
        binding.btnDelete.isVisible = show
    }

    /**
     * 设置标签点击监听器
     */
    fun setOnTagClickListener(listener: (BookTag) -> Unit) {
        this.onTagClickListener = listener
    }

    /**
     * 设置标签删除监听器
     */
    fun setOnTagDeleteListener(listener: (BookTag) -> Unit) {
        this.onTagDeleteListener = listener
    }

    /**
     * 获取当前标签
     */
    override fun getTag(): BookTag? {
        return tag
    }
}