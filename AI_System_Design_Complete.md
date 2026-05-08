# dai411项目AI功能完整技术方案

## 一、系统整体架构

### 1.1 架构分层

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              应用层（UI Layer）                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   AiChatActivity  │  │ AiSettingsActivity │ │ 分析结果Dialog/Fragment │ │
│  └────────┬────────┘  └────────┬────────┘  └──────────────┬──────────────┘ │
└───────────┼────────────────────┼─────────────────────────────────┼────────────┘
            │                    │                                 │
┌───────────┼────────────────────┼─────────────────────────────────┼────────────┐
│           ▼                    ▼                                 ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           业务层（ViewModel / Service）                  │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐   │ │
│  │  │ ChatManager    │  │ AnalysisManager│  │ SkillsManager          │   │ │
│  │  │ 对话管理器      │  │ 分析管理器      │  │ 技能管理器            │   │ │
│  │  └────────────────┘  └────────────────┘  └────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
            │                    │                                 │
┌───────────┼────────────────────┼─────────────────────────────────┼────────────┐
│           ▼                    ▼                                 ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           AI服务层                                      │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐   │ │
│  │  │ AiApiClient    │  │ PromptEngine   │  │ ToolRegistry            │   │ │
│  │  │ API客户端       │  │ 提示词引擎     │  │ 工具注册表             │   │ │
│  │  └────────────────┘  └────────────────┘  └────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
            │                    │                                 │
┌───────────┼────────────────────┼─────────────────────────────────┼────────────┐
│           ▼                    ▼                                 ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           数据层                                        │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐   │ │
│  │  │ Room数据库      │  │ 共享偏好设置   │  │ 阅读数据访问           │   │ │
│  │  │ AI配置/会话    │  │ 轻量配置       │  │ BookDao/ChapterDao   │   │ │
│  │  └────────────────┘  └────────────────┘  └────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 模块说明

| 层级 | 模块 | 职责 |
|------|------|------|
| **应用层** | Activity/Dialog/Fragment | 用户界面展示和交互 |
| **业务层** | Manager/ViewModel | 功能逻辑、状态管理、数据处理 |
| **AI服务层** | ApiClient/PromptEngine/ToolRegistry | AI通信、提示词管理、工具调用 |
| **数据层** | Room/SharedPreferences/Dao | 数据持久化和访问 |

---

## 二、核心功能模块

### 2.1 智能问答模块（ChatModule）

#### 2.1.1 模块职责

- 管理用户与AI的多轮对话
- 维护会话上下文和历史
- 处理对话的发送、接收、取消

#### 2.1.2 核心类设计

```kotlin
// 对话管理器
class ChatManager(
    private val aiService: AiService,
    private val sessionRepository: ChatSessionRepository
) {
    // 发送消息并获取流式响应
    fun sendMessage(
        content: String,
        context: AiContext
    ): Flow<ChatState>
    
    // 取消当前请求
    fun cancelRequest()
    
    // 加载历史会话
    suspend fun loadSession(sessionId: String): ChatSession
    
    // 创建新会话
    suspend fun createSession(context: AiContext): String
}

// 对话状态
sealed class ChatState {
    data object Idle : ChatState()
    data class Thinking(val thinkingContent: String) : ChatState()
    data class Answering(val content: String) : ChatState()
    data class Error(val message: String) : ChatState()
}
```

#### 2.1.3 功能特性

| 功能 | 说明 |
|------|------|
| 多轮对话 | 支持上下文连贯的多轮交互 |
| 流式响应 | 实时显示AI回复，支持打字机效果 |
| 会话保存 | 自动保存对话历史，支持恢复 |
| 请求取消 | 用户可中断正在进行的请求 |
| 快捷提示 | 预设问题按钮，快速发起对话 |

### 2.2 阅读分析模块（AnalysisModule）

#### 2.2.1 模块职责

- 处理章节摘要、全书总结等分析请求
- 管理前情提要缓存
- 生成结构化分析结果

#### 2.2.2 核心类设计

```kotlin
// 分析管理器
class AnalysisManager(
    private val aiService: AiService,
    private val bookRepository: BookRepository,
    private val recallCache: RecallCache
) {
    // 生成章节摘要
    suspend fun summarizeChapter(bookUrl: String, chapterIndex: Int): AnalysisResult
    
    // 生成全书总结
    suspend fun summarizeBook(bookUrl: String): AnalysisResult
    
    // 获取前情提要
    suspend fun getRecall(bookUrl: String): RecallResult
    
    // 预生成前情提要（后台调用）
    suspend fun preGenerateRecall(bookUrl: String, upToChapter: Int)
}

// 分析结果
data class AnalysisResult(
    val type: AnalysisType,  // CHAPTER_SUMMARY, BOOK_SUMMARY, RECALL
    val content: String,
    val keyPoints: List<String>,  // 要点列表
    val characters: List<String>?,  // 人物列表（可选）
    val createdAt: Long
)
```

#### 2.2.3 分析类型

| 类型 | 说明 | 触发场景 |
|------|------|----------|
| CHAPTER_SUMMARY | 章节摘要 | 用户点击"章节摘要" |
| BOOK_SUMMARY | 全书总结 | 用户点击"全书总结" |
| RECALL | 前情提要 | 间隔>30分钟进入阅读 |
| CONTENT_ANALYSIS | 内容分析 | 用户点击"内容分析" |

