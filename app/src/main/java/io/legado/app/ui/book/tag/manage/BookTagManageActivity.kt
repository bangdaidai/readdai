package io.legado.app.ui.book.tag.manage

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityBookTagManageBinding
import io.legado.app.ui.book.tag.excluded.ExcludedTagManageActivity
import io.legado.app.ui.book.tag.manage.adapter.BookTagManageAdapter
import io.legado.app.constant.EventBus
import io.legado.app.utils.applyTint
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 书籍标签管理界面
 */
class BookTagManageActivity : VMBaseActivity<ActivityBookTagManageBinding, BookTagManageViewModel>() {

    override val binding by viewBinding(ActivityBookTagManageBinding::inflate)
    override val viewModel by lazy {
        AndroidViewModelFactory.getInstance(application).create(BookTagManageViewModel::class.java)
    }

    private lateinit var adapter: BookTagManageAdapter

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        observeViewModel()
        observeLiveBus()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTags()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        // 监听标签更新事件，当标签发生变化时重新加载标签列表
        observeEvent<String>(EventBus.TAGS_UPDATED) {
            viewModel.loadTags()
        }
    }

    private fun initView() {
        // 设置标题
        binding.titleBar.title = getString(R.string.book_tag_manage)

        // 初始化搜索框
        val searchView = binding.titleBar.findViewById<io.legado.app.ui.widget.SearchView>(R.id.search_view)
        searchView?.apply {
            queryHint = getString(R.string.search_tag)
            // 应用主题颜色
            val textColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(this@BookTagManageActivity)
            applyTint(textColor)
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        viewModel.searchTags(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        viewModel.searchTags(it)
                    }
                    return true
                }
            })
        }

        // 设置RecyclerView
        adapter = BookTagManageAdapter(this)
        adapter.setViewModel(viewModel)
        // 使用FlowLayoutManager，实现标签自然排列和自动换行
        val layoutManager = io.legado.app.ui.widget.FlowLayoutManager()
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.tags.observe(this) { tags ->
            adapter.submitTagList(tags)
        }
        
        // 监听标签更新事件，执行局部刷新
        viewModel.tagUpdated.observe(this) {
            val (updatedTag, updateType) = it
            val currentTags = adapter.currentList
            val position = currentTags.indexOfFirst { item ->
                item is BookTagManageAdapter.Item.Tag && item.tag.id == updatedTag.id
            }
            if (position != -1) {
                // 使用payload进行局部刷新
                adapter.notifyItemChanged(position, updateType)
            } else {
                // 如果找不到标签，使用全量刷新
                viewModel.loadTags()
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_book_tag_manage, menu)
        menu.applyTint(this)
        
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                // 显式转换为FragmentActivity以解决类型不匹配问题
                viewModel.showAddTagDialog(this as FragmentActivity)
                return true
            }
            R.id.menu_excluded_tags -> {
                // 跳转到排除标签管理页面
                startActivity(Intent(this, ExcludedTagManageActivity::class.java))
                return true
            }
            R.id.menu_group_manage -> {
                // 显示分组管理对话框
                showGroupManageDialog()
                return true
            }
            R.id.menu_tag_mapping -> {
                // 显示标签映射管理对话框
                showTagMappingManageDialog()
                return true
            }
            else -> return super.onCompatOptionsItemSelected(item)
        }
    }

    /**
     * 显示分组管理对话框
     */
    private fun showGroupManageDialog() {
        GroupManageDialog.show(supportFragmentManager, viewModel)
    }

    /**
     * 显示标签映射管理对话框
     */
    private fun showTagMappingManageDialog() {
        TagMappingManageDialog.show(supportFragmentManager, viewModel)
    }
}