package io.legado.app.help.ai

import kotlinx.serialization.Serializable

@Serializable
sealed interface AiMessagePart {

    @Serializable
    data class Text(
        val text: String
    ) : AiMessagePart

    @Serializable
    data class Reasoning(
        val text: String
    ) : AiMessagePart

    @Serializable
    data class Tool(
        val toolCallId: String = "",
        val toolName: String = "",
        val input: String = "",
        val output: String = "",
        val rawType: String = "tool_call",
        val approvalState: AiToolApprovalState = AiToolApprovalState.APPROVED,
        val status: ToolStepStatus = ToolStepStatus.PENDING
    ) : AiMessagePart {
        constructor(
            index: Int,
            id: String,
            name: String,
            input: String,
            output: String?,
            approvalState: AiToolApprovalState,
            status: ToolStepStatus
        ) : this(
            toolCallId = id,
            toolName = name,
            input = input,
            output = output ?: "",
            approvalState = approvalState,
            status = status
        )
    }

    @Serializable
    data class BookResult(
        val bookUrl: String,
        val name: String = "",
        val author: String? = null,
        val origin: String? = null,
        val coverPath: String? = null,
        val latestChapterTitle: String? = null,
        val currentChapterTitle: String? = null,
        val intro: String? = null
    ) : AiMessagePart {
        constructor(
            bookUrl: String,
            bookName: String,
            author: String?,
            coverUrl: String?,
            lastChapter: String?
        ) : this(
            bookUrl = bookUrl,
            name = bookName,
            author = author,
            coverPath = coverUrl,
            latestChapterTitle = lastChapter
        )
    }
}

@Serializable
enum class AiToolApprovalState {
    PENDING,
    APPROVED,
    DENIED,
    AUTO
}