### 2.3 Skills模块（SkillsModule）

#### 2.3.1 模块职责

- 管理AI技能的定义和配置
- 处理技能调用请求
- 提供技能执行结果

#### 2.3.2 核心类设计

```kotlin
// 技能管理器
class SkillsManager(
    private val aiService: AiService,
    private val skillsRepository: SkillsRepository,
    private val toolRegistry: ToolRegistry
) {
    // 执行技能
    suspend fun executeSkill(
        skillId: String,
        params: Map<String, Any>
    ): SkillResult
    
    // 获取可用技能列表
    fun getAvailableSkills(): List<AiSkill>
    
    // 启用/禁用技能
    suspend fun setSkillEnabled(skillId: String, enabled: Boolean)
}

// 技能定义
data class AiSkill(
    val id: String,
    val name: String,
    val description: String,
    val promptTemplate: String,
    val parameters: List<SkillParameter>,
    val enabled: Boolean,
    val isBuiltin: Boolean
)
```

#### 2.3.3 内置Skills

| Skill ID | 名称 | 功能 | Prompt模板 |
|-----------|------|------|------------|
| summarizer | 摘要器 | 生成内容摘要 | 请用简洁语言总结以下内容：{{content}} |
| explainer | 解释者 | 解释专业术语 | 请解释以下内容：{{content}} |
| analyzer | 分析者 | 分析内容 | 请从多角度分析以下内容：{{content}} |
| qa | 问答系统 | 回答问题 | 基于以下上下文回答用户问题... |

---

## 三、AI工具系统

### 3.1 工具架构

工具系统让AI能够访问阅读器的数据和功能，实现智能问答。

```
┌─────────────────────────────────────────────────────────────┐
│                      AI工具系统                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                  工具注册表 (ToolRegistry)           │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │  │
│  │  │bookInfo │ │chapter  │ │  notes  │ │ history │   │  │
│  │  │  tool   │ │  tool   │ │  tool  │ │  tool  │   │  │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │  │
│  └─────────────────────────────────────────────────────┘  │
│                          │                                 │
│                          ▼                                 │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                   工具接口 (AiTool)                   │  │
│  │  - id: 唯一标识                                      │  │
│  │  - name: 名称                                       │  │
│  │  - description: 描述                                │  │
│  │  - execute(params): 执行方法                        │  │
│  └─────────────────────────────────────────────────────┘  │
│                          │                                 │
│                          ▼                                 │
│  ┌─────────────────────────────────────────────────────┐  │
│  │                   数据访问层                         │  │
│  │  - BookDao / BookChapterDao                        │  │
│  │  - BookAnnotationDao / BookmarkDao                 │  │
│  │  - ReadRecordDao                                   │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心工具实现

#### 3.2.1 工具接口

```kotlin
interface AiTool {
    val id: String           // 工具唯一标识，如 "current_book_info"
    val name: String        // 工具名称，如 "获取书籍信息"
    val description: String // 工具描述，供AI理解用途
    
    // 输入schema，用于AI构造参数
    val inputSchema: Map<String, ToolParameter>
    
    // 执行工具
    suspend fun execute(params: Map<String, Any>): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
)

data class ToolParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)
```

#### 3.2.2 工具列表

| 工具ID | 名称 | 功能 | 返回数据 |
|--------|------|------|----------|
| current_book_info | 当前书籍信息 | 获取当前阅读书籍的元信息 | {书名, 作者, 简介, 进度} |
| current_chapter | 当前章节 | 获取当前章节内容 | {标题, 内容, 章节索引} |
| book_toc | 书籍目录 | 获取目录结构 | [{标题, 索引, 进度}...] |
| book_notes | 书籍笔记 | 获取笔记列表 | [{笔记内容, 章节, 时间}...] |
| bookmarks | 书签列表 | 获取书签 | [{标题, 章节, 时间}...] |
| reading_history | 阅读历史 | 获取阅读记录 | [{书籍, 时间, 进度}...] |
| search_content | 内容搜索 | 搜索书籍内容 | [{章节, 匹配内容}...] |

#### 3.2.3 工具实现示例

```kotlin
// 当前书籍信息工具
class CurrentBookInfoTool(
    private val bookRepository: BookRepository,
    private val readRecordRepository: ReadRecordRepository
) : AiTool {
    
    override val id = "current_book_info"
    override val name = "获取当前书籍信息"
    override val description = "获取用户当前正在阅读的书籍的基本信息、作者和阅读进度"
    
    override val inputSchema = mapOf(
        "fields" to ToolParameter(
            name = "fields",
            type = "string",
            required = false,
            description = "指定返回字段，逗号分隔"
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val book = bookRepository.getCurrentBook()
                ?: return ToolResult(false, error = "当前没有正在阅读的书籍")
            
            val progress = readRecordRepository.getProgress(book.bookUrl)
            
            val data = mapOf(
                "bookTitle" to book.name,
                "author" to book.author,
                "intro" to book.intro,
                "progress" to progress,
                "lastReadTime" to book.lastCheckTime
            )
            
            ToolResult(true, data)
        } catch (e: Exception) {
            ToolResult(false, error = e.message)
        }
    }
}

