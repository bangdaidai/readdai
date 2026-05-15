package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.ColorUtils
import io.legado.app.lib.theme.elevation
import androidx.core.graphics.drawable.toDrawable

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    private var transparentBackground = false

    init {
        applyTheme()
        isItemHorizontalTranslationEnabled = false
        itemBackground = Color.TRANSPARENT.toDrawable()

        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    fun setTransparentBackground(transparent: Boolean) {
        transparentBackground = transparent
        if (transparent) {
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            applyTheme()
        }
    }

    fun applyTheme() {
        if (transparentBackground) {
            setBackgroundColor(Color.TRANSPARENT)
            return
        }
        if (AppConfig.immNavigationBar) {
            // 沉浸式模式使用主题背景色，达到沉浸式效果
            setBackgroundColor(context.backgroundColor)
            elevation = 0f
        } else {
            // 非沉浸式模式使用bottomBackground
            val bgColor = context.bottomBackground
            if (context.transparentNavBar) {
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                setBackgroundColor(bgColor)
                elevation = context.elevation
            }
        }
        // Unselected: use title bar text icon color
        // Selected: use accent color
        val unselectedColor = ThemeStore.titleBarTextIconColor(context)
        val selectedColor = ThemeStore.accentColor(context)
        val colorStateList = Selector.colorBuild()
            .setDefaultColor(unselectedColor)
            .setSelectedColor(selectedColor)
            .create()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
    }

    fun createThemeColorStateList(): ColorStateList {
        // Unselected: use title bar text icon color
        // Selected: use accent color
        val unselectedColor = ThemeStore.titleBarTextIconColor(context)
        val selectedColor = ThemeStore.accentColor(context)
        return Selector.colorBuild()
            .setDefaultColor(unselectedColor)
            .setSelectedColor(selectedColor)
            .create()
    }

    fun restoreThemeIconTint() {
        val colorStateList = createThemeColorStateList()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
    }

    fun addBadgeView(index: Int): BadgeView {
        //获取底部菜单view
        val menuView = getChildAt(0) as ViewGroup
        //获取第index个itemView
        val itemView = menuView.getChildAt(index) as ViewGroup
        if (itemView.layoutParams is FrameLayout.LayoutParams) {
            (itemView.layoutParams as FrameLayout.LayoutParams).apply {
                marginStart = 2
                marginEnd = 2
            }
        }
        val badgeBinding = ViewNavigationBadgeBinding.inflate(LayoutInflater.from(context))
        itemView.addView(badgeBinding.root)
        return badgeBinding.viewBadge
    }

}