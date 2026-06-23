package io.legado.app.ui.book.bookplate

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ItemBookplateTemplateBinding

class BookplateTemplateAdapter(
    context: Context,
    private val callBack: CallBack
) : RecyclerAdapter<BookplateTemplate, ItemBookplateTemplateBinding>(context) {

    private var selectedId = 0L

    val diffItemCallback = object : DiffUtil.ItemCallback<BookplateTemplate>() {
        override fun areItemsTheSame(
            oldItem: BookplateTemplate,
            newItem: BookplateTemplate
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: BookplateTemplate,
            newItem: BookplateTemplate
        ): Boolean {
            return oldItem.name == newItem.name
                    && oldItem.isBuiltin == newItem.isBuiltin
        }

        override fun getChangePayload(oldItem: BookplateTemplate, newItem: BookplateTemplate): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name) {
                payload.putBoolean("upName", true)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }
    }

    fun setSelectedId(id: Long) {
        selectedId = id
        notifyItemRangeChanged(getHeaderCount(), itemCount - getHeaderCount())
    }

    override fun getViewBinding(parent: ViewGroup): ItemBookplateTemplateBinding {
        return ItemBookplateTemplateBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookplateTemplateBinding,
        item: BookplateTemplate,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            cbTemplate.text = item.name
            cbTemplate.isChecked = item.id == selectedId
            cbTemplate.setOnCheckedChangeListener(null)
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemBookplateTemplateBinding
    ) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                callBack.onSelect(item)
            }
        }
        binding.ivPreview.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                callBack.onPreview(item)
            }
        }
        binding.ivEdit.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                callBack.onEdit(item)
            }
        }
        binding.ivMenuMore.setOnClickListener { view ->
            getItem(holder.layoutPosition)?.let { item ->
                showPopupMenu(view, item)
            }
        }
    }

    private fun showPopupMenu(view: View, item: BookplateTemplate) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.bookplate_item_menu, popup.menu)
        if (item.isBuiltin) {
            popup.menu.findItem(R.id.menu_delete)?.isVisible = false
        }
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_preview -> {
                    callBack.onPreview(item)
                    true
                }
                R.id.menu_edit -> {
                    callBack.onEdit(item)
                    true
                }
                R.id.menu_delete -> {
                    callBack.onDelete(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    interface CallBack {
        fun onSelect(item: BookplateTemplate)
        fun onPreview(item: BookplateTemplate)
        fun onEdit(item: BookplateTemplate)
        fun onDelete(item: BookplateTemplate)
    }
}
