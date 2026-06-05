package io.legado.app.ui.book.read.config

import android.app.Activity
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.help.config.ReadBookConfig

object RegexColorConfigDialog {
    const val REGEX_RULE_COLOR = 7900
    var pendingColorPosition = -1

    fun show(activity: FragmentActivity) {
        val dialog = BottomSheetDialog(activity)
        val composeView = ComposeView(activity).apply {
            setContent {
                RegexColorConfigDialogCompose(
                    onDismiss = { dialog.dismiss() }
                )
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }

    fun handleColorResult(activity: Activity, color: Int) {
        val position = pendingColorPosition
        if (position in ReadBookConfig.regexColorRules.indices) {
            ReadBookConfig.regexColorRules[position].color = color
            ReadBookConfig.saveRegexColorRules()
            io.legado.app.ui.book.read.page.provider.TextChapterLayout.invalidateRegexCache()
            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }
}
