package io.legado.app.base

import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.MenuExtensions
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setBackgroundKeepPadding
import io.legado.app.utils.setLayout
import io.legado.app.utils.observeEvent
import io.legado.app.utils.applyTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


abstract class BaseDialogFragment(
    @LayoutRes layoutID: Int,
    private val adaptationSoftKeyboard: Boolean = false
) : DialogFragment(layoutID) {

    private var onDismissListener: OnDismissListener? = null

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this.onDismissListener = onDismissListener
    }

    override fun onStart() {
        super.onStart()
        setLayout(1.0f, -1f)
        if (adaptationSoftKeyboard) {
            dialog?.window?.setBackgroundDrawableResource(R.color.transparent)
        } else if (AppConfig.isEInkMode) {
            dialog?.window?.let {
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val attr = it.attributes
                attr.dimAmount = 0.0f
                attr.windowAnimations = 0
                it.attributes = attr
                it.decorView.setBackgroundKeepPadding(R.color.transparent)
            }
            // 修改gravity的时机一般在子类的onStart方法中, 因此需要在onStart之后执行.
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    when (dialog?.window?.attributes?.gravity) {
                        Gravity.TOP -> view?.setBackgroundResource(R.drawable.bg_eink_border_bottom)
                        Gravity.BOTTOM -> view?.setBackgroundResource(R.drawable.bg_eink_border_top)
                        else -> {
                            val padding = 2.dpToPx();
                            view?.setPadding(padding, padding, padding, padding)
                            view?.setBackgroundResource(R.drawable.bg_eink_border_dialog)
                        }
                    }
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            //不加这个android 5.0对话框顶部会有空白
            setStyle(STYLE_NO_TITLE, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (adaptationSoftKeyboard) {
            view.findViewById<View>(R.id.vw_bg)?.setOnClickListener(null)
            view.setOnClickListener { dismiss() }
        } else if (!AppConfig.isEInkMode) {
            // 根视图背景色设置为透明
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        // 统一设置对话框内容区域的背景色为主题自定义的背景色
        view.findViewById<View>(R.id.root)?.setBackgroundColor(ThemeStore.backgroundColor(requireContext()))
        // 设置对话框中 Toolbar 的颜色为主题自定义的标题栏文字图标颜色
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.tool_bar)
        if (toolbar != null) {
            val textColor = ThemeStore.titleBarTextIconColor(requireContext())
            toolbar.setTitleTextColor(textColor)
            toolbar.setSubtitleTextColor(textColor)
            // 设置导航图标和溢出菜单图标的颜色
            toolbar.navigationIcon?.setTint(textColor)
            toolbar.overflowIcon?.setTint(textColor)
        }
        onFragmentCreated(view, savedInstanceState)
        observeLiveBus()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.applyTint(requireContext(), Theme.Auto)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            dismiss()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    abstract fun onFragmentCreated(view: View, savedInstanceState: Bundle?)

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            //在每个add事务前增加一个remove事务，防止连续的add
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context) { block() }

    open fun observeLiveBus() {
        observeEvent<String>(io.legado.app.constant.EventBus.THEME_CHANGED) {
            dismiss()
        }
        observeEvent<String>(io.legado.app.constant.EventBus.RECREATE) {
            dismiss()
        }
    }

    fun findParentTextInputLayout(view: View): TextInputLayout? {
        var parent = view.parent
        while (parent != null && parent !is TextInputLayout) {
            parent = parent.parent
        }
        return parent
    }
}