# dai411项目AI智能问答与内容分析功能复刻计划

## 文档概述

本文档详细描述了将anx53项目中的AI智能问答和阅读内容分析功能复刻到dai411项目的技术实现方案。文档聚焦于功能层面的技术拆解，包括模块架构设计、核心代码实现思路、数据流设计等，为开发团队提供可直接执行的技术指南。不涉及预算、资源、时间周期等非功能相关内容。

---

## 一、功能概述与架构设计

### 1.1 目标功能范围

本次复刻包含两个核心功能模块：

**智能问答功能**：用户可以通过自然语言与AI助手进行交互，获取与当前阅读内容相关的帮助。AI能够访问用户的阅读上下文，包括当前正在阅读的书籍、章节、笔记、书签等信息，从而提供个性化的问答服务。该功能支持多轮对话保持上下文连贯，提供预设快捷提示，以及对话历史管理。

**阅读内容分析功能**：用户可以请求AI对当前阅读的章节或全书进行内容分析，包括章节摘要、全书总结、情节回顾、人物分析等。该功能利用预定义的提示模板，引导AI生成结构化的分析结果。

### 1.2 技术架构设计

dai411项目采用Kotlin语言开发Android应用，技术架构需要适配到Kotlin生态。整体架构分为四个层次：

**表现层（UI Layer）**：负责用户交互界面，包括对话界面、消息展示、输入控制等。采用Android的Activity/Fragment + ViewModel架构，使用Kotlin协程处理异步操作。

**业务层（Business Layer）**：负责AI功能的核心逻辑，包括对话管理、会话状态维护、提示模板生成、结果处理等。

**服务层（Service Layer）**：负责与AI服务商的通信，包括HTTP请求封装、响应解析、流式处理、错误处理等。

**数据层（Data Layer）**：负责数据持久化，包括AI配置存储、会话历史存储、用户偏好设置等。

### 1.3 模块依赖关系

```
┌─────────────────────────────────────────────────────────┐
│                      UI 层                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
│  │ 对话Activity │  │ 设置页面   │  │ 分析结果    │   │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘   │
└─────────┼────────────────┼────────────────┼───────────┘
          │                │                │
┌─────────┼────────────────┼────────────────┼───────────┐
│         ▼                ▼                ▼           │
│  ┌─────────────────────────────────────────────┐     │
│  │              业务层 ViewModel               │     │
│  │  ┌────────────┐  ┌───────────┐  ┌────────┐│     │
│  │  │对话管理器  │  │提示生成器 │  │分析器  ││     │
│  │  └─────┬──────┘  └─────┬─────┘  └───┬────┘│     │
│  └────────┼────────────────┼────────────┼─────┘     │
└───────────┼────────────────┼────────────┼────────────┘
            │                │            │
┌───────────┼────────────────┼────────────┼────────────┐
│           ▼                ▼            ▼            │
│  ┌─────────────────────────────────────────────┐     │
│  │              AI 服务层                      │     │
│  │  ┌────────────┐  ┌───────────┐  ┌────────┐│     │
│  │  │API客户端   │  │流处理器   │  │工具注册│     │
│  │  └────────────┘  └───────────┘  └────────┘│     │
│  └─────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────┘
            │                │            │
┌───────────┼────────────────┼────────────┼────────────┐
│           ▼                ▼            ▼            │
│  ┌─────────────────────────────────────────────┐     │
│  │              数据层                          │     │
│  │  ┌────────────┐  ┌───────────┐  ┌────────┐│     │
│  │  │配置存储    │  │会话存储   │  │缓存    ││     │
│  │  └────────────┘  └───────────┘  └────────┘│     │
│  └─────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────┘
```

---

## 二、核心模块详细设计

### 2.1 AI配置管理模块

#### 2.1.1 数据模型设计

