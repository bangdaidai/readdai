# 语义上下文同步功能实现

## 📖 概述

本次优化为Legado项目添加了**语义上下文同步**功能，参照ReadAny项目的设计，实现了AI系统实时感知用户阅读状态的能力。

## ✨ 核心功能

### 1. ReadingContextService - 阅读上下文服务

**文件位置**: `app/src/main/java/io/legado/app/help/ai/ReadingContextService.kt`

**功能特性**:
- ✅ 实时跟踪用户的阅读状态
- ✅ 支持StateFlow响应式订阅
- ✅ 部分更新机制（避免全量替换）
- ✅ 操作类型感知（阅读、选择、搜索等）
- ✅ 时间戳追踪

**数据结构**:
```kotlin
data class ReadingContext(
    val bookId: String,              // 书籍ID
    val bookTitle: String,           // 书籍标题
    val author: String,              // 作者
    val currentChapter: ChapterInfo?, // 当前章节
    val currentPosition: PositionInfo?, // 当前位置
    val selection: SelectionInfo?,   // 选中文本
    val surroundingText: String,     // 周围文本
    val operationType: OperationType, // 操作类型
    val timestamp: Long              // 时间戳
)
```

### 2. ContextBasedTools - 基于上下文的AI工具

**文件位置**: `app/src/main/java/io/legado/app/help/ai/ContextBasedTools.kt`

**新增工具**:

| 工具ID | 名称 | 功能 |
|--------|------|------|
| `get_current_chapter` | 获取当前章节 | 获取当前阅读的章节信息 |
| `get_selection` | 获取选中文本 | 获取用户选中的文本内容 |
| `get_reading_progress` | 获取阅读进度 | 获取阅读进度百分比和位置 |
| `get_surrounding_context` | 获取周围文本 | 获取当前位置周围的文本 |

**优势**:
- ❌ 旧方式：需要手动传递参数 `variables["chapterTitle"]`
- ✅ 新方式：工具自动从 `ReadingContextService.getContext()` 获取最新数据

### 3. ReadBookActivity集成

**文件位置**: `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`

**集成点**:

#### A. 菜单显示时更新上下文
```kotlin
override fun onMenuShow() {
    binding.readView.autoPager.pause()
    updateReadingContext() // ← 新增
}
```

#### B. 文本选择时更新上下文
```kotlin
R.id.menu_ai_explain -> {
    updateSelectionContext(selectedText) // ← 新增
    AiFeatureHelper.handleTextMenuAiAction(...)
}
```

#### C. 取消选择时清除上下文
```kotlin
override fun onMenuActionFinally() {
    textActionMenu.dismiss()
    readView.cancelSelect()
    ReadingContextService.clearSelection() // ← 新增
}
```

## 🔄 工作流程

### 场景1: 用户选中文字问"这是什么意思？"

```
1. 用户长按选中文字 "量子纠缠"
   ↓
2. ReadBookActivity.updateSelectionContext() 
   → ReadingContextService.updateSelection()
   ↓
3. 用户点击"解释这段"
   ↓
4. AI工具 GetSelectionTool.execute()
   → 自动从 ReadingContextService 获取选中文本
   ↓
5. AI返回解释，包含完整的上下文信息
```

### 场景2: 用户问"我现在看到哪了？"

```
1. 用户翻页到第50章
   ↓
2. ReadBookActivity.updateReadingContext()
   → ReadingContextService.updateContext()
   ↓
3. 用户打开AI对话问"我现在看到哪了？"
   ↓
4. AI工具 GetReadingProgressTool.execute()
   → 自动获取最新进度：50/200章 (25%)
   ↓
5. AI返回准确的进度信息
```

## 🆚 对比优化前后

### 优化前（静态上下文）

```kotlin
// 问题：上下文在初始化时固定，不会更新
val context = AiToolContext(
    currentBook = ReadBook.book,      // 快照
    currentChapter = chapter,          // 快照
    chapterContent = content           // 快照
)

// AI工具只能使用这些旧数据
class CurrentChapterTool(val context: AiToolContext) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return ToolResult.ok(mapOf(
            "title" to context.currentChapter?.title  // 可能是旧章节
        ))
    }
}
```

**缺点**:
- ❌ 数据是静态快照，翻页后仍是旧数据
- ❌ 无法感知用户选中了什么
- ❌ 不知道用户的操作类型
- ❌ 多轮对话中上下文不一致

### 优化后（动态上下文）

