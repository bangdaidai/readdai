package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemTextMenuConfigBinding
import io.legado.app.utils.toastOnUi

class TextMenuConfigDialog : BaseDialogFragment(R.layout.dialog_text_menu_config) {

    private var hiddenIds = mutableSetOf<Int>()
    private var customTitles = mutableMapOf<Int, String>()
    private var visibleCount = TextMenuConfig.DEFAULT_VISIBLE_COUNT
    private lateinit var adapter: MenuConfigAdapter

    private data class MenuItemData(
        val info: TextMenuConfig.MenuItemInfo,
        var isVisible: Boolean,
        var customTitle: String?
    )

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

        val rv = view.findViewById<RecyclerView>(R.id.recycler_view)
        rv.layoutManager = LinearLayoutManager(context)

        val items = TextMenuConfig.getAllMenuItems().map { item ->
            MenuItemData(
                info = item,
                isVisible = item.id !in hiddenIds,
                customTitle = customTitles[item.id]
            )
        }

        adapter = MenuConfigAdapter(context) { data ->
            showEditTitleDialog(data)
        }
        adapter.setItems(items)
        rv.adapter = adapter

        val tvCount = view.findViewById<android.widget.TextView>(R.id.tv_count)
        tvCount.text = visibleCount.toString()

        view.findViewById<android.widget.Button>(R.id.btn_less).setOnClickListener {
            if (visibleCount > TextMenuConfig.MIN_VISIBLE_COUNT) {
                visibleCount--
                tvCount.text = visibleCount.toString()
            }
        }

        view.findViewById<android.widget.Button>(R.id.btn_more).setOnClickListener {
            if (visibleCount < TextMenuConfig.MAX_VISIBLE_COUNT) {
                visibleCount++
                tvCount.text = visibleCount.toString()
            }
        }

        view.findViewById<android.widget.Button>(R.id.btn_reset).setOnClickListener {
            TextMenuConfig.resetToDefault(context)
            hiddenIds.clear()
            customTitles.clear()
            visibleCount = TextMenuConfig.DEFAULT_VISIBLE_COUNT
            tvCount.text = visibleCount.toString()
            adapter.setItems(TextMenuConfig.getAllMenuItems().map { item ->
                MenuItemData(
                    info = item,
                    isVisible = true,
                    customTitle = null
                )
            })
        }

        view.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }

        view.findViewById<android.widget.Button>(R.id.btn_save).setOnClickListener {
            val newHiddenIds = mutableSetOf<Int>()
            adapter.getItems().forEach { item ->
                if (!item.isVisible) newHiddenIds.add(item.info.id)
            }
            TextMenuConfig.setHiddenMenuItemIds(context, newHiddenIds)
            TextMenuConfig.setTextMenuVisibleCount(context, visibleCount)
            context.toastOnUi("菜单配置已保存")
            (activity as? ReadBookActivity)?.textActionMenu?.upMenu()
            dismiss()
        }
    }

    private fun showEditTitleDialog(data: MenuItemData) {
        val context = requireContext()
        val defaultTitle = TextMenuConfig.getDefaultMenuTitle(context, data.info)
        val currentTitle = data.customTitle ?: defaultTitle

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
                if (newTitle.isBlank() || newTitle == defaultTitle) {
                    TextMenuConfig.setCustomMenuTitle(context, data.info.id, null)
                    data.customTitle = null
                } else {
                    TextMenuConfig.setCustomMenuTitle(context, data.info.id, newTitle)
                    data.customTitle = newTitle
                }
                adapter.notifyItemChanged(adapter.getItems().indexOf(data))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        val context = requireContext()
        hiddenIds = TextMenuConfig.getHiddenMenuItemIds(context).toMutableSet()
        customTitles = TextMenuConfig.getCustomMenuTitles(context).toMutableMap()
        visibleCount = TextMenuConfig.getTextMenuVisibleCount(context)
    }

    private inner class MenuConfigAdapter(
        context: android.content.Context,
        private val onEditClick: (MenuItemData) -> Unit
    ) : RecyclerAdapter<MenuItemData, ItemTextMenuConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemTextMenuConfigBinding {
            return ItemTextMenuConfigBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextMenuConfigBinding) {}

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextMenuConfigBinding,
            item: MenuItemData,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                val title = item.customTitle
                    ?: TextMenuConfig.getDefaultMenuTitle(context, item.info)
                tvTitle.text = title
                cbVisible.isChecked = item.isVisible

                btnEdit.setOnClickListener {
                    onEditClick(item)
                }

                root.setOnClickListener {
                    item.isVisible = !item.isVisible
                    cbVisible.isChecked = item.isVisible
                }

                cbVisible.setOnCheckedChangeListener(null)
                cbVisible.setOnCheckedChangeListener { _, checked ->
                    item.isVisible = checked
                }
            }
        }
    }
}
