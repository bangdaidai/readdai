package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.EditTextPreference.OnBindEditTextListener
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint

class EditTextPreference(context: Context, attrs: AttributeSet) :
    androidx.preference.EditTextPreference(context, attrs) {

    private var mOnBindEditTextListener: OnBindEditTextListener? = null
    private val onBindEditTextListener = OnBindEditTextListener { editText ->
        editText.applyTint(context.accentColor)
        // 设置文字颜色为主题自定义的其他文字颜色
        editText.setTextColor(io.legado.app.lib.theme.ThemeStore.textColorOther(context))
        mOnBindEditTextListener?.onBindEditText(editText)
    }

    init {
        // isPersistent = true
        layoutResource = R.layout.view_preference
        super.setOnBindEditTextListener(onBindEditTextListener)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        Preference.bindView<TextView>(context, holder, icon, title, summary, null, null)
        super.onBindViewHolder(holder)
    }

    override fun setOnBindEditTextListener(onBindEditTextListener: OnBindEditTextListener?) {
        mOnBindEditTextListener = onBindEditTextListener
    }

}