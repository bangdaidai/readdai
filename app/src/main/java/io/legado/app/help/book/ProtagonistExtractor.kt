package io.legado.app.help.book

import java.util.regex.Pattern

/**
 * 主角名提取工具
 * 从书籍简介中提取可能的主角名
 */
object ProtagonistExtractor {

    // 主角模式：匹配 "主角：" 或 "主角:" 后面的内容
    private val protagonistPattern = Pattern.compile("主角[：:](.+)")

    /**
     * 从简介中提取主角名
     * @param intro 书籍简介
     * @return 提取的主角名列表
     */
    fun extractProtagonists(intro: String?): List<String> {
        if (intro.isNullOrEmpty()) {
            return emptyList()
        }

        val protagonists = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()

        // 只提取 "主角：xxx" 格式的主角名
        extractByProtagonistPattern(intro, protagonists, seenNames)

        return protagonists
    }

    /**
     * 通过 "主角：xxx" 模式提取主角名
     */
    private fun extractByProtagonistPattern(intro: String, protagonists: MutableList<String>, seenNames: MutableSet<String>) {
        val matcher = protagonistPattern.matcher(intro)
        while (matcher.find()) {
            val namesPart = matcher.group(1)
            if (namesPart != null) {
                // 支持多种分隔符
                val names = namesPart.split("、", "，", ",")
                for (name in names) {
                    val trimmedName = name.trim()
                    if (trimmedName.isNotEmpty() && isValidName(trimmedName) && !seenNames.contains(trimmedName)) {
                        protagonists.add(trimmedName)
                        seenNames.add(trimmedName)
                    }
                }
            }
        }
    }

    /**
     * 验证是否为有效的人名
     */
    private fun isValidName(name: String): Boolean {
        // 排除一些常见的非人名词汇
        val invalidNames = listOf(
            "本书", "小说", "故事", "情节", "背景", "世界", "时间", "空间",
            "开始", "结束", "喜欢", "讨厌", "帮助", "支持", "朋友", "敌人",
            "家人", "亲人", "爱人", "情人", "敌人", "对手", "伙伴", "团队"
        )

        return name.length in 2..4 && !invalidNames.contains(name)
    }

    /**
     * 从简介中提取第一个可能的主角名
     */
    @Suppress("unused")
    fun extractFirstProtagonist(intro: String?): String? {
        return extractProtagonists(intro).firstOrNull()
    }
}