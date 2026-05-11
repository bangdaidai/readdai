package io.legado.app.help.ai

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ChunkEntity
import io.legado.app.data.entities.VectorizedBookEntity
import io.legado.app.data.entities.ReadStatistics
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.data.entities.BookReadTimeRank
import io.legado.app.data.entities.readRecord.ReadRecordSummary
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.ChunkDao
import io.legado.app.data.dao.VectorizedBookDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.AppDatabase
import io.legado.app.help.ai.rag.VectorSearchService
import io.legado.app.help.ai.rag.VectorConfigManager
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * AI工具接口
 * 参照anx53的RepositoryTool设计
 */
interface AiTool {
    val id: String
    val name: String
    val description: String
    val inputSchema: Map<String, Any>
    val timeout: Long?

    suspend fun execute(input: Map<String, Any>): ToolResult
}

/**
 * AI工具抽象基类
 * 提供统一的错误处理、超时控制、日志记录
 * 参考 anx53 的 RepositoryTool 设计
 */
abstract class BaseTool(
    override val id: String,
    override val name: String,
    override val description: String,
    override val inputSchema: Map<String, Any> = emptyMap(),
    override val timeout: Long? = null
) : AiTool {

    /**
     * 子类实现具体的业务逻辑
     */
    protected abstract suspend fun run(input: Map<String, Any>): ToolResult

    /**
     * 执行工具，包含统一的错误处理和超时控制
     */
    final override suspend fun execute(input: Map<String, Any>): ToolResult {
        return try {
            // 记录工具调用开始
            AiLogManager.log(
                AiLogManager.LogLevel.DEBUG,
                "AiTool",
                "执行工具: $id"
            )

            // 执行工具，支持超时控制
            val currentTimeout = timeout
            val result = if (currentTimeout != null && currentTimeout > 0) {
                withTimeoutOrNull(currentTimeout.milliseconds) {
                    run(input)
                } ?: ToolResult(
                    status = "error",
                    name = id,
                    message = "工具执行超时 (${timeout}ms)"
                )
            } else {
                run(input)
            }

            // 记录工具执行结果
            when (result.status) {
                "ok" -> AiLogManager.log(
                    AiLogManager.LogLevel.DEBUG,
                    "AiTool",
                    "工具 $id 执行成功"
                )
                "error" -> {
                    AiLogManager.log(
                        AiLogManager.LogLevel.ERROR,
                        "AiTool",
                        "工具 $id 执行失败: ${result.message}"
                    )
                    // 超时错误不记录堆栈
                    if (!(result.message?.contains("超时") ?: false)) {
                        AiLogManager.log(
                            AiLogManager.LogLevel.ERROR,
                            "AiTool",
                            "工具 $id 错误详情: ${result.data}"
                        )
                    }
                }
            }

            result
        } catch (e: TimeoutCancellationException) {
            // 超时异常
            AiLogManager.log(
                AiLogManager.LogLevel.ERROR,
                "AiTool",
                "工具 $id 执行超时"
            )
            ToolResult(
                status = "error",
                name = id,
                message = "工具执行超时 (${timeout?.toString() ?: "未知"}ms)"
            )
        } catch (e: Exception) {
            // 其他异常
            AiLogManager.log(
                AiLogManager.LogLevel.ERROR,
                "AiTool",
                "工具 $id 执行异常: ${e.message}",
                e
            )
            ToolResult(
                status = "error",
                name = id,
                message = "工具执行失败: ${e.message}"
            )
        }
    }

    /**
     * 辅助方法：格式化时间戳为相对时间
     */
    protected fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "刚刚"
            diff < 3600000 -> "${diff / 60000}分钟前"
            diff < 86400000 -> "${diff / 3600000}小时前"
            diff < 604800000 -> "${diff / 86400000}天前"
            else -> "${diff / 604800000}周前"
        }
    }

    /**
     * 辅助方法：格式化时长（毫秒）为可读字符串
     */
    protected fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}小时${minutes % 60}分钟"
            minutes > 0 -> "${minutes}分钟${seconds % 60}秒"
            else -> "${seconds}秒"
        }
    }
}

/**
 * AI工具解析结果
 * 用于LangChain4j集成
 */
data class AiResolvedTool(
    val name: String,
    val definition: org.json.JSONObject,
    val execute: suspend (org.json.JSONObject?) -> String
)

/**
 * AI工具注册表
 * 参照anx53的AiToolRegistry设计
 */
object AiToolRegistry {
    private val definitions = mutableListOf<AiToolDefinition>()
    private val tools = mutableMapOf<String, () -> AiTool>()

    fun register(definition: AiToolDefinition, toolFactory: () -> AiTool) {
        definitions.add(definition)
        tools[definition.id] = toolFactory
    }

    fun getDefinitions(): List<AiToolDefinition> = definitions.toList()

    fun getDefinition(id: String): AiToolDefinition? = definitions.find { it.id == id }

    fun buildTools(enabledIds: Set<String>): List<AiTool> {
        return definitions
            .filter { enabledIds.contains(it.id) }
            .mapNotNull { tools[it.id]?.invoke() }
    }

    fun defaultEnabledIds(): List<String> = definitions.map { it.id }
}

/**
 * 工具上下文
 * 包含工具执行所需的数据
 */
data class AiToolContext(
    val currentBook: Book?,
    val currentChapter: BookChapter?,
    val chapterContent: String?,
    val bookUrl: String,
    val appDatabase: AppDatabase,
    val appContext: android.content.Context
)

/**
 * 当前书籍信息工具 - 完整实现
 */
class CurrentBookInfoTool(
    private val context: AiToolContext
) : BaseTool(
    id = "current_book_info",
    name = "获取当前书籍信息",
    description = "获取用户当前正在阅读的书籍的基本信息、作者、简介和阅读进度。当用户询问当前在看什么书、这本书怎么样时使用。",
    inputSchema = emptyMap(),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val book = readingContext?.let { ctx ->
            if (ctx.bookId.isNotBlank()) {
                context.appDatabase.bookDao.getBook(ctx.bookId)
            } else null
        } ?: context.currentBook
        
        book ?: return ToolResult(
            status = "error",
            name = id,
            message = "当前没有正在阅读的书籍"
        )

        val progress = if (book.totalChapterNum > 0) {
            (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
        } else 0

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "bookTitle" to book.name,
                "author" to (book.author ?: "未知"),
                "intro" to (book.intro ?: "无"),
                "kind" to (book.kind ?: "未知"),
                "wordCount" to (book.wordCount ?: "未知"),
                "totalChapters" to book.totalChapterNum,
                "currentChapterIndex" to book.durChapterIndex,
                "currentChapterTitle" to (book.durChapterTitle ?: "未知"),
                "readingProgress" to progress,
                "readingPercentage" to "$progress%",
                "lastReadTime" to book.lastCheckTime
            )
        )
    }
}

/**
 * 当前章节内容工具 - 完整实现
 */
class CurrentChapterTool(
    private val context: AiToolContext
) : BaseTool(
    id = "current_chapter",
    name = "获取当前章节内容",
    description = "获取用户当前正在阅读的章节的标题和内容文本。当用户询问当前章节讲了什么、需要分析当前内容时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "maxLength" to mapOf(
                "type" to "integer",
                "description" to "最大返回字符数，默认8000"
            ),
            "includeTitle" to mapOf(
                "type" to "boolean",
                "description" to "是否包含章节标题，默认true"
            )
        )
    ),
    timeout = 5000  // 5秒超时（可能需要加载章节内容）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 尝试从多个来源获取章节信息
        val chapter = readingContext?.currentChapter?.let { ctxChapter ->
            // 从 ReadingContextService 获取章节索引和标题
            context.currentBook?.let { book ->
                context.appDatabase.bookChapterDao.getChapter(book.bookUrl, ctxChapter.index)
            }
        } ?: context.currentChapter
        
        chapter ?: return ToolResult(
            status = "error",
            name = id,
            message = "无法获取当前章节"
        )

        val maxLength = (input["maxLength"] as? Number)?.toInt() ?: 8000
        val includeTitle = input["includeTitle"] as? Boolean ?: true

        // 优先使用 ReadingContextService 的 surroundingText，否则使用静态 content
        var content = readingContext?.surroundingText
        if (content.isNullOrBlank()) {
            content = context.chapterContent ?: ""
        }

        val truncatedContent = if (content.length > maxLength) {
            content.take(maxLength) + "\n\n[内容已截断，超出最大长度限制]"
        } else {
            content
        }

        val resultContent = if (includeTitle) {
            "【${chapter.title}】\n\n$truncatedContent"
        } else {
            truncatedContent
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "title" to chapter.title,
                "index" to chapter.index,
                "url" to chapter.url,
                "wordCount" to (chapter.wordCount ?: "未知"),
                "content" to resultContent,
                "contentLength" to truncatedContent.length,
                "isTruncated" to (content.length > maxLength)
            )
        )
    }
}

/**
 * 书籍目录工具 - 完整实现
 */
class BookTocTool(
    private val context: AiToolContext
) : BaseTool(
    id = "book_toc",
    name = "获取书籍目录",
    description = "获取用户当前阅读书籍的完整目录结构，包括章节标题和当前阅读位置。当用户询问这本书有多少章、看到哪里了、还有哪些章节没看时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "maxItems" to mapOf(
                "type" to "integer",
                "description" to "最多返回章节数，默认100"
            )
        )
    ),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl
        val maxItems = (input["maxItems"] as? Number)?.toInt() ?: 100

        if (bookUrl.isBlank()) {
            return ToolResult(
                status = "error",
                name = id,
                message = "无法获取书籍URL"
            )
        }

        val chapterDao = context.appDatabase.bookChapterDao
        val chapters = chapterDao.getChapterList(bookUrl).take(maxItems)
        
        // 获取当前阅读章节索引
        val currentIndex = readingContext?.currentChapter?.index ?: context.currentBook?.durChapterIndex ?: 0

        val tocList = chapters.map { chapter ->
            mapOf(
                "index" to chapter.index,
                "title" to chapter.title,
                "url" to chapter.url,
                "isCurrentChapter" to (chapter.index == currentIndex),
                "progress" to if (chapter.index < currentIndex) {
                    "已阅读"
                } else if (chapter.index == currentIndex) {
                    "阅读中"
                } else {
                    "未阅读"
                }
            )
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "totalChapters" to chapters.size,
                "currentChapter" to currentIndex,
                "bookTitle" to (readingContext?.bookTitle ?: context.currentBook?.name ?: "未知"),
                "toc" to tocList
            )
        )
    }
}

/**
 * 搜索书籍内容工具 - 完整实现
 */
