package io.legado.app.ui.book.read

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.read.chat.AiChatScreen
import io.legado.app.ui.book.read.chat.AiChatViewModel
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.windowSize

class AiChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        upBackgroundImage()

        val bookUrl = intent.getStringExtra("bookUrl")
        val bookTitle = intent.getStringExtra("bookTitle")
        val author = intent.getStringExtra("author")
        val chapterTitle = intent.getStringExtra("chapterTitle")
        val chapterContent = intent.getStringExtra("chapterContent")
        val selectedText = intent.getStringExtra("selectedText")

        setContent {
            val viewModel: AiChatViewModel = viewModel()
            AiChatScreen(
                viewModel = viewModel,
                bookUrl = bookUrl,
                bookTitle = bookTitle,
                author = author,
                chapterTitle = chapterTitle,
                chapterContent = chapterContent,
                selectedText = selectedText,
                onFinish = { finish() },
                onOpenSettings = {
                    startActivity(Intent(this@AiChatActivity, ConfigActivity::class.java).apply {
                        putExtra("configTag", ConfigTag.AI_SETTINGS)
                    })
                }
            )
        }
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
