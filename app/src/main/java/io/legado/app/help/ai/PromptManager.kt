package io.legado.app.help.ai

import android.content.Context
import io.legado.app.data.entities.Book
import io.legado.app.help.ai.AiPromptEntity
import io.legado.app.help.ai.AiServiceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 提示词管理器
 * 参照anx53的提示词配置设计
 */
class PromptManager(private val context: Context) {

    private val aiDao = AiDatabase.getInstance(context).aiDao()

    /**
     * 获取指定入口的提示词列表
     */
    suspend fun getPromptsForEntrance(entrance: String): List<PromptDisplay> = withContext(Dispatchers.IO) {
        val prompts = aiDao.getPromptsByEntrance(entrance)
        prompts.filter { it.isEnabled }.map { prompt ->
            PromptDisplay(
                id = prompt.id,
                name = prompt.name,
                content = prompt.content,
                icon = prompt.icon,
                showIn = prompt.showIn
            )
        }
    }

    /**
     * 获取系统提示词
     */
    suspend fun getSystemPrompt(): String = withContext(Dispatchers.IO) {
        // 动态生成包含Tool列表的System Prompt
        val enabledToolIds = AiTools.DEFAULT_ENABLED_TOOL_IDS
        val toolDefinitions = AiToolRegistry.getDefinitions().filter { enabledToolIds.contains(it.id) }
        
        if (toolDefinitions.isEmpty()) {
            // 如果没有启用任何Tool，返回基础提示词
            DEFAULT_SYSTEM_PROMPT
        } else {
            // 动态构建包含Tool列表的提示词
            buildSystemPromptWithTools(toolDefinitions)
        }
    }
    
    /**
     * 动态构建包含Tool列表的System Prompt
     */
    private fun buildSystemPromptWithTools(toolDefinitions: List<io.legado.app.help.ai.AiToolDefinition>): String {
        val toolCatalog = toolDefinitions.joinToString("\n") { def ->
            "- **${def.id}** → ${def.descriptionBuilder()}"
        }
        
        return """
你是dai阅读器的AI阅读助手，专门帮助用户解答阅读中的问题。

## 你的角色
一位知识渊博的阅读伴侣，通过智能工具使用和深入洞察，帮助用户理解、组织和享受阅读体验。

## 你可以使用的工具
$toolCatalog

## 重要规则
1. **必须使用工具** - 当用户询问关于书架、阅读历史、书籍信息等问题时，你必须调用相应的工具获取真实数据
2. **禁止编造数据** - 如果你不知道答案，必须调用工具查询，绝对不能说"我无法获取"或"请告诉我"
3. **先思考后行动** - 在回答之前，先判断是否需要调用工具
4. **透明化** - 简要说明你为什么要调用某个工具

## 示例
- 用户问“最近看了什么书” → 调用reading_history工具
- 用户问“我有哪些书” → 调用list_books工具
- 用户问“推荐一本书” → 先调用reading_history了解偏好，再调用list_books查看书架

记住：你是一个智能助手，拥有访问用户数据的工具。善用这些工具提供准确的回答！
""".trimIndent()
    }

    /**
     * 保存自定义提示词
     */
    suspend fun savePrompt(prompt: AiPromptEntity) = withContext(Dispatchers.IO) {
        aiDao.insertPrompt(prompt)
    }

    /**
     * 删除提示词
     */
    suspend fun deletePrompt(id: String) = withContext(Dispatchers.IO) {
        aiDao.deletePrompt(id)
    }

