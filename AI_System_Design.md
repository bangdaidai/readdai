# dai411项目AI功能设计方案

## 设计理念

本方案综合参考anx53（Flutter）和ReadAny（Tauri + React）两个项目的AI功能实现，结合dai411（Kotlin/Android）项目自身特点，设计一套**智能问答 + 阅读内容分析**为核心的AI功能系统。

设计原则：

- **实用优先**：聚焦用户阅读场景的高频需求
- **渐进增强**：基础功能可用后再迭代高级功能
- **本地优先**：敏感数据本地处理，保护用户隐私
- **多商支持**：抽象AI服务商接口，降低依赖风险

---

## 一、系统架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         应用层 (UI Layer)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────┐  │
│  │ AI对话页面   │  │ 分析功能    │  │ 设置页面    │  │ 阅读页面 │  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────┬────┘  │
└─────────┼────────────────┼────────────────┼────────────────┼───────┘
          │                │                │                │
┌─────────┼────────────────┼────────────────┼────────────────┼───────┐
│         ▼                ▼                ▼                ▼       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    业务层 (ViewModel)                      │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │   │
│  │  │对话管理器  │  │分析管理器  │  │ Skills管理器│          │   │
│  │  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘          │   │
│  └────────┼────────────────┼────────────────┼──────────────────┘   │
└───────────┼────────────────┼────────────────┼─────────────────────┘
            │                │                │
┌───────────┼────────────────┼────────────────┼─────────────────────┐
│           ▼                ▼                ▼                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    AI服务层                                 │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │   │
│  │  │AI客户端    │  │提示引擎    │  │工具注册表  │          │   │
│  │  └────────────┘  └────────────┘  └────────────┘          │   │
│  └─────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
            │                │                │
┌───────────┼────────────────┼────────────────┼─────────────────────┐
│           ▼                ▼                ▼                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    数据层                                   │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐          │   │
│  │  │配置存储    │  │会话存储    │  │阅读数据访问│          │   │
│  │  └────────────┘  └────────────┘  └────────────┘          │   │
│  └─────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 模块层次说明

| 层次 | 说明 | 关键组件 |
|------|------|----------|
| **AI服务层** | 负责与AI服务商通信 | AI客户端、提示引擎、工具注册表 |
| **业务层** | 负责功能逻辑和状态管理 | 对话管理器、分析管理器、Skills管理器 |
| **数据层** | 负责数据持久化和访问 | 配置存储、会话存储、阅读数据访问 |
| **应用层** | 负责用户界面展示 | 对话页面、分析功能、设置页面 |

---

## 二、核心功能设计

### 2.1 智能问答功能

#### 2.1.1 功能定位

用户在阅读过程中或阅读后，可以与AI助手进行自然语言交互，获取与书籍内容相关的帮助。AI能够感知用户的阅读上下文，提供个性化的问答服务。

#### 2.1.2 功能特性

**多轮对话**：

- 支持多轮对话，保持上下文连贯
- AI能够记住对话历史，提供连续性交互
- 会话可保存和恢复

**阅读上下文感知**：

- AI自动获取当前阅读的书籍信息
- 可访问当前章节内容、目录结构
- 可查询用户笔记、高亮、书签

**预设快捷提示**：

| 提示 | 说明 |
|------|------|
| 这段讲了什么 | 解释选中或当前段落的内容 |
| 帮我分析一下 | 从文学/内容角度分析 |
| 后续会怎样 | 基于已有内容推测后续发展 |
| 总结本章 | 生成当前章节摘要 |
| 人物有哪些 | 提取本章/全书人物 |

**对话管理**：

- 查看历史对话列表
- 恢复历史对话
- 删除单个对话或清空历史

#### 2.1.3 交互流程

