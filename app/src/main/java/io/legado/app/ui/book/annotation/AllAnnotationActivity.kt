package io.legado.app.ui.book.annotation

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.databinding.ActivityAllAnnotationBinding
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 所有书摘
 */
class AllAnnotationActivity : VMBaseActivity<ActivityAllAnnotationBinding, AllAnnotationViewModel>(),
    AnnotationAdapter.Callback {

    override val viewModel by viewModels<AllAnnotationViewModel>()
    override val binding by viewBinding(ActivityAllAnnotationBinding::inflate)
    private val adapter by lazy {
        AnnotationAdapter(this, this)
    }
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.exportAnnotation(uri)
                2 -> viewModel.exportAnnotationMd(uri)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        lifecycleScope.launch {
            appDb.bookAnnotationDao.flowAll().catch {
                AppLog.put("所有书摘界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }
        }
    }

    private fun initView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.annotation, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export -> exportDir.launch {
                requestCode = 1
            }

            R.id.menu_export_md -> exportDir.launch {
                requestCode = 2
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onItemClick(annotation: BookAnnotation, position: Int) {
        showDialogFragment(BookAnnotationDialog.newInstance(annotation))
    }

}