class SearchContentTool(
    private val context: AiToolContext
) : BaseTool(
    id = "search_content",
    name = "搜索书籍内容",
    description = "在当前书籍中搜索指定内容，支持关键词搜索。当用户询问书中是否有提到某个词、查找特定内容、回顾某段情节时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "keyword" to mapOf(
                "type" to "string",
                "description" to "搜索关键词"
            ),
            "maxResults" to mapOf(
                "type" to "integer",
                "description" to "最大返回结果数，默认10"
            )
        )
    ),
    timeout = 30000  // 30秒超时（搜索可能需要遍历多个章节）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val keyword = input["keyword"]?.toString()
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "缺少搜索关键词"
            )

        val maxResults = (input["maxResults"] as? Number)?.toInt() ?: 10

        if (keyword.length < 2) {
            return ToolResult(
                status = "error",
                name = id,
                message = "搜索关键词至少需要2个字符"
            )
        }

        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl

        if (bookUrl.isBlank()) {
            return ToolResult(
                status = "error",
                name = id,
                message = "无法获取书籍URL"
            )
        }

        val chapterDao = context.appDatabase.bookChapterDao
        val chapters = chapterDao.getChapterList(bookUrl)

        val results = mutableListOf<Map<String, Any>>()
        val keywordLower = keyword.lowercase()

        for (chapter in chapters) {
            if (results.size >= maxResults) break

            // 优先使用 ReadingContextService 的 surroundingText，否则使用静态 content
            var chapterContent = readingContext?.surroundingText
            if (chapterContent.isNullOrBlank()) {
                chapterContent = context.chapterContent
            }
            
            if (chapterContent != null && chapter.index == (readingContext?.currentChapter?.index ?: context.currentChapter?.index ?: -1)) {
                val lines = chapterContent.split("\n")
                for ((lineIndex, line) in lines.withIndex()) {
                    if (line.lowercase().contains(keywordLower)) {
                        results.add(mapOf(
                            "chapterIndex" to chapter.index,
                            "chapterTitle" to chapter.title,
                            "lineIndex" to lineIndex,
                            "content" to line.trim(),
                            "context" to getContext(lines, lineIndex, 2)
                        ))
                        if (results.size >= maxResults) break
                    }
                }
                if (results.size >= maxResults) break
            }
        }

        if (results.isEmpty()) {
            return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "keyword" to keyword,
                    "totalResults" to 0,
                    "results" to emptyList<Map<String, Any>>(),
                    "message" to "未找到包含 '$keyword' 的内容"
                )
            )
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "keyword" to keyword,
                "totalResults" to results.size,
                "results" to results
            )
        )
    }

    private fun getContext(lines: List<String>, index: Int, range: Int): String {
        val start = maxOf(0, index - range)
        val end = minOf(lines.size, index + range + 1)
        return lines.subList(start, end).joinToString("\n")
    }
}

/**
 * 阅读进度工具 - 完整实现
 */
class ReadingProgressTool(
    private val context: AiToolContext
) : BaseTool(
    id = "reading_progress",
    name = "获取阅读进度",
    description = "获取用户当前阅读进度信息，包括已读章节数和进度百分比。当用户询问看到哪里了、还有多少没看、进度如何时使用。",
    inputSchema = emptyMap(),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val book = readingContext?.let { ctx ->
            if (ctx.bookId.isNotBlank()) {
                context.appDatabase.bookDao.getBook(ctx.bookId)
            } else null
        } ?: context.currentBook
        
        book ?: return ToolResult(
            status = "error",
            name = id,
            message = "当前没有正在阅读的书籍"
        )

        val totalChapters = book.totalChapterNum
        val currentChapter = readingContext?.currentChapter?.index ?: book.durChapterIndex

        if (totalChapters <= 0) {
            return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "totalChapters" to 0,
                    "currentChapter" to currentChapter,
                    "progress" to 0,
                    "progressPercentage" to "0%",
                    "message" to "无法计算进度"
                )
            )
        }

        val progress = ((currentChapter.toFloat() / totalChapters) * 100).toInt()

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "totalChapters" to totalChapters,
                "currentChapter" to currentChapter,
                "currentChapterTitle" to (readingContext?.currentChapter?.title ?: book.durChapterTitle ?: "未知"),
                "progress" to progress,
                "progressPercentage" to "$progress%",
                "chaptersRemaining" to (totalChapters - currentChapter - 1),
                "estimatedChapters" to (totalChapters - currentChapter)
            )
        )
    }
}

/**
 * 笔记标注工具 - 完整实现
 */
class BookNotesTool(
    private val context: AiToolContext
) : BaseTool(
    id = "book_notes",
    name = "获取书籍笔记",
    description = "获取用户在当前书籍中的所有笔记和高亮标注。当用户询问我在这本书中标记了什么、有哪些笔记、查找特定内容的笔记时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "maxItems" to mapOf(
                "type" to "integer",
                "description" to "最大返回笔记数，默认20"
            ),
            "searchKeyword" to mapOf(
                "type" to "string",
                "description" to "搜索笔记关键词"
            )
        )
    ),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl
        val maxItems = (input["maxItems"] as? Number)?.toInt() ?: 20
        val searchKeyword = input["searchKeyword"]?.toString()

        if (bookUrl.isBlank()) {
            return ToolResult(
                status = "error",
                name = id,
                message = "无法获取书籍URL"
            )
        }

        val annotationDao = context.appDatabase.bookAnnotationDao
        
        // 获取书籍名称和作者，优先使用实时上下文
        val bookName = readingContext?.bookTitle ?: context.currentBook?.name ?: ""
        val bookAuthor = readingContext?.author ?: context.currentBook?.author ?: ""
        
        val annotations = annotationDao.getByBook(bookName, bookAuthor)

        var filteredAnnotations = annotations

        if (!searchKeyword.isNullOrBlank()) {
            filteredAnnotations = annotations.filter { annotation ->
                annotation.content.contains(searchKeyword, ignoreCase = true) ||
                (annotation.note?.contains(searchKeyword, ignoreCase = true) == true)
            }
        }

        val notesList = filteredAnnotations.take(maxItems).map { annotation: io.legado.app.data.entities.BookAnnotation ->
            mapOf(
                "id" to annotation.time,
                "chapterIndex" to annotation.chapterIndex,
                "chapterTitle" to annotation.chapterName,
                "content" to annotation.content,
                "note" to (annotation.note ?: ""),
                "createTime" to annotation.time
            )
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "totalNotes" to annotations.size,
                "returnedNotes" to notesList.size,
                "notes" to notesList
            )
        )
    }
}

/**
 * 工具注册初始化 - 完整实现
 */
object AiTools {
    /**
     * 默认启用的工具ID列表
     * 只启用最核心的工具，避免过多工具导致AI无法正确选择
     */
    val DEFAULT_ENABLED_TOOL_IDS = setOf(
        "list_books",           // 列出书籍（回答“最近看了什么书”）
        "reading_history",      // 阅读历史
        "search_web_tavily"     // ✅ Tavily 网络搜索（如果已配置）
    )