```kotlin
// 优势：实时获取最新状态
class GetCurrentChapterTool : AiTool {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext() // ← 实时数据
        
        return ToolResult.ok(mapOf(
            "chapterTitle" to context?.currentChapter?.title, // 永远是最新的
            "progress" to context?.currentPosition?.percentage
        ))
    }
}
```

**优势**:
- ✅ 数据实时更新，翻页后立即同步
- ✅ 自动感知选中文本
- ✅ 知道用户在做什么（阅读/选择/搜索）
- ✅ 多轮对话保持一致的上下文

## 📊 技术细节

### StateFlow响应式订阅

```kotlin
// 其他组件可以订阅上下文变化
ReadingContextService.contextFlow.collect { context ->
    // 当上下文变化时自动收到通知
    Log.d("TAG", "Context updated: ${context?.bookTitle}")
}
```

### 部分更新机制

```kotlin
// 只更新需要的字段，其他保持不变
ReadingContextService.updateContext(ReadingContextUpdate(
    currentPosition = newPosition,  // 只更新位置
    // bookTitle, author等保持不变
))
```

### 操作类型追踪

```kotlin
enum class OperationType {
    READING,      // 普通阅读
    SELECTING,    // 选中文本
    SEARCHING,    // 搜索中
    NAVIGATING,   // 跳转章节
    ANNOTATING    // 添加笔记
}
```

## 🎯 实际应用场景

### 1. 智能问答
```
用户: "这一章讲了什么？"
AI: [自动获取当前章节] → "当前在第50章'大战前夕'，主要讲述了..."
```

### 2. 文本解释
```
用户选中: "薛定谔的猫"
用户: "这是什么意思？"
AI: [自动获取选中文本+周围上下文] → "这是量子力学中的一个著名思想实验..."
```

### 3. 进度查询
```
用户: "我看了多少了？"
AI: [自动获取进度] → "您已经阅读了25%，当前在第50章，共200章"
```

### 4. 笔记关联
```
用户: "我之前在哪里标记过这个概念？"
AI: [结合上下文搜索笔记] → "您在第30章和第45章都高亮了相关内容..."
```

## 🔧 使用方法

### 对于开发者

#### 1. 在Activity中更新上下文
```kotlin
// 章节加载时
ReadingContextService.updateContext(ReadingContextUpdate(
    bookId = book.bookUrl,
    bookTitle = book.name,
    currentChapter = ReadingContext.ChapterInfo(
        index = chapter.index,
        title = chapter.title
    )
))

// 用户选中文本时
ReadingContextService.updateSelection(ReadingContext.SelectionInfo(
    text = selectedText,
    chapterIndex = chapter.index
))
```

#### 2. 在AI工具中使用
```kotlin
class MyCustomTool : AiTool {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val context = ReadingContextService.getContext()
            ?: return ToolResult.error("没有阅读上下文")
        
        // 使用context中的数据
        return ToolResult.ok(mapOf(
            "bookTitle" to context.bookTitle,
            "chapter" to context.currentChapter?.title
        ))
    }
}
```

#### 3. 订阅上下文变化
```kotlin
lifecycleScope.launch {
    ReadingContextService.contextFlow.collect { context ->
        // 响应上下文变化
        updateUI(context)
    }
}
```

## 📈 性能优化

### 1. 防抖处理
上下文更新使用StateFlow，天然支持防抖，避免频繁更新导致的性能问题。

### 2. 轻量级数据结构
`ReadingContext` 只存储必要信息，避免大对象复制。

### 3. 懒加载
只在需要时创建工具实例，减少内存占用。

## 🚀 未来扩展

### 计划添加的功能

1. **阅读历史追踪**
   - 记录每本书的阅读时长
   - 分析阅读习惯

2. **智能推荐**
   - 根据当前阅读内容推荐相关书籍
   - 基于上下文的个性化建议

3. **跨设备同步**
   - 阅读进度云端同步
   - 多设备无缝切换

4. **语音交互**
   - 语音询问当前进度
   - 语音导航到特定章节

## 📝 总结

本次优化成功实现了**语义上下文同步**功能，使Legado的AI系统能够：

✅ **实时感知**用户的阅读状态  
✅ **自动获取**最新的上下文信息  
✅ **智能响应**用户的各种查询  
✅ **提供连贯**的多轮对话体验  

这标志着Legado的AI功能从**被动响应**升级为**主动感知**，大幅提升了用户体验和智能化水平。

---

**参考项目**: ReadAny53 - ReadingContextService  
**实现日期**: 2026年5月  
**版本**: v1.0
