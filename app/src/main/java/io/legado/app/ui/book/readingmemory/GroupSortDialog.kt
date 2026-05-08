package io.legado.app.ui.book.readingmemory

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.databinding.DialogGroupSettingsBinding

class GroupSortDialog(private val currentGroupBy: String, private val onConfirm: (String) -> Unit) : BaseDialogFragment(R.layout.dialog_group_settings) {

    private val binding by viewBinding(DialogGroupSettingsBinding::bind)
    private var selectedGroupBy = currentGroupBy

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置标题栏背景色
        binding.toolBar.setBackgroundColor(primaryColor)

        // 设置标题栏
        binding.toolBar.apply {
            setTitle(getString(R.string.group_method))
            setTitleTextColor(io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(requireContext()))
        }

        // 设置内容区域背景色
        val backgroundColor = io.legado.app.lib.theme.ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)

        // 设置分组选项
        when (currentGroupBy) {
            "none" -> binding.groupTypeGroup.check(R.id.radioNone)
            "year" -> binding.groupTypeGroup.check(R.id.radioYear)
            "rating" -> binding.groupTypeGroup.check(R.id.radioRating)
            "status" -> binding.groupTypeGroup.check(R.id.radioStatus)
            else -> binding.groupTypeGroup.check(R.id.radioNone)
        }

        // 监听分组变化，点击选项即为确认
        binding.groupTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.radioNone -> "none"
                R.id.radioYear -> "year"
                R.id.radioRating -> "rating"
                R.id.radioStatus -> "status"
                else -> "none"
            }
            onConfirm(selected)
            dismiss()
        }
    }
}