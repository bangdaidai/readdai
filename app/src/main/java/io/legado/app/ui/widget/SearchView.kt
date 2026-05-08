package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.app.SearchableInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import io.legado.app.R
import io.legado.app.utils.printOnDebug


class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SearchView(context, attrs) {
    private var mSearchHintIcon: Drawable? = null
    private var textView: TextView? = null
    private var tintColor: Int? = null
    private var isTextWatcherAdded = false

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onLayout(changed, left, top, right, bottom)
        try {
            if (textView == null) {
                textView = findViewById(androidx.appcompat.R.id.search_src_text)
                mSearchHintIcon = this.context.getDrawable(R.drawable.ic_search_hint)
                // 添加 TextWatcher 监听文本变化，确保清除按钮显示时也能应用颜色
                if (!isTextWatcherAdded && textView != null) {
                    textView?.tag?.let { tag ->
                        if (tag != "tint_added") {
                            textView?.setTag("tint_added")
                            textView?.addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: Editable?) {
                                    // 文本改变后，清除按钮可能会显示，重新应用颜色
                                    tintColor?.let {
                                        applyTint(it)
                                    }
                                }
                            })
                            isTextWatcherAdded = true
                        }
                    } ?: run {
                        textView?.setTag("tint_added")
                        textView?.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) {
                                // 文本改变后，清除按钮可能会显示，重新应用颜色
                                tintColor?.let {
                                    applyTint(it)
                                }
                            }
                        })
                        isTextWatcherAdded = true
                    }
                }
                // 应用之前保存的颜色
                tintColor?.let {
                    applyTint(it)
                }
            }
            // 改变字体
            textView!!.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            textView!!.gravity = Gravity.CENTER_VERTICAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                textView!!.isLocalePreferredLineHeightForMinimumUsed = false
            }
            updateQueryHint()
            // 再次应用颜色，确保所有按钮（包括清除按钮）都被正确设置
            tintColor?.let {
                applyTint(it)
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    private fun getDecoratedHint(hintText: CharSequence): CharSequence {
        // If the field is always expanded or we don't have a search hint icon,
        // then don't add the search icon to the hint.
        if (mSearchHintIcon == null) {
            return hintText
        }
        val textSize = textView!!.textSize.toInt()
        mSearchHintIcon!!.setBounds(0, 0, textSize, textSize)
        val ssb = SpannableStringBuilder("   ")
        ssb.setSpan(CenteredImageSpan(mSearchHintIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(hintText)
        return ssb
    }

    private fun updateQueryHint() {
        textView?.let {
            it.hint = getDecoratedHint(queryHint ?: "")
        }
    }

    override fun setIconifiedByDefault(iconified: Boolean) {
        super.setIconifiedByDefault(iconified)
        updateQueryHint()
    }

    override fun setSearchableInfo(searchable: SearchableInfo?) {
        super.setSearchableInfo(searchable)
        searchable?.let {
            updateQueryHint()
        }
    }

    override fun setQueryHint(hint: CharSequence?) {
        super.setQueryHint(hint)
        updateQueryHint()
    }

    internal class CenteredImageSpan(drawable: Drawable?) : ImageSpan(drawable!!) {
        override fun draw(
            canvas: Canvas, text: CharSequence,
            start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            // image to draw
            val b = drawable
            // font metrics of text to be replaced
            val fm = paint.fontMetricsInt
            val transY = ((y + fm.descent + y + fm.ascent) / 2
                    - b.bounds.bottom / 2)
            canvas.save()
            canvas.translate(x, transY.toFloat())
            b.draw(canvas)
            canvas.restore()
        }
    }
    
    /**
     * 设置搜索框的颜色
     */
    fun applyTint(color: Int) {
        // 保存颜色值
        tintColor = color
        
        // 尝试初始化textView
        if (textView == null) {
            try {
                textView = findViewById(androidx.appcompat.R.id.search_src_text)
                mSearchHintIcon = this.context.getDrawable(R.drawable.ic_search_hint)
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        
        // 设置搜索图标颜色
        findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_button)?.apply {
            setColorFilter(color)
        }
        // 设置清除按钮颜色和大小
        findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_close_btn)?.apply {
            setColorFilter(color)
        }
        // 设置确认按钮颜色
        findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_go_btn)?.apply {
            setColorFilter(color)
        }
        // 设置提示图标颜色
        mSearchHintIcon?.setTint(color)
        // 设置搜索框内文字的颜色
        textView?.setTextColor(color)
        // 设置搜索框提示文字的颜色
        textView?.setHintTextColor(android.graphics.Color.argb(128, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)))
        
        // 更新提示文字
        updateQueryHint()
    }
}
