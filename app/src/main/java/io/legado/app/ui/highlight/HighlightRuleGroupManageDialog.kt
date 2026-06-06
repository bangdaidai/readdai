package io.legado.app.ui.highlight

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.DialogHighlightRuleGroupManageBinding
import io.legado.app.databinding.ItemHighlightRuleGroupBinding
import io.legado.app.help.highlight.HighlightRuleGroupStore
import io.legado.app.help.highlight.HighlightRuleStore
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleGroupManageDialog(
    private val onChanged: (oldGroup: String?, newGroup: String?) -> Unit = { _, _ -> },
    private val onSelectGroup: (String?) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_rule_group_manage) {

    private val binding by viewBinding(DialogHighlightRuleGroupManageBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private val groups = ArrayList<String>()
    private val rules = ArrayList<HighlightRule>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnAddGroup.setOnClickListener { showGroupInputDialog(null) }
        binding.llViewAll.setOnClickListener {
            onSelectGroup(null)
            dismissAllowingStateLoss()
        }
        loadData()
    }

    private fun loadData() {
        groups.clear()
        groups.addAll(HighlightRuleGroupStore.load(requireContext()))
        rules.clear()
        rules.addAll(HighlightRuleStore.load(requireContext()))
        adapter.setItems(groups.toList())
    }

    private fun showGroupInputDialog(source: String?) {
        val editText = EditText(requireContext()).apply {
            setText(source.orEmpty())
            setSelection(text.length)
            hint = "输入分组名称"
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 0)
            addView(editText)
        }
        alert(if (source == null) "新增分组" else "重命名分组") {
            customView { container }
            okButton {
                val newName = editText.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    toastOnUi("分组名称不能为空")
                    return@okButton
                }
                if (groups.contains(newName) && newName != source) {
                    toastOnUi("分组名称已存在")
                    return@okButton
                }
                if (source == null) {
                    groups.add(newName)
                    HighlightRuleGroupStore.save(requireContext(), groups)
                    loadData()
                    onChanged(null, null)
                } else {
                    val index = groups.indexOf(source)
                    if (index >= 0) groups[index] = newName
                    rules.replaceAll { rule ->
                        if (rule.group == source) rule.copy(group = newName) else rule
                    }
                    HighlightRuleGroupStore.save(requireContext(), groups)
                    HighlightRuleStore.save(requireContext(), rules)
                    loadData()
                    onChanged(source, newName)
                }
            }
            cancelButton()
        }
    }

    private fun showGroupOptionsDialog(group: String) {
        alert("分组操作") {
            items(listOf("重命名", "导出", "删除")) { _, index ->
                when (index) {
                    0 -> showGroupInputDialog(group)
                    1 -> exportGroup(group)
                    2 -> deleteGroup(group)
                }
            }
            cancelButton()
        }
    }

    private fun deleteGroup(group: String) {
        if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
            toastOnUi("默认分组不能删除")
            return
        }
        alert("删除分组") {
            setMessage("删除后，该分组下的规则会移动到默认分组。")
            okButton {
                groups.remove(group)
                rules.replaceAll { rule ->
                    if (rule.group == group) {
                        rule.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP)
                    } else {
                        rule
                    }
                }
                HighlightRuleGroupStore.save(requireContext(), groups)
                HighlightRuleStore.save(requireContext(), rules)
                loadData()
                onChanged(group, null)
            }
            cancelButton()
        }
    }

    private fun exportGroup(group: String) {
        val targetRules = rules.filter { it.group == group }
        if (targetRules.isEmpty()) {
            toastOnUi("该分组暂无规则可导出")
            return
        }
        requireContext().sendToClip(GSON.toJson(targetRules))
        toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    private fun groupCount(group: String): Int {
        return rules.count { it.group == group }
    }

    private inner class GroupAdapter(context: android.content.Context) :
        RecyclerAdapter<String, ItemHighlightRuleGroupBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightRuleGroupBinding {
            return ItemHighlightRuleGroupBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightRuleGroupBinding) {
            binding.itemRoot.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    onSelectGroup(group)
                    dismissAllowingStateLoss()
                }
            }
            binding.itemRoot.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    showGroupOptionsDialog(group)
                }
                true
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::showGroupInputDialog)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    deleteGroup(group)
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightRuleGroupBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item
            binding.tvCount.text = "${groupCount(item)} 条规则"
            binding.tvDelete.visibility = if (item == HighlightRuleGroupStore.DEFAULT_GROUP) View.GONE else View.VISIBLE
        }
    }
}
