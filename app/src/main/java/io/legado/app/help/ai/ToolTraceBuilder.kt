package io.legado.app.help.ai

import org.json.JSONArray
import org.json.JSONObject

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
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                approvalState = if (call.result == null) {
                    AiToolApprovalState.PENDING
                } else {
                    AiToolApprovalState.AUTO
                }
            )
        }
        return parts
    }

    fun bookResults(): List<AiMessagePart.BookResult> {
        val books = linkedMapOf<String, AiMessagePart.BookResult>()
        calls.values.mapNotNull { it.result }.forEach { result ->
            val root = runCatching { JSONObject(result) }.getOrNull() ?: return@forEach
            root.optJSONArray("books")?.let { booksArray ->
                for (i in 0 until booksArray.length()) {
                    booksArray.optJSONObject(i)?.toBookResultPart()?.let {
                        books.putIfAbsent(it.bookUrl, it)
                    }
                }
            }
            root.optJSONObject("book")?.toBookResultPart()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
            root.optJSONArray("data")?.let { dataArray ->
                for (i in 0 until dataArray.length()) {
                    dataArray.optJSONObject(i)?.toBookResultPart()?.let {
                        books.putIfAbsent(it.bookUrl, it)
                    }
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

private fun JSONObject.toBookResultPart(): AiMessagePart.BookResult? {
    val bookUrl = optString("bookUrl").takeIf { it.isNotBlank() }
        ?: optString("book_url").takeIf { it.isNotBlank() }
        ?: return null
    return AiMessagePart.BookResult(
        bookUrl = bookUrl,
        name = optString("name").ifBlank { optString("bookTitle").ifBlank { optString("title") } },
        author = optString("author"),
        origin = optString("origin") ?: optString("originName"),
        coverPath = optString("coverPath") ?: optString("coverUrl"),
        latestChapterTitle = optString("latestChapterTitle").takeIf { it.isNotBlank() },
        currentChapterTitle = optString("currentChapterTitle").takeIf { it.isNotBlank() },
        intro = optString("intro").takeIf { it.isNotBlank() }
    )
}
