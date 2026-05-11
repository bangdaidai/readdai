package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

/**
 * 支持最大高度的 ScrollView
 * 解决原生 ScrollView maxHeight 不生效的问题
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var maxHeight = Int.MAX_VALUE

    init {
        isNestedScrollingEnabled = true
        context.theme.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.maxHeight),
            0, 0
        ).apply {
            val height = getDimensionPixelSize(0, Int.MAX_VALUE)
            if (height != Int.MAX_VALUE) {
                maxHeight = height
            }
            recycle()
        }
    }

    fun setMaxHeightPixels(height: Int) {
        maxHeight = height
        requestLayout()
    }

    fun setMaxHeightDp(dp: Int) {
        val pixels = (dp * resources.displayMetrics.density).toInt()
        setMaxHeightPixels(pixels)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxHeight < Int.MAX_VALUE) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)

            var maxWidth = Int.MAX_VALUE
            if (mode == MeasureSpec.EXACTLY || mode == MeasureSpec.AT_MOST) {
                maxWidth = widthSize
            }

            val childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

            val child = getChildAt(0)
            if (child != null && child.visibility != View.GONE) {
                child.measure(childWidthSpec, childHeightSpec)
                val childHeight = child.measuredHeight
                val measuredHeight = minOf(childHeight, maxHeight)

                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)

                val finalHeight = when {
                    heightMode == MeasureSpec.EXACTLY -> minOf(heightSize, measuredHeight)
                    heightMode == MeasureSpec.AT_MOST -> minOf(minOf(heightSize, measuredHeight), maxHeight)
                    else -> measuredHeight
                }

                setMeasuredDimension(widthSize, finalHeight)
                return
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            requestLayout()
        }
    }
}