package io.legado.app.ui.book.tag.excluded.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.databinding.DialogExcludedTagEditBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.applyTint
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ExcludedTagEditDialog : BaseDialogFragment(R.layout.dialog_excluded_tag_edit) {

    private val binding by viewBinding(DialogExcludedTagEditBinding::bind)
    private var excludedTag: ExcludedTag? = null
    private var onConfirm: (String) -> Unit = {}

    companion object {
        fun create(excludedTag: ExcludedTag? = null, onConfirm: (String) -> Unit): ExcludedTagEditDialog {
            return ExcludedTagEditDialog().apply {
                this.excludedTag = excludedTag
                this.onConfirm = onConfirm
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setWindowAnimations(android.R.style.Animation_Dialog)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置标题栏文字颜色为主题的主要文字颜色
        binding.tvTitle.setTextColor(ThemeStore.textColorPrimary(requireContext()))
        
        // 设置编辑框和其容器的背景色为主题自定义的背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        // 找到 TextInputLayout 并设置背景色
        val textInputLayout = findParentTextInputLayout(binding.etTagName)
        textInputLayout?.setBackgroundColor(backgroundColor)
        // 设置 TextInputLayout 的 box 背景色
        textInputLayout?.boxBackgroundColor = backgroundColor
        binding.etTagName.setBackgroundColor(backgroundColor)
        // 设置编辑框的文字颜色，确保在深色背景下也能正常显示
        binding.etTagName.setTextColor(ThemeStore.textColorPrimary(requireContext()))
        
        excludedTag?.let {
            binding.etTagName.setText(it.name)
        }

        binding.btnConfirm.applyTint(ThemeStore.primaryColor(requireContext()))
        binding.btnCancel.applyTint(getCompatColor(R.color.md_grey_600))

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnConfirm.setOnClickListener {
            val tagName = binding.etTagName.text.toString().trim()
            if (tagName.isEmpty()) {
                binding.etTagName.error = getString(R.string.excluded_tag_name_empty)
                return@setOnClickListener
            }
            onConfirm(tagName)
            dismiss()
        }
    }
}