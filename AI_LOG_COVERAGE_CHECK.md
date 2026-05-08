# AI日志覆盖情况检查

## 已添加日志的地方 ✅

### 1. ReadBookActivity (阅读页面)
- ✅ AI按钮点击事件 (`onClickAiButton`)
  - 记录书籍信息和章节索引
  - 记录章节内容获取结果
  - 记录菜单显示状态

### 2. AiAnalysisDialog (AI分析弹窗)
- ✅ 技能执行
  - 记录技能ID和内容长度
  - 记录变量映射信息
  - 记录技能找不到的错误

### 3. 前情提要功能 (ReadBookActivity)
- ✅ 前情提要检查
  - 记录开关状态
  - 记录当前章节索引
  - 记录弹窗显示状态
  - 记录前文内容获取情况

## 需要添加日志的地方 ❌

### 1. AiService (核心AI服务)
- ❌ `init()` - 初始化过程
- ❌ `chat()` - 聊天请求
  - 流式模式和非流式模式
  - Agent循环执行
  - 工具调用过程
- ❌ `executeSkill()` - 技能执行
  - 技能参数替换
  - API调用结果
- ❌ `getCurrentProvider()` - 获取服务商
- ❌ 错误处理

### 2. AiChatActivity (AI聊天页面)
- ❌ `onCreate()` - 页面初始化
- ❌ `sendMessage()` - 发送消息
  - 消息构建
  - 会话保存
  - 流式响应接收
- ❌ `loadSession()` - 加载会话历史
- ❌ `saveDraftSession()` - 保存草稿
- ❌ 工具调用显示
- ❌ 模型选择
- ❌ 历史记录操作

### 3. AiApiClient (API客户端)
- ❌ HTTP请求发送
- ❌ 响应接收和解析
- ❌ 错误处理
- ❌ 重试机制

### 4. ReadingContextService (阅读上下文服务)
- ❌ 上下文更新
- ❌ 上下文读取
- ❌ 缓存管理

### 5. SkillManager (技能管理器)
- ❌ 技能加载
- ❌ 技能注册
- ❌ 默认技能初始化

### 6. PromptManager (提示词管理器)
- ❌ 提示词加载
- ❌ 提示词保存
- ❌ 系统提示词构建

### 7. AiToolRegistry (工具注册表)
- ❌ 工具注册
- ❌ 工具执行
- ❌ 工具调用结果

### 8. AiHistoryStore (历史记录存储)
- ❌ 历史读取
- ❌ 历史保存
- ❌ 历史删除

### 9. VectorConfigManager (向量配置管理)
- ❌ 配置加载和保存
- ❌ 向量化过程

## 建议添加的日志点

### 高优先级 🔴
1. **AiService.chat()** - 每次聊天请求的开始和结束
2. **AiService.executeSkill()** - 技能执行的开始和结果
3. **AiChatActivity.sendMessage()** - 用户发送消息
4. **AiApiClient** - API请求和响应
5. **错误处理** - 所有异常捕获处

### 中优先级 🟡
6. **ReadingContextService** - 上下文更新
7. **AiHistoryStore** - 历史记录的读写
8. **SkillManager** - 技能的加载和执行
9. **PromptManager** - 提示词的构建

### 低优先级 🟢
10. **初始化和配置加载**
11. **工具注册和执行**
12. **向量模型相关操作**

## 日志添加示例

### AiService.kt
```kotlin
suspend fun init() {
    AiLogManager.log(LogLevel.INFO, "AiService", "开始初始化AI服务")
    
    promptManager = PromptManager(context)
    skillManager = SkillManager(context)
    
    AiHistoryStore.init(context)
    promptManager?.initDefaultPrompts()
    skillManager?.initDefaultSkills()
    
    val dao = aiDatabase.aiDao()
    currentProvider = dao.getDefaultProvider() ?: dao.getAllProviders().firstOrNull()
    
    if (currentProvider != null) {
        AiLogManager.log(LogLevel.INFO, "AiService", "使用服务商: ${currentProvider!!.name}")
    } else {
        AiLogManager.log(LogLevel.WARNING, "AiService", "未找到可用的AI服务商")
    }
    
    // ... 其余代码
    
    isInitialized = true
    AiLogManager.log(LogLevel.INFO, "AiService", "AI服务初始化完成")
}

fun chat(message: String, session: AiChatSession? = null): Flow<ChatResult> {
    AiLogManager.log(LogLevel.INFO, "AiService", "开始聊天: message长度=${message.length}, session=${session?.id}")
    
    return if (AppConfig.aiStreamMode) {
        AiLogManager.log(LogLevel.DEBUG, "AiService", "使用流式模式")
        chatStream(message, session)
    } else {
        AiLogManager.log(LogLevel.DEBUG, "AiService", "使用非流式模式")
        chatNoStream(message, session)
    }
}
```

### AiChatActivity.kt
```kotlin
private fun sendMessage(content: String, isRegenerate: Boolean = false) {
    AiLogManager.log(LogLevel.INFO, "AiChat", "用户发送消息: length=${content.length}")
    
    // ... 原有代码
    
    lifecycleScope.launch {
        aiService.init()
        
        aiService.chat(messageWithOptions, currentSession).collectLatest { result ->
            when (result) {
                is ChatResult.Chunk -> {
                    // 接收流式响应
                }
                is ChatResult.Success -> {
                    AiLogManager.log(LogLevel.INFO, "AiChat", "聊天完成: response长度=${result.content.length}")
                }
                is ChatResult.Error -> {
                    AiLogManager.log(LogLevel.ERROR, "AiChat", "聊天失败: ${result.message}")
                }
            }
        }
    }
}
```

### AiApiClient.kt
```kotlin
suspend fun chatCompletion(request: ChatRequest): Flow<ChatResult> {
    AiLogManager.log(LogLevel.INFO, "ApiClient", "发送API请求: model=${request.model}")
    
    try {
        // API调用代码
        
        AiLogManager.log(LogLevel.DEBUG, "ApiClient", "API响应成功")
    } catch (e: Exception) {
        AiLogManager.log(LogLevel.ERROR, "ApiClient", "API请求失败", e)
        throw e
    }
}
```

## 下一步行动

1. **立即添加**：高优先级的5个日志点
2. **本周完成**：中优先级的4个日志点  
3. **后续优化**：低优先级的3个日志点

## 注意事项

- 避免在高频调用的地方记录过多DEBUG日志（如每个chunk）
- 敏感信息（如API Key）不要记录到日志
- 长内容只记录长度或前100字符
- 错误日志要包含完整的异常堆栈
