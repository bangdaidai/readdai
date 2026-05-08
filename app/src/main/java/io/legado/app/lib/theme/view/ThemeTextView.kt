package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.lib.theme.ThemeStore

/**
 * ThemeTextView
 * 自动应用主题颜色的TextView
 */
class ThemeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    init {
        if (!isInEditMode) {
            setTextColor(ThemeStore.textColorPrimary(context))
        }
    }
}