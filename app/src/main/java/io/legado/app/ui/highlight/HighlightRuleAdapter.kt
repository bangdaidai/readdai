package io.legado.app.ui.highlight

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.ItemHighlightRuleBinding
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback

class HighlightRuleAdapter(context: Context, var callBack: CallBack) :
    RecyclerAdapter<HighlightRule, ItemHighlightRuleBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<HighlightRule>()

    val selection: List<HighlightRule>
        get() {
            return getItems().filter {
                selected.contains(it)
            }
        }

    val diffItemCallBack = object : DiffUtil.ItemCallback<HighlightRule>() {

        override fun areItemsTheSame(oldItem: HighlightRule, newItem: HighlightRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HighlightRule, newItem: HighlightRule): Boolean {
            if (oldItem.name != newItem.name) {
                return false
            }
            if (oldItem.group != newItem.group) {
                return false
            }
            if (oldItem.enabled != newItem.enabled) {
                return false
            }
            return true
        }

        override fun getChangePayload(oldItem: HighlightRule, newItem: HighlightRule): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name
                || oldItem.group != newItem.group
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enabled != newItem.enabled) {
                payload.putBoolean("enabled", newItem.enabled)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }
    }

    fun selectAll() {
        getItems().forEach {
            selected.add(it)
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    override fun getViewBinding(parent: ViewGroup): ItemHighlightRuleBinding {
        return ItemHighlightRuleBinding.inflate(inflater, parent, false)
    }

    override fun onCurrentListChanged() {
        callBack.upCountView()
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemHighlightRuleBinding,
        item: HighlightRule,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                cbName.text = item.name.ifBlank { item.pattern.ifBlank { "未命名规则" } }
                swtEnabled.isChecked = item.enabled
                cbName.isChecked = selected.contains(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbName.isChecked = selected.contains(item)
                            "upName" -> cbName.text = item.name.ifBlank { item.pattern.ifBlank { "未命名规则" } }
                            "enabled" -> swtEnabled.isChecked = item.enabled
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightRuleBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let {
                    it.enabled = isChecked
                    callBack.switchEnable(it, isChecked)
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
            cbName.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (cbName.isChecked) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                }
                callBack.upCountView()
            }
        }
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        swapItem(srcPosition, targetPosition)
        return true
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<HighlightRule>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<HighlightRule> {
                return selected
            }

            override fun getItemId(position: Int): HighlightRule {
                return getItem(position)!!
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let {
                    if (isSelected) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }

    interface CallBack {
        fun update(vararg rule: HighlightRule)
        fun delete(rule: HighlightRule)
        fun edit(rule: HighlightRule)
        fun switchEnable(rule: HighlightRule, enabled: Boolean)
        fun upCountView()
    }
}
