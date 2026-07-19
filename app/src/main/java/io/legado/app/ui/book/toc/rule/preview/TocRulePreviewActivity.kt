package io.legado.app.ui.book.toc.rule.preview

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.book.toc.rule.preview.TocRulePreviewRouteScreen
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.windowSize

class TocRulePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookUrl = intent.getStringExtra("bookUrl") ?: ""

        setContent {
            val viewModel: TocRulePreviewViewModel = viewModel()
            TocRulePreviewRouteScreen(
                bookUrl = bookUrl,
                viewModel = viewModel,
                onBack = { finish() },
                onApplyRule = { tocRegex ->
                    setResult(RESULT_OK, Intent().putExtra("tocRegex", tocRegex))
                    finish()
                },
                onOpenManagePage = {
                    startActivity(Intent(this, TxtTocRuleActivity::class.java))
                },
            )
        }

        setupSystemBar()
        upBackgroundImage()
    }

    private fun upBackgroundImage() {
        try {
            val drawable = ThemeConfig.getBgImage(this, windowManager.windowSize)
            if (drawable != null) {
                window.decorView.background = drawable
            } else {
                window.decorView.setBackgroundColor(backgroundColor)
            }
        } catch (_: OutOfMemoryError) {
            window.decorView.setBackgroundColor(backgroundColor)
        } catch (e: Exception) {
            window.decorView.setBackgroundColor(backgroundColor)
        }
    }

    private fun setupSystemBar() {
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        window.statusBarColor = statusBarColor
        setLightStatusBar(ColorUtils.isColorLight(statusBarColor))
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
