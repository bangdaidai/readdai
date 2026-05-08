package io.legado.app.help.ai

import android.content.Context
import io.legado.app.data.entities.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 技能管理器
 * 参照ReadAny的Skills系统设计
 * 比普通提示词更结构化，包含触发词、指令、变量等
 */
class SkillManager(private val context: Context) {

    private val aiDao = AiDatabase.getInstance(context).aiDao()

    /**
     * 获取指定入口的技能列表
     */
    suspend fun getSkillsForEntrance(entrance: String): List<SkillDisplay> = withContext(Dispatchers.IO) {
        val skills = aiDao.getSkillsByEntrance(entrance)
        skills.map { skill ->
            SkillDisplay(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                triggerWord = skill.triggerWord,
                icon = skill.icon,
                showIn = skill.showIn,
                category = skill.category
            )
        }
    }

    /**
     * 获取所有技能
     */
    suspend fun getAllSkills(): List<AiSkillEntity> = withContext(Dispatchers.IO) {
        aiDao.getAllSkills()
    }

    /**
     * 根据ID获取技能
     */
    suspend fun getSkill(id: String): AiSkillEntity? = withContext(Dispatchers.IO) {
        aiDao.getSkill(id)
    }

    /**
     * 保存技能
     */
    suspend fun saveSkill(skill: AiSkillEntity) = withContext(Dispatchers.IO) {
        aiDao.insertSkill(skill)
    }

    /**
     * 删除技能
     */
    suspend fun deleteSkill(id: String) = withContext(Dispatchers.IO) {
        aiDao.deleteSkill(id)
    }

