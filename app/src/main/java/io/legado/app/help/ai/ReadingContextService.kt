package io.legado.app.help.ai

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读上下文数据类
 * 参照ReadAny的ReadingContext设计
 */
data class ReadingContext(
    val bookId: String = "",
    val bookTitle: String = "",
    val author: String = "",
    val currentChapter: ChapterInfo? = null,
    val currentPosition: PositionInfo? = null,
    val selection: SelectionInfo? = null,
    val surroundingText: String = "",
    val operationType: OperationType = OperationType.READING,
    val timestamp: Long = System.currentTimeMillis()
) {
    data class ChapterInfo(
        val index: Int = 0,
        val title: String = "",
        val url: String = ""
    )

    data class PositionInfo(
        val pageIndex: Int = 0,
        val chapterPosition: Int = 0,
        val percentage: Float = 0f,
        val wordCount: Int = 0
    )

    data class SelectionInfo(
        val text: String = "",
        val startIndex: Int = 0,
        val endIndex: Int = 0,
        val chapterIndex: Int = 0,
        val chapterTitle: String = ""
    )

    enum class OperationType {
        READING,      // 普通阅读
        SELECTING,    // 选中文本
        SEARCHING,    // 搜索中
        NAVIGATING,   // 跳转章节
        ANNOTATING    // 添加笔记
    }
}

/**
 * 阅读上下文更新请求
 */
data class ReadingContextUpdate(
    val bookId: String? = null,
    val bookTitle: String? = null,
    val author: String? = null,
    val currentChapter: ReadingContext.ChapterInfo? = null,
    val currentPosition: ReadingContext.PositionInfo? = null,
    val selection: ReadingContext.SelectionInfo? = null,
    val surroundingText: String? = null,
    val operationType: ReadingContext.OperationType? = null
)

/**
 * 阅读上下文服务
 * 实时跟踪用户的阅读状态，为AI工具提供最新的上下文信息
 * 参照ReadAny的ReadingContextService设计
 */
object ReadingContextService {

    private const val TAG = "ReadingContextService"

    // 当前阅读上下文
    private var _context: ReadingContext? = null
    
    // StateFlow用于响应式订阅
    private val _contextFlow = MutableStateFlow<ReadingContext?>(null)
    val contextFlow: StateFlow<ReadingContext?> = _contextFlow.asStateFlow()

    /**
     * 获取当前阅读上下文
     */
    fun getContext(): ReadingContext? = _context

    /**
     * 更新阅读上下文（部分更新）
     */
    fun updateContext(update: ReadingContextUpdate) {
        val now = System.currentTimeMillis()
        
        // 详细日志：显示每次调用的完整信息（用于诊断）
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "ReadingContext", 
            "updateContext被调用: book=${update.bookTitle}, chapter=${update.currentChapter?.title}, operation=${update.operationType}, hasBookId=${update.bookId != null}")
        
        // 检查是否有实际变化（避免重复日志）
        val hasChanged = _context == null ||
            update.bookId != null ||
            update.currentChapter?.title != _context?.currentChapter?.title ||
            update.operationType != _context?.operationType
        
        if (hasChanged) {
            AiLogManager.log(AiLogManager.LogLevel.INFO, "ReadingContext", "上下文已变化: book=${update.bookTitle}, chapter=${update.currentChapter?.title}, operation=${update.operationType}")
        }
        
        _context = if (_context == null || update.bookId != null) {
            // 新书籍或首次初始化
            ReadingContext(
                bookId = update.bookId ?: "",
                bookTitle = update.bookTitle ?: "",
                author = update.author ?: "",
                currentChapter = update.currentChapter,
                currentPosition = update.currentPosition,
                surroundingText = update.surroundingText ?: "",
                operationType = update.operationType ?: ReadingContext.OperationType.READING,
                timestamp = now
            )
        } else {
            // 更新现有上下文
            _context!!.copy(
                bookTitle = update.bookTitle ?: _context!!.bookTitle,
                author = update.author ?: _context!!.author,
                currentChapter = update.currentChapter ?: _context!!.currentChapter,
                currentPosition = update.currentPosition ?: _context!!.currentPosition,
                surroundingText = update.surroundingText ?: _context!!.surroundingText,
                selection = update.selection, // selection可以为null表示清除选择
                operationType = update.operationType ?: _context!!.operationType,
                timestamp = now
            )
        }

        // 通知观察者
        _contextFlow.value = _context
        
        Log.d(TAG, "Context updated: book=${_context?.bookTitle}, " +
                "chapter=${_context?.currentChapter?.title}, " +
                "operation=${_context?.operationType}")
    }

    /**
     * 更新选中文本
     */
    fun updateSelection(selection: ReadingContext.SelectionInfo?) {
        if (_context == null) return

        _context = _context!!.copy(
            selection = selection,
            operationType = if (selection != null) {
                ReadingContext.OperationType.SELECTING
            } else {
                ReadingContext.OperationType.READING
            },
            timestamp = System.currentTimeMillis()
        )

        _contextFlow.value = _context
        
        Log.d(TAG, "Selection updated: ${selection?.text?.take(20)}...")
    }

    /**
     * 清除选中状态
     */
    fun clearSelection() {
        updateSelection(null)
    }

    /**
     * 更新阅读位置
     */
    fun updatePosition(position: ReadingContext.PositionInfo) {
        if (_context == null) return

        _context = _context!!.copy(
            currentPosition = position,
            timestamp = System.currentTimeMillis()
        )

        _contextFlow.value = _context
    }

    /**
     * 更新当前章节
     */
    fun updateChapter(chapter: ReadingContext.ChapterInfo) {
        if (_context == null) return

        _context = _context!!.copy(
            currentChapter = chapter,
            timestamp = System.currentTimeMillis()
        )

        _contextFlow.value = _context
    }

    /**
     * 设置操作类型
     */
    fun setOperationType(type: ReadingContext.OperationType) {
        if (_context == null) return

        _context = _context!!.copy(
            operationType = type,
            timestamp = System.currentTimeMillis()
        )

        _contextFlow.value = _context
    }

    /**
     * 清除所有上下文
     */
    fun clearContext() {
        _context = null
        _contextFlow.value = null
        Log.d(TAG, "Context cleared")
    }

    /**
     * 检查是否有有效的阅读上下文
     */
    fun hasValidContext(): Boolean {
        return _context != null && !_context!!.bookId.isBlank()
    }

    /**
     * 获取格式化的上下文字符串（用于调试或日志）
     */
    fun getFormattedContext(): String {
        val ctx = _context ?: return "No context available"
        
        return buildString {
            append("📖 阅读上下文\n")
            append("书籍: ${ctx.bookTitle}\n")
            append("作者: ${ctx.author}\n")
            ctx.currentChapter?.let {
                append("章节: ${it.title} (索引: ${it.index})\n")
            }
            ctx.currentPosition?.let {
                append("进度: ${it.percentage.toInt()}% (页码: ${it.pageIndex})\n")
            }
            ctx.selection?.let {
                append("选中: ${it.text.take(30)}...\n")
            }
            append("操作: ${ctx.operationType}\n")
            append("时间: ${ctx.timestamp}")
        }
    }
}
