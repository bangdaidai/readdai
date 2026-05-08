package io.legado.app.ui.book.vector

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.VectorizedBookEntity
import io.legado.app.databinding.ActivityVectorBooksBinding
import io.legado.app.help.ai.rag.VectorSearchService
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class VectorBooksActivity : VMBaseActivity<ActivityVectorBooksBinding, VectorBooksViewModel>(),
    VectorBooksAdapter.Callback {

    override val viewModel by viewModels<VectorBooksViewModel>()
    override val binding by viewBinding(ActivityVectorBooksBinding::inflate)

    private val adapter by lazy {
        VectorBooksAdapter(this, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        observeData()
    }

    private fun initView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.applyNavigationBarPadding()
    }

    private fun observeData() {
        lifecycleScope.launch {
            appDb.vectorizedBookDao.flowAll()
                .catch { e ->
                    toastOnUi("获取数据失败: ${e.message}")
                }
                .flowOn(Dispatchers.IO)
                .collect {
                    adapter.setItems(it)
                    updateEmptyState(it.isEmpty())
                }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerView.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vector_books, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_delete_all -> showDeleteAllDialog()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun showDeleteAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("清除所有向量数据")
            .setMessage("确定要清除所有向量化书籍的数据吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    val vectorService = VectorSearchService(this@VectorBooksActivity)
                    val books = appDb.vectorizedBookDao.getAll()
                    books.forEach { book ->
                        vectorService.deleteBookVectors(book.bookUrl)
                    }
                    appDb.vectorizedBookDao.deleteAll()
                    toastOnUi("已清除所有向量数据")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun deleteVectorBook(bookUrl: String, bookTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("删除向量数据")
            .setMessage("确定要删除《$bookTitle》的向量数据吗？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    val vectorService = VectorSearchService(this@VectorBooksActivity)
                    vectorService.deleteBookVectors(bookUrl)
                    appDb.vectorizedBookDao.deleteByBookUrl(bookUrl)
                    toastOnUi("已删除《$bookTitle》的向量数据")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onItemClick(book: VectorizedBookEntity) {
        // 暂时不实现点击跳转功能，仅显示详情
        AlertDialog.Builder(this)
            .setTitle(book.bookTitle)
            .setMessage(buildString {
                append("文本块: ${book.totalChunks}\n")
                append("向量: ${book.totalVectors}\n")
                append("分块大小: ${book.chunkSize}\n")
                append("模型: ${book.modelProvider} / ${book.modelName}\n")
            })
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                deleteVectorBook(book.bookUrl, book.bookTitle)
            }
            .show()
    }

    override fun onItemLongClick(book: VectorizedBookEntity) {
        deleteVectorBook(book.bookUrl, book.bookTitle)
    }
}
