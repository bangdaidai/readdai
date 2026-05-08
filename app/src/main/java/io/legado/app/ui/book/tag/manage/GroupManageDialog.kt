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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * 标签分组管理对话框
 */
class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private lateinit var viewModel: BookTagManageViewModel
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private var groups: List<BookTagGroup> = emptyList()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f) // 保留高度设置
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
        toolBar.title = getString(R.string.group_manage)
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        // 设置加号图标为标题栏文字图标颜色
        toolBar.menu.findItem(R.id.menu_add)?.icon?.setTint(titleBarTextIconColor)
        toolBar.setOnMenuItemClickListener(this@GroupManageDialog)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter

        // 初始化拖拽排序（只保留上下拖拽功能）
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                if (fromPosition < toPosition) {
                    for (i in fromPosition until toPosition) {
                        // 交换排序值
                        val temp = groups[i]
                        groups = groups.toMutableList().also { 
                            it[i] = it[i + 1].copy(sortOrder = i)
                            it[i + 1] = temp.copy(sortOrder = i + 1)
                        }
                    }
                } else {
                    for (i in fromPosition downTo toPosition + 1) {
                        // 交换排序值
                        val temp = groups[i]
                        groups = groups.toMutableList().also { 
                            it[i] = it[i - 1].copy(sortOrder = i)
                            it[i - 1] = temp.copy(sortOrder = i - 1)
                        }
                    }
                }
                adapter.setItems(groups)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 未使用
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖拽结束后保存排序变化
                viewModel.updateGroupSort(groups)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun initData() {
        lifecycleScope.launch {
            viewModel.groups.observe(viewLifecycleOwner) {
                groups = it
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> addGroup()
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun addGroup() {
        alert(title = getString(R.string.add_group)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotBlank()) {
                        viewModel.addGroup(it)
                    }
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    @SuppressLint("InflateParams")
    private fun editGroup(group: BookTagGroup) {
        alert(title = getString(R.string.group_edit)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setText(group.name)
            }
            customView { alertBinding.root }
            okButton {
                val newName = alertBinding.editView.text?.toString()?.trim()
                if (!newName.isNullOrBlank()) {
                    // 使用updateGroupName方法更新分组名称，保持标签的分组关联
                    viewModel.updateGroupName(group, newName)
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    /**
     * 删除分组
     */
    private fun deleteGroup(group: BookTagGroup) {
        alert(title = getString(R.string.delete), message = "确定要删除分组\"${group.name}\"吗？\n\n删除后，该分组下的所有标签将移到未分组。") {
            yesButton {
                viewModel.deleteGroup(group)
            }
            noButton()
        }
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<BookTagGroup, ItemGroupManageBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemGroupManageBinding {
            return ItemGroupManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupManageBinding,
            item: BookTagGroup,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(io.legado.app.lib.theme.ThemeStore.backgroundColor(context))
                tvGroup.text = item.name
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupManageBinding) {
            binding.apply {
                tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        editGroup(it)
                    }
                }
                tvDel.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { deleteGroup(it) }
                }
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager, viewModel: BookTagManageViewModel) {
            val dialog = GroupManageDialog()
            dialog.viewModel = viewModel
            dialog.show(fragmentManager, "groupManageDialog")
        }
    }
}