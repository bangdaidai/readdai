package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.ThemeStore

/**
 * @author Legado
 */
class OtherTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    init {
        updateTheme()
        LiveEventBus.get<String>(EventBus.THEME_CHANGED).observeForever {
            updateTheme()
        }
    }

    private fun updateTheme() {
        setTextColor(ThemeStore.textColorOther(context))
    }
}
