package io.legado.app.ui.book.tag.excluded.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import io.legado.app.ui.book.tag.excluded.ExcludedTagEditDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.databinding.ItemBookTagManageBinding
import io.legado.app.ui.book.tag.excluded.ExcludedTagManageViewModel
import io.legado.app.ui.widget.dialog.TextDialog

/**
 * 排除标签列表适配器
 */
class ExcludedTagAdapter(
    private val activity: FragmentActivity
) : ListAdapter<ExcludedTag, ExcludedTagAdapter.ViewHolder>(DiffCallback()) {

    private var viewModel: ExcludedTagManageViewModel? = null

    fun setViewModel(viewModel: ExcludedTagManageViewModel) {
        this.viewModel = viewModel
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookTagManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBookTagManageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tag: ExcludedTag) {
            binding.tvTagName.text = tag.name

            // 创建圆角背景并设置颜色（使用红色表示排除）
            val drawable = binding.root.context.getDrawable(R.drawable.bg_tag_rounded)
            val backgroundColor = 0xFFE57373.toInt() and 0x00FFFFFF or (0x1A000000) // 10%透明度红色
            drawable?.setTint(backgroundColor)
            binding.tvTagName.background = drawable

            // 字体颜色使用红色
            binding.tvTagName.setTextColor(0xFFE53935.toInt())

            // 设置点击事件
            binding.root.setOnClickListener {
                showPopupMenu(it, tag)
            }
        }

        private fun showPopupMenu(view: View, tag: ExcludedTag) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_excluded_tag_item, popup.menu)

            // 移除编辑选项，只保留删除功能
            popup.menu.removeItem(R.id.action_edit)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        showDeleteTagDialog(tag)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun showDeleteTagDialog(tag: ExcludedTag) {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("删除排除标签")
                .setMessage("确定要删除排除标签\"${tag.name}\"吗？")
                .setPositiveButton("确定") { _, _ ->
                    viewModel?.deleteTag(tag)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ExcludedTag>() {
        override fun areItemsTheSame(oldItem: ExcludedTag, newItem: ExcludedTag): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExcludedTag, newItem: ExcludedTag): Boolean {
            return oldItem.name == newItem.name
        }
    }
}