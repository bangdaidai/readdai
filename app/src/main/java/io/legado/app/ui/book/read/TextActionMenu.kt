package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    companion object {
        private const val TAG = "TextActionMenu"
    }

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    private var menuItems: List<MenuItemImpl> = emptyList()
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private val expandTextMenu get() = context.getPrefBoolean(io.legado.app.constant.PreferKey.expandTextMenu)
    private val handler = Handler(Looper.getMainLooper())
    private var dismissCallback: (() -> Unit)? = null

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter
        setOnDismissListener {
            if (!context.getPrefBoolean(io.legado.app.constant.PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.visibility == View.VISIBLE) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        upMenu()
    }

    fun reloadMenuItems() {
        Log.d(TAG, "reloadMenuItems called")

        val myMenu = MenuBuilder(context)
        val otherMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)

        // 获取隐藏的菜单项
        val hiddenIds = TextMenuConfig.getHiddenMenuItemIds(context)
        Log.d(TAG, "Hidden custom items: $hiddenIds")

        // 添加自定义菜单项（过滤隐藏项）
        val customMenuItems = ArrayList<MenuItemImpl>()
        for (i in 0 until myMenu.size()) {
            val item = myMenu.getItem(i) as MenuItemImpl
            if (item.itemId !in hiddenIds) {
                customMenuItems.add(item)
            } else {
                Log.d(TAG, "Skipping hidden custom item: ${item.title}")
            }
        }

        // 添加系统菜单项（问小爱等）
        val systemMenuItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemProcessTextMenuItems()
        } else {
            emptyList()
        }

        // 合并所有菜单项
        menuItems = customMenuItems + systemMenuItems
        Log.d(TAG, "Total menu items after filter: ${menuItems.size}")

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

        Log.d(TAG, "Visible items: ${visibleMenuItems.size}, More items: ${moreMenuItems.size}")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSystemProcessTextMenuItems(): List<MenuItemImpl> {
        val tempMenu = MenuBuilder(context)
        val hiddenItems = TextMenuConfig.getHiddenProcessTextItems(context)
        Log.d(TAG, "Hidden system items: $hiddenItems")

        var menuItemOrder = 100
        for (resolveInfo in getSupportedActivities()) {
            val packageName = resolveInfo.activityInfo.packageName
            val className = resolveInfo.activityInfo.name
            val itemKey = TextMenuConfig.getProcessTextItemKey(packageName, className)

            if (itemKey !in hiddenItems) {
                val item = tempMenu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                )
                item.intent = createProcessTextIntentForResolveInfo(resolveInfo)
                Log.d(TAG, "Adding system item: ${item.title}")
            } else {
                Log.d(TAG, "Skipping hidden system item: ${resolveInfo.loadLabel(context.packageManager)}")
            }
        }

        // 从临时菜单中提取所有项
        val items = ArrayList<MenuItemImpl>()
        for (i in 0 until tempMenu.size()) {
            items.add(tempMenu.getItem(i) as MenuItemImpl)
        }
        return items
    }

    fun upMenu() {
        reloadMenuItems()

        if (expandTextMenu) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.visible()
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        upMenu() // 每次显示前重新加载
        if (expandTextMenu) {
            when {
                startTopY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.BOTTOM or Gravity.START,
                        startX,
                        windowHeight - startTopY
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, startX, startBottomY)
                }

                else -> {
                    showAtLocation(view, Gravity.TOP or Gravity.START, endX, endBottomY)
                }
            }
        } else {
            contentView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED,
            )
            val popupHeight = contentView.measuredHeight
            when {
                startBottomY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        startX,
                        startTopY - popupHeight
                    )
                }

                endBottomY - startBottomY > 500 -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        startX,
                        startBottomY
                    )
                }

                else -> {
                    showAtLocation(
                        view,
                        Gravity.TOP or Gravity.START,
                        endX,
                        endBottomY
                    )
                }
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.title
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    val needDelayDismiss = it.itemId == R.id.menu_ai_explain || it.itemId == R.id.menu_ai_analyze
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                    if (needDelayDismiss) {
                        dismissCallback = {
                            callBack.onMenuActionFinally()
                        }
                        handler.postDelayed({
                            dismissCallback?.invoke()
                            dismissCallback = null
                        }, 500)
                    } else {
                        callBack.onMenuActionFinally()
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                if (AppConfig.contentSelectSpeakMod == 0) {
                    AppConfig.contentSelectSpeakMod = 1
                    context.toastOnUi("切换为从选择的地方开始一直朗读")
                } else {
                    AppConfig.contentSelectSpeakMod = 0
                    context.toastOnUi("切换为朗读选择内容")
                }
                true
            }
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        io.legado.app.constant.AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}
