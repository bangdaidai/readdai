package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.lib.theme.textMenuColor

/**
 * 菜单文字 TextView
 */
@Suppress("unused")
class MenuTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    init {
        setTextColor(context.textMenuColor)
    }
}