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
            return false
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
        notifyDataSetChanged()
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
            swApply.setOnCheckedChangeListener(null)
            swApply.isChecked = item.id == selectedId
            swApply.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    getItemByLayoutPosition(holder.layoutPosition)?.let {
                        callBack.onApply(it)
                    }
                } else {
                    swApply.setOnCheckedChangeListener(null)
                    swApply.isChecked = true
                    swApply.setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            getItemByLayoutPosition(holder.layoutPosition)?.let {
                                callBack.onApply(it)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(
        holder: ItemViewHolder,
        binding: ItemBookplateTemplateBinding
    ) {
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
