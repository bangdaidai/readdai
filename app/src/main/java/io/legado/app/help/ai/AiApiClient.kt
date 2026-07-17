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
    private val okHttpClient: OkHttpClient = aiOkHttpClient
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
     * 参照archive项目的resolveModelsUrl实现，智能处理各种URL格式
     */
    private fun buildModelListUrl(provider: AiProviderEntity, apiKey: String): Pair<String, Map<String, String>> {
        val headers = mutableMapOf<String, String>()
        
        return when (provider.protocol) {
            "claude" -> {
                // Claude API
                headers["x-api-key"] = apiKey
                headers["anthropic-version"] = "2023-06-01"
                Pair(resolveModelsUrl(provider.apiUrl, "/models"), headers)
            }
            "gemini" -> {
                // Gemini API
                headers["Authorization"] = "Bearer $apiKey"
                Pair(resolveModelsUrl(provider.apiUrl, "/v1beta/models"), headers)
            }
            "moonshot" -> {
                // Moonshot API
                headers["Authorization"] = "Bearer $apiKey"
                Pair(resolveModelsUrl(provider.apiUrl, "/v1/models"), headers)
            }
            "zhipu" -> {
                // 智谱 API
                headers["Authorization"] = "Bearer $apiKey"
                Pair(resolveModelsUrl(provider.apiUrl, "/v4/models"), headers)
            }
            "ollama" -> {
                // Ollama 本地API - 不需要认证
                Pair(resolveModelsUrl(provider.apiUrl, "/api/tags"), headers)
            }
            "lmstudio" -> {
                // LM Studio API
                headers["Authorization"] = "Bearer $apiKey"
                Pair(resolveModelsUrl(provider.apiUrl, "/models"), headers)
            }
            else -> {
                // OpenAI兼容格式
                headers["Authorization"] = "Bearer $apiKey"
                Pair(resolveModelsUrl(provider.apiUrl, "/v1/models"), headers)
            }
        }
    }
    
    /**
     * 智能解析模型列表URL
     * 参照archive项目的resolveModelsUrl实现
     */
    private fun resolveModelsUrl(baseUrl: String, defaultPath: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            // 已经以目标路径结尾，直接使用
            normalized.endsWith(defaultPath) -> normalized
            // 以 /chat/completions 结尾，替换为目标路径
            normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions") + defaultPath
            // 以 /v1 结尾，根据defaultPath决定是否移除/v1前缀
            normalized.endsWith("/v1") -> {
                if (defaultPath.startsWith("/v1/")) {
                    // defaultPath 是 /v1/models，移除 /v1 前缀后变成 /models
                    "$normalized${defaultPath.removePrefix("/v1")}"
                } else {
                    // defaultPath 不以 /v1 开头，直接拼接
                    "$normalized$defaultPath"
                }
            }
            // 其他情况，直接拼接
            else -> "$normalized$defaultPath"
        }
    }

    /**
     * 构建聊天请求URL
     * 参照archive项目的resolveChatUrl实现，智能处理各种URL格式
     */
    private fun buildChatUrl(provider: AiProviderEntity): String {
        return when (provider.protocol) {
            "claude" -> resolveChatUrl(provider.apiUrl, "/messages")
            "gemini" -> resolveChatUrl(provider.apiUrl, ":generateContent")
            "zhipu" -> resolveChatUrl(provider.apiUrl, "/chat/completions")
            "ollama" -> resolveChatUrl(provider.apiUrl, "/api/chat")
            else -> resolveChatUrl(provider.apiUrl, "/chat/completions")
        }
    }
    
    /**
     * 智能解析聊天URL
     * 参照archive项目的resolveChatUrl实现
     */
    private fun resolveChatUrl(baseUrl: String, defaultPath: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith(defaultPath) -> normalized
            normalized.endsWith("/v1") && defaultPath.startsWith("/") -> "$normalized$defaultPath"
            else -> "$normalized$defaultPath"
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
    ): Result<String> {
        AiLogManager.log(AiLogManager.LogLevel.INFO, "ApiClient", "发送API请求: provider=${currentProvider.identifier}, model=${currentProvider.model}, messages=${messages.size}")
        val handler = AiProviderRegistry.handlerFor(currentProvider.protocol)
        return handler.chat(currentProvider, messages, onChunk)
    }

    /**
     * 非流式调用
     */
    suspend fun chatNoStream(
        messages: List<ChatMessage>
    ): Result<String> {
        val handler = AiProviderRegistry.handlerFor(currentProvider.protocol)
        return handler.chatNoStream(currentProvider, messages)
    }

    /**
     * 带工具的流式调用
     */
    suspend fun chatWithTools(
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult> {
        val handler = AiProviderRegistry.handlerFor(currentProvider.protocol)
        return handler.chatWithTools(currentProvider, messages, tools, onChunk)
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
    val reasoningContent: String = "",
    val toolSteps: List<ToolStep> = emptyList(),
    val finishReason: String? = null
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

    fun toAnthropicJson(): JSONObject {
        return JSONObject().apply {
            put("name", function.name)
            put("description", function.description)
            put("input_schema", JSONObject().apply {
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

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

sealed class StreamChunk {
    data class Content(val content: String) : StreamChunk()
    data class Reasoning(val content: String) : StreamChunk()
    data class ToolCallDelta(val index: Int, val name: String, val arguments: String) : StreamChunk()
    data class Finish(val reason: String) : StreamChunk()
}
