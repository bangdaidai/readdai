package io.legado.app.ui.book.bookplate

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.DialogBookplateTemplateEditBinding
import io.legado.app.help.IntentData
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateHtmlRenderer
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookplateTemplateEditActivity : BaseActivity<DialogBookplateTemplateEditBinding>() {

    private var existingTemplate: BookplateTemplate? = null
    private var editContent: EditText? = null

    override val binding by lazy {
        DialogBookplateTemplateEditBinding.inflate(layoutInflater)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val templateData = IntentData.get<Triple<BookplateTemplate?, String, String>>("bookplateTemplateData")
        existingTemplate = templateData?.first
        val name = templateData?.second ?: ""
        val html = templateData?.third
            ?: BookplateGenerator.DEFAULT_TEMPLATE_HTML

        // 设置初始值
        binding.editName.setText(name)
        binding.editContent.setText(html)
        editContent = binding.editContent

        initToolbar()
        initViews()
    }

    private fun initToolbar() {
        binding.toolbar.setBackgroundColor(primaryColor)
        binding.toolbar.title = if (existingTemplate == null) "新建模板" else "编辑模板"
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 添加菜单
        binding.toolbar.inflateMenu(R.menu.bookplate_edit_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_fullscreen_edit -> {
                    // 跳转到完整编辑页面
                    val currentHtml = binding.editContent.text.toString()
                    val intent = Intent(this, CodeEditActivity::class.java).apply {
                        putExtra("text", currentHtml)
                        putExtra("title", "HTML 模板内容")
                        putExtra("cursorPosition", 0)
                    }
                    codeEditLauncher.launch(intent)
                    true
                }
                R.id.menu_save -> {
                    saveTemplate()
                    true
                }
                else -> false
            }
        }
    }

    private val codeEditLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("text")?.let { newText ->
                binding.editContent.setText(newText)
            }
        }
    }

    private fun initViews() {
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            finish()
        }

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveTemplate()
        }
    }

    private fun saveTemplate() {
        val name = binding.editName.text.toString().trim().ifBlank {
            toastOnUi("模板名称不能为空")
            return
        }
        val html = binding.editContent.text.toString().trim()

        if (html.isBlank()) {
            toastOnUi("模板内容不能为空")
            return
        }

        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val templateToSave = if (existingTemplate != null) {
                existingTemplate!!.copy(name = name, htmlContent = html, updateTime = now)
            } else {
                BookplateTemplate(
                    name = name,
                    htmlContent = html,
                    createTime = now,
                    updateTime = now
                )
            }

            withContext(Dispatchers.IO) {
                appDb.bookplateTemplateDao.insert(templateToSave)
            }

            // 保存后自动选中新/修改的模板
            appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, templateToSave.id)

            toastOnUi("模板已保存")
            // 返回结果
            setResult(RESULT_OK)
            finish()
        }
    }
}
