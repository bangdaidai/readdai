package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.app.data.entities.BookplateData
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.help.config.DataVisibilitySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BookplateHtmlRenderer {

    private const val RENDER_TIMEOUT_MS = 8000L
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

    private fun getRenderWidth(context: Context): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return (screenWidth * 0.9f).toInt().coerceAtLeast(320)
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
        BookplateLogger.log("RENDER", "缓存已清空")
    }

    private fun ensureViewportMeta(html: String, width: Int): String {
        if (VIEWPORT_META_REGEX.containsMatchIn(html)) {
            return html
        }
        return HEAD_TAG_REGEX.replaceFirst(html, "<head>\n<meta name=\"viewport\" content=\"width=${width}, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
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
            }
            return wv
        }
        val deferred = CompletableDeferred<WebView>()
        cachedWebViewDeferred = deferred
        return withContext(Dispatchers.Main) {
            val wv = WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                setInitialScale(100)
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
        val renderWidth = getRenderWidth(context)
        val cacheKey = "${data.bookName}_${data.author}_${template.id}_${template.htmlContent.hashCode()}_${renderWidth}"
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
            BookplateLogger.log("RENDER", "开始渲染: 模板=${template.name}, 宽度=${renderWidth}")
            val filteredData = applyVisibility(data, settings)
            val html = replaceVariables(template.htmlContent, filteredData)
            BookplateLogger.log("RENDER", "变量替换后HTML长度: ${html.length}")

            if (html.isBlank()) {
                BookplateLogger.log("RENDER", "HTML为空，返回null")
                return@withContext null
            }

            BookplateLogger.log("RENDER", "开始WebView离屏渲染...")
            val bitmap = renderHtml(context, ensureViewportMeta(html, renderWidth), renderWidth)
            if (bitmap != null) {
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

    /**
     * 核心渲染方法
     *
     * 关键思路：WebView 必须在正确的尺寸下加载 HTML，否则 Chromium 内部渲染管线
     * 不会为后续的 layout 变更重新绘制像素。
     *
     * 流程：
     * 1. 先把 WebView layout 成目标宽度 × 足够高度
     * 2. 加载 HTML（此时内容以正确宽度渲染）
     * 3. onPageFinished 后获取实际内容高度
     * 4. 重新 layout 成精确尺寸
     * 5. 等待一帧（Choreographer）确保绘制完成
     * 6. draw() 截图
     */
    private suspend fun renderHtml(context: Context, html: String, width: Int): Bitmap? {
        BookplateLogger.log("RENDER", "获取WebView实例...")
        val webView = getWebView(context)
        val startTime = System.currentTimeMillis()

        return try {
            // 1. 先把 WebView 设为目标宽度 × 足够大的高度
            //    这样 HTML 加载时就会以正确的宽度渲染内容
            val initialHeight = context.resources.displayMetrics.heightPixels * 2
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(initialHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, initialHeight)
            BookplateLogger.log("RENDER", "初始布局: ${width}x${initialHeight}")

            // 2. 加载 HTML，等待 onPageFinished
            val heightDeferred = CompletableDeferred<Int>()
            val pageLoaded = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val loadTime = System.currentTimeMillis() - startTime
                            BookplateLogger.log("RENDER", "onPageFinished, 耗时=${loadTime}ms")
                            // onPageFinished 表示 HTML 解析完成，但 CSS 布局可能还没完成
                            // 等待一帧后获取高度
                            Choreographer.getInstance().postFrameCallback {
                                Handler(Looper.getMainLooper()).post {
                                    if (continuation.isActive) {
                                        // 获取内容高度
                                        webView.evaluateJavascript(
                                            "Math.max(document.body?.scrollHeight||0, document.documentElement?.scrollHeight||0)"
                                        ) { result ->
                                            val h = result.trim('"').toIntOrNull() ?: 0
                                            heightDeferred.complete(h)
                                            continuation.resume(true) {}
                                        }
                                    }
                                }
                            }
                        }
                    }

                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    BookplateLogger.log("RENDER", "loadDataWithBaseURL完成")

                    continuation.invokeOnCancellation {
                        try { webView.stopLoading() } catch (_: Exception) {}
                    }
                }
            }

            if (pageLoaded != true) {
                BookplateLogger.log("RENDER", "页面加载超时")
                return null
            }

            // 3. 获取实际内容高度
            val contentHeight = withTimeoutOrNull(HEIGHT_TIMEOUT_MS) {
                heightDeferred.await()
            } ?: run {
                BookplateLogger.log("RENDER", "高度获取超时")
                return null
            }

            if (contentHeight <= 0) {
                BookplateLogger.log("RENDER", "内容高度为0")
                return null
            }

            BookplateLogger.log("RENDER", "内容高度: ${contentHeight}px")

            // 4. 重新 layout 成精确尺寸
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, contentHeight)

            // 5. 等待一帧确保 Chromium 完成重绘
            withTimeoutOrNull(1000L) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    Choreographer.getInstance().postFrameCallback {
                        if (continuation.isActive) {
                            continuation.resume(Unit) {}
                        }
                    }
                }
            }

            BookplateLogger.log("RENDER", "开始截图: ${width}x${contentHeight}")

            // 6. 截图
            return try {
                Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    webView.draw(canvas)
                }
            } catch (e: Exception) {
                BookplateLogger.log("RENDER", "截图异常: ${e.message}")
                null
            }.also {
                BookplateLogger.log("RENDER", "总耗时=${System.currentTimeMillis() - startTime}ms")
            }
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

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return true
        var whiteCount = 0
        var totalCount = 0
        for (y in (bitmap.height / 4) until (bitmap.height * 3 / 4)) {
            for (x in (bitmap.width / 4) until (bitmap.width * 3 / 4)) {
                val px = bitmap.getPixel(x, y)
                totalCount++
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r > 250 && g > 250 && b > 250) {
                    whiteCount++
                }
            }
        }
        return totalCount > 0 && whiteCount > totalCount * 0.9
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
}
