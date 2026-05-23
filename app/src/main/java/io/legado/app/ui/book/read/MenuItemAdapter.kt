package io.legado.app.ui.book.read

import android.content.Context
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemCheckBoxBinding

class MenuItemAdapter(context: Context) :
    RecyclerAdapter<MenuItemAdapter.DisplayItem, ItemCheckBoxBinding>(context) {

    data class DisplayItem(
        val id: Int,
        val name: String,
        val isSystemItem: Boolean = false,
        val systemItemKey: String? = null
    )

    private val checkedIds = mutableSetOf<Int>()
    private val checkedSystemItemKeys = mutableSetOf<String>()

    fun setCheckedIds(ids: Set<Int>) {
        checkedIds.clear()
        checkedIds.addAll(ids)
    }

    fun setCheckedSystemItemKeys(keys: Set<String>) {
        checkedSystemItemKeys.clear()
        checkedSystemItemKeys.addAll(keys)
    }

    fun getCheckedIds(): Set<Int> {
        return checkedIds.toSet()
    }

    fun getCheckedSystemItemKeys(): Set<String> {
        return checkedSystemItemKeys.toSet()
    }

    override fun getViewBinding(parent: ViewGroup): ItemCheckBoxBinding {
        return ItemCheckBoxBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemCheckBoxBinding,
        item: DisplayItem,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            checkBox.text = item.name
            checkBox.isChecked = if (item.isSystemItem) {
                item.systemItemKey !in checkedSystemItemKeys
            } else {
                item.id !in checkedIds
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCheckBoxBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.isSystemItem) {
                    if (item.systemItemKey in checkedSystemItemKeys) {
                        checkedSystemItemKeys.remove(item.systemItemKey)
                    } else {
                        checkedSystemItemKeys.add(item.systemItemKey!!)
                    }
                } else {
                    if (item.id in checkedIds) {
                        checkedIds.remove(item.id)
                    } else {
                        checkedIds.add(item.id)
                    }
                }
                notifyItemChanged(holder.layoutPosition)
            }
        }
    }
}
