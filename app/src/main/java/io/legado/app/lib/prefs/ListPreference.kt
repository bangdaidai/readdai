package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R

class ListPreference(context: Context, attrs: AttributeSet) :
    ListPreference(context, attrs) {

    private val isBottomBackground: Boolean
    private var onLongClick: ((preference: ListPreference) -> Boolean)? = null

    init {
        layoutResource = R.layout.view_preference
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Preference)
        isBottomBackground = typedArray.getBoolean(R.styleable.Preference_isBottomBackground, false)
        typedArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        Preference.bindView<View>(
            context, holder, icon, title, summary,
            isBottomBackground = isBottomBackground
        )
        super.onBindViewHolder(holder)
        onLongClick?.let { listener ->
            holder.itemView.setOnLongClickListener {
                listener.invoke(this)
            }
        }
    }

    fun onLongClick(listener: (preference: ListPreference) -> Boolean) {
        onLongClick = listener
    }
}