// 当前章节内容工具
class CurrentChapterTool(
    private val chapterRepository: BookChapterRepository
) : AiTool {
    
    override val id = "current_chapter"
    override val name = "获取当前章节内容"
    override val description = "获取用户当前正在阅读的章节的标题和内容文本"
    
    override val inputSchema = mapOf(
        "maxLength" to ToolParameter(
            name = "maxLength",
            type = "integer",
            required = false,
            description = "最大返回字符数，默认8000"
        )
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val chapter = chapterRepository.getCurrentChapter()
                ?: return ToolResult(false, error = "无法获取当前章节")
            
            val maxLength = params["maxLength"] as? Int ?: 8000
            val content = if (chapter.content.length > maxLength) {
                chapter.content.take(maxLength) + "..."
            } else {
                chapter.content
            }
            
            ToolResult(true, mapOf(
                "title" to chapter.title,
                "index" to chapter.index,
                "content" to content
            ))
        } catch (e: Exception) {
            ToolResult(false, error = e.message)
        }
    }
}
```

### 3.3 工具注册表

```kotlin
class ToolRegistry(
    private val tools: List<AiTool>
) {
    private val toolMap = tools.associateBy { it.id }
    
    fun getTool(id: String): AiTool? = toolMap[id]
    
    fun listAllTools(): List<AiTool> = tools
    
    // 获取工具定义列表，供AI模型了解可用工具
    fun getToolDefinitions(): List<ToolDefinition> = tools.map { tool ->
        ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = tool.id,
                description = tool.description,
                parameters = ObjectNode().apply {
                    put("type", "object")
                    put("properties", ObjectNode().apply {
                        tool.inputSchema.forEach { (key, param) ->
                            put(key, ObjectNode().apply {
                                put("type", param.type)
                                put("description", param.description)
                            })
                        }
                    })
                    put("required", tool.inputSchema.filter { it.value.required }.keys)
                }
            )
        )
    }
}
```

---

## 四、多服务商支持

### 4.1 服务商抽象

```kotlin
interface AiProvider {
    val id: String
    val name: String
    val protocol: AiProtocol
    
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        onChunk: (String) -> Unit
    ): Result<String>
    
    fun getModels(): List<String>
    fun validateConfig(apiKey: String, apiUrl: String): Boolean
}

enum class AiProtocol {
    OPENAI,    // OpenAI兼容
    ANTHROPIC, // Claude
    GOOGLE,    // Gemini
}
```

### 4.2 内置服务商

| 服务商 | 协议 | 默认模型 | API端点 |
|--------|------|----------|---------|
| 通用(阿里云) | OPENAI | qwen-long | https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions |
| OpenAI | OPENAI | gpt-4o-mini | https://api.openai.com/v1/chat/completions |
| Claude | ANTHROPIC | claude-3-5-sonnet-20240620 | https://api.anthropic.com/v1/messages |
| Gemini | GOOGLE | gemini-2.5-flash | https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent |
| DeepSeek | OPENAI | deepseek-chat | https://api.deepseek.com/v1/chat/completions |

### 4.3 API客户端

```kotlin
class AiApiClient(
    private val providers: Map<String, AiProvider>,
    private val rateLimiter: RateLimiter
) {
    private var currentProviderId: String = "default"
    
    fun setProvider(providerId: String) {
        currentProviderId = providerId
    }
    
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String? = null,
        onChunk: (String) -> Unit
    ): Result<String> {
        // 限流检查
        rateLimiter.throttle()
        
        val provider = providers[currentProviderId]
            ?: return Result.failure(IllegalArgumentException("未知服务商"))
        
        return provider.chat(
            messages = messages,
            model = model ?: provider.getModels().first(),
            onChunk = onChunk
        )
    }
}
```

---

## 五、数据模型

### 5.1 实体定义

```kotlin
// AI服务商配置
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val sortOrder: Int
)

// AI对话会话
@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    val bookUrl: String?,
    val providerId: String,
    val model: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

// AI对话消息
@Entity(tableName = "ai_chat_messages")
data class AiChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,  // "user" / "assistant" / "system"
    val content: String,
    val createdAt: Long
)

// AI技能配置
@Entity(tableName = "ai_skills")
data class AiSkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val promptTemplate: String,
    val enabled: Boolean,
    val isBuiltin: Boolean
)

// 用户自定义提示词（统一提示词系统）
@Entity(tableName = "ai_custom_prompts")
data class AiCustomPromptEntity(
    @PrimaryKey val id: String,
    val name: String,                    // 显示名称
    val content: String,                 // 提示词内容
    
    // 提示词类型
    val promptType: String,              // "system" / "skill" / "quick"
    
    // 入口控制（用逗号分隔，可在多个入口显示）
    // 可选值: "text_menu" / "toolbar" / "quick_bar" / "book_detail"
    val showIn: String,                  // 在哪些入口显示
    
    // 显示设置
    val icon: String?,                   // 图标资源名
    val sortOrder: Int = 0,             // 排序顺序
    
    // 状态
    val isEnabled: Boolean = true,       // 是否启用
    val isBuiltin: Boolean = false,      // 是否内置（内置不可删除）
    
    // 时间戳
    val createdAt: Long,
    val updatedAt: Long
)

