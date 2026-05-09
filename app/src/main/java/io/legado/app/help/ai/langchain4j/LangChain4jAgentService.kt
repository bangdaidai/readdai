package io.legado.app.help.ai.langchain4j

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.StreamingChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.service.AiServices
import io.legado.app.help.ai.AiToolContext
import io.legado.app.help.ai.ToolStep
import io.legado.app.help.ai.ToolStepStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * LangChain4j Agent服务响应
 */
data class LangChain4jResponse(
    val content: String,
    val toolSteps: List<ToolStep> = emptyList()
)

/**
 * LangChain4j Agent服务
 * 参考anx/readany的实现：每次请求都重新创建Assistant，不保存状态
 */
class LangChain4jAgentService {
    
    private var chatModel: ChatLanguageModel? = null
    private var streamingModel: StreamingChatLanguageModel? = null
    
    /**
     * 初始化模型（只创建一次）
     */
    fun initialize(apiKey: String, baseUrl: String, modelName: String) {
        // LangChain4j的OpenAiChatModel会自动在baseUrl后添加 /chat/completions
        // 所以baseUrl应该是基础URL，不包含 /v1 或 /chat
        // 例如：https://api.openai.com/v1 -> 最终请求 https://api.openai.com/v1/chat/completions
        val finalBaseUrl = baseUrl.trimEnd('/')
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "LangChain4j",
            "初始化模型: baseUrl=$finalBaseUrl, model=$modelName"
        )
        
        // 创建同步Chat模型（配置超时）
        chatModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(finalBaseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .timeout(java.time.Duration.ofSeconds(30))  // 30秒超时
            .logRequests(true)  // 启用请求日志，用于调试
            .logResponses(true)  // 启用响应日志，用于调试
            .build()
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "LangChain4j",
            "同步模型创建完成: modelName=$modelName, baseUrl=$finalBaseUrl"
        )
        