    fun registerAll(context: AiToolContext) {
        // 注册当前书籍信息工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "current_book_info",
                displayNameBuilder = { "当前书籍信息" },
                descriptionBuilder = { "获取当前阅读书籍的基本信息" },
                inputSchema = emptyMap()
            )
        ) { CurrentBookInfoTool(context) }

        // 注册当前章节内容工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "current_chapter",
                displayNameBuilder = { "当前章节内容" },
                descriptionBuilder = { "获取当前阅读章节的内容" },
                inputSchema = mapOf(
                    "maxLength" to mapOf("type" to "integer"),
                    "includeTitle" to mapOf("type" to "boolean")
                )
            )
        ) { CurrentChapterTool(context) }

        // 注册书籍目录工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "book_toc",
                displayNameBuilder = { "书籍目录" },
                descriptionBuilder = { "获取书籍完整目录" },
                inputSchema = mapOf(
                    "maxItems" to mapOf("type" to "integer")
                )
            )
        ) { BookTocTool(context) }

        // 注册搜索内容工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "search_content",
                displayNameBuilder = { "搜索内容" },
                descriptionBuilder = { "在书籍中搜索内容" },
                inputSchema = mapOf(
                    "keyword" to mapOf("type" to "string"),
                    "maxResults" to mapOf("type" to "integer")
                )
            )
        ) { SearchContentTool(context) }

        // 注册阅读进度工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "reading_progress",
                displayNameBuilder = { "阅读进度" },
                descriptionBuilder = { "获取当前阅读进度" },
                inputSchema = emptyMap()
            )
        ) { ReadingProgressTool(context) }

        // 注册笔记工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "book_notes",
                displayNameBuilder = { "书籍笔记" },
                descriptionBuilder = { "获取书籍中的笔记和高亮" },
                inputSchema = mapOf(
                    "maxItems" to mapOf("type" to "integer"),
                    "searchKeyword" to mapOf("type" to "string")
                )
            )
        ) { BookNotesTool(context) }

        // 注册阅读历史工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "reading_history",
                displayNameBuilder = { "阅读历史" },
                descriptionBuilder = { "获取用户的阅读历史记录，包括最近阅读的书籍和阅读时间" },
                inputSchema = mapOf(
                    "maxItems" to mapOf("type" to "integer", "description" to "最大返回记录数，默认20"),
                    "bookTitle" to mapOf("type" to "string", "description" to "筛选特定书籍的历史")
                )
            )
        ) { ReadingHistoryTool(context) }

        // 注册提取实体工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "extract_entities",
                displayNameBuilder = { "提取实体" },
                descriptionBuilder = { "从当前阅读内容中提取人物、地点、时间等实体" },
                inputSchema = mapOf(
                    "entityTypes" to mapOf("type" to "array", "items" to "string")
                )
            )
        ) { ExtractEntitiesTool(context) }

        // 注册添加引用工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "add_quote",
                displayNameBuilder = { "添加引用" },
                descriptionBuilder = { "在回答中引用书籍原文" },
                inputSchema = mapOf(
                    "quote" to mapOf("type" to "string"),
                    "chapterTitle" to mapOf("type" to "string")
                )
            )
        ) { AddQuoteTool(context) }

        // 注册列出书籍工具（已合并 bookshelf_lookup 功能）
        AiToolRegistry.register(
            AiToolDefinition(
                id = "list_books",
                displayNameBuilder = { "列出书籍" },
                descriptionBuilder = { "获取用户书架上的所有书籍列表。支持按标题、作者、分类、阅读状态筛选，支持多种排序方式。当用户询问有哪些书、找特定书籍、查看书架、推荐书籍时使用。" },
                inputSchema = mapOf(
                    "maxItems" to mapOf("type" to "integer", "description" to "最大返回书籍数，默认50"),
                    "keyword" to mapOf("type" to "string", "description" to "搜索关键词（匹配标题或作者）"),
                    "category" to mapOf("type" to "string", "description" to "按分类筛选"),
                    "status" to mapOf("type" to "string", "description" to "按阅读状态过滤: unread/reading/completed"),
                    "sortBy" to mapOf("type" to "string", "description" to "排序方式: lastRead/title/author/addTime"),
                    "includeTags" to mapOf("type" to "boolean", "description" to "是否包含标签信息")
                )
            )
        ) { ListBooksTool(context) }

        // 注册搜索所有笔记工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "search_all_notes",
                displayNameBuilder = { "搜索所有笔记" },
                descriptionBuilder = { "在所有书籍中搜索笔记和高亮" },
                inputSchema = mapOf(
                    "keyword" to mapOf("type" to "string"),
                    "maxItems" to mapOf("type" to "integer")
                )
            )
        ) { SearchAllNotesTool(context) }

        // 注册标签相关工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "tags_list",
                displayNameBuilder = { "标签列表" },
                descriptionBuilder = { "获取用户创建的所有标签" },
                inputSchema = emptyMap()
            )
        ) { TagsListTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "book_tags",
                displayNameBuilder = { "书籍标签" },
                descriptionBuilder = { "获取当前书籍的所有标签" },
                inputSchema = emptyMap()
            )
        ) { BookTagsTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "apply_book_tags",
                displayNameBuilder = { "应用书籍标签" },
                descriptionBuilder = { "为书籍添加或移除标签" },
                inputSchema = mapOf(
                    "books" to mapOf("type" to "array"),
                    "createTags" to mapOf("type" to "array")
                )
            )
        ) { ApplyBookTagsTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "manage_tags",
                displayNameBuilder = { "管理标签" },
                descriptionBuilder = { "创建、删除、重命名标签" },
                inputSchema = mapOf(
                    "action" to mapOf("type" to "string"),
                    "tagName" to mapOf("type" to "string")
                )
            )
        ) { ManageTagsTool(context) }


        AiToolRegistry.register(
            AiToolDefinition(
                id = "bookshelf_organize",
                displayNameBuilder = { "书架整理" },
                descriptionBuilder = { "规划书架分组重组方案" },
                inputSchema = mapOf(
                    "groups" to mapOf("type" to "array"),
                    "summary" to mapOf("type" to "string")
                )
            )
        ) { BookshelfOrganizeTool(context) }

        // 注册分析工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "analyze_arguments",
                displayNameBuilder = { "论证分析" },
                descriptionBuilder = { "分析作者的论证逻辑和论据" },
                inputSchema = mapOf(
                    "focusType" to mapOf("type" to "string"),
                    "chapterIndex" to mapOf("type" to "integer")
                )
            )
        ) { AnalyzeArgumentsTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "find_quotes",
                displayNameBuilder = { "查找引用" },
                descriptionBuilder = { "查找书中的精彩引用和金句" },
                inputSchema = mapOf(
                    "quoteType" to mapOf("type" to "string"),
                    "chapterIndex" to mapOf("type" to "integer"),
                    "maxQuotes" to mapOf("type" to "integer")
                )
            )
        ) { FindQuotesTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "compare_sections",
                displayNameBuilder = { "章节比较" },
                descriptionBuilder = { "比较两个章节的内容差异" },
                inputSchema = mapOf(
                    "chapterIndex1" to mapOf("type" to "integer"),
                    "chapterIndex2" to mapOf("type" to "integer"),
                    "compareType" to mapOf("type" to "string")
                )
            )
        ) { CompareSectionsTool(context) }

        // 注册RAG向量化工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "rag_search",
                displayNameBuilder = { "RAG搜索" },
                descriptionBuilder = { "语义搜索书籍内容" },
                inputSchema = mapOf(
                    "query" to mapOf("type" to "string"),
                    "topK" to mapOf("type" to "integer")
                )
            )
        ) { RagSearchTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "rag_toc",
                displayNameBuilder = { "RAG目录" },
                descriptionBuilder = { "获取书籍章节结构" },
                inputSchema = emptyMap()
            )
        ) { RagTocTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "rag_context",
                displayNameBuilder = { "RAG上下文" },
                descriptionBuilder = { "获取章节上下文内容" },
                inputSchema = mapOf(
                    "chapterIndex" to mapOf("type" to "integer"),
                    "range" to mapOf("type" to "integer")
                )
            )
        ) { RagContextTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "vectorization_status",
                displayNameBuilder = { "向量化状态" },
                descriptionBuilder = { "检查书籍向量化状态" },
                inputSchema = emptyMap()
            )
        ) { VectorizationStatusTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "summarize_content",
                displayNameBuilder = { "内容摘要" },
                descriptionBuilder = { "对书籍内容进行摘要" },
                inputSchema = mapOf(
                    "scope" to mapOf("type" to "string"),
                    "chapterIndex" to mapOf("type" to "integer"),
                    "style" to mapOf("type" to "string")
                )
            )
        ) { SummarizeContentTool(context) }

        // 注册阅读统计工具
        AiToolRegistry.register(
            AiToolDefinition(
                id = "reading_stats",
                displayNameBuilder = { "阅读统计" },
                descriptionBuilder = { "获取用户阅读统计数据（阅读时长、书籍数量等）" },
                inputSchema = mapOf(
                    "period" to mapOf("type" to "string"),
                    "limit" to mapOf("type" to "integer")
                )
            )
        ) { ReadingStatsTool(context) }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "book_read_time_rank",
                displayNameBuilder = { "阅读时长排行" },
                descriptionBuilder = { "获取读书时长排行榜" },
                inputSchema = mapOf(
                    "limit" to mapOf("type" to "integer")
                )
            )
        ) { BookReadTimeRankTool(context) }

        // ✅ 注册 Tavily 网络搜索工具
        if (AppConfig.aiTavilyEnabled && !AppConfig.aiTavilyApiKey.isNullOrBlank()) {
            AiToolRegistry.register(
                AiToolDefinition(
                    id = "search_web_tavily",
                    displayNameBuilder = { "Tavily 网络搜索" },
                    descriptionBuilder = { "使用 Tavily 联网搜索实时网页信息，返回答案摘要、来源链接和内容片段。适合新闻、实时事件、最新产品信息和网页检索。" },
                    inputSchema = mapOf(
                        "query" to mapOf("type" to "string", "description" to "要搜索的问题或关键词"),
                        "topic" to mapOf("type" to "string", "description" to "搜索主题: general/news/finance"),
                        "searchDepth" to mapOf("type" to "string", "description" to "搜索深度: basic/advanced/ultra-fast"),
                        "maxResults" to mapOf("type" to "integer", "description" to "最多返回几条结果，默认5")
                    )
                )
            ) { TavilySearchTool() }
        }

        // ✅ 注册 MCP 工具（动态加载）
        val mcpServers: List<io.legado.app.ui.main.ai.AiMcpServerConfig> = AppConfig.aiMcpServers.filter { server -> server.enabled }
        if (mcpServers.isNotEmpty()) {
            kotlinx.coroutines.runBlocking {
                try {
                    val mcpTools = AiMcpClient.resolveTools(mcpServers)
                    mcpTools.forEach { mcpTool ->
                        AiToolRegistry.register(
                            AiToolDefinition(
                                id = mcpTool.name,
                                displayNameBuilder = { mcpTool.name },
                                descriptionBuilder = { 
                                    mcpTool.definition.optJSONObject("function")?.optString("description") ?: "MCP 工具"
                                },
                                inputSchema = convertJsonToMap(
                                    mcpTool.definition.optJSONObject("function")?.optJSONObject("parameters")
                                )
                            )
                        ) { McpToolExecutor(mcpTool) }
                    }
                } catch (e: Exception) {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                        "AiTools",
                        "加载 MCP 工具失败: ${e.message}"
                    )
                }
            }
        }

        // 注意：reading_history 已在前面注册，此处不再重复
    }
    
    /**
     * 将 JSONObject 转换为 Map<String, Any>
     */
    private fun convertJsonToMap(jsonObject: org.json.JSONObject?): Map<String, Any> {
        if (jsonObject == null) return emptyMap()
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            when (value) {
                is org.json.JSONObject -> map[key] = convertJsonToMap(value)
                is org.json.JSONArray -> map[key] = convertJsonArrayToList(value)
                else -> map[key] = value
            }
        }
        return map
    }
    
    private fun convertJsonArrayToList(jsonArray: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            when (value) {
                is org.json.JSONObject -> list.add(convertJsonToMap(value))
                is org.json.JSONArray -> list.add(convertJsonArrayToList(value))
                else -> list.add(value)
            }
        }
        return list
    }
}

/**
 * Tavily 网络搜索工具适配器
 */
class TavilySearchTool : BaseTool(
    id = "search_web_tavily",
    name = "Tavily 网络搜索",
    description = "使用 Tavily 联网搜索实时网页信息，返回答案摘要、来源链接和内容片段。适合新闻、实时事件、最新产品信息和网页检索。",
    inputSchema = mapOf(
        "query" to mapOf("type" to "string", "description" to "要搜索的问题或关键词"),
        "topic" to mapOf("type" to "string", "description" to "搜索主题: general/news/finance"),
        "searchDepth" to mapOf("type" to "string", "description" to "搜索深度: basic/advanced/ultra-fast"),
        "maxResults" to mapOf("type" to "integer", "description" to "最多返回几条结果，默认5")
    ),
    timeout = 15000  // 15秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        return try {
            // 将 Map 转换为 JSONObject
            val args = org.json.JSONObject().apply {
                input.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            // 调用 AiTavilyTool 的 search 方法
            val resultJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // 由于 AiTavilyTool.search 是 private，我们需要通过 resolvedTools 来获取
                val tavilyTools = io.legado.app.help.ai.AiTavilyTool.resolvedTools()
                if (tavilyTools.isEmpty()) {
                    return@withContext org.json.JSONObject().apply {
                        put("ok", false)
                        put("error", "Tavily 未启用或 API Key 未配置")
                    }.toString()
                }
                tavilyTools.first().execute(args)
            }
            
            val result = org.json.JSONObject(resultJson)
            if (result.optBoolean("ok", false)) {
                ToolResult(
                    status = "ok",
                    name = id,
                    data = mapOf(
                        "query" to result.optString("query"),
                        "answer" to result.optString("answer"),
                        "results" to parseResultsArray(result.optJSONArray("results")),
                        "responseTime" to result.opt("response_time")
                    )
                )
            } else {
                ToolResult(
                    status = "error",
                    name = id,
                    message = result.optString("error", "搜索失败")
                )
            }
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "Tavily 搜索异常: ${e.message}"
            )
        }
    }
    
    private fun parseResultsArray(resultsArray: org.json.JSONArray?): List<Map<String, Any>> {
        if (resultsArray == null) return emptyList()
        val results = mutableListOf<Map<String, Any>>()
        for (i in 0 until resultsArray.length()) {
            val item = resultsArray.optJSONObject(i) ?: continue
            results.add(mapOf(
                "title" to item.optString("title"),
                "url" to item.optString("url"),
                "content" to item.optString("content"),
                "score" to item.optDouble("score", 0.0)
            ))
        }
        return results
    }
}

/**
 * MCP 工具执行器
 */
class McpToolExecutor(
    private val mcpTool: io.legado.app.help.ai.AiResolvedTool
) : BaseTool(
    id = mcpTool.name,
    name = mcpTool.name,
    description = mcpTool.definition.optJSONObject("function")?.optString("description") ?: "MCP 工具",
    inputSchema = emptyMap(),  // MCP 工具的 schema 在 definition 中
    timeout = 30000  // 30秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        return try {
            // 将 Map 转换为 JSONObject
            val args = org.json.JSONObject().apply {
                input.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value)
                        is Boolean -> put(key, value)
                        is List<*> -> put(key, org.json.JSONArray(value))
                        is Map<*, *> -> put(key, org.json.JSONObject(value as Map<*, *>))
                        else -> put(key, value.toString())
                    }
                }
            }
            
            // 执行 MCP 工具
            val resultJson = mcpTool.execute(args)
            val result = org.json.JSONObject(resultJson)
            
            ToolResult(
                status = "ok",
                name = id,
                data = mapOf("result" to result.toString())
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "MCP 工具执行失败: ${e.message}"
            )
        }
    }
}

