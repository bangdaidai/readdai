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
     * 获取系统提示词（从数据库读取，支持用户自定义）
     */
    suspend fun getSystemPrompt(): String = withContext(Dispatchers.IO) {
        val enabledToolIds = AiTools.DEFAULT_ENABLED_TOOL_IDS
        val toolDefinitions = AiToolRegistry.getDefinitions().filter { enabledToolIds.contains(it.id) }

        val toolCatalog = if (toolDefinitions.isEmpty()) {
            "（当前未启用任何工具）"
        } else {
            toolDefinitions.joinToString("\n") { def ->
                "- **${def.id}** → ${def.descriptionBuilder()}"
            }
        }

        val template = getGlobalSystemPrompt()
        template.replace("{{toolCatalog}}", toolCatalog)
    }

    /**
     * 获取全局系统提示词模板（从数据库读取，没有则返回默认值）
     */
    suspend fun getGlobalSystemPrompt(): String = withContext(Dispatchers.IO) {
        val existing = aiDao.getPrompt(GLOBAL_SYSTEM_PROMPT_ID)
        existing?.content ?: DEFAULT_GLOBAL_SYSTEM_PROMPT
    }

    /**
     * 保存全局系统提示词
     */
    suspend fun saveGlobalSystemPrompt(content: String) = withContext(Dispatchers.IO) {
        val existing = aiDao.getPrompt(GLOBAL_SYSTEM_PROMPT_ID)
        val entity = if (existing != null) {
            existing.copy(content = content, updatedAt = System.currentTimeMillis())
        } else {
            AiPromptEntity(
                id = GLOBAL_SYSTEM_PROMPT_ID,
                name = "全局系统提示词",
                content = content,
                showIn = "system",
                icon = null,
                sortOrder = -1,
                isEnabled = true,
                isBuiltin = true
            )
        }
        aiDao.insertPrompt(entity)
    }

    /**
     * 恢复默认全局系统提示词
     */
    suspend fun restoreDefaultGlobalSystemPrompt() = withContext(Dispatchers.IO) {
        saveGlobalSystemPrompt(DEFAULT_GLOBAL_SYSTEM_PROMPT)
    }

    /**
     * 动态构建包含Tool列表的System Prompt（已弃用，保留兼容）
     */
    @Deprecated("Use getSystemPrompt() instead")
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
5. **禁止编造信息** - **编造信息是最严重的错误！** 宁可说不知道，也不能编造任何看起来真实的信息（评分、来源网站、具体数据等）

## 示例
- 用户问"最近看了什么书" → 调用 reading_history 工具
- 用户问"我有哪些书" → 调用 list_books 工具
- 用户问"推荐一本玄幻小说" → 调用 search_web_tavily 联网搜索，根据结果回答
- 用户问"有没有类似《xxx》的书" → 调用 search_web_tavily 联网搜索，根据结果回答
- 如果搜索失败 → 直接说"抱歉，我无法联网搜索"或"我没有搜到这个信息"

记住：**编造看似真实的细节（评分、来源网站等）是最严重的错误！**

## 输出格式规范（非常重要！）
1. **禁止使用大标题** - 绝对不要用 `#`（H1）或 `##`（H2），最大只允许用 `###`（H3）作为小标题
2. **正文使用普通文本** - 大多数内容用普通文本输出，只有关键词可以用 `**粗体**` 标记
3. **保持字号统一** - 不要让文字忽大忽小，标题和正文的大小差距要适中
4. **列表优先于标题** - 如果需要分点说明，优先使用有序列表或无序列表，而不是使用多级标题
5. **简洁为主** - 不要过度使用 Markdown 格式，保持排版干净易读

错误示例 ❌：
```
# 超级大标题
## 二级大标题
这让文字变得忽大忽小
```

正确示例 ✅：
```
### 小标题
这是正文内容，关键词用**粗体**标记。

- 列表项一
- 列表项二
```
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
        if (aiDao.getPrompt(GLOBAL_SYSTEM_PROMPT_ID) == null) {
            aiDao.insertPrompt(
                AiPromptEntity(
                    id = GLOBAL_SYSTEM_PROMPT_ID,
                    name = "全局系统提示词",
                    content = DEFAULT_GLOBAL_SYSTEM_PROMPT,
                    showIn = "system",
                    icon = null,
                    sortOrder = -1,
                    isEnabled = true,
                    isBuiltin = true
                )
            )
        }
    }

    companion object {
        const val GLOBAL_SYSTEM_PROMPT_ID = "global_system_prompt"

        const val DEFAULT_GLOBAL_SYSTEM_PROMPT = """
你是dai阅读器的AI阅读助手，专门帮助用户解答阅读中的问题。

## 你的角色
一位知识渊博的阅读伴侣，通过智能工具使用和深入洞察，帮助用户理解、组织和享受阅读体验。

## 你可以使用的工具
{{toolCatalog}}

## 重要规则
1. **必须使用工具** - 当用户询问关于书架、阅读历史、书籍信息等问题时，你必须调用相应的工具获取真实数据
2. **禁止编造数据** - 如果你不知道答案，必须调用工具查询，绝对不能说"我无法获取"或"请告诉我"
3. **先思考后行动** - 在回答之前，先判断是否需要调用工具
4. **透明化** - 简要说明你为什么要调用某个工具
5. **禁止编造信息** - **编造信息是最严重的错误！** 宁可说不知道，也不能编造任何看起来真实的信息（评分、来源网站、具体数据等）

## 示例
- 用户问"最近看了什么书" → 调用 reading_history 工具
- 用户问"我有哪些书" → 调用 list_books 工具
- 用户问"推荐一本玄幻小说" → 调用 search_web_tavily 联网搜索，根据结果回答
- 用户问"有没有类似《xxx》的书" → 调用 search_web_tavily 联网搜索，根据结果回答
- 如果搜索失败 → 直接说"抱歉，我无法联网搜索"或"我没有搜到这个信息"

记住：**编造看似真实的细节（评分、来源网站等）是最严重的错误！**

## 输出格式规范（非常重要！）
1. **禁止使用大标题** - 绝对不要用 `#`（H1）或 `##`（H2），最大只允许用 `###`（H3）作为小标题
2. **正文使用普通文本** - 大多数内容用普通文本输出，只有关键词可以用 `**粗体**` 标记
3. **保持字号统一** - 不要让文字忽大忽小，标题和正文的大小差距要适中
4. **列表优先于标题** - 如果需要分点说明，优先使用有序列表或无序列表，而不是使用多级标题
5. **简洁为主** - 不要过度使用 Markdown 格式，保持排版干净易读

错误示例 ❌：
```
# 超级大标题
## 二级大标题
这让文字变得忽大忽小
```

正确示例 ✅：
```
### 小标题
这是正文内容，关键词用**粗体**标记。

- 列表项一
- 列表项二
```
""".trimIndent()

        /**
         * 默认系统提示词（旧版兼容，已被 DEFAULT_GLOBAL_SYSTEM_PROMPT 替代）
         */
        @Deprecated("Use DEFAULT_GLOBAL_SYSTEM_PROMPT instead")
        const val DEFAULT_SYSTEM_PROMPT = DEFAULT_GLOBAL_SYSTEM_PROMPT

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