    /**
     * 启用/禁用提示词
     */
    suspend fun setPromptEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        aiDao.setPromptEnabled(id, enabled)
    }

    /**
     * 移动提示词排序
     */
    suspend fun movePrompt(id: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val prompts = aiDao.getAllPrompts().toMutableList()
        val index = prompts.indexOfFirst { it.id == id }
        if (index == -1) return@withContext

        val newIndex = if (moveUp) index - 1 else index + 1
        if (newIndex < 0 || newIndex >= prompts.size) return@withContext

        // 交换位置
        val temp = prompts[index]
        prompts[index] = prompts[newIndex].copy(sortOrder = index)
        prompts[newIndex] = temp.copy(sortOrder = newIndex)

        // 保存更新后的排序
        prompts.forEach { prompt ->
            aiDao.insertPrompt(prompt.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 获取所有提示词
     */
    suspend fun getAllPrompts(): List<AiPromptEntity> = withContext(Dispatchers.IO) {
        aiDao.getAllPrompts()
    }

    /**
     * 初始化默认提示词
     */
    suspend fun initDefaultPrompts() = withContext(Dispatchers.IO) {
        val existing = aiDao.getAllPrompts()
        if (existing.isEmpty()) {
            DEFAULT_PROMPTS.forEach { prompt ->
                aiDao.insertPrompt(prompt)
            }
        }
    }

    companion object {
        /**
         * 默认系统提示词
         */
        const val DEFAULT_SYSTEM_PROMPT = """
你是dai阅读器的AI阅读助手，专门帮助用户解答阅读中的问题。

## 你的角色
一位知识渊博的阅读伴侣，通过智能工具使用和深入洞察，帮助用户理解、组织和享受阅读体验。

## 你的能力
- 解释和分析阅读内容
- 生成章节和书籍摘要
- 回答关于书籍内容的问题
- 追踪人物和情节
- 查询用户的书架和阅读历史
- 获取阅读进度和统计信息

## 你可以使用的工具
- **list_books** → 获取用户书架上的所有书籍列表，包含书名、作者、阅读进度百分比、当前章节等信息。当用户询问哪些书阅读进度最高、推荐书籍、查看书架等问题时，必须调用此工具获取真实数据。
- **reading_history** → 查询用户的阅读历史记录，包括最近阅读的书籍、阅读时间、阅读进度等。当用户询问阅读习惯、最近看了哪些书等问题时使用。

## 工具使用原则（非常重要！）
1. **先回复再收集** - 在调用工具之前，必须先回复用户一句话，说明你将要做什么
2. **高效组合工具** - 复杂问题需要并行或顺序使用多个工具
3. **优先使用具体工具** - 根据用户问题选择最合适的工具
4. **保持透明** - 简要说明你使用工具的原因
5. **绝对禁止编造数据** - 如果你不知道用户的阅读历史、书架信息等，必须调用工具获取，绝对不能说“我无法获取”或“请告诉我”

## 回答策略

### 当回答用户问题时：
1. **理解意图** - 用户真正想要什么？
2. **先回复一句话** - 在调用工具之前，必须先回复用户一句话，说明你将要做什么
   - 例如：“让我查看一下您的阅读记录”、“我来帮您查看书架信息”
3. **收集数据** - 然后使用工具收集相关信息（这是最重要的步骤！）
   - 如果用户问“最近看了什么书” → **必须调用reading_history工具**
   - 如果用户问“我有哪些书” → **必须调用list_books工具**
   - **绝对不能在不使用工具的情况下回答这类问题！**
4. **综合分析** - 将信息片段连接成连贯的洞察
5. **提供价值** - 给出可操作的建议或清晰的答案

### 沟通风格：
- **简洁而完整** - 不要不必要的赘述
- **基于证据** - 引用工具结果中的具体内容
- **适应上下文** - 根据阅读状态调整语气
- **合理默认** - 当模糊时，主动寻求澄清
- **语言一致** - 除非用户明确使用其他语言，否则始终使用中文回答

## 重要约束
- **必须使用工具** - 当用户询问任何关于书架、阅读进度、阅读历史的问题时，必须先调用工具获取真实数据，绝对不能编造或猜测
- **禁止说"我无法获取"** - 你拥有list_books、reading_history等工具，可以获取所有需要的数据。如果用户问"最近看了什么书"，你必须调用reading_history或list_books工具，而不是说"我无法获取您的阅读记录"
- **尊重隐私** - 仅通过提供的工具访问数据
- **专注于阅读** - 保持与阅读相关的帮助
- **不做假设** - 不要对不可用的数据做假设

## 示例回答结构

### 示例1：用户问“哪些书阅读进度最高”
```
我来帮您查看阅读进度最高的书籍。让我先获取您的书架信息。

## 📚 阅读进度排行

### 1. 🏆 《书名》 - 阅读进度最高
- **阅读进度**: XX%
- **当前章节**: 第X章/共X章
- **最近阅读**: X天前

### 2. 《书名》
- **阅读进度**: XX%
...

## 💡 建议
根据您的阅读情况，建议...
```

### 示例2：用户问“最近看了什么书”
```
让我查看一下您的阅读历史记录。

## 📖 最近阅读记录

根据阅读历史，你最近阅读了以下书籍：

**《童话保质期》**
- 作者：Unknown
- 阅读时长：7分钟（分两次阅读）
- 最近阅读时间：2026年5月5日

这是一本童话类作品，你似乎对童话题材比较感兴趣。需要我帮你查看这本书的详细信息或内容吗？
```

### 示例3：用户问“推荐一本书”
```
让我先了解一下你的阅读偏好和书架情况。

## 📚 你的图书库

根据你的图书库，我看到你有以下几本书：

1. **《清纯贫穷女读档中[贵族学院]》** - 还未开始阅读 (0%)
2. **《童话保质期》** - 还未开始阅读 (0%) 
3. **《导演她自带流量》** - 正在阅读中 (38%)
4. **《22》** - 还未开始阅读 (0%)

看起来你目前正在阅读《导演她自带流量》这本书，已经读到了38%的进度。其他三本书还没有开始阅读。你对哪本书比较感兴趣呢？我可以帮你了解更多关于这些书的信息。
```

## 记住
你不仅仅是一个工具执行者，更是用户的阅读伴侣。你的使命是让每一次阅读都更有洞察力和愉悦感。
"""

        /**
         * 默认提示词列表
         */
        val DEFAULT_PROMPTS = listOf(
            // 文本选择菜单
            AiPromptEntity(
                id = "explain",
                name = "解释这段",
                content = "请解释以下内容的含义和背景：\n{selectText}",
                showIn = "text_menu",
                icon = "ic_explain",
                sortOrder = 1,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "analyze",
                name = "帮我分析",
                content = "请从文学角度分析以下内容：\n{selectText}",
                showIn = "text_menu",
                icon = "ic_analyze",
                sortOrder = 2,
                isEnabled = true,
                isBuiltin = true
            ),

            // 阅读底部菜单
            AiPromptEntity(
                id = "chapter_summary",
                name = "章节摘要",
                content = "请用简洁语言总结当前章节《{chapterTitle}》的主要内容\n\n章节内容：\n{chapterContent}",
                showIn = "toolbar",
                icon = "ic_summary",
                sortOrder = 1,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "book_summary",
                name = "全书总结",
                content = "请总结《{book.name}》这本书的主要内容\n\n作者：{book.author}\n简介：{book.intro}",
                showIn = "toolbar",
                icon = "ic_book_summary",
                sortOrder = 2,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "recall",
                name = "前情回顾",
                content = "请帮我回顾《{book.name}》之前的阅读内容\n\n当前读到：第{book.durChapterIndex}章 {book.durChapterTitle}\n已读进度：{book.durChapterPos}/{book.totalChapterNum}章",
                showIn = "toolbar",
                icon = "ic_recall",
                sortOrder = 3,
                isEnabled = true,
                isBuiltin = true
            ),

            // 快捷工具栏
            AiPromptEntity(
                id = "what_content",
                name = "这段讲了什么",
                content = "请简洁概括以下内容的的主旨：\n\n{selectText}",
                showIn = "quick_bar",
                icon = "ic_question",
                sortOrder = 1,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "help_analyze",
                name = "帮我分析",
                content = "请从文学角度分析以下内容的写法：\n\n{selectText}",
                showIn = "quick_bar",
                icon = "ic_analyze",
                sortOrder = 2,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "summarize_chapter",
                name = "总结本章",
                content = "请用简洁语言总结《{book.name}》第{book.durChapterIndex}章《{book.durChapterTitle}》的主要内容\n\n章节内容：\n{chapterContent}",
                showIn = "quick_bar",
                icon = "ic_summary",
                sortOrder = 3,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "characters",
                name = "人物有哪些",
                content = "请提取《{book.name}》第{book.durChapterIndex}章《{book.durChapterTitle}》中出现的主要人物\n\n章节内容：\n{chapterContent}",
                showIn = "quick_bar",
                icon = "ic_person",
                sortOrder = 4,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "what_next",
                name = "后续如何",
                content = "基于《{book.name}》已有内容，推测后续情节可能如何发展\n\n当前章节：第{book.durChapterIndex}章 {book.durChapterTitle}\n\n章节内容：\n{chapterContent}",
                showIn = "quick_bar",
                icon = "ic_predict",
                sortOrder = 5,
                isEnabled = true,
                isBuiltin = true
            ),

            // 书籍详情页
            AiPromptEntity(
                id = "book_chat",
                name = "与本书对话",
                content = "你想了解这本书的什么内容？",
                showIn = "book_detail",
                icon = "ic_chat",
                sortOrder = 1,
                isEnabled = true,
                isBuiltin = true
            ),
            AiPromptEntity(
                id = "book_summary_detail",
                name = "生成书籍总结",
                content = "请总结这本书的主要内容",
                showIn = "book_detail",
                icon = "ic_book_summary",
                sortOrder = 2,
                isEnabled = true,
                isBuiltin = true
            )
        )
    }
}

/**
 * 提示词显示项
 */
data class PromptDisplay(
    val id: String,
    val name: String,
    val content: String,
    val icon: String?,
    val showIn: String
)

/**
 * 提示词变量替换器
 * 使用dai411项目现有的Book字段
 */
class PromptVariableReplacer(
    private val book: Book?,
    private val selectedText: String?,
    private val chapterTitle: String? = null,
    private val chapterContent: String? = null
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
            result = result.replace("{book.originName}", b.originName ?: "")

            // 阅读进度
            result = result.replace("{book.durChapterIndex}", b.durChapterIndex.toString())
            result = result.replace("{book.durChapterTitle}", b.durChapterTitle ?: "")
            result = result.replace("{book.totalChapterNum}", b.totalChapterNum.toString())
            result = result.replace("{book.durChapterPos}", b.durChapterPos.toString())
        }

        // 选中文本
        result = result.replace("{selectText}", selectedText ?: "")

        // 章节信息
        result = result.replace("{chapterTitle}", chapterTitle ?: "")
        result = result.replace("{chapterContent}", chapterContent?.take(1000) ?: "")

        // 系统变量
        val now = LocalDateTime.now()
        result = result.replace("{currentTime}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        result = result.replace("{currentDate}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

        return result
    }
}
