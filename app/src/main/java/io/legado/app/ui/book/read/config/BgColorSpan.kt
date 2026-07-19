package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

class BgColorSpan(
    private val textColor: Int,
    private val bgColor: Int,
    private val underlineMode: Int = 0,
    private val underlineColor: Int = 0,
    private val underlineWidth: Float = 1f,
    private val underlineSvgPath: String = "",
    private val underlineOffset: Float = 6f,
) : ReplacementSpan() {

    private val offsetPx = underlineOffset.toInt().dpToPx()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            val needsOffset = underlineMode in 1..5
            fm.descent = metrics.descent + if (needsOffset) offsetPx else 0
            fm.bottom = metrics.bottom + if (needsOffset) offsetPx else 0
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = paint.measureText(text, start, end)

        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = bgColor
            isAntiAlias = true
        }
        canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), bgPaint)

        paint.color = textColor
        paint.shader = null
        if (underlineMode == 7) {
            val oldSkewX = paint.textSkewX
            paint.textSkewX = -0.25f
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            paint.textSkewX = oldSkewX
        } else {
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
        }

        if (underlineMode != 0 && underlineMode != 7) {
            drawDecoration(canvas, x, x + width, y, paint)
        }
    }

    private fun drawDecoration(canvas: Canvas, startX: Float, endX: Float, y: Int, paint: Paint) {
        val ulPaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        when (underlineMode) {
            1 -> canvas.drawLine(startX, (y + offsetPx).toFloat(), endX, (y + offsetPx).toFloat(), ulPaint)
            2 -> {
                ulPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                canvas.drawLine(startX, (y + offsetPx).toFloat(), endX, (y + offsetPx).toFloat(), ulPaint)
            }
            3 -> {
                val path = android.graphics.Path()
                val waveAmplitude = 3.dpToPx().toFloat()
                val waveLength = 12.dpToPx().toFloat()
                val lineY = (y + offsetPx).toFloat()
                path.moveTo(startX, lineY)
                var currentX = startX
                while (currentX < endX) {
                    val nextX = (currentX + waveLength).coerceAtMost(endX)
                    val midX = (currentX + nextX) / 2
                    path.quadTo(midX, lineY - waveAmplitude, nextX, lineY)
                    currentX = nextX
                    if (currentX < endX) {
                        val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                        val midX2 = (currentX + nextX2) / 2
                        path.quadTo(midX2, lineY + waveAmplitude, nextX2, lineY)
                        currentX = nextX2
                    }
                }
                canvas.drawPath(path, ulPaint)
            }
            4 -> {
                val lineY = y + offsetPx
                val lineGap = 3.dpToPx()
                val line2Y = lineY + lineGap + underlineWidth.dpToPx()
                canvas.drawLine(startX, lineY.toFloat(), endX, lineY.toFloat(), ulPaint)
                canvas.drawLine(startX, line2Y.toFloat(), endX, line2Y.toFloat(), ulPaint)
            }
            6 -> {
                val fm = paint.fontMetrics
                val centerY = y + (fm.ascent + fm.descent) / 2f
                canvas.drawLine(startX, centerY, endX, centerY, ulPaint)
            }
            8 -> {
                val fm = paint.fontMetrics
                val pad = 1.dpToPx().toFloat()
                val boxTop = y + fm.ascent - pad
                val boxBottom = y + fm.descent + pad
                canvas.drawRect(startX, boxTop, endX, boxBottom, ulPaint)
            }
        }
    }
}
