package io.legado.app.ui.book.read

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemTextMenuConfigBinding
import io.legado.app.utils.toastOnUi

class TextMenuConfigDialog : BaseDialogFragment(R.layout.dialog_text_menu_config) {

    private enum class TabType { TEXT_MENU, PROCESS_TEXT }

    private var currentTab = TabType.TEXT_MENU

    private var hiddenIds = mutableSetOf<Int>()
    private var customTitles = mutableMapOf<Int, String>()
    private var visibleCount = TextMenuConfig.DEFAULT_VISIBLE_COUNT

    private var hiddenProcessKeys = mutableSetOf<String>()
    private var customProcessTitles = mutableMapOf<String, String>()

    private lateinit var adapter: MenuConfigAdapter

    private sealed class MenuItem {
        data class TextMenuItem(
            val info: TextMenuConfig.MenuItemInfo,
            var isVisible: Boolean,
            var customTitle: String?
        ) : MenuItem()

        data class ProcessTextItem(
            val key: String,
            val title: String,
            var isVisible: Boolean,
            var customTitle: String?
        ) : MenuItem()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        hiddenIds = TextMenuConfig.getHiddenMenuItemIds(context).toMutableSet()
        customTitles = TextMenuConfig.getCustomMenuTitles(context).toMutableMap()
        visibleCount = TextMenuConfig.getTextMenuVisibleCount(context)

        hiddenProcessKeys = TextMenuConfig.getHiddenProcessTextItems(context).toMutableSet()
        customProcessTitles = TextMenuConfig.getCustomProcessTextTitles(context).toMutableMap()

        val rv = view.findViewById<RecyclerView>(R.id.recycler_view)
        rv.layoutManager = LinearLayoutManager(context)

        adapter = MenuConfigAdapter(context) { item ->
            showEditTitleDialog(item)
        }
        rv.adapter = adapter

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = view.findViewById<android.widget.TextView>(R.id.tv_desc)
        val llVisibleCount = view.findViewById<View>(R.id.ll_visible_count)
        val tvCount = view.findViewById<android.widget.TextView>(R.id.tv_count)
        val btnLess = view.findViewById<android.widget.Button>(R.id.btn_less)
        val btnMoreCount = view.findViewById<android.widget.Button>(R.id.btn_more_count)
        val btnSwitch = view.findViewById<android.widget.TextView>(R.id.btn_switch)

        fun updateTab(tab: TabType) {
            currentTab = tab
            when (tab) {
                TabType.TEXT_MENU -> {
                    tvTitle.setText(R.string.text_menu_config)
                    tvDesc.setText(R.string.text_menu_config_desc)
                    btnSwitch.setText(R.string.process_text_menu_config)
                    llVisibleCount.visibility = View.VISIBLE
                    tvCount.text = visibleCount.toString()
                    val items = TextMenuConfig.getAllMenuItems().map { item ->
                        MenuItem.TextMenuItem(
                            info = item,
                            isVisible = item.id !in hiddenIds,
                            customTitle = customTitles[item.id]
                        )
                    }
                    adapter.setItems(items)
                }
                TabType.PROCESS_TEXT -> {
                    tvTitle.setText(R.string.process_text_menu_config)
                    tvDesc.setText(R.string.process_text_menu_config_desc)
                    btnSwitch.setText(R.string.text_menu_config)
                    llVisibleCount.visibility = View.GONE
                    val items = getProcessTextItems().map { info ->
                        val packageName = info.activityInfo.packageName
                        val className = info.activityInfo.name
                        val key = TextMenuConfig.getProcessTextItemKey(packageName, className)
                        MenuItem.ProcessTextItem(
                            key = key,
                            title = info.loadLabel(context.packageManager).toString(),
                            isVisible = key !in hiddenProcessKeys,
                            customTitle = customProcessTitles[key]
                        )
                    }
                    adapter.setItems(items)
                }
            }
        }

        btnSwitch.setOnClickListener {
            val nextTab = if (currentTab == TabType.TEXT_MENU) TabType.PROCESS_TEXT else TabType.TEXT_MENU
            updateTab(nextTab)
        }

        btnLess.setOnClickListener {
            if (visibleCount > TextMenuConfig.MIN_VISIBLE_COUNT) {
                visibleCount--
                tvCount.text = visibleCount.toString()
            }
        }

        btnMoreCount.setOnClickListener {
            if (visibleCount < TextMenuConfig.MAX_VISIBLE_COUNT) {
                visibleCount++
                tvCount.text = visibleCount.toString()
            }
        }

        view.findViewById<android.widget.Button>(R.id.btn_reset).setOnClickListener {
            when (currentTab) {
                TabType.TEXT_MENU -> {
                    TextMenuConfig.resetToDefault(context)
                    hiddenIds.clear()
                    customTitles.clear()
                    visibleCount = TextMenuConfig.DEFAULT_VISIBLE_COUNT
                    tvCount.text = visibleCount.toString()
                    adapter.setItems(TextMenuConfig.getAllMenuItems().map { item ->
                        MenuItem.TextMenuItem(
                            info = item,
                            isVisible = true,
                            customTitle = null
                        )
                    })
                }
                TabType.PROCESS_TEXT -> {
                    TextMenuConfig.resetProcessTextConfig(context)
                    hiddenProcessKeys.clear()
                    customProcessTitles.clear()
                    adapter.setItems(getProcessTextItems().map { info ->
                        val packageName = info.activityInfo.packageName
                        val className = info.activityInfo.name
                        val key = TextMenuConfig.getProcessTextItemKey(packageName, className)
                        MenuItem.ProcessTextItem(
                            key = key,
                            title = info.loadLabel(context.packageManager).toString(),
                            isVisible = true,
                            customTitle = null
                        )
                    })
                }
            }
        }

