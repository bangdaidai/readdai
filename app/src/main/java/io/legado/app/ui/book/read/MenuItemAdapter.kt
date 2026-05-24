package io.legado.app.ui.book.read

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemCheckBoxBinding

class MenuItemAdapter(context: Context) :
    RecyclerAdapter<MenuItemAdapter.DisplayItem, ItemCheckBoxBinding>(context) {

    companion object {
        private const val TAG = "MenuItemAdapter"
    }

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
        Log.d(TAG, "setCheckedIds: $checkedIds")
        notifyDataSetChanged() // 通知数据已变化
    }

    fun setCheckedSystemItemKeys(keys: Set<String>) {
        checkedSystemItemKeys.clear()
        checkedSystemItemKeys.addAll(keys)
        Log.d(TAG, "setCheckedSystemItemKeys: $checkedSystemItemKeys")
        notifyDataSetChanged() // 通知数据已变化
    }

    fun getCheckedIds(): Set<Int> {
        val result = checkedIds.toSet()
        Log.d(TAG, "getCheckedIds: $result")
        return result
    }

    fun getCheckedSystemItemKeys(): Set<String> {
        val result = checkedSystemItemKeys.toSet()
        Log.d(TAG, "getCheckedSystemItemKeys: $result")
        return result
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
            val isChecked = if (item.isSystemItem) {
                item.systemItemKey !in checkedSystemItemKeys
            } else {
                item.id !in checkedIds
            }
            Log.d(TAG, "convert: ${item.name}, isChecked=$isChecked, id=${item.id}, isSystemItem=${item.isSystemItem}")
            checkBox.isChecked = isChecked
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCheckBoxBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                Log.d(TAG, "clicked: ${item.name}")
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
                Log.d(TAG, "after click: checkedIds=$checkedIds, checkedSystemItemKeys=$checkedSystemItemKeys")
                notifyItemChanged(holder.layoutPosition)
            }
        }
    }
}
