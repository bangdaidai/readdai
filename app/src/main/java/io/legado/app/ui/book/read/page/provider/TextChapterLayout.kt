package io.legado.app.ui.book.read.page.provider

import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.highlight.HighlightRuleStore
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider.reviewStr
import io.legado.app.ui.book.read.page.provider.ChapterProvider.srcReplaceStr
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fastSum
import io.legado.app.utils.getTextWidthsCompat
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.textHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.LinkedList
import kotlin.math.roundToInt
import splitties.init.appCtx

/**
 * Created by GKF on 2018/1/30.
 * 文本段排版
 */
class TextChapterLayout(
    scope: CoroutineScope,
    private val textChapter: TextChapter,
    private val textPages: ArrayList<TextPage>,
    private val book: Book,
    private val bookContent: BookContent,
) {

    private val contentPaint: TextPaint = ChapterProvider.contentPaint
    private val stringBuilder = StringBuilder()

    private val paddingLeft = ChapterProvider.paddingLeft
    private val paddingRight = ChapterProvider.paddingRight
    private val paddingTop = ChapterProvider.paddingTop
    private val paragraphSpacing = ChapterProvider.paragraphSpacing
    private val visibleWidth = ChapterProvider.visibleWidth
    private val visibleHeight = ChapterProvider.visibleHeight
    private val viewWidth = ChapterProvider.viewWidth
    private val doublePage = ChapterProvider.doublePage
    private val useZhLayout = ReadBookConfig.useZhLayout
    private val fontMetrics = contentPaint.fontMetrics
    private val reviewCharWidth by lazy { contentPaint.measureText(srcReplaceStr) * 1.5556f }

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

    @Volatile
    private var listener: LayoutProgressListener? = textChapter

    private val paragraphIndent = ReadBookConfig.paragraphIndent
    private val lineSpacingExtra: Float
        get() = ReadBookConfig.lineSpacingExtra / 10f
    private val isMiddleTitle: Boolean
        get() = ReadBookConfig.titleMode == 1
    private val isRightTitle: Boolean
        get() = ReadBookConfig.titleMode == 2
    private val titleTopSpacing: Int
        get() = ReadBookConfig.titleTopSpacing.dpToPx()
    private val textFullJustify get() = ReadBookConfig.textFullJustify

    private val pageAnim = book.getPageAnim()

    private val compiledHighlightRules by lazy {
        HighlightRuleStore.loadEnabled(appCtx).mapNotNull { rule ->
            kotlin.runCatching {
                CompiledHighlightRule(
                    rule = rule,
                    regex = Regex(rule.pattern)
                )
            }.getOrNull()
        }
    }

    private var pendingTextPage = TextPage()

    private fun onException(e: Throwable) {
        listener?.onLayoutException(e)
        listener = null
    }

    private fun onMeasureStart(size: Int) {
        // 排版开始回调
    }

    private fun onMeasureComplete(pages: ArrayList<TextPage>) {
        listener?.onLayoutCompleted()
        listener = null
    }

    init {
        job = Coroutine.async(
            scope,
            start = CoroutineStart.LAZY,
            executeContext = IO
        ) {
            launch {
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin) ?: return@launch
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
        textPage.chapterIndex = bookChapter.index
        textPage.chapterSize = chaptersSize
        textPage.title = displayTitle
        textPage.doublePage = doublePage
        textPage.paddingTop = paddingTop
        textPage.isCompleted = true
        textPage.textChapter = textChapter
        textPage.upLinesPosition()
        textPage.upRenderHeight()
        textPages.add(textPage)
        val msg = channel.trySend(textPage)
        if (!msg.isSuccess) {
            onException(msg.exceptionOrNull()!!)
            throw msg.exceptionOrNull()!!
        }
        try {
            listener?.onLayoutPageCompleted(textPages.lastIndex, textPage)
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("调用布局进度监听回调出错\n${e.localizedMessage}", e)
        }
    }

    private fun onCompleted() {
        channel.close()
        try {
            listener?.onLayoutCompleted()
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("调用布局进度监听回调出错\n${e.localizedMessage}", e)
        } finally {
            listener = null
        }
    }

    /**
     * 获取拆分完的章节数据
     */
    private suspend fun getTextChapter(
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
    ) {
        val contents = bookContent.textList
        val imageStyle = book.getImageStyle()
        val textHeight = ChapterProvider.contentPaintTextHeight
        stringBuilder.clear()
        pendingTextPage = TextPage()
        durY = 0f
        textPaintInit(contentPaint)

        // 标题排版
        if (isMiddleTitle || isRightTitle || bookChapter.isVolume || contents.isEmpty()) {
            var firstLine = true
            displayTitle.splitNotBlank("\n").forEach { titleText ->
                val srcList = LinkedList<String>()
                val clickList = LinkedList<String?>()
                val titleImg = if (firstLine) {
                    firstLine = false
                    bookChapter.imgUrl
                } else {
                    null
                }
                val imgText = if (titleImg.isNullOrEmpty()) {
                    null
                } else {
                    srcList.add(titleImg)
                    clickList.add(null)
                    srcReplaceStr
                }
                setTypeText(
                    book,
                    if (imgText != null) titleText + imgText else titleText,
                    ChapterProvider.titlePaint,
                    ChapterProvider.titlePaintTextHeight,
                    ChapterProvider.titlePaintFontMetrics,
                    imageStyle,
                    srcList = srcList,
                    clickList = clickList,
                    isTitle = true,
                    emptyContent = contents.isEmpty(),
                    isVolumeTitle = bookChapter.isVolume
                )
                pendingTextPage.lines.lastOrNull()?.isParagraphEnd = true
                stringBuilder.append("\n")
            }
            durY += titleTopSpacing.dpToPx()
        }

        contents.forEach { content ->
            currentCoroutineContext().ensureActive()
            if (content.isEmpty()) {
                setTypeEmptyText(
                    book, textPages, textHeight,
                    imageStyle, false, false
                )
                return@forEach
            }
            if (content.contains("<img", true)) {
                setTypeHtml(imageStyle, book, content)
                return@forEach
            }
            setTypeText(
                book, content, contentPaint, textHeight, contentPaint.fontMetrics,
                imageStyle, false,
                content.length < 4,
                bookChapter.isVolume,
                false,
                null
            )
            pendingTextPage.lines.lastOrNull()?.isParagraphEnd = true
            stringBuilder.append("\n")
        }

        val textPage = pendingTextPage
        val endPadding = 20.dpToPx()
        val durYPadding = durY + endPadding
        if (textPage.height < durYPadding) {
            textPage.height = durYPadding
        } else {
            textPage.height += endPadding
        }
        textPage.text = stringBuilder.toString()
        onPageCompleted()
        pendingTextPage = TextPage()
        stringBuilder.clear()
        durY = 0f
        absStartX = paddingLeft
        onCompleted()
    }

    private fun textPaintInit(textPaint: TextPaint) {
        textPaint.typeface = ChapterProvider.typeface
        textPaint.letterSpacing = ReadBookConfig.letterSpacing
        textPaint.isAntiAlias = true
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
    ) {
        val textLine = TextLine(isTitle = isTitle)
        textLine.isParagraphEnd = true
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

    @Suppress("DEPRECATION")
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
        srcList: LinkedList<String>? = null,
        clickList: LinkedList<String?>? = null
    ) {
        val styledText = applyHighlightRules(SpannableStringBuilder(text), isTitle)
        val widthsArray = allocateFloatArray(text.length)
        textPaint.getTextWidthsCompat(text, widthsArray, reviewCharWidth)
        val layout = if (useZhLayout) {
            val (words, widths) = measureTextSplit(text, widthsArray)
            val indentSize = if (isFirstLine) paragraphIndent.length else 0
            ZhLayout(styledText, textPaint, visibleWidth, words, widths, indentSize)
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
        val indentCharWidth = ChapterProvider.indentCharWidth
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
                book,
                absStartX,
                textLine,
                char,
                x,
                x1,
                index + 1 == words.size,
                srcList,
                clickList,
                styledText,
                lineStart + index
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
        val spanned = styledText as? Spanned
        val textColor = spanned?.let {
            val spans = it.getSpans(textIndex, textIndex + 1, ForegroundColorSpan::class.java)
            spans.firstOrNull()?.foregroundColor
        }
        val highlightStyle = spanned?.let { extractHighlightStyle(it, textIndex) }
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
                    highlightColor = textColor,
                    highlightStyle = highlightStyle
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
            val clusterBaseIndex = i++
            widths.add(widthsArray[start + clusterBaseIndex])
            while (i < length && widthsArray[start + i] == 0f && !isZeroWidthChar(text[i])) {
                i++
            }
            stringList.add(text.substring(clusterBaseIndex, i))
        }
        return stringList to widths
    }

    private fun isZeroWidthChar(char: Char): Boolean {
        val code = char.code
        return code == 8203 || code == 8204 || code == 8205 || code == 8288
    }

    /**
     * 应用高亮规则到文本
     */
    private fun applyHighlightRules(
        spannable: SpannableStringBuilder,
        isTitle: Boolean = false
    ): SpannableStringBuilder {
        compiledHighlightRules.forEach { compiled ->
            if (!compiled.rule.appliesTo(isTitle)) return@forEach
            applyRuleSpans(spannable, compiled.rule, compiled.regex)
        }
        return spannable
    }

    /**
     * 应用规则到Spannable
     */
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

    /**
     * 提取高亮样式
     */
    private fun extractHighlightStyle(spanned: Spanned, index: Int): HighlightStyleSpan? {
        val spans = spanned.getSpans(
            index,
            index + 1,
            HighlightStyleSpan::class.java
        ) ?: return null
        if (spans.isEmpty()) return null
        var underlineMode = 0
        var underlineColor = 0xFF63C37D.toInt()
        var underlineWidth = 1f
        var underlineOffset = 2f
        var underlineSvgPath = ""
        var bgImage = ""
        var bgImageFit = 0
        var bgImageScale = 1f
        var hasUnderline = false
        var hasBgImage = false
        spans.forEach { span ->
            if (span.underlineMode != 0) {
                underlineMode = span.underlineMode
                underlineColor = span.underlineColor
                underlineWidth = span.underlineWidth
                underlineOffset = span.underlineOffset
                underlineSvgPath = span.underlineSvgPath
                hasUnderline = true
            }
            if (span.bgImage.isNotEmpty()) {
                bgImage = span.bgImage
                bgImageFit = span.bgImageFit
                bgImageScale = span.bgImageScale
                hasBgImage = true
            }
        }
        if (!hasUnderline && !hasBgImage) return null
        return HighlightStyleSpan(
            underlineMode = if (hasUnderline) underlineMode else 0,
            underlineColor = underlineColor,
            underlineWidth = underlineWidth,
            underlineOffset = underlineOffset,
            underlineSvgPath = if (hasUnderline) underlineSvgPath else "",
            bgImage = if (hasBgImage) bgImage else "",
            bgImageFit = if (hasBgImage) bgImageFit else 0,
            bgImageScale = if (hasBgImage) bgImageScale else 1f,
        )
    }

    private data class CompiledHighlightRule(
        val rule: io.legado.app.data.entities.HighlightRule,
        val regex: Regex,
    )

    private fun io.legado.app.data.entities.HighlightRule.appliesTo(isTitle: Boolean): Boolean {
        return when (targetScope) {
            io.legado.app.data.entities.HighlightRule.TARGET_TITLE -> isTitle
            io.legado.app.data.entities.HighlightRule.TARGET_BODY -> !isTitle
            else -> true
        }
    }
}
