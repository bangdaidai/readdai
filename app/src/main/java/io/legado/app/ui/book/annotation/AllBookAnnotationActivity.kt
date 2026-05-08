package io.legado.app.ui.book.annotation

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.ActivityAllAnnotationBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.annotation.adapter.BookAnnotationAdapter
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AllBookAnnotationActivity : VMBaseActivity<ActivityAllAnnotationBinding, AllBookAnnotationViewModel>(),
    BookAnnotationAdapter.Callback {

    override val viewModel by viewModels<AllBookAnnotationViewModel>()
    override val binding by viewBinding(ActivityAllAnnotationBinding::inflate)
    private val adapter by lazy { BookAnnotationAdapter(this, this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.annotation, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export -> {
                // TODO: 需要实现文件选择器并获取treeUri
                // viewModel.exportBookmark(treeUri)
            }
            R.id.menu_export_md -> {
                // TODO: 需要实现文件选择器并获取treeUri
                // viewModel.exportBookmarkMd(treeUri)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.titleBar.title = getString(R.string.annotation)
        binding.titleBar.setBackgroundColor(primaryColor)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.bookAnnotationDao.flowAll().collectLatest { list ->
                adapter.setItems(list)
            }
        }
    }

    override fun onItemClick(bookAnnotation: BookAnnotation, position: Int) {
        BookAnnotationDialog.newInstance(bookAnnotation)
            .show(supportFragmentManager, "bookAnnotationDialog")
    }
}