class ReadingHistoryTool(
    private val context: AiToolContext
) : BaseTool(
    id = "reading_history",
    name = "阅读历史",
    description = "获取用户的阅读历史记录，包括最近阅读的书籍、阅读时长和阅读次数。支持按书籍标题和时间范围过滤。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "maxItems" to mapOf(
                "type" to "integer",
                "description" to "最大返回记录数，默认20"
            ),
            "bookTitle" to mapOf(
                "type" to "string",
                "description" to "筛选特定书籍的历史"
            ),
            "fromDate" to mapOf(
                "type" to "string",
                "description" to "开始日期（ISO格式或时间戳），可选"
            ),
            "toDate" to mapOf(
                "type" to "string",
                "description" to "结束日期（ISO格式或时间戳），可选"
            )
        )
    ),
    timeout = 5000  // 5秒超时
) {
    
    private val repository = io.legado.app.help.ai.repository.ReadingHistoryRepository(context.appDatabase)

    override suspend fun run(input: Map<String, Any>): ToolResult {
        val maxItems = (input["maxItems"] as? Number)?.toInt() ?: 20
        val bookTitleFilter = input["bookTitle"]?.toString()
        
        // 解析日期参数（可选）
        val fromDate = parseTimestamp(input["fromDate"])
        val toDate = parseTimestamp(input["toDate"])

        // 使用 Repository 获取数据
        val historyRecords = repository.fetchHistory(
            bookTitleFilter = bookTitleFilter,
            fromDate = fromDate,
            toDate = toDate,
            limit = maxItems
        )

        // 转换为工具结果格式
        val historyList = historyRecords.map { record ->
            mapOf(
                "bookTitle" to record.bookTitle,
                "author" to record.author,
                "lastReadTime" to record.lastReadTime,
                "lastReadTimeStr" to formatTime(record.lastReadTime),
                "totalReadTime" to record.totalReadTime,
                "totalReadTimeStr" to formatDuration(record.totalReadTime),
                "readCount" to record.readCount,
                "lastChapter" to (record.lastChapter ?: "未知"),
                "bookUrl" to record.bookUrl
            )
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "totalRecords" to historyList.size,
                "history" to historyList
            )
        )
    }
    
    /**
     * 解析时间戳参数
     * 支持 ISO 格式字符串或数字时间戳
     */
    private fun parseTimestamp(value: Any?): Long? {
        if (value == null) return null
        
        return when (value) {
            is Number -> value.toLong()
            is String -> {
                try {
                    // 尝试解析为数字
                    value.toLongOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}

/**
 * 提取实体工具 - 参照ReadAny53实现
 * 功能: 返回书籍原始内容，让AI自己提取人物、地点等实体
 */
class ExtractEntitiesTool(
    private val context: AiToolContext
) : BaseTool(
    id = "extract_entities",
    name = "提取实体",
    description = "提取书中的人物、地点、组织等命名实体。返回原始文本，由AI自行分析识别实体。当用户询问书中有哪些人物、地点、组织时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "entityTypes" to mapOf(
                "type" to "array",
                "description" to "要提取的实体类型: characters(人物), places(地点), organizations(组织), concepts(概念), all(全部)"
            ),
            "chapterIndex" to mapOf(
                "type" to "integer",
                "description" to "指定章节索引，不指定则提取整本书"
            )
        )
    ),
    timeout = 30000
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val entityTypes = (input["entityTypes"] as? List<*>)?.filterIsInstance<String>()
            ?: listOf("all")
        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()

        val content = context.chapterContent
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "无法获取当前章节内容"
            )

        val maxTokens = 3000
        val truncatedContent = if (content.length > maxTokens * 4) {
            content.take(maxTokens * 4) + "\n\n[内容已截断]"
        } else {
            content
        }

        val typeDesc = entityTypes.joinToString(", ")

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "entityTypes" to entityTypes,
                "chapterTitle" to (context.currentChapter?.title ?: "未知"),
                "chapterIndex" to chapterIndex,
                "content" to truncatedContent,
                "contentLength" to truncatedContent.length,
                "instruction" to "请仔细阅读以上内容，识别出所有${typeDesc}类型的命名实体。对于每个人物，列出其名称；对于每个地点，列出地名；对于每个组织，列出机构名；对于每个概念，列出专业术语。要求：1) 基于原文内容提取，不要凭空编造；2) 每个实体尽量简洁；3) 如有重复只列一次。"
            )
        )
    }
}

/**
 * 论证分析工具 - 参照ReadAny53实现
 * 功能: 分析作者的论点、论据和逻辑结构
 */
class AnalyzeArgumentsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "analyze_arguments",
    name = "论证分析",
    description = "分析作者的论证逻辑、论据使用和推理结构。当用户询问作者如何论证观点、论据是否充分、逻辑是否严密时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "focusType" to mapOf(
                "type" to "string",
                "description" to "分析重点: main(主要论点), evidence(论据), structure(逻辑结构), all(全部)"
            ),
            "chapterIndex" to mapOf(
                "type" to "integer",
                "description" to "指定章节索引"
            )
        )
    ),
    timeout = 30000  // 30秒超时（需要处理长文本）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val focusType = (input["focusType"] as? String) ?: "all"
        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()

        val content = context.chapterContent
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "无法获取当前章节内容"
            )

        val maxTokens = 3000
        val truncatedContent = if (content.length > maxTokens * 4) {
            content.take(maxTokens * 4) + "\n\n[内容已截断]"
        } else {
            content
        }

        val focusInstruction = when (focusType) {
            "main" -> "请识别并阐述文章的主要论点或核心主张，作者想要证明或传达什么？"
            "evidence" -> "请分析文中使用的证据、数据和实例，这些论据有多强的说服力？"
            "structure" -> "请分析文章的组织结构，论点之间是如何连接的，推理链条是什么？"
            else -> "请提供综合分析：1) 主要论点是什么；2) 使用了哪些证据和例子；3) 逻辑结构如何；4) 整体说服力如何。"
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "focusType" to focusType,
                "chapterTitle" to (context.currentChapter?.title ?: "未知"),
                "chapterIndex" to chapterIndex,
                "content" to truncatedContent,
                "totalTokens" to truncatedContent.length / 4,
                "tokenBudget" to maxTokens,
                "instruction" to focusInstruction
            )
        )
    }
}

/**
 * 引用查找工具 - 参照ReadAny53实现
 * 功能: 查找书中的精彩引用、金句和难忘段落
 */
class FindQuotesTool(
    private val context: AiToolContext
) : BaseTool(
    id = "find_quotes",
    name = "查找引用",
    description = "在书中查找名言、精彩段落和难忘的句子。当用户询问书中有哪些金句、找优美语句、回顾精彩段落时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "quoteType" to mapOf(
                "type" to "string",
                "description" to "引用类型: insightful(洞见智慧), beautiful(文学优美), controversial(争议观点), all(全部)"
            ),
            "chapterIndex" to mapOf(
                "type" to "integer",
                "description" to "指定章节索引"
            ),
            "maxQuotes" to mapOf(
                "type" to "integer",
                "description" to "最多返回引用数，默认5"
            )
        )
    ),
    timeout = 30000  // 30秒超时（需要处理长文本）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val quoteType = (input["quoteType"] as? String) ?: "all"
        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()
        val maxQuotes = (input["maxQuotes"] as? Number)?.toInt() ?: 5

        val content = context.chapterContent
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "无法获取当前章节内容"
            )

        val maxTokens = 4000
        val truncatedContent = if (content.length > maxTokens * 4) {
            content.take(maxTokens * 4) + "\n\n[内容已截断]"
        } else {
            content
        }

        val quoteInstruction = when (quoteType) {
            "insightful" -> "请找出包含智慧、洞见或发人深省的引语，解释每条引语为何重要。"
            "beautiful" -> "请找出语言优美、意象生动或文学价值高的引语，标注其风格特点。"
            "controversial" -> "请找出观点有争议或值得讨论的引语，解释其中的争议所在。"
            else -> "请找出有智慧、有文采或值得关注的引语，解释每条的 significance。"
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "quoteType" to quoteType,
                "maxQuotes" to maxQuotes,
                "chapterTitle" to (context.currentChapter?.title ?: "未知"),
                "chapterIndex" to chapterIndex,
                "content" to truncatedContent,
                "totalTokens" to truncatedContent.length / 4,
                "tokenBudget" to maxTokens,
                "instruction" to "$quoteInstruction 返回最多${maxQuotes}条引语，每条注明在原文中的位置。"
            )
        )
    }
}

/**
 * 添加引用工具 - 完整实现
 * 功能: 在回答中引用书籍原文
 */
class AddQuoteTool(
    private val context: AiToolContext
) : BaseTool(
    id = "add_quote",
    name = "添加引用",
    description = "在回答中引用书籍原文，支持指定章节出处。当用户需要在回答中添加书籍引用时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "quote" to mapOf(
                "type" to "string",
                "description" to "要引用的原文内容"
            ),
            "chapterTitle" to mapOf(
                "type" to "string",
                "description" to "章节标题"
            ),
            "chapterIndex" to mapOf(
                "type" to "integer",
                "description" to "章节索引"
            )
        )
    ),
    timeout = null  // 无需超时（简单格式化操作）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val quote = input["quote"]?.toString()
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "缺少引用内容"
            )

        val chapterTitle = input["chapterTitle"]?.toString()
            ?: context.currentChapter?.title
            ?: "未知章节"

        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()
            ?: context.currentChapter?.index
            ?: 0

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "quote" to quote,
                "chapterTitle" to chapterTitle,
                "chapterIndex" to chapterIndex,
                "formattedQuote" to "「${quote}」—— ${chapterTitle}"
            )
        )
    }
}

/**
 * 列出书籍工具 - 完整实现
 * 功能: 获取用户书架上的书籍列表，支持多种筛选和排序
 * 合并了 list_books 和 bookshelf_lookup 的功能
 */
class ListBooksTool(
    private val context: AiToolContext
) : BaseTool(
    id = "list_books",
    name = "列出书籍",
    description = "获取用户书架上的书籍列表。支持按标题、作者、分类、阅读状态筛选，支持多种排序方式。当用户询问有哪些书、找特定书籍、查看书架、推荐书籍时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "maxItems" to mapOf(
                "type" to "integer",
                "description" to "最大返回书籍数，默认50"
            ),
            "keyword" to mapOf(
                "type" to "string",
                "description" to "搜索关键词（匹配标题或作者）"
            ),
            "category" to mapOf(
                "type" to "string",
                "description" to "按分类筛选"
            ),
            "status" to mapOf(
                "type" to "string",
                "description" to "按阅读状态过滤: unread(未读), reading(阅读中), completed(已完成)"
            ),
            "sortBy" to mapOf(
                "type" to "string",
                "description" to "排序方式: lastRead(最近阅读), title(书名), author(作者), addTime(添加时间)"
            ),
            "includeTags" to mapOf(
                "type" to "boolean",
                "description" to "是否包含标签信息，默认true"
            )
        )
    ),
    timeout = 3000  // 3秒超时
) {
    
    private val repository = io.legado.app.help.ai.repository.BooksRepository(context.appDatabase)

    override suspend fun run(input: Map<String, Any>): ToolResult {
        // ✅ 参考 ReadAny：默认限制 20 本，避免 token 消耗过大
        val defaultLimit = 20
        val maxItems = (input["maxItems"] as? Number)?.toInt() ?: defaultLimit
        
        // ✅ 调试：打印所有接收到的参数
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "ListBooks",
            "原始输入参数: ${input.entries.joinToString { "${it.key}=${it.value}" }}"
        )
        
        // ✅ 容错处理：尝试所有可能的参数名
        val keyword = (
            input["keyword"]?.toString() ?:           // 标准参数名
            input["title"]?.toString() ?:             // AI可能使用的参数名（搜索书名）
            input["search"]?.toString() ?:            // 备选
            input["q"]?.toString() ?:                 // 简写
            input["query"]?.toString() ?:             // 另一种常见命名
            input["name"]?.toString() ?:              // 书名
            input["book"]?.toString() ?:              // 书
            input["arg0"]?.toString()                 // AI可能使用的错误参数名
        )
        
        // ✅ 添加调试日志
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "ListBooks",
            "提取的关键词: keyword=$keyword"
        )
        
        val category = input["category"]?.toString()
        val status = input["status"]?.toString()
        
        // ✅ 容错处理：sortBy参数
        val sortBy = (
            input["sortBy"]?.toString() ?:            // 标准参数名
            input["sort"]?.toString() ?:              // 备选
            input["arg1"]?.toString() ?:              // AI可能使用的错误参数名
            "lastRead"                                // 默认值
        )
        
        val includeTags = input["includeTags"] as? Boolean ?: true

        // ✅ 关键修复：如果有搜索关键词，返回所有匹配结果；否则限制数量
        val effectiveLimit = if (!keyword.isNullOrBlank()) {
            Int.MAX_VALUE  // 搜索时返回所有匹配
        } else {
            maxItems       // 无搜索时限制数量
        }

        // 使用 Repository 获取数据
        val books = repository.searchBooks(
            keyword = keyword,
            category = category,
            status = status,
            sortBy = sortBy,
            limit = effectiveLimit,
            includeTags = includeTags
        )
        
        // ✅ 添加调试日志
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "ListBooks",
            "搜索结果: 找到 ${books.size} 本书, 关键词='$keyword'"
        )

        // 获取分组信息
        val groups = repository.getGroups()
        
        // ✅ 关键修复：如果有搜索关键词，添加明确的提示信息
        val searchHint = if (!keyword.isNullOrBlank()) {
            if (books.isEmpty()) {
                "\n\n⚠️ 未找到包含'$keyword'的书籍。请检查书名是否正确，或者尝试其他关键词。"
            } else {
                "\n\n✅ 找到 ${books.size} 本包含'$keyword'的书籍："
            }
        } else {
            ""
        }

        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "totalBooks" to books.size,
                "searchKeyword" to keyword,  // 返回搜索关键词，方便AI确认
                "books" to books.map { it.toMap() },
                "groups" to groups.map { it.toMap() },
                "hint" to searchHint  // 添加提示信息
            )
        )
    }
}