// 前情提要缓存
@Entity(tableName = "ai_recall_cache")
data class AiRecallCacheEntity(
    @PrimaryKey val bookUrl: String,
    val content: String,
    val chapterIndex: Int,
    val createdAt: Long
)

// DAO接口
@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY sortOrder")
    suspend fun getAll(): List<AiProviderEntity>
    
    @Query("SELECT * FROM ai_providers WHERE enabled = 1")
    suspend fun getEnabled(): List<AiProviderEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: AiProviderEntity)
    
    @Delete
    suspend fun delete(provider: AiProviderEntity)
}

@Dao
interface AiChatSessionDao {
    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<AiChatSessionEntity>>
    
    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AiChatSessionEntity?
    
    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY createdAt")
    suspend fun getMessages(sessionId: String): List<AiChatMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiChatSessionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity)
    
    @Query("DELETE FROM ai_chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface AiCustomPromptDao {
    @Query("SELECT * FROM ai_custom_prompts WHERE type = :type ORDER BY name")
    suspend fun getPromptsByType(type: String): List<AiCustomPromptEntity>
    
    @Query("SELECT * FROM ai_custom_prompts WHERE type = :type AND id = :id")
    suspend fun getPrompt(type: String, id: String): AiCustomPromptEntity?
    
    @Query("SELECT * FROM ai_custom_prompts WHERE type = :type AND isEnabled = 1")
    suspend fun getEnabledPrompt(type: String): AiCustomPromptEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: AiCustomPromptEntity)
    
    @Query("DELETE FROM ai_custom_prompts WHERE type = :type AND id = :id")
    suspend fun delete(type: String, id: String)
}
```

### 5.4 用户自定义提示词系统

用户不仅可以配置AI服务商，还可以自定义提示词，定制AI助手的回答风格和行为。更重要的是，**所有AI功能入口的选项都统一由提示词系统控制**，每个提示词都可以独立设置在哪些入口显示。

#### 5.4.1 设计理念

将所有AI功能入口（长按文本菜单、底部工具栏、快捷工具栏、书籍详情页）统一为一个提示词系统：

- **一个提示词 = 一个功能入口选项**
- **showIn字段控制** 在哪些入口显示
- **每个提示词独立开关** 可以随时启用/禁用

#### 5.4.2 入口类型定义

| 入口标识 | 说明 | 位置 |
|----------|------|------|
| `text_menu` | 文本选择菜单 | 长按选中文本后弹出 |
| `toolbar` | 阅读底部菜单 | 阅读页面底部工具栏 |
| `quick_bar` | 快捷工具栏 | AI对话页面的快捷按钮 |
| `book_detail` | 书籍详情页 | 书籍详情页的AI入口 |

#### 5.4.3 提示词类型

| 类型 | 说明 | 示例 |
|------|------|------|
| system | 系统级提示词 | AI助手的角色定义（不显示在入口） |
| skill | 技能提示词 | 摘要器、解释者等（在quick_bar显示） |

#### 5.4.4 默认提示词配置

```kotlin
object DefaultPrompts {
    
    // 系统提示词（不显示在入口）
    const val SYSTEM_DEFAULT = """
你是dai阅读器的AI阅读助手，专门帮助用户解答阅读中的问题。
请根据用户的问题和当前阅读上下文，提供准确、有帮助的回答。
"""
    
    // 文本选择菜单（text_menu）提示词
    val TEXT_MENU_PROMPTS = listOf(
        PromptConfig(
            id = "explain",
            name = "解释这段",
            content = "请解释以下内容的含义和背景：\n{selectedText}",
            icon = "ic_explain",
            showIn = "text_menu",
            sortOrder = 1
        ),
        PromptConfig(
            id = "analyze",
            name = "帮我分析",
            content = "请从文学角度分析以下内容：\n{selectedText}",
            icon = "ic_analyze",
            showIn = "text_menu",
            sortOrder = 2
        ),
        PromptConfig(
            id = "translate",
            name = "翻译",
            content = "请翻译以下内容：\n{selectedText}",
            icon = "ic_translate",
            showIn = "text_menu",
            sortOrder = 3
        )
    )
    
    // 阅读底部菜单（toolbar）提示词
    val TOOLBAR_PROMPTS = listOf(
        PromptConfig(
            id = "chapter_summary",
            name = "章节摘要",
            content = "请用简洁语言总结当前章节的主要内容",
            icon = "ic_summary",
            showIn = "toolbar",
            sortOrder = 1
        ),
        PromptConfig(
            id = "book_summary",
            name = "全书总结",
            content = "请总结这本书的主要内容",
            icon = "ic_book_summary",
            showIn = "toolbar",
            sortOrder = 2
        ),
        PromptConfig(
            id = "recall",
            name = "前情回顾",
            content = "请帮我回顾之前的阅读内容",
            icon = "ic_recall",
            showIn = "toolbar",
            sortOrder = 3
        )
    )
    
