package io.legado.app.ui.book.bookplate

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ActivityBookTagManageBinding
import io.legado.app.databinding.ItemBookplateClassicBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateLogger
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookplateManageActivity :
    BaseActivity<ActivityBookTagManageBinding>(
        fullScreen = false,
        theme = io.legado.app.constant.Theme.Auto,
        toolBarTheme = io.legado.app.constant.Theme.Auto
    ),
    BookplateTemplateAdapter.CallBack {

    private var templates = listOf<BookplateTemplate>()
    private var selectedId = 0L
    private var ignoreClassicSwitch = false
    private val adapter by lazy {
        BookplateTemplateAdapter(this, this)
    }
    private var classicBinding: ItemBookplateClassicBinding? = null

    override val binding by lazy {
        ActivityBookTagManageBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "藏书票模板"
        initRecyclerView()
        loadAndShowList()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bookplate_menu, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                showEditDialog(null)
                return true
            }
            R.id.menu_log -> {
                showLogDialog()
                return true
            }
            R.id.menu_help -> {
                showHelp("bookplateTemplateHelp")
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        adapter.addHeaderView { parent ->
            ItemBookplateClassicBinding.inflate(layoutInflater, parent, false).also {
                classicBinding = it
                it.swClassicApply.setOnCheckedChangeListener { _, isChecked ->
                    if (ignoreClassicSwitch) {
                        ignoreClassicSwitch = false
                        return@setOnCheckedChangeListener
                    }
                    if (isChecked) {
                        onClassicApply()
                    } else {
                        ignoreClassicSwitch = true
                        it.swClassicApply.isChecked = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAndShowList()
    }

    private fun loadAndShowList() {
        lifecycleScope.launch {
            templates = withContext(Dispatchers.IO) {
                val all = appDb.bookplateTemplateDao.getAll()
                val builtins = all.filter { it.isBuiltin }
                if (builtins.size > 1) {
                    builtins.drop(1).forEach { appDb.bookplateTemplateDao.deleteById(it.id) }
                }
                builtins.take(1) + all.filter { !it.isBuiltin }
            }
            if (templates.isEmpty()) {
                val builtin = withContext(Dispatchers.IO) { BookplateGenerator.getOrCreateBuiltinTemplate() }
                templates = listOf(builtin)
            }
            selectedId = appCtx.getPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
            adapter.setItems(templates, adapter.diffItemCallback)
            adapter.setSelectedId(selectedId)
            updateClassicSwitch()
        }
    }

    private fun updateClassicSwitch() {
        ignoreClassicSwitch = true
        classicBinding?.swClassicApply?.isChecked = selectedId == 0L
    }

    private fun onClassicApply() {
        appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
        selectedId = 0L
        adapter.setSelectedId(0L)
        updateClassicSwitch()
        toastOnUi("已选择经典风格")
    }

    override fun onApply(item: BookplateTemplate) {
        appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, item.id)
        selectedId = item.id
        adapter.setSelectedId(item.id)
        updateClassicSwitch()
        toastOnUi("已应用 ${item.name}")
    }

    override fun onPreview(item: BookplateTemplate) {
        previewTemplate(item)
    }

    override fun onEdit(item: BookplateTemplate) {
        showEditDialog(item)
    }

    override fun onDelete(item: BookplateTemplate) {
        if (item.isBuiltin) return
        deleteTemplate(item)
    }

    private fun showEditDialog(template: BookplateTemplate?) {
        val dialog = BookplateTemplateEditDialog(template?.id)
        dialog.setOnDismissListener { loadAndShowList() }
        showDialogFragment(dialog)
    }

    private fun previewTemplate(template: BookplateTemplate) {
        lifecycleScope.launch {
            val previewData = BookplateGenerator.getPreviewData()
            val bitmap = io.legado.app.help.book.BookplateHtmlRenderer.render(
                this@BookplateManageActivity, template, previewData
            )
            if (bitmap != null) {
                showBookplateDialog(bitmap, "藏书票预览_${template.name}")
            } else {
                val error = io.legado.app.help.book.BookplateHtmlRenderer.lastError ?: "未知错误"
                toastOnUi("渲染失败: $error")
            }
        }
    }

    private fun showBookplateDialog(bitmap: android.graphics.Bitmap, fileName: String) {
        io.legado.app.ui.widget.dialog.BookplateDialog.show(this@BookplateManageActivity, bitmap, fileName)
    }

    private fun deleteTemplate(template: BookplateTemplate) {
        AlertDialog.Builder(this)
            .setTitle("删除模板")
            .setMessage("确定要删除 \"${template.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { appDb.bookplateTemplateDao.deleteById(template.id) }
                    io.legado.app.help.book.BookplateHtmlRenderer.clearCache()
                    if (selectedId == template.id) {
                        appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
                        selectedId = 0L
                    }
                    toastOnUi("已删除")
                    loadAndShowList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLogDialog() {
        val logText = BookplateLogger.dump()
        val scrollView = android.widget.ScrollView(this)
        val textView = TextView(this).apply {
            text = logText
            textSize = 11f
            setTextColor(primaryTextColor)
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setTypeface(Typeface.MONOSPACE)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)
        AlertDialog.Builder(this)
            .setTitle("藏书票日志")
            .setView(scrollView)
            .setPositiveButton("清空") { _, _ -> BookplateLogger.clear() }
            .setNegativeButton("关闭", null)
            .show()
    }

}
