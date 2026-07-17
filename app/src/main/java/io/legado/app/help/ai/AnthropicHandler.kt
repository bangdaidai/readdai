package io.legado.app.help.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AnthropicHandler : BaseProtocolHandler() {

    override val protocols = setOf("claude")

    override suspend fun chat(
        provider: AiProviderEntity,
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val body = buildStreamingBody(provider, messages)
            val headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val inputStream = response.body?.byteStream()
                ?: throw java.io.IOException("Empty response body")
            parseClaudeStream(inputStream, onChunk)
        }
    }

    override suspend fun chatNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val systemPrompt = messages.filter { it.type == "system" }
                .joinToString("\n\n") { it.content }.takeIf { it.isNotBlank() }
            val chatMessages = messages.filter { it.type != "system" }

            val jsonBody = JSONObject().apply {
                put("model", provider.model)
                put("max_tokens", 2048)
                systemPrompt?.let { put("system", it) }
                val msgs = JSONArray()
                chatMessages.forEach { msg ->
                    msgs.put(JSONObject().apply {
                        put("role", if (msg.type == "ai") "assistant" else msg.type)
                        put("content", msg.content)
                    })
                }
                put("messages", msgs)
            }
            val headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), jsonBody.toString(), headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val body = response.body?.string() ?: throw java.io.IOException("Empty response")
            val json = JSONObject(body)
            val content = json.optJSONArray("content")
            if (content != null && content.length() > 0) {
                val text = content.getJSONObject(0).optString("text", "")
                if (text.isNotEmpty()) return@executeWithRetry text
            }
            throw java.io.IOException("No content in response")
        }
    }

    override suspend fun chatWithTools(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val body = buildToolsBody(provider, messages, tools)
            val headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val inputStream = response.body?.byteStream()
                ?: throw java.io.IOException("Empty response body")
            parseClaudeToolStream(inputStream, onChunk)
        }
    }

    private fun buildStreamingBody(provider: AiProviderEntity, messages: List<ChatMessage>): String {
        val systemPrompt = messages.filter { it.type == "system" }
            .joinToString("\n\n") { it.content }.takeIf { it.isNotBlank() }
        val chatMessages = messages.filter { it.type != "system" }
        return JSONObject().apply {
            put("model", provider.model)
            put("max_tokens", 4096)
            put("stream", true)
            systemPrompt?.let { put("system", it) }
            val msgs = JSONArray()
            chatMessages.forEach { msg ->
                msgs.put(JSONObject().apply {
                    put("role", if (msg.type == "ai") "assistant" else msg.type)
                    put("content", msg.content)
                })
            }
            put("messages", msgs)
        }.toString()
    }

    private fun buildToolsBody(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>
    ): String {
        return JSONObject().apply {
            put("model", provider.model)
            put("max_tokens", 4096)
            put("stream", true)
            val msgs = JSONArray()
            messages.forEach { msg ->
                msgs.put(JSONObject(msg))
            }
            put("messages", msgs)
            if (tools.isNotEmpty()) {
                val toolsArray = JSONArray()
                tools.forEach { toolsArray.put(it.toAnthropicJson()) }
                put("tools", toolsArray)
            }
        }.toString()
    }

    private suspend fun parseClaudeStream(
        inputStream: java.io.InputStream,
        onChunk: suspend (String) -> Unit
    ): String {
        val content = StringBuilder()
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (!currentLine.startsWith("data:")) continue
            val data = currentLine.removePrefix("data:").trim()
            if (data.isEmpty()) continue
            try {
                val json = JSONObject(data)
                when (json.optString("type", "")) {
                    "content_block_delta" -> {
                        val delta = json.optJSONObject("delta")
                        val text = delta?.optString("text", "") ?: ""
                        if (text.isNotEmpty()) {
                            content.append(text)
                            onChunk(text)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.close()
        return content.toString()
    }

    private suspend fun parseClaudeToolStream(
        inputStream: java.io.InputStream,
        onChunk: suspend (StreamChunk) -> Unit
    ): StreamResponseResult {
        val content = StringBuilder()
        val reasoningContent = StringBuilder()
        val toolSteps = mutableListOf<ToolStep>()
        var currentToolStep: ToolStep? = null
        var currentToolIndex = 0

        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (!currentLine.startsWith("data:")) continue
            val data = currentLine.removePrefix("data:").trim()
            if (data.isEmpty()) continue
            try {
                val json = JSONObject(data)
                when (json.optString("type", "")) {
                    "content_block_start" -> {
                        val block = json.optJSONObject("content_block")
                        if (block?.optString("type") == "tool_use") {
                            val id = block.optString("id", "")
                            val name = block.optString("name", "")
                            currentToolStep = ToolStep(
                                id = id,
                                name = name,
                                input = "",
                                status = ToolStepStatus.RUNNING
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val delta = json.optJSONObject("delta")
                        when (delta?.optString("type", "")) {
                            "text_delta" -> {
                                val text = delta.optString("text", "")
                                if (text.isNotEmpty()) {
                                    content.append(text)
                                    onChunk(StreamChunk.Content(text))
                                }
                            }
                            "input_json_delta" -> {
                                val partialJson = delta.optString("partial_json", "")
                                if (partialJson.isNotEmpty()) {
                                    currentToolStep = currentToolStep?.copy(
                                        input = currentToolStep.input + partialJson
                                    )
                                    onChunk(StreamChunk.ToolCallDelta(currentToolIndex, currentToolStep?.name ?: "", partialJson))
                                }
                            }
                        }
                    }
                    "content_block_stop" -> {
                        currentToolStep?.let {
                            toolSteps.add(it.copy(status = ToolStepStatus.PENDING))
                            currentToolIndex++
                            currentToolStep = null
                        }
                    }
                    "message_delta" -> {
                        val stopReason = json.optJSONObject("delta")?.optString("stop_reason", "")
                        if (stopReason?.isNotEmpty() == true) {
                            onChunk(StreamChunk.Finish(stopReason))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.close()
        return StreamResponseResult(
            content.toString(),
            reasoningContent.toString(),
            toolSteps
        )
    }
}
