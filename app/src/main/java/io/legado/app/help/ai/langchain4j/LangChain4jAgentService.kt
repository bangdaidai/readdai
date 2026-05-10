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
                
                // ✅ 关键修复：支持多轮工具调用循环（类似ReadAny的ReAct agent）
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
                    
                    // ✅ 关键修复：在第一次调用工具前，立即发送提示消息
                    if (!hasSentIntroMessage) {
                        trySend(LangChain4jResponse(content = "让我先查询一下相关信息...", toolSteps = emptyList()))
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
                    // ✅ 关键修复：包含失败的工具步骤，让AI知道哪些工具执行失败了
                    val resultsText = toolSteps.map { step ->
                        when (step.status) {
                            ToolStepStatus.SUCCESS -> "工具 [${step.name}] 执行成功：${step.output}"
                            ToolStepStatus.FAILED -> "工具 [${step.name}] 执行失败：${step.error ?: "未知错误"}"
                            else -> "工具 [${step.name}] 状态：${step.status}"
                        }
                    }.joinToString("\n\n")
                    
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
                
                // ✅ 直接发送最终内容，不再合并提示消息（因为已经单独发送了）
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
                
                // ✅ 安全检查：确保列表不为空后再访问
                if (toolSteps.isNotEmpty()) {
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
                }
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
1. **理解问题** - 先弄清楚用户真正想要什么
2. **选择合适的工具** - 根据需要选择最合适的工具（不是所有问题都需要工具）
3. **保持透明** - 简要说明你使用工具的原因
4. **避免重复** - 不要反复调用相同的工具获取相似信息
5. **按需获取数据** - ⭐ **重要原则**：
   - ✅ **精确搜索时**：使用关键词参数，只获取相关数据
     - 例："童话保质期结局" → `rag_search(query="结局", keyword="童话保质期")`
   - ✅ **分类查询时**：使用分类/标签参数，过滤无关数据
     - 例："我有哪些科幻小说" → `list_books(keyword="科幻")`
   - ❌ **不要盲目获取所有数据**：除非用户明确要求浏览全部内容
     - 错误：用户问某本书 → 你获取整个书架
     - 正确：用户问某本书 → 你直接搜索那本书

## 可用工具

### 书架和阅读历史
- **list_books**: 获取用户的书架列表。
  - 参数：`keyword`（搜索关键词，匹配标题或作者）、`category`（分类）、`status`（阅读状态）、`sortBy`（排序方式）、`maxItems`（最大返回数量，默认20）
  - **重要**：
    - 搜索书籍时使用 `keyword` 参数，不是 `title`！例如：`{"keyword": "童话保质期"}`
    - **默认只返回最近阅读的 20 本书**，避免消耗过多 token
    - **如果需要查找特定书籍，请使用 `keyword` 参数进行搜索**，此时会返回所有匹配结果
  - **智能使用策略**：
    - ✅ **用户询问特定书名** → 使用 `keyword` 参数精确搜索
      - 例："我的书架上有童话保质期吗" → `list_books(keyword="童话保质期")`
    - ✅ **用户询问某类书籍** → 使用 `keyword` 或 `category` 参数
      - 例："我有哪些科幻小说" → `list_books(keyword="科幻")`
    - ✅ **用户浏览书架** → 不使用参数，返回最近阅读的 20 本
      - 例："我的书架上有哪些书" → `list_books()`
    - ❌ **不要盲目获取所有书籍**，除非用户明确要求
- **reading_history**: 获取用户的阅读历史记录
- **search_all_notes**: 在所有书籍中搜索笔记和高亮

### 当前阅读上下文
- **get_current_book_info**: 获取当前阅读书籍的详细信息
- **current_chapter**: 获取当前章节的内容
- **book_toc**: 获取书籍完整目录
- **search_content**: 在当前书籍中搜索指定内容
- **reading_progress**: 获取当前阅读进度
- **book_notes**: 获取当前书籍的笔记和高亮

### RAG 向量搜索（需要书籍已向量化）
- **rag_search**: 在已向量化的书籍中进行搜索，支持三种模式：
  - `mode="hybrid"` (默认): 混合搜索，结合语义和关键词，推荐用于大多数场景
  - `mode="vector"`: 纯语义搜索，适合概念性问题
  - `mode="bm25"`: 纯关键词搜索，适合精确匹配特定词汇
  - **当用户询问某本书的具体内容、情节、结局、角色等信息时，优先使用此工具**
- **rag_toc**: 获取向量化书籍的目录结构
- **rag_context**: 获取特定章节的上下文内容
- **vectorization_status**: 检查书籍的向量化状态

### 内容分析
- **extract_entities**: 从当前阅读内容中提取人物、地点、时间等实体
- **analyze_arguments**: 分析作者的论证逻辑和论据
- **find_quotes**: 查找书中的精彩引用和金句
- **compare_sections**: 比较两个章节的内容差异

### 标签管理
- **tags_list**: 获取用户创建的所有标签
- **book_tags**: 获取当前书籍的所有标签
- **apply_book_tags**: 为书籍添加或移除标签
- **manage_tags**: 创建、删除、重命名标签

### 其他
- **bookshelf_organize**: 规划书架分组重组方案
- **add_quote**: 在回答中引用书籍原文

## 响应策略

### 回答用户问题时：
1. **理解意图** - 用户真正想要什么？
2. **决定是否需要工具** - 
   - **如果用户询问某本书的具体内容、情节、结局、角色、主题等** → 优先使用 `rag_search` 在已向量化的书籍中搜索
   - **如果用户询问书架上有哪些书** → 使用 `list_books`
   - **如果用户询问阅读历史** → 使用 `reading_history`
   - **如果只是闲聊或一般性问题** → 直接回答即可
3. **重要提示**：当用户提到具体书名并询问其内容时（如“童话保质期的结局是什么”），应该使用 `rag_search` 而不是 `list_books`。`list_books` 只用于获取书架列表，不能用于查询书籍内容。
4. **RAG 搜索策略**：
   - **默认使用混合搜索** (`mode="hybrid"`)，它结合了语义搜索和关键词搜索的优势
   - **当用户询问“结局”、“结尾”、“最后”等内容时**：
     - **❌ 不要直接使用 rag_search！** 因为它返回的是碎片化的段落，无法提供完整的结局
     - **✅ 正确做法**：
       1. **第一步**：先用 `rag_toc` 获取目录，确定总章节数（例如：共82章）
       2. **第二步**：再用 `rag_context` 获取最后几章的完整内容（例如：chapterIndex=80, range=2，获取第80-82章）
       3. **第三步**：基于完整的章节内容，总结并回答用户的问颗
     - **备选方案**：如果向量化不完整，可以提示用户“这本书可能没有完整向量化，建议您重新向量化或手动阅读最后几章”
   - **当用户询问特定关键词时**：可以使用 `mode="bm25"` 进行精准的关键词匹配
   - **当用户询问概念性或语义相关问题时**：可以使用 `mode="vector"` 进行语义搜索
   - **重要**：如果 `rag_search` 返回的都是前几章的内容，说明向量化可能不完整，应该改用 `rag_toc` + `rag_context` 策略
5. **综合分析** - 将信息片段整合成连贯的见解
6. **提供价值** - 给出可操作的建议或清晰的答案

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