```kotlin
// AI服务商实体
data class AiProvider(
    val id: String,                          // 服务商标识
    val title: String,                      // 显示名称
    val url: String,                         // API端点
    val protocol: AiProtocol,                // 协议类型
    val enabled: Boolean = true,             // 是否启用
    val isBuiltin: Boolean = false,          // 是否内置
    val apiKeys: List<ApiKey> = emptyList(), // API密钥列表
    val model: String = "",                  // 当前模型
    val keyIndex: Int = 0                   // 密钥轮换索引
)

// API密钥实体
data class ApiKey(
    val id: String,             // 密钥ID
    val key: String,           // 密钥值
    val enabled: Boolean = true,// 是否启用
    val label: String? = null  // 标签/备注
)

// AI协议枚举
enum class AiProtocol(val code: String) {
    OPENAI("openai"),
    CLAUDE("claude"),
    GEMINI("gemini")
}
```

#### 2.1.2 配置存储

使用SharedPreferences存储AI配置。配置以JSON格式序列化存储。

```kotlin
object AiConfigManager {
    private const val KEY_AI_PROVIDERS = "ai_providers"
    private const val KEY_SELECTED_PROVIDER = "selected_ai_service"
    
    // 保存服务商列表
    fun saveProviders(providers: List<AiProvider>)
    
    // 获取服务商列表
    fun getProviders(): List<AiProvider>
    
    // 获取当前选中的服务商
    fun getSelectedProvider(): AiProvider?
    
    // 设置选中的服务商
    fun setSelectedProvider(providerId: String)
    
    // 获取单个服务商的配置
    fun getProviderConfig(providerId: String): Map<String, String>
}
```

#### 2.1.3 内置服务商定义

参考anx53项目，定义以下内置服务商：

| 服务商 | 默认URL | 默认模型 |
|--------|---------|----------|
| 通用（OpenAI兼容） | https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions | qwen-long |
| OpenAI | https://api.openai.com/v1/chat/completions | gpt-4o-mini |
| Claude | https://api.anthropic.com/v1/messages | claude-3-5-sonnet-20240620 |
| Gemini | https://generativelanguage.googleapis.com | gemini-2.5-flash |
| DeepSeek | https://api.deepseek.com/v1/chat/completions | deepseek-chat |

### 2.2 AI服务通信模块

#### 2.2.1 API客户端设计

基于项目现有的OkHttp网络层进行扩展。

```kotlin
class AiApiClient {
    // 流式调用AI接口
    suspend fun chatCompletions(
        url: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Result<String>
    
    // 构建请求体
    private fun buildRequestBody(messages: List<ChatMessage>): RequestBody
    
    // 解析流式响应
    private fun parseStreamResponse(response: Response): Flow<String>
}
```

#### 2.2.2 消息格式定义

```kotlin
sealed class ChatMessage {
    data class HumanMessage(val content: String) : ChatMessage()
    data class AiMessage(val content: String) : ChatMessage()
    data class SystemMessage(val content: String) : ChatMessage()
}

data class Message(
    val role: String,      // "user", "assistant", "system"
    val content: String    // 消息内容
)
```

#### 2.2.3 请求限流机制

实现基于滑动窗口的请求限流。

```kotlin
class RateLimiter {
    private val requestTimestamps = mutableListOf<Long>()
    private val rpm: Int  // 每分钟最大请求数
    
    suspend fun throttle() {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000
        
        // 清理过期时间戳
        requestTimestamps.removeAll { it < windowStart }
        
        // 超过限制则等待
        if (requestTimestamps.size >= rpm) {
            val waitTime = 60_000 - (now - requestTimestamps.first())
            delay(waitTime)
        }
        
        requestTimestamps.add(now)
    }
}
```

### 2.3 对话管理模块

#### 2.3.1 会话数据模型

```kotlin
data class ChatSession(
    val id: String,                    // 会话ID
    val serviceId: String,             // 使用的服务商ID
    val model: String,                 // 使用的模型
    val createdAt: Long,               // 创建时间
    val updatedAt: Long,               // 更新时间
    val messages: List<ChatMessage>,   // 消息列表
    val completed: Boolean              // 是否完成
)
```

