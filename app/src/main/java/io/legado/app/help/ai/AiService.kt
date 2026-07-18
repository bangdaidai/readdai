package io.legado.app.help.ai

import android.content.Context
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: AiService? = null

        fun getInstance(context: Context): AiService {
            return instance ?: synchronized(this) {
                instance ?: AiService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val aiDatabase = AiDatabase.getInstance(context)
    private var promptManager: PromptManager? = null
    private var skillManager: SkillManager? = null

    private var currentProvider: AiProviderEntity? = null
    private var toolContext: AiToolContext? = null
    private var currentApiClient: AiApiClient? = null
    private var currentJob: Job? = null

    @Volatile
    private var isInitialized = false

    suspend fun init() {
        // 如果已经初始化，直接返回（避免重复日志）
        if (isInitialized) {
            return
        }
        
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "开始初始化AI服务")
        
        promptManager = PromptManager(context)
        skillManager = SkillManager(context)

        AiHistoryStore.init(context)
        promptManager?.initDefaultPrompts()
        skillManager?.initDefaultSkills()

        val dao = aiDatabase.aiDao()
        currentProvider = dao.getDefaultProvider() ?: dao.getAllProviders().firstOrNull()
        
        if (currentProvider != null) {
            AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "使用服务商: ${currentProvider!!.identifier}, 模型: ${currentProvider!!.model}")
        } else {
            AiLogManager.log(AiLogManager.LogLevel.WARNING, "AiService", "未找到可用的AI服务商，请先在设置中配置")
        }

        // 初始化工具上下文
        toolContext = AiToolContext(
            currentBook = null,
            currentChapter = null,
            chapterContent = null,
            bookUrl = "",
            appDatabase = appDb,
            appContext = context
        )

        // 注册基于上下文的工具
        toolContext?.let { AiTools.registerAll(it) }

        isInitialized = true
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "AI服务初始化完成")
    }

    /**
     * 确保初始化完成
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            init()
        }
    }

    /**
     * 设置工具上下文（旧方式，保留兼容性）
     * @deprecated 建议使用 ReadingContextService.updateContext()
     */
    @Deprecated("Use ReadingContextService instead", ReplaceWith("ReadingContextService.updateContext(...)"))
    fun setToolContext(book: Book?, chapter: BookChapter?, content: String?) {
        toolContext = createToolContext(book, chapter, content)
        toolContext?.let { AiTools.registerAll(it) }

        if (book != null) {
            ReadingContextService.updateContext(ReadingContextUpdate(
                bookId = book.bookUrl,
                bookTitle = book.name,
                author = book.author ?: "",
                currentChapter = chapter?.let {
                    ReadingContext.ChapterInfo(
                        index = it.index,
                        title = it.title,
                        url = it.url
                    )
                },
                surroundingText = content ?: ""
            ))
        }
    }

    private fun createToolContext(book: Book?, chapter: BookChapter?, content: String?): AiToolContext? {
        if (book == null) return null
        return AiToolContext(
            currentBook = book,
            currentChapter = chapter,
            chapterContent = content,
            bookUrl = book.bookUrl,
            appDatabase = appDb,
            appContext = context
        )
    }

    suspend fun getCurrentProvider(): AiProviderEntity? {
        val dao = aiDatabase.aiDao()
        return currentProvider ?: dao.getDefaultProvider() ?: dao.getAllProviders().firstOrNull()
    }

    suspend fun setProvider(provider: AiProviderEntity) {
        currentProvider = provider
    }

    fun cancelCurrentRequest() {
        currentApiClient?.cancelRequest()
        currentJob?.cancel()
        currentApiClient = null
        currentJob = null

        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "所有AI请求已取消")
    }
    
    fun hasActiveRequest(): Boolean {
        return currentApiClient?.isRequestActive() == true
    }

    fun chat(
        message: String,
        session: AiChatSession? = null,
        enabledToolIds: Set<String>? = null
    ): Flow<ChatResult> {
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "开始聊天: message长度=${message.length}, session=${session?.id ?: "null"}")
        return chatStream(message, session, enabledToolIds)
    }

    private fun chatStream(
        message: String,
        session: AiChatSession? = null,
        enabledToolIds: Set<String>? = null
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", "聊天失败: 未配置AI服务商")
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }
        
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "创建API客户端: ${provider.identifier}")

        val client = AiApiClient(provider)
        currentApiClient = client

        // 获取启用的工具
        val toolDefinitions = if (enabledToolIds.isNullOrEmpty()) {
            // 如果没有指定启用的工具，使用默认启用列表
            val defaultEnabledIds = AiTools.DEFAULT_ENABLED_TOOL_IDS
            AiToolRegistry.getDefinitions().filter { defaultEnabledIds.contains(it.id) }
        } else {
            AiToolRegistry.getDefinitions().filter { enabledToolIds.contains(it.id) }
        }

        // 如果没有工具，直接流式输出
        if (toolDefinitions.isEmpty()) {
            simpleStreamChat(client, message, session) { result ->
                trySend(result)
            }
            close()
            return@callbackFlow
        }

        val chatTools = toolDefinitions.map { it.toChatTool() }
        val toolsMap = toolDefinitions.associate { def ->
            def.id to (AiToolRegistry.buildTools(setOf(def.id)).firstOrNull())
        }

        // 构建消息列表
        val messages = mutableListOf<Map<String, Any>>()

        val systemPrompt = promptManager?.getSystemPrompt() ?: ""
        messages.add(mapOf("role" to "system", "content" to systemPrompt))

        session?.messages?.forEach { msg ->
            when (msg.type) {
                "ai" -> messages.add(mapOf("role" to "assistant", "content" to msg.content))
                "human" -> messages.add(mapOf("role" to "user", "content" to msg.content))
            }
        }

        messages.add(mapOf("role" to "user", "content" to message))

        val parser = ThinkTagStreamParser()
        val fullContent = StringBuilder()
        var reasoningContent = ""
        var iterations = 0
        val maxIterations = 10
        val maxConsecutiveFailures = 3
        var finalContent = ""
        var consecutiveFailures = 0
        val toolTrace = ToolTraceBuilder()

        // Agent循环
        while (iterations < maxIterations) {
            iterations++
            fullContent.clear()
            parser.reset()
            toolTrace.beginResponse()

            // 发送请求并处理流式响应
            val result = client.chatWithTools(messages, chatTools) { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        fullContent.append(chunk.content)
                        val events = parser.push(chunk.content)
                        for (event in events) {
                            when (event) {
                                is ReasoningChunk.Text -> {
                                    trySend(ChatResult.Chunk(event.content))
                                }
                                is ReasoningChunk.Reasoning -> {
                                    reasoningContent += event.content
                                    trySend(ChatResult.ReasoningChunk(event.content))
                                }
                            }
                        }
                    }
                    is StreamChunk.Reasoning -> {
                        reasoningContent += chunk.content
                        trySend(ChatResult.ReasoningChunk(chunk.content))
                    }
                    is StreamChunk.ToolCallDelta -> {
                        toolTrace.append(
                            index = chunk.index,
                            id = chunk.id,
                            name = chunk.name,
                            argumentsDelta = chunk.arguments
                        )
                        trySend(ChatResult.ToolTraceUpdate(
                            toolParts = toolTrace.toParts(),
                            bookResults = toolTrace.bookResults(),
                            traceText = toolTrace.toString()
                        ))
                    }
                    is StreamChunk.Finish -> {}
                }
            }

            // 处理剩余的解析事件
            for (event in parser.flush()) {
                when (event) {
                    is ReasoningChunk.Text -> trySend(ChatResult.Chunk(event.content))
                    is ReasoningChunk.Reasoning -> {
                        reasoningContent += event.content
                        trySend(ChatResult.ReasoningChunk(event.content))
                    }
                }
            }

            val contentStr = fullContent.toString()

            // 检查finish_reason是否为tool_calls
            val streamResult = result.getOrNull()
            val finishReason = streamResult?.finishReason
            val pendingCalls = toolTrace.pendingToolCalls()
            val hasToolCalls = finishReason == "tool_calls" || pendingCalls.isNotEmpty()

            if (!hasToolCalls) {
                // 没有工具调用，这是一个普通回复
                finalContent = contentStr
                messages.add(mapOf("role" to "assistant", "content" to contentStr))
                break
            }

            // 有工具调用，需要执行工具
            val toolCallsList = pendingCalls.map { tc ->
                mapOf(
                    "id" to tc.id,
                    "type" to "function",
                    "name" to tc.name,
                    "arguments" to tc.arguments
                )
            }
            messages.add(mapOf(
                "role" to "assistant",
                "content" to contentStr,
                "tool_calls" to toolCallsList
            ))

            // 执行每个工具
            var roundSuccessCount = 0
            var roundFailureCount = 0
            for (toolCall in pendingCalls) {
                val toolName = toolCall.name.ifBlank { toolCall.id }
                trySend(ChatResult.ToolStart(id = toolCall.id, name = toolName))

                val tool = toolsMap[toolName] ?: toolsMap[toolCall.id]
                if (tool != null) {
                    try {
                        val argsMap = parseJsonArguments(toolCall.arguments)
                        if (argsMap.isEmpty() && toolCall.arguments.isNotBlank()) {
                            throw IllegalArgumentException("Tool arguments JSON is malformed: ${toolCall.arguments.take(100)}")
                        }
                        val toolResult = tool.execute(argsMap)

                        val resultContent = when (toolResult.status) {
                            "ok" -> JSONObject().apply {
                                put("status", "success")
                                put("data", toolResult.data)
                                if (toolResult.message != null) put("message", toolResult.message)
                            }.toString()
                            else -> JSONObject().apply {
                                put("status", "error")
                                put("message", toolResult.message ?: "Unknown error")
                            }.toString()
                        }

                        messages.add(mapOf(
                            "role" to "tool",
                            "tool_call_id" to toolCall.id,
                            "content" to resultContent
                        ))

                        toolTrace.appendResult(toolCall.id, resultContent)

                        trySend(ChatResult.ToolResult(id = toolCall.id, name = toolName, result = resultContent))
                        trySend(ChatResult.ToolTraceUpdate(
                            toolParts = toolTrace.toParts(),
                            bookResults = toolTrace.bookResults(),
                            traceText = toolTrace.toString()
                        ))

                        if (toolResult.status == "ok") {
                            roundSuccessCount++
                        } else {
                            roundFailureCount++
                        }
                    } catch (e: Exception) {
                        val errorResult = JSONObject().apply {
                            put("status", "error")
                            put("message", e.message ?: "Tool execution failed")
                        }.toString()

                        messages.add(mapOf(
                            "role" to "tool",
                            "tool_call_id" to toolCall.id,
                            "content" to errorResult
                        ))

                        toolTrace.appendResult(toolCall.id, errorResult)

                        trySend(ChatResult.ToolResult(id = toolCall.id, name = toolName, result = errorResult))
                        trySend(ChatResult.ToolTraceUpdate(
                            toolParts = toolTrace.toParts(),
                            bookResults = toolTrace.bookResults(),
                            traceText = toolTrace.toString()
                        ))
                        roundFailureCount++
                    }
                } else {
                    val errorResult = JSONObject().apply {
                        put("status", "error")
                        put("message", "Tool not found: $toolName")
                    }.toString()

                    messages.add(mapOf(
                        "role" to "tool",
                        "tool_call_id" to toolCall.id,
                        "content" to errorResult
                    ))

                    toolTrace.appendResult(toolCall.id, errorResult)

                    trySend(ChatResult.ToolResult(id = toolCall.id, name = toolName, result = errorResult))
                    trySend(ChatResult.ToolTraceUpdate(
                        toolParts = toolTrace.toParts(),
                        bookResults = toolTrace.bookResults(),
                        traceText = toolTrace.toString()
                    ))
                    roundFailureCount++
                }
            }

            // 如果本轮全部工具调用失败，则连续失败计数+1，否则重置
            if (roundFailureCount > 0 && roundSuccessCount == 0) {
                consecutiveFailures++
            } else {
                consecutiveFailures = 0
            }

            // 连续失败达到阈值，退出工具调用循环
            if (consecutiveFailures >= maxConsecutiveFailures) {
                break
            }
        }

        currentApiClient = null

        val envelope = ReasoningEnvelope.split(finalContent)
        trySend(ChatResult.Success(
            content = envelope.answerContent,
            reasoningContent = envelope.reasoningContent,
            parts = toolTrace.toParts(),
            bookResults = toolTrace.bookResults()
        ))

        close()

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 简单流式聊天（无工具）
     */
    private suspend fun simpleStreamChat(
        client: AiApiClient,
        message: String,
        session: AiChatSession?,
        onResult: (ChatResult) -> Unit
    ) {
        val messages = mutableListOf<ChatMessage>()

        val systemPrompt = promptManager?.getSystemPrompt() ?: ""
        messages.add(ChatMessage("system", systemPrompt))

        session?.messages?.forEach { msg ->
            messages.add(msg)
        }

        messages.add(ChatMessage("human", message))

        val parser = ThinkTagStreamParser()
        val fullContent = StringBuilder()
        var reasoningContent = ""

        val result = client.chat(messages) { chunk ->
            fullContent.append(chunk)

            val events = parser.push(chunk)
            for (event in events) {
                when (event) {
                    is ReasoningChunk.Text -> {
                        onResult(ChatResult.Chunk(event.content))
                    }
                    is ReasoningChunk.Reasoning -> {
                        reasoningContent += event.content
                        onResult(ChatResult.ReasoningChunk(event.content))
                    }
                }
            }
        }

        for (event in parser.flush()) {
            when (event) {
                is ReasoningChunk.Text -> onResult(ChatResult.Chunk(event.content))
                is ReasoningChunk.Reasoning -> {
                    reasoningContent += event.content
                    onResult(ChatResult.ReasoningChunk(event.content))
                }
            }
        }

        result.onSuccess {
            val envelope = ReasoningEnvelope.split(fullContent.toString())
            onResult(ChatResult.Success(
                content = envelope.answerContent,
                reasoningContent = envelope.reasoningContent
            ))
        }.onFailure { error ->
            onResult(ChatResult.Error(error.message ?: "未知错误"))
        }
    }

    /**
     * 工具调用信息
     */
    data class ToolCallInfo(
        val index: Int,
        val id: String,
        val name: String,
        val arguments: String
    )

    private fun parseJsonArguments(args: String): Map<String, Any> {
        return try {
            val json = JSONObject(args)
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { key ->
                map[key] = json.get(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun chatNoStream(
        message: String,
        session: AiChatSession? = null
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }

        val client = AiApiClient(provider)
        currentApiClient = client

        val messages = mutableListOf<ChatMessage>()

        val systemPrompt = promptManager?.getSystemPrompt() ?: ""
        messages.add(ChatMessage("system", systemPrompt))

        session?.messages?.forEach { msg ->
            messages.add(msg)
        }

        messages.add(ChatMessage("human", message))

        try {
            val result = client.chatNoStream(messages)
            result.onSuccess { content ->
                val envelope = ReasoningEnvelope.split(content)
                trySend(ChatResult.Success(
                    content = envelope.answerContent,
                    reasoningContent = envelope.reasoningContent
                ))
                close()
            }.onFailure { error ->
                trySend(ChatResult.Error(error.message ?: "未知错误"))
                close()
            }
        } catch (e: Exception) {
            trySend(ChatResult.Error(e.message ?: "未知错误"))
            close()
        }

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    private fun getCurrentProviderSync(): AiProviderEntity? {
        return currentProvider
    }

    private fun buildBaseMessages(systemPrompt: String): MutableList<ChatMessage> {
        return mutableListOf(ChatMessage("system", systemPrompt))
    }

    private fun buildBaseMessageMaps(systemPrompt: String): MutableList<Map<String, Any>> {
        return mutableListOf(mapOf("role" to "system", "content" to systemPrompt))
    }

    private fun applyVariables(content: String, variables: Map<String, String>): String {
        var result = content
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }

    private suspend fun <T> withClient(block: suspend (AiApiClient) -> T): T? {
        val provider = getCurrentProviderSync() ?: return null
        val client = AiApiClient(provider)
        currentApiClient = client
        return try {
            block(client)
        } finally {
            currentApiClient = null
        }
    }

    fun executePrompt(
        promptContent: String,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> {
        return if (AppConfig.aiStreamMode) {
            executePromptStream(promptContent, variables)
        } else {
            executePromptNoStream(promptContent, variables)
        }
    }

    private fun executePromptStream(
        promptContent: String,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }

        var content = promptContent
        variables.forEach { (key, value) ->
            content = content.replace("{$key}", value)
        }

        val client = AiApiClient(provider)

        val messages = listOf(
            ChatMessage("system", promptManager?.getSystemPrompt() ?: ""),
            ChatMessage("human", content)
        )

        val result = client.chat(messages) { chunk ->
            trySend(ChatResult.Chunk(chunk))
        }

        result.onSuccess { fullContent ->
            trySend(ChatResult.Success(fullContent))
            close()
        }.onFailure { error ->
            trySend(ChatResult.Error(error.message ?: "未知错误"))
            close()
        }

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    private fun executePromptNoStream(
        promptContent: String,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }

        var content = promptContent
        variables.forEach { (key, value) ->
            content = content.replace("{$key}", value)
        }

        val client = AiApiClient(provider)

        val messages = listOf(
            ChatMessage("system", promptManager?.getSystemPrompt() ?: ""),
            ChatMessage("human", content)
        )

        try {
            val result = client.chatNoStream(messages)
            result.onSuccess { fullContent ->
                trySend(ChatResult.Success(fullContent))
                close()
            }.onFailure { error ->
                trySend(ChatResult.Error(error.message ?: "未知错误"))
                close()
            }
        } catch (e: Exception) {
            trySend(ChatResult.Error(e.message ?: "未知错误"))
            close()
        }

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPrompts(entrance: String): List<PromptDisplay> {
        return promptManager?.getPromptsForEntrance(entrance) ?: emptyList()
    }

    suspend fun savePrompt(prompt: AiPromptEntity) {
        promptManager?.savePrompt(prompt)
    }

    suspend fun deletePrompt(id: String) {
        promptManager?.deletePrompt(id)
    }

    suspend fun getSkills(entrance: String): List<SkillDisplay> {
        return skillManager?.getSkillsForEntrance(entrance) ?: emptyList()
    }

    suspend fun getAllSkills(): List<AiSkillEntity> {
        return skillManager?.getAllSkills() ?: emptyList()
    }

    fun executeSkill(
        skill: AiSkillEntity,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> {
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "执行技能: ${skill.name} (${skill.id}), 变量数=${variables.size}")
        
        return if (AppConfig.aiStreamMode) {
            AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "技能使用流式模式")
            executeSkillStream(skill, variables)
        } else {
            AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "技能使用非流式模式")
            executeSkillNoStream(skill, variables)
        }
    }

    private fun executeSkillStream(
        skill: AiSkillEntity,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }

        val instruction = skillManager?.buildSkillInstruction(skill, variables) ?: skill.instruction

        val client = AiApiClient(provider)

        val messages = listOf(
            ChatMessage("system", instruction),
            ChatMessage("human", variables["question"] ?: "请执行技能")
        )

        val result = client.chat(messages) { chunk ->
            trySend(ChatResult.Chunk(chunk))
        }

        result.onSuccess { fullContent ->
            trySend(ChatResult.Success(fullContent))
            close()
        }.onFailure { error ->
            trySend(ChatResult.Error(error.message ?: "未知错误"))
            close()
        }

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    private fun executeSkillNoStream(
        skill: AiSkillEntity,
        variables: Map<String, String> = emptyMap()
    ): Flow<ChatResult> = callbackFlow {
        val provider = getCurrentProviderSync()
            ?: run {
                trySend(ChatResult.Error("请先配置AI服务商"))
                close()
                return@callbackFlow
            }

        val instruction = skillManager?.buildSkillInstruction(skill, variables) ?: skill.instruction

        val client = AiApiClient(provider)

        val messages = listOf(
            ChatMessage("system", instruction),
            ChatMessage("human", variables["question"] ?: "请执行技能")
        )

        try {
            val result = client.chatNoStream(messages)
            result.onSuccess { fullContent ->
                trySend(ChatResult.Success(fullContent))
                close()
            }.onFailure { error ->
                trySend(ChatResult.Error(error.message ?: "未知错误"))
                close()
            }
        } catch (e: Exception) {
            trySend(ChatResult.Error(e.message ?: "未知错误"))
            close()
        }

        awaitClose {
            currentApiClient?.cancelRequest()
            currentApiClient = null
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveSkill(skill: AiSkillEntity) {
         skillManager?.saveSkill(skill)
     }

    suspend fun deleteSkill(id: String) {
        skillManager?.deleteSkill(id)
    }

    suspend fun getRecallCache(bookUrl: String): String? {
        return aiDatabase.aiDao().getRecallCache(bookUrl)?.content
    }

    suspend fun saveRecallCache(bookUrl: String, content: String, chapterIndex: Int, chapterTitle: String) {
        val cache = AiRecallCacheEntity(
            bookUrl = bookUrl,
            content = content,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle
        )
        aiDatabase.aiDao().insertRecallCache(cache)
    }

    suspend fun clearRecallCache() {
        aiDatabase.aiDao().clearRecallCache()
    }

    suspend fun testConnection(provider: AiProviderEntity): Result<String> = withContext(Dispatchers.IO) {
        val client = AiApiClient(provider)
        val testMessage = listOf(ChatMessage("human", "Hello"))
        client.chat(testMessage) { }
    }
}
