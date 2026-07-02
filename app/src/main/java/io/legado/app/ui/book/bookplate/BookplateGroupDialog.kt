package io.legado.app.ui.book.bookplate

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemGroupManageBinding
import io.legado.app.help.book.BookplateGenerator
import io.legado.app.help.book.BookplateHtmlRenderer
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class BookplateGroupDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundColor(backgroundColor)
        initView()
        initData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.title = "分组管理"
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        toolBar.menu.findItem(R.id.menu_add)?.icon?.setTint(ThemeStore.titleBarTextIconColor(requireContext()))
        toolBar.setOnMenuItemClickListener(this@BookplateGroupDialog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.bookplateTemplateDao.flowDistinctGroupNames().conflate().collect {
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
        alert(title = "添加分组") {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint("分组名称")
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { name ->
                    if (name.isNotBlank()) {
                        lifecycleScope.launch {
                            val now = System.currentTimeMillis()
                            val template = BookplateTemplate(
                                name = name,
                                htmlContent = BookplateGenerator.DEFAULT_TEMPLATE_HTML,
                                isBuiltin = true,
                                groupName = name,
                                createTime = now,
                                updateTime = now
                            )
                            withContext(Dispatchers.IO) {
                                appDb.bookplateTemplateDao.insert(template)
                            }
                            BookplateHtmlRenderer.clearCache()
                        }
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
                alertBinding.editView.text?.toString()?.let { newName ->
                    if (newName.isNotBlank() && newName != group) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                appDb.bookplateTemplateDao.updateGroupName(group, newName)
                            }
                            BookplateHtmlRenderer.clearCache()
                        }
                    }
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    @SuppressLint("InflateParams")
    private fun deleteGroup(group: String) {
        alert(title = "删除分组") {
            setMessage("确定要删除分组「${group}」及其所有模板吗？")
            okButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.bookplateTemplateDao.deleteByGroupName(group)
                    }
                    BookplateHtmlRenderer.clearCache()
                    toastOnUi("已删除分组「${group}」")
                }
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
                    getItem(holder.layoutPosition)?.let { deleteGroup(it) }
                }
            }
        }
    }

}
