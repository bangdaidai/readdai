package io.legado.app.help.ai

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key)
        when (value) {
            is JSONObject -> map[key] = value.toMap()
            is JSONArray -> map[key] = value.toList()
            else -> map[key] = value
        }
    }
    return map
}

private fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        val item = get(i)
        when (item) {
            is JSONObject -> list.add(item.toMap())
            is JSONArray -> list.add(item.toList())
            else -> list.add(item)
        }
    }
    return list
}

/**
 * AI协议类型
 */
enum class AiProtocol {
    openai,
    claude,
    gemini
}

/**
 * 推理强度
 */
enum class AiReasoningEffort {
    auto,
    low,
    medium,
    high
}

/**
 * 聊天显示模式
 */
enum class AiChatDisplayMode {
    adaptive,
    split,
    popup
}

/**
 * 面板位置
 */
enum class AiPanelPosition {
    bottom,
    right
}

/**
 * API密钥
 */
data class AiApiKey(
    val id: String,
    val key: String,
    val enabled: Boolean = true,
    val label: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): Map<String, Any> = buildMap {
        put("id", id)
        put("key", key)
        put("enabled", enabled)
        label?.let { put("label", it) }
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: Map<String, Any>): AiApiKey {
            return AiApiKey(
                id = json["id"]?.toString() ?: "",
                key = json["key"]?.toString() ?: "",
                enabled = json["enabled"] != false,
                label = json["label"]?.toString(),
                createdAt = (json["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * AI服务商配置实体
 * 参照anx53的AiProvider设计
 */
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey
    val identifier: String,
    val title: String,
    val apiUrl: String,
    val apiKeys: String = "[]",  // JSON数组存储多个API Key
    val model: String,
    val availableModels: String = "[]",  // JSON数组存储可用模型列表
    val protocol: String = "openai",  // openai, claude, gemini
    val reasoningEffort: String = "auto",  // auto, low, medium, high
    val headers: String = "",   // 自定义请求头(JSON字符串)
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val isBuiltin: Boolean = true,
    val keyIndex: Int = 0,  // 当前使用的Key索引，用于轮换
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getApiKeyList(): List<AiApiKey> {
        return try {
            val jsonList = org.json.JSONArray(apiKeys)
            (0 until jsonList.length()).map { i ->
                val obj = jsonList.getJSONObject(i)
                AiApiKey.fromJson(obj.toMap())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setApiKeyList(keys: List<AiApiKey>): AiProviderEntity {
        val jsonArray = org.json.JSONArray()
        keys.forEach { jsonArray.put(org.json.JSONObject(it.toJson())) }
        return copy(apiKeys = jsonArray.toString())
    }

    fun hasValidKey(): Boolean {
        return getApiKeyList().any { it.enabled && it.key.isNotBlank() }
    }

    /**
     * 获取当前应该使用的API Key（支持轮换）
     */
    fun getCurrentApiKey(): AiApiKey? {
        val keys = getApiKeyList().filter { it.enabled && it.key.isNotBlank() }
        if (keys.isEmpty()) return null
        val index = keyIndex % keys.size
        return keys.getOrNull(index)
    }

    /**
     * 推进Key索引（用于失败后轮换）
     */
    fun advanceKeyIndex(): AiProviderEntity {
        val keys = getApiKeyList().filter { it.enabled && it.key.isNotBlank() }
        if (keys.isEmpty()) return this
        return copy(keyIndex = (keyIndex + 1) % keys.size)
    }
    
    /**
     * 获取可用模型列表
     */
    fun parseAvailableModels(): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(availableModels)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getString(i)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 设置可用模型列表
     */
    fun setAvailableModels(models: List<String>): AiProviderEntity {
        val jsonArray = org.json.JSONArray()
        models.forEach { jsonArray.put(it) }
        return copy(availableModels = jsonArray.toString())
    }
}

/**
 * AI对话消息
 * 参照anx53的ChatMessage设计
 */
data class ChatMessage(
    val type: String,    // "human", "ai", "system", "tool"
    val content: String,  // 消息内容
    val toolCallId: String? = null,  // 工具调用ID（用于tool类型的消息）
    val toolSteps: List<ToolStep> = emptyList()  // 工具调用步骤列表
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type,
        "content" to content
    ).let { map ->
        val withToolCallId = if (toolCallId != null) {
            map + ("toolCallId" to toolCallId)
        } else {
            map
        }
        // 添加工具步骤数据
        if (toolSteps.isNotEmpty()) {
            withToolCallId + ("toolSteps" to toolSteps.map { it.toMap() })
        } else {
            withToolCallId
        }
    }

    companion object {
        fun fromMap(map: Map<String, Any>): ChatMessage {
            val toolSteps = if (map.containsKey("toolSteps")) {
                @Suppress("UNCHECKED_CAST")
                (map["toolSteps"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.map {
                    ToolStep.fromMap(it)
                } ?: emptyList()
            } else {
                emptyList()
            }
            
            return ChatMessage(
                type = map["type"]?.toString() ?: "human",
                content = map["content"]?.toString() ?: "",
                toolCallId = map["toolCallId"]?.toString(),
                toolSteps = toolSteps
            )
        }
    }
}

/**
 * AI聊天结果
 * 用于流式响应，支持推理过程和工具步骤
 */
sealed class ChatResult {
    /**
     * 流式输出片段（普通文本）
     */
    data class Chunk(val content: String) : ChatResult()

    /**
     * 推理过程片段
     */
    data class ReasoningChunk(val content: String) : ChatResult()

    /**
     * 工具步骤更新
     */
    data class ToolStepUpdate(val step: ToolStep) : ChatResult()

    /**
     * 工具调用开始
     */
    data class ToolCall(
        val name: String,
        val arguments: String
    ) : ChatResult()

    /**
     * 工具开始执行
     */
    data class ToolStart(val name: String) : ChatResult()

    /**
     * 工具执行结果
     */
    data class ToolResult(
        val name: String,
        val result: String
    ) : ChatResult()

    /**
     * 完整输出
     */
    data class Success(
        val content: String,
        val reasoningContent: String = "",
        val toolSteps: List<ToolStep> = emptyList()
    ) : ChatResult()

    /**
     * 错误
     */
    data class Error(val message: String) : ChatResult()
}

/**
 * AI对话会话
 * 参照anx53的AiChatHistoryEntry设计
 */
data class AiChatSession(
    val id: String,
    val serviceId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
    val completed: Boolean = true
) {
    fun toJson(): Map<String, Any> = mapOf(
        "id" to id,
        "serviceId" to serviceId,
        "model" to model,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "completed" to completed,
        "messages" to messages.map { it.toMap() }
    )

    companion object {
        fun fromJson(json: Map<String, Any>): AiChatSession {
            val rawMessages = json["messages"]
            @Suppress("UNCHECKED_CAST")
            val messages = if (rawMessages is List<*>) {
                rawMessages.filterIsInstance<Map<String, Any>>()
                    .map { ChatMessage.fromMap(it) }
            } else emptyList()

            return AiChatSession(
                id = json["id"]?.toString() ?: "",
                serviceId = json["serviceId"]?.toString() ?: "",
                model = json["model"]?.toString() ?: "",
                createdAt = (json["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (json["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                messages = messages,
                completed = json["completed"] == true
            )
        }
    }
}

/**
 * AI工具定义
 * 参照anx53的AiToolDefinition设计
 */
data class AiToolDefinition(
    val id: String,
    val displayNameBuilder: () -> String,
    val descriptionBuilder: () -> String,
    val inputSchema: Map<String, Any>
)

/**
 * 工具执行结果
 */
data class ToolResult(
    val status: String,   // "ok" 或 "error"
    val name: String,
    val data: Any? = null,
    val message: String? = null
) {
    fun toJson(): Map<String, Any> = buildMap {
        put("status", status)
        put("name", name)
        data?.let { put("data", it) }
        message?.let { put("message", it) }
    }

    companion object {
        fun error(message: String, name: String = ""): ToolResult {
            return ToolResult(
                status = "error",
                name = name,
                message = message
            )
        }

        fun ok(data: Any? = null, name: String = ""): ToolResult {
            return ToolResult(
                status = "ok",
                name = name,
                data = data
            )
        }
    }
}

/**
 * 技能分类
 */
enum class SkillCategory {
    summarizer,      // 总结归纳
    explainer,       // 概念解释
    translator,      // 翻译
    analysis,        // 分析
    tracking,        // 追踪（人物/情节）
    custom           // 自定义
}

/**
 * AI技能实体
 * 参照ReadAny的Skills系统设计，比普通提示词更结构化
 */
@Entity(tableName = "ai_skills")
data class AiSkillEntity(
    @PrimaryKey
    val id: String,
    val name: String,                    // 技能名称
    val description: String,             // 技能描述
    val triggerWord: String,             // 触发词/关键词
    val instruction: String,              // 详细指令（系统提示词）
    val category: String = "custom",     // 分类
    val icon: String? = null,           // 图标
    val showIn: String = "quick_bar",    // 显示位置
    val sortOrder: Int = 0,             // 排序
    val isEnabled: Boolean = true,       // 是否启用
    val isBuiltin: Boolean = false,     // 是否内置
    @get:JvmName("getVariablesJson")
    val variables: String = "[]",         // 变量列表JSON
    @get:JvmName("getExamplesJson")
    val examples: String = "[]",         // 使用示例JSON
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getVariables(): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(variables)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setVariables(list: List<String>): AiSkillEntity {
        val jsonArray = org.json.JSONArray()
        list.forEach { jsonArray.put(it) }
        return copy(variables = jsonArray.toString())
    }

    fun getExamples(): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(examples)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setExamples(list: List<String>): AiSkillEntity {
        val jsonArray = org.json.JSONArray()
        list.forEach { jsonArray.put(it) }
        return copy(examples = jsonArray.toString())
    }
}

/**
 * AI模型配置实体
 * 参照archive项目设计，一个服务商可以有多个模型
 */
@Entity(tableName = "ai_models")
data class AiModelConfig(
    @PrimaryKey
    val id: String,
    val providerId: String,  // 关联的服务商ID
    val modelId: String,     // 模型ID（如 gpt-4, claude-3-sonnet 等）
    val displayName: String? = null,  // 显示名称（可选）
    val enabled: Boolean = true,      // 是否启用
    val sortOrder: Int = 0,           // 排序
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * AI提示词实体
 */
@Entity(tableName = "ai_custom_prompts")
data class AiPromptEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val content: String,
    val showIn: String = "quick_bar",
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 技能执行结果
 */
data class SkillResult(
    val skillId: String,
    val success: Boolean,
    val message: String = "",
    val data: Any? = null
)

/**
 * 前情提要缓存
 */
@Entity(tableName = "ai_recall_cache")
data class AiRecallCacheEntity(
    @PrimaryKey
    val bookUrl: String,
    val content: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * AI服务商选项（用于配置界面）
 * 参照anx53的AiServiceOption
 */
data class AiServiceOption(
    val identifier: String,
    val title: String,
    val logo: String,
    val defaultUrl: String,
    val defaultModel: String,
    val isCustom: Boolean = false
)

/**
 * AI协议类型（扩展版本）
 */
enum class AiProtocolType {
    OPENAI,      // OpenAI 兼容
    ANTHROPIC,   // Anthropic (Claude)
    GOOGLE,      // Google Gemini
    DEEPSEEK,    // DeepSeek
    MOONSHOT,    // Moonshot (Kimi)
    ZHIPU,       // 智谱 GLM
    ALIBABA,     // 阿里云通义
    OLLAMA,      // Ollama 本地
    LMSTUDIO,    // LM Studio 本地
    OPENROUTER,  // OpenRouter
    SILICONFLOW, // SiliconFlow
    CUSTOM       // 自定义
}

/**
 * 内置服务商列表（完整版）
 * 参照ReadAny的provider设计
 */
object AiServiceOptions {
    val defaultServices = listOf(
        // OpenAI 兼容（通用）
        AiServiceOption(
            identifier = "openai",
            title = "通用 (OpenAI兼容)",
            logo = "ai_common",
            defaultUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            defaultModel = "qwen-long"
        ),
        // Anthropic Claude
        AiServiceOption(
            identifier = "claude",
            title = "Claude",
            logo = "ai_claude",
            defaultUrl = "https://api.anthropic.com/v1/messages",
            defaultModel = "claude-3-5-sonnet-20240620"
        ),
        // Google Gemini
        AiServiceOption(
            identifier = "gemini",
            title = "Gemini",
            logo = "ai_gemini",
            defaultUrl = "https://generativelanguage.googleapis.com/v1beta/models",
            defaultModel = "gemini-2.0-flash-exp"
        ),
        // DeepSeek
        AiServiceOption(
            identifier = "deepseek",
            title = "DeepSeek",
            logo = "ai_deepseek",
            defaultUrl = "https://api.deepseek.com/v1/chat/completions",
            defaultModel = "deepseek-chat"
        ),
        // Moonshot (Kimi)
        AiServiceOption(
            identifier = "moonshot",
            title = "Moonshot (Kimi)",
            logo = "ai_moonshot",
            defaultUrl = "https://api.moonshot.cn/v1/chat/completions",
            defaultModel = "moonshot-v1-8k"
        ),
        // 智谱 GLM
        AiServiceOption(
            identifier = "zhipu",
            title = "智谱 GLM",
            logo = "ai_zhipu",
            defaultUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            defaultModel = "glm-4-flash"
        ),
        // 阿里云通义
        AiServiceOption(
            identifier = "aliyun",
            title = "阿里云通义",
            logo = "ai_aliyun",
            defaultUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            defaultModel = "qwen-turbo"
        ),
        // Ollama
        AiServiceOption(
            identifier = "ollama",
            title = "Ollama (本地)",
            logo = "ai_ollama",
            defaultUrl = "http://localhost:11434/api/chat",
            defaultModel = "llama3"
        ),
        // LM Studio
        AiServiceOption(
            identifier = "lmstudio",
            title = "LM Studio (本地)",
            logo = "ai_lmstudio",
            defaultUrl = "http://localhost:1234/v1/chat/completions",
            defaultModel = "local-model"
        ),
        // OpenRouter
        AiServiceOption(
            identifier = "openrouter",
            title = "OpenRouter",
            logo = "ai_openrouter",
            defaultUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "anthropic/claude-3-haiku"
        ),
        // SiliconFlow
        AiServiceOption(
            identifier = "siliconflow",
            title = "SiliconFlow",
            logo = "ai_siliconflow",
            defaultUrl = "https://api.siliconflow.cn/v1/chat/completions",
            defaultModel = "Qwen/Qwen2-7B-Instruct"
        ),
        // 自定义
        AiServiceOption(
            identifier = "custom",
            title = "自定义 (Custom)",
            logo = "ai_custom",
            defaultUrl = "",
            defaultModel = "",
            isCustom = true
        )
    )

    fun getByIdentifier(identifier: String): AiServiceOption? {
        return defaultServices.find { it.identifier == identifier }
    }
}