        // 创建流式Chat模型（配置超时）
        streamingModel = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(finalBaseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .timeout(java.time.Duration.ofSeconds(60))  // 60秒超时（流式需要更长时间）
            .logRequests(true)  // 启用请求日志，用于调试
            .logResponses(true)  // 启用响应日志，用于调试
            .build()
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "LangChain4j",
            "流式模型创建完成: modelName=$modelName"
        )
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "LangChain4j",
            "模型初始化完成"
        )
    }
    
    /**
     * 同步聊天（每次调用都创建新的Assistant）
     */
    fun chat(message: String, context: AiToolContext): String {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "LangChain4j",
            "创建Assistant: book=${context.currentBook?.name}, chapter=${context.currentChapter?.title}"
        )
        
        val model = chatModel ?: throw IllegalStateException("Chat model not initialized")
        
        // 验证模型配置
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "LangChain4j",
            "使用模型实例: ${model.javaClass.simpleName}"
        )
        
        try {
            // 创建Tool执行器
            val toolExecutor = LangChain4jUniversalToolExecutor(context)
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "Tool执行器创建成功"
            )
            
            // 构建System Prompt
            val systemPrompt = buildSystemPrompt(context)
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "System Prompt构建成功，长度: ${systemPrompt.length}"
            )
            
            // 创建Chat Memory
            val chatMemory = MessageWindowChatMemory.withMaxMessages(20)
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "Chat Memory创建成功"
            )
            
            // 创建Assistant（不保存，用完即弃）
            val assistant = AiServices.builder(ReadingAssistant::class.java)
                .chatLanguageModel(model)
                .tools(toolExecutor)
                .systemMessageProvider { systemPrompt }
                .chatMemory(chatMemory)
                .build()
            
            // 验证模型配置（调试用）
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "Assistant创建成功，模型类型: ${model.javaClass.simpleName}"
            )
            
            // 如果是OpenAiChatModel，检查modelName是否正确设置
            if (model is OpenAiChatModel) {
                try {
                    val modelNameField = model.javaClass.getDeclaredField("modelName")
                    modelNameField.isAccessible = true
                    val actualModelName = modelNameField.get(model) as? String
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "LangChain4j",
                        "OpenAiChatModel实际配置的modelName: $actualModelName"
                    )
                } catch (e: Exception) {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                        "LangChain4j",
                        "无法读取modelName字段: ${e.message}"
                    )
                }
            }
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "开始调用API..."
            )
            
            return assistant.chat(message)
        } catch (e: Exception) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "LangChain4j",
                "聊天过程失败: ${e.message}",
                e
            )
            throw e
        }
    }
    
    /**
     * 流式聊天（每次调用都创建新的Assistant）
     */
    fun chatStream(message: String, context: AiToolContext, sessionMessages: List<io.legado.app.help.ai.ChatMessage> = emptyList()): Flow<LangChain4jResponse> = callbackFlow {
        // 在IO线程执行网络请求，避免NetworkOnMainThreadException
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val model = chatModel ?: throw IllegalStateException("Chat model not initialized")
            
            try {
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "LangChain4j",
                    "开始聊天: message长度=${message.length}, 历史消息数=${sessionMessages.size}"
                )
                
                // 创建Tool执行器
                val toolExecutor = LangChain4jUniversalToolExecutor(context)
                
                // 构建System Prompt
                val systemPrompt = buildSystemPrompt(context)
                
                // 创建Chat Memory并加载历史消息
                val chatMemory = MessageWindowChatMemory.withMaxMessages(20)
                
                // 添加历史消息到memory中，保持上下文
                if (sessionMessages.isNotEmpty()) {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                        "LangChain4j",
                        "加载历史消息到Chat Memory"
                    )
                    
                    for (msg in sessionMessages) {
                        when (msg.type) {
                            "human" -> {
                                chatMemory.add(dev.langchain4j.data.message.UserMessage.from(msg.content))
                            }
                            "ai" -> {
                                chatMemory.add(dev.langchain4j.data.message.AiMessage.from(msg.content))
                            }
                        }
                    }
                    
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                        "LangChain4j",
                        "已加载 ${sessionMessages.size} 条历史消息"
                    )
                }
                
                // 创建Assistant（不保存，用完即弃）
                val assistant = AiServices.builder(ReadingAssistant::class.java)
                    .chatLanguageModel(model)
                    .tools(toolExecutor)
                    .systemMessageProvider { systemPrompt }
                    .chatMemory(chatMemory)
                    .build()
                
                // 执行聊天并获取响应
                var response = assistant.chat(message)
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "LangChain4j",
                    "聊天成功: response长度=${response.length}"
                )
                
                val toolSteps = mutableListOf<ToolStep>()
                var hasSentIntroMessage = false  // 标记是否已发送介绍消息
                
                // 关键修复：支持多轮工具调用循环（类似ReadAny的ReAct agent）
                var currentResponse = response
                var iteration = 0
                val maxIterations = 10  // 最多10轮工具调用，防止无限循环
                
                while (iteration < maxIterations) {
                    iteration++
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                        "LangChain4j",
                        "第 $iteration 轮工具调用检查..."
                    )
                    
                    // 检查是否包含LongCat工具调用格式
                    if (!currentResponse.contains("<longcat_tool_call>")) {
                        // 没有工具调用了，退出循环
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                            "LangChain4j",
                            "第 $iteration 轮：没有检测到工具调用，退出循环"
                        )
                        break
                    }
                    
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "LangChain4j",
                        "第 $iteration 轮：检测到LongCat工具调用格式，开始解析..."
                    )
                    
                    // 关键修复：在第一次调用工具前，先发送一条友好的提示消息
                    if (!hasSentIntroMessage) {
                        trySend(LangChain4jResponse(content = "让我先查询一下相关信息...", toolSteps = emptyList()))
                        kotlinx.coroutines.delay(200)  // 让用户看到提示
                        hasSentIntroMessage = true
                    }
                    
                    // 解析所有工具调用
                    val regex = Regex("<longcat_tool_call>\\s*(.*?)\\s*</longcat_tool_call>", RegexOption.DOT_MATCHES_ALL)
                    val matches = regex.findAll(currentResponse)
                    
                    for (match in matches) {
                        val toolCallJson = match.groupValues[1]
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                            "LangChain4j",
                            "解析到工具调用JSON: $toolCallJson"
                        )
                        
                        try {
                            val jsonObject = org.json.JSONObject(toolCallJson)
                            val toolName = jsonObject.getString("name")
                            val arguments = jsonObject.getJSONObject("arguments").toString()
                            
                            io.legado.app.help.ai.AiLogManager.log(
                                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                                "LangChain4j",
                                "执行LongCat工具: name=$toolName, args=$arguments"
                            )
                            
                            // 添加工具步骤（PENDING状态）并立即发送
                            val pendingStep = ToolStep(
                                name = toolName,
                                status = ToolStepStatus.PENDING,
                                input = arguments
                            )
                            toolSteps.add(pendingStep)
                            trySend(LangChain4jResponse(content = "", toolSteps = listOf(pendingStep)))
                            
                            // 短暂延迟让UI有时间渲染
                            kotlinx.coroutines.delay(100)
                            
                            // 更新为RUNNING状态并发送
                            val runningStep = pendingStep.copy(status = ToolStepStatus.RUNNING)
                            toolSteps[toolSteps.size - 1] = runningStep
                            trySend(LangChain4jResponse(content = "", toolSteps = listOf(runningStep)))
                            
                            // 短暂延迟让UI有时间渲染
                            kotlinx.coroutines.delay(100)
                            
                            // 执行工具
                            val result = toolExecutor.executeToolByName(toolName, arguments)
                            
                            io.legado.app.help.ai.AiLogManager.log(
                                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                                "LangChain4j",
                                "工具执行结果: $result"
                            )
                            
                            // 更新为SUCCESS状态并发送
                            val successStep = runningStep.copy(
                                status = ToolStepStatus.SUCCESS,
                                output = result
                            )
                            toolSteps[toolSteps.size - 1] = successStep
                            trySend(LangChain4jResponse(content = "", toolSteps = listOf(successStep)))
                            
                            // 短暂延迟让UI有时间渲染
                            kotlinx.coroutines.delay(100)
                            
                        } catch (e: Exception) {
                            io.legado.app.help.ai.AiLogManager.log(
                                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                                "LangChain4j",
                                "执行LongCat工具调用失败: ${e.message}",
                                e
                            )
                            
                            // 更新为FAILED状态并发送
                            if (toolSteps.isNotEmpty()) {
                                val failedStep = toolSteps.last().copy(
                                    status = ToolStepStatus.FAILED,
                                    error = e.message
                                )
                                toolSteps[toolSteps.size - 1] = failedStep
                                trySend(LangChain4jResponse(content = "", toolSteps = listOf(failedStep)))
                                
                                // 短暂延迟让UI有时间渲染
                                kotlinx.coroutines.delay(100)
                            }
                        }
                    }
                    
                    // 将所有工具结果组合，返回给AI继续决策
                    val resultsText = toolSteps.filter { it.status == ToolStepStatus.SUCCESS }
                        .map { "工具 [${it.name}] 执行结果：${it.output}" }
                        .joinToString("\n\n")
                    val followUpMessage = "已执行以下工具：\n\n$resultsText\n\n请根据这些结果决定：如果需要更多信息，可以继续调用工具；如果信息足够，请直接回答用户的问题。"
                    
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "LangChain4j",
                        "第 $iteration 轮工具执行完成，正在请求AI继续决策..."
                    )
                    
                    // 关键修复：发送空Chunk保持光标闪烁
                    trySend(LangChain4jResponse(content = "", toolSteps = emptyList()))
                    
                    // 使用同一个assistant继续对话，让AI决定是否需要再次调用工具
                    currentResponse = assistant.chat(followUpMessage)
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                        "LangChain4j",
                        "第 $iteration 轮AI响应完成: response长度=${currentResponse.length}"
                    )
                }
                
                // 检查是否达到最大迭代次数
                if (iteration >= maxIterations) {
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                        "LangChain4j",
                        "达到最大迭代次数 ($maxIterations)，强制退出"
                    )
                }
                
                // currentResponse 现在是最终回答（不包含工具调用）
                response = currentResponse
                
                // 发送最终内容
                trySend(LangChain4jResponse(content = response, toolSteps = toolSteps))
                close()
            } catch (e: Exception) {
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "LangChain4j",
                    "聊天失败: ${e.message}",
                    e
                )
                close(e)
            }
        }
        
        awaitClose { }
    }
    
    /**
     * 执行LongCat工具调用（带步骤记录）
     */
    private fun executeLongCatToolCallsWithSteps(response: String, context: AiToolContext): LangChain4jResponse {
        // 解析所有 <longcat_tool_call> {"name": "...", "arguments": {...}} </longcat_tool_call>
        val regex = Regex("<longcat_tool_call>\\s*(.*?)\\s*</longcat_tool_call>", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(response)
        
        if (!matches.any()) {
            return LangChain4jResponse(content = response, toolSteps = emptyList())
        }
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "LangChain4j",
            "检测到 ${matches.count()} 个LongCat工具调用"
        )
        
        val toolSteps = mutableListOf<ToolStep>()
        val toolResults = mutableListOf<String>()
        
        for (match in matches) {
            val toolCallJson = match.groupValues[1]
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "解析到工具调用JSON: $toolCallJson"
            )
            
            try {
                val jsonObject = org.json.JSONObject(toolCallJson)
                val toolName = jsonObject.getString("name")
                val arguments = jsonObject.getJSONObject("arguments").toString()
                
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                    "LangChain4j",
                    "执行LongCat工具: name=$toolName, args=$arguments"
                )
                
                // 添加工具步骤（PENDING状态）
                toolSteps.add(ToolStep(
                    name = toolName,
                    status = ToolStepStatus.PENDING,
                    input = arguments
                ))
                
                // 更新为RUNNING状态
                toolSteps[toolSteps.size - 1] = toolSteps.last().copy(status = ToolStepStatus.RUNNING)
                
                // 创建Tool执行器并执行
                val toolExecutor = LangChain4jUniversalToolExecutor(context)
                val result = toolExecutor.executeToolByName(toolName, arguments)
                
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "LangChain4j",
                    "工具执行结果: $result"
                )
                
                // 更新为SUCCESS状态
                toolSteps[toolSteps.size - 1] = toolSteps.last().copy(
                    status = ToolStepStatus.SUCCESS,
                    output = result
                )
                
                toolResults.add("工具 [$toolName] 执行结果：$result")
            } catch (e: Exception) {
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "LangChain4j",
                    "执行LongCat工具调用失败: ${e.message}",
                    e
                )
                
                // 更新为FAILED状态
                if (toolSteps.isNotEmpty()) {
                    toolSteps[toolSteps.size - 1] = toolSteps.last().copy(
                        status = ToolStepStatus.FAILED,
                        error = e.message
                    )
                }
                
                toolResults.add("工具执行失败：${e.message}")
            }
        }
        
        // 将所有工具结果组合，返回给AI生成最终回答
        val resultsText = toolResults.joinToString("\n\n")
        val followUpMessage = "已执行以下工具：\n\n$resultsText\n\n请根据这些结果回答用户的问题。"
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "LangChain4j",
            "将工具结果返回给AI生成最终回答"
        )
        
        val finalContent = chat(followUpMessage, context)
        
        return LangChain4jResponse(content = finalContent, toolSteps = toolSteps)
    }
    
    /**
     * 执行LongCat工具调用（旧版本，保留兼容）
     */
    private fun executeLongCatToolCalls(response: String, context: AiToolContext): String {
        // 解析所有 <longcat_tool_call> {"name": "...", "arguments": {...}} </longcat_tool_call>
        val regex = Regex("<longcat_tool_call>\\s*(.*?)\\s*</longcat_tool_call>", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(response)
        
        if (!matches.any()) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                "LangChain4j",
                "未找到有效的LongCat工具调用格式"
            )
            return response
        }
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "LangChain4j",
            "检测到 ${matches.count()} 个LongCat工具调用"
        )
        
        var finalResponse = response
        val toolResults = mutableListOf<String>()
        
        for (match in matches) {
            val toolCallJson = match.groupValues[1]
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "LangChain4j",
                "解析到工具调用JSON: $toolCallJson"
            )
            
            try {
                val jsonObject = org.json.JSONObject(toolCallJson)
                val toolName = jsonObject.getString("name")
                val arguments = jsonObject.getJSONObject("arguments").toString()
                
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                    "LangChain4j",
                    "执行LongCat工具: name=$toolName, args=$arguments"
                )
                
                // 创建Tool执行器并执行
                val toolExecutor = LangChain4jUniversalToolExecutor(context)
                val result = toolExecutor.executeToolByName(toolName, arguments)
                
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "LangChain4j",
                    "工具执行结果: $result"
                )
                
                toolResults.add("工具 [$toolName] 执行结果：$result")
            } catch (e: Exception) {
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "LangChain4j",
                    "执行LongCat工具调用失败: ${e.message}",
                    e
                )
                toolResults.add("工具执行失败：${e.message}")
            }
        }
        
        // 将所有工具结果组合，返回给AI生成最终回答
        val resultsText = toolResults.joinToString("\n\n")
        val followUpMessage = "已执行以下工具：\n\n$resultsText\n\n请根据这些结果回答用户的问题。"
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "LangChain4j",
            "将工具结果返回给AI生成最终回答"
        )
        
        return chat(followUpMessage, context)
    }
    
    /**
     * 构建System Prompt
     */
    private fun buildSystemPrompt(context: AiToolContext): String {
        val bookInfo = if (context.currentBook != null) {
            """
📖 当前阅读状态：
- 书名：${context.currentBook?.name}
- 作者：${context.currentBook?.author}
- 章节：${context.currentChapter?.title ?: "未知"}
            """.trimIndent()
        } else {
            "📚 用户正在浏览书架或查询阅读历史"
        }
        
        return """
你是“dai411 AI助手”，一个智能阅读助手。

## 你的角色
帮助读者理解、组织并享受阅读体验的智能伙伴。

## 当前上下文
$bookInfo

## 工具使用原则
1. **先收集信息** - 在回答之前，使用工具了解情况
2. **高效组合工具** - 根据需要并行或顺序使用多个工具
3. **优先使用具体工具** - 当用户在阅读时，优先使用当前相关的工具
4. **保持透明** - 简要说明你使用复杂工具组合的原因

## 可用工具
- list_books: 获取用户的书架列表
- reading_history: 获取用户的阅读历史记录
- get_current_book_info: 获取当前阅读书籍的详细信息
- search_books: 搜索书籍

## 响应策略

### 回答用户问题时：
1. **理解意图** - 用户真正想要什么？
2. **收集数据** - 使用工具收集相关信息
3. **综合分析** - 将信息片段整合成连贯的见解
4. **提供价值** - 给出可操作的建议或清晰的答案

### 沟通风格：
- **简洁而完整** - 不必要的赘述
- **基于证据** - 引用工具结果中的具体内容
- **适应上下文** - 根据阅读状态调整语气
- **合理默认** - 当有歧义时，主动询问澄清
- **语言一致** - 除非用户明确使用其他语言，否则始终用中文回复

## 重要约束
- 尊重用户隐私 - 仅通过提供的工具访问数据
- 专注于阅读相关的帮助
- 不要对不可用的数据做出假设
- 使用用户的语言进行回复

## 记住
你不只是工具执行者，而是用户的阅读伙伴。你的使命是让每次阅读都更有洞察力和乐趣。
        """.trimIndent()
    }
}

/**
 * Reading Assistant接口
 * LangChain4j会通过这个接口与AI交互
 * AI可以调用executeToolByName方法来执行Tool
 */
interface ReadingAssistant {
    fun chat(userMessage: String): String
}
