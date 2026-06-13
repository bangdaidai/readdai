package io.legado.app.ui.book.read.config

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import io.legado.app.data.entities.HighlightRule

object HighlightRulePreview {

    fun build(rule: HighlightRule): CharSequence {
        val text = rule.normalizedSampleText()
        val spannable = SpannableStringBuilder(text)
        val regex = kotlin.runCatching { Regex(rule.pattern) }.getOrNull() ?: return spannable
        regex.findAll(text).forEachIndexed { index, match ->
            val start = match.range.first
            val end = match.range.last + 1
            val textColor = rule.textColor ?: 0xFF111111.toInt()
            val accentColor = rule.underlineColor ?: rule.textColor ?: 0xFF63C37D.toInt()
            val underlineWidth = rule.underlineWidth
            val underlineOffset = rule.underlineOffset
            val hasBgImage = !rule.bgImage.isNullOrBlank()

            if (hasBgImage) {
                spannable.setSpan(
                    BgImageSpan(
                        textColor,
                        rule.bgImage!!,
                        rule.bgImageFit,
                        rule.bgImageScale,
                        rule.underlineMode,
                        accentColor,
                        underlineWidth,
                        rule.underlineSvgPath.orEmpty(),
                        underlineOffset
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                when (rule.underlineMode) {
                    4 -> {
                        spannable.setSpan(
                            DoubleUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    5 -> {
                        val svgPath = rule.underlineSvgPath
                        if (!svgPath.isNullOrBlank()) {
                            spannable.setSpan(
                                SvgUnderlineSpan(textColor, accentColor, underlineWidth, svgPath),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } else {
                            spannable.setSpan(
                                ForegroundColorSpan(textColor),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                    else -> {
                        when (rule.underlineMode) {
                            1 -> {
                                spannable.setSpan(
                                    SolidUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            2 -> {
                                spannable.setSpan(
                                    DashUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            3 -> {
                                spannable.setSpan(
                                    WaveUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            else -> {
                                spannable.setSpan(
                                    ForegroundColorSpan(textColor),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    }
                }
                if (index == 0 && rule.bgColor != null && rule.underlineMode != 4 && rule.underlineMode != 5) {
                    spannable.setSpan(
                        BackgroundColorSpan(rule.bgColor!!),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return spannable
    }
}
