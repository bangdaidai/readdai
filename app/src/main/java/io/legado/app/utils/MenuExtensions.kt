@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.core.view.forEach
import io.legado.app.R
import io.legado.app.constant.Theme
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryTextColor
import java.lang.reflect.Method

@SuppressLint("RestrictedApi")
@Suppress("UsePropertyAccessSyntax")
fun Menu.applyTint(context: Context, theme: Theme = Theme.Auto): Menu = this.let { menu ->
    if (menu is MenuBuilder) {
        menu.setOptionalIconsVisible(true)
    }
    val defaultTextColor = io.legado.app.lib.theme.ThemeStore.textColorOther(context)
    val tintColor = MenuExtensions.getMenuColor(context, theme)
    menu.forEach { item ->
        (item as MenuItemImpl).let { impl ->
            //overflow：展开的item
            val textColor = if (impl.requiresOverflow()) defaultTextColor else tintColor
            impl.icon?.setTintMutate(textColor)
            //设置文字颜色
            item.title?.let {
                val spannableString = android.text.SpannableString(it)
                spannableString.setSpan(
                    android.text.style.ForegroundColorSpan(textColor),
                    0,
                    spannableString.length,
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = spannableString
            }
        }
    }
    return menu
}

@SuppressLint("RestrictedApi")
fun Menu.applyOpenTint(context: Context, showIcon: Boolean = true) {
    //展开菜单显示图标
    if (this.javaClass.simpleName.equals("MenuBuilder", ignoreCase = true)) {
        val tintColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(context)
        kotlin.runCatching {
            var method: Method =
                this.javaClass.getDeclaredMethod("setOptionalIconsVisible", java.lang.Boolean.TYPE)
            method.isAccessible = true
            method.invoke(this, showIcon)
            if (showIcon) {
                method = this.javaClass.getDeclaredMethod("getNonActionItems")
                val menuItems = method.invoke(this)
                if (menuItems is ArrayList<*>) {
                    for (menuItem in menuItems) {
                        if (menuItem is MenuItem) {
                            menuItem.icon?.setTintMutate(tintColor)
                            // 设置文字颜色
                            menuItem.title?.let {
                                val spannableString = android.text.SpannableString(it)
                                spannableString.setSpan(
                                    android.text.style.ForegroundColorSpan(tintColor),
                                    0,
                                    spannableString.length,
                                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
                                )
                                menuItem.title = spannableString
                            }
                        }
                    }
                }
            }
        }
    } else if (this.javaClass.simpleName.equals("SubMenuBuilder", ignoreCase = true)) {
        val tintColor = io.legado.app.lib.theme.ThemeStore.titleBarTextIconColor(context)
        (this as? SubMenuBuilder)?.forEach { item: MenuItem ->
            item.icon?.setTintMutate(tintColor)
            // 设置文字颜色
            item.title?.let {
                val spannableString = android.text.SpannableString(it)
                spannableString.setSpan(
                    android.text.style.ForegroundColorSpan(tintColor),
                    0,
                    spannableString.length,
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = spannableString
            }
        }
    }
}

fun Menu.iconItemOnLongClick(id: Int, function: (view: View) -> Unit) {
    findItem(id)?.let { item ->
        item.setActionView(R.layout.view_action_button)
        item.actionView?.run {
            contentDescription = item.title
            findViewById<ImageButton>(R.id.item).setImageDrawable(item.icon)
            setOnLongClickListener {
                function.invoke(this)
                true
            }
            setOnClickListener {
                performIdentifierAction(id, 0)
            }
        }
    }
}

@SuppressLint("RestrictedApi")
inline fun Menu.transaction(block: (Menu) -> Unit) {
    val menuBuilder = this as? MenuBuilder
    menuBuilder?.stopDispatchingItemsChanged()
    try {
        block(this)
    } finally {
        menuBuilder?.startDispatchingItemsChanged()
    }
}

object MenuExtensions {

    fun getMenuColor(
        context: Context,
        theme: Theme = Theme.Auto,
        requiresOverflow: Boolean = false
    ): Int {
        return ThemeStore.titleBarTextIconColor(context)
    }

}