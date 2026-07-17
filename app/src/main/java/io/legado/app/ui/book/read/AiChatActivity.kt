package io.legado.app.ui.book.read

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.ui.book.read.chat.AiChatScreen
import io.legado.app.ui.book.read.chat.AiChatViewModel
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag

class AiChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    ConfigActivity.startConfig(this@AiChatActivity, ConfigTag.AI_CONFIG)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
