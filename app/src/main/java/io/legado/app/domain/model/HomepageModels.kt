package io.legado.app.domain.model

import androidx.annotation.Keep

@Keep
data class ModuleItem(
    val id: String = "",
    val sourceUrl: String = "",
    val moduleKey: String = "",
    val type: String = "",
    val title: String = "",
    val customTitle: String? = null,
    val customSetTitle: String? = null,
    val args: String? = null,
    val layoutConfig: String? = null,
    val url: String? = null,
    val isEnabled: Boolean = true,
    val customSetId: String? = null,
    val isUserCreated: Boolean = false,
    val sortOrder: Int = 0,
    val sourceJsonHash: String? = null,
    val syncedAt: Long = 0,
) {
    val displayTitle: String get() = customTitle ?: title
}

@Keep
data class CustomSetItem(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
)

@Keep
data class ModuleDef(
    val key: String = "",
    val type: String = "",
    val title: String = "",
    val args: String? = null,
    val layoutConfig: String? = null,
    val url: String? = null,
    val sourceUrl: String = "",
) {
    val globalId: String get() = globalIdOf(sourceUrl, key)

    companion object {
        fun globalIdOf(sourceUrl: String, key: String, setId: String? = null): String {
            val targetSetId = setId ?: "src_$sourceUrl"
            return "$targetSetId::$sourceUrl::$key"
        }
    }
}

enum class HomepageModuleType(val key: String, val title: String) {
    Banner("banner", "横滑轮播"),
    Ranking("ranking", "排行榜"),
    GridRanking("gridRanking", "网格排行榜"),
    Grid("grid", "网格"),
    Card("card", "推荐卡片"),
    InfiniteGrid("infiniteGrid", "无限网格"),
    ButtonGroup("buttonGroup", "按钮组"),
    Waterfall("waterfall", "错位瀑布流"),
    Unknown("", "未知");

    companion object {
        fun fromKey(key: String?): HomepageModuleType =
            entries.find { it.key == key } ?: Unknown
    }
}

enum class BookShelfState {
    IN_SHELF, SAME_NAME_AUTHOR, NOT_IN_SHELF
}