/**
 * 搜索所有笔记工具 - 完整实现
 * 功能: 在所有书籍中搜索笔记和高亮
 */
class SearchAllNotesTool(
    private val context: AiToolContext
) : BaseTool(
    id = "search_all_notes",
    name = "搜索所有笔记",
    description = "在用户所有书籍中搜索笔记和高亮内容，支持关键词搜索。当用户询问我在哪些书中标记过某个内容、查找特定主题的笔记时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "keyword" to mapOf(
                "type" to "string",
                "description" to "搜索关键词"
            ),
            "maxItems" to mapOf(
                "type" to "integer",
                "description" to "最大返回结果数，默认30"
            )
        )
    ),
    timeout = 30000  // 30秒超时（可能需要遍历大量笔记）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val keyword = input["keyword"]?.toString()
        val maxItems = (input["maxItems"] as? Number)?.toInt() ?: 30

        if (keyword.isNullOrBlank()) {
            return ToolResult(
                status = "error",
                name = id,
                message = "缺少搜索关键词"
            )
        }

        if (keyword.length < 2) {
            return ToolResult(
                status = "error",
                name = id,
                message = "搜索关键词至少需要2个字符"
            )
        }

        return try {
            val annotationDao = context.appDatabase.bookAnnotationDao
            val bookDao = context.appDatabase.bookDao

            val allAnnotations = annotationDao.all

            val keywordLower = keyword.lowercase()
            val filtered = allAnnotations.filter { annotation: io.legado.app.data.entities.BookAnnotation ->
                annotation.content.lowercase().contains(keywordLower) ||
                (annotation.note?.lowercase()?.contains(keywordLower) == true)
            }

            val bookMap = bookDao.all.associateBy { "${it.name}_${it.author}" }

            val results = filtered.take(maxItems).mapNotNull { annotation: io.legado.app.data.entities.BookAnnotation ->
                val book = bookMap["${annotation.bookName}_${annotation.bookAuthor}"]
                if (book != null) {
                    mapOf(
                        "id" to annotation.time,
                        "bookTitle" to book.name,
                        "bookAuthor" to (book.author ?: "未知"),
                        "chapterIndex" to annotation.chapterIndex,
                        "chapterTitle" to annotation.chapterName,
                        "content" to annotation.content,
                        "note" to (annotation.note ?: ""),
                        "createTime" to annotation.time
                    )
                } else {
                    null
                }
            }

            val groupedByBook = results.groupBy { it["bookTitle"] as String }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "keyword" to keyword,
                    "totalResults" to filtered.size,
                    "returnedResults" to results.size,
                    "results" to results,
                    "booksWithResults" to groupedByBook.keys.toList(),
                    "resultsByBook" to groupedByBook.mapValues { (_, notes) -> notes.size }
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "搜索笔记失败: ${e.message}"
            )
        }
    }
}

/**
 * 标签列表工具 - 参照anx53实现
 * 功能: 获取用户的所有标签
 */
class TagsListTool(
    private val context: AiToolContext
) : BaseTool(
    id = "tags_list",
    name = "标签列表",
    description = "获取用户创建的所有标签列表。当用户询问有哪些标签、管理标签时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "searchKeyword" to mapOf(
                "type" to "string",
                "description" to "搜索标签关键词"
            )
        )
    ),
    timeout = null  // 无需超时（简单查询）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val searchKeyword = input["searchKeyword"]?.toString()

        return try {
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao

            val tags = if (searchKeyword.isNullOrBlank()) {
                tagDao.getAll()
            } else {
                tagDao.searchByKeyword(searchKeyword)
            }

            val tagCounts = tagRelationDao.getTagBookCounts()
            val countMap = tagCounts.associate { it.tagId to it.bookCount }

            val tagsList = tags.map { tag ->
                mapOf(
                    "id" to tag.id,
                    "name" to tag.name,
                    "color" to tag.color,
                    "groupId" to tag.groupId,
                    "bookCount" to (countMap[tag.id] ?: 0)
                )
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "totalTags" to tags.size,
                    "tags" to tagsList
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "获取标签列表失败: ${e.message}"
            )
        }
    }
}

/**
 * 书籍标签工具 - 参照anx53实现
 * 功能: 获取某本书的所有标签
 */
class BookTagsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "book_tags",
    name = "书籍标签",
    description = "获取当前书籍的所有标签。当用户询问这本书有哪些标签、查看书籍分类时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf<String, Any>()
    ),
    timeout = null  // 无需超时（简单查询）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val book = context.currentBook
            ?: return ToolResult(
                status = "error",
                name = id,
                message = "当前没有正在阅读的书籍"
            )

        return try {
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao

            val relations = tagRelationDao.getRelationsByBook(book.bookUrl)
            val tagIds = relations.map { it.tagId }
            val tags = if (tagIds.isNotEmpty()) {
                tagDao.getTagsByIds(tagIds)
            } else {
                emptyList()
            }

            val tagsList = tags.map { tag ->
                mapOf(
                    "id" to tag.id,
                    "name" to tag.name,
                    "color" to tag.color
                )
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "bookTitle" to book.name,
                    "totalTags" to tags.size,
                    "tags" to tagsList
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "获取书籍标签失败: ${e.message}"
            )
        }
    }
}

/**
 * 应用书籍标签工具 - 参照anx53实现
 * 功能: 为书籍添加或移除标签，返回需要用户确认的操作计划
 */
class ApplyBookTagsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "apply_book_tags",
    name = "应用书籍标签",
    description = "为书籍添加或移除标签，返回需要用户确认的操作计划。可以智能分析书籍内容推荐标签，并自动选择合适的颜色。当用户要求给书籍打标签、分类书籍时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "books" to mapOf(
                "type" to "array",
                "description" to "要标记的书籍列表 [{bookUrl, tags}]。tags可以是字符串数组，也可以是空数组让AI智能推荐"
            ),
            "createTags" to mapOf(
                "type" to "array",
                "description" to "要创建的新标签 [{name, color}]。如果不指定color，系统会自动选择合适的颜色"
            ),
            "autoAnalyze" to mapOf(
                "type" to "boolean",
                "description" to "是否让AI智能分析书籍内容并推荐标签。默认为false"
            )
        )
    ),
    timeout = 12000  // 12秒超时（可能需要分析书籍内容）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val booksInput = input["books"] as? List<*>
        val createTagsInput = input["createTags"] as? List<*>
        val autoAnalyze = input["autoAnalyze"] as? Boolean ?: false

        return try {
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao
            val bookDao = context.appDatabase.bookDao

            val createPlans = mutableListOf<Map<String, Any>>()
            val bookChanges = mutableListOf<Map<String, Any>>()
            val conflicts = mutableListOf<Map<String, Any>>()
            
            // 预定义的标签颜色调色板（Material Design风格）
            val tagColorPalette = listOf(
                0xFFE57373.toInt(), // 红色
                0xFFF06292.toInt(), // 粉红
                0xFFBA68C8.toInt(), // 紫色
                0xFF9575CD.toInt(), // 深紫
                0xFF7986CB.toInt(), // 靛蓝
                0xFF64B5F6.toInt(), // 蓝色
                0xFF4FC3F7.toInt(), // 浅蓝
                0xFF4DD0E1.toInt(), // 青色
                0xFF4DB6AC.toInt(), // 青绿
                0xFF81C784.toInt(), // 绿色
                0xFFAED581.toInt(), // 浅绿
                0xFFFFD54F.toInt(), // 黄色
                0xFFFFB74D.toInt(), // 橙色
                0xFFA1887F.toInt(), // 棕色
                0xFF90A4AE.toInt()  // 灰色
            )
            
            // 根据标签名称智能选择颜色的函数
            fun selectColorForTag(tagName: String): Int {
                // 基于标签名称的哈希值选择颜色，确保同一标签总是得到相同颜色
                val hash = Math.abs(tagName.hashCode())
                return tagColorPalette[hash % tagColorPalette.size]
            }

            // 处理创建标签
            if (createTagsInput != null) {
                for (tagSpec in createTagsInput.filterIsInstance<Map<String, Any>>()) {
                    val name = tagSpec["name"] as? String
                    if (!name.isNullOrBlank()) {
                        val existingTag = tagDao.getTagByName(name)
                        if (existingTag != null) {
                            conflicts.add(mapOf(
                                "type" to "tag_exists",
                                "name" to name,
                                "message" to "标签 '$name' 已存在"
                            ))
                        } else {
                            // 如果没有指定颜色，自动选择合适的颜色
                            val color = (tagSpec["color"] as? Number)?.toInt() ?: selectColorForTag(name)
                            createPlans.add(mapOf(
                                "name" to name,
                                "color" to color
                            ))
                        }
                    }
                }
            }

            // 处理书籍标签
            if (booksInput != null) {
                for (bookSpec in booksInput.filterIsInstance<Map<String, Any>>()) {
                    val bookUrl = bookSpec["bookUrl"] as? String
                    val tags = bookSpec["tags"] as? List<*> ?: emptyList<String>()

                    if (bookUrl.isNullOrBlank()) {
                        conflicts.add(mapOf("type" to "missing_book_url", "message" to "书籍URL为空"))
                        continue
                    }

                    val book = bookDao.getBook(bookUrl)
                    if (book == null) {
                        conflicts.add(mapOf(
                            "type" to "missing_book",
                            "bookUrl" to bookUrl,
                            "message" to "书籍不存在"
                        ))
                        continue
                    }

                    val currentRelations = tagRelationDao.getRelationsByBook(bookUrl)
                    val currentTagIds = currentRelations.map { it.tagId }.toSet()
                    val tagNames = tags.filterIsInstance<String>()
                    val desiredTags = mutableListOf<Map<String, Any>>()

                    for (tagName in tagNames) {
                        var tag = tagDao.getTagByName(tagName)
                        if (tag == null) {
                            val createPlan = createPlans.find { it["name"] == tagName }
                            if (createPlan != null) {
                                val newId = tagDao.insert(io.legado.app.data.entities.BookTag(
                                    name = tagName,
                                    color = (createPlan["color"] as? Number)?.toInt() ?: selectColorForTag(tagName)
                                ))
                                tag = tagDao.getTag(newId)
                            }
                        }
                        if (tag != null) {
                            desiredTags.add(mapOf("id" to tag.id, "name" to tag.name))
                        }
                    }

                    val desiredTagIds = desiredTags.map { it["id"] as Long }.toSet()
                    val toAdd = desiredTagIds - currentTagIds
                    val toRemove = currentTagIds - desiredTagIds

                    bookChanges.add(mapOf(
                        "bookUrl" to bookUrl,
                        "bookTitle" to book.name,
                        "addTagIds" to toAdd.toList(),
                        "removeTagIds" to toRemove.toList(),
                        "finalTags" to desiredTags
                    ))
                }
            }

            return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "requiresConfirmation" to true,
                    "plan" to mapOf(
                        "createTags" to createPlans,
                        "bookChanges" to bookChanges
                    ),
                    "conflicts" to conflicts
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "应用标签失败: ${e.message}"
            )
        }
    }
}