        view.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }

        view.findViewById<android.widget.Button>(R.id.btn_save).setOnClickListener {
            adapter.getItems().forEach { item ->
                when (item) {
                    is MenuItem.TextMenuItem -> {
                        if (!item.isVisible) {
                            hiddenIds.add(item.info.id)
                        } else {
                            hiddenIds.remove(item.info.id)
                        }
                        if (item.customTitle.isNullOrBlank()) {
                            customTitles.remove(item.info.id)
                        } else {
                            customTitles[item.info.id] = item.customTitle!!
                        }
                    }
                    is MenuItem.ProcessTextItem -> {
                        if (!item.isVisible) {
                            hiddenProcessKeys.add(item.key)
                        } else {
                            hiddenProcessKeys.remove(item.key)
                        }
                        if (item.customTitle.isNullOrBlank()) {
                            customProcessTitles.remove(item.key)
                        } else {
                            customProcessTitles[item.key] = item.customTitle!!
                        }
                    }
                }
            }
            TextMenuConfig.setHiddenMenuItemIds(context, hiddenIds)
            TextMenuConfig.setCustomMenuTitles(context, customTitles)
            TextMenuConfig.setTextMenuVisibleCount(context, visibleCount)
            TextMenuConfig.setHiddenProcessTextItems(context, hiddenProcessKeys)
            TextMenuConfig.setCustomProcessTextTitles(context, customProcessTitles)
            context.toastOnUi("菜单配置已保存")
            (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
            dismiss()
        }

        updateTab(TabType.TEXT_MENU)
    }

    private fun setCustomMenuTitles(context: android.content.Context, titles: Map<Int, String>) {
        TextMenuConfig.setCustomMenuTitles(context, titles)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getProcessTextItems(): List<ResolveInfo> {
        val intent = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        return requireContext().packageManager.queryIntentActivities(intent, 0)
    }

    private fun showEditTitleDialog(item: MenuItem) {
        val context = requireContext()
        val (defaultTitle, currentTitle) = when (item) {
            is MenuItem.TextMenuItem -> {
                TextMenuConfig.getDefaultMenuTitle(context, item.info) to
                    (item.customTitle ?: TextMenuConfig.getDefaultMenuTitle(context, item.info))
            }
            is MenuItem.ProcessTextItem -> {
                item.title to (item.customTitle ?: item.title)
            }
        }

        val editText = android.widget.EditText(context).apply {
            setText(currentTitle)
            setHint(defaultTitle)
            setSingleLine()
            setSelectAllOnFocus(true)
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.text_menu_edit_title)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTitle = editText.text.toString().trim()
                val normalizedTitle = if (newTitle.isBlank() || newTitle == defaultTitle) null else newTitle
                when (item) {
                    is MenuItem.TextMenuItem -> item.customTitle = normalizedTitle
                    is MenuItem.ProcessTextItem -> item.customTitle = normalizedTitle
                }
                adapter.notifyItemChanged(adapter.getItems().indexOf(item))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class MenuConfigAdapter(
        context: android.content.Context,
        private val onEditClick: (MenuItem) -> Unit
    ) : RecyclerAdapter<MenuItem, ItemTextMenuConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTextMenuConfigBinding {
            return ItemTextMenuConfigBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextMenuConfigBinding) {}

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextMenuConfigBinding,
            item: MenuItem,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                val title = when (item) {
                    is MenuItem.TextMenuItem -> item.customTitle
                        ?: TextMenuConfig.getDefaultMenuTitle(context, item.info)
                    is MenuItem.ProcessTextItem -> item.customTitle ?: item.title
                }
                tvTitle.text = title
                cbVisible.isChecked = when (item) {
                    is MenuItem.TextMenuItem -> item.isVisible
                    is MenuItem.ProcessTextItem -> item.isVisible
                }

                btnEdit.setOnClickListener {
                    onEditClick(item)
                }

                root.setOnClickListener {
                    when (item) {
                        is MenuItem.TextMenuItem -> {
                            item.isVisible = !item.isVisible
                            cbVisible.isChecked = item.isVisible
                        }
                        is MenuItem.ProcessTextItem -> {
                            item.isVisible = !item.isVisible
                            cbVisible.isChecked = item.isVisible
                        }
                    }
                }

                cbVisible.setOnCheckedChangeListener(null)
                cbVisible.setOnCheckedChangeListener { _, checked ->
                    when (item) {
                        is MenuItem.TextMenuItem -> item.isVisible = checked
                        is MenuItem.ProcessTextItem -> item.isVisible = checked
                    }
                }
            }
        }
    }
}
