package io.legado.app.ui.book.read.page.provider

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

/**
 * 用于在阅读排版阶段传递局部下划线样式
 */
class HighlightStyleSpan(
    val underlineMode: Int,
    val underlineColor: Int,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    val bgPaddingStart: Float = 0f,
    val bgPaddingEnd: Float = 0f,
    val bgPaddingTop: Float = 0f,
    val bgPaddingBottom: Float = 0f,
    val nineLeftX: Float = 1f / 3f,
    val nineRightX: Float = 2f / 3f,
    val nineTopY: Float = 1f / 3f,
    val nineBottomY: Float = 2f / 3f,
    val nineStretchMode: Int = 0,
) : CharacterStyle(), UpdateAppearance {

    override fun updateDrawState(tp: TextPaint) = Unit

}
