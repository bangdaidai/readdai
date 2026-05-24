package io.legado.app.ui.book.read

import android.content.Context
import android.view.ViewGroup
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

    private val visibleItemIds = mutableSetOf<Int>()
    private val visibleSystemItemKeys = mutableSetOf<String>()

    /**
     * 设置要显示的自定义菜单项ID（即应该勾选的
     */
    fun setVisibleItemIds(ids: Set<Int>) {
        visibleItemIds.clear()
        visibleItemIds.addAll(ids)
        notifyDataSetChanged()
    }

    /**
     * 设置要显示的系统菜单项Key
     */
    fun setVisibleSystemItemKeys(keys: Set<String>) {
        visibleSystemItemKeys.clear()
        visibleSystemItemKeys.addAll(keys)
        notifyDataSetChanged()
    }

    /**
     * 获取要隐藏的自定义菜单项ID
     */
    fun getHiddenItemIds(): Set<Int> {
        val result = mutableSetOf<Int>()
        for (i in 0 until itemCount) {
            val item = getItem(i) ?: continue
            if (!item.isSystemItem && item.id !in visibleItemIds) {
                result.add(item.id)
            }
        }
        return result
    }

    /**
     * 获取要隐藏的系统菜单项Key
     */
    fun getHiddenSystemItemKeys(): Set<String> {
        val result = mutableSetOf<String>()
        for (i in 0 until itemCount) {
            val item = getItem(i) ?: continue
            if (item.isSystemItem && item.systemItemKey != null && item.systemItemKey !in visibleSystemItemKeys) {
                result.add(item.systemItemKey)
            }
        }
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
            checkBox.isChecked = if (item.isSystemItem) {
                item.systemItemKey in visibleSystemItemKeys
            } else {
                item.id in visibleItemIds
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCheckBoxBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.isSystemItem) {
                    if (item.systemItemKey in visibleSystemItemKeys) {
                        visibleSystemItemKeys.remove(item.systemItemKey)
                    } else {
                        visibleSystemItemKeys.add(item.systemItemKey!!)
                    }
                } else {
                    if (item.id in visibleItemIds) {
                        visibleItemIds.remove(item.id)
                    } else {
                        visibleItemIds.add(item.id)
                    }
                }
                notifyItemChanged(holder.layoutPosition)
            }
        }
    }
}
