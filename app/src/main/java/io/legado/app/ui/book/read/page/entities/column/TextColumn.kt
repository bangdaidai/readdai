package io.legado.app.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.annotation.Keep
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.HighlightStyleSpan

/**
 * 文字列
 */
@Keep
data class TextColumn(
    override var start: Float,
    override var end: Float,
    override val charData: String,
    val highlightColor: Int? = null,
    val highlightStyle: HighlightStyleSpan? = null,
) : TextBaseColumn {

    override var textLine: TextLine = emptyTextLine

    /** TextBaseColumn 实现 */
    override val textColor: Int? get() = highlightColor

    override val underlineMode: Int get() = highlightStyle?.underlineMode ?: 0

    override val underlineColor: Int? get() = highlightStyle?.underlineColor

    override val underlineWidth: Float get() = highlightStyle?.underlineWidth ?: 1f

    override val underlineOffset: Float get() = highlightStyle?.underlineOffset ?: 2f

    override val underlineSvgPath: String get() = highlightStyle?.underlineSvgPath ?: ""

    override val bgImage: String get() = highlightStyle?.bgImage ?: ""

    override val bgImageFit: Int get() = highlightStyle?.bgImageFit ?: 0

    override val bgImageScale: Float get() = highlightStyle?.bgImageScale ?: 1f

    override var selected: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }
    override var isSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
                if (value) {
                    textLine.searchResultColumnCount++
                } else {
                    textLine.searchResultColumnCount--
                }
            }
            field = value
        }
    override var isCurrentSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (textLine.isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = when {
            textLine.isReadAloud || isSearchResult -> ReadBookConfig.textAccentColor
            highlightColor != null -> highlightColor
            else -> ReadBookConfig.textColor
        }
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }

        val originalStrokeWidth = textPaint.strokeWidth
        val originalStrokeCap = textPaint.strokeCap

        highlightStyle?.let { style ->
            when (style.underlineMode) {
                1 -> drawSolidUnderline(canvas, textPaint, style)
                2 -> drawDashedUnderline(canvas, textPaint, style)
                3 -> drawWavyUnderline(canvas, textPaint, style)
                4 -> drawDoubleUnderline(canvas, textPaint, style)
            }
        }

        textPaint.strokeWidth = originalStrokeWidth
        textPaint.strokeCap = originalStrokeCap

        val y = textLine.lineBase - textLine.lineTop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }

        if (selected && !isSearchResult) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

    private fun drawSolidUnderline(canvas: Canvas, paint: Paint, style: HighlightStyleSpan) {
        paint.strokeWidth = style.underlineWidth
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = style.underlineColor
        val y = (textLine.lineBase - textLine.lineTop + style.underlineOffset).coerceAtMost(textLine.height)
        canvas.drawLine(start, y, end, y, paint)
    }

    private fun drawDashedUnderline(canvas: Canvas, paint: Paint, style: HighlightStyleSpan) {
        paint.strokeWidth = style.underlineWidth
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = style.underlineColor
        val y = (textLine.lineBase - textLine.lineTop + style.underlineOffset).coerceAtMost(textLine.height)
        val dashLength = 4f * style.underlineWidth
        var x = start
        while (x < end) {
            val nextX = (x + dashLength).coerceAtMost(end)
            canvas.drawLine(x, y, nextX, y, paint)
            x += dashLength * 2
        }
    }

    private fun drawWavyUnderline(canvas: Canvas, paint: Paint, style: HighlightStyleSpan) {
        paint.strokeWidth = style.underlineWidth
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = style.underlineColor
        val y = (textLine.lineBase - textLine.lineTop + style.underlineOffset).coerceAtMost(textLine.height)
        val amplitude = style.underlineWidth * 1.5f
        val halfWidth = amplitude * 2
        var x = start
        var goingUp = true
        while (x < end) {
            val nextX = (x + halfWidth).coerceAtMost(end)
            val endY = if (goingUp) y - amplitude else y + amplitude
            canvas.drawLine(x, y, nextX, endY, paint)
            x = nextX
            goingUp = !goingUp
        }
    }

    private fun drawDoubleUnderline(canvas: Canvas, paint: Paint, style: HighlightStyleSpan) {
        paint.strokeWidth = style.underlineWidth
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = style.underlineColor
        val y1 = (textLine.lineBase - textLine.lineTop + style.underlineOffset - 1f).coerceAtMost(textLine.height)
        val y2 = (textLine.lineBase - textLine.lineTop + style.underlineOffset + 2f).coerceAtMost(textLine.height)
        canvas.drawLine(start, y1, end, y1, paint)
        canvas.drawLine(start, y2, end, y2, paint)
    }

}