    // 快捷工具栏（quick_bar）提示词
    val QUICK_BAR_PROMPTS = listOf(
        PromptConfig(
            id = "what_content",
            name = "这段讲了什么",
            content = "请简洁概括这段内容的主旨",
            icon = "ic_question",
            showIn = "quick_bar",
            sortOrder = 1
        ),
        PromptConfig(
            id = "help_analyze",
            name = "帮我分析",
            content = "请从文学角度分析这段内容的写法",
            icon = "ic_analyze",
            showIn = "quick_bar",
            sortOrder = 2
        ),
        PromptConfig(
            id = "summarize_chapter",
            name = "总结本章",
            content = "请用简洁语言总结当前章节的主要内容",
            icon = "ic_summary",
            showIn = "quick_bar",
            sortOrder = 3
        ),
        PromptConfig(
            id = "characters",
            name = "人物有哪些",
            content = "请提取本章出现的主要人物",
            icon = "ic_person",
            showIn = "quick_bar",
            sortOrder = 4
        ),
        PromptConfig(
            id = "what_next",
            name = "后续如何",
            content = "基于已有内容，推测后续情节可能如何发展",
            icon = "ic_predict",
            showIn = "quick_bar",
            sortOrder = 5
        )
    )
    
    // 书籍详情页（book_detail）提示词
    val BOOK_DETAIL_PROMPTS = listOf(
        PromptConfig(
            id = "book_chat",
            name = "与本书对话",
            content = "你想了解这本书的什么内容？",
            icon = "ic_chat",
            showIn = "book_detail",
            sortOrder = 1
        ),
        PromptConfig(
            id = "book_summary",
            name = "生成书籍总结",
            content = "请总结这本书的主要内容",
            icon = "ic_book_summary",
            showIn = "book_detail",
            sortOrder = 2
        )
    )
}

data class PromptConfig(
    val id: String,
    val name: String,
    val content: String,
    val icon: String?,
    val showIn: String,  // 入口标识，多个用逗号分隔
    val sortOrder: Int = 0
)
```

#### 5.4.5 数据模型

```kotlin
@Entity(tableName = "ai_prompts")
data class AiPromptEntity(
    @PrimaryKey val id: String,
    val name: String,                    // 显示名称
    val content: String,                 // 提示词内容
    
    // 入口控制
    val showIn: String,                 // "text_menu,toolbar,quick_bar,book_detail"
    val icon: String?,                  // 图标资源名
    val sortOrder: Int = 0,             // 排序
    
    // 状态
    val isEnabled: Boolean = true,      // 全局开关
    val isBuiltin: Boolean = false,     // 内置不可删除
    
    val createdAt: Long,
    val updatedAt: Long
)
```

#### 5.4.6 提示词管理器

```kotlin
class PromptManager(
    private val promptDao: AiPromptDao
) {
    
    // 获取指定入口的提示词列表
    fun getPromptsForEntrance(entrance: String): List<PromptDisplay> {
        return runBlocking {
            promptDao.getPromptsByEntrance(entrance)
                .filter { it.isEnabled }
                .sortedBy { it.sortOrder }
                .map { PromptDisplay(it.name, it.content, it.icon) }
        }
    }
    
    // 获取系统提示词
    fun getSystemPrompt(): String {
        return runBlocking {
            promptDao.getSystemPrompt()?.content 
                ?: DefaultPrompts.SYSTEM_DEFAULT
        }
    }
    
    // 保存/更新提示词
    suspend fun savePrompt(prompt: AiPromptEntity) {
        promptDao.insert(prompt)
    }
    
    // 启用/禁用提示词
    suspend fun setPromptEnabled(id: String, enabled: Boolean) {
        promptDao.setEnabled(id, enabled)
    }
    
    // 重置为默认
    suspend fun resetToDefault(id: String) {
        promptDao.delete(id)
    }
}
```

#### 5.4.7 提示词变量

在提示词内容中可以使用变量，运行时自动替换为实际值。变量使用dai411项目现有的Book字段：

##### 书籍相关变量

| 变量 | 对应Book字段 | 说明 | 示例 |
|------|-------------|------|------|
| `{book.name}` | Book.name | 书籍名称 | 《红楼梦》 |
| `{book.author}` | Book.author | 作者名称 | 曹雪芹 |
| `{book.intro}` | Book.intro | 书籍简介 | 本书讲述了... |
| `{book.kind}` | Book.kind | 分类信息 | 古典名著 |
| `{book.wordCount}` | Book.wordCount | 字数 | 100万字 |
| `{book.coverUrl}` | Book.coverUrl | 封面URL |  |
| `{book.rating}` | Book.rating | 评分 | 4.5 |
| `{book.origin}` | Book.origin | 书源 | local |
| `{book.originName}` | Book.originName | 来源名称 | 本地书籍 |

##### 阅读进度变量

| 变量 | 对应Book字段 | 说明 | 示例 |
|------|-------------|------|------|
| `{book.durChapterIndex}` | Book.durChapterIndex | 当前章节索引 | 15 |
| `{book.durChapterTitle}` | Book.durChapterTitle | 当前章节标题 | 第一回 甄士隐梦幻 |
| `{book.totalChapterNum}` | Book.totalChapterNum | 章节总数 | 120 |
| `{book.durChapterPos}` | Book.durChapterPos | 当前阅读位置 | 1024 |
| `{book.durChapterTime}` | Book.durChapterTime | 最后阅读时间 | 1704067200000 |
| `{book.lastCheckTime}` | Book.lastCheckTime | 最后检查时间 | 1704067200000 |
| `{book.readingStatus}` | Book.readingStatus | 阅读状态 | 1(在读) |

##### 文本相关变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{selectText}` | 用户选中的文本 | 这段内容... |
| `{selectStart}` | 选中文本的起始位置 | 100 |
| `{selectEnd}` | 选中文本的结束位置 | 150 |

