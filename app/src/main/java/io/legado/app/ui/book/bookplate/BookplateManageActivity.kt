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
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ActivityBookTagManageBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateHtmlRenderer
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
    private var groupNames = listOf("<默认>")
    private var currentGroupName = BookplateTemplate.DEFAULT_GROUP_BOOK
    private val adapter by lazy {
        BookplateTemplateAdapter(this, this)
    }

    override val binding by lazy {
        ActivityBookTagManageBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "藏书票模板"
        initRecyclerView()
        initTabLayout()
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
    }

    private fun loadGroupNames() {
        lifecycleScope.launch {
            groupNames = withContext(Dispatchers.IO) {
                val names = appDb.bookplateTemplateDao.getDistinctGroupNames()
                if (names.isEmpty()) listOf(BookplateTemplate.DEFAULT_GROUP_BOOK) else names
            }
            val oldGroup = currentGroupName
            if (currentGroupName !in groupNames && groupNames.isNotEmpty()) {
                currentGroupName = groupNames.first()
            }
            if (oldGroup != currentGroupName || binding.tabLayout.tabCount != groupNames.size) {
                rebuildTabs()
            } else {
                loadAndShowList()
            }
        }
    }

    private fun initTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val newGroup = tab.tag as? String ?: return
                if (currentGroupName != newGroup) {
                    currentGroupName = newGroup
                    loadAndShowList()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        loadGroupNames()
    }

    private fun rebuildTabs() {
        binding.tabLayout.removeAllTabs()
        var selectIdx = -1
        groupNames.forEachIndexed { idx, name ->
            val tab = binding.tabLayout.newTab().setText(name)
            tab.tag = name
            binding.tabLayout.addTab(tab)
            if (name == currentGroupName) selectIdx = idx
        }
        if (selectIdx >= 0) {
            binding.tabLayout.getTabAt(selectIdx)?.select()
        }
        loadAndShowList()
    }

    override fun onResume() {
        super.onResume()
        loadAndShowList()
    }

    private fun loadAndShowList() {
        lifecycleScope.launch {
            templates = withContext(Dispatchers.IO) {
                BookplateGenerator.getOrCreateBuiltinTemplates(currentGroupName)
                val all = appDb.bookplateTemplateDao.getByGroupName(currentGroupName)
                val builtins = all.filter { it.isBuiltin }
                builtins + all.filter { !it.isBuiltin }
            }
            val key = PreferKey.templateIdKey(currentGroupName)
            selectedId = appCtx.getPrefLong(key, 0L)
            adapter.setItems(templates, adapter.diffItemCallback)
            adapter.setSelectedId(selectedId)
        }
    }

    override fun onApply(item: BookplateTemplate) {
        val key = PreferKey.templateIdKey(currentGroupName)
        appCtx.putPrefLong(key, item.id)
        selectedId = item.id
        adapter.setSelectedId(item.id)
        toastOnUi("已应用 ${item.name}")
    }

    override fun onPreview(item: BookplateTemplate) {
        previewTemplate(item)
    }

    override fun onEdit(item: BookplateTemplate) {
        showEditDialog(item)
    }

    override fun onDelete(item: BookplateTemplate) {
        deleteTemplate(item)
    }

    private fun showEditDialog(template: BookplateTemplate?) {
        val dialog = BookplateTemplateEditDialog(template?.id, currentGroupName)
        dialog.setOnDismissListener { loadAndShowList() }
        showDialogFragment(dialog)
    }

    private fun previewTemplate(template: BookplateTemplate) {
        lifecycleScope.launch {
            if (template.groupName == BookplateTemplate.DEFAULT_GROUP_STATS) {
                val statsVariables = getStatisticsPreviewVariables()
                val bitmap = BookplateHtmlRenderer.renderCustom(
                    this@BookplateManageActivity, template.htmlContent, statsVariables
                )
                if (bitmap != null) {
                    showBookplateDialog(bitmap, "统计预览_${template.name}")
                } else {
                    toastOnUi("渲染失败: ${BookplateHtmlRenderer.lastError ?: "未知错误"}")
                }
            } else {
                val previewData = BookplateGenerator.getPreviewData()
                val bitmap = BookplateHtmlRenderer.render(
                    this@BookplateManageActivity, template, previewData
                )
                if (bitmap != null) {
                    showBookplateDialog(bitmap, "藏书票预览_${template.name}")
                } else {
                    toastOnUi("渲染失败: ${BookplateHtmlRenderer.lastError ?: "未知错误"}")
                }
            }
        }
    }

    private fun getStatisticsPreviewVariables(): Map<String, String> {
        return mapOf(
            "pageTitle" to "阅读统计",
            "pageSubtitle" to "LEGADO READING REPORT",
            "periodLabel" to "全部时间",
            "bookCount" to "42",
            "finishedBookCount" to "18",
            "abandonedBookCount" to "5",
            "reviewCount" to "12",
            "readDays" to "365",
            "totalWords" to "856.3",
            "timeDays" to "12",
            "timeHours" to "8",
            "timeMinutes" to "30",
            "footerLine1" to "LEGADO · 阅读统计",
            "footerLine2" to "Generated by Legado Reading App"
        )
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
                    BookplateHtmlRenderer.clearCache()
                    val key = PreferKey.templateIdKey(currentGroupName)
                    if (selectedId == template.id) {
                        appCtx.putPrefLong(key, 0L)
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
