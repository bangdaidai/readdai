# AI功能章节内容传递修复

## 问题描述

点击阅读页面的AI按钮，选择"章节摘要"、"全书总结"或"前情回顾"等功能时，AI返回的答案是：

> "由于您没有提供具体的章节内容或书名，我无法生成针对性的章节摘要。请提供以下信息以便我为您总结..."

这说明AI没有接收到书籍内容。

## 根本原因

在 `ReadBookActivity.onClickAiButton()` 方法中，调用 `AiFeatureHelper.showAiMenu()` 时，传递的 `currentChapterContent` 参数是硬编码的 `null`：

```kotlin
// 修复前的代码
override fun onClickAiButton() {
    val book = ReadBook.book
    AiFeatureHelper.showAiMenu(
        context = this,
        anchorView = binding.readMenu.fabNightTheme,
        book = book,
        currentChapterTitle = book?.durChapterTitle,
        currentChapterContent = null  // ❌ 这里是 null！
    )
}
```

导致后续所有依赖章节内容的AI功能都无法正常工作。

## 修复方案

### 1. 异步获取章节内容

修改 `onClickAiButton()` 方法，在显示菜单之前先异步获取当前章节的实际内容：

```kotlin
override fun onClickAiButton() {
    val book = ReadBook.book
    if (book == null) {
        Toast.makeText(this, "未找到书籍", Toast.LENGTH_SHORT).show()
        return
    }
    
    // 异步获取当前章节内容
    lifecycleScope.launch {
        val chapterContent = withContext(IO) {
            try {
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                if (chapter != null) {
                    BookHelp.getContent(book, chapter) ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                LogUtils.e("获取章节内容失败", e)
                ""
            }
        }
        
        // 在主线程显示菜单
        withContext(Main) {
            AiFeatureHelper.showAiMenu(
                context = this@ReadBookActivity,
                anchorView = binding.readMenu.fabNightTheme,
                book = book,
                currentChapterTitle = book.durChapterTitle,
                currentChapterContent = chapterContent  // ✅ 传入实际内容
            )
        }
    }
}
```

### 2. 添加调试日志

在关键位置添加日志，方便排查问题：

- 记录AI按钮点击事件
- 记录章节内容获取情况（长度、标题）
- 记录传递给AI的内容长度

## 数据流程

修复后的完整数据流程：

```
用户点击AI按钮
    ↓
ReadBookActivity.onClickAiButton()
    ↓
异步获取当前章节内容（IO线程）
    ├─ 从数据库获取章节信息
    ├─ 从缓存/文件读取章节内容
    └─ 返回内容字符串
    ↓
在主线程显示AI菜单
    ├─ 传递 book 对象
    ├─ 传递 currentChapterTitle
    └─ 传递 currentChapterContent ✅
    ↓
用户选择功能（如"章节摘要"）
    ↓
AiFeatureHelper.showAnalysisDialog()
    ├─ 创建 AiAnalysisDialog
    └─ 传递 chapterContent
    ↓
AiAnalysisDialog.executeSkill()
    ├─ 构建变量映射
    │   ├─ chapterContent → 实际内容
    │   ├─ bookName → 书名
    │   └─ ...
    └─ 执行技能
    ↓
AI服务接收完整信息
    ↓
返回正确的分析结果 ✅
```

## 测试步骤

### 1. 确认章节已下载

确保当前阅读的章节内容已经下载到本地：
- 在线书籍需要先浏览一下章节
- 本地书籍应该可以直接读取

### 2. 测试各功能

#### 章节摘要
1. 打开一本书，翻到任意章节（非第一章）
2. 点击AI按钮
3. 选择"章节摘要"
4. 应该看到针对当前章节的摘要

#### 全书总结
1. 点击AI按钮
2. 选择"全书总结"
3. 应该看到基于书籍信息的总结

#### 前情回顾
1. 翻到后面的章节（至少第2章）
2. 点击AI按钮
3. 选择"前情回顾"
4. 应该看到前面章节的回顾

### 3. 查看日志

如果仍然有问题，查看 Logcat 日志，搜索关键词：
- "AI按钮点击"
- "获取章节内容"
- "执行技能"
- "技能变量"

正常日志示例：
```
D/AI按钮点击: book=xxx, chapterIndex=5
D/获取章节内容成功: length=3456, title=第五章 xxx
D/显示AI菜单: chapterContent长度=3456
D/执行技能: skillId=skill_summarize_chapter, chapterContent长度=3456
D/技能变量: chapterContent前100字符=...
```

## 注意事项

1. **章节内容为空的情况**
   - 如果章节还未下载，`BookHelp.getContent()` 会返回 `null`
   - 此时AI会收到空字符串，可能无法生成准确摘要
   - 建议先浏览章节确保内容已缓存

2. **内容长度限制**
   - 传递给AI的内容会被截取前2000字符
   - 这是为了避免超出AI模型的上下文限制
   - 对于长章节，只会使用前部分内容

3. **性能考虑**
   - 获取章节内容是异步操作，不会阻塞UI
   - 菜单显示会有轻微延迟（通常<100ms）

4. **本地书籍 vs 在线书籍**
   - 本地书籍（TXT、EPUB等）可以直接读取
   - 在线书籍需要先下载章节内容

## 相关文件

- `ReadBookActivity.kt` - 修复了 `onClickAiButton()` 方法
- `AiFeatureHelper.kt` - AI功能入口
- `AiAnalysisDialog.kt` - AI分析结果弹窗
- `SkillManager.kt` - 技能定义和变量替换

## 如果仍然有问题

请提供以下信息：
1. Logcat 中的完整日志
2. 书籍类型（本地/在线）
3. 章节是否已下载
4. AI服务商配置情况
