package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R

/**
 * 状态视图，用于显示空状态、错误状态等
 */
class StatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var titleView: AppCompatTextView? = null
    private var subTitleView: AppCompatTextView? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_status, this, true)
        initView()
    }

    private fun initView() {
        titleView = findViewById(R.id.tv_title)
        subTitleView = findViewById(R.id.tv_sub_title)
    }

    /**
     * 设置标题
     */
    fun setTitle(title: CharSequence) {
        titleView?.text = title
    }

    /**
     * 设置标题
     */
    fun setTitle(titleId: Int) {
        titleView?.setText(titleId)
    }

    /**
     * 设置副标题
     */
    fun setSubTitle(subTitle: CharSequence) {
        subTitleView?.text = subTitle
    }

    /**
     * 设置副标题
     */
    fun setSubTitle(subTitleId: Int) {
        subTitleView?.setText(subTitleId)
    }

    /**
     * 设置标题颜色
     */
    fun setTitleColor(color: Int) {
        titleView?.setTextColor(color)
    }

    /**
     * 设置副标题颜色
     */
    fun setSubTitleColor(color: Int) {
        subTitleView?.setTextColor(color)
    }
}