/**
 * 管理书籍标签工具 - 参照ReadAny53实现
 * 功能: 创建、删除、重命名标签，管理书籍标签
 */
class ManageTagsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "manage_tags",
    name = "管理标签",
    description = "创建、删除、重命名标签，或从书籍添加/移除标签。当用户要求创建新标签、删除标签、重命名标签、给书籍打标签时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf(
                "type" to "string",
                "description" to "操作类型: create(创建), delete(删除), rename(重命名), addToBook(添加到书籍), removeFromBook(从书籍移除), setBookTags(设置书籍标签)"
            ),
            "tagName" to mapOf("type" to "string", "description" to "标签名称"),
            "newTagName" to mapOf("type" to "string", "description" to "新标签名称（用于重命名）"),
            "bookUrl" to mapOf("type" to "string", "description" to "书籍URL"),
            "color" to mapOf("type" to "integer", "description" to "标签颜色")
        )
    ),
    timeout = 10000  // 10秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val action = (input["action"] as? String) ?: return ToolResult(
            status = "error",
            name = id,
            message = "缺少action参数"
        )

        return try {
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao
            
            // 预定义的标签颜色调色板（Material Design风格）
            val tagColorPalette = listOf(
                0xFFE57373.toInt(), // 红色
                0xFFF06292.toInt(), // 粉红
                0xFFBA68C8.toInt(), // 紫色
                0xFF9575CD.toInt(), // 深紫
                0xFF7986CB.toInt(), // 靛蓝
                0xFF64B5F6.toInt(), // 蓝色
                0xFF4FC3F7.toInt(), // 浅蓝
                0xFF4DD0E1.toInt(), // 青色
                0xFF4DB6AC.toInt(), // 青绿
                0xFF81C784.toInt(), // 绿色
                0xFFAED581.toInt(), // 浅绿
                0xFFFFD54F.toInt(), // 黄色
                0xFFFFB74D.toInt(), // 橙色
                0xFFA1887F.toInt(), // 棕色
                0xFF90A4AE.toInt()  // 灰色
            )
            
            // 根据标签名称智能选择颜色的函数
            fun selectColorForTag(tagName: String): Int {
                val hash = Math.abs(tagName.hashCode())
                return tagColorPalette[hash % tagColorPalette.size]
            }

            when (action) {
                "create" -> {
                    val tagName = (input["tagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少tagName")
                    // 如果没有指定颜色，自动选择合适的颜色
                    val color = (input["color"] as? Number)?.toInt() ?: selectColorForTag(tagName)

                    val existing = tagDao.getTagByName(tagName)
                    if (existing != null) {
                        return ToolResult(
                            status = "ok",
                            name = id,
                            data = mapOf("success" to true, "message" to "标签已存在", "tag" to mapOf(
                                "id" to existing.id,
                                "name" to existing.name,
                                "color" to existing.color
                            ))
                        )
                    }

                    val newTag = io.legado.app.data.entities.BookTag(name = tagName, color = color)
                    val tagId = tagDao.insert(newTag)
                    ToolResult(
                        status = "ok",
                        name = "create_tag",
                        data = mapOf("success" to true, "created" to true, "tagId" to tagId)
                    )
                }

                "delete" -> {
                    val tagName = (input["tagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少tagName")

                    val tag = tagDao.getTagByName(tagName)
                        ?: return ToolResult(status = "error", name = id, message = "标签不存在")

                    tagRelationDao.deleteRelationsByTag(tag.id)
                    tagDao.delete(tag)

                    ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf("success" to true, "deleted" to tagName)
                    )
                }

                "rename" -> {
                    val tagName = (input["tagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少tagName")
                    val newTagName = (input["newTagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少newTagName")

                    val tag = tagDao.getTagByName(tagName)
                        ?: return ToolResult(status = "error", name = id, message = "标签不存在")

                    val updated = tag.copy(name = newTagName, updateTime = System.currentTimeMillis())
                    tagDao.update(updated)

                    ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf("success" to true, "oldName" to tagName, "newName" to newTagName)
                    )
                }

                "addToBook" -> {
                    val tagName = (input["tagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少tagName")
                    val bookUrl = (input["bookUrl"] as? String)?.trim()
                        ?: context.bookUrl

                    val tag = tagDao.getTagByName(tagName)
                        ?: return ToolResult(status = "error", name = id, message = "标签不存在")

                    val relation = BookTagRelation(
                        id = "${bookUrl}_${tag.id}_${System.currentTimeMillis()}",
                        bookUrl = bookUrl,
                        tagId = tag.id
                    )
                    tagRelationDao.insert(relation)

                    ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf("success" to true, "added" to tagName)
                    )
                }

                "removeFromBook" -> {
                    val tagName = (input["tagName"] as? String)?.trim()
                        ?: return ToolResult(status = "error", name = id, message = "缺少tagName")
                    val bookUrl = (input["bookUrl"] as? String)?.trim()
                        ?: context.bookUrl

                    val tag = tagDao.getTagByName(tagName)
                        ?: return ToolResult(status = "error", name = id, message = "标签不存在")

                    tagRelationDao.deleteRelation(bookUrl, tag.id)

                    ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf("success" to true, "removed" to tagName)
                    )
                }

                "setBookTags" -> {
                    val bookUrl = (input["bookUrl"] as? String)?.trim()
                        ?: context.bookUrl
                    val tagsInput = input["tags"] as? List<*> ?: emptyList<Any>()
                    val tagNames = tagsInput.filterIsInstance<String>()

                    val tagIds = mutableListOf<Long>()
                    for (name in tagNames) {
                        var tag = tagDao.getTagByName(name)
                        if (tag == null) {
                            val newId = tagDao.insert(io.legado.app.data.entities.BookTag(name = name))
                            tag = tagDao.getTag(newId)
                        }
                        if (tag != null) tagIds.add(tag.id)
                    }

                    tagRelationDao.deleteRelationsByBook(bookUrl)
                    for (tagId in tagIds) {
                        tagRelationDao.insert(BookTagRelation(
                            id = "${bookUrl}_${tagId}_${System.currentTimeMillis()}",
                            bookUrl = bookUrl,
                            tagId = tagId
                        ))
                    }

                    ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf("success" to true, "bookUrl" to bookUrl, "tags" to tagNames)
                    )
                }

                else -> ToolResult(
                    status = "error",
                    name = id,
                    message = "未知操作: $action"
                )
            }
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "管理标签失败: ${e.message}"
            )
        }
    }
}

/**
 * 书架查询工具 - 参照anx53实现
 * 功能: 获取书架上的书籍列表和分组
 */
class BookshelfLookupTool(
    private val context: AiToolContext
) : BaseTool(
    id = "bookshelf_lookup",
    name = "书架查询",
    description = "获取书架上的书籍列表，可以按分组筛选。当用户询问有哪些书、查看书架时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "groupId" to mapOf("type" to "integer", "description" to "分组ID，不指定则返回全部"),
            "limit" to mapOf("type" to "integer", "description" to "最大返回数量，默认50")
        )
    ),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val groupId = (input["groupId"] as? Number)?.toLong()
        val limit = (input["limit"] as? Number)?.toInt() ?: 50

        return try {
            val bookDao = context.appDatabase.bookDao
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao
            val groupDao = context.appDatabase.bookTagGroupDao

            val allBooks = bookDao.all.take(limit)

            val booksWithTags = allBooks.map { book ->
                val relations = tagRelationDao.getRelationsByBook(book.bookUrl)
                val tagIds = relations.map { it.tagId }
                val tags = if (tagIds.isNotEmpty()) tagDao.getTagsByIds(tagIds) else emptyList()

                val progress = if (book.totalChapterNum > 0) {
                    (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
                } else 0

                mapOf(
                    "bookUrl" to book.bookUrl,
                    "title" to book.name,
                    "author" to (book.author ?: "未知"),
                    "kind" to (book.kind ?: "未分类"),
                    "progress" to progress,
                    "lastRead" to formatTime(book.lastCheckTime),
                    "tags" to tags.map { mapOf("id" to it.id, "name" to it.name) }
                )
            }

            val groups = if (groupDao != null) {
                try {
                    val method = groupDao.javaClass.getMethod("getAll")
                    @Suppress("UNCHECKED_CAST")
                    (method.invoke(groupDao) as? List<*>)?.mapNotNull { item ->
                        try {
                            item?.javaClass?.let { clazz ->
                                val idField = clazz.getField("id")
                                val nameField = clazz.getField("name")
                                mapOf("id" to idField.get(item), "name" to nameField.get(item))
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }?.take(100) ?: emptyList()
                } catch (e: Exception) {
                    emptyList<Map<String, Any?>>()
                }
            } else {
                emptyList()
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "totalBooks" to booksWithTags.size,
                    "books" to booksWithTags,
                    "groups" to groups
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "获取书架失败: ${e.message}"
            )
        }
    }
}

/**
 * 书架整理工具 - 参照anx53实现
 * 功能: 规划书架分组重组方案，需要用户确认
 */
class BookshelfOrganizeTool(
    private val context: AiToolContext
) : BaseTool(
    id = "bookshelf_organize",
    name = "书架整理",
    description = "规划书架分组重组方案，需要用户确认。当用户要求整理书架、分类书籍时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "groups" to mapOf(
                "type" to "array",
                "description" to "分组计划 [{groupName, bookUrls, createNew}]"
            ),
            "summary" to mapOf("type" to "string", "description" to "整理方案说明")
        )
    ),
    timeout = 8000  // 8秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val groupsInput = input["groups"] as? List<*>
        val summary = input["summary"] as? String

        if (groupsInput.isNullOrEmpty()) {
            return ToolResult(
                status = "error",
                name = id,
                message = "缺少groups参数"
            )
        }

        return try {
            val tagDao = context.appDatabase.bookTagDao
            val tagRelationDao = context.appDatabase.bookTagRelationDao
            val bookDao = context.appDatabase.bookDao

            val groupsPlan = mutableListOf<Map<String, Any>>()
            var movedBooksCount = 0

            for (groupSpec in groupsInput.filterIsInstance<Map<String, Any>>()) {
                val groupName = groupSpec["groupName"] as? String ?: "未命名分组"
                val bookUrls = (groupSpec["bookUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val createNew = groupSpec["createNew"] as? Boolean ?: false

                val booksInfo = mutableListOf<Map<String, Any?>>()
                for (url in bookUrls) {
                    val book = bookDao.getBook(url)
                    if (book != null) {
                        booksInfo.add(mapOf(
                            "bookUrl" to url,
                            "title" to book.name,
                            "author" to book.author
                        ))
                        movedBooksCount++
                    }
                }

                groupsPlan.add(mapOf(
                    "groupName" to groupName,
                    "createNew" to createNew,
                    "books" to booksInfo
                ))
            }

            return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "requiresConfirmation" to true,
                    "plan" to mapOf(
                        "summary" to (summary ?: "将 ${movedBooksCount} 本书整理到 ${groupsPlan.size} 个分组"),
                        "groups" to groupsPlan,
                        "stats" to mapOf(
                            "groups" to groupsPlan.size,
                            "movedBooks" to movedBooksCount
                        )
                    )
                )
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "整理书架失败: ${e.message}"
            )
        }
    }
}

/**
 * 章节比较工具 - 参照ReadAny53实现
 * 功能: 比较书中两个章节的内容差异
 */
class CompareSectionsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "compare_sections",
    name = "章节比较",
    description = "比较书中两个章节的内容、主题或写作风格。当用户询问两个章节的区别、比较不同部分时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "chapterIndex1" to mapOf("type" to "integer", "description" to "第一个章节索引", "required" to true),
            "chapterIndex2" to mapOf("type" to "integer", "description" to "第二个章节索引", "required" to true),
            "compareType" to mapOf("type" to "string", "description" to "比较类型: themes(主题), arguments(论点), style(风格), all(全部)")
        )
    ),
    timeout = 30000  // 30秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val chapterIndex1 = (input["chapterIndex1"] as? Number)?.toInt()
            ?: return ToolResult(status = "error", name = id, message = "缺少chapterIndex1")
        val chapterIndex2 = (input["chapterIndex2"] as? Number)?.toInt()
            ?: return ToolResult(status = "error", name = id, message = "缺少chapterIndex2")
        val compareType = (input["compareType"] as? String) ?: "all"

        val chapterDao = context.appDatabase.bookChapterDao
        val bookUrl = context.bookUrl

        if (bookUrl.isBlank()) {
            return ToolResult(status = "error", name = id, message = "无法获取书籍URL")
        }

        return try {
            val chapters1 = chapterDao.getChapterList(bookUrl).filter { it.index == chapterIndex1 }
            val chapters2 = chapterDao.getChapterList(bookUrl).filter { it.index == chapterIndex2 }

            if (chapters1.isEmpty() || chapters2.isEmpty()) {
                return ToolResult(status = "error", name = id, message = "章节不存在")
            }

            val chapter1Content = if (chapterIndex1 == context.currentChapter?.index) {
                context.chapterContent ?: ""
            } else {
                ""
            }

            val chapter2Content = if (chapterIndex2 == context.currentChapter?.index) {
                context.chapterContent ?: ""
            } else {
                ""
            }

            val compareInstruction = when (compareType) {
                "themes" -> "比较这两个章节讨论的主题异同点，有哪些主题是共有的，哪些是各自独有的？"
                "arguments" -> "比较两个章节中的论点是否一致、矛盾或互补？"
                "style" -> "比较两个章节的写作风格、语气和语言特点有何不同？"
                else -> "请综合比较：主题、论点和写作风格三个方面的异同。"
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "chapter1" to mapOf(
                        "index" to chapterIndex1,
                        "title" to chapters1.first().title,
                        "content" to chapter1Content.take(1500)
                    ),
                    "chapter2" to mapOf(
                        "index" to chapterIndex2,
                        "title" to chapters2.first().title,
                        "content" to chapter2Content.take(1500)
                    ),
                    "compareType" to compareType,
                    "instruction" to compareInstruction
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "比较章节失败: ${e.message}")
        }
    }
}

/**
 * RAG搜索工具 - 完整实现
 * 功能: 使用语义搜索在已向量化书籍中查找相关内容
 */
class RagSearchTool(
    private val context: AiToolContext
) : BaseTool(
    id = "rag_search",
    name = "RAG搜索",
    description = "在已向量化的书籍内容中进行搜索。支持语义搜索、关键词搜索和混合搜索。返回相关段落及其位置信息，可用于精确定位和引用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "搜索查询", "required" to true),
            "mode" to mapOf("type" to "string", "description" to "搜索模式：hybrid(混合，推荐)、vector(语义)、bm25(关键词)", "default" to "hybrid"),
            "topK" to mapOf("type" to "integer", "description" to "返回结果数量，默认5")
        )
    ),
    timeout = 30000  // 30秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // ✅ 容错处理：尝试多种可能的参数名
        var query = (
            (input["query"] as? String)?.trim() ?:           // 标准参数名
            (input["q"] as? String)?.trim() ?:               // 简写
            (input["search"] as? String)?.trim() ?:          // 备选
            (input["keyword"] as? String)?.trim()            // 其他可能
        )
        
        // ✅ 如果AI使用了arg0/arg1格式，将它们合并
        if (query.isNullOrBlank()) {
            val arg0 = input["arg0"] as? String
            val arg1 = input["arg1"] as? String
            
            if (!arg0.isNullOrBlank() && !arg1.isNullOrBlank()) {
                // 合并两个参数，例如："童话保质期" + "故事结局" -> "童话保质期 故事结局"
                query = "$arg0 $arg1".trim()
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                    "RagSearch",
                    "检测到arg0/arg1格式，已合并为: $query"
                )
            } else if (!arg0.isNullOrBlank()) {
                query = arg0.trim()
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                    "RagSearch",
                    "使用arg0作为query: $query"
                )
            }
        }
        
        if (query.isNullOrBlank()) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                "RagSearch",
                "缺少query参数，收到的参数: ${input.keys.joinToString(", ")}"
            )
            return ToolResult(
                status = "error", 
                name = id, 
                message = "缺少query参数。正确格式：{\"query\": \"搜索内容\"}。例如：{\"query\": \"童话保质期的结局是什么\"}。不要使用arg0/arg1等参数名。"
            )
        }
        
        val topK = (input["topK"] as? Number)?.toInt() ?: 5

        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        var bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl
        
        // ✅ 如果没有 bookUrl，尝试从查询中匹配已向量化的书籍
        if (bookUrl.isBlank()) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                "RagSearch",
                "没有当前阅读上下文，尝试从查询中匹配书籍"
            )
            
            // 尝试与已向量化的书籍名称进行模糊匹配
            val vectorizedBooks = context.appDatabase.vectorizedBookDao.getAll()
            if (vectorizedBooks.isNotEmpty()) {
                // 将查询分词，尝试匹配书名中的关键词
                val queryWords = query.split(Regex("[\\s,，、。！？；;]+"))
                    .filter { it.length >= 2 }  // 只保留长度>=2的词
                
                if (queryWords.isNotEmpty()) {
                    // 计算每个书籍的匹配度
                    val scoredBooks = vectorizedBooks.mapNotNull { book ->
                        val title = book.bookTitle
                        var score = 0
                        
                        // 完全包含查询词
                        for (word in queryWords) {
                            if (title.contains(word, ignoreCase = true)) {
                                score += word.length * 2
                            }
                        }
                        
                        // 查询包含书名的部分
                        if (query.contains(title, ignoreCase = true)) {
                            score += title.length * 3
                        }
                        
                        if (score > 0) Pair(book, score) else null
                    }.sortedByDescending { it.second }
                    
                    if (scoredBooks.isNotEmpty()) {
                        val bestMatch = scoredBooks.first()
                        bookUrl = bestMatch.first.bookUrl
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                            "RagSearch",
                            "模糊匹配到书籍: ${bestMatch.first.bookTitle}, 匹配度: ${bestMatch.second}"
                        )
                    }
                }
            }
        }
        
        if (bookUrl.isBlank()) {
            // 获取已向量化的书籍列表，给用户一些建议
            val vectorizedBooks = context.appDatabase.vectorizedBookDao.getAll()
            val suggestions = if (vectorizedBooks.isNotEmpty()) {
                val bookTitles = vectorizedBooks.take(3).map { it.bookTitle }.joinToString("、")
                "\n\n已向量化的书籍：$bookTitles"
            } else {
                "\n\n提示：还没有已向量化书籍，请先在书籍详情页进行向量化"
            }
            
            return ToolResult(
                status = "error", 
                name = id, 
                message = "未找到匹配的书籍$suggestions"
            )
        }

        val config = VectorConfigManager.getConfig()
        if (!config.enabled) {
            return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "results" to emptyList<Map<String, Any>>(),
                    "totalResults" to 0,
                    "message" to "向量搜索未启用，请在AI设置 → 向量模型中配置并启用"
                )
            )
        }

        return try {
            val vectorService = VectorSearchService(context.appContext)
            
            // ✅ 获取搜索模式，默认使用混合搜索
            val mode = (input["mode"] as? String)?.lowercase() ?: "hybrid"
            
            // 根据模式选择搜索方法
            val results = when (mode) {
                "bm25" -> {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "RagSearch",
                        "使用BM25关键词搜索模式"
                    )
                    if (bookUrl != null) {
                        vectorService.bm25Search(query, bookUrl, topK)
                    } else {
                        emptyList()
                    }
                }
                "vector" -> {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "RagSearch",
                        "使用向量语义搜索模式"
                    )
                    vectorService.semanticSearch(query, bookUrl, config, topK, maxTokensPerResult = 500)
                }
                else -> {  // hybrid
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "RagSearch",
                        "使用混合搜索模式（vector + BM25）"
                    )
                    vectorService.hybridSearch(query, bookUrl, config, topK)
                }
            }

            if (results.isEmpty()) {
                return ToolResult(
                    status = "ok",
                    name = id,
                    data = mapOf(
                        "query" to query,
                        "results" to emptyList<Map<String, Any>>(),
                        "totalResults" to 0,
                        "totalChunks" to 0,
                        "message" to "未找到相关内容，可能该书籍尚未向量化"
                    )
                )
            }

            // ✅ 结果已经被智能截断，不需要再次 take(800)
            val searchResults = results.map { result ->
                mapOf(
                    "chunkId" to result.chunk.id,
                    "chapterIndex" to result.chunk.chapterIndex,
                    "chapterTitle" to result.chunk.chapterTitle,
                    "content" to result.content,  // 已经截断过了
                    "score" to result.score,
                    "matchType" to "vector"
                )
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "query" to query,
                    "results" to searchResults,
                    "totalResults" to searchResults.size,
                    "message" to "找到 ${searchResults.size} 条相关内容",
                    "instruction" to "请根据以上搜索结果回答用户问题。引用相关段落时注明来源章节。"
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "RAG搜索失败: ${e.message}")
        }
    }
}

/**
 * RAG目录工具 - 完整实现
 * 功能: 获取已向量化书籍的章节结构
 */
class RagTocTool(
    private val context: AiToolContext
) : BaseTool(
    id = "rag_toc",
    name = "RAG目录",
    description = "获取向量化书籍的目录结构。当用户询问书籍结构、章节组织时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf<String, Any>()
    ),
    timeout = 5000  // 5秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl
        if (bookUrl.isBlank()) {
            return ToolResult(status = "error", name = id, message = "无法获取书籍URL")
        }

        return try {
            val chunkDb = io.legado.app.help.ai.rag.TextChunkDatabase(context.appContext)
            val chunks = chunkDb.getChunksByBookUrl(bookUrl)

            if (chunks.isEmpty()) {
                return ToolResult(
                    status = "ok",
                    name = id,
                    data = mapOf(
                        "chapters" to emptyList<Map<String, Any>>(),
                        "totalChapters" to 0,
                        "isVectorized" to false,
                        "message" to "该书籍尚未向量化"
                    )
                )
            }

            val chapterMap = mutableMapOf<Int, String>()
            for (chunk in chunks) {
                if (!chapterMap.containsKey(chunk.chapterIndex)) {
                    chapterMap[chunk.chapterIndex] = chunk.chapterTitle
                }
            }

            val chapters = chapterMap.entries.sortedBy { it.key }.map { (index, title) ->
                mapOf("index" to index, "title" to title)
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "chapters" to chapters,
                    "totalChapters" to chapters.size,
                    "isVectorized" to true,
                    "totalChunks" to chunks.size
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "获取目录失败: ${e.message}")
        }
    }
}

/**
 * RAG上下文工具 - 完整实现
 * 功能: 获取特定章节周围的上下文内容
 */
