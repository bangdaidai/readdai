package io.legado.app.help.ai

import io.legado.app.data.appDb

/**
 * 基于阅读上下文的AI工具
 * 这些工具自动从ReadingContextService获取最新的阅读状态
 */

/**
 * 获取当前章节信息工具
 */
class GetCurrentChapterTool : AiTool {
    override val id = "get_current_chapter"
    override val name = "获取当前章节"
    override val description = "获取用户当前正在阅读的章节信息，包括标题、索引和位置"
    override val timeout: Long? = null

    override val inputSchema = mapOf(
        "description" to mapOf(
            "type" to "object",
            "properties" to mapOf<String, Any>()
        )
    )

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext()
            ?: return ToolResult.error("没有阅读上下文", id)

        val chapter = context.currentChapter
            ?: return ToolResult.error("无法获取当前章节信息", id)

        return ToolResult.ok(mapOf(
            "bookTitle" to context.bookTitle,
            "author" to context.author,
            "chapterIndex" to chapter.index,
            "chapterTitle" to chapter.title,
            "chapterUrl" to chapter.url,
            "position" to context.currentPosition?.let { pos ->
                mapOf(
                    "pageIndex" to pos.pageIndex,
                    "percentage" to pos.percentage,
                    "wordCount" to pos.wordCount
                )
            },
            "operationType" to context.operationType.name,
            "timestamp" to context.timestamp
        ))
    }
}

/**
 * 获取选中文本工具
 */
class GetSelectionTool : AiTool {
    override val id = "get_selection"
    override val name = "获取选中文本"
    override val description = "获取用户在阅读器中当前选中的文本内容"
    override val timeout: Long? = null

    override val inputSchema = mapOf(
        "description" to mapOf(
            "type" to "object",
            "properties" to mapOf<String, Any>()
        )
    )

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext()
            ?: return ToolResult.error("没有阅读上下文", id)

        val selection = context.selection
            ?: return ToolResult(
                status = "ok",
                name = id,
                data = mapOf(
                    "hasSelection" to false,
                    "message" to "用户当前没有选中文本",
                    "currentChapter" to (context.currentChapter?.title ?: "未知")
                )
            )

        return ToolResult.ok(mapOf(
            "hasSelection" to true,
            "selectedText" to selection.text,
            "startIndex" to selection.startIndex,
            "endIndex" to selection.endIndex,
            "chapterIndex" to selection.chapterIndex,
            "chapterTitle" to selection.chapterTitle,
            "textLength" to selection.text.length,
            "surroundingContext" to context.surroundingText
        ))
    }
}

/**
 * 获取阅读进度工具
 */
class GetReadingProgressTool : AiTool {
    override val id = "get_reading_progress"
    override val name = "获取阅读进度"
    override val description = "获取用户当前的阅读进度信息，包括百分比、页码等"
    override val timeout: Long? = null

    override val inputSchema = mapOf(
        "description" to mapOf(
            "type" to "object",
            "properties" to mapOf<String, Any>()
        )
    )

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext()
            ?: return ToolResult.error("没有阅读上下文", id)

        val position = context.currentPosition
            ?: return ToolResult.error("无法获取阅读位置", id)

        val chapter = context.currentChapter

        return ToolResult.ok(mapOf(
            "bookTitle" to context.bookTitle,
            "currentChapter" to (chapter?.title ?: "未知"),
            "chapterIndex" to (chapter?.index ?: 0),
            "progress" to mapOf(
                "percentage" to position.percentage,
                "percentageFormatted" to "${position.percentage.toInt()}%",
                "pageIndex" to position.pageIndex,
                "chapterPosition" to position.chapterPosition,
                "wordCount" to position.wordCount
            ),
            "lastActivity" to context.timestamp,
            "operationType" to context.operationType.name
        ))
    }
}

/**
 * 获取周围文本工具
 */
class GetSurroundingContextTool : AiTool {
    override val id = "get_surrounding_context"
    override val name = "获取周围文本"
    override val description = "获取用户当前阅读位置周围的文本内容"
    override val timeout: Long? = null

    override val inputSchema = mapOf(
        "description" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "includeSelection" to mapOf(
                    "type" to "boolean",
                    "description" to "是否包含选中文本（默认true）"
                )
            )
        )
    )

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext()
            ?: return ToolResult.error("没有阅读上下文", id)

        val includeSelection = input["includeSelection"] as? Boolean ?: true

        return ToolResult.ok(mapOf(
            "currentChapter" to (context.currentChapter?.title ?: "未知"),
            "currentPosition" to context.currentPosition?.let {
                mapOf(
                    "pageIndex" to it.pageIndex,
                    "percentage" to it.percentage
                )
            },
            "surroundingText" to context.surroundingText,
            "selection" to if (includeSelection) {
                context.selection?.let {
                    mapOf(
                        "text" to it.text,
                        "chapterTitle" to it.chapterTitle
                    )
                }
            } else null,
            "operationType" to context.operationType.name
        ))
    }
}

/**
 * 注册所有基于上下文的工具
 */
object ContextBasedTools {
    fun registerAll() {
        // 注意：这些工具不需要AiToolContext，因为它们直接从ReadingContextService获取数据
        
        AiToolRegistry.register(
            AiToolDefinition(
                id = "get_current_chapter",
                displayNameBuilder = { "获取当前章节" },
                descriptionBuilder = { "获取用户当前正在阅读的章节信息" },
                inputSchema = emptyMap()
            )
        ) { GetCurrentChapterTool() }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "get_selection",
                displayNameBuilder = { "获取选中文本" },
                descriptionBuilder = { "获取用户当前选中的文本" },
                inputSchema = emptyMap()
            )
        ) { GetSelectionTool() }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "get_reading_progress",
                displayNameBuilder = { "获取阅读进度" },
                descriptionBuilder = { "获取当前阅读进度信息" },
                inputSchema = emptyMap()
            )
        ) { GetReadingProgressTool() }

        AiToolRegistry.register(
            AiToolDefinition(
                id = "get_surrounding_context",
                displayNameBuilder = { "获取周围文本" },
                descriptionBuilder = { "获取当前位置周围的文本内容" },
                inputSchema = mapOf(
                    "includeSelection" to mapOf("type" to "boolean")
                )
            )
        ) { GetSurroundingContextTool() }
    }
}