##### 系统变量

| 变量 | 说明 | 示例 |
|------|------|------|
| `{currentTime}` | 当前时间 | 2024-01-15 10:30:00 |
| `{currentDate}` | 当前日期 | 2024-01-15 |

##### 变量替换器实现

```kotlin
class PromptVariableReplacer(
    private val book: Book?,
    private val selectedText: String?,
    private val selectionStart: Int = 0,
    private val selectionEnd: Int = 0
) {
    
    fun replace(input: String): String {
        var result = input
        
        // 书籍字段
        book?.let { b ->
            result = result.replace("{book.name}", b.name)
            result = result.replace("{book.author}", b.author)
            result = result.replace("{book.intro}", b.intro ?: "")
            result = result.replace("{book.kind}", b.kind ?: "")
            result = result.replace("{book.wordCount}", b.wordCount ?: "")
            result = result.replace("{book.rating}", b.rating.toString())
            result = result.replace("{book.origin}", b.origin)
            result = result.replace("{book.originName}", b.originName)
            
            // 阅读进度
            result = result.replace("{book.durChapterIndex}", b.durChapterIndex.toString())
            result = result.replace("{book.durChapterTitle}", b.durChapterTitle ?: "")
            result = result.replace("{book.totalChapterNum}", b.totalChapterNum.toString())
            result = result.replace("{book.durChapterPos}", b.durChapterPos.toString())
            result = result.replace("{book.durChapterTime}", b.durChapterTime.toString())
            result = result.replace("{book.lastCheckTime}", b.lastCheckTime.toString())
            result = result.replace("{book.readingStatus}", b.readingStatus.toString())
        }
        
        // 选中文本
        result = result.replace("{selectText}", selectedText ?: "")
        result = result.replace("{selectStart}", selectionStart.toString())
        result = result.replace("{selectEnd}", selectionEnd.toString())
        
        // 系统变量
        val now = LocalDateTime.now()
        result = result.replace("{currentTime}", now.format(DateTimeFormatter))
        result = result.replace("{currentDate}", now.toLocalDate().toString())
        
        return result
    }
}
```

##### 使用示例

```
提示词内容：
请解释"{selectText}"这段内容在《{book.name}》中的作用，
作者{book.author}在第{book.durChapterIndex}章进行了怎样的描写？

实际替换后：
请解释"甄士隐梦幻通灵玉"这段内容在《红楼梦》中的作用，
作者曹雪芹在第1章进行了怎样的描写？
```

#### 5.4.8 入口显示逻辑

```kotlin
class PromptEntranceManager(
    private val promptManager: PromptManager
) {
    
    // 获取文本选择菜单的选项
    fun getTextMenuPrompts(): List<PromptDisplay> {
        return promptManager.getPromptsForEntrance("text_menu")
    }
    
    // 获取底部工具栏的选项
    fun getToolbarPrompts(): List<PromptDisplay> {
        return promptManager.getPromptsForEntrance("toolbar")
    }
    
    // 获取快捷工具栏的选项
    fun getQuickBarPrompts(): List<PromptDisplay> {
        return promptManager.getPromptsForEntrance("quick_bar")
    }
    
    // 获取书籍详情页的选项
    fun getBookDetailPrompts(): List<PromptDisplay> {
        return promptManager.getPromptsForEntrance("book_detail")
    }
}
```

#### 5.4.8 用户自定义示例

用户可以完全自定义每个入口的选项：

```kotlin
// 示例：添加自定义提示词到文本选择菜单
val customPrompt = AiPromptEntity(
    id = "my_custom_explain",
    name = "古文解释",
    content = "请用通俗易懂的语言解释这段古文：\n{selectedText}",
    showIn = "text_menu",  // 只在文本选择菜单显示
    icon = "ic_classic",
    sortOrder = 4,
    isEnabled = true,
    isBuiltin = false,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)

// 示例：添加一个在多个入口显示的提示词
val multiEntrancePrompt = AiPromptEntity(
    id = "my_summarizer",
    name = "智能摘要",
    content = "请用简洁语言总结以下内容：\n{selectedText}",
    showIn = "text_menu,quick_bar",  // 同时在文本菜单和快捷工具栏显示
    icon = "ic_summary",
    sortOrder = 1,
    isEnabled = true,
    isBuiltin = false,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)
```

#### 5.4.9 UI交互设计

