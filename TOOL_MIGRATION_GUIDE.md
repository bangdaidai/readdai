# AI 工具迁移指南

## 📋 迁移步骤

### 步骤 1: 继承 BaseTool

**之前**:
```kotlin
class MyTool(private val context: AiToolContext) : AiTool {
    override val id = "my_tool"
    override val name = "我的工具"
    override val description = "描述"
    override val timeout: Long? = null
    
    override val inputSchema = mapOf(...)
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return try {
            // 业务逻辑
        } catch (e: Exception) {
            ToolResult(status = "error", ...)
        }
    }
}
```

**之后**:
```kotlin
class MyTool(private val context: AiToolContext) : BaseTool(
    id = "my_tool",
    name = "我的工具",
    description = "描述",
    inputSchema = mapOf(...),
    timeout = 5000  // 添加超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 专注业务逻辑，无需 try-catch
        // 错误处理由 BaseTool 自动完成
    }
}
```

---

### 步骤 2: 创建 Repository（可选但推荐）

如果工具需要访问数据库，创建对应的 Repository：

```kotlin
package io.legado.app.help.ai.repository

import io.legado.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MyRepository(private val appDatabase: AppDatabase) {
    private val myDao = appDatabase.myDao
    
    suspend fun fetchData(param: String): List<MyData> = withContext(Dispatchers.IO) {
        // 数据访问逻辑
        myDao.query(param)
    }
}
```

---

### 步骤 3: 在工具中使用 Repository

```kotlin
class MyTool(private val context: AiToolContext) : BaseTool(...) {
    
    private val repository = MyRepository(context.appDatabase)
    
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 使用 Repository 获取数据
        val data = repository.fetchData(input["param"].toString())
        
        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf("data" to data)
        )
    }
}
```

---

## ✅ 迁移检查清单

### 基础检查
- [ ] 继承 BaseTool 而非 AiTool
- [ ] 移除 `override val` 声明（id, name, description等）
- [ ] 将 `execute` 改为 `run`
- [ ] 移除 try-catch（由 BaseTool 处理）
- [ ] 添加超时时间（timeout）

### 功能检查
- [ ] 是否需要创建 Repository？
- [ ] 参数说明是否清晰？
- [ ] 描述是否详细且包含使用场景？
- [ ] 是否使用了 BaseTool 的辅助方法（formatTime, formatDuration）？

### 注册检查
- [ ] 更新 AiToolRegistry.register 中的描述
- [ ] 更新 inputSchema 以反映新参数
- [ ] 如果有重复工具，考虑合并

---

## 📝 迁移示例

### 示例 1: CurrentBookInfoTool

**迁移前**:
```kotlin
class CurrentBookInfoTool(private val context: AiToolContext) : AiTool {
    override val id = "current_book_info"
    override val name = "获取当前书籍信息"
    override val description = "获取当前阅读书籍的基本信息"
    override val timeout: Long? = null
    
    override val inputSchema = emptyMap()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return try {
            val readingContext = ReadingContextService.getContext()
            val book = readingContext?.let { ... } ?: context.currentBook
            
            book ?: return ToolResult(
                status = "error",
                name = id,
                message = "没有正在阅读的书籍"
            )
            
            ToolResult(
                status = "ok",
                name = id,
                data = mapOf(...)
            )
        } catch (e: Exception) {
            ToolResult(
                status = "error",
                name = id,
                message = "获取失败: ${e.message}"
            )
        }
    }
}
```

**迁移后**:
```kotlin
class CurrentBookInfoTool(private val context: AiToolContext) : BaseTool(
    id = "current_book_info",
    name = "获取当前书籍信息",
    description = "获取用户当前正在阅读的书籍的基本信息、作者、简介和阅读进度。当用户询问当前在看什么书时使用。",
    inputSchema = emptyMap(),
    timeout = 3000  // 3秒超时
) {
    override suspend fun run(input: Map<String, Any>): ToolResult {
        // 优先从 ReadingContextService 获取实时上下文
        val readingContext = ReadingContextService.getContext()
        val book = readingContext?.let { ctx ->
            if (ctx.bookId.isNotBlank()) {
                context.appDatabase.bookDao.getBook(ctx.bookId)
            } else null
        } ?: context.currentBook
        
        book ?: return ToolResult(
            status = "error",
            name = id,
            message = "当前没有正在阅读的书籍"
        )
        
        return ToolResult(
            status = "ok",
            name = id,
            data = mapOf(
                "title" to book.name,
                "author" to (book.author ?: "未知"),
                "kind" to (book.kind ?: "未分类"),
                "progress" to book.durChapterIndex
            )
        )
    }
}
```

