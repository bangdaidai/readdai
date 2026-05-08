package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import io.legado.app.R
import io.legado.app.lib.theme.accentColor

class RatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var starCount = 5
    private var starSize = 18f  // 将默认星星尺寸从24f减小到18f
    private var currentRating = 0f
    private var isRatingClickable = true
    private var useYellowStars = true
    private val starViews = mutableListOf<ImageView>()
    private var onRatingChangeListener: ((Float) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        // 设置View的clickable属性为true，确保可以接收点击事件
        super.setClickable(true)
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.RatingView)
            starCount = typedArray.getInt(R.styleable.RatingView_starCount, 5)
            starSize = typedArray.getDimension(R.styleable.RatingView_starSize, 18f)  // 将默认星星尺寸从24f减小到18f
            currentRating = typedArray.getFloat(R.styleable.RatingView_rating, 0f)
            isRatingClickable = typedArray.getBoolean(R.styleable.RatingView_clickableView, true)
            useYellowStars = typedArray.getBoolean(R.styleable.RatingView_useYellowStars, true)
            typedArray.recycle()
        }
        
        // 在Design视图中设置一个默认评分，使星星可见
        if (isInEditMode) {
            currentRating = 3.5f
        }
        
        initStars()
    }

    private fun initStars() {
        removeAllViews()
        starViews.clear()
        
        // 先创建所有的星星视图并添加到starViews中
        for (i in 0 until starCount) {
            val star = ImageView(context).apply {
                layoutParams = LayoutParams(
                    starSize.toInt(),
                    starSize.toInt()
                ).also {
                    if (i < starCount - 1) {
                        it.marginEnd = 3  // 减小星星之间的间距，从4减小到3
                    }
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            addView(star)
            starViews.add(star)
        }
        
        // 然后更新每个星星的图像
        updateStars()
        
        // 最后设置触摸监听器
        if (isRatingClickable) {
            for (i in 0 until starCount) {
                val star = starViews[i]
                star.setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN, 
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // 获取点击位置相对于星星的X坐标
                            val x = event.x
                            val starWidth = star.width.toFloat()
                            
                            // 确保星星宽度不为0
                            if (starWidth > 0) {
                                // 去除半星功能，点击星星直接给整星评分
                                val newRating = i + 1f
                                
                                // 如果点击的是当前评级的最后一个星星，则取消评分
                                val finalRating = if (newRating == currentRating) {
                                    0f
                                } else {
                                    newRating
                                }
                                
                                setRating(finalRating)
                            }
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            star.performClick()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
    }

    private fun updateStarImage(index: Int) {
        // 无论是否选中，都使用实心星星
        val starImage = R.drawable.ic_star_filled
        val drawable = ContextCompat.getDrawable(context, starImage)
        if (index < currentRating) {
            // 选中状态：主题强调色实色
            drawable?.setTint(context.accentColor)
        } else {
            // 未选中状态：主题强调色20%透明度
            val accentColor = context.accentColor
            val alpha = (255 * 0.2).toInt() // 20%透明度，让未选中状态更明显
            val transparentAccentColor = (alpha shl 24) or (accentColor and 0xFFFFFF)
            drawable?.setTint(transparentAccentColor)
        }
        starViews[index].setImageDrawable(drawable)
    }

    fun setRating(rating: Float) {
        currentRating = rating.coerceIn(0f, starCount.toFloat())
        updateStars()
        onRatingChangeListener?.invoke(currentRating)
    }

    fun getRating(): Float = currentRating

    private fun updateStars() {
        for (i in 0 until starCount) {
            updateStarImage(i)
        }
    }

    fun setOnRatingChangeListener(listener: (Float) -> Unit) {
        onRatingChangeListener = listener
    }

    fun setStarCount(count: Int) {
        starCount = count
        initStars()
    }

    fun setStarSize(size: Float) {
        starSize = size
        initStars()
    }

    fun setClickableView(clickable: Boolean) {
        isRatingClickable = clickable
        initStars()
    }

    fun setUseYellowStars(useYellow: Boolean) {
        // 不再使用黄色星星，而是使用主题的强调色
        updateStars()
    }
}