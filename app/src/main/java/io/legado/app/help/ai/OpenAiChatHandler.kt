package io.legado.app.help.ai

import io.legado.app.help.ai.AiEntities.ChatMessage
import io.legado.app.help.ai.AiEntities.ChatTool
import io.legado.app.help.ai.AiEntities.StreamChunk
import io.legado.app.help.ai.AiEntities.StreamResponseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiChatHandler : BaseProtocolHandler() {

    override val protocols = setOf("openai")

    override suspend fun chat(
        provider: AiProviderEntity,
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val body = buildStreamingBody(provider, messages)
            val headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val inputStream = response.body?.byteStream()
                ?: throw java.io.IOException("Empty response body")
            parseSseStream(inputStream, onChunk)
        }
    }

    override suspend fun chatNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val jsonBody = JSONObject().apply {
                put("model", provider.model)
                put("stream", false)
                put("messages", openAiMessages(messages))
            }
            val headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), jsonBody.toString(), headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val body = response.body?.string() ?: throw java.io.IOException("Empty response")
            val json = JSONObject(body)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                throw java.io.IOException("No choices in response")
            }
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
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val inputStream = response.body?.byteStream()
                ?: throw java.io.IOException("Empty response body")
            parseToolStream(inputStream, onChunk)
        }
    }

    private fun buildStreamingBody(provider: AiProviderEntity, messages: List<ChatMessage>): String {
        return JSONObject().apply {
            put("model", provider.model)
            put("stream", true)
            put("messages", openAiMessages(messages))
        }.toString()
    }

    private fun buildToolsBody(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>
    ): String {
        val jsonBody = JSONObject().apply {
            put("model", provider.model)
            put("stream", true)
        }
        val msgs = JSONArray()
        messages.forEach { msg ->
            val role = msg["role"]?.toString() ?: "user"
            val content = msg["content"]?.toString()
            val msgObj = JSONObject().apply {
                put("role", role)
                val toolCallId = msg["tool_call_id"]
                if (toolCallId != null) {
                    put("tool_call_id", toolCallId)
                    put("content", content ?: "")
                } else {
                    put("content", content ?: "")
                }
            }
            msgs.put(msgObj)
        }
        jsonBody.put("messages", msgs)
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            tools.forEach { toolsArray.put(it.toJson()) }
            jsonBody.put("tools", toolsArray)
            jsonBody.put("tool_choice", "auto")
        }
        return jsonBody.toString()
    }

    private suspend fun parseToolStream(
        inputStream: java.io.InputStream,
        onChunk: suspend (StreamChunk) -> Unit
    ): StreamResponseResult {
        val content = StringBuilder()
        val reasoningContent = StringBuilder()
        val toolSteps = mutableListOf<AiEntities.ToolStep>()
        var currentToolStep: AiEntities.ToolStep? = null

        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (!currentLine.startsWith("data:")) continue
            val data = currentLine.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) continue

            try {
                val json = JSONObject(data)
                val choices = json.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue

                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                val contentText = delta.optString("content", "")
                if (contentText.isNotEmpty()) {
                    content.append(contentText)
                    onChunk(StreamChunk.Content(contentText))
                }

                val reasoningText = delta.optString("reasoning_content", "")
                    ?: delta.optJSONObject("thinking")?.optString("thinking", "") ?: ""
                if (reasoningText.isNotEmpty()) {
                    reasoningContent.append(reasoningText)
                    onChunk(StreamChunk.Reasoning(reasoningText))
                }

                val toolCalls = delta.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    val toolCall = toolCalls.getJSONObject(0)
                    val index = toolCall.optInt("index", 0)
                    val id = toolCall.optString("id", "")
                    val function = toolCall.optJSONObject("function")
                    val name = function?.optString("name", "") ?: ""
                    val args = function?.optString("arguments", "") ?: ""

                    if (currentToolStep == null || currentToolStep.id != id) {
                        currentToolStep?.let { toolSteps.add(it) }
                        currentToolStep = AiEntities.ToolStep(
                            id = id,
                            name = name,
                            input = args,
                            status = AiEntities.ToolStep.Status.RUNNING
                        )
                    } else {
                        currentToolStep = currentToolStep.copy(
                            input = currentToolStep.input + args
                        )
                    }
                    onChunk(StreamChunk.ToolCallDelta(index, name, args))
                }

                val finishReason = choices.getJSONObject(0).optString("finish_reason", "")
                if (finishReason.isNotEmpty() && finishReason != "null") {
                    currentToolStep?.let {
                        toolSteps.add(it.copy(status = AiEntities.ToolStep.Status.PENDING))
                        currentToolStep = null
                    }
                    onChunk(StreamChunk.Finish(finishReason))
                }
            } catch (_: Exception) {
            }
        }
        reader.close()
        currentToolStep?.let { toolSteps.add(it.copy(status = AiEntities.ToolStep.Status.PENDING)) }
        return StreamResponseResult(
            content.toString(),
            reasoningContent.toString(),
            toolSteps
        )
    }
}
