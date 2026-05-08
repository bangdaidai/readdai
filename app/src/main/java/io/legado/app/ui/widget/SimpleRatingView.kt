package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor

/**
 * 简单的评分视图，使用Unicode字符显示星星
 * 在Design视图中也能正确显示
 */
class SimpleRatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var starCount = 5
    private var currentRating = 0f
    private var isRatingClickable = true
    private var onRatingChangeListener: ((Float) -> Unit)? = null

    init {
        // 设置基本属性
        textSize = 8f  // 调整字体大小，从14f减小到12f
        setTypeface(null, Typeface.BOLD)
        
        // 从XML属性获取值
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.RatingView)
            starCount = typedArray.getInt(R.styleable.RatingView_starCount, 5)
            currentRating = typedArray.getFloat(R.styleable.RatingView_rating, 0f)
            isRatingClickable = typedArray.getBoolean(R.styleable.RatingView_clickableView, true)
            typedArray.recycle()
        }
        
        // 设置颜色为主题的强调色
        setTextColor(context.accentColor)
        
        // 在Design视图中设置一个默认评分，使星星可见
        if (isInEditMode) {
            currentRating = 3.5f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
        }
        
        // 设置点击事件
        if (isRatingClickable) {
            setOnClickListener {
                // 简单的点击循环：0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 0
                currentRating = ((currentRating + 1) % (starCount + 1)).toFloat()
                updateStars()
                onRatingChangeListener?.invoke(currentRating)
            }
        }
        
        // 更新星星显示
        updateStars()
    }

    private fun updateStars() {
        val fullStars = currentRating.toInt()
        val emptyStars = starCount - fullStars
        
        val ratingText = buildString {
            // 添加实心星星
            repeat(fullStars) {
                append("★")
            }
            // 添加空心星星
            repeat(emptyStars) {
                append("☆")
            }
        }
        
        text = ratingText
    }

    fun setRating(rating: Float) {
        currentRating = rating.coerceIn(0f, starCount.toFloat())
        updateStars()
        onRatingChangeListener?.invoke(currentRating)
    }

    fun getRating(): Float = currentRating

    fun setOnRatingChangeListener(listener: (Float) -> Unit) {
        onRatingChangeListener = listener
    }
}