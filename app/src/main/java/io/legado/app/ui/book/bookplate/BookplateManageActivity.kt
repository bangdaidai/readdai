package io.legado.app.ui.book.bookplate

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ActivityBookTagManageBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateLogger
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookplateManageActivity : BaseActivity<ActivityBookTagManageBinding>(
    fullScreen = false, theme = io.legado.app.constant.Theme.Auto, toolBarTheme = io.legado.app.constant.Theme.Auto
) {

    private val container by lazy { FrameLayout(this) }
    private var templates = listOf<BookplateTemplate>()
    private var selectedId = 0L

    // 跳转到完整编辑页面的 result launcher
    private val templateEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // 完整编辑页面已保存，刷新列表
            loadAndShowList()
        }
    }

    override val binding by lazy {
        ActivityBookTagManageBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "藏书票模板"
        initMenu()

        val root = binding.root
        if (root is ViewGroup) {
            root.addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        loadAndShowList()
    }

    private fun initMenu() {
        binding.titleBar.toolbar.inflateMenu(R.menu.bookplate_menu)
        binding.titleBar.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_add -> {
                    showEditTemplateDialog(null)
                    true
                }
                R.id.menu_log -> {
                    showLogDialog()
                    true
                }
                R.id.menu_help -> {
                    showHelp("bookplateTemplateHelp")
                    true
                }
                else -> false
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
                // 清除并发竞争产生的重复内置模板
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
            showTemplateList()
        }
    }

    private fun showTemplateList() {
        container.removeAllViews()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
        }

        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        // Classic style entry
        val classicView = createClassicItemView(isSelected = selectedId == 0L)
        classicView.setOnClickListener {
            appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
            selectedId = 0L
            showTemplateList()
            toastOnUi("已选择经典风格")
        }
        linearLayout.addView(classicView)

        templates.forEach { template ->
            val itemView = createTemplateItemView(template)
            linearLayout.addView(itemView)
        }

        scrollView.addView(linearLayout)
        container.addView(scrollView)
    }

    private fun createClassicItemView(isSelected: Boolean): LinearLayout {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

        val nameView = TextView(this).apply {
            text = "经典风格"
            setTextColor(primaryTextColor)
            textSize = 16f
        }
        itemLayout.addView(nameView)

        if (isSelected) {
            val checkView = TextView(this).apply {
                text = "\u2713"
                setTextColor(accentColor)
                textSize = 20f
                setTypeface(Typeface.DEFAULT_BOLD)
            }
            itemLayout.addView(checkView)
        }

        return itemLayout
    }

    private fun createTemplateItemView(template: BookplateTemplate): LinearLayout {
        val isSelected = template.id == selectedId
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 8.dpToPx(), 12.dpToPx())
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

        // 文字区域
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = template.name
            setTextColor(primaryTextColor)
            textSize = 16f
        }
        textLayout.addView(nameView)

        itemLayout.addView(textLayout)

        // 选中标记
        if (isSelected) {
            val checkView = TextView(this).apply {
                text = "\u2713"
                setTextColor(accentColor)
                textSize = 20f
                setTypeface(Typeface.DEFAULT_BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4.dpToPx() }
            }
            itemLayout.addView(checkView)
        }

        // 按钮区域
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 编辑按钮
        val editBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(primaryTextColor)
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            setPadding(8.dpToPx())
            setOnClickListener {
                showEditTemplateDialog(template)
            }
        }
        btnLayout.addView(editBtn)

        // 预览按钮
        val previewBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_view_list)
            setColorFilter(primaryTextColor)
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            setPadding(8.dpToPx())
            setOnClickListener {
                previewTemplate(template)
            }
        }
        btnLayout.addView(previewBtn)

        // 删除按钮（仅用户模板显示）
        if (!template.isBuiltin) {
            val deleteBtn = ImageView(this).apply {
                setImageResource(R.drawable.ic_delete)
                setColorFilter(primaryTextColor)
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
                setPadding(8.dpToPx())
                setOnClickListener {
                    deleteTemplate(template)
                }
            }
            btnLayout.addView(deleteBtn)
        }

        itemLayout.addView(btnLayout)

        // 点击整行也可选中
        itemLayout.setOnClickListener {
            if (!isSelected) {
                appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, template.id)
                selectedId = template.id
                showTemplateList()
            }
        }

        return itemLayout
    }

    private fun showEditTemplateDialog(template: BookplateTemplate?) {
        val data = Triple(
            template,
            template?.name ?: "",
            template?.htmlContent ?: BookplateGenerator.DEFAULT_TEMPLATE_HTML
        )
        IntentData.put("bookplateTemplateData", data)

        // 跳转到完整编辑页面
        val intent = Intent(this, BookplateTemplateEditActivity::class.java)
        templateEditLauncher.launch(intent)
    }

    private fun saveTemplate(existing: BookplateTemplate?, name: String, html: String) {
        lifecycleScope.launch {
            if (html.isBlank()) {
                toastOnUi("模板内容不能为空")
                return@launch
            }
            val now = System.currentTimeMillis()
            val templateToSave = if (existing != null) {
                existing.copy(name = name, htmlContent = html, updateTime = now)
            } else {
                BookplateTemplate(
                    name = name,
                    htmlContent = html,
                    createTime = now,
                    updateTime = now
                )
            }
            val savedId = withContext(Dispatchers.IO) {
                // 使用 insertOrUpdate 确保更新生效
                appDb.bookplateTemplateDao.insert(templateToSave)
            }
            // 保存后自动选中新/修改的模板
            appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, savedId)
            selectedId = savedId
            toastOnUi("模板已保存")
            loadAndShowList()
        }
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
                toastOnUi("渲染失败，请检查模板语法")
            }
        }
    }

    private fun showBookplateDialog(bitmap: android.graphics.Bitmap, fileName: String) {
        io.legado.app.ui.widget.dialog.BookplateDialog.show(this@BookplateManageActivity, bitmap, fileName)
    }

    private fun deleteTemplate(template: BookplateTemplate) {
        if (template.isBuiltin) return
        AlertDialog.Builder(this)
            .setTitle("删除模板")
            .setMessage("确定要删除 \"${template.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { appDb.bookplateTemplateDao.deleteById(template.id) }
                    toastOnUi("已删除")
                    loadAndShowList()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLogDialog() {
        val logText = BookplateLogger.dump()
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = logText
            textSize = 11f
            setTextColor(primaryTextColor)
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setTypeface(Typeface.MONOSPACE)
        }
        scrollView.addView(textView)
        AlertDialog.Builder(this)
            .setTitle("藏书票日志")
            .setView(scrollView)
            .setPositiveButton("清空") { _, _ -> BookplateLogger.clear() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun resetToDefault() {
        AlertDialog.Builder(this)
            .setTitle("重置为默认")
            .setMessage("将内置模板恢复为系统默认样式？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.bookplateTemplateDao.deleteBuiltin()
                    }
                    loadAndShowList()
                    toastOnUi("已重置")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
