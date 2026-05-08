package io.legado.app.ui.book.readingmemory

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.databinding.DialogTimelineSettingsBinding

class TimelineSortDialog(private val currentSortBy: String, private val onConfirm: (String) -> Unit) : BaseDialogFragment(R.layout.dialog_timeline_settings) {

    private val binding by viewBinding(DialogTimelineSettingsBinding::bind)
    private var selectedSortBy = currentSortBy

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置标题栏背景色
        binding.toolBar.setBackgroundColor(primaryColor)

        // 设置标题栏
        binding.toolBar.apply {
            setTitle(getString(R.string.sort_method))
            setTitleTextColor(io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(requireContext()))
        }

        // 设置内容区域背景色
        val backgroundColor = io.legado.app.lib.theme.ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)

        // 设置排序选项
        when (currentSortBy) {
            "lastRead_desc" -> binding.timelineSortGroup.check(R.id.radioLastReadDesc)
            "lastRead_asc" -> binding.timelineSortGroup.check(R.id.radioLastReadAsc)
            "added_desc" -> binding.timelineSortGroup.check(R.id.radioAddedDesc)
            "added_asc" -> binding.timelineSortGroup.check(R.id.radioAddedAsc)
            else -> binding.timelineSortGroup.check(R.id.radioLastReadDesc)
        }

        // 监听排序变化，点击选项即为确认
        binding.timelineSortGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.radioLastReadDesc -> "lastRead_desc"
                R.id.radioLastReadAsc -> "lastRead_asc"
                R.id.radioAddedDesc -> "added_desc"
                R.id.radioAddedAsc -> "added_asc"
                else -> "lastRead_desc"
            }
            onConfirm(selected)
            dismiss()
        }
    }
}