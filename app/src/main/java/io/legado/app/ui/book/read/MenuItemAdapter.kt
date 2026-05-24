package io.legado.app.ui.book.read

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemCheckBoxBinding

/**
 * 菜单项适配器 - 简化版
 * 直接操作隐藏项集合，逻辑更清晰
 */
class MenuItemAdapter(context: Context) :
    RecyclerAdapter<MenuItemAdapter.DisplayItem, ItemCheckBoxBinding>(context) {

    data class DisplayItem(
        val id: Int,
        val name: String,
        val isSystemItem: Boolean = false,
        val systemItemKey: String? = null
    )

    // 隐藏的自定义菜单项ID
    private val hiddenItemIds = mutableSetOf<Int>()
    // 隐藏的系统菜单项Key
    private val hiddenSystemItemKeys = mutableSetOf<String>()

    /**
     * 设置隐藏的自定义菜单项ID
     */
    fun setHiddenItemIds(ids: Set<Int>) {
        hiddenItemIds.clear()
        hiddenItemIds.addAll(ids)
        notifyDataSetChanged()
    }

    /**
     * 设置隐藏的系统菜单项Key
     */
    fun setHiddenSystemItemKeys(keys: Set<String>) {
        hiddenSystemItemKeys.clear()
        hiddenSystemItemKeys.addAll(keys)
        notifyDataSetChanged()
    }

    /**
     * 获取隐藏的自定义菜单项ID
     */
    fun getHiddenItemIds(): Set<Int> {
        return hiddenItemIds.toSet()
    }

    /**
     * 获取隐藏的系统菜单项Key
     */
    fun getHiddenSystemItemKeys(): Set<String> {
        return hiddenSystemItemKeys.toSet()
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
            // 勾选表示可见，不勾选表示隐藏
            checkBox.isChecked = if (item.isSystemItem) {
                item.systemItemKey !in hiddenSystemItemKeys
            } else {
                item.id !in hiddenItemIds
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCheckBoxBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.isSystemItem) {
                    val key = item.systemItemKey ?: return@let
                    if (key in hiddenSystemItemKeys) {
                        hiddenSystemItemKeys.remove(key)
                    } else {
                        hiddenSystemItemKeys.add(key)
                    }
                } else {
                    if (item.id in hiddenItemIds) {
                        hiddenItemIds.remove(item.id)
                    } else {
                        hiddenItemIds.add(item.id)
                    }
                }
                notifyItemChanged(holder.layoutPosition)
            }
        }
    }
}
