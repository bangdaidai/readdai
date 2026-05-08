package io.legado.app.ui.book.tag.manage.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.databinding.ItemBookTagManageBinding
import io.legado.app.databinding.ItemTagGroupHeaderBinding
import io.legado.app.ui.book.tag.manage.BookTagManageViewModel
import io.legado.app.ui.widget.dialog.BookTagEditDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 标签管理列表适配器
 */
class BookTagManageAdapter(
    private val activity: FragmentActivity
) : ListAdapter<BookTagManageAdapter.Item, BookTagManageAdapter.ViewHolder>(DiffCallback()) {

    private var viewModel: BookTagManageViewModel? = null
    private var groups: List<BookTagGroup> = emptyList()
    private var tagGroupMap: Map<Long, List<BookTag>> = emptyMap()
    private var displayItems: List<Item> = emptyList()

    fun setViewModel(viewModel: BookTagManageViewModel) {
        this.viewModel = viewModel
        viewModel.groups.observe(activity) { groups ->
            this.groups = groups
            updateDisplayItems()
        }
    }

    // 自定义方法来处理 BookTag 列表
    fun submitTagList(list: List<BookTag>?) {
        if (list != null) {
            // 按分组ID分组
            tagGroupMap = list.groupBy { it.groupId }
            updateDisplayItems()
        }
    }

    /**
     * 更新显示项，包括分组标题和标签
     */
    private fun updateDisplayItems() {
        val items = mutableListOf<Item>()

        // 先添加未分组的标签
        val ungroupedTags = tagGroupMap[0] ?: emptyList()
        if (ungroupedTags.isNotEmpty()) {
            items.add(Item.Header(0, "未分组"))
            ungroupedTags.forEach { items.add(Item.Tag(it)) }
        }

        // 然后按分组排序添加分组标签
        groups.sortedBy { it.sortOrder }.forEach { group ->
            val groupTags = tagGroupMap[group.id] ?: emptyList()
            if (groupTags.isNotEmpty()) {
                items.add(Item.Header(group.id, group.name))
                groupTags.forEach { items.add(Item.Tag(it)) }
            }
        }

        displayItems = items
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is Item.Header -> VIEW_TYPE_HEADER
            is Item.Tag -> VIEW_TYPE_TAG
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemTagGroupHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_TAG -> {
                val binding = ItemBookTagManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TagViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is Item.Header -> (holder as HeaderViewHolder).bind(item)
            is Item.Tag -> (holder as TagViewHolder).bind(item.tag)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val item = displayItems[position]
            if (item is Item.Tag && holder is TagViewHolder) {
                for (payload in payloads) {
                    if (payload == "COLOR_CHANGE") {
                        val tag = item.tag
                        val drawable = holder.itemView.context.getDrawable(R.drawable.bg_tag_rounded)
                        val backgroundColor = tag.color and 0x00FFFFFF or (0x1A000000) // 10%透明度
                        drawable?.setTint(backgroundColor)
                        holder.binding.tvTagName.background = drawable
                        holder.binding.tvTagName.setTextColor(tag.color)
                        return
                    } else if (payload == "NAME_CHANGE") {
                        holder.bind(item.tag)
                        return
                    }
                }
                onBindViewHolder(holder, position)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TagViewHolder) {
            holder.clear()
        }
    }

    override fun getItemCount(): Int {
        return displayItems.size
    }

    public override fun getItem(position: Int): Item {
        return displayItems[position]
    }

    sealed class Item {
        data class Header(val groupId: Long, val groupName: String) : Item()
        data class Tag(val tag: BookTag) : Item()
    }

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class HeaderViewHolder(private val binding: ItemTagGroupHeaderBinding) : ViewHolder(binding.root) {
        fun bind(header: Item.Header) {
            binding.tvGroupName.text = "#${header.groupName}"
            binding.tvGroupName.setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    inner class TagViewHolder(val binding: ItemBookTagManageBinding) : ViewHolder(binding.root) {

        private var currentTagId: Long = -1

        fun bind(tag: BookTag) {
            currentTagId = tag.id

            // 设置点击事件
            binding.root.setOnClickListener {
                showPopupMenu(it, tag)
            }

            // 先设置默认文本（包含数量占位符），确保测量时考虑完整文本长度
            binding.tvTagName.text = "${tag.name} 0"

            // 创建圆角背景并设置颜色（使用10%透明度）
            val drawable = binding.root.context.getDrawable(R.drawable.bg_tag_rounded)
            val backgroundColor = tag.color and 0x00FFFFFF or (0x1A000000) // 10%透明度
            drawable?.setTint(backgroundColor)
            binding.tvTagName.background = drawable

            // 字体颜色使用选择颜色本身
            binding.tvTagName.setTextColor(tag.color)

            // 异步获取关联书籍数量并更新显示
            // 使用Activity级别的CoroutineScope，避免ViewHolder回收时协程被取消
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bookCount = withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.countBooksByTagId(tag.id)
                    }
                    // 检查ViewHolder是否仍然绑定到同一个标签
                    if (currentTagId == tag.id && binding.tvTagName.isAttachedToWindow) {
                        // 合并显示标签名和数量，格式为"标签名 数量"
                        binding.tvTagName.text = "${tag.name} ($bookCount)"
                        // 强制重新测量和布局，确保标签显示完整
                        binding.tvTagName.requestLayout()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookTagManageAdapter", "获取书籍数量失败", e)
                    // 检查ViewHolder是否仍然绑定到同一个标签
                    if (currentTagId == tag.id && binding.tvTagName.isAttachedToWindow) {
                        // 发生错误时只显示标签名，数量默认为0
                        binding.tvTagName.text = "${tag.name} (0)"
                        // 强制重新测量和布局，确保标签显示完整
                        binding.tvTagName.requestLayout()
                    }
                }
            }
        }

        // 清理方法，现在不需要取消协程，因为使用的是临时的CoroutineScope
        fun clear() {
            currentTagId = -1
        }

        private fun showPopupMenu(view: View, tag: BookTag) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_book_tag_item, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        showEditTagDialog(tag)
                        true
                    }
                    R.id.action_search -> {
                        searchTagInReadingMemory(tag)
                        true
                    }
                    R.id.action_replace -> {
                        showReplaceTagDialog(tag)
                        true
                    }
                    R.id.action_exclude -> {
                        showExcludeTagDialog(tag)
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteTagDialog(tag)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun showReplaceTagDialog(tag: BookTag) {
            val alertBinding = io.legado.app.databinding.DialogEditTextBinding.inflate(LayoutInflater.from(activity))
            alertBinding.editView.hint = "请输入新标签名"
            alertBinding.editView.setText(tag.name)
            // 设置输入文本左边距，与其他内容对齐
            alertBinding.editView.setPadding(16, alertBinding.editView.paddingTop, alertBinding.editView.paddingRight, alertBinding.editView.paddingBottom)

            // 加载所有现有标签
            CoroutineScope(Dispatchers.IO).launch {
                val existingTags = appDb.bookTagDao.getAll()
                val tagNames = existingTags.map { it.name }.filter { it != tag.name }

                withContext(Dispatchers.Main) {
                    val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("替换标签")
                        .setMessage("将所有使用标签\"${tag.name}\"的书籍替换为新标签")
                        .setView(alertBinding.root)
                        .setPositiveButton("确定") { _, _ ->
                            val newTagName = alertBinding.editView.text?.toString()?.trim()
                            if (!newTagName.isNullOrEmpty() && newTagName != tag.name) {
                                viewModel?.replaceTag(tag, newTagName)
                            }
                        }
                        .setNegativeButton("取消", null)

                    // 如果有其他标签，添加选择按钮
                    if (tagNames.isNotEmpty()) {
                        builder.setNeutralButton("选择现有标签") { _, _ ->
                            val selectBuilder = androidx.appcompat.app.AlertDialog.Builder(activity)
                                .setTitle("选择现有标签")
                                .setItems(tagNames.toTypedArray()) { _, which ->
                                    val selectedTag = tagNames[which]
                                    alertBinding.editView.setText(selectedTag)
                                }
                            val selectDialog = selectBuilder.create()
                            // 设置选择现有标签对话框的背景色为主题背景色
                            selectDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(io.legado.app.lib.theme.ThemeStore.backgroundColor(activity)))
                            selectDialog.show()
                        }
                    }

                    val dialog = builder.create()
                    // 设置替换标签对话框的背景色为主题背景色
                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(io.legado.app.lib.theme.ThemeStore.backgroundColor(activity)))
                    dialog.show()
                }
            }
        }

        private fun searchTagInReadingMemory(tag: BookTag) {
            val intent = android.content.Intent(activity, io.legado.app.ui.book.readingmemory.ReadingMemoryActivity::class.java)
            intent.putExtra("tagName", tag.name)
            activity.startActivity(intent)
        }

        private fun showEditTagDialog(tag: BookTag) {
            BookTagEditDialog.show(
                fragmentManager = activity.supportFragmentManager,
                bookUrl = null,
                oldTagName = tag.name,
                callback = { tagInfo: BookTagEditDialog.TagInfo ->
                    val updatedTag = tag.copy(name = tagInfo.name, color = tagInfo.color, groupId = tagInfo.groupId)
                    viewModel?.updateTag(updatedTag, tag) // 传递旧标签信息
                }
            )
        }

        private fun showDeleteTagDialog(tag: BookTag) {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("删除标签")
                .setMessage("确定要删除标签\"${tag.name}\"吗？\n\n注意：这将会删除整个标签及其与所有书籍的关联关系。")
                .setPositiveButton("确定") { _, _ ->
                    viewModel?.deleteTag(tag)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        private fun showExcludeTagDialog(tag: BookTag) {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("排除标签")
                .setMessage("确定要将标签\"${tag.name}\"添加到排除列表吗？\n\n注意：添加到排除列表后，该标签将不会在标签列表中显示。")
                .setPositiveButton("确定") { _, _ ->
                    excludeTag(tag)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        private fun excludeTag(tag: BookTag) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 检查是否已经在排除列表中
                    val existingExcludedTag = appDb.excludedTagDao.getTagByName(tag.name)
                    if (existingExcludedTag == null) {
                        // 添加到排除列表
                        val excludedTag = ExcludedTag(
                            name = tag.name
                        )
                        appDb.excludedTagDao.insert(excludedTag)
                        withContext(Dispatchers.Main) {
                            showToast("标签已添加到排除列表")
                            // 刷新标签列表
                            viewModel?.loadTags()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showToast("标签已在排除列表中")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("添加到排除列表失败: ${e.localizedMessage}")
                    }
                }
            }
        }

        private fun showToast(message: String) {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when (oldItem) {
                is Item.Header -> newItem is Item.Header && oldItem.groupId == newItem.groupId
                is Item.Tag -> newItem is Item.Tag && oldItem.tag.id == newItem.tag.id
            }
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when (oldItem) {
                is Item.Header -> newItem is Item.Header && oldItem.groupName == newItem.groupName
                is Item.Tag -> newItem is Item.Tag && oldItem.tag.name == newItem.tag.name && oldItem.tag.color == newItem.tag.color && oldItem.tag.groupId == newItem.tag.groupId
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TAG = 1
    }
}