#### 2.3.2 对话管理器

```kotlin
class ChatManager {
    // 发送消息并获取流式响应
    fun sendMessage(
        message: String,
        sessionId: String? = null
    ): Flow<List<ChatMessage>>
    
    // 重新生成最后AI回复
    fun regenerateLast(): Flow<List<ChatMessage>>
    
    // 取消当前请求
    fun cancelRequest()
    
    // 加载历史会话
    fun loadSession(sessionId: String)
    
    // 清空当前会话
    fun clearSession()
}
```

### 2.4 提示模板模块

#### 2.4.1 提示类型定义

参考anx53项目的AiPrompts枚举，定义以下提示类型：

```kotlin
enum class PromptType {
    TEST,                      // AI连接测试
    SUMMARY_THE_CHAPTER,       // 章节摘要
    SUMMARY_THE_BOOK,          // 全书总结
    SUMMARY_PREVIOUS_CONTENT,  // 回顾前情
    MINDMAP                   // 思维导图
}
```

#### 2.4.2 提示模板定义

**章节摘要模板**：

```
Summarize the chapter content. Your reply must follow these requirements:
Language: Use the same language as the original chapter content.
Length: 8-10 complete sentences.
Structure: Three paragraphs: Main plot, Core characters, Themes/messages.
Style: Avoid boilerplate phrases like "This chapter describes..."
Perspective: Maintain a literary analysis perspective, not just narration.
```

**全书总结模板**：

```
Generate a book summary
[Requirements]:
Language matches the book title's language
Central conflict (highlight with » symbol)
3 core characters + their motivations (name + critical choice)
Theme keywords (3-5)
Avoid spoiling the final outcome
```

#### 2.4.3 提示生成器

```kotlin
class PromptGenerator {
    // 生成章节摘要提示
    fun generateSummaryChapterPrompt(): List<ChatMessage>
    
    // 生成全书总结提示
    fun generateSummaryBookPrompt(): List<ChatMessage>
    
    // 生成前情回顾提示
    fun generatePreviousContentPrompt(previousContent: String): List<ChatMessage>
    
    // 生成思维导图提示
    fun generateMindmapPrompt(): List<ChatMessage>
}
```

### 2.5 Agent工具系统模块

#### 2.5.1 工具接口设计

```kotlin
interface AiTool {
    val name: String           // 工具名称
    val description: String    // 工具描述
    val inputSchema: Map<String, Any>  // 输入schema
    
    suspend fun execute(input: Map<String, Any>): Map<String, Any>
}
```

#### 2.5.2 核心工具实现

**当前章节内容工具**：

```
工具名称：current_chapter_content
功能描述：获取用户当前正在阅读的章节纯文本内容。用于引用当前章节内容或进行摘要时调用。
返回：章节内容字符串
```

**当前书籍目录工具**：

```
工具名称：current_book_toc
功能描述：获取用户当前阅读书籍的目录结构，包括当前阅读位置和各章节进度百分比。
返回：isReading标志、当前位置、完整目录树
```

**阅读历史工具**：

```
工具名称：reading_history
功能描述：查询用户的阅读历史记录，包括最近阅读的书籍、阅读时长、阅读进度等。
返回：阅读历史列表
```

**笔记搜索工具**：

```
工具名称：notes_search
功能描述：搜索用户的读书笔记，可以按关键词搜索或获取所有笔记。
返回：笔记列表
```

#### 2.5.3 工具注册表

```kotlin
class ToolRegistry {
    private val tools = mutableMapOf<String, AiTool>()
    
    fun register(tool: AiTool)
    fun getTool(name: String): AiTool?
    fun listTools(): List<AiTool>
    fun getToolDefinitions(): List<ToolDefinition>
}
```

---

## 三、功能实现详细方案

### 3.1 智能问答功能实现

#### 3.1.1 入口设计

在阅读页面添加AI助手入口，可以通过悬浮按钮或菜单触发。