**改进点**:
- ✅ 代码更简洁（减少 ~15 行）
- ✅ 添加超时控制
- ✅ 改进描述
- ✅ 无需手动错误处理

---

## 🎯 优先级建议

### 高优先级（立即迁移）
1. ✅ ReadingHistoryTool - 已完成
2. ✅ ListBooksTool - 已完成
3. ⏳ CurrentBookInfoTool - 简单，快速迁移
4. ⏳ CurrentChapterTool - 常用工具
5. ⏳ BookTocTool - 常用工具

### 中优先级（本周内）
6. ReadingProgressTool
7. BookNotesTool
8. SearchContentTool
9. ExtractEntitiesTool

### 低优先级（本月内）
10. 所有标签相关工具
11. 所有 RAG 相关工具
12. 所有分析工具

---

## 💡 最佳实践

### 1. 超时时间设置
| 工具类型 | 建议超时 | 原因 |
|----------|----------|------|
| 简单查询 | 2-3秒 | 快速获取数据 |
| 复杂查询 | 5秒 | 需要聚合或过滤 |
| RAG搜索 | 10秒 | 向量搜索较慢 |
| 内容生成 | 15秒 | 可能需要AI处理 |

### 2. 描述编写
**好的描述**:
```kotlin
description = "获取用户书架上的书籍列表。支持按标题、作者、分类、阅读状态筛选。当用户询问有哪些书、找特定书籍、查看书架、推荐书籍时使用。"
```

**不好的描述**:
```kotlin
description = "获取书籍列表"
```

### 3. 参数说明
每个参数都应该有清晰的 description：
```kotlin
"status" to mapOf(
    "type" to "string",
    "description" to "按阅读状态过滤: unread(未读), reading(阅读中), completed(已完成)"
)
```

### 4. Repository 设计
- 一个 Repository 对应一个业务领域
- 方法名清晰表达意图
- 使用 withContext(Dispatchers.IO) 确保在后台线程执行
- 返回数据类而非原始 Map

---

## 🔧 常见问题

### Q1: 什么时候需要创建 Repository？
**A**: 当工具需要：
- 访问数据库
- 复杂的数据聚合
- 可能被多个工具复用

### Q2: 超时时间设多少合适？
**A**: 
- 默认 3-5 秒
- 简单查询 2-3 秒
- 复杂操作 5-10 秒
- 根据实际情况调整

### Q3: 如何处理特殊情况？
**A**: 在 `run` 方法中直接返回错误：
```kotlin
if (condition) {
    return ToolResult(
        status = "error",
        name = id,
        message = "具体错误信息"
    )
}
```

### Q4: BaseTool 的日志会影响性能吗？
**A**: 不会，AiLogManager 已经优化了日志记录，只在 DEBUG 级别记录详细信息。

---

## 📚 参考资料

- [BaseTool 实现](file://d:\desktop\personal\com\dai411\app\src\main\java\io\legado\app\help\ai\AiTools.kt#L38-L165)
- [ReadingHistoryRepository](file://d:\desktop\personal\com\dai411\app\src\main\java\io\legado\app\help\ai\repository\ReadingHistoryRepository.kt)
- [BooksRepository](file://d:\desktop\personal\com\dai411\app\src\main\java\io\legado\app\help\ai\repository\BooksRepository.kt)
- [anx53 BaseTool](file://d:\desktop\personal\com\anx53\lib\service\ai\tools\base_tool.dart)

---

**最后更新**: 2026-05-08  
**维护者**: AI Assistant