```
用户点击AI入口
       ↓
打开AI对话页面，传递当前阅读上下文
       ↓
用户输入问题 或 点击快捷提示
       ↓
系统构建消息（用户问题 + 阅读上下文）
       ↓
调用AI API，流式获取响应
       ↓
实时显示AI回复
       ↓
保存对话到会话历史
```

### 2.2 阅读内容分析功能

#### 2.2.1 功能定位

用户可以请求AI对当前阅读的章节或全书进行深度分析，生成结构化的分析结果。

#### 2.2.2 功能特性

**章节摘要**：

- 生成当前章节的内容摘要
- 提取核心情节、关键人物、主题思想
- 支持中英文，自动识别语言

**全书总结**：

- 基于书籍信息（书名、作者、简介、目录）生成总结
- 提取核心冲突、主要人物、主题关键词
- 避免剧透最终结局

**前情回顾**：

- 当用户间隔较长时间后继续阅读时，提供前情回顾
- 基于之前阅读的进度生成摘要
- 帮助用户快速回忆之前内容

**内容分析**（进阶）：

- 人物关系分析
- 情节发展脉络
- 写作手法分析

#### 2.2.3 交互入口

在阅读页面菜单中添加：

```
阅读菜单
├── AI分析
│   ├── 章节摘要
│   ├── 全书总结
│   ├── 前情回顾
│   └── 内容分析
```

### 2.3 Skills系统（技能系统）

#### 2.3.1 设计理念

参考ReadAny的Skills系统，设计一套可扩展的AI技能框架。每个Skill是一个预定义的AI能力，可以被智能问答调用。

#### 2.3.2 内置Skills

| Skill | 功能 | 说明 |
|-------|------|------|
| **摘要器** | Summarizer | 生成章节/全书摘要 |
| **解释者** | Explainer | 解释专业术语、概念 |
| **人物追踪** | CharacterTracker | 追踪和汇总人物信息 |
| **翻译** | Translator | 翻译外文内容（可选） |
| **问答** | QASystem | 基于内容回答问题 |
| **笔记整理** | NoteOrganizer | 整理用户笔记 |

#### 2.3.3 Skill定义格式

```json
{
  "id": "summarizer",
  "name": "摘要器",
  "description": "生成书籍内容的摘要",
  "prompt_template": "请用简洁的语言总结以下内容：\n{{content}}",
  "parameters": [
    {
      "name": "content",
      "type": "string",
      "required": true,
      "description": "要摘要的内容"
    }
  ]
}
```

#### 2.3.4 Skill调用机制

用户可以通过自然语言触发Skill：

```
用户：帮我总结一下这章
    ↓
系统识别意图：调用Summarizer Skill
    ↓
提取参数：当前章节内容
    ↓
填充Prompt模板
    ↓
调用AI获取结果
    ↓
返回格式化结果
```

---

## 三、AI工具系统设计

### 3.1 工具架构

参考anx53的Agent工具系统，设计一套适用于dai411的AI工具框架。

### 3.2 核心工具

| 工具ID | 名称 | 功能 | 返回数据 |
|--------|------|------|----------|
| **current_book_info** | 当前书籍信息 | 获取当前阅读书籍的元信息 | 书名、作者、简介、阅读进度 |
| **current_chapter** | 当前章节 | 获取当前正在阅读的章节内容 | 章节标题、章节内容 |
| **book_toc** | 书籍目录 | 获取书籍完整目录结构 | 目录树、当前章节位置 |
| **book_notes** | 书籍笔记 | 获取当前书籍的所有笔记 | 笔记列表 |
| **book_highlights** | 书籍高亮 | 获取当前书籍的高亮内容 | 高亮列表 |
| **reading_history** | 阅读历史 | 获取用户阅读历史 | 阅读记录列表 |
| **search_content** | 内容搜索 | 在书籍中搜索内容 | 搜索结果 |
| **bookmarks** | 书签列表 | 获取当前书籍的书签 | 书签列表 |

### 3.3 工具接口设计