    /**
     * 启用/禁用技能
     */
    suspend fun setSkillEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        aiDao.setSkillEnabled(id, enabled)
    }

    /**
     * 移动技能排序
     */
    suspend fun moveSkill(id: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val skills = aiDao.getAllSkills().toMutableList()
        val index = skills.indexOfFirst { it.id == id }
        if (index == -1) return@withContext

        val newIndex = if (moveUp) index - 1 else index + 1
        if (newIndex < 0 || newIndex >= skills.size) return@withContext

        // 交换排序值
        val temp = skills[index].sortOrder
        skills[index] = skills[index].copy(sortOrder = skills[newIndex].sortOrder)
        skills[newIndex] = skills[newIndex].copy(sortOrder = temp)

        // 保存
        skills.forEach { skill ->
            aiDao.insertSkill(skill.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 构建技能的完整指令（包含变量替换）
     */
    fun buildSkillInstruction(
        skill: AiSkillEntity,
        variables: Map<String, String> = emptyMap()
    ): String {
        var instruction = skill.instruction

        // 替换变量
        variables.forEach { (key, value) ->
            instruction = instruction.replace("{{$key}}", value)
        }

        return instruction
    }

    /**
     * 初始化默认技能
     */
    suspend fun initDefaultSkills() = withContext(Dispatchers.IO) {
        val existing = aiDao.getAllSkills()
        if (existing.isEmpty()) {
            DEFAULT_SKILLS.forEach { skill ->
                aiDao.insertSkill(skill)
            }
        }
    }

    companion object {
        /**
         * 默认技能列表
         * 参照ReadAny的Built-in skills设计
         */
        val DEFAULT_SKILLS = listOf(
            // 总结类技能
            AiSkillEntity(
                id = "skill_summarize_chapter",
                name = "章节摘要",
                description = "用简洁语言总结当前章节的主要内容",
                triggerWord = "总结本章",
                instruction = """你是一个专业的书籍内容总结助手。
请用简洁清晰的语言总结以下章节内容。

要求：
- 用3-5句话概括核心内容
- 保留关键人物和事件
- 不要添加主观评价

章节内容：
{{chapterContent}}""",
                category = "summarizer",
                icon = "summary",
                showIn = "quick_bar,toolbar",
                sortOrder = 1,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"chapterContent\"]",
                examples = "[\"总结这段内容\", \"帮我概括本章\"]"
            ),
            AiSkillEntity(
                id = "skill_summarize_book",
                name = "全书总结",
                description = "总结整本书的主要内容和主题",
                triggerWord = "总结全书",
                instruction = """你是一个专业的书籍总结助手。
请根据以下书籍信息总结全书的主要内容。

书籍信息：
- 书名：{{bookName}}
- 作者：{{bookAuthor}}
- 简介：{{bookIntro}}
- 当前阅读进度：第{{currentChapter}}章

请总结：
1. 书籍的主要内容和主题
2. 核心人物和情节
3. 书籍的整体风格和特点""",
                category = "summarizer",
                icon = "book",
                showIn = "toolbar,book_detail",
                sortOrder = 2,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"bookName\", \"bookAuthor\", \"bookIntro\", \"currentChapter\"]",
                examples = "[\"这本书讲了什么\", \"帮我总结全书\"]"
            ),
            AiSkillEntity(
                id = "skill_recall",
                name = "前情回顾",
                description = "回顾之前的阅读内容",
                triggerWord = "前情回顾",
                instruction = """你是一个阅读记忆助手。
请根据之前阅读的内容，帮助用户回顾：

之前的阅读内容：
{{previousContent}}

当前章节：{{currentChapter}}

请：
1. 简要回顾之前的关键情节
2. 列出主要人物
3. 说明与当前章节的关联""",
                category = "summarizer",
                icon = "history",
                showIn = "toolbar",
                sortOrder = 3,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"previousContent\", \"currentChapter\"]",
                examples = "[\"之前讲了什么\", \"帮我回顾一下\"]"
            ),

            // 解释类技能
            AiSkillEntity(
                id = "skill_explain_concept",
                name = "概念解释",
                description = "解释选中的概念或术语",
                triggerWord = "解释这个",
                instruction = """你是一个专业的概念解释助手。
请用通俗易懂的语言解释以下概念：

概念：{{concept}}

上下文：{{contextText}}

请解释：
1. 这个概念的基本含义
2. 在这个语境中的具体含义
3. 如果有相关的例子请举一个""",
                category = "explainer",
                icon = "help",
                showIn = "text_menu,quick_bar",
                sortOrder = 10,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"concept\", \"contextText\"]",
                examples = "[\"这是什么意思\", \"解释一下这个\"]"
            ),
            AiSkillEntity(
                id = "skill_explain_sentence",
                name = "句子分析",
                description = "分析选中的句子",
                triggerWord = "分析这句",
                instruction = """你是一个文学分析助手。
请分析以下句子：

句子：{{selectedText}}

请从以下角度分析：
1. 句子含义
2. 写作手法和修辞
3. 表达的情感或意图
4. 在文中的作用""",
                category = "explainer",
                icon = "analysis",
                showIn = "text_menu",
                sortOrder = 11,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"selectedText\"]",
                examples = "[\"分析这段话\", \"这句话有什么含义\"]"
            ),

            // 分析类技能
            AiSkillEntity(
                id = "skill_analyze_character",
                name = "人物分析",
                description = "分析出现的人物",
                triggerWord = "人物分析",
                instruction = """你是一个人物分析助手。
请分析以下内容中出现的人物：

内容：{{content}}

请列出：
1. 主要人物及其特点
2. 人物之间的关系
3. 人物的成长或变化
4. 作者对人物的刻画方式""",
                category = "analysis",
                icon = "person",
                showIn = "text_menu,quick_bar",
                sortOrder = 20,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"content\"]",
                examples = "[\"有哪些人物\", \"分析这段的人物\"]"
            ),
            AiSkillEntity(
                id = "skill_analyze_writing",
                name = "写作分析",
                description = "分析写作手法和风格",
                triggerWord = "写作分析",
                instruction = """你是一个写作分析助手。
请分析以下内容的写作特点：

内容：{{selectedText}}

请分析：
1. 使用的写作手法（比喻、拟人、排比等）
2. 语言风格特点
3. 段落结构
4. 值得学习的地方""",
                category = "analysis",
                icon = "write",
                showIn = "text_menu",
                sortOrder = 21,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"selectedText\"]",
                examples = "[\"分析写作手法\", \"这段写得怎么样\"]"
            ),

            // 追踪类技能
            AiSkillEntity(
                id = "skill_track_character",
                name = "人物追踪",
                description = "追踪某个人物的出场和活动",
                triggerWord = "追踪人物",
                instruction = """你是一个人物追踪助手。
请追踪以下人物在书籍中的出现：

要追踪的人物：{{characterName}}

已阅读的内容：{{readContent}}

请列出：
1. 该人物出场的情节
2. 人物的重要行为和决策
3. 与其他人物的关系变化
4. 人物的命运走向""",
                category = "tracking",
                icon = "ic追踪",
                showIn = "quick_bar",
                sortOrder = 30,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"characterName\", \"readContent\"]",
                examples = "[\"追踪XXX\", \"这个人物做了什么\"]"
            ),
            AiSkillEntity(
                id = "skill_predict",
                name = "情节预测",
                description = "基于已有内容预测后续情节",
                triggerWord = "后续如何",
                instruction = """你是一个情节预测助手。
请基于以下内容预测后续情节：

当前阅读内容：{{currentContent}}

请预测：
1. 可能的情节发展方向
2. 人物可能的命运
3. 作者可能的写作意图
4. 有哪些可能的转折

注意：预测要基于已有线索，不要凭空想象。""",
                category = "tracking",
                icon = "predict",
                showIn = "quick_bar",
                sortOrder = 31,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"currentContent\"]",
                examples = "[\"后面会怎样\", \"接下来会发生什么\"]"
            ),

            // 自定义技能示例
            AiSkillEntity(
                id = "skill_custom_qa",
                name = "智能问答",
                description = "回答关于书籍内容的问题",
                triggerWord = "问我答",
                instruction = """你是一个阅读问答助手。
用户的问题是：{{question}}

当前阅读内容：
- 书籍：{{bookName}}
- 章节：{{chapterTitle}}
- 内容：{{chapterContent}}

请根据以上信息回答用户的问题。
如果问题与当前阅读内容无关，请礼貌地说明并尝试回答。""",
                category = "custom",
                icon = "chat",
                showIn = "quick_bar,text_menu",
                sortOrder = 100,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"question\", \"bookName\", \"chapterTitle\", \"chapterContent\"]",
                examples = "[\"这本书怎么样\", \"这个人物是谁\"]"
            ),

            // 思维导图技能
            AiSkillEntity(
                id = "skill_mindmap",
                name = "生成思维导图",
                description = "根据内容生成结构化的思维导图",
                triggerWord = "思维导图",
                instruction = """你是一个专业的思维导图生成助手。
请根据以下内容生成一个结构化的思维导图。

内容：{{content}}

请按以下格式生成思维导图（使用Markdown格式）：

# 中心主题

## 一级分支1
- 二级要点1.1
- 二级要点1.2
  - 三级细节

## 一级分支2
- 二级要点2.1
- 二级要点2.2

要求：
1. 中心主题要简洁明了
2. 分支层次清晰，一般不超过3层
3. 每个要点用简洁的词语或短语
4. 相关内容可以互相连接
5. 可以使用emoji增强可读性

请直接生成思维导图，不要有其他解释。""",
                category = "summarizer",
                icon = "mindmap",
                showIn = "toolbar,quick_bar",
                sortOrder = 5,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"content\", \"bookName\", \"chapterTitle\"]",
                examples = "[\"生成思维导图\", \"帮我画个图\"]"
            ),

            // 主题分析技能
            AiSkillEntity(
                id = "skill_theme_analysis",
                name = "主题解读",
                description = "分析书籍的主题思想和深层含义",
                triggerWord = "主题解读",
                instruction = """你是一个文学主题分析专家。
请分析以下书籍的主题思想：

书籍信息：
- 书名：{{bookName}}
- 作者：{{bookAuthor}}
- 简介：{{bookIntro}}

当前阅读内容：
{{chapterContent}}

请从以下角度分析：
1. 核心主题和思想
2. 作者想要表达的价值观
3. 社会意义和现实启示
4. 文学手法和象征意义

注意：分析要深入但易懂，避免过度学术化。""",
                category = "analysis",
                icon = "theme",
                showIn = "quick_bar,toolbar",
                sortOrder = 4,
                isEnabled = true,
                isBuiltin = true,
                variables = "[\"bookName\", \"bookAuthor\", \"bookIntro\", \"chapterContent\"]",
                examples = "[\"这本书想表达什么\", \"主题是什么\"]"
            )
        )
    }
}

/**
 * 技能显示项
 */
data class SkillDisplay(
    val id: String,
    val name: String,
    val description: String,
    val triggerWord: String,
    val icon: String?,
    val showIn: String,
    val category: String
)

/**
 * 技能变量替换器
 */
class SkillVariableReplacer(
    private val book: Book?,
    private val selectedText: String?,
    private val chapterTitle: String? = null,
    private val chapterContent: String? = null,
    private val previousContent: String? = null,
    private val question: String? = null
) {

    fun replace(input: String): String {
        var result = input

        // 书籍基本信息
        book?.let { b ->
            result = result.replace("{{bookName}}", b.name)
            result = result.replace("{{bookAuthor}}", b.author ?: "")
            result = result.replace("{{bookIntro}}", b.intro ?: "")
            result = result.replace("{{currentChapter}}", (b.durChapterIndex + 1).toString())
            result = result.replace("{{chapterTitle}}", b.durChapterTitle ?: "")
        }

        // 选中文本
        result = result.replace("{{selectedText}}", selectedText ?: "")
        result = result.replace("{{concept}}", selectedText ?: "")

        // 章节信息
        result = result.replace("{{chapterTitle}}", chapterTitle ?: "")
        result = result.replace("{{chapterContent}}", chapterContent?.take(2000) ?: "")
        result = result.replace("{{content}}", chapterContent?.take(2000) ?: "")
        result = result.replace("{{currentContent}}", chapterContent?.take(1000) ?: "")

        // 前情回顾
        result = result.replace("{{previousContent}}", previousContent ?: "")

        // 问答
        result = result.replace("{{question}}", question ?: "")

        // 上下文
        result = result.replace("{{contextText}}", selectedText ?: "")

        return result
    }

    fun getVariables(): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        book?.let { b ->
            vars["bookName"] = b.name
            vars["bookAuthor"] = b.author ?: ""
            vars["bookIntro"] = b.intro ?: ""
            vars["currentChapter"] = (b.durChapterIndex + 1).toString()
            vars["chapterTitle"] = b.durChapterTitle ?: ""
        }

        selectedText?.let {
            vars["selectedText"] = it
            vars["concept"] = it
            vars["contextText"] = it
        }

        chapterTitle?.let { vars["chapterTitle"] = it }
        chapterContent?.let {
            vars["chapterContent"] = it.take(2000)
            vars["content"] = it.take(2000)
            vars["currentContent"] = it.take(1000)
        }

        previousContent?.let { vars["previousContent"] = it }
        question?.let { vars["question"] = it }

        return vars
    }
}