```kotlin
// 在ReadBookActivity中添加
private fun showAiAssistant() {
    val intent = Intent(this, AiChatActivity::class.java)
    // 传递当前阅读上下文
    intent.putExtra("bookUrl", currentBookUrl)
    intent.putExtra("chapterTitle", currentChapterTitle)
    startActivity(intent)
}
```

#### 3.1.2 对话界面设计

对话界面包含以下组件：

- **消息列表**：展示对话历史，区分用户消息和AI消息
- **输入框**：支持多行输入，发送按钮
- **服务商选择器**：切换AI服务商
- **预设快捷提示**：快速提问按钮
- **历史记录入口**：查看过往对话

#### 3.1.3 消息处理流程

```
用户输入 → 构建消息 → 添加到会话 → 发送API请求 → 
流式接收响应 → 实时更新UI → 保存会话 → 完成
```

```kotlin
// 伪代码实现
class AiChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            // 1. 添加用户消息
            val newMessages = _messages.value + HumanMessage(content)
            _messages.value = newMessages
            
            // 2. 添加占位AI消息
            _messages.value = newMessages + AiMessage("")
            
            // 3. 调用AI服务
            aiService.chat(
                messages = newMessages,
                onChunk = { chunk ->
                    // 4. 流式更新AI消息
                    val currentMessages = _messages.value.toMutableList()
                    val lastMsg = currentMessages.last() as AiMessage
                    currentMessages[currentMessages.lastIndex] = 
                        AiMessage(lastMsg.content + chunk)
                    _messages.value = currentMessages
                }
            )
            
            // 5. 保存会话
            sessionManager.saveSession(currentSession)
        }
    }
}
```

#### 3.1.4 预设快捷提示

参考anx53，提供以下预设快捷提示：

| 提示标签 | 提示内容 |
|---------|----------|
| 解释这段 | 详细解释选中内容的含义和背景 |
| 有什么看法 | 你对这段内容有什么看法和分析 |
| 总结一下 | 用简洁的语言总结这段内容 |
| 分析一下 | 从文学角度分析这段内容的写法 |
| 后续如何 | 请推测后续情节可能如何发展 |

### 3.2 阅读内容分析功能实现

#### 3.2.1 功能入口

在阅读菜单中添加分析选项。

```kotlin
// 在阅读菜单中添加
menu.add(0, MENU_AI_ANALYSIS, 0, "AI分析").apply {
    setIcon(R.drawable.ic_ai)
    setOnMenuItemClickListener {
        showAnalysisDialog()
        true
    }
}

private fun showAnalysisDialog() {
    val options = arrayOf("章节摘要", "全书总结", "前情回顾", "生成思维导图")
    AlertDialog.Builder(this)
        .setItems(options) { _, which ->
            when (which) {
                0 -> requestChapterSummary()
                1 -> requestBookSummary()
                2 -> requestPreviousSummary()
                3 -> requestMindmap()
            }
        }
        .show()
}
```

#### 3.2.2 章节摘要功能

```kotlin
private fun requestChapterSummary() {
    viewModelScope.launch {
        // 1. 获取当前章节内容
        val chapterContent = readBookModel.getCurrentChapterContent()
        
        // 2. 生成摘要提示
        val messages = promptGenerator.generateSummaryChapterPrompt()
        
        // 3. 添加章节内容作为上下文
        val fullMessages = messages + HumanMessage("请分析以下内容：\n$chapterContent")
        
        // 4. 调用AI并显示结果
        aiService.chat(fullMessages) { result ->
            showAnalysisResult(result)
        }
    }
}
```

#### 3.2.3 全书总结功能

