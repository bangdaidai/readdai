package io.legado.app.ui.book.read.page.provider

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.highlight.HighlightRuleStore
import io.legado.app.lib.core.Coroutine
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.read.ReadBook
import io.legado.app.ui.book.read.ReadMendBottomSheet
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider.reviewChar
import io.legado.app.ui.book.read.page.provider.ChapterProvider.reviewStr
import io.legado.app.ui.book.read.page.provider.ChapterProvider.srcReplaceStr
import io.legado.app.ui.book.read.page.provider.ImageProvider.isGif
import io.legado.app.utils.fastSum
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import splitties.init.appCtx
import java.util.LinkedList

/**
 * Created by GKF on 2018/1/30.
 * 文本段排版
 */
class TextChapterLayout(
    private val book: Book,
    textChapter: TextChapter,
    bookContent: String,
    scope: CoroutineScope,
) : BaseLayout(book, textChapter, bookContent, scope) {

    companion object {
        private val compiledHighlightRules by lazy {
            HighlightRuleStore.loadEnabled(appCtx).mapNotNull { rule ->
                kotlin.runCatching {
                    object {
                        val rule = rule
                        val regex = Regex(rule.pattern)
                    }
                }.getOrNull()
            }
        }

        fun invalidateCompiledHighlightRulesCache() {
            // trigger re-initialization
        }
    }

    private val paragraphIndent = if (ReadBookConfig.config.paragraphIndent.isBlank()) {
        ChapterProvider.indentCharFull
    } else {
        ReadBookConfig.config.paragraphIndent
    }
    private val indentCharWidth by lazy {
        contentPaint.measureText(ChapterProvider.indentCharFull)
    }

    private val lineSpacingExtra: Float
        get() = ReadBookConfig.config.lineSpacingExtra / 10f

    private val isMiddleTitle: Boolean
        get() = ReadBookConfig.config.titleMode == 1

    private val isRightTitle: Boolean
        get() = ReadBookConfig.config.titleMode == 2

    private val titleTopSpacing: Int
        get() = ReadBookConfig.config.titleBottomSpacing

    private val textFullJustify get() = ReadBookConfig.textFullJustify

    private val pageAnim = book.getPageAnim()

    private fun applyRuleSpans(
        spannable: SpannableStringBuilder,
        rule: io.legado.app.data.entities.HighlightRule,
        regex: Regex
    ) {
        regex.findAll(spannable).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (start >= end) return@forEach
            rule.textColor?.let { color ->
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (rule.underlineMode != 0 || !rule.bgImage.isNullOrBlank()) {
                spannable.setSpan(
                    HighlightStyleSpan(
                        underlineMode = rule.underlineMode,
                        underlineColor = rule.underlineColor ?: rule.textColor ?: 0xFF63C37D.toInt(),
                        underlineWidth = rule.underlineWidth,
                        underlineOffset = rule.underlineOffset,
                        underlineSvgPath = rule.underlineSvgPath.orEmpty(),
                        bgImage = rule.bgImage.orEmpty(),
                        bgImageFit = rule.bgImageFit,
                        bgImageScale = rule.bgImageScale
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun applyHighlightRules(
        spannable: SpannableStringBuilder,
        isTitle: Boolean = false
    ): SpannableStringBuilder {
        if (!book.getUseHighlightRule()) return spannable
        
        compiledHighlightRules.forEach { compiled ->
            if (!compiled.rule.appliesTo(isTitle)) return@forEach
            kotlin.runCatching {
                applyRuleSpans(spannable, compiled.rule, compiled.regex)
            }.onFailure {
                AppLog.put("高亮规则应用失败: ${compiled.rule.name}, 错误: ${it.localizedMessage}")
            }
        }
        return spannable
    }

    private fun extractTextColor(spanned: Spanned, textIndex: Int): Int? {
        val spans = spanned.getSpans(textIndex, textIndex + 1, ForegroundColorSpan::class.java)
        return spans.firstOrNull()?.color
    }

    private fun extractHighlightStyle(spanned: Spanned, textIndex: Int): HighlightStyleSpan? {
        val spans = spanned.getSpans(textIndex, textIndex + 1, HighlightStyleSpan::class.java)
        return spans.firstOrNull()
    }

    private var pendingTextPage = TextPage()

    private val bookChapter inline get() = textChapter.chapter
    private val displayTitle inline get() = textChapter.title
    private val chaptersSize inline get() = textChapter.chaptersSize

    private var durY = 0f
    private var absStartX = paddingLeft
    private var floatArray = FloatArray(128)

    private var isCompleted = false
    private val job: Coroutine<*>

    var exception: Throwable? = null

    var channel = Channel<TextPage>(Channel.UNLIMITED)


    init {
        job = Coroutine.async(
            scope,
            start = CoroutineStart.LAZY,
            executeContext = IO
        ) {
            launch {
                val bookSource = book.getBookSource() ?: return@launch
                BookHelp.saveImages(bookSource, book, bookChapter, bookContent.toString())
            }
            getTextChapter(book, bookChapter, displayTitle, bookContent)
        }.onError {
            exception = it
            onException(it)
        }.onCancel {
            channel.cancel()
        }.onFinally {
            isCompleted = true
        }
        job.start()
    }



    fun cancel() {
        job.cancel()
        listener = null
    }

    private fun onPageCompleted() {
        val textPage = pendingTextPage
        textPage.index = textPages.size
        textPages.add(textPage)
        val msg = channel.trySend(textPage)
        if (!msg.isSuccess) {
            onException(msg.exceptionOrNull())
            throw msg.exceptionOrNull()!!
        }
    }

    private suspend fun getTextChapter(
        book: Book,
        chapter: BookChapter,
        chapterTitle: String,
        bookContent: String,
    ) {
        val textPages = ArrayList<TextPage>()
        durY = 0f
        stringBuilder.clear()
        pendingTextPage = TextPage()
        textPaintInit(contentPaint)
        val imageStyle = book.getImageStyle()
        val textHeight = contentPaint.textHeight(ReadBookConfig.config.lineSpacingMultiplier)
        contentPaint.fontMetrics = fontMetrics
        val paragraphs = bookContent.splitNotBlank("\n")
        onMeasureStart(textPages.size)
        val srcList = LinkedList<String>()
        val clickList = LinkedList<String?>()
        for (index in paragraphs.indices) {
            val paragraph0 = paragraphs[index]
            srcList.clear()
            clickList.clear()
            val paragraph = ChapterProvider.getDisplayContents(
                paragraph0, srcList, clickList, chapter.getUseReplaceRule(), chapter.url
            )
            val paragraphText = paragraph.text
            val isTitle = paragraph.isTitle || chapter.isVolume && index == 0
            val text = if (isTitle && chapter.isVolume) {
                chapterTitle + paragraphText
            } else {
                paragraphText
            }
            currentCoroutineContext().ensureActive()
            if (text.isEmpty()) {
                setTypeEmptyText(
                    book, textPages, textHeight,
                    imageStyle, isTitle, index == 0,
                    chapter.isVolume
                )
                continue
            }
            if (chapter.getUseImageReplace() && text.contains("<img", true)) {
                setTypeHtml(imageStyle, book, text)
                continue
            }
            setTypeText(
                book, text, contentPaint, textHeight, contentPaint.fontMetrics,
                imageStyle, isTitle,
                index == 0, text.length < 4,
                chapter.isVolume,
                srcList,
                clickList
            )
            if (paragraph.isImage) {
                textPages.lastOrNull()?.apply {
                    addImageLine(textHeight * 0.7f, text)
                    calcTextLinePosition(textPages, this.lines.last(), stringBuilder.length)
                    stringBuilder.append(text)
                }
            }
            if (chapter.isVolume && index == 0) {
                listener?.onChapterTitleUpdate(text)
            }
        }
        //最后一页
        val textPage = pendingTextPage
        textPage.text = stringBuilder.toString()
        textPages.add(textPage)
        textPage.index = textPages.size
        channel.trySend(textPage)
        //计算页长度
        textPages.forEach {
            textChapter.addPage(it)
        }
        //更新字数统计
        ReadMendBottomSheet.updateWordCount()
        onMeasureComplete(textPages)
        EventBus.mPost(EventBus.UP_CONTENT_VIEW)
    }

    private fun textPaintInit(textPaint: TextPaint) {
        textPaint.typeface = getTypeface(ReadBookConfig.config.fontName)
        textPaint.letterSpacing = ReadBookConfig.config.letterSpacing
        textPaint.isAntiAlias = true
    }

    private fun getTypeface(typefaceName: String?): Typeface? {
        return ReadBookConfig.getTypeface(typefaceName)
    }

    /**
     * 排版空段落
     */
    private suspend fun setTypeEmptyText(
        book: Book,
        textPages: ArrayList<TextPage>,
        textHeight: Float,
        imageStyle: String?,
        isTitle: Boolean,
        isFirstLine: Boolean,
        isVolume: Boolean,
    ) {
        val textLine = TextLine(isTitle = isTitle)
        textLine.isParagraphEnd = true
        textLine.isVolumeEnd = isVolume
        prepareNextPageIfNeed(durY + textHeight)
        textLine.upTopBottom(durY, textHeight, contentPaint.fontMetrics)
        calcTextLinePosition(textPages, textLine, stringBuilder.length)
        stringBuilder.append("\n")
        val textPage = pendingTextPage
        textPage.addLine(textLine)
        durY += textHeight * lineSpacingExtra
        durY += textHeight * paragraphSpacing / 10f
    }

    /**
     * 排版html样式
     */
    private suspend fun setTypeHtml(
        imageStyle: String?,
        book: Book,
        htmlContent: String,
    ) {
        // TODO: 实现HTML样式排版
        val textLine = TextLine(isTitle = false)
        textLine.isParagraphEnd = true
        prepareNextPageIfNeed(durY + 100f)
        textLine.upTopBottom(durY, 100f, contentPaint.fontMetrics)
        stringBuilder.append(htmlContent)
        pendingTextPage.addLine(textLine)
    }

    private suspend fun setTypeText(
        book: Book,
        text: String,
        textPaint: TextPaint,
        textHeight: Float,
        fontMetrics: Paint.FontMetrics,
        imageStyle: String?,
        isTitle: Boolean = false,
        isFirstLine: Boolean = true,
        emptyContent: Boolean = false,
        isVolumeTitle: Boolean = false,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?
    ) {
        val styledText = applyHighlightRules(SpannableStringBuilder(text), isTitle)
        val widthsArray = allocateFloatArray(text.length)
        textPaint.getTextWidthsCompat(text, widthsArray, reviewCharWidth)
        val layout = if (useZhLayout) {
            val (words, widths) = measureTextSplit(text, widthsArray)
            val indentSize = if (isFirstLine) paragraphIndent.length else 0
            ZhLayout(text, textPaint, visibleWidth, words, widths, indentSize)
        } else {
            StaticLayout(styledText, textPaint, visibleWidth, Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true)
        }
        durY = when {
            emptyContent && textPages.isEmpty() -> {
                val textPage = pendingTextPage
                if (textPage.lineSize == 0) {
                    val ty = (visibleHeight - layout.lineCount * textHeight) / 2
                    if (ty > titleTopSpacing) ty else titleTopSpacing.toFloat()
                } else {
                    var textLayoutHeight = layout.lineCount * textHeight
                    val fistLine = textPage.getLine(0)
                    if (fistLine.lineTop < textLayoutHeight + titleTopSpacing) {
                        textLayoutHeight = fistLine.lineTop - titleTopSpacing
                    }
                    textPage.lines.forEach {
                        it.lineTop -= textLayoutHeight
                        it.lineBase -= textLayoutHeight
                        it.lineBottom -= textLayoutHeight
                    }
                    durY - textLayoutHeight
                }
            }

            isTitle && textPages.isEmpty() && pendingTextPage.lines.isEmpty() -> {
                when (imageStyle?.uppercase()) {
                    Book.imgStyleSingle -> {
                        val ty = (visibleHeight - layout.lineCount * textHeight) / 2
                        if (ty > titleTopSpacing) ty else titleTopSpacing.toFloat()
                    }

                    else -> durY + titleTopSpacing
                }
            }

            else -> durY
        }
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine(isTitle = isTitle)
            prepareNextPageIfNeed(durY + textHeight)
            val lineStart = layout.getLineStart(lineIndex)
            val lineEnd = layout.getLineEnd(lineIndex)
            val lineText = text.substring(lineStart, lineEnd)
            val (words, widths) = measureTextSplit(lineText, widthsArray, lineStart)
            val desiredWidth = widths.fastSum()
            textLine.text = lineText
            when (lineIndex) {
                0 if layout.lineCount > 1 && !isTitle && isFirstLine -> {
                    addCharsToLineFirst(
                        book, absStartX, textLine, words, textPaint,
                        desiredWidth, widths, srcList, clickList, styledText, lineStart
                    )
                }
                layout.lineCount - 1 -> {
                    val startX = if (
                        isTitle &&
                        (isMiddleTitle || emptyContent || isVolumeTitle
                                || imageStyle?.uppercase() == Book.imgStyleSingle)
                    ) {
                        (visibleWidth - desiredWidth) / 2
                    } else {
                        0f
                    }
                    addCharsToLineNatural(
                        book, absStartX, textLine, words,
                        startX, !isTitle && lineIndex == 0, widths, srcList, clickList, styledText, lineStart
                    )
                }
                else -> {
                    if (
                        isTitle &&
                        (isMiddleTitle || emptyContent || isVolumeTitle
                                || imageStyle?.uppercase() == Book.imgStyleSingle)
                    ) {
                        val startX = (visibleWidth - desiredWidth) / 2
                        addCharsToLineNatural(
                            book, absStartX, textLine, words,
                            startX, false, widths, srcList, clickList, styledText, lineStart
                        )
                    } else {
                        addCharsToLineMiddle(
                            book, absStartX, textLine, words, textPaint,
                            desiredWidth, 0f, widths, srcList, clickList, styledText, lineStart
                        )
                    }
                }
            }
            if (doublePage) {
                textLine.isLeftLine = absStartX < viewWidth / 2
            }
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(lineText)
            textLine.upTopBottom(durY, textHeight, fontMetrics)
            val textPage = pendingTextPage
            textPage.addLine(textLine)
            durY += textHeight * lineSpacingExtra
            if (textPage.height < durY) {
                textPage.height = durY
            }
        }
        durY += textHeight * paragraphSpacing / 10f
    }

    private fun calcTextLinePosition(
        textPages: ArrayList<TextPage>,
        textLine: TextLine,
        sbLength: Int
    ) {
        val lastLine = pendingTextPage.lines.lastOrNull { it.paragraphNum > 0 }
            ?: textPages.lastOrNull()?.lines?.lastOrNull { it.paragraphNum > 0 }
        val paragraphNum = when {
            lastLine == null -> 1
            lastLine.isParagraphEnd -> lastLine.paragraphNum + 1
            else -> lastLine.paragraphNum
        }
        textLine.paragraphNum = paragraphNum
        textLine.chapterPosition =
            (textPages.lastOrNull()?.lines?.lastOrNull()?.run {
                chapterPosition + charSize + if (isParagraphEnd) 1 else 0
            } ?: 0) + sbLength
        textLine.pagePosition = sbLength
    }

    private suspend fun addCharsToLineFirst(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        styledText: CharSequence,
        lineStart: Int,
    ) {
        var x = 0f
        if (!textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                x, true, textWidths, srcList, clickList, styledText, lineStart
            )
            return
        }
        val bodyIndent = paragraphIndent
        repeat(bodyIndent.length) {
            val x1 = x + indentCharWidth
            textLine.addColumn(
                TextColumn(
                    charData = ChapterProvider.indentChar,
                    start = absStartX + x,
                    end = absStartX + x1
                )
            )
            x = x1
            textLine.indentWidth = x
        }
        textLine.indentSize = bodyIndent.length
        if (words.size > bodyIndent.length) {
            val text1 = words.subList(bodyIndent.length, words.size)
            val textWidths1 = textWidths.subList(bodyIndent.length, textWidths.size)
            addCharsToLineMiddle(
                book, absStartX, textLine, text1, textPaint,
                desiredWidth, x, textWidths1, srcList, clickList, styledText, lineStart + bodyIndent.length
            )
        }
    }

    private suspend fun addCharsToLineMiddle(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        startX: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        styledText: CharSequence,
        lineStart: Int,
    ) {
        if (!textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                startX, false, textWidths, srcList, clickList, styledText, lineStart
            )
            return
        }
        val residualWidth = visibleWidth - desiredWidth
        val spaceSize = words.count { it == " " }
        textLine.startX = absStartX + startX
        if (spaceSize > 1) {
            val d = residualWidth / spaceSize
            textLine.wordSpacing = d
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (char == " ") {
                    if (index != words.lastIndex) (x + cw + d) else (x + cw)
                } else {
                    (x + cw)
                }
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList,
                    clickList, styledText, lineStart + index
                )
                x = x1
            }
        } else {
            val gapCount: Int = words.lastIndex
            val d = if (gapCount > 0) residualWidth / gapCount else 0f
            textLine.extraLetterSpacingOffsetX = -d / 2
            textLine.extraLetterSpacing = d / textPaint.textSize
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (index != words.lastIndex) (x + cw + d) else (x + cw)
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList,
                    clickList, styledText, lineStart + index
                )
                x = x1
            }
        }
        exceed(absStartX, textLine, words)
    }

    private suspend fun addCharsToLineNatural(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        startX: Float,
        hasIndent: Boolean,
        textWidths: List<Float>,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        styledText: CharSequence,
        lineStart: Int,
    ) {
        val indentLength = paragraphIndent.length
        var x = startX
        textLine.startX = absStartX + startX
        for (index in words.indices) {
            val char = words[index]
            val cw = textWidths[index]
            val x1 = x + cw
            addCharToLine(
                book, absStartX, textLine, char, x, x1, index + 1 == words.size,
                srcList, clickList, styledText, lineStart + index
            )
            x = x1
            if (hasIndent && index == indentLength - 1) {
                textLine.indentWidth = x
            }
        }
        exceed(absStartX, textLine, words)
    }

    private suspend fun addCharToLine(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        char: String,
        xStart: Float,
        xEnd: Float,
        isLineEnd: Boolean,
        srcList: LinkedList<String>?,
        clickList: LinkedList<String?>?,
        styledText: CharSequence,
        textIndex: Int,
    ) {
        val textColor = extractTextColor(styledText as Spanned, textIndex)
        val highlightStyle = extractHighlightStyle(styledText, textIndex)
        val underlineMode = highlightStyle?.underlineMode ?: 0
        val underlineColor = highlightStyle?.underlineColor
        val underlineWidth = highlightStyle?.underlineWidth ?: 1f
        val underlineOffset = highlightStyle?.underlineOffset ?: 2f
        val underlineSvgPath = highlightStyle?.underlineSvgPath ?: ""
        val bgImage = highlightStyle?.bgImage ?: ""
        val bgImageFit = highlightStyle?.bgImageFit ?: 0
        val bgImageScale = highlightStyle?.bgImageScale ?: 1f
        
        val column = when {
            !srcList.isNullOrEmpty() && (char == srcReplaceStr || char == reviewStr) -> {
                val src = srcList.removeFirst()
                val click = clickList?.removeFirst()
                ImageProvider.cacheImage(book, src, ReadBook.bookSource)
                ImageColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    src = src,
                    click = click
                )
            }

            else -> {
                TextColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    charData = char,
                    textColor = textColor,
                    highlightColor = textColor,
                    highlightStyle = highlightStyle,
                    underlineMode = underlineMode,
                    underlineColor = underlineColor,
                    underlineWidth = underlineWidth,
                    underlineOffset = underlineOffset,
                    underlineSvgPath = underlineSvgPath,
                    bgImage = bgImage,
                    bgImageFit = bgImageFit,
                    bgImageScale = bgImageScale
                )
            }
        }
        textLine.addColumn(column)
    }

    private fun exceed(absStartX: Int, textLine: TextLine, words: List<String>) {
        var size = words.size
        if (size < 2) return
        val visibleEnd = absStartX + visibleWidth
        val columns = textLine.columns
        var offset = 0
        val endColumn = if (words.last() == " ") {
            size--
            offset++
            columns[columns.lastIndex - 1]
        } else {
            columns.last()
        }
        val endX = endColumn.end.roundToInt()
        if (endX > visibleEnd) {
            textLine.exceed = true
            val cc = (endX - visibleEnd) / size
            for (i in 0..<size) {
                textLine.getColumnReverseAt(i, offset).let {
                    val py = cc * (size - i)
                    it.start -= py
                    it.end -= py
                }
            }
        }
    }

    private suspend fun prepareNextPageIfNeed(requestHeight: Float = -1f) {
        if (requestHeight > visibleHeight || requestHeight == -1f) {
            val textPage = pendingTextPage
            if (textPage.height < durY) {
                textPage.height = durY
            }
            if (doublePage && absStartX < viewWidth / 2) {
                textPage.leftLineSize = textPage.lineSize
                absStartX = viewWidth / 2 + paddingLeft
            } else {
                if (textPage.leftLineSize == 0) {
                    textPage.leftLineSize = textPage.lineSize
                }
                textPage.text = stringBuilder.toString()
                currentCoroutineContext().ensureActive()
                onPageCompleted()
                pendingTextPage = TextPage()
                stringBuilder.clear()
                absStartX = paddingLeft
            }
            durY = 0f
        }
    }

    private fun allocateFloatArray(size: Int): FloatArray {
        if (size > floatArray.size) {
            floatArray = FloatArray(size)
        }
        return floatArray
    }

    private fun measureTextSplit(
        text: String,
        widthsArray: FloatArray,
        start: Int = 0
    ): Pair<ArrayList<String>, ArrayList<Float>> {
        val length = text.length
        var clusterCount = 0
        for (i in start..<start + length) {
            if (widthsArray[i] > 0) clusterCount++
        }
        val widths = ArrayList<Float>(clusterCount)
        val stringList = ArrayList<String>(clusterCount)
        var i = 0
        while (i < length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val clusterIndex = start + i
            var clusterWidth = widthsArray[clusterIndex]
            if (charCount == 1 && isZeroWidthChar(text[i])) {
                clusterWidth = 0f
            } else if (charCount > 1) {
                for (j in 1..<charCount) {
                    val nextIndex = start + i + j
                    clusterWidth += widthsArray.getOrNull(nextIndex) ?: 0f
                }
            }
            if (clusterWidth == 0f && !isZeroWidthChar(text[i])) {
                clusterWidth = 0f
            }
            val clusterStr = if (charCount == 1) {
                text[i].toString()
            } else {
                text.substring(i, i + charCount)
            }
            stringList.add(clusterStr)
            widths.add(clusterWidth)
            i += charCount
        }
        return stringList to widths
    }

    private fun isZeroWidthChar(char: Char): Boolean {
        val code = char.code
        return code == 8203 || code == 8204 || code == 8205 || code == 8288
    }
}
