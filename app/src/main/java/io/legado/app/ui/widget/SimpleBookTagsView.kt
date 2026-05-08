package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import io.legado.app.R
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ViewBookTagsBinding
import io.legado.app.ui.widget.adapter.BookTagsAdapter

/**
 * 简化版书籍标签容器视图，不包含添加按钮功能
 * 专用于我的阅读详情页面
 */
class SimpleBookTagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBookTagsBinding.inflate(LayoutInflater.from(context), this, true)
    private var adapter: BookTagsAdapter? = null
    private var onTagClickListener: ((String) -> Unit)? = null
    private var onTagDeleteListener: ((String) -> Unit)? = null
    private var showDeleteButton = false
    private var enableDelete = false

    init {
        initRecyclerView()
    }

    private fun initRecyclerView() {
        // 使用FlexboxLayoutManager替代LinearLayoutManager，实现标签自动换行
        val flexboxLayoutManager = FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.FLEX_START
            alignItems = AlignItems.CENTER
            flexWrap = FlexWrap.WRAP
        }
        binding.recyclerView.layoutManager = flexboxLayoutManager
        adapter = BookTagsAdapter().apply {
            setOnTagClickListener { tag ->
                onTagClickListener?.invoke(tag)
            }
            setOnTagDeleteListener { tag ->
                onTagDeleteListener?.invoke(tag)
            }
            // 不设置添加按钮监听器，因为这个组件不包含添加按钮功能
            setShowAddButton(false)
        }
        binding.recyclerView.adapter = adapter
    }

    /**
     * 设置标签数据
     */
    fun setTags(tags: List<BookTag>) {
        adapter?.setTags(tags.map { it.name to it.color })
        updateEmptyView(tags.map { it.name })
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
     * 刷新视图
     */
    fun refresh() {
        adapter?.notifyDataSetChanged()
    }
    
    /**
     * 更新空视图显示
     */
    private fun updateEmptyView(tags: List<String>) {
        binding.tvEmpty.isVisible = tags.isEmpty()
        binding.recyclerView.isVisible = tags.isNotEmpty()
    }
}