package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
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

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }
    private var menuItems: List<MenuItem> = emptyList()
    private val visibleMenuItems = arrayListOf<MenuItem>()
    private val moreMenuItems = arrayListOf<MenuItem>()
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)
    private val handler = Handler(Looper.getMainLooper())
    private var dismissCallback: (() -> Unit)? = null

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
            if (!context.getPrefBoolean(PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }
        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
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

    /**
     * 重新加载菜单项，根据配置过滤被隐藏的菜单项
     */
    private fun reloadMenuItems() {
        val hiddenIds = TextMenuConfig.getHiddenMenuItemIds(context)
        val hiddenProcessTextItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TextMenuConfig.getHiddenProcessTextItems(context)
        } else {
            emptySet()
        }

        val myMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)

        val customMenuItems = myMenu.visibleItems
            .filter { it.itemId !in hiddenIds }
            .map { MenuItem(it.itemId, it.title.toString()) }

        val systemMenuItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemProcessTextItems(hiddenProcessTextItems)
        } else {
            emptyList()
        }

        menuItems = customMenuItems + systemMenuItems

        visibleMenuItems.clear()
        moreMenuItems.clear()

        if (menuItems.size > 5) {
            visibleMenuItems.addAll(menuItems.subList(0, 5))
            moreMenuItems.addAll(menuItems.subList(5, menuItems.size))
        } else {
            visibleMenuItems.addAll(menuItems)
        }
    }

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
                val itemKey = TextMenuConfig.getProcessTextItemKey(packageName, className)
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
            with(binding) {
                textView.text = item.title
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    if (item.intent != null) {
                        kotlin.runCatching {
                            context.startActivity(item.intent)
                        }.onFailure { e ->
                            AppLog.put("执行文本菜单操作出错\n$e", e, true)
                        }
                        callBack.onMenuActionFinally()
                    } else {
                        val needDelayDismiss = item.id == R.id.menu_ai_explain || item.id == R.id.menu_ai_analyze
                        if (!callBack.onMenuItemSelected(item.id)) {
                            onMenuItemSelected(item.id)
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

    private fun onMenuItemSelected(itemId: Int) {
        when (itemId) {
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
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}
