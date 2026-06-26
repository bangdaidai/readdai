package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import io.legado.app.data.entities.BookplateData
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.help.config.DataVisibilitySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BookplateHtmlRenderer {

    var lastError: String? = null
        private set

    private const val RENDER_TIMEOUT_MS = 10000L
    private const val MAX_CACHE_SIZE = 16
    private const val MAX_GENEROUS_HEIGHT = 16000

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
    private val HEAD_TAG_REGEX = Regex("<head>", RegexOption.IGNORE_CASE)

    private fun getRenderWidth(context: Context): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return (screenWidth * 0.92f).toInt().coerceAtLeast(360)
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
        BookplateLogger.log("RENDER", "缓存已清空")
    }

    // 不再注入任何样式，完全尊重模板原始CSS
    private fun ensureViewportMeta(html: String, width: Int): String {
        val viewportTag = "<meta name=\"viewport\" content=\"width=${width}, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        return if (HEAD_TAG_REGEX.containsMatchIn(html)) {
            HEAD_TAG_REGEX.replaceFirst(html, "<head>\n$viewportTag\n")
        } else {
            "$viewportTag\n$html"
        }
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
                wv.stopLoading()
                wv.clearHistory()
                wv.clearCache(true)
                wv.removeJavascriptInterface("HeightBridge")
                wv.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            return wv
        }

        val deferred = CompletableDeferred<WebView>()
        cachedWebViewDeferred = deferred
        return withContext(Dispatchers.Main) {
            val wv = WebView(context.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    blockNetworkLoads = false
                    blockNetworkImage = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }
                setBackgroundColor(Color.TRANSPARENT)
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
            BookplateLogger.log("RENDER", "开始渲染: 模板=${template.name}(id=${template.id}), 宽度=${renderWidth}")
            lastError = null
            val filteredData = applyVisibility(data, settings)

            val coverDataUri = coverUrlToDataUri(filteredData.coverUrl)
            val dataWithCover = if (coverDataUri != null) {
                filteredData.copy(coverUrl = coverDataUri)
            } else {
                filteredData
            }

            val html = replaceVariables(template.htmlContent, dataWithCover)
            if (html.isBlank()) {
                lastError = "模板变量替换后 HTML 为空"
                return@withContext null
            }

            val processedHtml = ensureViewportMeta(html, renderWidth)
            val bitmap = renderHtml(context, processedHtml, renderWidth)
            
            if (bitmap != null) {
                synchronized(bitmapCache) {
                    bitmapCache[cacheKey] = bitmap
                }
            }
            bitmap
        }
    }

    /**
     * 核心渲染方法 — 完全对齐 Reeden 流程
     * 
     * 关键原则：
     * 1. 不注入任何额外CSS
     * 2. 用 UNSPECIFIED 让 WebView 自然布局
     * 3. 不依赖 JS 测量高度
     * 4. 直接使用 measuredHeight 作为最终高度
     */
    private suspend fun renderHtml(context: Context, html: String, width: Int): Bitmap? {
        val t0 = System.currentTimeMillis()

        BookplateLogger.log("RENDER", "========== 渲染开始 ==========")
        BookplateLogger.log("RENDER", "renderWidth=$width")

        val webView = getWebView(context)

        return try {
            // ====== 第一步：用 UNSPECIFIED 让 WebView 自然布局 ======
            // 这是最关键的一步：不指定高度，让内容决定高度
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            
            // 获取自然高度
            val naturalHeight = webView.measuredHeight
            BookplateLogger.log("RENDER", "初始自然高度: ${naturalHeight}px")
            
            // 用自然高度布局，避免撑大
            val layoutHeight = maxOf(naturalHeight, 100)
            webView.layout(0, 0, width, layoutHeight)
            BookplateLogger.log("RENDER", "初始布局: ${width}x${layoutHeight}")

            // ====== 第二步：加载内容 ======
            var onPageFinishedCalled = false
            var contentHeight = 0

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    onPageFinishedCalled = true
                    val loadTime = System.currentTimeMillis() - t0
                    BookplateLogger.log("RENDER", "onPageFinished: +${loadTime}ms")
                    
                    // 页面加载完成后，再次用 UNSPECIFIED 测量真实内容高度
                    view?.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    contentHeight = view?.measuredHeight ?: 0
                    BookplateLogger.log("RENDER", "页面加载后内容高度: ${contentHeight}px")
                }
            }

            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            BookplateLogger.log("RENDER", "loadDataWithBaseURL完成: +${System.currentTimeMillis() - t0}ms")

            // ====== 第三步：等待页面加载完成 ======
            withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                while (!onPageFinishedCalled) {
                    delay(50)
                }
            } ?: run {
                BookplateLogger.log("RENDER", "页面加载超时")
                return null
            }

            // 给渲染一些稳定时间
            delay(200)

            // ====== 第四步：再次测量最终内容高度 ======
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val finalHeight = webView.measuredHeight
            BookplateLogger.log("RENDER", "最终内容高度: ${finalHeight}px")

            if (finalHeight <= 0) {
                BookplateLogger.log("RENDER", "内容高度为0，渲染失败")
                return null
            }

            // ====== 第五步：用最终高度重新布局并截图 ======
            webView.layout(0, 0, width, finalHeight)
            
            BookplateLogger.log("RENDER", "截图: ${width}x${finalHeight}")

            val bitmap = try {
                Bitmap.createBitmap(width, finalHeight, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    webView.draw(canvas)
                }
            } catch (e: Exception) {
                BookplateLogger.log("RENDER", "截图异常: ${e.message}")
                null
            }

            if (bitmap != null) {
                val totalTime = System.currentTimeMillis() - t0
                BookplateLogger.log("RENDER", "========== 渲染成功: ${bitmap.width}x${bitmap.height}, 总耗时=${totalTime}ms ==========")
            }

            bitmap
        } catch (e: CancellationException) {
            BookplateLogger.log("RENDER", "渲染被取消")
            null
        } finally {
            try { webView.stopLoading() } catch (_: Exception) {}
        }
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

    private fun coverUrlToDataUri(url: String): String? {
        if (url.isBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("data:")) return url

        return try {
            val bitmap = when {
                url.startsWith("file://") -> {
                    val file = File(URI(url))
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                url.startsWith("content://") -> null
                else -> {
                    val file = File(url)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
            }

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                bitmap.recycle()
                "data:image/jpeg;base64,$base64"
            } else {
                null
            }
        } catch (e: Exception) {
            BookplateLogger.log("RENDER", "封面转换失败: ${e.message}")
            null
        }
    }
}
