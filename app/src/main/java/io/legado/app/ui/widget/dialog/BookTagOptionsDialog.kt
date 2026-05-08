package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogBookTagOptionsBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.setLayout

class BookTagOptionsDialog : BaseDialogFragment(R.layout.dialog_book_tag_options) {

    private val binding by lazy { DialogBookTagOptionsBinding.bind(requireView()) }
    private var bookUrl: String? = null
    private var tagName: String? = null
    private var callback: ((String) -> Unit)? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.5f) // 保留高度设置
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(ThemeStore.primaryColor(requireContext()))
        binding.tvTagTitle.text = tagName
        initData()
    }

    private fun initData() {
        binding.llEditTag.setOnClickListener {
            callback?.invoke("edit")
            dismiss()
        }
        
        binding.llDeleteTag.setOnClickListener {
            callback?.invoke("delete")
            dismiss()
        }
        
        binding.llFindSimilar.setOnClickListener {
            callback?.invoke("find")
            dismiss()
        }
    }

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            bookUrl: String,
            tagName: String,
            callback: (String) -> Unit
        ) {
            BookTagOptionsDialog().apply {
                this.bookUrl = bookUrl
                this.tagName = tagName
                this.callback = callback
            }.show(fragmentManager, "bookTagOptionsDialog")
        }
    }
}