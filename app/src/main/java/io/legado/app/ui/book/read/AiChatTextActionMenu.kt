package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.utils.sendToClip

/**
 * AI 对话页面文本操作菜单
 *
 * 功能说明：
 * 长按文本后显示的弹出菜单，提供复制、搜书、追问等操作
 * 使用自定义 PopupWindow 样式，与阅读页面的 TextActionMenu 一致
 */
@SuppressLint("RestrictedApi")
class AiChatTextActionMenu(
    private val context: Context,
    private val callBack: CallBack
) : PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    private var menuItems: List<MenuItem> = emptyList()
    private val visibleMenuItems = arrayListOf<MenuItem>()
    private val moreMenuItems = arrayListOf<MenuItem>()

    /**
     * 菜单项数据类
     */
    data class MenuItem(
        val id: Int,
        val title: String,
        val intent: Intent? = null
    )

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter

        setOnDismissListener {
            binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
            binding.recyclerViewMore.visibility = View.GONE
            adapter.setItems(visibleMenuItems)
            binding.recyclerView.visibility = View.VISIBLE
        }

        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.visibility == View.VISIBLE) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.visibility = View.GONE
                binding.recyclerViewMore.visibility = View.VISIBLE
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.visibility = View.GONE
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visibility = View.VISIBLE
            }
        }

        upMenu()
    }

    /**
     * 重新加载菜单项，根据配置过滤被隐藏的菜单项
     */
    private fun reloadMenuItems() {
        // 获取用户配置的隐藏菜单项
        val hiddenIds = AiChatMenuConfig.getHiddenMenuItemIds(context)
        val hiddenProcessTextItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AiChatMenuConfig.getHiddenProcessTextItems(context)
        } else {
            emptySet()
        }

        // 构建基础菜单项列表
        val baseMenuItems = AiChatMenuConfig.getAllMenuItems()
            .filter { it.id !in hiddenIds }
            .map { MenuItem(it.id, context.getString(it.nameResId)) }

        // 添加系统菜单项（ACTION_PROCESS_TEXT 应用，如"问小爱"）
        val systemMenuItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemProcessTextItems(hiddenProcessTextItems)
        } else {
            emptyList()
        }

        // 合并所有菜单项
        menuItems = baseMenuItems + systemMenuItems

        // 清空旧数据
        visibleMenuItems.clear()
        moreMenuItems.clear()

        // 将菜单项分为可见项（前5项）和更多项（第5项之后）
        if (menuItems.size > 5) {
            visibleMenuItems.addAll(menuItems.subList(0, 5))
            moreMenuItems.addAll(menuItems.subList(5, menuItems.size))
        } else {
            visibleMenuItems.addAll(menuItems)
        }
    }

    /**
     * 获取系统文本处理应用（如"问小爱"）
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSystemProcessTextItems(hiddenItems: Set<String>): List<MenuItem> {
        return try {
            val intent = Intent().apply {
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
            }
            val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
            resolveInfoList.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val className = resolveInfo.activityInfo.name
                val itemKey = AiChatMenuConfig.getProcessTextItemKey(packageName, className)
                if (itemKey in hiddenItems) {
                    return@mapNotNull null
                }
                MenuItem(
                    id = itemKey.hashCode(),
                    title = resolveInfo.loadLabel(context.packageManager).toString(),
                    intent = Intent().apply {
                        action = Intent.ACTION_PROCESS_TEXT
                        type = "text/plain"
                        putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        setClassName(packageName, className)
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upMenu() {
        reloadMenuItems()

        if (menuItems.size <= 5) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.visibility = View.GONE
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.visibility = View.VISIBLE
        }
    }

    fun show(
        anchor: View,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        upMenu()

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        when {
            startBottomY > 500 -> {
                showAtLocation(
                    anchor,
                    Gravity.TOP or Gravity.START,
                    startX,
                    startTopY
                )
            }
            endBottomY - startBottomY > 500 -> {
                showAtLocation(anchor, Gravity.TOP or Gravity.START, startX, startBottomY)
            }
            else -> {
                showAtLocation(anchor, Gravity.TOP or Gravity.START, endX, endBottomY)
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItem, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItem,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.title
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let { menuItem ->
                    // 如果菜单项有 intent，执行它（系统菜单项）
                    menuItem.intent?.let { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 忽略启动失败
                        }
                    } ?: run {
                        // 否则调用回调处理
                        val handled = callBack.onMenuItemSelected(menuItem.id)
                        if (!handled) {
                            when (menuItem.id) {
                                R.id.menu_copy -> {
                                    context.sendToClip(callBack.selectedText)
                                }
                            }
                        }
                    }
                    callBack.onMenuActionFinally()
                }
            }
        }
    }

    /**
     * 回调接口
     */
    interface CallBack {
        val selectedText: String

        /**
         * 菜单项被选中时的回调
         * @param itemId 菜单项ID
         * @return true表示已处理，false表示未处理需要菜单自己处理
         */
        fun onMenuItemSelected(itemId: Int): Boolean

        /**
         * 菜单操作完成后的回调
         */
        fun onMenuActionFinally()
    }
}
