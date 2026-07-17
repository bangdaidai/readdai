package io.legado.app.help.ai

data class ChatMessageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val reasoningContent: String? = "",
    val toolSteps: List<ToolStep> = emptyList(),
    val parts: List<AiMessagePart> = emptyList(),
    val parentMessageId: String? = null,
    val branchIndex: Int = 0,
    val isSelected: Boolean = true,
    var isExpanded: Boolean = true,
    var isReasoningExpanded: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "role" to role,
        "content" to content,
        "reasoningContent" to (reasoningContent ?: ""),
        "toolSteps" to toolSteps.map { it.toMap() },
        "isExpanded" to isExpanded,
        "isReasoningExpanded" to isReasoningExpanded
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ChatMessageItem {
            return ChatMessageItem(
                role = map["role"]?.toString() ?: "user",
                content = map["content"]?.toString() ?: "",
                reasoningContent = map["reasoningContent"]?.toString() ?: "",
                toolSteps = (map["toolSteps"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any>>()
                    ?.map { ToolStep.fromMap(it) }
                    ?: emptyList(),
                isExpanded = map["isExpanded"] as? Boolean ?: true,
                isReasoningExpanded = map["isReasoningExpanded"] as? Boolean ?: false
            )
        }
    }
}