```kotlin
private fun requestBookSummary() {
    viewModelScope.launch {
        // 1. 获取书籍元信息
        val bookInfo = bookDao.getBook(bookUrl)
        val toc = chapterDao.getChapterList(bookUrl)
        
        // 2. 构建书籍信息摘要
        val bookIntro = buildString {
            appendLine("书名：${bookInfo.name}")
            appendLine("作者：${bookInfo.author}")
            appendLine("简介：${bookInfo.intro}")
            appendLine("目录：${toc.take(10).joinToString(", ") { it.title }}")
        }
        
        // 3. 生成总结提示
        val messages = promptGenerator.generateSummaryBookPrompt()
        val fullMessages = messages + HumanMessage("请总结以下书籍：\n$bookIntro")
        
        // 4. 调用AI
        aiService.chat(fullMessages) { result ->
            showAnalysisResult(result)
        }
    }
}
```

#### 3.2.4 分析结果展示

```kotlin
private fun showAnalysisResult(content: String) {
    val dialog = AnalysisResultDialog(this, content)
    dialog.show()
}

// AnalysisResultDialog布局
// - 标题栏：分析类型
// - 内容区：Markdown渲染的分析结果
// - 底部按钮：复制、分享、关闭
```

---

## 四、数据访问层设计

### 4.1 会话历史存储

使用JSON文件存储会话历史，参考anx53的AiHistoryStore实现。

```kotlin
class SessionStorage {
    private val fileName = "ai_chat_history.json"
    
    suspend fun saveSessions(sessions: List<ChatSession>)
    
    suspend fun loadSessions(): List<ChatSession>
    
    suspend fun addOrUpdateSession(session: ChatSession)
    
    suspend fun deleteSession(sessionId: String)
    
    suspend fun clearAllSessions()
}
```

### 4.2 数据获取接口

#### 4.2.1 获取当前阅读状态

```kotlin
class ReadingStateManager {
    // 获取当前阅读的书籍信息
    fun getCurrentBook(): Book?
    
    // 获取当前章节标题
    fun getCurrentChapterTitle(): String
    
    // 获取当前章节内容
    suspend fun getCurrentChapterContent(): String
    
    // 获取当前阅读进度
    fun getReadingProgress(): Float
    
    // 获取目录
    fun getTableOfContents(): List<TocItem>
}
```

#### 4.2.2 获取笔记数据

```kotlin
class NotesRepository {
    // 获取当前书籍的笔记
    fun getBookNotes(bookUrl: String): List<BookAnnotation>
    
    // 搜索笔记
    fun searchNotes(keyword: String): List<BookAnnotation>
    
    // 获取所有笔记
    fun getAllNotes(): List<BookAnnotation>
}
```

#### 4.2.3 获取阅读历史

```kotlin
class ReadingHistoryRepository {
    // 获取阅读历史记录
    fun getReadHistory(days: Int = 30): List<ReadRecord>
    
    // 获取最近阅读的书籍
    fun getRecentBooks(limit: Int = 10): List<Book>
}
```

---

## 五、UI组件设计

### 5.1 对话Activity

```kotlin
class AiChatActivity : AppCompatActivity() {
    // 布局结构：
    // - Toolbar：标题、历史入口、更多选项
    // - RecyclerView：消息列表
    // - InputBar：输入框、发送按钮、服务商选择
    
    // 消息项布局：
    // - 用户消息：右对齐，背景色不同
    // - AI消息：左对齐，显示思考过程（可折叠）
}
```

### 5.2 设置页面

在现有设置中添加AI设置入口。

```
设置
├── AI设置
│   ├── 服务商管理
│   │   ├── 添加服务商
│   │   ├── 编辑服务商
│   │   └── 删除服务商
│   ├── API密钥管理
│   ├── 默认模型选择
│   ├── 请求限流设置
│   └── 缓存设置
```

### 5.3 消息展示组件

```kotlin
class MessageAdapter : RecyclerView.Adapter<MessageViewHolder>() {
    // 消息类型：
    // - 用户消息 (TYPE_USER)
    // - AI消息 (TYPE_AI)
    // - 思考过程 (TYPE_THINKING) - 可折叠显示
    
    // AI消息支持：
    // - 文本展示（支持Markdown）
    // - 复制按钮
    // - 重新生成按钮（仅最后一条）
}
```

---

## 六、关键实现细节

### 6.1 流式响应处理

