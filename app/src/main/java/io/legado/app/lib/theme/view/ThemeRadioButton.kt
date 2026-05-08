package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.R

class ThemeRadioButton(context: Context, attrs: AttributeSet) :
    AppCompatRadioButton(context, attrs) {

    private var isUserAction = false

    init {
        if (!isInEditMode) {
            updateTheme()
        }
    }

    private fun updateTheme() {
        val accentColor = ThemeStore.accentColor(context)
        val isDark = io.legado.app.help.config.ThemeConfig.isDarkTheme()
        
        val unCheckedColor = if (isDark) {
            ContextCompat.getColor(context, R.color.ate_control_normal_dark)
        } else {
            ContextCompat.getColor(context, R.color.ate_control_normal_light)
        }
        
        val disabledColor = ContextCompat.getColor(
            context,
            if (isDark) R.color.ate_control_disabled_dark else R.color.ate_control_disabled_light
        )
        
        buttonTintList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            ),
            intArrayOf(disabledColor, unCheckedColor, accentColor)
        )
        
        buttonTintMode = android.graphics.PorterDuff.Mode.SRC_IN
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