```kotlin
interface AiTool {
    val id: String           // 工具唯一标识
    val name: String        // 工具名称
    val description: String // 工具描述
    
    // 执行工具
    suspend fun execute(params: Map<String, Any>): ToolResult
    
    // 获取输入schema
    fun getInputSchema(): Map<String, Any>
}

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
)
```

### 3.4 工具注册表

```kotlin
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AiTool>()
    
    fun register(tool: AiTool)
    fun getTool(id: String): AiTool?
    fun listAllTools(): List<AiTool>
    fun getToolDefinitions(): List<ToolDefinition>
}
```

### 3.5 工具执行流程

```
用户问题：这本书的作者还写过什么书？
    ↓
1. LLM分析用户意图
    ↓
2. 确定需要调用的工具：current_book_info + reading_history
    ↓
3. 并行执行工具获取数据
    ↓
4. 将工具返回结果整合到Prompt
    ↓
5. 再次调用LLM生成最终回答
    ↓
6. 返回结果给用户
```

---

## 四、多服务商支持设计

### 4.1 服务商抽象

设计统一的服务商接口，支持运行时切换：

```kotlin
interface AiProvider {
    val id: String
    val name: String
    val logo: String?  // Logo资源路径
    
    suspend fun chat(
        messages: List<ChatMessage>,
        options: ChatOptions
    ): Flow<String>  // 流式响应
    
    fun getModels(): List<String>
    fun validateConfig(): Boolean
}
```

### 4.2 内置服务商

| 服务商 | 协议 | 默认模型 | 特点 |
|--------|------|----------|------|
| **通用** | OpenAI兼容 | qwen-long | 阿里云，便宜 |
| **OpenAI** | OpenAI | gpt-4o-mini | 官方模型 |
| **Claude** | Anthropic | claude-3-5-sonnet | 强推理能力 |
| **Gemini** | Google | gemini-2.5-flash | 性价比高 |
| **DeepSeek** | OpenAI兼容 | deepseek-chat | 国产模型 |

### 4.3 配置管理

```kotlin
data class ProviderConfig(
    val id: String,
    val name: String,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true
)

object AiConfigManager {
    // 获取所有服务商配置
    fun getProviders(): List<ProviderConfig>
    
    // 保存服务商配置
    fun saveProviders(configs: List<ProviderConfig>)
    
    // 获取当前选中的服务商
    fun getSelectedProvider(): ProviderConfig?
    
    // 设置选中的服务商
    fun setSelectedProvider(providerId: String)
}
```

---

## 五、数据模型设计

### 5.1 对话会话

```kotlin
@Entity(tableName = "ai_chat_sessions")
data class AiChatSession(
    @PrimaryKey
    val id: String,
    val providerId: String,
    val model: String,
    val title: String,        // 会话标题（首条用户消息）
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

@Entity(tableName = "ai_chat_messages")
data class AiChatMessage(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,         // "user" / "assistant" / "system"
    val content: String,
    val createdAt: Long
)
```

### 5.2 AI配置

```kotlin
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val sortOrder: Int
)
```

### 5.3 Skills配置

```kotlin
@Entity(tableName = "ai_skills")
data class AiSkill(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val promptTemplate: String,
    val enabled: Boolean,
    val isBuiltin: Boolean
)
```

---

## 六、UI设计

### 6.1 AI对话页面

```
┌────────────────────────────────────────┐
│ ← AI阅读助手                    ⋮     │
├────────────────────────────────────────┤
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ AI                              │ │
│  │ 你好！我是你的阅读助手。可以     │ │
│  │ 帮你解答阅读中的问题...         │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ 用户                              │ │
│  │ 这章主要讲了什么？               │ │
│  └──────────────────────────────────┘ │
│                                        │
│  ┌──────────────────────────────────┐ │
│  │ AI                              │ │
│  │ 本章主要讲述了...（流式输出中）  │ │
│  └──────────────────────────────────┘ │
│                                        │
├────────────────────────────────────────┤
│ [解释] [分析] [总结] [推测]  +────────┤
│ 你有什么问题？...                   [发送]│
└────────────────────────────────────────┘
```

