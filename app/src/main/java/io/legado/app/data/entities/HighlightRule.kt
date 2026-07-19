package io.legado.app.data.entities

data class HighlightRule(
    var id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var pattern: String = "",
    var sampleText: String = "",
    var group: String = "默认分组",
    var targetScope: Int = TARGET_ALL,
    var enabled: Boolean = true,
    var textColor: Int? = null,
    var bgColor: Int? = null,
    var bold: Boolean = false,
    var underlineMode: Int = 0,
    var underlineColor: Int? = null,
    var underlineWidth: Float = 1f,
    var underlineOffset: Float = 2f,
    var underlineSvgPath: String? = null,
    var bgImage: String? = null,
    var bgImageFit: Int = 0,
    var bgImageScale: Float = 1f,
    // 高亮背景与文字的边距（单位 dp）：负=向内收缩，正=向外扩展
    var bgPaddingStart: Float = 0f,
    var bgPaddingEnd: Float = 0f,
    var bgPaddingTop: Float = 0f,
    var bgPaddingBottom: Float = 0f,
    // 九宫格（bgImageFit=3）：用户用 4 条线自定义「允许拉伸的区域」。
    // 两条竖线 nineLeftX/nineRightX（/图片宽，0~1）、两条横线 nineTopY/nineBottomY（/图片高，0~1），
    // 中间矩形即为可拉伸区；四角与四条边其余部分保持原比例不拉伸。
    // nineStretchMode: 0=全方向拉伸、1=仅水平拉伸、2=仅垂直拉伸
    var nineLeftX: Float = 1f / 3f,
    var nineRightX: Float = 2f / 3f,
    var nineTopY: Float = 1f / 3f,
    var nineBottomY: Float = 2f / 3f,
    var nineStretchMode: Int = 0,
    var scope: String? = null,","explanation":"将九宫格字段改为可拖动的线位置 + 拉伸模式"}
    var excludeScope: String? = null,
    var useProtagonist: Boolean = false,
) {

    fun styleSummary(): String {
        val parts = ArrayList<String>(5)
        parts.add(targetScopeLabel())
        textColor?.let {
            parts.add("字色 ${it.toHexColor()}")
        }
        if (bold) {
            parts.add("加粗")
        }
        bgColor?.let {
            parts.add("背景色 ${it.toHexColor()}")
        }
        if (underlineMode != 0) {
            parts.add(
                when (underlineMode) {
                    1 -> "实线下划线"
                    2 -> "虚线下划线"
                    3 -> "波浪下划线"
                    4 -> "双下划线"
                    5 -> "自定义SVG"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty()
            )
        }
        if (!bgImage.isNullOrBlank()) {
            parts.add(
                when (bgImageFit) {
                    1 -> "背景图(拉伸)"
                    2 -> "背景图(裁剪)"
                    3 -> "背景图(九宫格)"
                    else -> "背景图(平铺)"
                }
            )
        }
        if (parts.isEmpty()) {
            parts.add("无样式")
        }
        return parts.joinToString(" / ")
    }

    fun targetScopeLabel(): String {
        return when (targetScope) {
            TARGET_TITLE -> "作用于标题"
            TARGET_BODY -> "作用于正文"
            else -> "作用于全部"
        }
    }

    fun displayPattern(): String {
        return pattern.ifBlank { ".*" }
    }

    fun normalizedSampleText(): String {
        return sampleText.ifBlank {
            """她轻声说："今晚就出发。"
最近在重读《百年孤独》（纪念版），节奏依然很稳。"""
        }
    }

    fun copyWithNewId(): HighlightRule {
        return copy(id = "${System.currentTimeMillis()}_${name.hashCode()}")
    }

    fun getDisplayNameGroup(): String {
        return name.ifBlank { pattern.ifBlank { "未命名规则" } }
    }

    fun appliesTo(isTitle: Boolean): Boolean {
        return when (targetScope) {
            TARGET_TITLE -> isTitle
            TARGET_BODY -> !isTitle
            else -> true
        }
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
