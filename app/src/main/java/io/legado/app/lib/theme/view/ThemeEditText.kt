package io.legado.app.lib.theme.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.TintHelper

class ThemeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    init {
        if (!isInEditMode) {
            updateTheme()
            LiveEventBus.get<String>(EventBus.THEME_CHANGED).observeForever {
                updateTheme()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            isLocalePreferredLineHeightForMinimumUsed = false
        }
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        val paddingRight = paddingRight
        val paddingBottom = paddingBottom
        setPadding(0, paddingTop, 0, paddingBottom)
    }

    private fun updateTheme() {
        val accentColor = context.accentColor

        // 只设置光标颜色，不设置背景色
        TintHelper.setCursorTint(this, accentColor)
        // 设置透明背景
        setBackgroundColor(0)
        // 设置文字颜色为主题自定义的其他文字颜色
        setTextColor(ThemeStore.textColorOther(context))
    }
}