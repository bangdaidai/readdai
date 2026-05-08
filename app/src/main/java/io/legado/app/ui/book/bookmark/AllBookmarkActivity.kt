package io.legado.app.ui.book.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ActivityAllBookmarkBinding
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog

/**
 * 所有书签
 */
class AllBookmarkActivity : VMBaseActivity<ActivityAllBookmarkBinding, AllBookmarkViewModel>(),
    BookmarkAdapter.Callback {

    override val viewModel by viewModels<AllBookmarkViewModel>()
    override val binding by viewBinding(ActivityAllBookmarkBinding::inflate)
    private val adapter by lazy {
        BookmarkAdapter(this, this)
    }
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.exportBookmark(uri)
                2 -> viewModel.exportBookmarkMd(uri)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        lifecycleScope.launch {
            appDb.bookmarkDao.flowAll().catch {
                AppLog.put("所有书签界面获取数据失败\n${it.localizedMessage}", it)
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
        menuInflater.inflate(R.menu.bookmark, menu)
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

            R.id.menu_clear_all -> AlertDialog.Builder(this)
                .setTitle("清除所有书签")
                .setMessage("确定要清除所有书签吗？此操作不可恢复。")
                .setPositiveButton("确定") { _, _ ->
                    lifecycleScope.launch {
                        appDb.bookmarkDao.deleteAll()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onItemClick(bookmark: Bookmark, position: Int) {
        // 直接跳转到书籍位置，不显示对话框
        val book = appDb.bookDao.getBook(bookmark.bookName, bookmark.bookAuthor)
        book?.let {
            val intent = Intent(this, ReadBookActivity::class.java).apply {
                putExtra("bookUrl", it.bookUrl)
                putExtra("index", bookmark.chapterIndex)
                putExtra("chapterPos", bookmark.chapterPos)
            }
            startActivity(intent)
        }
    }

    override fun onLongClick(bookmark: Bookmark, position: Int) {
        // 长按删除书签
        AlertDialog.Builder(this)
            .setTitle("删除书签")
            .setMessage("确定要删除这个书签吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    appDb.bookmarkDao.delete(bookmark)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

}