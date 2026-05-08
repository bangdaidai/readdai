package io.legado.app.ui.book.read

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import io.legado.app.R
import io.legado.app.help.ai.AiService
import io.legado.app.help.ai.AiSkillEntity
import io.legado.app.help.ai.ChatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AI分析结果弹窗
 * 用于展示AI分析结果（摘要、解释等），不进入完整对话页面
 */
class AiAnalysisDialog : DialogFragment() {

    private lateinit var tvTitle: TextView
    private lateinit var tvSkillName: TextView
    private lateinit var tvContent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnClose: ImageButton
    private lateinit var btnCopy: TextView
    private lateinit var btnShare: io.legado.app.ui.widget.text.AccentTextView
    private lateinit var btnChat: io.legado.app.ui.widget.text.AccentTextView

    private var aiService: AiService? = null
    private var skillId: String = ""
    private var skillName: String = ""
    private var bookTitle: String = ""
    private var author: String = ""
    private var chapterTitle: String = ""
    private var chapterContent: String = ""
    private var selectedText: String = ""
    private var question: String = ""

    private var collectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_ai_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        executeSkill()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tv_title)
        tvSkillName = view.findViewById(R.id.tv_skill_name)
        tvContent = view.findViewById(R.id.tv_content)
        progressBar = view.findViewById(R.id.progress_bar)
        btnClose = view.findViewById(R.id.btn_close)
        btnCopy = view.findViewById(R.id.btn_copy)
        btnShare = view.findViewById(R.id.btn_share)
        btnChat = view.findViewById(R.id.btn_chat)

        tvTitle.text = "AI分析结果"
        tvSkillName.text = skillName
        progressBar.visibility = View.VISIBLE

        btnClose.setOnClickListener { dismiss() }

        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI分析结果", tvContent.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, tvContent.text)
            }
            startActivity(Intent.createChooser(shareIntent, "分享到"))
        }

        btnChat.setOnClickListener {
            dismiss()
            openChatActivity()
        }
    }

    private fun executeSkill() {
        aiService = AiService(requireContext())

        CoroutineScope(Dispatchers.Main).launch {
            aiService?.init()

            // 等待init完成后获取技能
            val allSkills = aiService?.getAllSkills()
            val skill = allSkills?.find { it.id == skillId }

            if (skill == null) {
                tvContent.text = "未找到技能: $skillId\n\n可用的技能: ${allSkills?.map { it.id }?.joinToString() ?: "无"}"
                progressBar.visibility = View.GONE
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "AI技能",
                    "未找到技能: $skillId"
                )
                return@launch
            }

            // 优先从 ReadingContextService 获取实时上下文
            val readingContext = io.legado.app.help.ai.ReadingContextService.getContext()
            
            // 如果 ReadingContextService 有实时数据，使用它；否则回退到静态参数
            val realBookTitle = readingContext?.bookTitle?.takeIf { it.isNotBlank() } ?: bookTitle
            val realAuthor = readingContext?.author?.takeIf { it.isNotBlank() } ?: author
            val realChapterTitle = readingContext?.currentChapter?.title?.takeIf { it.isNotBlank() } ?: chapterTitle
            val realChapterContent = readingContext?.surroundingText?.takeIf { it.isNotBlank() } ?: chapterContent
            
            // 获取书籍简介（从数据库）
            val bookIntro = try {
                val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() }
                if (!bookUrl.isNullOrBlank()) {
                    io.legado.app.data.appDb.bookDao.getBook(bookUrl)?.intro ?: ""
                } else ""
            } catch (e: Exception) {
                ""
            }
            
            // 获取前情内容（真正的前文，不是当前章节）
            val previousContent = try {
                val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() }
                if (!bookUrl.isNullOrBlank()) {
                    val currentChapterIndex = readingContext.currentChapter?.index ?: 0
                    if (currentChapterIndex > 0) {
                        // 获取前一章的内容
                        val prevChapter = io.legado.app.data.appDb.bookChapterDao.getChapter(bookUrl, currentChapterIndex - 1)
                        if (prevChapter != null) {
                            // 从缓存或文件中加载章节内容
                            val book = io.legado.app.data.appDb.bookDao.getBook(bookUrl)
                            if (book != null) {
                                io.legado.app.help.book.BookHelp.getContent(book, prevChapter)?.take(3000) ?: ""
                            } else ""
                        } else ""
                    } else ""
                } else ""
            } catch (e: Exception) {
                io.legado.app.utils.LogUtils.e("AiAnalysisDialog", "获取前情内容失败: ${e.message}")
                ""
            }

            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                "AI技能",
                "执行技能: skillId=$skillId, chapterContent长度=${realChapterContent.length}"
            )

            val variables = mapOf(
                "bookName" to realBookTitle,
                "bookAuthor" to realAuthor,
                "bookIntro" to bookIntro,
                "chapterTitle" to realChapterTitle,
                "chapterContent" to realChapterContent.take(2000),
                "selectedText" to selectedText,
                "question" to question,
                "content" to realChapterContent.take(2000),
                "concept" to selectedText,
                "contextText" to selectedText,
                // 前情回顾需要的变量
                "currentChapter" to realChapterTitle,
                "previousContent" to previousContent
            )

            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "AI技能",
                "技能变量: chapterContent前100字符=${realChapterContent.take(100)}"
            )

            aiService?.executeSkill(skill, variables)?.collectLatest { result ->
                when (result) {
                    is ChatResult.Chunk -> {
                        tvContent.text = result.content
                    }
                    is ChatResult.Success -> {
                        progressBar.visibility = View.GONE
                        tvContent.text = result.content
                    }
                    is ChatResult.Error -> {
                        progressBar.visibility = View.GONE
                        tvContent.text = "错误：${result.message}"
                    }
                    is ChatResult.ReasoningChunk -> {
                    }
                    is ChatResult.ToolStepUpdate -> {
                    }
                    is ChatResult.ToolCall -> {
                    }
                    is ChatResult.ToolResult -> {
                    }
                    is ChatResult.ToolStart -> {
                    }
                }
            }
        }
    }

    private fun openChatActivity() {
        val intent = Intent(requireContext(), AiChatActivity::class.java).apply {
            putExtra("bookUrl", "")
            putExtra("bookTitle", bookTitle)
            putExtra("author", author)
            putExtra("chapterTitle", chapterTitle)
            putExtra("chapterContent", chapterContent)
            putExtra("selectedText", selectedText)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        collectJob?.cancel()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AiAnalysisDialog"

        fun newInstance(
            skillId: String,
            skillName: String,
            bookTitle: String,
            author: String,
            chapterTitle: String,
            chapterContent: String,
            selectedText: String,
            question: String
        ): AiAnalysisDialog {
            return AiAnalysisDialog().apply {
                this.skillId = skillId
                this.skillName = skillName
                this.bookTitle = bookTitle
                this.author = author
                this.chapterTitle = chapterTitle
                this.chapterContent = chapterContent
                this.selectedText = selectedText
                this.question = question
            }
        }
    }
}
