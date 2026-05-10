package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
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
        // 从 XML 属性中读取 maxHeight（如果设置了）
        context.theme.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.maxHeight),
            0, 0
        ).apply {
            val height = getDimensionPixelSize(0, Int.MAX_VALUE)
            if (height != Int.MAX_VALUE) {
                maxHeight = height
                android.util.Log.d("MaxHeightScrollView", "设置最大高度: ${height}px = ${height / resources.displayMetrics.density}dp")
            }
            recycle()
        }
    }

    /**
     * 设置最大高度（像素）
     */
    fun setMaxHeightPixels(height: Int) {
        maxHeight = height
        requestLayout()
    }

    /**
     * 设置最大高度（dp）
     */
    fun setMaxHeightDp(dp: Int) {
        val pixels = (dp * resources.displayMetrics.density).toInt()
        setMaxHeightPixels(pixels)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 关键：限制测量高度不超过 maxHeight
        val modifiedHeightSpec = if (maxHeight < Int.MAX_VALUE) {
            val originalSize = MeasureSpec.getSize(heightMeasureSpec)
            val limitedSize = minOf(originalSize, maxHeight)
            android.util.Log.d("MaxHeightScrollView", "onMeasure: 原始=$originalSize px, 限制后=$limitedSize px, maxHeight=$maxHeight px")
            
            MeasureSpec.makeMeasureSpec(
                limitedSize,
                MeasureSpec.getMode(heightMeasureSpec)
            )
        } else {
            heightMeasureSpec
        }
        
        super.onMeasure(widthMeasureSpec, modifiedHeightSpec)
    }
}
