package io.legado.app.help.ai

/**
 * 推理过程解析器
 * 参照ReadAny的ThinkTagStreamParser和anx53的AiReasoningParser设计
 * 用于解析AI返回的思维链标记 <think>...</think>
 */

/**
 * 推理片段类型
 */
sealed class ReasoningChunk {
    /**
     * 普通文本内容
     */
    data class Text(val content: String) : ReasoningChunk()
    
    /**
     * 推理过程内容
     */
    data class Reasoning(val content: String) : ReasoningChunk()
}

/**
 * 推理信封 - 分离推理过程和最终答案
 */
data class ReasoningEnvelope(
    val reasoningContent: String = "",
    val answerContent: String = ""
) {
    companion object {
        private val THINK_REGEX = Regex("<think>([\\s\\S]*?)</think>")
        
        /**
         * 从完整内容中分离推理和答案
         */
        fun split(content: String): ReasoningEnvelope {
            val matches = THINK_REGEX.findAll(content).toList()
            
            if (matches.isEmpty()) {
                return ReasoningEnvelope(
                    reasoningContent = "",
                    answerContent = content
                )
            }
            
            val reasoning = matches
                .mapNotNull { it.groupValues.getOrNull(1) }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            
            val answer = content.replace(THINK_REGEX, "")
                .trimStart('\n', '\r')
            
            return ReasoningEnvelope(
                reasoningContent = reasoning,
                answerContent = answer
            )
        }
        
        /**
         * 组合推理和答案为完整内容
         */
        fun compose(answerContent: String, reasoningContent: String): String {
            if (reasoningContent.isEmpty()) {
                return answerContent
            }
            if (answerContent.isEmpty()) {
                return "<think>$reasoningContent</think>"
            }
            return "<think>$reasoningContent</think>\n$answerContent"
        }
    }
}

/**
 * 流式推理标签解析器
 * 用于实时解析流式响应中的<think>标签
 */
class ThinkTagStreamParser {
    
    private enum class Mode {
        TEXT,
        REASONING
    }
    
    private var mode: Mode = Mode.TEXT
    private var carry: String = ""
    
    companion object {
        private const val THINK_OPEN_TAG = "<think>"
        private const val THINK_CLOSE_TAG = "</think>"
    }
    
    /**
     * 推送新的文本块，返回解析后的片段列表
     */
    fun push(chunk: String): List<ReasoningChunk> {
        if (chunk.isEmpty()) return emptyList()
        
        carry += chunk
        val events = mutableListOf<ReasoningChunk>()
        
        while (carry.isNotEmpty()) {
            val currentTag = if (mode == Mode.TEXT) THINK_OPEN_TAG else THINK_CLOSE_TAG
            val nextMode = if (mode == Mode.TEXT) Mode.REASONING else Mode.TEXT
            val matchIndex = carry.indexOf(currentTag)
            
            if (matchIndex >= 0) {
                // 找到标签，提取前面的内容
                val content = carry.substring(0, matchIndex)
                if (content.isNotEmpty()) {
                    events.add(
                        if (mode == Mode.TEXT) {
                            ReasoningChunk.Text(content)
                        } else {
                            ReasoningChunk.Reasoning(content)
                        }
                    )
                }
                
                // 移除已处理的标签
                carry = carry.substring(matchIndex + currentTag.length)
                mode = nextMode
                continue
            }
            
            // 检查是否有未完成的标签前缀
            val carryLength = getTrailingTagPrefixLength(carry, currentTag)
            val safeContent = carry.substring(0, carry.length - carryLength)
            
            if (safeContent.isNotEmpty()) {
                events.add(
                    if (mode == Mode.TEXT) {
                        ReasoningChunk.Text(safeContent)
                    } else {
                        ReasoningChunk.Reasoning(safeContent)
                    }
                )
            }
            
            // 保留可能的标签前缀
            carry = carry.substring(carry.length - carryLength)
            break
        }
        
        return events
    }
    
    /**
     * 刷新缓冲区，返回剩余内容
     */
    fun flush(): List<ReasoningChunk> {
        if (carry.isEmpty()) {
            reset()
            return emptyList()
        }
        
        val events = listOf(
            if (mode == Mode.TEXT) {
                ReasoningChunk.Text(carry)
            } else {
                ReasoningChunk.Reasoning(carry)
            }
        )
        
        reset()
        return events
    }
    
