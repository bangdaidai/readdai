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

private fun AiMessagePart.toMap(): Map<String, Any> = when (this) {
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

private fun AiMessagePart.Companion.fromMap(map: Map<String, Any>): AiMessagePart? {
    return when (map["type"]?.toString()) {
        "text" -> AiMessagePart.Text(
            text = map["text"]?.toString() ?: ""
        )
        "reasoning" -> AiMessagePart.Reasoning(
            text = map["text"]?.toString() ?: ""
        )
        "tool" -> AiMessagePart.Tool(
            toolCallId = map["toolCallId"]?.toString() ?: "",
            toolName = map["toolName"]?.toString() ?: "",
            input = map["input"]?.toString() ?: "",
            output = map["output"]?.toString() ?: "",
            rawType = map["rawType"]?.toString() ?: "tool_call",
            approvalState = runCatching {
                AiToolApprovalState.valueOf(map["approvalState"]?.toString() ?: "APPROVED")
            }.getOrDefault(AiToolApprovalState.APPROVED),
            status = runCatching {
                ToolStepStatus.valueOf(map["status"]?.toString() ?: "PENDING")
            }.getOrDefault(ToolStepStatus.PENDING)
        )
        "bookResult" -> AiMessagePart.BookResult(
            bookUrl = map["bookUrl"]?.toString() ?: "",
            name = map["name"]?.toString() ?: "",
            author = map["author"]?.toString()?.takeIf { it.isNotBlank() },
            origin = map["origin"]?.toString()?.takeIf { it.isNotBlank() },
            coverPath = map["coverPath"]?.toString()?.takeIf { it.isNotBlank() },
            latestChapterTitle = map["latestChapterTitle"]?.toString()?.takeIf { it.isNotBlank() },
            currentChapterTitle = map["currentChapterTitle"]?.toString()?.takeIf { it.isNotBlank() },
            intro = map["intro"]?.toString()?.takeIf { it.isNotBlank() }
        )
        else -> null
    }
}
