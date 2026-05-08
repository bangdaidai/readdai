# AI 历史会话保存优化

## 问题描述
dai411 项目的 AI 阅读助手历史对话没有正确保存，用户无法查看之前的对话记录。

## 根本原因
1. **会话未初始化**：`currentSession` 对象从未被创建，导致所有保存逻辑被跳过
2. **保存时机不当**：只在聊天完成后才尝试保存，且由于 session 为 null 而失败
3. **缺少草稿机制**：没有在发送消息时立即保存，导致如果请求中断会丢失数据

## 参考实现

### anx53 (Flutter)
- **三步保存策略**：
  1. 发送消息前保存草稿（completed = false）
  2. 流式输出中持续更新状态
  3. 完成后标记为完成（completed = true）
  4. 失败时也保存（保留已生成的内容）

### ReadAny (React/TypeScript)
- **实时持久化**：
  - 使用 SQLite 数据库
  - 每次添加消息时立即保存到数据库
  - 线程（Thread）作为会话容器
  - 支持按书籍分组

## 实施的改进

### 1. 会话初始化
```kotlin
// 在 Activity 启动时创建新会话
override fun onActivityCreated(savedInstanceState: Bundle?) {
    // ...
    createNewSession()
}

private fun createNewSession() {
    currentSession = AiChatSession(
        id = "session_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}",
        serviceId = "default",
        model = currentModel,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        messages = emptyList(),
        completed = false
    )
}
```

### 2. 三步保存策略

#### 第一步：发送消息时保存草稿
```kotlin
private fun sendMessage(content: String, isRegenerate: Boolean = false) {
    // ... 添加消息到 UI
    setRequestState(true)
    
    // 立即保存草稿会话
    saveDraftSession(content)
    
    // 开始流式请求
    lifecycleScope.launch {
        // ...
    }
}

private fun saveDraftSession(userMessage: String) {
    currentSession?.let { session ->
        val updatedMessages = session.messages.toMutableList()
        updatedMessages.add(ChatMessage("human", userMessage))
        val draftSession = session.copy(
            messages = updatedMessages,
            model = currentModel,
            updatedAt = System.currentTimeMillis(),
            completed = false
        )
        lifecycleScope.launch {
            AiHistoryStore.upsertSession(draftSession)
        }
        currentSession = draftSession
    }
}
```

#### 第二步：流式输出中定期保存
```kotlin
is ChatResult.Chunk -> {
    // 更新 UI
    messages[streamingPosition] = ChatMessageItem("ai", content)
    adapter.notifyItemChanged(streamingPosition)
    
    // 每收到一定数量的 chunk 就保存进度
    if (messages[streamingPosition].content.length % 50 == 0) {
        saveStreamingProgress()
    }
}

private fun saveStreamingProgress() {
    currentSession?.let { session ->
        val aiContent = messages.getOrNull(streamingPosition)?.content ?: ""
        if (aiContent.isEmpty()) return
        
        val updatedMessages = session.messages.toMutableList()
        // 移除旧的 AI 消息，添加最新的
        if (updatedMessages.isNotEmpty() && updatedMessages.last().type == "ai") {
            updatedMessages.removeAt(updatedMessages.size - 1)
        }
        updatedMessages.add(ChatMessage("ai", aiContent))
        
        val progressSession = session.copy(
            messages = updatedMessages,
            model = currentModel,
            updatedAt = System.currentTimeMillis(),
            completed = false
        )
        lifecycleScope.launch {
            AiHistoryStore.upsertSession(progressSession)
        }
        currentSession = progressSession
    }
}
```

#### 第三步：完成后标记
```kotlin
is ChatResult.Success -> {
    // 更新 UI
    messages[streamingPosition] = messages[streamingPosition].copy(
        content = result.content,
        toolSteps = messages[streamingPosition].toolSteps
    )
    streamingPosition = -1
    updateAdapter()

    // 保存到历史（标记为完成）
    currentSession?.let { session ->
        val updatedMessages = session.messages.toMutableList()
        // 注意：用户消息已在 saveDraftSession 中添加，这里只添加 AI 回复
        updatedMessages.add(ChatMessage("ai", result.content))
        val updatedSession = session.copy(
            messages = updatedMessages,
            model = currentModel,
            updatedAt = System.currentTimeMillis(),
            completed = true  // 标记为完成
        )
        lifecycleScope.launch {
            AiHistoryStore.upsertSession(updatedSession)
        }
        currentSession = updatedSession
    }

    setRequestState(false)
}
```

