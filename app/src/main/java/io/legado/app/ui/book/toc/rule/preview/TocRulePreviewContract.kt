package io.legado.app.ui.book.toc.rule.preview

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.TxtTocRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * 目录规则预览页面状态（复用 MD3 的单向数据流架构）
 *
 * TXT 本地书：支持选中规则并应用到本书目录、就地编辑规则、跳转管理页。
 * 网络书籍：展示缓存目录“原标题 → 替换后标题”对比。
 */
@Stable
data class TocRulePreviewUiState(
    val loading: Boolean = true,
    // 模式：网络书籍 or TXT 本地书
    val isTxt: Boolean = false,
    // 网络书籍：原目录 vs 替换后目录对比
    val originalChapters: ImmutableList<String> = persistentListOf(),
    val displayChapters: ImmutableList<String> = persistentListOf(),
    val useReplace: Boolean = true,
    val titleReplaceRules: ImmutableList<ReplaceRule> = persistentListOf(),
    // TXT：正则目录规则预览
    val txtRules: ImmutableList<TxtRulePreviewItem> = persistentListOf(),
    // TXT：当前选中（高亮）的规则正则，初始化为本书正在使用的目录规则
    val selectedRule: String = "",
    // TXT：正在编辑的规则（非 null 时弹出编辑抽屉）
    val editingRule: TxtTocRule? = null,
    val activeSheet: TocRulePreviewSheet? = null,
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val emptyHint: String = "",
) {
    val hasSelection: Boolean get() = selectedRule.isNotEmpty()

    val filteredTxtRules: ImmutableList<TxtRulePreviewItem>
        get() = if (searchQuery.isBlank()) txtRules
        else txtRules.filter {
            it.rule.name.contains(searchQuery, ignoreCase = true) ||
                    it.rule.rule.contains(searchQuery, ignoreCase = true) ||
                    it.rule.example?.contains(searchQuery, ignoreCase = true) == true
        }.toImmutableList()
}

@Stable
data class TxtRulePreviewItem(
    val rule: TxtTocRule,
    val matchCount: Int = 0,
    // -1 表示尚未计算（TXT 模式占位）
    val computedState: Int = -1,
    val chapters: ImmutableList<String> = persistentListOf(),
) {
    val computed: Boolean get() = computedState >= 0
    val matchCountResolved: Int get() = if (computedState < 0) 0 else computedState
}

sealed interface TocRulePreviewSheet {
    data class ChapterList(val item: TxtRulePreviewItem) : TocRulePreviewSheet
}

sealed interface TocRulePreviewIntent {
    data object DismissSheet : TocRulePreviewIntent
    data class ShowChapterList(val item: TxtRulePreviewItem) : TocRulePreviewIntent
    // TXT：选中某条规则（用于高亮 + 应用）
    data class SelectRule(val rule: String) : TocRulePreviewIntent
    // TXT：打开规则编辑抽屉
    data class EditRule(val rule: TxtTocRule) : TocRulePreviewIntent
    data object DismissEditDialog : TocRulePreviewIntent
    // TXT：保存编辑后的规则
    data class SaveRule(val rule: TxtTocRule) : TocRulePreviewIntent
    // TXT：把选中规则应用到本书目录
    data object ApplyRule : TocRulePreviewIntent
    // TXT：跳转规则管理页
    data object OpenManagePage : TocRulePreviewIntent
    data object ToggleSearch : TocRulePreviewIntent
    data class UpdateSearchQuery(val query: String) : TocRulePreviewIntent
}

sealed interface TocRulePreviewEffect {
    data class ShowToast(val message: String) : TocRulePreviewEffect
    // 应用规则到书籍，附带完整 tocRegex（rule + spaceChars + replacement）
    data class ApplyRule(val tocRegex: String) : TocRulePreviewEffect
    data object OpenManagePage : TocRulePreviewEffect
}
