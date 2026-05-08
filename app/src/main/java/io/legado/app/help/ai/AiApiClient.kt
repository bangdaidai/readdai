package io.legado.app.help.ai

import io.legado.app.help.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI API客户端
 * 参照anx53的LangChain设计，支持多服务商
 * 完整支持请求取消功能
 * 支持Function Calling/Tool Use
 */
class AiApiClient(
    private val provider: AiProviderEntity,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private var currentProvider: AiProviderEntity = provider
    private var currentCall: Call? = null
    private var currentJob: Job? = null

    fun updateProvider(provider: AiProviderEntity) {
        currentProvider = provider
    }

    private fun getCurrentApiKey(): String? {
        return currentProvider.getCurrentApiKey()?.key
    }

    fun advanceKeyIndex(): AiProviderEntity {
        val newProvider = currentProvider.advanceKeyIndex()
        currentProvider = newProvider
        return newProvider
    }

    /**
     * 取消当前请求
     */
    fun cancelRequest() {
        currentCall?.cancel()
        currentJob?.cancel()
        currentCall = null
        currentJob = null
    }

    /**
     * 检查是否有正在进行的请求
     */
    fun isRequestActive(): Boolean {
        return currentCall != null && !currentCall!!.isCanceled()
    }

    /**
     * 工具调用结果
     */
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )

    /**
     * 流式响应解析结果
     */
    data class StreamChunk(
        val content: String = "",
        val reasoningContent: String = "",
        val toolCalls: List<ToolCall> = emptyList(),
        val finishReason: String? = null
    )

    /**
     * 获取可用模型列表
     * 完善实现，支持更多服务商
     */
    suspend fun fetchModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getCurrentApiKey() ?: return@withContext Result.failure(
                IOException("No valid API key")
            )

            val (url, headers) = buildModelListUrl(currentProvider, apiKey)

            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .get()
                .build()

            currentCall = okHttpClient.newCall(request)
            val response = currentCall!!.execute()
            currentCall = null

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response"))

            val models = parseModelsResponse(body, currentProvider.protocol)
            Result.success(models)
        } catch (e: Exception) {
            currentCall = null
            Result.failure(e)
        }
    }

    /**
     * 智能提取基础URL，移除末尾的路径段
     * 参照anx53的deriveBaseUrl实现
     */
    private fun deriveBaseUrl(url: String): String {
        if (url.isBlank()) return url

        try {
            val uri = java.net.URI(url.trim())
            val path = uri.path ?: ""
            if (path.isEmpty()) return url.trimEnd('/')

            val removableSegments = setOf(
                "chat", "messages", "completions", "responses", "invoke", "openai"
            )

            val segments = path.split("/").filter { it.isNotEmpty() }.toMutableList()
            while (segments.isNotEmpty() && removableSegments.contains(segments.last().lowercase())) {
                segments.removeLast()
            }

            val newPath = if (segments.isEmpty()) "" else "/" + segments.joinToString("/")
            val newUri = java.net.URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                newPath,
                uri.query,
                uri.fragment
            )

            return newUri.toString().trimEnd('/')
        } catch (e: Exception) {
            return url.trimEnd('/')
        }
    }

    /**
     * 构建模型列表请求URL
     */
    private fun buildModelListUrl(provider: AiProviderEntity, apiKey: String): Pair<String, Map<String, String>> {
        val headers = mutableMapOf<String, String>()
        val baseUrl = deriveBaseUrl(provider.apiUrl)

        return when (provider.protocol) {
            "claude" -> {
                // Claude API
                headers["x-api-key"] = apiKey
                headers["anthropic-version"] = "2023-06-01"
                Pair("$baseUrl/models", headers)
            }
            "gemini" -> {
                // Gemini API
                headers["Authorization"] = "Bearer $apiKey"
                Pair("$baseUrl/v1beta/models", headers)
            }
            "moonshot" -> {
                // Moonshot API
                headers["Authorization"] = "Bearer $apiKey"
                Pair("$baseUrl/v1/models", headers)
            }
            "zhipu" -> {
                // 智谱 API
                headers["Authorization"] = "Bearer $apiKey"
                Pair("$baseUrl/v4/models", headers)
            }
            "ollama" -> {
                // Ollama 本地API - 不需要认证
                Pair("$baseUrl/api/tags", headers)
            }
            "lmstudio" -> {
                // LM Studio API
                headers["Authorization"] = "Bearer $apiKey"
                Pair("$baseUrl/models", headers)
            }
            else -> {
                // OpenAI兼容格式
                headers["Authorization"] = "Bearer $apiKey"
                Pair("$baseUrl/v1/models", headers)
            }
        }
    }

    /**
     * 构建聊天请求URL
     */
    private fun buildChatUrl(provider: AiProviderEntity): String {
        val baseUrl = deriveBaseUrl(provider.apiUrl)
        return when (provider.protocol) {
            "claude" -> "$baseUrl/messages"
            "gemini" -> "$baseUrl:generateContent"
            "zhipu" -> "$baseUrl/chat/completions"
            "ollama" -> "$baseUrl/api/chat"
            else -> "$baseUrl/chat/completions"
        }
    }

    /**
     * 测试连接
     * 完善实现，返回更详细的测试结果
     */
    suspend fun testConnection(): Result<TestConnectionResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getCurrentApiKey() ?: return@withContext Result.failure(
                IOException("No valid API key")
            )

            // 先尝试获取模型列表
            val modelsResult = fetchModels()

            if (modelsResult.isFailure) {
                // 如果获取模型列表失败，尝试简单的聊天测试
                return@withContext testWithSimpleChat(apiKey)
            }

            val models = modelsResult.getOrNull() ?: emptyList()

            Result.success(TestConnectionResult(
                success = true,
                message = if (models.isNotEmpty()) {
                    "连接成功，发现 ${models.size} 个模型"
                } else {
                    "连接成功"
                },
                testedModel = null,
                modelCount = models.size,
                availableModels = models
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用简单聊天测试连接
     */
    private suspend fun testWithSimpleChat(apiKey: String): Result<TestConnectionResult> {
        return try {
            val testMessages = listOf(
                ChatMessage("system", "You are a helpful assistant."),
                ChatMessage("human", "Hello, please reply with 'OK'")
            )

            val result = chatNoStream(testMessages)

            if (result.isSuccess) {
                Result.success(TestConnectionResult(
                    success = true,
                    message = "连接成功",
                    testedModel = currentProvider.model,
                    modelCount = 0,
                    availableModels = emptyList()
                ))
            } else {
                Result.failure(result.exceptionOrNull() ?: IOException("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelsResponse(body: String, protocol: String): List<String> {
        return try {
            val json = JSONObject(body)
            when (protocol) {
                "claude" -> {
                    val data = json.getJSONArray("data")
                    (0 until data.length()).map { data.getJSONObject(it).getString("id") }
                }
                "gemini" -> {
                    val models = json.getJSONArray("models")
                    (0 until models.length()).map { models.getJSONObject(it).getString("name") }
                        .map { it.substringAfterLast("/") }
                }
                "ollama" -> {
                    // Ollama格式不同
                    val models = json.getJSONArray("models")
                    (0 until models.length()).map { models.getJSONObject(it).getString("name") }
                }
                "zhipu" -> {
                    // 智谱格式
                    val data = json.getJSONArray("data")
                    (0 until data.length()).map { data.getJSONObject(it).getString("id") }
                }
                else -> {
                    // OpenAI兼容格式
                    val data = json.getJSONArray("data")
                    (0 until data.length()).map { data.getJSONObject(it).getString("id") }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 流式调用AI接口
     * @param messages 消息列表
     * @param onChunk 每收到一个chunk时的回调
     * @return 完整的响应内容
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        AiLogManager.log(AiLogManager.LogLevel.INFO, "ApiClient", "发送API请求: provider=${currentProvider.identifier}, model=${currentProvider.model}, messages=${messages.size}")
        
        try {
            val apiKey = getCurrentApiKey() ?: return@withContext Result.failure(
                IOException("No valid API key")
            )

            val requestBody = buildRequestBody(messages)
            val headers = mutableMapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )

            // 根据协议添加额外header
            when (currentProvider.protocol) {
                "claude" -> {
                    headers["x-api-key"] = apiKey
                    headers["anthropic-version"] = "2023-06-01"
                }
                "gemini" -> {
                    headers.remove("Authorization")
                    headers["Authorization"] to "Bearer $apiKey"
                }
            }

            val requestBuilder = Request.Builder()
                .url(buildChatUrl(currentProvider))

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            requestBuilder.post(requestBody)

            val request = requestBuilder.build()

            // 保存当前调用以便取消
            currentCall = okHttpClient.newCall(request)

            val response = currentCall!!.execute()

            if (!response.isSuccessful) {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "ApiClient", "API请求失败: HTTP ${response.code}, ${response.message}")
                currentCall = null
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body ?: run {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "ApiClient", "API响应为空")
                currentCall = null
                return@withContext Result.failure(
                    IOException("Empty response body")
                )
            }

            // 解析流式响应
            val content = parseStreamResponse(body.byteStream()) { chunk ->
                onChunk(chunk)
            }

            currentCall = null
            AiLogManager.log(AiLogManager.LogLevel.DEBUG, "ApiClient", "API响应成功: content长度=${content.length}")
            Result.success(content)
        } catch (e: java.lang.SecurityException) {
            // 请求被取消
            AiLogManager.log(AiLogManager.LogLevel.WARNING, "ApiClient", "请求被取消")
            currentCall = null
            Result.failure(IOException("请求已取消"))
        } catch (e: java.io.InterruptedIOException) {
            // 请求被中断（取消）
            AiLogManager.log(AiLogManager.LogLevel.WARNING, "ApiClient", "请求被中断")
            currentCall = null
            Result.failure(IOException("请求已取消"))
        } catch (e: Exception) {
            AiLogManager.log(AiLogManager.LogLevel.ERROR, "ApiClient", "API请求异常", e)
            currentCall = null
            Result.failure(e)
        }
    }

    /**
     * 构建请求体
     */
    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<ChatTool>? = null
    ): okhttp3.RequestBody {
        // 转换为Map格式的消息
        val messageMaps = messages.map { msg ->
            when (msg.type) {
                "tool" -> {
                    // 工具结果消息
                    mapOf(
                        "role" to "tool",
                        "tool_call_id" to (msg.toolCallId ?: ""),
                        "content" to msg.content
                    )
                }
                else -> {
                    // 普通消息
                    mapOf(
                        "role" to when (msg.type) {
                            "system" -> "system"
                            "ai" -> "assistant"
                            else -> "user"
                        },
                        "content" to msg.content
                    )
                }
            }
        }
        return buildRequestBodyFromMaps(messageMaps, tools)
    }

    /**
     * 从Map列表构建请求体（支持Function Calling格式）
     */
    private fun buildRequestBodyFromMaps(
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>? = null
    ): okhttp3.RequestBody {
        val jsonBody = JSONObject()

        when (currentProvider.protocol) {
            "claude" -> {
                jsonBody.put("model", currentProvider.model)
                jsonBody.put("stream", true)

                val msgs = JSONArray()
                messages.forEach { msg ->
                    val role = msg["role"]?.toString() ?: "user"
                    val content = msg["content"]?.toString() ?: ""
                    msgs.put(JSONObject().apply {
                        put("role", role)
                        put("content", content)
                    })
                }
                jsonBody.put("messages", msgs)

                if (!tools.isNullOrEmpty()) {
                    val toolsArray = JSONArray()
                    tools.forEach { tool ->
                        toolsArray.put(tool.toJson())
                    }
                    jsonBody.put("tools", toolsArray)
                }

                if (currentProvider.reasoningEffort != "auto") {
                    jsonBody.put("reasoning_effort", currentProvider.reasoningEffort)
                }
            }

            "gemini" -> {
                jsonBody.put("model", currentProvider.model)
                jsonBody.put("stream", true)

                val msgs = JSONArray()
                messages.forEach { msg ->
                    val role = msg["role"]?.toString() ?: "user"
                    val content = msg["content"]?.toString() ?: ""
                    msgs.put(JSONObject().apply {
                        put("role", when (role) {
                            "system" -> "model"
                            "ai" -> "model"
                            else -> "user"
                        })
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", content))
                        })
                    })
                }
                jsonBody.put("contents", msgs)

                if (!tools.isNullOrEmpty()) {
                    val toolsArray = JSONArray()
                    tools.forEach { tool ->
                        toolsArray.put(tool.toGeminiJson())
                    }
                    jsonBody.put("tools", toolsArray)
                }
            }

            else -> {
                // OpenAI兼容格式
                jsonBody.put("model", currentProvider.model)
                jsonBody.put("stream", true)

                val msgs = JSONArray()
                messages.forEach { msg ->
                    val role = msg["role"]?.toString() ?: "user"
                    val content = msg["content"]?.toString()

                    val msgObj = JSONObject()
                    msgObj.put("role", role)

                    // 处理工具调用
                    val toolCallId = msg["tool_call_id"]
                    if (toolCallId != null) {
                        msgObj.put("tool_call_id", toolCallId)
                        msgObj.put("content", content ?: "")
                    } else {
                        msgObj.put("content", content ?: "")
                    }

                    msgs.put(msgObj)
                }
                jsonBody.put("messages", msgs)

                if (!tools.isNullOrEmpty()) {
                    val toolsArray = JSONArray()
                    tools.forEach { tool ->
                        toolsArray.put(tool.toJson())
                    }
                    jsonBody.put("tools", toolsArray)
                    // 添加强制使用工具的参数
                    jsonBody.put("tool_choice", "auto")
                }
            }
        }

        return jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    /**
     * 构建非流式请求体
     */
    private fun buildNoStreamRequestBody(
        messages: List<ChatMessage>,
        tools: List<ChatTool>? = null
    ): okhttp3.RequestBody {
        val jsonBody = JSONObject()
        jsonBody.put("model", currentProvider.model)
        jsonBody.put("stream", false)

        val msgs = JSONArray()
        messages.forEach { msg ->
            msgs.put(JSONObject().apply {
                put("role", when (msg.type) {
                    "system" -> "system"
                    "ai" -> "assistant"
                    else -> "user"
                })
                put("content", msg.content)
            })
        }
        jsonBody.put("messages", msgs)

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            tools.forEach { tool ->
                toolsArray.put(tool.toJson())
            }
            jsonBody.put("tools", toolsArray)
        }

        return jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    /**
     * 解析SSE流式响应
     */
    private suspend fun parseStreamResponse(
        inputStream: java.io.InputStream,
        onChunk: suspend (String) -> Unit
    ): String {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        val content = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            when {
                line!!.startsWith("data:") -> {
                    val data = line!!.substring(5).trim()
                    if (data == "[DONE]") {
                        break
                    }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            if (delta != null) {
                                val contentText = delta.optString("content", "")
                                if (contentText.isNotEmpty()) {
                                    content.append(contentText)
                                    onChunk(content.toString())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误，继续读取
                    }
                }
            }
        }

        reader.close()
        return content.toString()
    }

    /**
     * 非流式调用
     */
    suspend fun chatNoStream(
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getCurrentApiKey() ?: return@withContext Result.failure(
                IOException("No valid API key")
            )

            val requestBody = buildNoStreamRequestBody(messages)

            val headers = mutableMapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )

            when (currentProvider.protocol) {
                "claude" -> {
                    headers["x-api-key"] = apiKey
                    headers["anthropic-version"] = "2023-06-01"
                }
            }

            val requestBuilder = Request.Builder()
                .url(buildChatUrl(currentProvider))

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            requestBuilder.post(requestBody)

            val request = requestBuilder.build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response"))

            val json = JSONObject(body)

            // 处理Claude响应格式
            if (currentProvider.protocol == "claude") {
                val content = json.optJSONArray("content")
                if (content != null && content.length() > 0) {
                    val text = content.getJSONObject(0).optString("text", "")
                    if (text.isNotEmpty()) {
                        return@withContext Result.success(text)
                    }
                }
                return@withContext Result.failure(IOException("No content in response"))
            }

            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = message.getString("content")
                Result.success(content)
            } else {
                Result.failure(IOException("No choices in response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 带工具的流式调用
     */
    suspend fun chatWithTools(
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getCurrentApiKey() ?: return@withContext Result.failure(
                IOException("No valid API key")
            )

            val requestBody = buildRequestBodyFromMaps(messages, tools)
            val headers = mutableMapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )

            when (currentProvider.protocol) {
                "claude" -> {
                    headers["x-api-key"] = apiKey
                    headers["anthropic-version"] = "2023-06-01"
                }
                "gemini" -> {
                    headers.remove("Authorization")
                    headers["Authorization"] to "Bearer $apiKey"
                }
            }

            val requestBuilder = Request.Builder()
                .url(buildChatUrl(currentProvider))

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            requestBuilder.post(requestBody)

            val request = requestBuilder.build()

            currentCall = okHttpClient.newCall(request)

            val response = currentCall!!.execute()

            if (!response.isSuccessful) {
                currentCall = null
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: ${response.message}")
                )
            }

            val body = response.body ?: run {
                currentCall = null
                return@withContext Result.failure(
                    IOException("Empty response body")
                )
            }

            val result = parseStreamResponseWithTools(body.byteStream()) { chunk ->
                onChunk(chunk)
            }

            currentCall = null
            Result.success(result)
        } catch (e: java.lang.SecurityException) {
            currentCall = null
            Result.failure(IOException("请求已取消"))
        } catch (e: java.io.InterruptedIOException) {
            currentCall = null
            Result.failure(IOException("请求已取消"))
        } catch (e: Exception) {
            currentCall = null
            Result.failure(e)
        }
    }

    /**
     * 解析SSE流式响应（支持tool_calls）
     */
    private suspend fun parseStreamResponseWithTools(
        inputStream: java.io.InputStream,
        onChunk: suspend (StreamChunk) -> Unit
    ): StreamResponseResult {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
        val content = StringBuilder()
        var line: String?
        var finishReason: String? = null

        // 累积tool_calls的部分数据
        val pendingToolCalls = mutableMapOf<Int, MutableMap<String, StringBuilder>>()

        while (reader.readLine().also { line = it } != null) {
            when {
                line!!.startsWith("data:") -> {
                    val data = line!!.substring(5).trim()
                    if (data == "[DONE]") {
                        break
                    }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            finishReason = choice.optString("finish_reason", null)

                            if (delta != null) {
                                // 处理文本内容
                                val contentText = delta.optString("content", "")
                                if (contentText.isNotEmpty()) {
                                    content.append(contentText)
                                    onChunk(StreamChunk(content = contentText))
                                }

                                // 处理tool_calls - 流式响应中各个部分可能分布在不同chunk
                                val toolCalls = delta.optJSONArray("tool_calls")
                                if (toolCalls != null) {
                                    for (i in 0 until toolCalls.length()) {
                                        val toolCall = toolCalls.getJSONObject(i)
                                        val index = toolCall.optInt("index", i)
                                        val func = toolCall.optJSONObject("function")

                                        // 获取或创建该index的累积数据
                                        val callData = pendingToolCalls.getOrPut(index) {
                                            mutableMapOf(
                                                "id" to StringBuilder(),
                                                "name" to StringBuilder(),
                                                "arguments" to StringBuilder()
                                            )
                                        }

                                        // 累积id
                                        val id = toolCall.optString("id", "")
                                        if (id.isNotEmpty()) {
                                            callData["id"]!!.append(id)
                                        }

                                        if (func != null) {
                                            // 累积name
                                            val name = func.optString("name", "")
                                            if (name.isNotEmpty()) {
                                                callData["name"]!!.append(name)
                                            }

                                            // 累积arguments
                                            val arguments = func.optString("arguments", "")
                                            if (arguments.isNotEmpty()) {
                                                callData["arguments"]!!.append(arguments)
                                            }
                                        }

                                        // 当有完整数据时，发送tool_call
                                        val finalId = callData["id"]!!.toString()
                                        val finalName = callData["name"]!!.toString()
                                        val finalArgs = callData["arguments"]!!.toString()

                                        if (finalName.isNotEmpty() && finalArgs.isNotEmpty()) {
                                            onChunk(StreamChunk(
                                                toolCalls = listOf(
                                                    ToolCall(
                                                        id = finalId.ifBlank { "call_${index}_${System.currentTimeMillis()}" },
                                                        name = finalName,
                                                        arguments = finalArgs
                                                    )
                                                )
                                            ))
                                            // 从pending中移除，避免重复发送
                                            pendingToolCalls.remove(index)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误，继续读取
                    }
                }
            }
        }

        reader.close()
        return StreamResponseResult(content.toString(), finishReason)
    }
}

/**
 * AI服务提供商
 */
interface AiProvider {
    val identifier: String
    val title: String

    suspend fun chat(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Result<String>
}

/**
 * OpenAI兼容服务商
 */
class OpenAICompatibleProvider(
    private val provider: AiProviderEntity
) : AiProvider {
    override val identifier = provider.identifier
    override val title = provider.title

    private val client by lazy { AiApiClient(provider) }

    override suspend fun chat(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Result<String> = client.chat(messages, onChunk)
}

/**
 * 测试连接结果
 */
data class TestConnectionResult(
    val success: Boolean,
    val message: String,
    val testedModel: String?,
    val modelCount: Int,
    val availableModels: List<String>
)

/**
 * 流式响应解析结果
 */
data class StreamResponseResult(
    val content: String,
    val finishReason: String?
)

/**
 * ChatTool - OpenAI Function Calling格式的工具定义
 */
data class ChatTool(
    val type: String = "function",
    val function: FunctionSpec
) {
    data class FunctionSpec(
        val name: String,
        val description: String,
        val parameters: ParametersSpec
    )

    data class ParametersSpec(
        val type: String = "object",
        val properties: Map<String, PropertySpec> = emptyMap(),
        val required: List<String> = emptyList()
    )

    data class PropertySpec(
        val type: String,
        val description: String = ""
    )

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("function", JSONObject().apply {
                put("name", function.name)
                put("description", function.description)
                put("parameters", JSONObject().apply {
                    put("type", function.parameters.type)
                    val props = JSONObject()
                    function.parameters.properties.forEach { (name, prop) ->
                        props.put(name, JSONObject().apply {
                            put("type", prop.type)
                            put("description", prop.description)
                        })
                    }
                    put("properties", props)
                    put("required", JSONArray(function.parameters.required))
                })
            })
        }
    }

    fun toGeminiJson(): JSONObject {
        return JSONObject().apply {
            put("name", function.name)
            put("description", function.description)
            put("parameters", JSONObject().apply {
                put("type", function.parameters.type)
                val props = JSONObject()
                function.parameters.properties.forEach { (name, prop) ->
                    props.put(name, JSONObject().apply {
                        put("type", prop.type)
                        put("description", prop.description)
                    })
                }
                put("properties", props)
                put("required", JSONArray(function.parameters.required))
            })
        }
    }
}

/**
 * 扩展AiToolDefinition转换为ChatTool
 */
fun AiToolDefinition.toChatTool(): ChatTool {
    val properties = mutableMapOf<String, ChatTool.PropertySpec>()
    val required = mutableListOf<String>()

    // 当前dai411的inputSchema格式是: { "paramName" -> mapOf("type" to "string") }
    // 需要转换为OpenAI的格式: { "properties" -> {...}, "required" -> [...] }
    inputSchema.forEach { (key, value) ->
        if (key == "description") {
            // 如果是描述字段，忽略（descriptionBuilder单独处理）
            return@forEach
        }
        if (value is Map<*, *>) {
            val propMap = value as Map<String, Any>
            val type = propMap["type"]?.toString() ?: "string"
            val desc = propMap["description"]?.toString() ?: ""
            properties[key] = ChatTool.PropertySpec(type, desc)

            // 检查是否required
            val isRequired = propMap["required"] as? Boolean ?: false
            if (isRequired) {
                required.add(key)
            }
        }
    }

    return ChatTool(
        function = ChatTool.FunctionSpec(
            name = this.id,
            description = this.descriptionBuilder(),
            parameters = ChatTool.ParametersSpec(
                properties = properties,
                required = required
            )
        )
    )
}
