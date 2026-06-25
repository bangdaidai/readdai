package io.legado.app.ui.book.bookplate

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.DialogBookplateTemplateEditBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.applyTint
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookplateTemplateEditDialog() : BaseDialogFragment(R.layout.dialog_bookplate_template_edit, true),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogBookplateTemplateEditBinding::bind)
    private var templateId: Long? = null
    private var existingTemplate: BookplateTemplate? = null
    private var focusedEditText: EditText? = null

    constructor(templateId: Long?) : this() {
        arguments = Bundle().apply {
            if (templateId != null) {
                putLong("templateId", templateId)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        templateId = arguments?.getLong("templateId")?.takeIf { it != 0L }

        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.bookplate_edit_menu)
        binding.toolBar.menu.applyTint(requireContext())
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.menu.findItem(R.id.menu_save)?.icon?.setTint(titleBarTextIconColor)
        binding.toolBar.menu.findItem(R.id.menu_fullscreen_edit)?.icon?.setTint(titleBarTextIconColor)
        binding.toolBar.setOnMenuItemClickListener(this)

        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.cardView.setBackgroundColor(backgroundColor)

        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            existingTemplate = withContext(Dispatchers.IO) {
                templateId?.let { appDb.bookplateTemplateDao.getById(it) }
            }
            binding.toolBar.title = if (existingTemplate == null) "新建模板" else "编辑模板"
            binding.tvTemplateName.setText(existingTemplate?.name)
            val htmlContent = existingTemplate?.htmlContent ?: BookplateGenerator.DEFAULT_TEMPLATE_HTML
            binding.tvHtmlContent.setText(htmlContent)
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val view = focusedEditText
            if (view == null) {
                toastOnUi(R.string.focus_lost_on_textbox)
                return@registerForActivityResult
            }
            view.requestFocus()
            result.data?.getStringExtra("text")?.let {
                view.setText(it)
            }
            result.data?.getIntExtra("cursorPosition", -1)?.takeIf { it in 0..view.text.length }?.let {
                view.setSelection(it)
            }
        }
    }

    private fun onFullEditClicked() {
        val view = dialog?.window?.decorView?.findFocus()
        if (view is EditText) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            focusedEditText = view
            val currentText = view.text.toString()
            val intent = Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
            }
            textEditLauncher.launch(intent)
        } else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()
            R.id.menu_save -> saveTemplate()
        }
        return true
    }

    private fun saveTemplate() {
        val name = binding.tvTemplateName.text.toString().trim().ifBlank {
            toastOnUi("模板名称不能为空")
            return
        }
        val html = binding.tvHtmlContent.text.toString().trim()

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

            val savedId = withContext(Dispatchers.IO) {
                appDb.bookplateTemplateDao.insert(templateToSave)
            }

            appCtx.putPrefLong(PreferKey.selectedBookplateTemplateId, savedId)
            appCtx.putPrefLong("lastBookplateTemplateId", savedId)

            io.legado.app.help.book.BookplateHtmlRenderer.clearCache()

            toastOnUi("模板已保存")
            dismissAllowingStateLoss()
        }
    }

}
