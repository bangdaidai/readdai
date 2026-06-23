package io.legado.app.ui.book.bookplate

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ActivityBookTagManageBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateLogger
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefLong
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

    override val binding by lazy {
        ActivityBookTagManageBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "藏书票模板"

        val root = binding.root
        if (root is ViewGroup) {
            root.addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
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
        val classicView = createTemplateItemView(
            name = "经典风格",
            subtitle = "Canvas 绘制的固定样式藏书票",
            isSelected = selectedId == 0L,
            isBuiltin = true
        )
        classicView.setOnClickListener {
            appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, 0L)
            selectedId = 0L
            showTemplateList()
            toastOnUi("已选择经典风格")
        }
        linearLayout.addView(classicView)

        // Separator
        val separator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1.dpToPx()
            ).apply { setMargins(0, 8.dpToPx(), 0, 8.dpToPx()) }
            setBackgroundColor(0x22000000)
        }
        linearLayout.addView(separator)

        templates.forEach { template ->
            val itemView = createTemplateItemView(
                name = template.name,
                subtitle = if (template.isBuiltin) "内置模板" else "用户模板",
                isSelected = template.id == selectedId,
                isBuiltin = template.isBuiltin
            )
            itemView.setOnClickListener {
                appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, template.id)
                selectedId = template.id
                showTemplateList()
                toastOnUi("已选择: ${template.name}")
            }
            itemView.setOnLongClickListener {
                showTemplateOptions(template)
                true
            }
            linearLayout.addView(itemView)
        }

        // Add new template button
        val addButton = TextView(this).apply {
            text = "+ 新建模板"
            setTextColor(primaryColor)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        addButton.setOnClickListener {
            showEditTemplateDialog(null)
        }
        linearLayout.addView(addButton)

        val logButton = TextView(this).apply {
            text = "+ 查看日志"
            setTextColor(primaryColor)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        logButton.setOnClickListener {
            showLogDialog()
        }
        linearLayout.addView(logButton)

        scrollView.addView(linearLayout)
        container.addView(scrollView)
    }

    private fun createTemplateItemView(
        name: String,
        subtitle: String,
        isSelected: Boolean,
        isBuiltin: Boolean
    ): LinearLayout {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isSelected) primaryColor and 0x22FFFFFF else 0x00000000)
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = name
            setTextColor(primaryTextColor)
            textSize = 16f
            setTypeface(Typeface.DEFAULT, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }
        textLayout.addView(nameView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            setTextColor(primaryTextColor and 0x66000000)
            textSize = 12f
        }
        textLayout.addView(subtitleView)

        itemLayout.addView(textLayout)

        if (isSelected) {
            val checkView = TextView(this).apply {
                text = "\u2713"
                setTextColor(primaryColor)
                textSize = 20f
            setTypeface(Typeface.DEFAULT_BOLD)
            }
            itemLayout.addView(checkView)
        }

        return itemLayout
    }

    private fun showTemplateOptions(template: BookplateTemplate) {
        val options = if (template.isBuiltin) {
            arrayOf("编辑", "复制新建", "预览", "重置为默认")
        } else {
            arrayOf("编辑", "复制新建", "预览", "删除")
        }
        AlertDialog.Builder(this)
            .setTitle(template.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "编辑" -> showEditTemplateDialog(template)
                    "复制新建" -> duplicateTemplate(template)
                    "预览" -> previewTemplate(template)
                    "重置为默认" -> resetToDefault()
                    "删除" -> deleteTemplate(template)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditTemplateDialog(template: BookplateTemplate?) {
        val editTitle = EditText(this).apply {
            setHint("模板名称")
            setText(template?.name ?: "")
        }
        val editContent = EditText(this).apply {
            setHint("HTML 模板内容")
            setText(template?.htmlContent ?: BookplateGenerator.DEFAULT_TEMPLATE_HTML)
            minLines = 10
            maxLines = 20
            gravity = Gravity.TOP
            setHorizontallyScrolling(true)
            textSize = 12f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32.dpToPx(), 16.dpToPx(), 32.dpToPx(), 16.dpToPx())
            addView(editTitle)
            addView(View(this@BookplateManageActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8.dpToPx())
            })
            addView(editContent)
        }

        val scrollView = ScrollView(this).apply { addView(layout) }

        val title = if (template == null) "新建模板" else "编辑模板"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val name = editTitle.text.toString().trim().ifEmpty { "未命名模板" }
                val html = editContent.text.toString().trim()
                saveTemplate(template, name, html)
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }

    private fun saveTemplate(existing: BookplateTemplate?, name: String, html: String) {
        lifecycleScope.launch {
            if (html.isBlank()) {
                toastOnUi("模板内容不能为空")
                return@launch
            }
            val now = System.currentTimeMillis()
            val template = if (existing != null) {
                existing.copy(name = name, htmlContent = html, updateTime = now)
            } else {
                BookplateTemplate(
                    name = name,
                    htmlContent = html,
                    createTime = now,
                    updateTime = now
                )
            }
            withContext(Dispatchers.IO) {
                if (existing != null) {
                    appDb.bookplateTemplateDao.update(template)
                } else {
                    appDb.bookplateTemplateDao.insert(template)
                }
            }
            toastOnUi("模板已保存")
            loadAndShowList()
        }
    }

    private fun duplicateTemplate(template: BookplateTemplate) {
        showEditTemplateDialog(
            BookplateTemplate(
                name = "${template.name} (副本)",
                htmlContent = template.htmlContent,
                isBuiltin = false
            )
        )
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
