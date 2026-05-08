package io.legado.app.ui.widget.text

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.visible


@Suppress("unused")
class AutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatAutoCompleteTextView(context, attrs) {

    var delCallBack: ((value: String) -> Unit)? = null

    init {
        applyTint(context.accentColor)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            isLocalePreferredLineHeightForMinimumUsed = false
        }
        // 监听文本变化，当用户输入时隐藏提示文字
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // 当有文本输入时，尝试找到父级TextInputLayout并隐藏hint
                var parent = parent
                while (parent != null && parent !is io.legado.app.ui.widget.text.TextInputLayout) {
                    parent = parent.parent
                }
                (parent as? io.legado.app.ui.widget.text.TextInputLayout)?.let {
                    it.isHintEnabled = s.isNullOrEmpty()
                }
            }
        })
    }

    override fun enoughToFilter(): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // 当用户点击编辑框时自动显示下拉列表
        event?.let {
            if (it.action == MotionEvent.ACTION_DOWN) {
                showDropDown()
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun showDropDown() {
        // 增加下拉列表与编辑框之间的距离，避免挤在一起
        try {
            val popup = javaClass.getDeclaredField("mPopup")
            popup.isAccessible = true
            val popupWindow = popup.get(this) as android.widget.ListPopupWindow
            // 设置下拉列表与编辑框之间的垂直偏移
            popupWindow.setVerticalOffset(16) // 16dp的距离，增加间距
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.showDropDown()
    }

    fun setFilterValues(values: List<String>?) {
        values?.let {
            setAdapter(MyAdapter(context, values))
        }
    }

    fun setFilterValues(vararg value: String) {
        setAdapter(MyAdapter(context, value.toMutableList()))
    }

    inner class MyAdapter(context: Context, values: List<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, values) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_1line_text_and_del, parent, false)
            val textView = view.findViewById<TextView>(R.id.text_view)
            textView.text = getItem(position)
            val ivDelete = view.findViewById<ImageView>(R.id.iv_delete)
            if (delCallBack != null) ivDelete.visible() else ivDelete.gone()
            ivDelete.setOnClickListener {
                getItem(position)?.let {
                    remove(it)
                    delCallBack?.invoke(it)
                    showDropDown()
                }
            }
            return view
        }
    }

}