```
AI设置
├── 提示词管理
│   ├── 文本选择菜单
│   │   ├── ☑ 解释这段 (ic_explain)
│   │   ├── ☑ 帮我分析 (ic_analyze)
│   │   ├── ☑ 翻译 (ic_translate)
│   │   ├── + 添加自定义
│   │   │
│   ├── 阅读底部菜单
│   │   ├── ☑ 章节摘要
│   │   ├── ☑ 全书总结
│   │   ├── ☑ 前情回顾
│   │   ├── + 添加自定义
│   │   │
│   ├── 快捷工具栏
│   │   ├── ☑ 这段讲了什么
│   │   ├── ☑ 帮我分析
│   │   ├── ☑ 总结本章
│   │   ├── ☑ 人物有哪些
│   │   ├── ☑ 后续如何
│   │   ├── + 添加自定义
│   │   │
│   └── 书籍详情页
│       ├── ☑ 与本书对话
│       ├── ☑ 生成书籍总结
│       └── + 添加自定义
│
└── 系统提示词
    └── 编辑AI助手的角色定义
```

点击任意提示词可以：
- 查看/编辑提示词内容
- 修改显示名称
- 更改图标
- 调整显示顺序
- 删除（自定义提示词）
- 启用/禁用

#### 5.4.10 总结

这个设计的核心优势：

1. **统一管理**：所有AI功能入口的选项都由提示词系统统一管理
2. **灵活配置**：用户可以自由决定每个提示词在哪些入口显示
3. **自由定制**：用户可以添加自定义提示词，定制自己的AI助手
4. **独立开关**：每个提示词都可以独立启用/禁用

---

## 六、用户交互入口设计

### 6.1 入口总览

根据整体架构，AI功能通过以下入口与用户交互：

| 入口位置 | 触发方式 | 调用模块 | 功能 |
|----------|----------|----------|------|
| 文本选择菜单 | 长按选中文本 | SkillsModule | 解释、分析 |
| 阅读底部菜单 | 点击AI按钮 | AnalysisModule | 章节摘要、全书总结 |
| 阅读退出时 | 自动检测 | AnalysisModule | 章节总结 |
| 阅读进入时 | 自动检测 | AnalysisModule | 前情提要 |
| 书籍详情页 | 点击按钮 | ChatModule | 本书问答 |

### 6.2 入口与模块对应关系

```
                         ┌──────────────────────────────────────────┐
                         │                 用户入口                  │
                         └──────────────────────────────────────────┘
                                            │
          ┌──────────────────────────────────┼──────────────────────────────────┐
          │                                  │                                  │
          ▼                                  ▼                                  ▼
   ┌──────────────┐                 ┌──────────────┐                 ┌──────────────┐
   │ 文本选择菜单  │                 │ 阅读底部菜单  │                 │ 书籍详情页   │
   │ (长按文本)   │                 │ (点击AI按钮) │                 │ (点击按钮)   │
   └──────┬───────┘                 └──────┬───────┘                 └──────┬───────┘
          │                                 │                                  │
          │                                 │                                  │
          ▼                                 ▼                                  ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                         SkillsModule / AnalysisModule                         │
   │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
   │  │ explainer      │  │ summarizer     │  │ qa             │              │
   │  │ (解释技能)     │  │ (摘要技能)     │  │ (问答技能)     │              │
   │  └────────────────┘  └────────────────┘  └────────────────┘              │
   └──────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                              AiService                                       │
   │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐              │
   │  │ AiApiClient   │  │ PromptEngine   │  │ ToolRegistry  │              │
   │  └────────────────┘  └────────────────┘  └────────────────┘              │
   └──────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 入口实现集成点

#### 入口1：文本选择菜单

- **修改文件**：`res/menu/content_select_action.xml`
- **新增菜单**：
```xml
<item android:id="@+id/menu_ai" android:title="AI助手" app:showAsAction="never">
    <menu>
        <item android:id="@+id/menu_ai_explain" android:title="解释这段" />
        <item android:id="@+id/menu_ai_analyze" android:title="帮我分析" />
    </menu>
</item>
```
- **调用流程**：`TextActionMenu` → `SkillsManager.executeSkill("explainer", ...)`

#### 入口2：阅读底部菜单

- **修改文件**：`ReadMenu.kt` + 新增菜单文件
- **新增按钮**：底部菜单栏添加AI按钮
- **调用流程**：`ReadMenu` → `AnalysisManager.summarizeChapter()` 或 `AnalysisManager.summarizeBook()`

#### 入口3：阅读进入/退出

- **修改文件**：`ReadBookActivity.kt`
- **进入检测**：`onResume()` 检查间隔，调用 `AnalysisManager.getRecall()`
- **退出检测**：`onBackPressed()` 检查时长，调用 `AnalysisManager.summarizeChapter()`

#### 入口4：书籍详情页

- **修改文件**：`BookInfoActivity.kt`
- **新增按钮**：详情页添加"AI问答"按钮
- **调用流程**：`BookInfoActivity` → `AiChatActivity` (传入bookUrl)

---

## 七、提示词设计

### 7.1 System Prompt

```kotlin
object SystemPrompts {
    
    const val DEFAULT = """
你是dai阅读器的AI阅读助手，专门帮助用户解答阅读中的问题。

你的能力：
- 解释和分析阅读内容
- 生成章节和书籍摘要
- 回答关于书籍内容的问题
- 追踪人物和情节

当你需要了解用户当前阅读状态时，可以使用工具获取信息。
请根据用户的问题和当前阅读上下文，提供准确、有帮助的回答。
"""
    
