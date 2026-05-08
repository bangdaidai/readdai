# AI功能优化使用指南

## 🎯 功能概览

本次优化为Legado的AI聊天功能添加了三个重要特性：

1. **推理过程显示** - 查看AI的思考过程
2. **工具步骤可视化** - 详细了解AI调用的每个工具
3. **去掉角色标签** - 更简洁的对话界面

---

## 📱 用户视角

### 1. 推理过程（思维链）

当AI进行复杂推理时，会在回复前显示思考过程：

```
┌─────────────────────────────┐
│ 🧠 思考过程            ▼   │  ← 点击展开
└─────────────────────────────┘

[用户看到AI的最终回答]
```

**如何查看**:
- 点击"思考过程"标题栏
- 推理内容会展开显示
- 再次点击可折叠

**适用场景**:
- 复杂问题分析
- 多步骤推理
- 需要理解AI思路时

---

### 2. 工具步骤可视化

当AI调用工具时，会显示详细的调用信息：

```
⚙️ 工具调用

┌──────────────────────────────────┐
│ ✓ get_current_chapter   14:30   │
│                                  │
│ 输入                              │
│ ┌──────────────────────────────┐│
│ │ {}                           ││  ← 点击📋复制
│ └──────────────────────────────┘│
│                                  │
│ 输出                              │
│ ┌──────────────────────────────┐│
│ │ ChapterInfo(index=5, ...)   ││  ← 点击📋复制
│ └──────────────────────────────┘│
└──────────────────────────────────┘
```

**状态图标**:
- ⚪ 灰色圆圈 - 待执行
- ⚠️ 橙色感叹号 - 执行中
- ✓ 绿色对勾 - 成功
- ✗ 红色叉号 - 失败

**可操作**:
- 点击📋按钮复制输入/输出
- 查看错误详情（如果有）
- 了解工具调用顺序

---

### 3. 无角色标签设计

**之前**:
```
你: 这本书讲了什么？
AI: 这本书讲述了...
```

**现在**:
```
              这本书讲了什么？  ← 右对齐，蓝色背景

这本书讲述了...                ← 左对齐，无背景
```

**优势**:
- 界面更简洁
- 通过位置和颜色区分角色
- 减少视觉干扰
- 更符合现代聊天应用设计

---

## 🔧 开发者视角

### 数据结构

#### ChatMessageItem
```kotlin
data class ChatMessageItem(
    val role: String,                    // "user" 或 "ai"
    val content: String,                 // 消息内容
    val reasoningContent: String = "",   // 推理过程
    val toolSteps: List<ToolStep> = emptyList(),  // 工具步骤
    var isExpanded: Boolean = false,     // 长文本是否展开
    var isReasoningExpanded: Boolean = false  // 推理是否展开
)
```

#### ToolStep
```kotlin
data class ToolStep(
    val name: String,                    // 工具名称
    val status: ToolStepStatus,          // 状态
    val input: String? = null,           // 输入参数
    val output: String? = null,          // 输出结果
    val error: String? = null,           // 错误信息
    val timestamp: Long                  // 时间戳
)

enum class ToolStepStatus {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    SUCCESS,    // 成功
    FAILED      // 失败
}
```

---

### 渲染流程

#### 1. 推理过程渲染
```kotlin
// 在 ChatAdapter.onBindViewHolder 中
if (message.reasoningContent.isNotEmpty()) {
    holder.layoutReasoning.visibility = View.VISIBLE
    holder.tvReasoningContent.text = message.reasoningContent
    
    // 根据展开状态显示/隐藏内容
    if (message.isReasoningExpanded) {
        holder.tvReasoningContent.visibility = View.VISIBLE
        holder.ivExpandIndicator.rotation = 180f
    } else {
        holder.tvReasoningContent.visibility = View.GONE
        holder.ivExpandIndicator.rotation = 0f
    }
    
    // 点击切换展开/折叠
    holder.btnToggleReasoning.setOnClickListener {
        message.isReasoningExpanded = !message.isReasoningExpanded
        notifyItemChanged(position)
    }
}
```

#### 2. 工具步骤渲染
```kotlin
// 遍历所有工具步骤
message.toolSteps.forEach { step ->
    // 1. 充布局
    val stepView = layoutInflater.inflate(R.layout.item_tool_step, container, false)
    
    // 2. 设置状态图标
    when (step.status) {
        PENDING -> ivStatus.setImageResource(R.drawable.ic_circle_outline)
        RUNNING -> ivStatus.setImageResource(R.drawable.ic_circle_running)
        SUCCESS -> ivStatus.setImageResource(R.drawable.ic_circle_success)
        FAILED -> ivStatus.setImageResource(R.drawable.ic_circle_failed)
    }
    
    // 3. 填充数据
    tvName.text = step.name
    tvTime.text = formatTimestamp(step.timestamp)
    
    // 4. 条件显示输入/输出/错误
    if (!step.input.isNullOrBlank()) {
        layoutInput.visibility = View.VISIBLE
        tvInput.text = step.input
        btnCopyInput.setOnClickListener { copyToClipboard(step.input) }
    }
    
    if (!step.output.isNullOrBlank()) {
        layoutOutput.visibility = View.VISIBLE
        tvOutput.text = step.output
        btnCopyOutput.setOnClickListener { copyToClipboard(step.output) }
    }
    
    if (!step.error.isNullOrBlank()) {
        layoutError.visibility = View.VISIBLE
        tvError.text = step.error
    }
    
    // 5. 添加到容器
    container.addView(stepView)
}
```

