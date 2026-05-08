package io.legado.app.ui.book.tag.excluded

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityExcludedTagManageBinding

import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 排除标签管理页面
 */
class ExcludedTagManageActivity : VMBaseActivity<ActivityExcludedTagManageBinding, ExcludedTagManageViewModel>() {
    
    override val binding by viewBinding(ActivityExcludedTagManageBinding::inflate)
    override val viewModel by viewModels<ExcludedTagManageViewModel>()
    private lateinit var adapter: ExcludedTagManageAdapter
    
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTags()
    }
    
    private fun initView() {
        // 设置标题
        binding.titleBar.title = getString(R.string.excluded_tag_management)
        
        adapter = ExcludedTagManageAdapter(this, viewModel)
        // 使用FlowLayoutManager实现流式布局
        val layoutManager = io.legado.app.ui.widget.FlowLayoutManager()
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        
        // 设置空视图
        binding.tvEmpty.visibility = if (adapter.itemCount == 0) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    private fun observeViewModel() {
        viewModel.tags.observe(this, Observer { tags ->
            adapter.submitList(tags)
            binding.tvEmpty.visibility = if (tags.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        })
        
        viewModel.errorMsg.observe(this, Observer { msg ->
            toastOnUi(msg)
        })
    }
    
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_excluded_tag_manage, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }
    
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                viewModel.showAddTagDialog(this)
                return true
            }
            else -> return super.onCompatOptionsItemSelected(item)
        }
    }
}