    const val SUMMARIZER = """
你是一个专业的书籍摘要助手。
请用简洁的语言总结提供的内容，包括：
- 主要情节/核心内容
- 关键人物
- 主题思想

语言：使用与原内容相同的语言
长度：100-300字
"""
    
    const val EXPLAINER = """
你是一个专业的知识解释助手。
请解释以下内容的含义、背景和相关知识。
要求：
- 解释清晰准确
- 提供相关背景信息
- 如有专业术语需额外说明
"""
    
    const val ANALYZER = """
你是一个专业的文学分析助手。
请从以下角度分析提供的内容：
- 写作手法
- 人物塑造
- 情节结构
- 主题表达

要求分析深入、有独到见解。
"""
}
```

### 7.2 快捷提示定义

```kotlin
object QuickPrompts {
    val prompts = listOf(
        QuickPrompt("这段讲了什么", "请简洁概括这段内容的主旨"),
        QuickPrompt("帮我分析", "请从文学角度分析这段内容的写法"),
        QuickPrompt("总结本章", "请用简洁语言总结当前章节的主要内容"),
        QuickPrompt("人物有哪些", "请提取本章出现的主要人物"),
        QuickPrompt("后续如何", "基于已有内容，推测后续情节可能如何发展")
    )
}

data class QuickPrompt(
    val label: String,
    val prompt: String
)
```

---

## 八、技术实现要点

### 8.1 流式响应处理

```kotlin
class SseParser {
    
    fun parseSseStream(
        inputStream: InputStream,
        onChunk: (String) -> Unit
    ) {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            val buffer = StringBuilder()
            
            while (reader.readLine().also { line = it } != null) {
                when {
                    line!!.startsWith("data:") -> {
                        val data = line!!.substring(5).trim()
                        if (data == "[DONE]") {
                            break
                        }
                        // 解析JSON，提取content字段
                        val content = extractContent(data)
                        if (content != null) {
                            buffer.append(content)
                            onChunk(buffer.toString())
                        }
                    }
                }
            }
        }
    }
    
    private fun extractContent(json: String): String? {
        // 解析OpenAI格式的响应
        // return json.parseAs<OpenAIResponse>().choices.first().delta.content
        return null
    }
}
```

### 8.2 请求限流

```kotlin
class RateLimiter(private val rpm: Int = 60) {
    private val timestamps = ArrayDeque<Long>()
    private val lock = ReentrantLock()
    
    suspend fun throttle() = withContext(Dispatchers.IO) {
        lock.withLock {
            val now = System.currentTimeMillis()
            val windowStart = now - 60_000
            
            // 清理过期时间戳
            while (timestamps.isNotEmpty() && timestamps.first() < windowStart) {
                timestamps.removeFirst()
            }
            
            // 超过限制则等待
            if (timestamps.size >= rpm) {
                val waitTime = 60_000 - (now - timestamps.first())
                delay(waitTime)
            }
            
            timestamps.addLast(System.currentTimeMillis())
        }
    }
}
```

---

## 九、模块依赖关系

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           应用层                                            │
│  AiChatActivity ────── AiSettingsActivity ────── AnalysisDialog          │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
┌─────────────────────────────────┴───────────────────────────────────────────┐
│                           业务层                                            │
│         │                      │                      │                    │
│         ▼                      ▼                      ▼                    │
│  ┌─────────────┐        ┌─────────────┐        ┌─────────────┐          │
│  │ ChatManager │        │AnalysisManager│        │SkillsManager│          │
│  └──────┬──────┘        └──────┬───────┘        └──────┬──────┘          │
└─────────┼─────────────────────┼─────────────────────┼──────────────────┘
          │                     │                     │
┌─────────┴─────────────────────┴─────────────────────┴──────────────────┐
│                           AI服务层                                          │
│         │                     │                     │                     │
│         ▼                     ▼                     ▼                     │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐           │
│  │ AiApiClient │       │PromptEngine │       │ToolRegistry │           │
│  └─────────────┘       └─────────────┘       └─────────────┘           │
└───────────────────────────────────────────────────────────────────────────┘
          │                     │                     │
┌─────────┴─────────────────────┴─────────────────────┴──────────────────┐
│                           数据层                                            │
│         │                     │                     │                     │
│         ▼                     ▼                     ▼                     │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐           │
│  │ Room (DAO)  │       │SharedPrefs  │       │BookDao等    │           │
│  └─────────────┘       └─────────────┘       └─────────────┘           │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 十、总结

本方案从整体架构出发，包含以下核心内容：

1. **四大模块**：
   - ChatModule（智能问答）
   - AnalysisModule（阅读分析）
   - SkillsModule（技能系统）
   - ToolModule（工具系统）

2. **多服务商支持**：OpenAI/Claude/Gemini/DeepSeek/阿里云

3. **用户入口**：
   - 文本选择菜单 → SkillsModule
   - 阅读底部菜单 → AnalysisModule
   - 阅读进入/退出 → AnalysisModule
   - 书籍详情页 → ChatModule

4. **数据层**：Room数据库存储AI配置、会话、缓存

5. **技术实现**：流式响应、请求限流、工具调用

所有功能模块相互独立又协同工作，形成完整的AI功能系统。