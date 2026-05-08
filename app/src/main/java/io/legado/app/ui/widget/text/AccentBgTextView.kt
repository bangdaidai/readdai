package io.legado.app.ui.widget.text

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

class AccentBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var radius = 0
    private var customColor: Int? = null

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentBgTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.AccentBgTextView_radius, radius)
        typedArray.recycle()
        upBackground()
    }

    fun setRadius(radius: Int) {
        this.radius = radius.dpToPx()
        upBackground()
    }

    fun setCustomBackgroundColor(color: Int) {
        customColor = color
        upBackground()
    }

    private fun upBackground() {
        val bgColor = customColor ?: if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setDefaultBgColor(bgColor)
            .setPressedBgColor(ColorUtils.darkenColor(bgColor))
            .create()
        setTextColor(
            if (ColorUtils.isColorLight(bgColor)) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        )
    }
}