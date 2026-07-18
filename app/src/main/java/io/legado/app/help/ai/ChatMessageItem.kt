package io.legado.app.help.ai

data class ChatMessageItem(
    val role: String,
    val content: String,
    val reasoningContent: String? = "",
    val toolSteps: List<ToolStep> = emptyList(),
    val parts: List<AiMessagePart> = emptyList(),
    val parentMessageId: String? = null,
    val branchIndex: Int = 0,
    val totalBranches: Int = 1,
    val isSelected: Boolean = true,
    val assistantLabel: String? = null,
    var isExpanded: Boolean = true,
    var isReasoningExpanded: Boolean = false,
    val id: String = java.util.UUID.randomUUID().toString()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "role" to role,
        "content" to content,
        "reasoningContent" to (reasoningContent ?: ""),
        "toolSteps" to toolSteps.map { it.toMap() },
        "parts" to parts.map { it.toMap() },
        "isExpanded" to isExpanded,
        "isReasoningExpanded" to isReasoningExpanded
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ChatMessageItem {
            val parts = (map["parts"] as? List<*>)
                ?.filterIsInstance<Map<String, Any>>()
                ?.mapNotNull { AiMessagePart.fromMap(it) }
                ?: emptyList()
            return ChatMessageItem(
                role = map["role"]?.toString() ?: "user",
                content = map["content"]?.toString() ?: "",
                reasoningContent = map["reasoningContent"]?.toString() ?: "",
                toolSteps = (map["toolSteps"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any>>()
                    ?.map { ToolStep.fromMap(it) }
                    ?: emptyList(),
                parts = parts,
                isExpanded = map["isExpanded"] as? Boolean ?: true,
                isReasoningExpanded = map["isReasoningExpanded"] as? Boolean ?: false
            )
        }
    }
}

internal fun AiMessagePart.toMap(): Map<String, Any> = when (this) {
    is AiMessagePart.Text -> mapOf(
        "type" to "text",
        "text" to text
    )
    is AiMessagePart.Reasoning -> mapOf(
        "type" to "reasoning",
        "text" to text
    )
    is AiMessagePart.Tool -> mapOf(
        "type" to "tool",
        "toolCallId" to toolCallId,
        "toolName" to toolName,
        "input" to input,
        "output" to output,
        "rawType" to rawType,
        "approvalState" to approvalState.name,
        "status" to status.name
    )
    is AiMessagePart.BookResult -> mapOf(
        "type" to "bookResult",
        "bookUrl" to bookUrl,
        "name" to name,
        "author" to (author ?: ""),
        "origin" to (origin ?: ""),
        "coverPath" to (coverPath ?: ""),
        "latestChapterTitle" to (latestChapterTitle ?: ""),
        "currentChapterTitle" to (currentChapterTitle ?: ""),
        "intro" to (intro ?: "")
    )
}
