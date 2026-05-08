package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import android.graphics.Rect
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import io.legado.app.R
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ViewBookTagsBinding
import io.legado.app.ui.widget.adapter.BookTagsAdapter

/**
 * 书籍标签容器视图
 */
class BookTagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBookTagsBinding.inflate(LayoutInflater.from(context), this, true)
    private var adapter: BookTagsAdapter? = null
    private var onTagClickListener: ((String) -> Unit)? = null
    private var onTagDeleteListener: ((String) -> Unit)? = null
    private var onAddTagClickListener: (() -> Unit)? = null
    private var showDeleteButton = false
    private var enableDelete = false

    init {
        initRecyclerView()
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.CENTER
            flexWrap = FlexWrap.WRAP
        }
        // 添加 ItemDecoration 控制标签间距
        binding.recyclerView.addItemDecoration(object : ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val position = parent.getChildAdapterPosition(view)
                if (position > 0) {
                    outRect.left = 4 // 标签之间的水平间距
                }
            }
        })
        adapter = BookTagsAdapter().apply {
            setOnTagClickListener { tag ->
                onTagClickListener?.invoke(tag)
            }
            setOnTagDeleteListener { tag ->
                onTagDeleteListener?.invoke(tag)
            }
            setOnAddTagClickListener {
                onAddTagClickListener?.invoke()
            }
        }
        binding.recyclerView.adapter = adapter
    }

    /**
     * 设置标签数据
     */
    fun setTags(tags: List<BookTag>) {
        // 确保标签列表去重，避免重复显示
        val uniqueTags = tags.distinctBy { it.id }
        adapter?.setTags(uniqueTags.map { it.name to it.color })
        updateEmptyView(uniqueTags.map { it.name })
    }

    /**
     * 设置删除按钮是否可见
     */
    fun setDeleteButtonVisible(show: Boolean) {
        this.showDeleteButton = show
        adapter?.setShowDeleteButton(show)
    }
    
    /**
     * 设置是否启用删除功能
     */
    fun setDeleteEnabled(enable: Boolean) {
        this.enableDelete = enable
        adapter?.setEnableDelete(enable)
    }

    /**
     * 设置是否显示添加按钮
     */
    fun setShowAddButton(show: Boolean) {
        adapter?.setShowAddButton(show)
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
     * 设置添加标签点击监听器
     */
    fun setOnAddTagClickListener(listener: () -> Unit) {
        this.onAddTagClickListener = listener
    }
    
    /**
     * 设置爆炸效果实例
     */
    fun setExplosionField(explosionField: io.legado.app.ui.widget.anima.explosion_field.ExplosionView) {
        adapter?.setExplosionField(explosionField)
    }

    /**
     * 刷新视图
     */
    fun refresh() {
        adapter?.notifyDataSetChanged()
    }
    
    /**
     * 更新空视图显示
     */
    private fun updateEmptyView(tags: List<String>) {
        // 当标签为空时，隐藏空状态文本和RecyclerView，并设置整个视图高度为0
        binding.tvEmpty.isVisible = false
        binding.recyclerView.isVisible = tags.isNotEmpty()
        
        // 当没有标签时，设置整个视图的高度为0，避免占用不必要空间
        if (tags.isEmpty()) {
            layoutParams = layoutParams.apply {
                height = 0
            }
        } else {
            layoutParams = layoutParams.apply {
                height = LinearLayout.LayoutParams.WRAP_CONTENT
            }
        }
    }
}