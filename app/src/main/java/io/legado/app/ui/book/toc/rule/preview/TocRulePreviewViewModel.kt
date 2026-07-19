package io.legado.app.ui.book.toc.rule.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DefaultData
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.toReplaceBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.utils.Utf8BomUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TocRulePreviewViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()

    private val context get() = app.applicationContext

    private val _uiState = MutableStateFlow(TocRulePreviewUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TocRulePreviewEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var book: Book? = null
    private var lazyJob: Job? = null

    fun init(bookUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = runCatching { appDb.bookDao.getBook(bookUrl) }.getOrNull()
            this@TocRulePreviewViewModel.book = book
            if (book == null) {
                _uiState.update {
                    it.copy(loading = false, emptyHint = context.getString(R.string.toc_preview_empty))
                }
                return@launch
            }
            if (book.isLocalTxt) {
                loadTxtPreview(book)
            } else {
                loadNetworkPreview(book)
            }
        }
    }

    fun onIntent(intent: TocRulePreviewIntent) {
        when (intent) {
            is TocRulePreviewIntent.ShowChapterList -> {
                _uiState.update { it.copy(activeSheet = TocRulePreviewSheet.ChapterList(intent.item)) }
            }
            is TocRulePreviewIntent.ShowNetworkRuleChapters -> {
                _uiState.update { it.copy(activeSheet = TocRulePreviewSheet.NetworkRuleChapters(intent.item)) }
            }
            is TocRulePreviewIntent.DismissSheet -> {
                _uiState.update { it.copy(activeSheet = null) }
            }
            is TocRulePreviewIntent.ToggleSearch -> {
                _uiState.update {
                    it.copy(showSearch = !it.showSearch, searchQuery = if (it.showSearch) "" else it.searchQuery)
                }
            }
            is TocRulePreviewIntent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }
            // ===== TXT 模式交互 =====
            is TocRulePreviewIntent.SelectRule -> {
                _uiState.update { it.copy(selectedRule = intent.rule) }
            }
            is TocRulePreviewIntent.EditRule -> {
                _uiState.update { it.copy(activeSheet = null, editingRule = intent.rule) }
            }
            is TocRulePreviewIntent.DismissEditDialog -> {
                _uiState.update { it.copy(editingRule = null) }
            }
            is TocRulePreviewIntent.SaveRule -> {
                viewModelScope.launch(Dispatchers.IO) {
                    saveRuleAndRefresh(intent.rule)
                }
            }
            is TocRulePreviewIntent.ApplyRule -> {
                val selected = _uiState.value.selectedRule
                val item = _uiState.value.txtRules.find { it.rule.rule == selected }
                if (item != null) {
                    val tocRegex = item.rule.rule + TextFile.spaceChars + item.rule.replacement
                    _effects.tryEmit(TocRulePreviewEffect.ApplyRule(tocRegex))
                } else {
                    _effects.tryEmit(TocRulePreviewEffect.ShowToast(context.getString(R.string.apply_fail)))
                }
            }
            is TocRulePreviewIntent.OpenManagePage -> {
                _effects.tryEmit(TocRulePreviewEffect.OpenManagePage)
            }
        }
    }

    // ===================== 网络书籍预览 =====================

    private fun loadNetworkPreview(book: Book) {
        _uiState.update { it.copy(loading = true, isTxt = false) }
        val useReplace = book.getUseReplaceRule()
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapters.isEmpty()) {
            _uiState.update {
                it.copy(
                    loading = false,
                    useReplace = useReplace,
                    emptyHint = context.getString(R.string.toc_preview_empty),
                )
            }
            return
        }
        val processor = ContentProcessor.get(book)
        val titleRules: List<ReplaceRule> = processor.getTitleReplaceRules()
            .filter { it.isEnabled && it.scopeTitle }

        val replaceBook = book.toReplaceBook()
        // 按实际阅读时的顺序链式应用：统计每条规则在「前序规则结果」上的增量命中，
        // 与 getDisplayTitle(titleRules) 的真实行为一致（后一条作用于前一条的输出）
        val items = titleRules.mapIndexed { index, rule ->
            var matchCount = 0
            val samples = mutableListOf<Pair<String, String>>()
            for (ch in chapters) {
                val before = ch.getDisplayTitle(
                    titleRules.subList(0, index),
                    useReplace = true,
                    chineseConvert = false,
                    replaceBook = replaceBook,
                )
                val after = ch.getDisplayTitle(
                    titleRules.subList(0, index + 1),
                    useReplace = true,
                    chineseConvert = false,
                    replaceBook = replaceBook,
                )
                if (after != before) {
                    matchCount++
                    if (samples.size < 200) {
                        samples.add(before to after)
                    }
                }
            }
            val exampleText = samples.firstOrNull()?.let { (before, after) -> "$before → $after" }
            NetworkRulePreviewItem(
                rule = rule,
                matchCount = matchCount,
                totalChapter = chapters.size,
                chapters = samples.toImmutableList(),
                example = exampleText,
            )
        }

        _uiState.update {
            it.copy(
                loading = false,
                useReplace = useReplace,
                chapterTotal = chapters.size,
                titleReplaceRules = titleRules.toImmutableList(),
                networkRuleItems = items.toImmutableList(),
            )
        }
    }

    // ===================== TXT 目录规则预览 =====================

    private fun loadTxtPreview(book: Book) {
        _uiState.update { it.copy(loading = true, isTxt = true) }
        val currentRule = book.tocUrl.split(TextFile.spaceChars, limit = 2).firstOrNull() ?: ""
        val rules = getAllTxtRules()
        val items = rules.map { TxtRulePreviewItem(rule = it) }
        _uiState.update {
            it.copy(
                loading = false,
                isTxt = true,
                selectedRule = currentRule,
                txtRules = items.toImmutableList(),
            )
        }
        computeTxtRuleCounts(book, rules)
    }

    private fun computeTxtRuleCounts(book: Book, rules: List<TxtTocRule>) {
        lazyJob?.cancel()
        lazyJob = viewModelScope.launch(Dispatchers.IO) {
            val resultMap = mutableMapOf<Long, TxtRulePreviewItem>()
            for (rule in rules) {
                ensureActive()
                resultMap[rule.id] = computeTxtRulePreview(book, rule)
            }
            _uiState.update { state ->
                val newRules = state.txtRules.map { existing ->
                    resultMap[existing.rule.id] ?: existing
                }.toImmutableList()
                state.copy(txtRules = newRules)
            }
        }
    }

    private suspend fun computeTxtRulePreview(book: Book, rule: TxtTocRule): TxtRulePreviewItem {
        val pattern = try {
            rule.rule.toPattern(Pattern.MULTILINE)
        } catch (e: PatternSyntaxException) {
            return TxtRulePreviewItem(rule = rule, computedState = 0)
        }
        return try {
            val (chapters, total) = analyzeForPreview(book, pattern)
            TxtRulePreviewItem(
                rule = rule,
                computedState = total,
                chapters = chapters.take(200).toImmutableList(),
            )
        } catch (e: Exception) {
            TxtRulePreviewItem(rule = rule, computedState = 0)
        }
    }

    private suspend fun analyzeForPreview(book: Book, pattern: Pattern): Pair<List<String>, Int> {
        val chapters = mutableListOf<String>()
        var total = 0
        val charset = book.fileCharset()
        val blank = 0x0a.toByte()
        val bufferSize = 512000
        runCatching {
            LocalBook.getBookInputStream(book).use { bis ->
                val buffer = ByteArray(bufferSize)
                var bufferStart = 3
                bis.read(buffer, 0, 3)
                if (Utf8BomUtils.hasBom(buffer)) {
                    bufferStart = 0
                }
                var length: Int
                while (bis.read(buffer, bufferStart, bufferSize - bufferStart).also { length = it } > 0) {
                    coroutineContext.ensureActive()
                    var end = bufferStart + length
                    if (end == bufferSize) {
                        for (i in bufferStart + length - 1 downTo (bufferStart + length - 4096).coerceAtLeast(0)) {
                            if (buffer[i] == blank) {
                                end = i
                                break
                            }
                        }
                    }
                    val blockContent = String(buffer, 0, end, charset)
                    buffer.copyInto(buffer, 0, end, bufferStart + length)
                    bufferStart = bufferStart + length - end
                    val matcher = pattern.matcher(blockContent)
                    while (matcher.find()) {
                        total++
                        if (chapters.size < 200) {
                            chapters.add(matcher.group())
                        }
                    }
                }
            }
        }
        return chapters to total
    }

    // ===================== TXT 规则编辑/保存 =====================

    private fun saveRuleAndRefresh(updatedRule: TxtTocRule) {
        if (updatedRule.name.isBlank() || updatedRule.rule.isBlank()) {
            _effects.tryEmit(TocRulePreviewEffect.ShowToast(context.getString(R.string.cannot_empty)))
            _uiState.update { it.copy(editingRule = null) }
            return
        }
        if (runCatching { updatedRule.rule.toPattern(Pattern.MULTILINE) }.isFailure) {
            _effects.tryEmit(TocRulePreviewEffect.ShowToast(context.getString(R.string.invalid_format)))
            _uiState.update { it.copy(editingRule = null) }
            return
        }
        val existing = runCatching { appDb.txtTocRuleDao.get(updatedRule.id) }.getOrNull()
        if (existing != null) {
            appDb.txtTocRuleDao.update(updatedRule)
        } else {
            appDb.txtTocRuleDao.insert(updatedRule)
        }
        _uiState.update { it.copy(editingRule = null) }
        val book = this.book
        if (book != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val refreshed = computeTxtRulePreview(book, updatedRule)
                _uiState.update { state ->
                    val newRules = state.txtRules.map { existingItem ->
                        if (existingItem.rule.id == updatedRule.id) refreshed else existingItem
                    }.toImmutableList()
                    state.copy(txtRules = newRules)
                }
            }
        }
    }

    private fun getAllTxtRules(): List<TxtTocRule> {
        var rules = appDb.txtTocRuleDao.enabled
        if (appDb.txtTocRuleDao.count == 0) {
            rules = DefaultData.txtTocRules.apply {
                appDb.txtTocRuleDao.insert(*this.toTypedArray())
            }.filter { it.enable }
        }
        return rules.sortedBy { it.serialNumber }
    }
}
