package io.legado.app.ui.book.tag.excluded

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.databinding.ItemExcludedTagBinding

/**
 * 排除标签管理适配器
 */
class ExcludedTagManageAdapter(
    private val context: Context,
    private val viewModel: ExcludedTagManageViewModel
) : ListAdapter<ExcludedTag, ExcludedTagManageAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExcludedTagBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = getItem(position)
        holder.bind(tag)
        
        holder.itemView.setOnClickListener {
            // 显示删除确认对话框
            showDeleteConfirmDialog(context, tag)
        }
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(context: Context, tag: ExcludedTag) {
        AlertDialog.Builder(context)
            .setTitle("删除排除标签")
            .setMessage("确定要删除排除标签「${tag.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteTag(tag)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    class ViewHolder(private val binding: ItemExcludedTagBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(tag: ExcludedTag) {
            binding.tvTagName.text = tag.name
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ExcludedTag>() {
        override fun areItemsTheSame(oldItem: ExcludedTag, newItem: ExcludedTag): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExcludedTag, newItem: ExcludedTag): Boolean {
            return oldItem == newItem
        }
    }
}