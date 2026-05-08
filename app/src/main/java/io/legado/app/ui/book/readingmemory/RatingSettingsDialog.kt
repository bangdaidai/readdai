package io.legado.app.ui.book.readingmemory

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.databinding.DialogRatingSettingsBinding

class RatingSettingsDialog(private val currentRatingFilter: String, private val currentRatingSort: String, private val onConfirm: (String, String) -> Unit) : BaseDialogFragment(R.layout.dialog_rating_settings) {

    private val binding by viewBinding(DialogRatingSettingsBinding::bind)
    private var selectedRatingFilter = currentRatingFilter
    private var selectedRatingSort = currentRatingSort

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 设置标题栏背景色
        binding.toolBar.setBackgroundColor(primaryColor)

        // 设置标题栏
        binding.toolBar.apply {
            setTitle(getString(R.string.rating_settings))
            setTitleTextColor(io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(requireContext()))
        }

        // 设置内容区域背景色
        val backgroundColor = io.legado.app.lib.theme.ThemeStore.backgroundColor(requireContext())
        binding.root.setBackgroundColor(backgroundColor)

        // 设置评分筛选选项
        when (currentRatingFilter) {
            "all" -> binding.ratingFilterGroup.check(R.id.radioAll)
            "5" -> binding.ratingFilterGroup.check(R.id.radio5Star)
            "4" -> binding.ratingFilterGroup.check(R.id.radio4Star)
            "3" -> binding.ratingFilterGroup.check(R.id.radio3Star)
            "2" -> binding.ratingFilterGroup.check(R.id.radio2Star)
            "1" -> binding.ratingFilterGroup.check(R.id.radio1Star)
            "unrated" -> binding.ratingFilterGroup.check(R.id.radioUnrated)
        }

        // 设置评分排序选项
        if (currentRatingSort == "high_to_low") {
            binding.ratingSortGroup.check(R.id.radioHighToLow)
        } else {
            binding.ratingSortGroup.check(R.id.radioLowToHigh)
        }

        // 监听评分筛选变化，点击选项即为确认
        binding.ratingFilterGroup.setOnCheckedChangeListener { _, checkedId ->
            val filter = when (checkedId) {
                R.id.radioAll -> "all"
                R.id.radio5Star -> "5"
                R.id.radio4Star -> "4"
                R.id.radio3Star -> "3"
                R.id.radio2Star -> "2"
                R.id.radio1Star -> "1"
                R.id.radioUnrated -> "unrated"
                else -> "all"
            }
            val sort = if (binding.ratingSortGroup.checkedRadioButtonId == R.id.radioHighToLow) "high_to_low" else "low_to_high"
            onConfirm(filter, sort)
            dismiss()
        }

        // 监听评分排序变化，点击选项即为确认
        binding.ratingSortGroup.setOnCheckedChangeListener { _, checkedId ->
            val sort = if (checkedId == R.id.radioHighToLow) "high_to_low" else "low_to_high"
            val filter = when (binding.ratingFilterGroup.checkedRadioButtonId) {
                R.id.radioAll -> "all"
                R.id.radio5Star -> "5"
                R.id.radio4Star -> "4"
                R.id.radio3Star -> "3"
                R.id.radio2Star -> "2"
                R.id.radio1Star -> "1"
                R.id.radioUnrated -> "unrated"
                else -> "all"
            }
            onConfirm(filter, sort)
            dismiss()
        }
    }
}