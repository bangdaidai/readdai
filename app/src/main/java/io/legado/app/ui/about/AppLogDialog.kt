package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick
import java.util.*

class AppLogDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy {
        LogAdapter(requireContext())
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.log)
            toolBar.inflateMenu(R.menu.app_log)
            toolBar.setOnMenuItemClickListener(this@AppLogDialog)
            // 设置工具栏图标颜色为标题栏文字图标颜色
            val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
            toolBar.setTitleTextColor(titleBarTextIconColor)
            // 设置溢出菜单图标颜色为标题栏文字图标颜色
            toolBar.overflowIcon?.setTint(titleBarTextIconColor)
            // 设置菜单项图标和文字颜色为标题栏文字图标颜色
            for (i in 0 until toolBar.menu.size()) {
                val item = toolBar.menu.getItem(i)
                item.icon?.setTint(titleBarTextIconColor)
                // 设置菜单项文字颜色
                val title = item.title.toString()
                val spannable = android.text.SpannableString(title)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(titleBarTextIconColor),
                    0,
                    title.length,
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = spannable
            }
            root.setBackgroundColor(ThemeStore.backgroundColor(requireContext()))
            recyclerView.setBackgroundColor(ThemeStore.backgroundColor(requireContext()))
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.setItems(AppLog.logs)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> {
                AppLog.clear()
                adapter.clearItems()
            }
        }
        return true
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = item.second
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    item.third?.let {
                        showDialogFragment(TextDialog("Log", it.stackTraceToString()))
                    }
                }
            }
        }

    }

}