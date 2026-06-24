package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object BookplateHtmlRenderer {

    /** 最近一次渲染失败的原因 */
    var lastError: String? = null
        private set

    private const val RENDER_TIMEOUT_MS = 8000L
    private const val MAX_CACHE_SIZE = 16

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
    private val VH_REGEX = Regex("""(\d+(?:\.\d+)?)vh""")

    private fun getRenderWidth(context: Context): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return (screenWidth * 0.9f).toInt().coerceAtLeast(320)
    }

    /** 将 CSS 中的 vh 单位翻译成 px，以 screenHeight 为基准 */
    private fun translateVhToPx(html: String, screenHeight: Int): String {
        return VH_REGEX.replace(html) { match ->
            val vh = match.groupValues[1].toFloat()
            "${(vh * screenHeight / 100).toInt()}px"
        }
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.clear()
        }
        BookplateLogger.log("RENDER", "缓存已清空")
    }

    /**
     * 强制将 viewport meta 替换为指定的宽度
     * 无论模板中是否有 viewport meta，都强制使用我们计算的宽度
     */
    private fun ensureViewportMeta(html: String, width: Int): String {
        return if (VIEWPORT_META_REGEX.containsMatchIn(html)) {
            // 已有 viewport meta，替换 width= 后面的值
            VIEWPORT_META_REGEX.replace(html) { matchResult ->
                val original = matchResult.value
                // 简单替换 width= 后面的内容（可能是 device-width 或具体数值）
                val replaced = original.replaceFirst(Regex("""width=[^,;]+"""), "width=${width}")
                // 如果原来没有指定 initial-scale，确保添加
                if (!replaced.contains("initial-scale")) {
                    replaced.replaceFirst(">", ", initial-scale=1.0>")
                } else {
                    replaced
                }
            }
        } else {
            // 没有 viewport meta，直接在 head 标签后插入
            HEAD_TAG_REGEX.replaceFirst(
                html,
                "<head>\n<meta name=\"viewport\" content=\"width=${width}, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
            )
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
                wv.clearHistory()
                wv.clearCache(true)
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
                    domStorageEnabled = true
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    // 允许加载网络资源和文件，使外部图片/字体可用
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
            BookplateLogger.log("RENDER", "开始渲染: 模板=${template.name}, 宽度=${renderWidth}")
            lastError = null
            val filteredData = applyVisibility(data, settings)

            // 将封面 URL 转为 base64 data URI，确保 WebView 能加载
            val coverDataUri = coverUrlToDataUri(filteredData.coverUrl)
            val dataWithCover = if (coverDataUri != null) {
                filteredData.copy(coverUrl = coverDataUri)
            } else {
                filteredData
            }

            val html = replaceVariables(template.htmlContent, dataWithCover)
            BookplateLogger.log("RENDER", "变量替换后HTML长度: ${html.length}")

            if (html.isBlank()) {
                val msg = "模板变量替换后 HTML 为空，请检查模板中是否使用了 {{变量名}}"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                return@withContext null
            }

            BookplateLogger.log("RENDER", "开始WebView离屏渲染...")
            // vh→px 翻译后，viewHeight 可设为屏幕高度，scrollHeight 准确
            val vhFixedHtml = translateVhToPx(html, context.resources.displayMetrics.heightPixels)
            val processedHtml = ensureViewportMeta(vhFixedHtml, renderWidth)
            val viewportMatch = Regex("""<meta\s+name=["']viewport["'][^>]*>""", RegexOption.IGNORE_CASE).find(processedHtml)
            BookplateLogger.log("RENDER", "viewport meta: ${viewportMatch?.value ?: "未找到"}")
            val bitmap = renderHtml(context, processedHtml, renderWidth)
            if (bitmap != null) {
                lastError = null
                BookplateLogger.log("RENDER", "WebView渲染成功: ${bitmap.width}x${bitmap.height}")
                synchronized(bitmapCache) {
                    bitmapCache[cacheKey] = bitmap
                }
            }
            bitmap
        }
    }

    /**
     * 核心渲染方法
     *
     * 自适应方案：
     * 1. WebView 直接用目标宽度，不限制高度
     * 2. 让 WebView 自己测量内容尺寸
     * 3. 用 WebView 测量出的尺寸创建 Bitmap
     */
    private suspend fun renderHtml(context: Context, html: String, width: Int): Bitmap? {
        BookplateLogger.log("RENDER", "获取WebView实例...")
        val webView = getWebView(context)
        val startTime = System.currentTimeMillis()
        // 用屏幕高度作为初始 viewport（vh 已翻译成 px，不需要大视口）
        val screenH = context.resources.displayMetrics.heightPixels

        return try {
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, screenH)
            BookplateLogger.log("RENDER", "初始布局: ${width}x${screenH}")

            val heightDeferred = CompletableDeferred<Int>()
            val webViewErrors = mutableListOf<String>()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    BookplateLogger.log("RENDER", "页面开始加载: ${System.currentTimeMillis() - startTime}ms")
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    val err = "WebView错误[$errorCode]: $description ($failingUrl)"
                    webViewErrors.add(err)
                    BookplateLogger.log("RENDER", err)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    val loadTime = System.currentTimeMillis() - startTime
                    BookplateLogger.log("RENDER", "onPageFinished, 耗时=${loadTime}ms")

                    // vh 已翻译成 px，viewport=screenH，scrollHeight 准确反映内容高度
                    // 两阶段：先测，re-layout 到所测高度，再测一次（处理 % 高度重算）
                    Handler(Looper.getMainLooper()).postDelayed({
                        webView.evaluateJavascript(
                            "Math.max(document.body.scrollHeight||0,document.documentElement.scrollHeight||0)"
                        ) { jsResult ->
                            val h1 = jsResult.trim('"').toIntOrNull()?.coerceAtLeast(1) ?: 1
                            BookplateLogger.log("RENDER", "高度[1]: $h1")

                            // re-layout 到测量高度
                            webView.measure(
                                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(h1, View.MeasureSpec.EXACTLY)
                            )
                            webView.layout(0, 0, width, h1)

                            Handler(Looper.getMainLooper()).postDelayed({
                                webView.evaluateJavascript(
                                    "Math.max(document.body.scrollHeight||0,document.documentElement.scrollHeight||0)"
                                ) { jsResult2 ->
                                    val h2 = jsResult2.trim('"').toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    val finalH = maxOf(h1, h2)
                                    BookplateLogger.log("RENDER", "高度[2]: $h2, 取max: $finalH")
                                    // 如有变化，re-layout 到最终高度
                                    if (finalH != h1) {
                                        webView.measure(
                                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                            View.MeasureSpec.makeMeasureSpec(finalH, View.MeasureSpec.EXACTLY)
                                        )
                                        webView.layout(0, 0, width, finalH)
                                    }
                                    heightDeferred.complete(finalH)
                                }
                            }, 150)
                        }
                    }, 300)
                }
            }

            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            BookplateLogger.log("RENDER", "loadDataWithBaseURL完成, 开始计时...")

            val finalHeight = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                heightDeferred.await()
            } ?: run {
                val wvErrors = webViewErrors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                val msg = buildString {
                    append("页面加载超时 (${RENDER_TIMEOUT_MS}ms)")
                    if (wvErrors != null) append(" | $wvErrors")
                }
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                return null
            }

            if (finalHeight <= 0) {
                val msg = "内容高度为0，渲染失败"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                return null
            }

            BookplateLogger.log("RENDER", "最终尺寸: ${width}x${finalHeight}")

            captureBitmap(webView, width, finalHeight, startTime)
        } catch (e: CancellationException) {
            val msg = "渲染被取消: ${e.message}"
            BookplateLogger.log("RENDER", msg)
            lastError = msg
            null
        } finally {
            try { webView.stopLoading() } catch (_: Exception) {}
        }
    }

    private fun captureBitmap(webView: WebView, width: Int, height: Int, startTime: Long): Bitmap? {
        // 软件绘制兼容性最好，所有设备都能正确捕获
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
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

    /**
     * 将封面 URL 转为 base64 data URI，确保 WebView 能加载
     * file:// 和 content:// 需要转 base64，http(s):// 直接使用原 URL
     */
    private fun coverUrlToDataUri(url: String): String? {
        if (url.isBlank()) return null

        // HTTP(S) URL 直接返回，WebView 可以加载
        if (url.startsWith("http://") || url.startsWith("https://")) return url

        // 已经是 data URI，直接返回
        if (url.startsWith("data:")) return url

        return try {
            val bitmap = when {
                url.startsWith("file://") -> {
                    val file = File(URI(url))
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                url.startsWith("content://") -> {
                    // content:// 不在此上下文中处理，返回 null
                    null
                }
                else -> {
                    val file = File(url)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
            }

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
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
