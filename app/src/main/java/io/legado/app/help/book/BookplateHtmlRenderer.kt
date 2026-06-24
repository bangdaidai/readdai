package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.app.data.entities.BookplateData
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.help.config.DataVisibilitySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BookplateHtmlRenderer {

    private const val IMAGE_WIDTH = 750
    private const val RENDER_TIMEOUT_MS = 5000L
    private const val CSS_LAYOUT_DELAY_MS = 60L
    private const val MAX_CACHE_SIZE = 16
    private const val HEIGHT_TIMEOUT_MS = 3000L

    @Volatile
    private var cachedWebViewDeferred: CompletableDeferred<WebView>? = null

    private val bitmapCache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val shouldRemove = size > MAX_CACHE_SIZE
            if (shouldRemove && eldest != null) {
                eldest.value.recycle()
            }
            return shouldRemove
        }
    }

    private val VARIABLE_REGEX = Regex("\\{\\{(\\w+)\\}\\}")
    private val VIEWPORT_META_REGEX = Regex("""<meta\s+name=["']viewport["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val HEAD_TAG_REGEX = Regex("<head>", RegexOption.IGNORE_CASE)

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
        BookplateLogger.log("RENDER", "缓存已清空")
    }

    private fun ensureViewportMeta(html: String): String {
        if (VIEWPORT_META_REGEX.containsMatchIn(html)) {
            return VIEWPORT_META_REGEX.replace(html, """<meta name="viewport" content="width=$IMAGE_WIDTH, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">""")
        }
        return HEAD_TAG_REGEX.replaceFirst(html, "<head>\n<meta name=\"viewport\" content=\"width=$IMAGE_WIDTH, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
    }

    private fun buildVariableMap(data: BookplateData): Map<String, String> {
        return mapOf(
            "bookName" to data.bookName,
            "author" to data.author,
            "coverUrl" to data.coverUrl,
            "intro" to escapeHtml(data.intro),
            "kind" to data.kind,
            "wordCount" to data.wordCount,
            "originName" to data.originName,
            "totalChapterNum" to data.totalChapterNum.toString(),
            "latestChapterTitle" to data.latestChapterTitle,
            "typeText" to data.typeText,
            "charset" to data.charset,
            "readingStatusText" to data.readingStatusText,
            "readingProgress" to data.readingProgress,
            "readChapters" to data.readChapters,
            "unreadChapters" to data.unreadChapters.toString(),
            "readIteration" to data.readIteration.toString(),
            "readIterationText" to data.readIterationText,
            "durChapterTitle" to data.durChapterTitle,
            "totalReadTime" to data.totalReadTime,
            "totalReadHours" to data.totalReadHours.toString(),
            "totalReadMinutes" to data.totalReadMinutes.toString(),
            "readingDays" to data.readingDays.toString(),
            "maxDayReadTime" to data.maxDayReadTime,
            "maxDayReadDate" to data.maxDayReadDate,
            "totalReadWords" to data.totalReadWords,
            "remainingWords" to data.remainingWords,
            "firstReadTime" to data.firstReadTime,
            "lastReadTime" to data.lastReadTime,
            "finishReadTime" to data.finishReadTime,
            "addBookshelfTime" to data.addBookshelfTime,
            "lastCheckTime" to data.lastCheckTime,
            "lastReadTimeRelative" to data.lastReadTimeRelative,
            "rating" to data.rating.toString(),
            "ratingStars" to data.ratingStars,
            "ratingMax" to data.ratingMax.toString(),
            "reviewContent" to escapeHtml(data.reviewContent),
            "annotationCount" to data.annotationCount.toString(),
            "thoughtCount" to data.thoughtCount.toString(),
            "latestAnnotation" to escapeHtml(data.latestAnnotation),
            "latestAnnotationNote" to escapeHtml(data.latestAnnotationNote),
            "latestAnnotationChapter" to data.latestAnnotationChapter,
            "protagonists" to data.protagonists,
            "tags" to data.tags,
            "tagCount" to data.tagCount.toString(),
            "bookSourceName" to data.bookSourceName,
            "bookSourceGroup" to data.bookSourceGroup,
            "readTimeRank" to data.readTimeRank
        )
    }

    private suspend fun getWebView(context: Context): WebView {
        cachedWebViewDeferred?.let {
            val wv = it.await()
            withContext(Dispatchers.Main) {
                wv.clearHistory()
                wv.stopLoading()
                wv.layout(0, 0, IMAGE_WIDTH, 1)
            }
            return wv
        }
        val deferred = CompletableDeferred<WebView>()
        cachedWebViewDeferred = deferred
        return withContext(Dispatchers.Main) {
            val wv = WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }
                setBackgroundColor(Color.WHITE)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                setInitialScale(100)
                layout(0, 0, IMAGE_WIDTH, 1)
            }
            deferred.complete(wv)
            wv
        }
    }

    fun destroyWebView() {
        synchronized(bitmapCache) {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
        cachedWebViewDeferred?.let { deferred ->
            if (deferred.isCompleted) {
                val wv = deferred.getCompleted()
                try { wv.destroy() } catch (_: Exception) {}
            }
        }
        cachedWebViewDeferred = null
    }

    suspend fun render(
        context: Context,
        template: BookplateTemplate,
        data: BookplateData,
        settings: DataVisibilitySettings = DataVisibilitySettings
    ): Bitmap? {
        val cacheKey = "${data.bookName}_${data.author}_${template.id}_${template.htmlContent.hashCode()}"
        synchronized(bitmapCache) {
            bitmapCache[cacheKey]?.let { cached ->
                if (!cached.isRecycled) {
                    BookplateLogger.log("RENDER", "命中缓存")
                    return cached
                }
                bitmapCache.remove(cacheKey)
            }
        }

        return withContext(Dispatchers.Main) {
            BookplateLogger.log("RENDER", "开始渲染: 模板=${template.name}")
            val filteredData = applyVisibility(data, settings)
            val html = replaceVariables(template.htmlContent, filteredData)
            BookplateLogger.log("RENDER", "变量替换后HTML长度: ${html.length}")

            if (html.isBlank()) {
                BookplateLogger.log("RENDER", "HTML为空，返回null")
                return@withContext null
            }

            BookplateLogger.log("RENDER", "开始WebView离屏渲染...")
            val bitmap = renderHtml(context, ensureViewportMeta(html))
            if (bitmap != null) {
                if (isBitmapBlank(bitmap)) {
                    BookplateLogger.log("RENDER", "渲染结果全白，丢弃")
                    bitmap.recycle()
                    return@withContext null
                }
                BookplateLogger.log("RENDER", "WebView渲染成功: ${bitmap.width}x${bitmap.height}")
                synchronized(bitmapCache) {
                    bitmapCache[cacheKey] = bitmap
                }
            } else {
                BookplateLogger.log("RENDER", "WebView渲染失败")
            }
            bitmap
        }
    }

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val sampleWidth = minOf(bitmap.width, 10)
        val sampleHeight = minOf(bitmap.height, 10)
        var firstColor = 0
        var allSame = true

        for (y in 1 until sampleHeight) {
            for (x in 1 until sampleWidth) {
                val px = bitmap.getPixel(
                    x * bitmap.width / sampleWidth,
                    y * bitmap.height / sampleHeight
                )
                if (x == 1 && y == 1) {
                    firstColor = px
                } else if (px != firstColor) {
                    allSame = false
                    break
                }
            }
            if (!allSame) break
        }
        return allSame
    }

    private fun applyVisibility(data: BookplateData, settings: DataVisibilitySettings): BookplateData {
        return data.copy(
            bookName = if (settings.isBasicInfoVisible()) data.bookName else "",
            author = if (settings.isBasicInfoVisible()) data.author else "",
            coverUrl = if (settings.isBasicInfoVisible()) data.coverUrl else "",
            intro = if (settings.isBasicInfoVisible()) data.intro else "",
            kind = if (settings.isBasicInfoVisible()) data.kind else "",
            wordCount = if (settings.isBasicInfoVisible()) data.wordCount else "",
            originName = if (settings.isBasicInfoVisible()) data.originName else "",
            totalChapterNum = if (settings.isBasicInfoVisible()) data.totalChapterNum else 0,
            latestChapterTitle = if (settings.isBasicInfoVisible()) data.latestChapterTitle else "",
            typeText = if (settings.isBasicInfoVisible()) data.typeText else "",
            charset = if (settings.isBasicInfoVisible()) data.charset else "",

            readingStatusText = if (settings.isProgressVisible()) data.readingStatusText else "",
            readingProgress = if (settings.isProgressVisible()) data.readingProgress else "",
            readChapters = if (settings.isProgressVisible()) data.readChapters else "0/0",
            unreadChapters = if (settings.isProgressVisible()) data.unreadChapters else 0,
            readIteration = if (settings.isProgressVisible()) data.readIteration else 0,
            readIterationText = if (settings.isProgressVisible()) data.readIterationText else "",
            durChapterTitle = if (settings.isProgressVisible()) data.durChapterTitle else "",

            totalReadTime = if (settings.isStatisticsVisible()) data.totalReadTime else "",
            totalReadHours = if (settings.isStatisticsVisible()) data.totalReadHours else 0,
            totalReadMinutes = if (settings.isStatisticsVisible()) data.totalReadMinutes else 0,
            readingDays = if (settings.isStatisticsVisible()) data.readingDays else 0,
            maxDayReadTime = if (settings.isStatisticsVisible()) data.maxDayReadTime else "",
            maxDayReadDate = if (settings.isStatisticsVisible()) data.maxDayReadDate else "",
            totalReadWords = if (settings.isStatisticsVisible()) data.totalReadWords else "",
            remainingWords = if (settings.isStatisticsVisible()) data.remainingWords else "",

            firstReadTime = if (settings.isBasicInfoVisible()) data.firstReadTime else "____/__/__",
            lastReadTime = if (settings.isBasicInfoVisible()) data.lastReadTime else "____/__/__",
            finishReadTime = if (settings.isBasicInfoVisible()) data.finishReadTime else "____/__/__",
            addBookshelfTime = if (settings.isBasicInfoVisible()) data.addBookshelfTime else "____/__/__",
            lastCheckTime = if (settings.isBasicInfoVisible()) data.lastCheckTime else "____/__/__",
            lastReadTimeRelative = if (settings.isBasicInfoVisible()) data.lastReadTimeRelative else "",

            rating = if (settings.isRatingReviewVisible()) data.rating else 0f,
            ratingStars = if (settings.isRatingReviewVisible()) data.ratingStars else "☆☆☆☆☆",
            ratingMax = if (settings.isRatingReviewVisible()) data.ratingMax else 5,
            reviewContent = if (settings.isRatingReviewVisible()) data.reviewContent else "",

            annotationCount = if (settings.isAnnotationVisible()) data.annotationCount else 0,
            thoughtCount = if (settings.isAnnotationVisible()) data.thoughtCount else 0,
            latestAnnotation = if (settings.isAnnotationVisible()) data.latestAnnotation else "",
            latestAnnotationNote = if (settings.isAnnotationVisible()) data.latestAnnotationNote else "",
            latestAnnotationChapter = if (settings.isAnnotationVisible()) data.latestAnnotationChapter else "",

            protagonists = if (settings.isProtagonistVisible()) data.protagonists else "未知",

            tags = if (settings.isTagsVisible()) data.tags else "",
            tagCount = if (settings.isTagsVisible()) data.tagCount else 0,

            bookSourceName = if (settings.isSourceVisible()) data.bookSourceName else "",
            bookSourceGroup = if (settings.isSourceVisible()) data.bookSourceGroup else "",

            readTimeRank = if (settings.isRankVisible()) data.readTimeRank else ""
        )
    }

    private fun replaceVariables(html: String, data: BookplateData): String {
        val varMap = buildVariableMap(data)
        return VARIABLE_REGEX.replace(html) { matchResult ->
            varMap[matchResult.groupValues[1]] ?: matchResult.value
        }
    }

    private fun escapeHtml(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private suspend fun renderHtml(context: Context, html: String): Bitmap? {
        BookplateLogger.log("RENDER", "获取WebView实例...")
        val webView = getWebView(context)
        val startTime = System.currentTimeMillis()

        return try {
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(IMAGE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            BookplateLogger.log("RENDER", "预设宽度: ${IMAGE_WIDTH}, 初始高度: ${webView.measuredHeight}")
            webView.layout(0, 0, IMAGE_WIDTH, webView.measuredHeight.coerceAtLeast(1))

            val pageLoaded = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val loadTime = System.currentTimeMillis() - startTime
                            BookplateLogger.log("RENDER", "onPageFinished触发, 耗时=${loadTime}ms, postDelayed ${CSS_LAYOUT_DELAY_MS}ms")
                            if (continuation.isActive) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (continuation.isActive) {
                                        continuation.resume(true) {}
                                    }
                                }, CSS_LAYOUT_DELAY_MS)
                            }
                        }
                    }

                    webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
                    BookplateLogger.log("RENDER", "loadDataWithBaseURL调用完成")

                    continuation.invokeOnCancellation {
                        try { webView.stopLoading() } catch (_: Exception) {}
                    }
                }
            }

            if (pageLoaded != true) {
                BookplateLogger.log("RENDER", "页面加载超时")
                return null
            }

            BookplateLogger.log("RENDER", "页面加载完成, 开始强制布局...")

            val bitmap = captureFullBitmap(webView, startTime)
            BookplateLogger.log("RENDER", "总耗时=${System.currentTimeMillis() - startTime}ms")
            bitmap
        } catch (e: CancellationException) {
            BookplateLogger.log("RENDER", "渲染取消: ${e.message}")
            null
        } finally {
            try {
                webView.stopLoading()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun captureFullBitmap(webView: WebView, startTime: Long): Bitmap? {
        val heightDeferred = CompletableDeferred<Int>()
        webView.settings.javaScriptEnabled = true
        webView.evaluateJavascript("document.documentElement.scrollHeight") { result ->
            webView.settings.javaScriptEnabled = false
            try {
                val parsed = result.trim('"').toIntOrNull() ?: 0
                heightDeferred.complete(parsed)
            } catch (_: Exception) {
                heightDeferred.complete(0)
            }
        }
        val contentHeight = withTimeoutOrNull(HEIGHT_TIMEOUT_MS) {
            heightDeferred.await()
        } ?: run {
            BookplateLogger.log("RENDER", "JS高度测量超时")
            return null
        }
        BookplateLogger.log("RENDER", "JS测量内容高度: $contentHeight")

        if (contentHeight <= 0) {
            BookplateLogger.log("RENDER", "内容高度为0，无法渲染")
            return null
        }

        webView.measure(
            View.MeasureSpec.makeMeasureSpec(IMAGE_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, IMAGE_WIDTH, contentHeight)

        BookplateLogger.log("RENDER", "最终布局: ${IMAGE_WIDTH}x$contentHeight")

        return try {
            Bitmap.createBitmap(IMAGE_WIDTH, contentHeight, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                webView.draw(canvas)
            }
        } catch (e: Exception) {
            BookplateLogger.log("RENDER", "位图创建异常: ${e.message}")
            null
        }
    }
}
