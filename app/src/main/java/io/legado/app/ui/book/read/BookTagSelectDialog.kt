package io.legado.app.ui.book.read

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.DialogBookTagSelectBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.tag.BookTagSelectAdapter
import io.legado.app.ui.widget.dialog.BookTagEditDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 书籍标签选择对话框
 */
class BookTagSelectDialog : BaseDialogFragment(R.layout.dialog_book_tag_select) {

    private val binding by viewBinding(DialogBookTagSelectBinding::bind)
    private var callback: ((String) -> Unit)? = null
    private var bookUrl: String? = null
    private var allTags: List<BookTag> = emptyList()
    private var groups: List<BookTagGroup> = emptyList()
    private var adapter: BookTagSelectAdapter? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.7f) // 保留高度设置
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 调用父类方法，确保binding被正确初始化
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(ThemeStore.primaryColor(requireContext()))
        
        // 获取标题栏文字图标颜色
        val titleBarTextColor = ThemeStore.titleBarTextIconColor(requireContext())
        
        // 设置标题文字颜色
        binding.tvTitle.setTextColor(titleBarTextColor)
        
        // 获取参数
        bookUrl = arguments?.getString("bookUrl")
        
        // 设置搜索框颜色
        binding.searchView.applyTint(titleBarTextColor)
        
        // 设置RecyclerView
        val layoutManager = io.legado.app.ui.widget.FlowLayoutManager()
        binding.recyclerView.layoutManager = layoutManager
        
        // 设置搜索监听器
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterTags(query ?: "")
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterTags(newText ?: "")
                return true
            }
        })
        
        // 加载标签数据
        loadTags()
    }
    
    private fun loadTags() {
        lifecycleScope.launch {
            val tags = appDb.bookTagDao.getAll()
            val excludedTags = appDb.excludedTagDao.getAllSync()
            allTags = tags.filter { tag ->
                excludedTags.none { excluded -> excluded.name == tag.name }
            }
            // 加载分组
            groups = appDb.bookTagGroupDao.getAllSorted()
            updateAdapter(allTags)
        }
    }
    
    private fun filterTags(keyword: String) {
        val filteredTags = if (keyword.isBlank()) {
            allTags
        } else {
            allTags.filter { tag ->
                tag.name.lowercase().contains(keyword.lowercase())
            }
        }
        updateAdapter(filteredTags)
    }
    
    private fun updateAdapter(tags: List<BookTag>) {
        val items = mutableListOf<BookTagSelectAdapter.Item>()
        
        // 直接添加所有标签，不分组
        tags.sortedBy { it.name }.forEach {
            items.add(BookTagSelectAdapter.Item.Tag(it))
        }
        
        if (adapter == null) {
            adapter = BookTagSelectAdapter { tag ->
                callback?.invoke(tag.name)
                dismiss()
            }
            binding.recyclerView.adapter = adapter
        }
        adapter?.submitList(items)
    }
    
    private fun showCreateTagDialog() {
        BookTagEditDialog.show(
            fragmentManager = childFragmentManager,
            bookUrl = bookUrl,
            callback = { tagInfo ->
                // 创建新标签后刷新列表
                loadTags()
            }
        )
    }

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            bookUrl: String? = null,
            callback: ((String) -> Unit)? = null
        ) {
            val dialog = BookTagSelectDialog()
            val args = Bundle()
            args.putString("bookUrl", bookUrl)
            dialog.arguments = args
            dialog.callback = callback
            
            dialog.show(fragmentManager, "bookTagSelectDialog")
        }
    }
}

/**
 * 标签信息数据类
 */
data class TagInfo(
    val name: String,
    val color: Int
)