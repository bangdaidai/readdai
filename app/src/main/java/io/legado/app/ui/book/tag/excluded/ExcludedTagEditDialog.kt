package io.legado.app.ui.book.tag.excluded

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.databinding.DialogBookTagEditBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 排除标签添加对话框
 * 复用新增标签对话框布局，隐藏颜色选择功能
 */
class ExcludedTagEditDialog : BaseDialogFragment(R.layout.dialog_book_tag_edit) {

    private val binding by viewBinding(DialogBookTagEditBinding::bind)
    private var callback: ((ExcludedTag) -> Unit)? = null
    private var existingTag: ExcludedTag? = null
    private var isProcessing = false // 防止重复提交的标志

    override fun onStart() {
        super.onStart()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(ThemeStore.primaryColor(requireContext()))
        binding.toolBar.setTitle(R.string.edit_excluded_tag)
        // 设置标题栏文字颜色为主题自定义的标题栏文字图标颜色
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        binding.toolBar.setTitleTextColor(titleBarTextIconColor)

        // 设置对话框内容区域背景色为主题自定义的背景色
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)
        // 设置编辑框和其容器的背景色为主题自定义的背景色
        // 找到 TextInputLayout 并设置背景色
        val textInputLayout = findParentTextInputLayout(binding.editTag)
        textInputLayout?.setBackgroundColor(backgroundColor)
        // 设置 TextInputLayout 的 box 背景色
        textInputLayout?.boxBackgroundColor = backgroundColor
        binding.editTag.setBackgroundColor(backgroundColor)
        // 设置编辑框的文字颜色，确保在深色背景下也能正常显示
        binding.editTag.setTextColor(ThemeStore.textColorPrimary(requireContext()))

        // 显示现有的标签名称（如果有）
        existingTag?.let {
            binding.editTag.setText(it.name)
        }

        // 隐藏颜色选择相关控件和文字
        binding.btnSelectColor.visibility = View.GONE
        binding.colorHexValue.visibility = View.GONE
        binding.tagPreview.visibility = View.GONE
        binding.btnDelete.visibility = View.GONE

        // 隐藏分组选择相关控件
        binding.editGroup.visibility = View.GONE
        
        // 找到并隐藏颜色选择区域的整个LinearLayout
        val colorLayout = binding.btnSelectColor.parent as? android.widget.LinearLayout
        colorLayout?.visibility = View.GONE
        
        // 找到并隐藏分组选择的TextInputLayout
        val groupLayout = binding.editGroup.parent as? android.widget.LinearLayout
        groupLayout?.visibility = View.GONE

        // 调整按钮区域的 marginTop，减少空白空间
        val buttonLayout = binding.btnCancel.parent as? android.widget.LinearLayout
        buttonLayout?.let {
            val layoutParams = it.layoutParams as android.widget.LinearLayout.LayoutParams
            layoutParams.topMargin = 16 // 设置为与编辑框相同的间距
            it.layoutParams = layoutParams
        }

        // 确认按钮样式已在XML布局中定义

        // 确定按钮
        binding.btnConfirm.setOnClickListener {
            // 防止重复提交
            if (isProcessing) {
                return@setOnClickListener
            }

            val tagName = binding.editTag.text?.toString()?.trim()
            if (tagName.isNullOrEmpty()) {
                binding.editTag.error = "标签名称不能为空"
                return@setOnClickListener
            }

            isProcessing = true
            val excludedTag = ExcludedTag(name = tagName)
            callback?.invoke(excludedTag)
            dismiss()
        }

        // 取消按钮
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            existingTag: ExcludedTag? = null,
            callback: (ExcludedTag) -> Unit
        ) {
            val dialog = ExcludedTagEditDialog()
            dialog.existingTag = existingTag
            dialog.callback = callback
            dialog.show(fragmentManager, "excludedTagEditDialog")
        }
    }
}