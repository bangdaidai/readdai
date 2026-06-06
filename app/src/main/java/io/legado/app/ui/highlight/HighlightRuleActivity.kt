package io.legado.app.ui.highlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.reflect.TypeToken
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.ActivityHighlightRuleBinding
import io.legado.app.help.highlight.HighlightRuleGroupStore
import io.legado.app.help.highlight.HighlightRuleStore
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.highlight.edit.HighlightRuleEditDialog
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleActivity : BaseActivity<ActivityHighlightRuleBinding>(),
    SearchView.OnQueryTextListener,
    SelectActionBar.CallBack,
    HighlightRuleAdapter.CallBack {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, HighlightRuleActivity::class.java))
        }
    }

    override val binding by viewBinding(ActivityHighlightRuleBinding::inflate)
    private val adapter by lazy { HighlightRuleAdapter(this, this) }
    private val rules = ArrayList<HighlightRule>()
    private var currentGroup: String? = null
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        initSelectActionView()
        loadRules()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.highlight_rule, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.queryHint = "搜索高亮规则"
        searchView.setOnQueryTextListener(this)
    }

    private fun initSelectActionView() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.highlight_rule_sel)
        binding.selectActionBar.setCallBack(this)
    }

    private fun loadRules() {
        lifecycleScope.launch {
            rules.clear()
            rules.addAll(HighlightRuleStore.load(this@HighlightRuleActivity))
            loadCurrentGroup()
            applyGroupFilter()
        }
    }

    private fun loadCurrentGroup() {
        val saved = getSharedPreferences("config", MODE_PRIVATE)
            .getString("highlightRuleCurrentGroup", null)
        if (!saved.isNullOrBlank()) {
            val groups = HighlightRuleGroupStore.load(this)
            if (groups.contains(saved)) {
                currentGroup = saved
            } else {
                currentGroup = null
                saveCurrentGroup()
            }
        }
    }

    private fun saveCurrentGroup() {
        getSharedPreferences("config", MODE_PRIVATE).edit()
            .putString("highlightRuleCurrentGroup", currentGroup.orEmpty())
            .apply()
    }

    private fun applyGroupFilter() {
        val filtered = getFilteredRules()
        adapter.setItems(filtered)
        updateSubtitle()
        saveCurrentGroup()
    }

    private fun getFilteredRules(): List<HighlightRule> {
        return if (currentGroup == null) {
            rules.toList()
        } else {
            rules.filter { it.group == currentGroup }
        }
    }

    private fun updateSubtitle() {
        val groupText = currentGroup ?: "全部分组"
        val count = getFilteredRules().size
        binding.tvSubtitle.text = "$groupText · $count 条规则"
    }

    private fun upGroupMenu() = groupMenu?.let { menu ->
        menu.removeGroup(R.id.highlight_group)
        HighlightRuleGroupStore.load(this).forEach {
            menu.add(R.id.highlight_group, Menu.NONE, Menu.NONE, it)
        }
    }

    private fun syncRules() {
        HighlightRuleStore.save(this, rules)
        applyGroupFilter()
    }

    // SelectActionBar
    override fun selectAll(selectAll: Boolean) {
        if (selectAll) adapter.selectAll() else adapter.revertSelection()
    }
    override fun revertSelection() = adapter.revertSelection()
    override fun onClickSelectBarMainAction() {
        alert(R.string.draw, R.string.sure_del) {
            yesButton {
                rules.removeAll(adapter.selection.toSet())
                syncRules()
            }
            noButton()
        }
    }
    override fun upCountView() = binding.selectActionBar.upCountView(
        adapter.selection.size, adapter.itemCount
    )

    // Search
    override fun onQueryTextChange(newText: String?): Boolean {
        val filtered = getFilteredRules()
        val key = newText?.trim()
        adapter.setItems(if (key.isNullOrBlank()) filtered else filtered.filter {
            it.name.contains(key, true) || it.pattern.contains(key, true) || it.group.contains(key, true)
        })
        return false
    }
    override fun onQueryTextSubmit(query: String?) = false

    // Menu
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> editRule(null)
            R.id.menu_import -> importRulesFromClipboard()
            R.id.menu_group_manage -> showGroupManager()
            R.id.menu_export -> exportRulesToClipboard(getFilteredRules())
            R.id.menu_reset -> resetRules()
            R.id.menu_enabled_group -> {
                val enabled = rules.filter { it.enabled }
                adapter.setItems(enabled)
            }
            R.id.menu_disabled_group -> {
                val disabled = rules.filter { !it.enabled }
                adapter.setItems(disabled)
            }
            else -> if (item.groupId == R.id.highlight_group) {
                currentGroup = item.title.toString().takeIf { it != "全部分组" }
                applyGroupFilter()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun resetRules() = alert("恢复默认") {
        setMessage("恢复后会重新生成预置规则，自定义规则会被覆盖。")
        okButton {
            rules.clear()
            rules.addAll(HighlightRuleStore.reset(this@HighlightRuleActivity))
            syncRules()
        }
        cancelButton()
    }

    private fun editRule(rule: HighlightRule?) {
        HighlightRuleEditDialog(rule, currentGroup) { newRule ->
            val index = rules.indexOfFirst { it.id == newRule.id }
            if (index >= 0) {
                rules[index] = newRule
            } else {
                rules.add(newRule)
            }
            syncRules()
        }.show(supportFragmentManager, "highlightRuleEdit")
    }

    private fun showGroupManager() {
        HighlightRuleGroupManageDialog(
            onChanged = { oldGroup, newGroup ->
                if (oldGroup != null && currentGroup == oldGroup) {
                    currentGroup = newGroup
                }
                loadRules()
            },
            onSelectGroup = { group ->
                currentGroup = group
                applyGroupFilter()
            }
        ).show(supportFragmentManager, "highlightRuleGroupManage")
    }

    private fun importRulesFromClipboard() {
        val clip = getClipText()
        if (clip.isNullOrBlank()) {
            toastOnUi("剪贴板为空")
            return
        }
        val type = object : TypeToken<List<HighlightRule>>() {}.type
        val imported: List<HighlightRule>? = GSON.fromJson(clip, type)
        if (imported.isNullOrEmpty()) {
            toastOnUi("导入格式错误")
            return
        }
        val targetGroup = currentGroup ?: HighlightRuleGroupStore.DEFAULT_GROUP
        imported.forEach { rule ->
            var normalized = HighlightRuleStore.sanitizeRule(rule, targetGroup)
            if (rules.any { it.id == normalized.id }) {
                normalized = normalized.copyWithNewId()
            }
            rules.add(normalized)
        }
        syncRules()
        toastOnUi("已导入 ${imported.size} 条规则")
    }

    private fun exportRulesToClipboard(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            toastOnUi("暂无规则可导出")
            return
        }
        sendToClip(GSON.toJson(targetRules))
        toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    private fun enableSelection(selection: List<HighlightRule>) {
        selection.forEach { it.enabled = true }
        syncRules()
    }

    private fun disableSelection(selection: List<HighlightRule>) {
        selection.forEach { it.enabled = false }
        syncRules()
    }

    // Adapter Callbacks
    override fun update(vararg rule: HighlightRule) = syncRules()
    override fun delete(rule: HighlightRule) {
        alert("删除") {
            setMessage("确定删除 \"${rule.name}\" 吗？")
            okButton { rules.removeAll { it.id == rule.id }; syncRules() }
            cancelButton()
        }
    }
    override fun edit(rule: HighlightRule) = editRule(rule)
    override fun switchEnable(rule: HighlightRule, enabled: Boolean) {
        rule.enabled = enabled
        syncRules()
    }
}