### 6.2 AI设置页面

```
AI设置
├── 当前服务商
│   └── [下拉选择] OpenAI / Claude / Gemini / ...
├── API配置
│   ├── API Key：┌─────────────────────┐│
│   └── 模型：   │ gpt-4o-mini      ▼││
├── 请求设置
│   ├── 每分钟请求限制：[滑块 1-60]
│   └── 密钥轮换：[开关]
├── Skills设置
│   ├── ☑ 摘要器
│   ├── ☑ 解释者
│   ├── ☑ 人物追踪
│   ├── ☑ 问答系统
│   └── ☑ 笔记整理
└── 缓存管理
    └── 清除对话历史
```

### 6.3 阅读分析入口

在阅读页面底部菜单添加AI分析按钮：

```
┌────────────────────────────────────────┐
│                   📖                   │
│              第15章 危机               │
│                                        │
│         （阅读内容区域）              │
│                                        │
├────────────────────────────────────────┤
│  [目录] [笔记] [书签] [⚡AI] [更多]   │
└────────────────────────────────────────┘
```

点击AI按钮后弹出分析选项：

```
┌────────────────────────────────────────┐
│           AI阅读助手                   │
├────────────────────────────────────────┤
│  🟡 章节摘要                          │
│     用简洁语言总结本章内容             │
│                                        │
│  📚 全书总结                          │
│     生成书籍整体概述                   │
│                                        │
│  🔄 前情回顾                          │
│     回顾之前的阅读内容                 │
│                                        │
│  📝 内容分析                          │
│     人物、情节、写作手法分析           │
│                                        │
│  💬 智能问答                          │
│     向AI助手提问                       │
└────────────────────────────────────────┘
```

---

## 七、技术实现要点

### 7.1 网络层

基于项目现有的OkHttp扩展AI API调用：

```kotlin
class AiApiClient(private val provider: AiProvider) {
    
    suspend fun chat(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Result<String> {
        // 1. 构建请求
        val request = buildRequest(messages)
        
        // 2. 执行请求并处理流式响应
        val response = okHttpClient.newCall(request).execute()
        
        // 3. 解析SSE流
        response.body?.byteStream()?.buffered()?.let { stream ->
            parseSseStream(stream, onChunk)
        }
        
        return Result.success(accumulatedContent)
    }
    
    private fun parseSseStream(
        stream: BufferedInputStream,
        onChunk: (String) -> Unit
    ) {
        // 解析Server-Sent Events流
        // 提取data字段，触发onChunk回调
    }
}
```

### 7.2 提示词引擎

```kotlin
class PromptEngine {
    
    fun buildSystemPrompt(context: ReadingContext): String {
        return """
            你是dai阅读器的AI阅读助手。
            用户的当前阅读状态：
            - 书籍：${context.bookTitle}
            - 作者：${context.author}
            - 进度：第${context.chapterIndex}章 / 共${context.totalChapters}章
            
            你可以使用的工具：
            ${context.availableTools.joinToString("\n") { "- ${it.name}: ${it.description}" }}
            
            请根据用户的问题和阅读上下文，提供准确、有帮助的回答。
        """.trimIndent()
    }
    
    fun buildSkillPrompt(skill: AiSkill, params: Map<String, Any>): String {
        var prompt = skill.promptTemplate
        params.forEach { (key, value) ->
            prompt = prompt.replace("{{$key}}", value.toString())
        }
        return prompt
    }
}
```

### 7.3 请求限流

```kotlin
class RateLimiter(private val rpm: Int) {
    private val timestamps = ArrayDeque<Long>()
    
    suspend fun throttle() {
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
        
        timestamps.addLast(now)
    }
}
```

---

## 八、功能模块清单

### 8.1 智能问答模块

