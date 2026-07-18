package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.utils.GSON

class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexKeys = mutableMapOf<Int, String>()

    fun beginResponse() {
        indexKeys.clear()
    }

    fun append(index: Int?, id: String?, name: String?, argumentsDelta: String?, rawType: String = "tool_call"): String {
        val eventId = id?.takeIf { it.isNotBlank() }
        if (eventId != null && index != null) {
            indexKeys[index] = eventId
        }
        val baseId = eventId
            ?: index?.let { indexKeys[it] ?: "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
        val callId = if (eventId == null && calls[baseId]?.result != null) {
            "${baseId}_${calls.size + 1}"
        } else {
            baseId
        }
        val call = calls.getOrPut(callId) { ToolCallTrace(id = callId, rawType = rawType) }
        name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        argumentsDelta?.takeIf { it.isNotEmpty() }?.let { call.arguments.append(it) }
        if (call.rawType.isBlank()) call.rawType = rawType
        return toString()
    }

    fun appendResult(id: String, result: String): String {
        calls[id]?.result = result
        return toString()
    }

    fun pendingToolCalls(): List<AiToolCall> {
        return calls.values.filter { it.result == null }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(
                id = call.id,
                name = name,
                arguments = call.arguments.toString().ifBlank { "{}" }
            )
        }
    }

    fun toParts(): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        calls.values.forEach { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@forEach
            val hasResult = call.result != null
            val isSuccess = hasResult && runCatching {
                val json = GSON.fromJson(call.result, JsonObject::class.java)
                json.string("status") != "error"
            }.getOrDefault(true)
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                approvalState = if (hasResult) {
                    AiToolApprovalState.AUTO
                } else {
                    AiToolApprovalState.PENDING
                },
                status = when {
                    !hasResult -> ToolStepStatus.PENDING
                    isSuccess -> ToolStepStatus.SUCCESS
                    else -> ToolStepStatus.FAILED
                }
            )
        }
        return parts
    }

    fun bookResults(): List<AiMessagePart.BookResult> {
        val books = linkedMapOf<String, AiMessagePart.BookResult>()
        calls.values.mapNotNull { it.result }.forEach { result ->
            val root = runCatching {
                GSON.fromJson(result, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultPart()?.let {
                    books.putIfAbsent(it.bookUrl, it)
                }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultPart()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
            root.getAsJsonArrayOrNull("data")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultPart()?.let {
                    books.putIfAbsent(it.bookUrl, it)
                }
            }
        }
        return books.values.toList()
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                append(call.name.ifBlank { call.rawType.ifBlank { call.id } })
                append('\n')
                append("ID: ")
                append(call.id)
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append(call.arguments)
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var rawType: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var result: String? = null
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class PendingToolRun(
    val request: AiGenerateRequest,
    val fullText: String,
    val fullReasoning: String,
    val toolTrace: ToolTraceBuilder,
    val toolCalls: List<AiToolCall>,
    val assistantTextStart: Int,
    val round: Int
)

data class AiGenerateRequest(
    val messages: List<Any> = emptyList(),
    val tools: List<Any> = emptyList(),
    val provider: String = "",
    val model: String = ""
)

internal fun JsonObject.toBookResultPart(): AiMessagePart.BookResult? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() }
        ?: string("book_url")?.takeIf { it.isNotBlank() }
        ?: return null
    return AiMessagePart.BookResult(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

internal fun JsonObject.string(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
    return get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }
}

internal fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

internal fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
}

internal fun String.truncateToolOutput(): String {
    if (length <= 8000) return this
    return take(8000) + "\n\n[...truncated from ${length} chars]"
}
