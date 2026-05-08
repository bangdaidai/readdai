package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.ContextCompat
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.ThemeStore

class ThemeCheckBox(context: Context, attrs: AttributeSet) : AppCompatCheckBox(context, attrs) {

    private var isUserAction = false

    init {
        if (!isInEditMode) {
            updateTheme()
            LiveEventBus.get<String>(EventBus.THEME_CHANGED).observeForever {
                updateTheme()
            }
        }
    }

    private fun updateTheme() {
        // 使用主题自定义的其他文字颜色
        val textColor = ThemeStore.textColorOther(context)
        // 获取次要文字颜色，用于未选中状态
        val secondaryTextColor = io.legado.app.help.config.ThemeConfig.getTextColorSecondary(context)
        // 获取强调色，用于选中状态
        val accentColor = io.legado.app.lib.theme.ThemeStore.accentColor(context)
        // 创建颜色状态列表，控制复选框的各种状态颜色
        val colorStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            ),
            intArrayOf(
                // 禁用状态
                ContextCompat.getColor(context, android.R.color.darker_gray),
                // 未选中状态，使用次要文字颜色
                secondaryTextColor,
                // 选中状态，使用强调色
                accentColor
            )
        )
        // 直接设置按钮的颜色状态列表
        buttonTintList = colorStateList
        // 设置文字颜色为其他文字颜色
        setTextColor(textColor)
    }

    override fun performClick(): Boolean {
        isUserAction = true
        val result = super.performClick()
        isUserAction = false
        return result
    }

    fun setOnUserCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        if (listener == null) {
            return super.setOnCheckedChangeListener(null)
        }
        super.setOnCheckedChangeListener { _, isChecked ->
            if (isUserAction) {
                listener.invoke(isChecked)
            }
        }
    }

}