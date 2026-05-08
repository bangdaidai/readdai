package io.legado.app.ui.book.tag.manage

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.TagMapping
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemTagMappingBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 标签映射管理对话框
 */
class TagMappingManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),

    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private lateinit var viewModel: BookTagManageViewModel
    private val adapter by lazy { TagMappingAdapter(requireContext()) }
    private var mappings: List<TagMapping> = emptyList()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val backgroundColor = ThemeStore.backgroundColor(requireContext())
        view.setBackgroundColor(backgroundColor)
        initView()
        initData()
    }

    private fun initView() = binding.run {
        val primaryColor = ThemeStore.primaryColor(requireContext())
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        
        toolBar.setBackgroundColor(primaryColor)
        toolBar.title = "标签映射管理"
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        // 移除添加按钮，因为标签映射是自动创建的
        toolBar.menu.removeItem(R.id.menu_add)
        toolBar.setOnMenuItemClickListener(this@TagMappingManageDialog)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initData() {
        lifecycleScope.launch {
            loadMappings()
        }
    }

    private suspend fun loadMappings() {
        mappings = viewModel.loadTagMappings()
        adapter.setItems(mappings)
        binding.tvMsg.visibility = if (mappings.isEmpty()) View.VISIBLE else View.GONE
        binding.tvMsg.text = "无标签映射关系"
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return true
    }

    /**
     * 删除标签映射
     */
    private fun deleteMapping(mapping: TagMapping) {
        alert(title = getString(R.string.delete), message = "确定要删除标签映射关系吗？") {
            yesButton {
                viewModel.deleteTagMapping(mapping)
                lifecycleScope.launch {
                    loadMappings()
                }
            }
            noButton()
        }
    }

    private inner class TagMappingAdapter(context: Context) :
        RecyclerAdapter<TagMapping, ItemTagMappingBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTagMappingBinding {
            return ItemTagMappingBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTagMappingBinding,
            item: TagMapping,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(ThemeStore.backgroundColor(context))
                val textPrimaryColor = ThemeStore.textColorPrimary(context)
                val accentColor = ThemeStore.accentColor(context)
                tvOldTag.text = item.oldTagName
                tvOldTag.setTextColor(textPrimaryColor)
                tvNewTag.setTextColor(textPrimaryColor)
                ivArrow.setColorFilter(textPrimaryColor)
                tvDelete.setTextColor(accentColor)
                lifecycleScope.launch {
                    val newTagName = viewModel.getTagName(item.newTagId)
                    tvNewTag.text = newTagName ?: "未知标签"
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTagMappingBinding) {
            binding.apply {
                tvDelete.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        deleteMapping(it)
                    }
                }
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager, viewModel: BookTagManageViewModel) {
            val dialog = TagMappingManageDialog()
            dialog.viewModel = viewModel
            dialog.show(fragmentManager, "tagMappingManageDialog")
        }
    }
}
