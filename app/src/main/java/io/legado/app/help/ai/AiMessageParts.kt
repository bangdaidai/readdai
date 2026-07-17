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
        val id: String,
        val name: String,
        val input: String = "",
        val output: String? = null,
        val approvalState: AiToolApprovalState = AiToolApprovalState.APPROVED,
        val status: ToolStepStatus = ToolStepStatus.PENDING
    ) : AiMessagePart

    @Serializable
    data class BookResult(
        val bookUrl: String,
        val bookName: String,
        val author: String?,
        val coverUrl: String?,
        val lastChapter: String?
    ) : AiMessagePart
}

@Serializable
enum class AiToolApprovalState {
    PENDING,
    APPROVED,
    DENIED
}