```kotlin
fun Flow<String>.collectChunks(onChunk: (String) -> Unit): Job {
    return coroutineScope {
        launch {
            var accumulated = ""
            collect { chunk ->
                accumulated += chunk
                onChunk(accumulated)
            }
        }
    }
}
```

### 6.2 请求取消机制

```kotlin
class CancellableRequest {
    private var job: Job? = null
    private var httpCall: Call? = null
    
    fun start(request: Request, onResponse: (Response) -> Unit) {
        job = CoroutineScope(Dispatchers.IO).launch {
            httpCall = okHttpClient.newCall(request)
            val response = httpCall!!.execute()
            onResponse(response)
        }
    }
    
    fun cancel() {
        job?.cancel()
        httpCall?.cancel()
    }
}
```

### 6.3 错误处理

```kotlin
sealed class AiError {
    data class NetworkError(val message: String) : AiError()
    data class AuthError(val message: String) : AiError()  // 401
    data class RateLimitError(val message: String) : AiError()  // 429
    data class TimeoutError(val message: String) : AiError()
    data class UnknownError(val message: String) : AiError()
}

fun parseAiError(response: Response): AiError {
    return when (response.code) {
        401 -> AuthError("API密钥无效或已过期")
        429 -> RateLimitError("请求频率超限，请稍后重试")
        else -> UnknownError(response.message)
    }
}
```

---

## 七、功能模块清单

### 7.1 智能问答模块

| 序号 | 功能点 | 说明 |
|------|--------|------|
| 1 | AI设置页面 | 服务商配置、API密钥管理 |
| 2 | 对话界面 | 消息列表、输入框、发送按钮 |
| 3 | 服务商切换 | 运行时切换AI服务商 |
| 4 | 预设快捷提示 | 快速提问按钮 |
| 5 | 对话历史 | 查看、恢复、删除历史会话 |
| 6 | 会话管理 | 创建新会话、继续会话 |
| 7 | 流式响应 | 实时显示AI回复 |
| 8 | 请求取消 | 取消正在进行的请求 |
| 9 | 消息操作 | 复制、重新生成 |
| 10 | 阅读上下文 | AI可获取当前阅读内容 |

### 7.2 阅读内容分析模块

| 序号 | 功能点 | 说明 |
|------|--------|------|
| 1 | 章节摘要 | 生成当前章节的摘要 |
| 2 | 全书总结 | 生成书籍的整体总结 |
| 3 | 前情回顾 | 回顾之前阅读的内容 |
| 4 | 思维导图 | 生成内容结构图（可选） |
| 5 | 分析结果展示 | 专门的结果展示界面 |
| 6 | 结果操作 | 复制、分享分析结果 |

### 7.3 Agent工具模块

| 序号 | 工具 | 说明 |
|------|------|------|
| 1 | current_chapter_content | 获取当前章节内容 |
| 2 | current_book_toc | 获取书籍目录 |
| 3 | reading_history | 获取阅读历史 |
| 4 | notes_search | 搜索用户笔记 |
| 5 | current_reading_metadata | 获取阅读元数据 |

---

## 八、技术要点总结

**核心实现要点**：

1. **多服务商支持**：通过AiProtocol抽象不同AI服务商的API差异，使用统一的接口屏蔽底层实现细节
2. **流式响应**：使用OkHttp的流式读取实现实时响应，提升用户体验
3. **请求限流**：滑动窗口算法控制请求频率，避免触发API限制
4. **密钥轮换**：多个API密钥时自动轮换使用，分散请求负载
5. **会话管理**：JSON文件存储会话历史，支持恢复和继续对话
6. **Agent工具**：通过定义标准工具接口，让AI能够访问阅读器数据

**与dai411现有架构的集成点**：

1. 利用现有OkHttp网络层进行AI API调用
2. 使用SharedPreferences存储AI配置
3. 复用Book、BookChapter、BookAnnotation等现有数据实体
4. 在ReadBookActivity中添加AI入口
5. 使用ViewModel + StateFlow管理UI状态