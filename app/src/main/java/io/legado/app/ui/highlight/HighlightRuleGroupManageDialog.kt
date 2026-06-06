package io.legado.app.ui.highlight

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemGroupManageBinding
import io.legado.app.help.highlight.HighlightRuleGroupStore
import io.legado.app.help.highlight.HighlightRuleStore
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleGroupManageDialog(
    private val onChanged: (oldGroup: String?, newGroup: String?) -> Unit = { _, _ -> },
    private val onSelectGroup: (String?) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private var groups = listOf<String>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundColor(backgroundColor)
        initView()
        loadData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.title = "分组管理"
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        toolBar.menu.findItem(R.id.menu_add)?.icon?.setTint(io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(requireContext()))
        toolBar.setOnMenuItemClickListener(this@HighlightRuleGroupManageDialog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun loadData() {
        groups = HighlightRuleGroupStore.load(requireContext())
        adapter.setItems(groups)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> addGroup()
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun addGroup() {
        alert(title = "新增分组") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint("分组名称")
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotBlank()) {
                        val currentGroups = HighlightRuleGroupStore.load(requireContext()).toMutableList()
                        if (currentGroups.contains(it)) {
                            requireContext().toastOnUi("分组已存在")
                            return@okButton
                        }
                        currentGroups.add(it)
                        HighlightRuleGroupStore.save(requireContext(), currentGroups)
                        loadData()
                        onChanged(null, null)
                    }
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    @SuppressLint("InflateParams")
    private fun editGroup(group: String) {
        alert(title = "编辑分组") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint("分组名称")
                editView.setText(group)
            }
            customView { alertBinding.root }
            okButton {
                val newName = alertBinding.editView.text?.toString().orEmpty()
                if (newName.isBlank()) {
                    requireContext().toastOnUi("分组名称不能为空")
                    return@okButton
                }
                val currentGroups = HighlightRuleGroupStore.load(requireContext()).toMutableList()
                if (currentGroups.contains(newName) && newName != group) {
                    requireContext().toastOnUi("分组名称已存在")
                    return@okButton
                }
                val index = currentGroups.indexOf(group)
                if (index >= 0) {
                    currentGroups[index] = newName
                    val rules = HighlightRuleStore.load(requireContext()).toMutableList()
                    rules.replaceAll { rule ->
                        if (rule.group == group) rule.copy(group = newName) else rule
                    }
                    HighlightRuleGroupStore.save(requireContext(), currentGroups)
                    HighlightRuleStore.save(requireContext(), rules)
                    loadData()
                    onChanged(group, newName)
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    private fun deleteGroup(group: String) {
        if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
            requireContext().toastOnUi("默认分组不能删除")
            return
        }
        alert(title = "删除分组") {
            setMessage("删除后，该分组下的规则会移动到默认分组。")
            okButton {
                val currentGroups = HighlightRuleGroupStore.load(requireContext()).toMutableList()
                currentGroups.remove(group)
                val rules = HighlightRuleStore.load(requireContext()).toMutableList()
                rules.replaceAll { rule ->
                    if (rule.group == group) rule.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP) else rule
                }
                HighlightRuleGroupStore.save(requireContext(), currentGroups)
                HighlightRuleStore.save(requireContext(), rules)
                loadData()
                onChanged(group, null)
            }
            cancelButton()
        }
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<String, ItemGroupManageBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemGroupManageBinding {
            return ItemGroupManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupManageBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                tvGroup.text = item
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
                    getItem(holder.layoutPosition)?.let {
                        deleteGroup(it)
                    }
                }
            }
        }
    }
}
