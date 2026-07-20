package io.legado.app.ui.book.read.config

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.ReplacementSpan
import io.legado.app.ui.book.read.page.entities.NinePatchHelper
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.utils.dpToPx

class BgImageSpan(
    private val textColor: Int,
    private val bgImagePath: String,
    private val bgImageFit: Int = 0,
    private val underlineMode: Int = 0,
    private val underlineColor: Int = 0,
    private val underlineWidth: Float = 1f,
    private val underlineSvgPath: String = "",
    private val underlineOffset: Float = 6f,
    private val bgPaddingStart: Float = 0f,
    private val bgPaddingEnd: Float = 0f,
    private val bgPaddingTop: Float = 0f,
    private val bgPaddingBottom: Float = 0f,
    private val nineLeftX: Float = 0.5f,
    private val nineRightX: Float = 0.5f,
    private val nineTopY: Float = 0.5f,
    private val nineBottomY: Float = 0.5f,
    private val nineStretchMode: Int = 0,
) : ReplacementSpan() {

    private val offsetPx = underlineOffset.toInt().dpToPx()
    private val padStartPx = bgPaddingStart.dpToPx()
    private val padEndPx = bgPaddingEnd.dpToPx()
    private val padTopPx = bgPaddingTop.dpToPx()
    private val padBottomPx = bgPaddingBottom.dpToPx()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = (metrics.top - padTopPx).toInt()
            fm.ascent = (metrics.ascent - padTopPx).toInt()
            val needsOffset = underlineMode in 1..5
            val underlinePad = if (needsOffset) offsetPx else 0
            fm.descent = (metrics.descent + underlinePad + padBottomPx).toInt()
            fm.bottom = (metrics.bottom + underlinePad + padBottomPx).toInt()
        }
        return (paint.measureText(text, start, end) + padStartPx + padEndPx).toInt()
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
        val drawLeft = x - padStartPx
        val drawRight = x + width + padEndPx
        val drawTop = top - padTopPx
        val drawBottom = bottom + padBottomPx
        val rectWidth = drawRight - drawLeft
        val rectHeight = (drawBottom - drawTop).toFloat()
        val scale = 1f

        val bitmap = TextLine.getBgBitmap(bgImagePath)
        if (bitmap != null) {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                isFilterBitmap = true
            }
            when (bgImageFit) {
                1 -> {
                    val sw = rectWidth * scale
                    val sh = rectHeight * scale
                    val dx = drawLeft + (rectWidth - sw) / 2f
                    val dy = drawTop + (rectHeight - sh) / 2f
                    canvas.save()
                    canvas.clipRect(drawLeft, drawTop, drawRight, drawBottom)
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + sw, dy + sh), bgPaint)
                    canvas.restore()
                }
                2 -> {
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val fitScale = (rectWidth / bw).coerceAtLeast(rectHeight / bh) * scale
                    val scaledW = bw * fitScale
                    val scaledH = bh * fitScale
                    val dx = drawLeft + (rectWidth - scaledW) / 2f
                    val dy = drawTop + (rectHeight - scaledH) / 2f
                    canvas.save()
                    canvas.clipRect(drawLeft, drawTop, drawRight, drawBottom)
                    canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + scaledW, dy + scaledH), bgPaint)
                    canvas.restore()
                }
                3 -> {
                    NinePatchHelper.draw(canvas, bitmap, drawLeft, drawTop, drawRight, drawBottom, bgPaint, nineLeftX, nineRightX, nineTopY, nineBottomY, nineStretchMode)
                }
                else -> {
                    // 旧数据「平铺(0)」等统一回退为「拉伸填充」，铺满文字框
                    canvas.drawBitmap(bitmap, null, RectF(drawLeft, drawTop, drawRight, drawBottom), bgPaint)
                }
            }
        }

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
