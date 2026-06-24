package io.legado.app.ui.book.bookplate

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.databinding.ItemBookplateTemplateBinding

class BookplateTemplateAdapter(
    context: Context,
    private val callBack: CallBack
) : RecyclerAdapter<BookplateTemplate, ItemBookplateTemplateBinding>(context) {

    private var selectedId = 0L
    private var ignoreNextChange = false

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
        ignoreNextChange = true
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
            tvTemplateName.text = item.name
            ignoreNextChange = true
            swApply.isChecked = item.id == selectedId
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemBookplateTemplateBinding
    ) {
        binding.swApply.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreNextChange) {
                ignoreNextChange = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    callBack.onApply(item)
                }
            } else {
                ignoreNextChange = true
                binding.swApply.isChecked = true
            }
        }
        binding.ivPreview.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                callBack.onPreview(item)
            }
        }
        binding.ivEdit.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                callBack.onEdit(item)
            }
        }
        binding.ivDelete.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                callBack.onDelete(item)
            }
        }
    }

    interface CallBack {
        fun onApply(item: BookplateTemplate)
        fun onPreview(item: BookplateTemplate)
        fun onEdit(item: BookplateTemplate)
        fun onDelete(item: BookplateTemplate)
    }
}
