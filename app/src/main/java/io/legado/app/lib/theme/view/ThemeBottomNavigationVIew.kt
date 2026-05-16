package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.LabelVisibilityMode
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.widget.text.BadgeView

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    init {
        val transparentNavBar = context.transparentNavBar
        val bgColor = context.bottomBackground
        if (transparentNavBar) {
            background = ColorDrawable(Color.TRANSPARENT)
        } else {
            background = ColorDrawable(bgColor)
            elevation = context.elevation
        }
        backgroundTintList = null
        setWillNotDraw(false)

        val unselectedColor = ThemeStore.bottomNavIconUnselectedColor(context)
        val selectedColor = ThemeStore.accentColor(context)
        val colorStateList = Selector.colorBuild()
            .setDefaultColor(unselectedColor)
            .setSelectedColor(selectedColor)
            .create()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList
        isItemHorizontalTranslationEnabled = false
        itemBackground = Color.TRANSPARENT.toDrawable()
        labelVisibilityMode = LabelVisibilityMode.LABEL_VISIBILITY_UNLABELED
        itemRippleColor = null

        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    fun createThemeColorStateList(): ColorStateList {
        val unselectedColor = ThemeStore.bottomNavIconUnselectedColor(context)
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
        val menuView = getChildAt(0) as ViewGroup
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