class RagContextTool(
    private val context: AiToolContext
) : BaseTool(
    id = "rag_context",
    name = "RAG上下文",
    description = "获取向量化搜索的上下文信息。当需要理解搜索结果的背景时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "chapterIndex" to mapOf("type" to "integer", "description" to "章节索引", "required" to true),
            "range" to mapOf("type" to "integer", "description" to "前后章节数，默认2")
        )
    ),
    timeout = 10000  // 10秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()
            ?: return ToolResult(status = "error", name = id, message = "缺少chapterIndex参数")
        val range = (input["range"] as? Number)?.toInt() ?: 2

        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        
        // 如果 ReadingContextService 有数据，使用它；否则回退到静态 context
        val bookUrl = readingContext?.bookId?.takeIf { it.isNotBlank() } ?: context.bookUrl
        if (bookUrl.isBlank()) {
            return ToolResult(status = "error", name = id, message = "无法获取书籍URL")
        }

        return try {
            val chunkDb = io.legado.app.help.ai.rag.TextChunkDatabase(context.appContext)
            val chunks = chunkDb.getChunksByBookUrl(bookUrl)

            if (chunks.isEmpty()) {
                return ToolResult(
                    status = "ok",
                    name = id,
                    data = mapOf(
                        "context" to "",
                        "chapters" to emptyList<Map<String, Any>>(),
                        "message" to "该书籍尚未向量化"
                    )
                )
            }

            val targetChunks = chunks.filter { it.chapterIndex in (chapterIndex - range)..(chapterIndex + range) }
            val sorted = targetChunks.sortedBy { it.chapterIndex }

            val contextText = sorted.joinToString("\n\n") { chunk ->
                "【${chunk.chapterTitle}】\n${chunk.content}"
            }

            val chapterList = sorted.map { chunk ->
                mapOf(
                    "chapterIndex" to chunk.chapterIndex,
                    "chapterTitle" to chunk.chapterTitle
                )
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "chapterTitle" to (sorted.firstOrNull()?.chapterTitle ?: "未知"),
                    "chapterIndex" to chapterIndex,
                    "context" to contextText.take(3000),
                    "chapters" to chapterList,
                    "chunksIncluded" to sorted.size
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "获取上下文失败: ${e.message}")
        }
    }
}

/**
 * 向量化状态工具 - 完整实现
 * 功能: 检查书籍是否已向量化
 */
class VectorizationStatusTool(
    private val context: AiToolContext
) : BaseTool(
    id = "vectorization_status",
    name = "向量化状态",
    description = "检查书籍向量化处理的状态和进度。当用户询问向量化进度、是否完成时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf<String, Any>()
    ),
    timeout = null  // 无需超时（简单查询）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val bookUrl = context.bookUrl
        if (bookUrl.isBlank()) {
            return ToolResult(status = "error", name = id, message = "无法获取书籍URL")
        }

        val config = VectorConfigManager.getConfig()

        return try {
            val chunkDb = io.legado.app.help.ai.rag.TextChunkDatabase(context.appContext)
            val vectorDb = io.legado.app.help.ai.rag.VectorDb(context.appContext)

            val chunks = chunkDb.getChunksByBookUrl(bookUrl)
            val vectors = vectorDb.getByBookUrl(bookUrl)
            val isVectorized = chunks.isNotEmpty() && vectors.isNotEmpty()

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "bookUrl" to bookUrl,
                    "bookTitle" to (context.currentBook?.name ?: "未知"),
                    "isVectorized" to isVectorized,
                    "isEnabled" to config.enabled,
                    "totalChunks" to chunks.size,
                    "totalVectors" to vectors.size,
                    "message" to if (isVectorized) {
                        "该书籍已向量化，共 ${chunks.size} 个文本块"
                    } else if (!config.enabled) {
                        "向量搜索未启用，请在AI设置中启用"
                    } else {
                        "该书籍尚未向量化，请先进行向量化"
                    }
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "检查向量化状态失败: ${e.message}")
        }
    }
}

/**
 * 全文摘要工具 - 参照ReadAny53实现
 * 功能: 对书籍内容进行分块摘要
 */
class SummarizeContentTool(
    private val context: AiToolContext
) : BaseTool(
    id = "summarize_content",
    name = "内容摘要",
    description = "总结书籍或章节的主要内容。当用户询问这本书讲了什么、需要摘要时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "scope" to mapOf("type" to "string", "description" to "范围: chapter(章节), book(全书)"),
            "chapterIndex" to mapOf("type" to "integer", "description" to "章节索引"),
            "style" to mapOf("type" to "string", "description" to "风格: brief(简短), detailed(详细)")
        )
    ),
    timeout = 30000  // 30秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val scope = (input["scope"] as? String) ?: "chapter"
        val chapterIndex = (input["chapterIndex"] as? Number)?.toInt()
        val style = (input["style"] as? String) ?: "brief"

        val bookUrl = context.bookUrl
        if (bookUrl.isBlank()) {
            return ToolResult(status = "error", name = id, message = "无法获取书籍URL")
        }

        return try {
            val chunkDb = io.legado.app.help.ai.rag.TextChunkDatabase(context.appContext)
            val chapterDao = context.appDatabase.bookChapterDao

            val chunks = if (scope == "chapter" && chapterIndex != null) {
                chunkDb.getChunksByBookUrl(bookUrl).filter { it.chapterIndex == chapterIndex }
            } else {
                chunkDb.getChunksByBookUrl(bookUrl)
            }

            if (chunks.isEmpty()) {
                val content = context.chapterContent ?: ""
                if (content.isBlank()) {
                    return ToolResult(
                        status = "ok",
                        name = id,
                        data = mapOf(
                            "scope" to scope,
                            "content" to "",
                            "message" to "该书籍尚未向量化，请先进行向量化处理"
                        )
                    )
                }

                val maxTokens = if (style == "brief") 1000 else 2500
                return ToolResult(
                    status = "ok",
                    name = id,
                    data = mapOf(
                        "scope" to scope,
                        "chapterTitle" to (context.currentChapter?.title ?: "未知"),
                        "content" to content.take(maxTokens * 4),
                        "instruction" to if (style == "brief") {
                            "请用2-3句话简洁概括以上内容的主要观点。"
                        } else {
                            "请详细总结以上内容，包括主要论点、关键细节和重要结论。"
                        }
                    )
                )
            }

            val maxTokens = if (style == "brief") 1000 else 2500
            val sampledContent = chunks.take(5).joinToString("\n\n") { it.content }
                .take(maxTokens * 4)

            val chapterTitle = if (chapterIndex != null) {
                chunks.find { it.chapterIndex == chapterIndex }?.chapterTitle
            } else {
                chunks.firstOrNull()?.chapterTitle
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "scope" to scope,
                    "chapterTitle" to chapterTitle,
                    "chapterIndex" to chapterIndex,
                    "totalChunks" to chunks.size,
                    "content" to sampledContent,
                    "instruction" to if (style == "brief") {
                        "请用2-3句话简洁概括以上内容的主要观点。"
                    } else {
                        "请详细总结以上内容，包括主要论点、关键细节和重要结论。"
                    }
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "摘要失败: ${e.message}")
        }
    }
}

/**
 * 阅读统计工具 - 参照ReadAny53实现
 * 功能: 获取用户的阅读统计数据
 */
class ReadingStatsTool(
    private val context: AiToolContext
) : BaseTool(
    id = "reading_stats",
    name = "阅读统计",
    description = "获取用户的阅读统计数据，包括阅读时长、频率等。当用户询问阅读习惯、统计信息时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "period" to mapOf("type" to "string", "description" to "统计周期: day(今日), week(本周), month(本月), year(今年), all(全部)"),
            "limit" to mapOf("type" to "integer", "description" to "返回最近阅读记录数量，默认10")
        )
    ),
    timeout = null  // 无需超时（简单查询）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val period = (input["period"] as? String) ?: "all"
        val limit = (input["limit"] as? Number)?.toInt() ?: 10

        return try {
            val readRecordDao = context.appDatabase.readRecordDao
            val bookDao = context.appDatabase.bookDao

            val totalReadTime = readRecordDao.getTotalReadTime()
            var totalTime = 0L
            totalReadTime.collect { time ->
                totalTime = time ?: 0L
            }

            val allSessions = mutableListOf<ReadSession>()
            readRecordDao.getAllSessions().collect { sessions ->
                allSessions.addAll(sessions)
            }

            val (startTime, endTime) = getPeriodRange(period)
            val filteredSessions = if (startTime > 0) {
                allSessions.filter { it.startTime >= startTime && it.endTime <= endTime }
            } else {
                allSessions
            }

            val bookReadTimes = filteredSessions
                .groupBy { it.bookName }
                .mapValues { (_, sessions) -> sessions.sumOf { it.duration } }
                .entries
                .sortedByDescending { it.value }
                .take(limit)

            val bookDetails = bookReadTimes.mapNotNull { (bookName, readTime) ->
                val book = bookDao.findByName(bookName).firstOrNull()
                mapOf(
                    "bookName" to bookName,
                    "author" to (book?.author ?: "未知"),
                    "readTime" to readTime,
                    "readTimeFormatted" to formatDuration(readTime),
                    "lastRead" to (book?.lastCheckTime ?: 0L)
                )
            }

            val totalBooks = bookDao.all.size
            val finishedBooks = bookDao.all.count { it.readingStatus == 2 }
            val abandonedBooks = bookDao.all.count { it.readingStatus == 3 }

            val periodReadTime = filteredSessions.sumOf { it.duration }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "period" to period,
                    "stats" to mapOf(
                        "totalBooks" to totalBooks,
                        "finishedBooks" to finishedBooks,
                        "abandonedBooks" to abandonedBooks,
                        "totalReadTime" to totalTime,
                        "totalReadTimeFormatted" to formatDuration(totalTime),
                        "periodReadTime" to periodReadTime,
                        "periodReadTimeFormatted" to formatDuration(periodReadTime),
                        "readingDays" to filteredSessions.map {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(it.startTime))
                        }.distinct().size
                    ),
                    "topBooks" to bookDetails,
                    "instruction" to "请基于以上阅读统计数据回答用户的问题，可以进行横向对比分析。"
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "获取阅读统计失败: ${e.message}")
        }
    }

    private fun getPeriodRange(period: String): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis
        return when (period) {
            "day" -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                Pair(start, calendar.timeInMillis)
            }
            "week" -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                Pair(start, calendar.timeInMillis)
            }
            "month" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(java.util.Calendar.MONTH, 1)
                Pair(start, calendar.timeInMillis)
            }
            "year" -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(java.util.Calendar.YEAR, 1)
                Pair(start, calendar.timeInMillis)
            }
            else -> Pair(0L, now)
        }
    }
}

/**
 * 书籍阅读时长排行工具
 * 功能: 获取用户读书时长排行榜
 */
class BookReadTimeRankTool(
    private val context: AiToolContext
) : BaseTool(
    id = "book_read_time_rank",
    name = "阅读时长排行",
    description = "获取书籍阅读时长排行榜。当用户询问哪本书读得最多、阅读偏好时使用。",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "limit" to mapOf("type" to "integer", "description" to "返回数量，默认10")
        )
    ),
    timeout = null  // 无需超时（简单查询）
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        val limit = (input["limit"] as? Number)?.toInt() ?: 10

        return try {
            val readRecordDao = context.appDatabase.readRecordDao
            val bookDao = context.appDatabase.bookDao

            val allSessions = mutableListOf<ReadSession>()
            readRecordDao.getAllSessions().collect { sessions ->
                allSessions.addAll(sessions)
            }

            val bookReadTimes = allSessions
                .groupBy { it.bookName }
                .mapValues { (_, sessions) -> sessions.sumOf { it.duration } }
                .entries
                .sortedByDescending { it.value }
                .take(limit)

            val rankList = bookReadTimes.mapIndexed { index, (bookName, readTime) ->
                val book = bookDao.findByName(bookName).firstOrNull()
                mapOf(
                    "rank" to (index + 1),
                    "bookName" to bookName,
                    "author" to (book?.author ?: "未知"),
                    "readTime" to readTime,
                    "readTimeFormatted" to formatDuration(readTime),
                    "progress" to if (book != null && book.totalChapterNum > 0) {
                        (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
                    } else 0
                )
            }

            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "totalBooks" to bookReadTimes.size,
                    "ranking" to rankList,
                    "instruction" to "请基于阅读时长排行分析用户的阅读偏好和习惯。"
                )
            )
        } catch (e: Exception) {
            ToolResult(status = "error", name = id, message = "获取排行失败: ${e.message}")
        }
    }
}
