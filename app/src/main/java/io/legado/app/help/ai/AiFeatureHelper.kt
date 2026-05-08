package io.legado.app.help.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import io.legado.app.R
import io.legado.app.ui.book.read.AiAnalysisDialog
import io.legado.app.ui.book.read.AiChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AI功能帮助类
 * 用于在阅读器中集成AI功能
 * 参考anx53和ReadAny项目设计
 */
object AiFeatureHelper {

    /**
     * 显示AI功能菜单
     */
    fun showAiMenu(
        context: FragmentActivity,
        anchorView: View,
        book: io.legado.app.data.entities.Book?,
        currentChapterTitle: String?,
        currentChapterContent: String?
    ) {
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.read_ai_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_ai_summary -> {
                    // 章节摘要
                    showAnalysisDialog(
                        context, 
                        "skill_summarize_chapter", 
                        "章节摘要",
                        book, 
                        currentChapterTitle, 
                        currentChapterContent
                    )
                    true
                }
                R.id.menu_ai_book_summary -> {
                    // 全书总结
                    showAnalysisDialog(
                        context, 
                        "skill_summarize_book", 
                        "全书总结",
                        book, 
                        null, 
                        null
                    )
                    true
                }
                R.id.menu_ai_recall -> {
                    // 前情回顾
                    showRecallDialog(context, book, currentChapterTitle, currentChapterContent)
                    true
                }
                R.id.menu_ai_chat -> {
                    // 智能问答
                    openAiChat(context, book, currentChapterTitle, currentChapterContent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * 显示分析结果对话框（使用技能系统）
     */
    private fun showAnalysisDialog(
        context: FragmentActivity,
        skillId: String,
        skillName: String,
        book: io.legado.app.data.entities.Book?,
        chapterTitle: String?,
        chapterContent: String?,
        selectedText: String = ""
    ) {
        if (book == null) return

        // 使用新的弹窗
        val dialog = AiAnalysisDialog.newInstance(
            skillId = skillId,
            skillName = skillName,
            bookTitle = book.name,
            author = book.author ?: "",
            chapterTitle = chapterTitle ?: "",
            chapterContent = chapterContent ?: "",
            selectedText = selectedText,
            question = skillName
        )
        dialog.show(context.supportFragmentManager, AiAnalysisDialog.TAG)
    }

    /**
     * 显示前情回顾对话框
     */
    private fun showRecallDialog(
        context: FragmentActivity, 
        book: io.legado.app.data.entities.Book?,
        chapterTitle: String?,
        chapterContent: String?
    ) {
        if (book == null) return

        // 使用技能系统
        val dialog = AiAnalysisDialog.newInstance(
            skillId = "skill_recall",
            skillName = "前情回顾",
            bookTitle = book.name,
            author = book.author ?: "",
            chapterTitle = chapterTitle ?: "",
            chapterContent = chapterContent ?: "",
            selectedText = "",
            question = "前情回顾"
        )
        dialog.show(context.supportFragmentManager, AiAnalysisDialog.TAG)
    }

    /**
     * 打开AI对话页面
     */
    fun openAiChat(
        context: Context,
        book: io.legado.app.data.entities.Book?,
        currentChapterTitle: String? = null,
        currentChapterContent: String? = null,
        selectedText: String? = null
    ) {
        val intent = Intent(context, AiChatActivity::class.java)
        book?.let {
            intent.putExtra("bookUrl", it.bookUrl)
            intent.putExtra("bookTitle", it.name)
            intent.putExtra("author", it.author)
        }
        currentChapterTitle?.let {
            intent.putExtra("chapterTitle", it)
        }
        currentChapterContent?.let {
            intent.putExtra("chapterContent", it)
        }
        selectedText?.let {
            intent.putExtra("selectedText", it)
        }
        context.startActivity(intent)
    }

    /**
     * 处理长按文本菜单的AI功能
     */
    fun handleTextMenuAiAction(
        context: FragmentActivity,
        menuItemId: Int,
        selectedText: String,
        book: io.legado.app.data.entities.Book?,
        currentChapterTitle: String?,
        currentChapterContent: String? = null
    ) {
        when (menuItemId) {
            R.id.menu_ai_explain -> {
                // 解释选中文本 - 使用技能系统
                showAnalysisDialog(
                    context,
                    "skill_explain_concept",
                    "概念解释",
                    book,
                    currentChapterTitle,
                    currentChapterContent,
                    selectedText
                )
            }
            R.id.menu_ai_analyze -> {
                // 分析选中文本 - 使用技能系统
                showAnalysisDialog(
                    context,
                    "skill_analyze_writing",
                    "写作分析",
                    book,
                    currentChapterTitle,
                    currentChapterContent,
                    selectedText
                )
            }
        }
    }
}