    /**
     * 重置解析器状态
     */
    fun reset() {
        mode = Mode.TEXT
        carry = ""
    }
    
    /**
     * 获取尾部标签前缀长度
     */
    private fun getTrailingTagPrefixLength(value: String, tag: String): Int {
        val maxPrefixLength = minOf(value.length, tag.length - 1)
        
        for (length in maxPrefixLength downTo 1) {
            if (value.endsWith(tag.substring(0, length))) {
                return length
            }
        }
        
        return 0
    }
}

/**
 * 工具步骤数据类
 */
data class ToolStep(
    val name: String,
    val status: ToolStepStatus = ToolStepStatus.PENDING,
    val input: String? = null,
    val output: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 工具步骤状态
 */
enum class ToolStepStatus {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    SUCCESS,    // 成功
    FAILED      // 失败
}

/**
 * 推理时间线索引
 */
data class ReasoningTimeline(
    val entries: List<ReasoningEntry> = emptyList()
) {
    val reasoningEntries: List<ReasoningEntry> 
        get() = entries.filter { it.section == ReasoningSection.REASONING }
    
    val answerEntries: List<ReasoningEntry>
        get() = entries.filter { it.section == ReasoningSection.ANSWER }
    
    val toolSteps: List<ToolStep>
        get() = entries.mapNotNull { it.toolStep }
    
    val hasToolSteps: Boolean
        get() = entries.any { it.toolStep != null }
    
    val hasReasoning: Boolean
        get() = entries.any { it.type == ReasoningEntryType.REPLY && it.section == ReasoningSection.REASONING }
}

/**
 * 推理条目类型
 */
enum class ReasoningEntryType {
    REPLY,  // 回复文本
    TOOL    // 工具调用
}

/**
 * 推理部分
 */
enum class ReasoningSection {
    REASONING,  // 推理过程
    ANSWER      // 最终答案
}

/**
 * 推理条目
 */
sealed class ReasoningEntry {
    abstract val type: ReasoningEntryType
    abstract val section: ReasoningSection
    abstract val toolStep: ToolStep?
    
    data class ReplyEntry(
        val text: String,
        override val section: ReasoningSection
    ) : ReasoningEntry() {
        override val type: ReasoningEntryType = ReasoningEntryType.REPLY
        override val toolStep: ToolStep? = null
    }
    
    data class ToolEntry(
        val step: ToolStep,
        override val section: ReasoningSection
    ) : ReasoningEntry() {
        override val type: ReasoningEntryType = ReasoningEntryType.TOOL
        override val toolStep: ToolStep? = step
    }
}

/**
 * 解析推理内容
 */
object ReasoningParser {
    
    private val THINK_REGEX = Regex("<think>([\\s\\S]*?)</think>")
    private val TOOL_STEP_REGEX = Regex("<tool-step\\s+([^/>]+?)\\s*/>")
    private val REPLY_REGEX = Regex("<reply\\s+([^/>]+?)\\s*/>")
    
    /**
     * 解析完整的推理内容
     */
    fun parse(content: String): ReasoningTimeline {
        val timeline = mutableListOf<ReasoningEntry>()
        var remaining = content
        
        // 提取<think>标签内的推理内容
        val thinkMatches = THINK_REGEX.findAll(content).toList()
        if (thinkMatches.isNotEmpty()) {
            for (match in thinkMatches) {
                val inner = match.groupValues.getOrNull(1)
                if (!inner.isNullOrEmpty()) {
                    parseTimeline(inner, timeline, ReasoningSection.REASONING)
                }
            }
            remaining = content.replace(THINK_REGEX, "")
        }
        
        // 处理剩余的答案内容
        if (remaining.isNotEmpty()) {
            parseTimeline(remaining, timeline, ReasoningSection.ANSWER)
        }
        
        return ReasoningTimeline(entries = timeline)
    }
    
    /**
     * 解析时间线
     */
    private fun parseTimeline(
        source: String,
        timeline: MutableList<ReasoningEntry>,
        section: ReasoningSection
    ) {
        if (source.isEmpty()) return
        
        var currentIndex = 0
        var buffer = StringBuilder()
        
        fun flushBuffer() {
            val chunk = buffer.toString()
            buffer = StringBuilder()
            if (chunk.isNotEmpty()) {
                timeline.add(ReasoningEntry.ReplyEntry(chunk, section))
            }
        }
        
        // 查找工具步骤标签
        for (match in TOOL_STEP_REGEX.findAll(source)) {
            val preceding = source.substring(currentIndex, match.range.first)
            if (preceding.isNotEmpty()) {
                buffer.append(preceding)
            }
            
            val attrs = parseAttributes(match.groupValues[1])
            val step = parseToolStep(attrs)
            
            flushBuffer()
            timeline.add(ReasoningEntry.ToolEntry(step, section))
            
            currentIndex = match.range.last + 1
        }
        
        // 处理剩余内容
        val trailing = source.substring(currentIndex)
        if (trailing.isNotEmpty()) {
            buffer.append(trailing)
        }
        flushBuffer()
    }
    
    /**
     * 解析属性
     */
    private fun parseAttributes(raw: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val attrRegex = Regex("(\\w+)='([^']*)'")
        
        for (match in attrRegex.findAll(raw)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            attrs[key] = unescapeAttr(value)
        }
        
        return attrs
    }
    
    /**
     * 解码属性值（支持base64）
     */
    private fun decodeAttrValue(attrs: Map<String, String>, key: String): String? {
        val direct = attrs[key]
        if (direct != null) {
            return unescapeAttr(direct)
        }
        
        val encoded = attrs["${key}_b64"]
        if (encoded != null) {
            return try {
                android.util.Base64.decode(unescapeAttr(encoded), android.util.Base64.DEFAULT)
                    .toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }
        
        return null
    }
    
    /**
     * 反转义属性值
     */
    private fun unescapeAttr(value: String): String {
        return try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
    }
    
    /**
     * 解析工具步骤
     */
    private fun parseToolStep(attrs: Map<String, String>): ToolStep {
        val name = unescapeAttr(attrs["name"] ?: "")
        val status = when ((attrs["status"] ?: "pending").lowercase()) {
            "running" -> ToolStepStatus.RUNNING
            "success" -> ToolStepStatus.SUCCESS
            "failed" -> ToolStepStatus.FAILED
            else -> ToolStepStatus.PENDING
        }
        val input = decodeAttrValue(attrs, "input")
        val output = decodeAttrValue(attrs, "output")
        val error = decodeAttrValue(attrs, "error")
        
        return ToolStep(
            name = name,
            status = status,
            input = input,
            output = output,
            error = error
        )
    }
    
    /**
     * 将推理内容转换为纯文本（去除标签）
     */
    fun toPlainText(content: String): String {
        val timeline = parse(content)
        
        if (timeline.entries.isEmpty()) {
            return content
        }
        
        val sections = mutableListOf<String>()
        
        for (entry in timeline.entries) {
            when (entry) {
                is ReasoningEntry.ReplyEntry -> {
                    if (entry.text.isNotEmpty()) {
                        sections.add(entry.text)
                    }
                }
                is ReasoningEntry.ToolEntry -> {
                    val step = entry.toolStep!!
                    val lines = mutableListOf<String>()
                    lines.add("[工具 ${step.name} ${getStatusText(step.status)}]")
                    
                    step.output?.trim()?.let { output ->
                        if (output.isNotEmpty()) {
                            lines.add(output)
                        }
                    }
                    
                    step.error?.trim()?.let { error ->
                        if (error.isNotEmpty()) {
                            lines.add("错误: $error")
                        }
                    }
                    
                    step.input?.trim()?.let { input ->
                        if (input.isNotEmpty()) {
                            lines.add("输入: $input")
                        }
                    }
                    
                    val section = lines.joinToString("\n").trim()
                    if (section.isNotEmpty()) {
                        sections.add(section)
                    }
                }
            }
        }
        
        val result = sections.joinToString("\n\n").trim()
        return if (result.isEmpty()) content else result
    }
    
    private fun getStatusText(status: ToolStepStatus): String {
        return when (status) {
            ToolStepStatus.PENDING -> "待执行"
            ToolStepStatus.RUNNING -> "执行中"
            ToolStepStatus.SUCCESS -> "成功"
            ToolStepStatus.FAILED -> "失败"
        }
    }
}