---

### 流式更新支持

当前实现支持静态的工具步骤显示。如需支持流式更新：

```kotlin
// 在 AiService.chat() 中
flow {
    // 发送推理过程
    emit(ChatResult.ReasoningChunk("正在分析..."))
    
    // 发送工具步骤更新
    emit(ChatResult.ToolStepUpdate(
        ToolStep(
            name = "get_current_chapter",
            status = ToolStepStatus.RUNNING
        )
    ))
    
    // 工具执行完成后
    emit(ChatResult.ToolStepUpdate(
        ToolStep(
            name = "get_current_chapter",
            status = ToolStepStatus.SUCCESS,
            input = "{}",
            output = "ChapterInfo(...)"
        )
    ))
    
    // 发送最终答案
    emit(ChatResult.Chunk("根据当前章节..."))
}
```

在 ChatActivity 中处理：
```kotlin
aiService.chat(message).collectLatest { result ->
    when (result) {
        is ChatResult.ReasoningChunk -> {
            // 更新推理过程
            messages[aiPosition].reasoningContent += result.content
            adapter.notifyItemChanged(aiPosition)
        }
        is ChatResult.ToolStepUpdate -> {
            // 更新工具步骤
            messages[aiPosition].toolSteps = 
                updateToolStep(messages[aiPosition].toolSteps, result.step)
            adapter.notifyItemChanged(aiPosition)
        }
        is ChatResult.Chunk -> {
            // 更新答案内容
            messages[aiPosition].content += result.content
            adapter.notifyItemChanged(aiPosition)
        }
        // ...
    }
}
```

---

## 🧪 测试场景

### 场景1: 简单问答
**操作**: 询问"这本书的作者是谁？"
**预期**: 
- 不显示推理过程
- 不调用工具
- 直接显示答案

### 场景2: 需要工具的问答
**操作**: 询问"当前章节讲了什么？"
**预期**:
- 可能显示推理过程
- 显示 `get_current_chapter` 工具调用
- 显示工具输入/输出
- 显示最终答案

### 场景3: 复杂推理
**操作**: 询问"分析这个人物的发展轨迹"
**预期**:
- 显示较长的推理过程
- 可能调用多个工具
- 按顺序显示工具步骤
- 显示综合分析结果

### 场景4: 工具调用失败
**操作**: 在网络异常时提问
**预期**:
- 工具状态显示为FAILED
- 显示红色错误信息
- AI尝试其他方式回答或提示错误

---

## 🎨 自定义样式

### 修改推理过程样式
编辑 `item_ai_chat_message.xml`:
```xml
<TextView
    android:id="@+id/tv_reasoning_content"
    android:background="#0A000000"  <!-- 修改背景色 -->
    android:textColor="?attr/secondaryTextColor"  <!-- 修改文字颜色 -->
    android:textSize="13sp"  <!-- 修改字体大小 -->
    ... />
```

### 修改工具步骤卡片样式
编辑 `item_tool_step.xml`:
```xml
<com.google.android.material.card.MaterialCardView
    app:cardBackgroundColor="#0A000000"  <!-- 修改卡片背景 -->
    app:strokeColor="?attr/divider"  <!-- 修改边框颜色 -->
    app:cardCornerRadius="8dp"  <!-- 修改圆角 -->
    ... />
```

### 修改状态图标颜色
编辑 drawable 文件:
- `ic_circle_success.xml` - 修改 `android:fillColor`
- `ic_circle_running.xml` - 修改 `android:fillColor`
- `ic_circle_failed.xml` - 修改 `android:fillColor`

---

## 🐛 常见问题

### Q1: 推理过程不显示？
**A**: 检查AI提供商是否支持推理过程输出。不是所有模型都支持。

### Q2: 工具步骤为空？
**A**: 
1. 确认AI服务配置了工具
2. 检查AiService是否正确解析工具调用
3. 查看日志确认工具是否被调用

### Q3: 复制按钮不工作？
**A**: 检查是否有剪贴板权限。Android 10+ 不需要特殊权限。

### Q4: 样式在深色模式下不正常？
**A**: 确保使用 `?attr/` 引用主题颜色，不要使用硬编码颜色。

### Q5: 工具步骤太多导致卡顿？
**A**: 
1. 考虑限制显示的工具步骤数量
2. 实现虚拟化列表（RecyclerView嵌套）
3. 延迟加载工具步骤详情

---

## 📚 相关文档

- [AI功能优化完成报告](AI_OPTIMIZATION_COMPLETE.md)
- [语义上下文同步功能](READING_CONTEXT_SYNC.md)
- [anx53工具步骤实现](../anx53/lib/widgets/ai/tool_step_tile.dart)
- [ReadAny推理过程解析](../ReadAny53/packages/core/src/ai/reasoning-parser.ts)

---

**最后更新**: 2026-05-04  
**版本**: 1.0.0
