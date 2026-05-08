package io.legado.app.ui.widget.text

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.lib.theme.ThemeStore

class MultilineTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isFallbackLineSpacing = false
        }
        setTextColor(ThemeStore.textColorOther(context))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        calculateLines(heightSize)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun calculateLines(measuredHeight: Int) {
        val lHeight = lineHeight
        val lines = measuredHeight / lHeight
        setLines(lines)
    }
}