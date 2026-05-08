package io.legado.app.help.ai

import android.content.Context
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfig
import io.legado.app.help.ai.langchain4j.LangChain4jAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiService(private val context: Context) {

    private val aiDatabase = AiDatabase.getInstance(context)
    private var promptManager: PromptManager? = null
    private var skillManager: SkillManager? = null
    
    // LangChain4j Agent服务
    private var langChain4jService: LangChain4jAgentService? = null

    private var currentProvider: AiProviderEntity? = null
    private var toolContext: AiToolContext? = null
    private var currentApiClient: AiApiClient? = null
    private var currentJob: Job? = null
    private var langChain4jJob: Job? = null  // LangChain4j请求的Job

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
        
        // 初始化LangChain4j Agent服务
        currentProvider?.let { provider ->
            try {
                AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "开始初始化LangChain4j...")
                langChain4jService = LangChain4jAgentService()
                
                // 获取第一个有效的API Key
                val apiKeyList = provider.getApiKeyList()
                AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "API Key列表大小: ${apiKeyList.size}")
                
                val firstValidKey = apiKeyList.firstOrNull { it.enabled && it.key.isNotBlank() }?.key
                    ?: throw IllegalStateException("没有有效的API Key (列表大小=${apiKeyList.size})")
                
                AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "使用API Key: ${firstValidKey.take(8)}...")
                
                langChain4jService?.initialize(
                    apiKey = firstValidKey,
                    baseUrl = provider.apiUrl,
                    modelName = provider.model
                )
                AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "LangChain4j Agent服务初始化成功")
            } catch (e: Exception) {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", "LangChain4j Agent服务初始化失败: ${e.message}", e)
                e.printStackTrace()
            }
        } ?: run {
            AiLogManager.log(AiLogManager.LogLevel.WARNING, "AiService", "currentProvider为null，跳过LangChain4j初始化")
        }

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
        toolContext = AiToolContext(
            currentBook = book,
            currentChapter = chapter,
            chapterContent = content,
            bookUrl = book?.bookUrl ?: "",
            appDatabase = appDb,
            appContext = context
        )
        toolContext?.let { AiTools.registerAll(it) }
        
        // 同时更新ReadingContextService以保持同步
        updateReadingContextFromLegacy(book, chapter, content)
    }
    
    /**
     * 从旧版上下文更新ReadingContextService
     */
    private fun updateReadingContextFromLegacy(book: Book?, chapter: BookChapter?, content: String?) {
        if (book == null) return

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
        
        // 更新toolContext（Assistant将在下次请求时自动使用新上下文）
        toolContext = AiToolContext(
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
        // 取消原生API请求
        currentApiClient?.cancelRequest()
        currentJob?.cancel()
        currentApiClient = null
        currentJob = null
        
        // 取消LangChain4j请求
        langChain4jJob?.cancel()
        langChain4jJob = null
        
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "所有AI请求已取消")
    }
    
    /**
     * 使用LangChain4j Agent聊天（支持Function Calling）
     */
    suspend fun chatWithLangChain4j(message: String): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        
        val service = langChain4jService
            ?: throw IllegalStateException("LangChain4j service not initialized")
        
        try {
            AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "使用LangChain4j Agent聊天: $message")
            
            // 获取当前上下文
            val context = toolContext ?: AiToolContext(
                currentBook = null,
                currentChapter = null,
                chapterContent = null,
                bookUrl = "",
                appDatabase = appDb,
                appContext = this@AiService.context
            )
            
            val response = service.chat(message, context)
            AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "LangChain4j响应: ${response.take(100)}...")
            response
        } catch (e: Exception) {
            // 详细记录网络错误信息
            val errorType = when {
                e.message?.contains("Unable to resolve host") == true -> "DNS解析失败"
                e.message?.contains("Connection refused") == true -> "连接被拒绝"
                e.message?.contains("timeout") == true -> "连接超时"
                e is java.net.UnknownHostException -> "未知主机"
                else -> "网络错误"
            }
            AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", 
                "LangChain4j聊天失败 [$errorType]: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 使用LangChain4j Agent流式聊天
     */
    fun chatStreamWithLangChain4j(message: String): Flow<ChatResult> = callbackFlow {
        ensureInitialized()
        
        val service = langChain4jService
            ?: run {
                trySend(ChatResult.Error("LangChain4j service not initialized"))
                close()
                return@callbackFlow
            }
        
        // 获取当前上下文
        val context = toolContext ?: AiToolContext(
            currentBook = null,
            currentChapter = null,
            chapterContent = null,
            bookUrl = "",
            appDatabase = appDb,
            appContext = this@AiService.context
        )
        
        // 保存当前Job以便取消
        langChain4jJob = coroutineContext[Job]
        
        try {
            AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "使用LangChain4j流式聊天: $message")
            
            langChain4jService!!.chatStream(message, context).collect { response ->
                // 发送工具步骤
                for (step in response.toolSteps) {
                    when (step.status) {
                        io.legado.app.help.ai.ToolStepStatus.PENDING -> {
                            trySend(ChatResult.ToolCall(step.name, step.input ?: ""))
                        }
                        io.legado.app.help.ai.ToolStepStatus.RUNNING -> {
                            trySend(ChatResult.ToolStart(step.name))
                        }
                        io.legado.app.help.ai.ToolStepStatus.SUCCESS -> {
                            trySend(ChatResult.ToolResult(step.name, step.output ?: ""))
                        }
                        io.legado.app.help.ai.ToolStepStatus.FAILED -> {
                            trySend(ChatResult.Error("工具 ${step.name} 执行失败: ${step.error}"))
                        }
                    }
                }
                
                // 发送最终内容
                trySend(ChatResult.Chunk(response.content))
                trySend(ChatResult.Success(response.content))
            }
            close()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                AiLogManager.log(AiLogManager.LogLevel.WARNING, "AiService", "LangChain4j流式聊天被取消")
            } else {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", "LangChain4j流式聊天失败: ${e.message}")
                trySend(ChatResult.Error(e.message ?: "Unknown error"))
            }
            close()
        } finally {
            langChain4jJob = null
        }
    }

    fun hasActiveRequest(): Boolean {
        return currentApiClient?.isRequestActive() == true
    }

    fun chat(
        message: String,
        session: AiChatSession? = null,
        enabledToolIds: Set<String>? = null
    ): Flow<ChatResult> = callbackFlow {
        // LangChain4j使用通用Tool执行器，所有Tool都已注册，不需要enabledToolIds参数
        AiLogManager.log(AiLogManager.LogLevel.INFO, "AiService", "开始聊天: message长度=${message.length}, session=${session?.id ?: "null"}")
        
        // 强制使用LangChain4j（参考anx/readany的实现）
        // LangChain4j内部会处理HTTP请求，不需要单独的ApiClient
        if (langChain4jService == null) {
            AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", "LangChain4j未初始化")
            trySend(ChatResult.Error("LangChain4j未初始化，请检查AI服务商配置"))
            close()
            return@callbackFlow
        }
        
        // 获取当前上下文
        val context = toolContext ?: AiToolContext(
            currentBook = null,
            currentChapter = null,
            chapterContent = null,
            bookUrl = "",
            appDatabase = appDb,
            appContext = this@AiService.context
        )
        
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "使用LangChain4j Agent模式（支持Tool调用）")
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiService", "当前上下文: book=${context.currentBook?.name ?: "null"}, chapter=${context.currentChapter?.title ?: "null"}")
        
        try {
            langChain4jService!!.chatStream(message, context).collect { response ->
                // 发送工具步骤
                for (step in response.toolSteps) {
                    when (step.status) {
                        io.legado.app.help.ai.ToolStepStatus.PENDING -> {
                            trySend(ChatResult.ToolCall(step.name, step.input ?: ""))
                        }
                        io.legado.app.help.ai.ToolStepStatus.RUNNING -> {
                            trySend(ChatResult.ToolStart(step.name))
                        }
                        io.legado.app.help.ai.ToolStepStatus.SUCCESS -> {
                            trySend(ChatResult.ToolResult(step.name, step.output ?: ""))
                        }
                        io.legado.app.help.ai.ToolStepStatus.FAILED -> {
                            trySend(ChatResult.Error("工具 ${step.name} 执行失败: ${step.error}"))
                        }
                    }
                }
                
                // 发送最终内容
                trySend(ChatResult.Chunk(response.content))
                trySend(ChatResult.Success(response.content))
            }
            close()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                AiLogManager.log(AiLogManager.LogLevel.WARNING, "AiService", "LangChain4j聊天被取消")
            } else {
                AiLogManager.log(AiLogManager.LogLevel.ERROR, "AiService", "LangChain4j聊天失败: ${e.message}", e)
                trySend(ChatResult.Error(e.message ?: "Unknown error"))
            }
            close()
        }
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
        var finalContent = ""

        // Agent循环
        while (iterations < maxIterations) {
            iterations++
            fullContent.clear()
            parser.reset()

            val pendingToolCalls = mutableListOf<ToolCallInfo>()
            var currentToolCallArgs = StringBuilder()

            // 发送请求并处理流式响应
            val result = client.chatWithTools(messages, chatTools) { chunk ->
                // 处理文本内容
                if (chunk.content.isNotEmpty()) {
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

                // 处理工具调用
                for (toolCall in chunk.toolCalls) {
                    pendingToolCalls.add(ToolCallInfo(
                        id = toolCall.id,
                        name = toolCall.name,
                        arguments = toolCall.arguments
                    ))
                    trySend(ChatResult.ToolCall(
                        name = toolCall.name,
                        arguments = toolCall.arguments
                    ))
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
            val hasToolCalls = finishReason == "tool_calls" || pendingToolCalls.isNotEmpty()

            if (!hasToolCalls) {
                // 没有工具调用，这是一个普通回复
                finalContent = contentStr
                messages.add(mapOf("role" to "assistant", "content" to contentStr))
                break
            }

            // 有工具调用，需要执行工具
            messages.add(mapOf("role" to "assistant", "content" to contentStr))

            // 执行每个工具
            for (toolCall in pendingToolCalls) {
                trySend(ChatResult.ToolStart(toolCall.name))

                val tool = toolsMap[toolCall.name]
                if (tool != null) {
                    try {
                        val argsMap = parseJsonArguments(toolCall.arguments)
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

                        trySend(ChatResult.ToolResult(toolCall.name, resultContent))
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

                        trySend(ChatResult.ToolResult(toolCall.name, errorResult))
                    }
                } else {
                    val errorResult = JSONObject().apply {
                        put("status", "error")
                        put("message", "Tool not found: ${toolCall.name}")
                    }.toString()

                    messages.add(mapOf(
                        "role" to "tool",
                        "tool_call_id" to toolCall.id,
                        "content" to errorResult
                    ))

                    trySend(ChatResult.ToolResult(toolCall.name, errorResult))
                }
            }
        }

        currentApiClient = null

        val envelope = ReasoningEnvelope.split(finalContent)
        trySend(ChatResult.Success(
            content = envelope.answerContent,
            reasoningContent = envelope.reasoningContent
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
