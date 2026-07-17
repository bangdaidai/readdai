package io.legado.app.help.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiHandler : BaseProtocolHandler() {

    override val protocols = setOf("gemini")

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
            parseGeminiStream(inputStream, onChunk)
        }
    }

    override suspend fun chatNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        executeWithRetry(provider) { apiKey ->
            val body = buildNoStreamBody(provider, messages)
            val headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            val noStreamUrl = buildChatUrl(provider)
                .replace(":streamGenerateContent?alt=sse", ":generateContent")
            val request = newRequest(noStreamUrl, body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val responseBody = response.body?.string() ?: throw java.io.IOException("Empty response")
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return@executeWithRetry parts.getJSONObject(0).optString("text", "")
                }
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
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            val request = newRequest(buildChatUrl(provider), body, headers)
            val response = aiOkHttpClient.newCall(request).execute().ensureSuccessful()
            val inputStream = response.body?.byteStream()
                ?: throw java.io.IOException("Empty response body")
            parseGeminiToolStream(inputStream, onChunk)
        }
    }

    private fun buildStreamingBody(provider: AiProviderEntity, messages: List<ChatMessage>): String {
        return JSONObject().apply {
            val contents = JSONArray()
            messages.forEach { msg ->
                if (msg.type == "system") return@forEach
                contents.put(JSONObject().apply {
                    put("role", if (msg.type == "ai") "model" else "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", msg.content))
                    })
                })
            }
            put("contents", contents)
        }.toString()
    }

    private fun buildNoStreamBody(provider: AiProviderEntity, messages: List<ChatMessage>): String {
        return buildStreamingBody(provider, messages)
    }

    private fun buildToolsBody(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>
    ): String {
        return JSONObject().apply {
            val contents = JSONArray()
            messages.forEach { msg ->
                contents.put(JSONObject(msg))
            }
            put("contents", contents)
            if (tools.isNotEmpty()) {
                val toolsArray = JSONArray()
                tools.forEach { toolsArray.put(it.toGeminiJson()) }
                put("tools", toolsArray)
            }
        }.toString()
    }

    private suspend fun parseGeminiStream(
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
                val jsonArray = JSONArray(data)
                for (i in 0 until jsonArray.length()) {
                    val chunk = jsonArray.getJSONObject(i)
                    val candidates = chunk.optJSONArray("candidates") ?: continue
                    for (j in 0 until candidates.length()) {
                        val candidate = candidates.getJSONObject(j)
                        val contentObj = candidate.optJSONObject("content") ?: continue
                        val parts = contentObj.optJSONArray("parts") ?: continue
                        for (k in 0 until parts.length()) {
                            val text = parts.getJSONObject(k).optString("text", "")
                            if (text.isNotEmpty()) {
                                content.append(text)
                                onChunk(text)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.close()
        return content.toString()
    }

    private suspend fun parseGeminiToolStream(
        inputStream: java.io.InputStream,
        onChunk: suspend (StreamChunk) -> Unit
    ): StreamResponseResult {
        val content = StringBuilder()
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
                val jsonArray = JSONArray(data)
                for (i in 0 until jsonArray.length()) {
                    val chunk = jsonArray.getJSONObject(i)
                    val candidates = chunk.optJSONArray("candidates") ?: continue
                    for (j in 0 until candidates.length()) {
                        val candidate = candidates.getJSONObject(j)
                        val contentObj = candidate.optJSONObject("content") ?: continue
                        val parts = contentObj.optJSONArray("parts") ?: continue
                        for (k in 0 until parts.length()) {
                            val part = parts.getJSONObject(k)
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                content.append(text)
                                onChunk(StreamChunk.Content(text))
                            }
                            val functionCall = part.optJSONObject("functionCall")
                            if (functionCall != null) {
                                val name = functionCall.optString("name", "")
                                val args = functionCall.optJSONObject("args")?.toString() ?: "{}"
                                if (currentToolStep == null || currentToolStep.name != name) {
                                    currentToolStep?.let { toolSteps.add(it) }
                                    currentToolStep = ToolStep(
                                        id = name,
                                        name = name,
                                        input = args,
                                        status = ToolStepStatus.RUNNING
                                    )
                                }
                                onChunk(StreamChunk.ToolCallDelta(currentToolIndex, name, args))
                            }
                        }
                    }
                    val finishReason = candidate.optString("finishReason", "")
                    if (finishReason.isNotEmpty()) {
                        currentToolStep?.let {
                            toolSteps.add(it.copy(status = ToolStepStatus.PENDING))
                            currentToolIndex++
                            currentToolStep = null
                        }
                        onChunk(StreamChunk.Finish(finishReason))
                    }
                }
            } catch (_: Exception) {
            }
        }
        reader.close()
        currentToolStep?.let { toolSteps.add(it.copy(status = ToolStepStatus.PENDING)) }
        return StreamResponseResult(content.toString(), "", toolSteps)
    }
}
