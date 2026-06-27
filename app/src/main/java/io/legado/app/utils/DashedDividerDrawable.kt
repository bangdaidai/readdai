package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class DashedDividerDrawable(
    private val color: Int,
    private val strokeWidth: Float,
    private val dashLength: Float,
    private val gapLength: Float
) : Drawable() {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = this@DashedDividerDrawable.strokeWidth
        pathEffect = DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
        this.color = this@DashedDividerDrawable.color
    }

    override fun draw(canvas: Canvas) {
        val y = bounds.height() / 2f
        canvas.drawLine(0f, y, bounds.width().toFloat(), y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
