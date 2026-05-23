package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

class TextMenuConfigDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { MenuItemAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        toolBar.setTitle(R.string.text_menu_config)
        toolBar.inflateMenu(R.menu.text_menu_config)
        toolBar.setNavigationOnClickListener { dismiss() }
        toolBar.setOnMenuItemClickListener {
            if (it.itemId == R.id.menu_reset) {
                TextMenuConfig.resetToDefault(requireContext())
                TextMenuConfig.resetProcessTextConfig(requireContext())
                reloadMenuItems()
                toastOnUi(getString(R.string.reset_to_default))
                true
            } else {
                false
            }
        }

        // 显示保存和取消按钮
        tvCancel.visible()
        tvOk.visible()

        tvCancel.setOnClickListener {
            dismiss()
        }

        tvOk.setOnClickListener {
            saveConfig()
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        reloadMenuItems()
    }

    private fun reloadMenuItems() {
        // 添加自定义菜单项
        val customItems = TextMenuConfig.getAllMenuItems().map {
            MenuItemAdapter.DisplayItem(it.id, requireContext().getString(it.nameResId))
        }

        // 加载并添加系统菜单项（问小爱等）
        val systemItems = mutableListOf<MenuItemAdapter.DisplayItem>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = android.content.Intent()
                    .setAction(android.content.Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                val resolveInfoList = requireContext().packageManager.queryIntentActivities(intent, 0)
                resolveInfoList.forEach { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val className = resolveInfo.activityInfo.name
                    val itemKey = TextMenuConfig.getProcessTextItemKey(packageName, className)
                    systemItems.add(
                        MenuItemAdapter.DisplayItem(
                            id = itemKey.hashCode(),
                            name = resolveInfo.loadLabel(requireContext().packageManager).toString(),
                            isSystemItem = true,
                            systemItemKey = itemKey
                        )
                    )
                }
            } catch (e: Exception) {
                // 忽略
            }
        }

        adapter.setItems(customItems + systemItems)
        adapter.setCheckedIds(TextMenuConfig.getHiddenMenuItemIds(requireContext()))
        adapter.setCheckedSystemItemKeys(TextMenuConfig.getHiddenProcessTextItems(requireContext()))
    }

    private fun saveConfig() {
        TextMenuConfig.setHiddenMenuItemIds(requireContext(), adapter.getCheckedIds())
        TextMenuConfig.setHiddenProcessTextItems(requireContext(), adapter.getCheckedSystemItemKeys())
    }

    override fun dismiss() {
        // 不在这里保存，让用户点击保存按钮才保存
        super.dismiss()
    }
}
