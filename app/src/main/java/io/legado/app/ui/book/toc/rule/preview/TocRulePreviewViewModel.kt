package io.legado.app.ui.book.toc.rule.preview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DefaultData
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocalTxt
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
    private var networkCountJob: Job? = null

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
            is TocRulePreviewIntent.EditNetworkRule -> {
                _effects.tryEmit(TocRulePreviewEffect.OpenReplaceRuleEditor(intent.ruleId))
            }
            is TocRulePreviewIntent.Refresh -> {
                val currentBook = book
                if (currentBook != null) {
                    if (currentBook.isLocalTxt) {
                        loadTxtPreview(currentBook)
                    } else {
                        // 编辑替换规则后，ContentProcessor 可能仍是旧缓存，先刷新再重新统计
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching { ContentProcessor.upReplaceRules() }
                            loadNetworkPreview(currentBook)
                        }
                    }
                }
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

        // 先立即展示界面（卡片显示“统计中”），命中数在后台逐步计算，
        // 避免像之前那样把所有 getDisplayTitle 同步算完才肯打开页面。
        val initialItems = titleRules.map { rule ->
            NetworkRulePreviewItem(
                rule = rule,
                matchCount = 0,
                totalChapter = chapters.size,
                computed = false,
            )
        }
        _uiState.update {
            it.copy(
                loading = false,
                useReplace = useReplace,
                chapterTotal = chapters.size,
                titleReplaceRules = titleRules.toImmutableList(),
                networkRuleItems = initialItems.toImmutableList(),
            )
        }
        computeNetworkCounts(chapters, titleRules)
    }

    /**
     * 后台统计每条标题替换规则的命中情况。
     *
     * 旧实现：对每条规则都调用 getDisplayTitle 前缀子列表，时间复杂度约 O(N²·M)，
     * 且 getDisplayTitle 内部走带超时的正则替换（runBlocking + 协程），章节多时极慢。
     *
     * 新实现：每条章节只顺序应用一次规则链（O(N·M)），并直接复用已编译的 java 正则，
     * 不再走 getDisplayTitle，避免 runBlocking 与“超时重启 App”的风险。
     * 同时逐条规则回写状态，界面上能看到卡片一张张从“统计中”变为命中数。
     */
    private fun computeNetworkCounts(
        chapters: List<BookChapter>,
        titleRules: List<ReplaceRule>,
    ) {
        networkCountJob?.cancel()
        networkCountJob = viewModelScope.launch(Dispatchers.IO) {
            if (titleRules.isEmpty()) return@launch
            // 预编译每条规则的正则（仅正则模式）
            val compiled = titleRules.map { rule ->
                if (rule.isRegex && rule.pattern.isNotEmpty()) {
                    runCatching { Pattern.compile(rule.pattern) }.getOrNull()
                } else {
                    null
                }
            }
            // 每条章节的“当前标题”，随规则链推进而更新
            val current = Array(chapters.size) { i ->
                chapters[i].title.replace(AppPattern.rnRegex, "")
            }
            val matchCounts = IntArray(titleRules.size)
            val samples = Array(titleRules.size) { mutableListOf<Pair<String, String>>() }

            titleRules.forEachIndexed { index, rule ->
                ensureActive()
                val pattern = compiled[index]
                for (i in chapters.indices) {
                    val before = current[i]
                    val after = applyRuleToTitle(rule, pattern, before)
                    if (after != before) {
                        matchCounts[index]++
                        if (samples[index].size < 200) {
                            samples[index].add(before to after)
                        }
                    }
                    current[i] = after
                }
                val example = samples[index].firstOrNull()?.let { (b, a) -> "$b → $a" }
                val itemIndex = index
                _uiState.update { state ->
                    val newItems = state.networkRuleItems.mapIndexed { i, item ->
                        if (i == itemIndex) {
                            item.copy(
                                matchCount = matchCounts[itemIndex],
                                chapters = samples[itemIndex].toImmutableList(),
                                example = example,
                                computed = true,
                            )
                        } else {
                            item
                        }
                    }.toImmutableList()
                    state.copy(networkRuleItems = newItems)
                }
            }
        }
    }

    /**
     * 把单条替换规则应用到标题上。等价于 getDisplayTitle 中“叠加该条规则”的效果，
     * 但使用已编译的 java.util.regex.Pattern，避免 getDisplayTitle 内带超时的替换扩展
     * （其内部 runBlocking 且超时可能重启 App），从而让预览统计既快又安全。
     */
    private fun applyRuleToTitle(
        rule: ReplaceRule,
        pattern: Pattern?,
        input: String,
    ): String {
        if (rule.pattern.isEmpty()) return input
        // @js: 形式的替换依赖 Rhino 引擎，预览统计里不执行，按“未变化”处理，避免产生错误计数
        if (rule.replacement.startsWith("@js:")) return input
        val result = if (rule.isRegex && pattern != null) {
            try {
                val matcher = pattern.matcher(input)
                val sb = StringBuffer()
                while (matcher.find()) {
                    matcher.appendReplacement(sb, rule.replacement)
                }
                matcher.appendTail(sb)
                sb.toString()
            } catch (_: Exception) {
                input
            }
        } else {
            // 非正则：与 getDisplayTitle 一致，按字面量整体替换
            input.replace(rule.pattern, rule.replacement)
        }
        // 与 getDisplayTitle 一致：替换后为空则保留原标题
        return if (result.isBlank()) input else result
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
