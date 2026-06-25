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
import org.json.JSONArray

object BookplateHtmlRenderer {

    /** 最近一次渲染失败的原因 */
    var lastError: String? = null
        private set

    private const val RENDER_TIMEOUT_MS = 10000L
    private const val MAX_CACHE_SIZE = 16
    // 限制最大初始布局高度，防止超出GPU纹理上限或OOM
    private const val MAX_GENEROUS_HEIGHT = 6000

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
        val withoutMinHeight = HEAD_TAG_REGEX.replaceFirst(
            html,
            "<head>\n<style>body{min-height:0!important}</style>"
        )
        return if (VIEWPORT_META_REGEX.containsMatchIn(withoutMinHeight)) {
            VIEWPORT_META_REGEX.replace(withoutMinHeight) { matchResult ->
                val original = matchResult.value
                val replaced = original.replaceFirst(Regex("""width=[^,;]+"""), "width=${width}")
                if (!replaced.contains("initial-scale")) {
                    replaced.replaceFirst(">", ", initial-scale=1.0>")
                } else {
                    replaced
                }
            }
        } else {
            HEAD_TAG_REGEX.replaceFirst(
                withoutMinHeight,
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
                wv.stopLoading()
                wv.clearHistory()
                wv.clearCache(true)
                wv.removeJavascriptInterface("HeightBridge")
                // 清除上一次渲染残留的 JS 定时器和全局状态
                wv.evaluateJavascript("""
                    (function(){
                        try {
                            if(window.__bookplateTimer__){
                                clearInterval(window.__bookplateTimer__);
                                delete window.__bookplateTimer__;
                            }
                            var id = window.setTimeout(function(){}, 0);
                            while(id--) { window.clearTimeout(id); window.clearInterval(id); }
                        } catch(e){}
                        delete window.__bookplateTimer__;
                    })()
                """, null)
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
            BookplateLogger.log("RENDER", "数据概要: bookName=${data.bookName}, author=${data.author}, coverUrl前30字=${data.coverUrl.take(30)}")
            lastError = null
            val filteredData = applyVisibility(data, settings)

            val coverStart = System.currentTimeMillis()
            val coverDataUri = coverUrlToDataUri(filteredData.coverUrl)
            BookplateLogger.log("RENDER", "封面转换耗时: ${System.currentTimeMillis() - coverStart}ms, 转换结果=${if (coverDataUri != null) "成功(${coverDataUri.length}字)" else "跳过(空或远程)"}")
            val dataWithCover = if (coverDataUri != null) {
                filteredData.copy(coverUrl = coverDataUri)
            } else {
                filteredData
            }

            val htmlStart = System.currentTimeMillis()
            val html = replaceVariables(template.htmlContent, dataWithCover)
            BookplateLogger.log("RENDER", "变量替换耗时: ${System.currentTimeMillis() - htmlStart}ms, HTML长度: ${html.length}")

            if (html.isBlank()) {
                val msg = "模板变量替换后 HTML 为空，请检查模板中是否使用了 {{变量名}}"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                return@withContext null
            }

            BookplateLogger.log("RENDER", "开始WebView离屏渲染...")
            val processedHtml = ensureViewportMeta(html, renderWidth)
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
     * 关键修复：不再依赖JS返回的高度，而是用Android的View测量机制重新计算真实高度
     */
    private suspend fun renderHtml(context: Context, html: String, width: Int): Bitmap? {
        val t0 = System.currentTimeMillis()
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val density = context.resources.displayMetrics.density
        val generousH = minOf(maxOf(screenH * 2, 3000), MAX_GENEROUS_HEIGHT)

        BookplateLogger.log("RENDER", "========== 渲染开始 ==========")
        BookplateLogger.log("RENDER", "屏幕: ${screenW}x${screenH}px, density=$density, renderWidth=$width, generousH=$generousH")
        BookplateLogger.log("RENDER", "HTML总长: ${html.length} 字符")
        BookplateLogger.log("RENDER", "HTML头200字: ${html.take(200).replace("\n","\\n")}")
        BookplateLogger.log("RENDER", "HTML尾200字: ${html.takeLast(200).replace("\n","\\n")}")

        val webView = getWebView(context)
        val t1 = System.currentTimeMillis()
        BookplateLogger.log("RENDER", "WebView获取耗时: ${t1 - t0}ms")

        return try {
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(generousH, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, generousH)
            val t2 = System.currentTimeMillis()
            BookplateLogger.log("RENDER", "初始布局完成: ${width}x${generousH}, 耗时=${t2 - t1}ms")

            val heightDeferred = CompletableDeferred<Int>()
            val webViewErrors = mutableListOf<String>()
            var onPageFinishedCalled = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    BookplateLogger.log("RENDER", "onPageStarted: +${System.currentTimeMillis() - t0}ms")
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                ) {
                    val err = "WebView错误[code=$errorCode]: $description, url=$failingUrl"
                    webViewErrors.add(err)
                    BookplateLogger.log("RENDER", err)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    onPageFinishedCalled = true
                    val loadTime = System.currentTimeMillis() - t0
                    BookplateLogger.log("RENDER", "onPageFinished: +${loadTime}ms")
                }
            }

            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            val t3 = System.currentTimeMillis()
            BookplateLogger.log("RENDER", "loadDataWithBaseURL完成: +${t3 - t0}ms, 开始等待页面加载...")

            // 关键修复：不再依赖JS返回的高度，而是用Android的View测量机制
            val contentHeight = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                // 等待页面加载完成
                while (!onPageFinishedCalled) {
                    delay(50)
                }
                
                // 给渲染一点时间
                delay(300)
                
                // 使用Android的View测量机制获取真实高度
                withContext(Dispatchers.Main) {
                    // 用UNSPECIFIED模式重新测量，获取不被裁剪时的真实高度
                    webView.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    
                    val measuredHeight = webView.measuredHeight
                    BookplateLogger.log("RENDER", "View测量高度: ${measuredHeight}px")
                    
                    if (measuredHeight > 0) {
                        // 加50px安全边距
                        val finalHeight = measuredHeight + 50
                        BookplateLogger.log("RENDER", "使用View测量高度: ${finalHeight}px")
                        heightDeferred.complete(finalHeight)
                    } else {
                        // 备用方案：使用generousH的一半
                        val fallbackHeight = generousH.coerceAtMost(2500)
                        BookplateLogger.log("RENDER", "View测量高度为0，使用备用高度: ${fallbackHeight}px")
                        heightDeferred.complete(fallbackHeight)
                    }
                }
                
                heightDeferred.await()
            } ?: run {
                val elapsed = System.currentTimeMillis() - t0
                val diagnoseInfo = buildString {
                    append("=== 超时诊断 ===")
                    append("\n总耗时: ${elapsed}ms, 超时阈值: ${RENDER_TIMEOUT_MS}ms")
                    append("\nonPageFinished触发: $onPageFinishedCalled")
                    if (webViewErrors.isNotEmpty()) {
                        append("\nWebView错误: ${webViewErrors.joinToString("; ")}")
                    }
                }
                BookplateLogger.log("RENDER", diagnoseInfo)
                
                val msg = "页面加载超时 (${RENDER_TIMEOUT_MS}ms, 实际${elapsed}ms)"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                try {
                    webView.destroy()
                    cachedWebViewDeferred = null
                } catch (_: Exception) {}
                return null
            }

            if (contentHeight <= 0) {
                val msg = "内容高度为0，渲染失败"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
                return null
            }

            // ====== 核心变更：不 re-layout，直接在 generous 布局下截图再裁剪 ======
            val t4 = System.currentTimeMillis()
            BookplateLogger.log("RENDER", "等待截图前稳定: delay(150ms)")
            delay(150)

            val captureH = minOf(generousH, contentHeight + 200)
            BookplateLogger.log("RENDER", "截图: ${width}x${captureH} (contentHeight=$contentHeight, generousH=$generousH)")

            val fullBitmap = captureBitmap(webView, width, captureH, t4)
            if (fullBitmap == null) {
                BookplateLogger.log("RENDER", "截图失败, fullBitmap=null")
                return null
            }

            BookplateLogger.log("RENDER", "截图完成: ${fullBitmap.width}x${fullBitmap.height}")

            // 裁剪到稳定内容高度
            val cropH = contentHeight.coerceAtMost(fullBitmap.height)
            val cropped = if (cropH < fullBitmap.height) {
                BookplateLogger.log("RENDER", "裁剪: ${width}x${contentHeight} (从${fullBitmap.height}裁剪)")
                val bmp = Bitmap.createBitmap(fullBitmap, 0, 0, width, cropH)
                if (bmp != fullBitmap) fullBitmap.recycle()
                bmp
            } else {
                BookplateLogger.log("RENDER", "无需裁剪, 截图高度=${fullBitmap.height} <= 内容高度=$contentHeight")
                fullBitmap
            }

            val totalTime = System.currentTimeMillis() - t0
            BookplateLogger.log("RENDER", "========== 渲染成功: ${cropped.width}x${cropped.height}, 总耗时=${totalTime}ms ==========")
            cropped
        } catch (e: CancellationException) {
            val msg = "渲染被取消: ${e.message}"
            BookplateLogger.log("RENDER", msg)
            lastError = msg
            null
        } finally {
            try { webView.stopLoading() } catch (_: Exception) {}
        }
    }

    /**
     * 【核心修复】优先使用硬件渲染截图，失败时降级为软件渲染
     * 并在截图后强制恢复 LAYER_TYPE_NONE，防止污染缓存的 WebView
     */
    private fun captureBitmap(webView: WebView, width: Int, height: Int, startTime: Long): Bitmap? {
        var bitmap = tryCapture(webView, width, height)

        if (bitmap == null || isBitmapBlank(bitmap)) {
            BookplateLogger.log("RENDER", "硬件截图失败/空白，降级为软件渲染")
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            try {
                bitmap?.recycle()
                bitmap = tryCapture(webView, width, height)
            } finally {
                // 必须恢复，否则缓存的WebView永远是软件渲染
                webView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }

        BookplateLogger.log("RENDER", "截图总耗时=${System.currentTimeMillis() - startTime}ms")
        return bitmap
    }

    private fun tryCapture(webView: WebView, width: Int, height: Int): Bitmap? {
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                webView.draw(canvas)
            }
        } catch (e: Exception) {
            BookplateLogger.log("RENDER", "截图异常: ${e.message}")
            null
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
                if (r > 250 && g > 250 && b > 250) whiteCount++
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
                // 压缩封面以减小 Base64 体积，避免超出 WebView 加载限制
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