#### 失败时也保存
```kotlin
is ChatResult.Error -> {
    messages[streamingPosition] = ChatMessageItem("ai", "错误：${result.message}")
    streamingPosition = -1
    updateAdapter()

    // 即使失败也保存会话
    currentSession?.let { session ->
        val updatedMessages = session.messages.toMutableList()
        updatedMessages.add(ChatMessage("ai", "错误：${result.message}"))
        val failedSession = session.copy(
            messages = updatedMessages,
            model = currentModel,
            updatedAt = System.currentTimeMillis(),
            completed = false  // 标记为未完成
        )
        lifecycleScope.launch {
            AiHistoryStore.upsertSession(failedSession)
        }
        currentSession = failedSession
    }

    setRequestState(false)
}
```

### 3. 新建对话功能
```kotlin
// 菜单项
R.id.menu_new_chat -> {
    createNewSession()  // 创建新会话
    messages.clear()
    adapter.notifyDataSetChanged()
    updateEmptyState()
    Toast.makeText(this, "已新建对话", Toast.LENGTH_SHORT).show()
    true
}

// 侧边栏
btnNewChat.setOnClickListener {
    createNewSession()
    messages.clear()
    adapter.notifyDataSetChanged()
    updateEmptyState()
    dialog.dismiss()
}
```

### 4. 历史记录显示优化

#### 加载历史列表
```kotlin
lifecycleScope.launch {
    val sessions = AiHistoryStore.readHistory()
    if (sessions.isEmpty()) {
        // 显示空状态
    } else {
        recyclerHistory.layoutManager = LinearLayoutManager(this@AiChatActivity)
        recyclerHistory.adapter = object : RecyclerView.Adapter<HistoryViewHolder>() {
            override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
                holder.bind(sessions[position])
            }
            override fun getItemCount() = sessions.size
        }
    }
}
```

#### 显示格式
```kotlin
inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(session: AiChatSession) {
        // 获取第一条用户消息作为标题
        val firstUserMessage = session.messages.firstOrNull { it.type == "human" }?.content ?: "空对话"
        val title = if (firstUserMessage.length > 30) {
            firstUserMessage.take(30) + "..."
        } else {
            firstUserMessage
        }
        
        // 格式化时间（刚刚、X分钟前、X小时前、日期）
        val timeStr = formatTimestamp(session.updatedAt)
        textView.text = "$title\n$timeStr"
        
        itemView.setOnClickListener {
            loadSession(session)
            historyDialog?.dismiss()
        }
    }
}
```

### 5. 技能执行的历史保存
```kotlin
is ChatResult.Success -> {
    // 更新 UI
    adapter.notifyDataSetChanged()
    
    // 保存到历史
    currentSession?.let { session ->
        val updatedMessages = session.messages.toMutableList()
        updatedMessages.add(ChatMessage("human", userQuestion))
        updatedMessages.add(ChatMessage("ai", result.content))
        val updatedSession = session.copy(
            messages = updatedMessages,
            model = currentModel,
            updatedAt = System.currentTimeMillis(),
            completed = true
        )
        lifecycleScope.launch {
            AiHistoryStore.upsertSession(updatedSession)
        }
        currentSession = updatedSession
    }
}
```

## 技术细节

### 存储方式
- **文件位置**：`context.cacheDir/ai_chat_history.json`
- **格式**：JSON 数组
- **限制**：最多保存 100 条历史会话
- **排序**：按 `updatedAt` 降序排列

### 会话 ID 生成
```kotlin
id = "session_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
```
使用时间戳 + 随机数确保唯一性

### 初始化顺序
```kotlin
lifecycleScope.launch {
    // 1. 先初始化历史存储
    AiHistoryStore.init(this@AiChatActivity)
    
    // 2. 再初始化 AI 服务
    aiService.init()
    
    // 3. 设置工具上下文
    if (bookUrl != null) {
        // ...
    }
    
    // 4. 更新 UI
    updateEmptyState()
}
```

## 测试建议

1. **基本流程测试**：
   - 发送消息 → 检查是否立即出现在历史中
   - 等待回复完成 → 检查 completed 字段是否为 true
   - 刷新页面 → 检查历史是否正确加载

2. **异常场景测试**：
   - 发送消息后取消请求 → 检查是否保存了草稿
   - 网络错误 → 检查是否保存了错误信息
   - 中途关闭应用 → 检查是否保留了已生成的内容

3. **边界情况测试**：
   - 超过 100 条历史 → 检查是否正确截断
   - 空会话 → 检查是否正常处理
   - 超长消息 → 检查标题截断是否正常

## 后续优化建议

1. **按书籍分组**：类似 ReadAny，将历史会话按 bookId 分组
2. **搜索功能**：允许用户搜索历史对话
3. **导出功能**：支持导出单个会话或全部历史
4. **云同步**：可选的云备份功能
5. **自动清理**：定期清理过久的未完成会话

## 相关文件

- `AiChatActivity.kt` - 主要修改文件
- `AiDao.kt` - 包含 `AiHistoryStore` 实现
- `AiEntities.kt` - 包含 `AiChatSession` 和 `ChatMessage` 定义
- `layout_history_sidebar.xml` - 历史侧边栏布局（需要确保有 recycler_history）