| 序号 | 功能 | 优先级 | 说明 |
|------|------|--------|------|
| 1 | AI设置页面 | P0 | 服务商配置、API Key管理 |
| 2 | 对话界面 | P0 | 消息列表、输入框、发送 |
| 3 | 服务商切换 | P0 | 运行时切换AI服务商 |
| 4 | 快捷提示 | P0 | 预设问题按钮 |
| 5 | 会话历史 | P1 | 查看、恢复、删除 |
| 6 | 流式响应 | P0 | 实时显示AI回复 |
| 7 | 请求取消 | P0 | 取消进行中的请求 |
| 8 | 消息操作 | P1 | 复制、重新生成 |

### 8.2 阅读分析模块

| 序号 | 功能 | 优先级 | 说明 |
|------|------|--------|------|
| 1 | 章节摘要 | P0 | 生成当前章节摘要 |
| 2 | 全书总结 | P0 | 生成书籍整体总结 |
| 3 | 前情回顾 | P1 | 回顾之前阅读内容 |
| 4 | 内容分析 | P2 | 进阶分析功能 |
| 5 | 结果操作 | P1 | 复制、分享结果 |

### 8.3 工具系统模块

| 序号 | 工具 | 优先级 | 说明 |
|------|------|--------|------|
| 1 | current_book_info | P0 | 获取书籍信息 |
| 2 | current_chapter | P0 | 获取章节内容 |
| 3 | book_toc | P0 | 获取目录结构 |
| 4 | book_notes | P1 | 获取笔记 |
| 5 | reading_history | P1 | 获取阅读历史 |
| 6 | search_content | P2 | 搜索内容 |

### 8.4 Skills系统模块

| 序号 | Skill | 优先级 | 说明 |
|------|-------|--------|------|
| 1 | 摘要器 | P0 | 生成摘要 |
| 2 | 解释者 | P1 | 解释术语 |
| 3 | 人物追踪 | P2 | 追踪人物 |
| 4 | 问答系统 | P0 | 回答问题 |

---

## 九、与现有系统集成

### 9.1 数据复用

| 现有数据 | 用途 | 集成方式 |
|----------|------|----------|
| Book | 书籍信息 | 直接查询BookDao |
| BookChapter | 章节内容 | 通过ReadBookModel获取 |
| BookAnnotation | 笔记 | 查询BookAnnotationDao |
| Bookmark | 书签 | 查询BookmarkDao |
| ReadRecord | 阅读历史 | 查询ReadRecordDao |

### 9.2 入口集成

- **阅读页面**：在菜单添加AI分析入口
- **主导航**：添加AI助手Tab或侧边入口
- **设置页面**：添加AI设置子页面

### 9.3 存储集成

- 使用Room数据库存储AI配置和会话
- 使用SharedPreferences存储轻量配置

---

## 十、技术选型总结

| 组件 | 选型 | 说明 |
|------|------|------|
| **AI框架** | LangChain4j | Java/Kotlin版LangChain |
| **网络层** | OkHttp | 复用现有 |
| **数据库** | Room | 复用现有 |
| **状态管理** | Kotlin Flow + StateFlow | 协程异步处理 |
| **JSON解析** | Kotlinx Serialization | 现有使用 |

---

## 十一、总结

本方案综合了anx53和ReadAny两个项目的优点：

1. **anx53的借鉴点**：
   - Agent工具系统设计
   - 多服务商支持架构
   - 流式响应处理
   - 请求限流机制

2. **ReadAny的借鉴点**：
   - Skills系统设计理念
   - 预设快捷提示
   - 统一的Prompt模板

3. **dai411适配**：
   - 使用Kotlin实现
   - 复用现有Room数据库
   - 集成到现有阅读器UI
   - LangChain4j作为AI框架

方案设计遵循实用优先原则，首先实现核心功能（P0），再逐步迭代进阶功能（P1/P2）。