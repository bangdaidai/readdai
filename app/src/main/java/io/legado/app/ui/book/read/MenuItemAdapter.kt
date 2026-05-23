package io.legado.app.ui.book.read

import android.content.Context
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemCheckBoxBinding

class MenuItemAdapter(context: Context) : RecyclerAdapter<MenuItemAdapter.Item, ItemCheckBoxBinding>(context) {

    data class Item(
        val id: Int,
        val nameResId: Int
    )

    private val checkedIds = mutableSetOf<Int>()

    fun setCheckedIds(ids: Set<Int>) {
        checkedIds.clear()
        checkedIds.addAll(ids)
    }

    fun getCheckedIds(): Set<Int> {
        return checkedIds.toSet()
    }

    override fun getViewBinding(parent: ViewGroup): ItemCheckBoxBinding {
        return ItemCheckBoxBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemCheckBoxBinding,
        item: Item,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            checkBox.text = context.getString(item.nameResId)
            checkBox.isChecked = item.id !in checkedIds
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCheckBoxBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.id in checkedIds) {
                    checkedIds.remove(item.id)
                } else {
                    checkedIds.add(item.id)
                }
                notifyItemChanged(holder.layoutPosition)
            }
        }
    }

}
