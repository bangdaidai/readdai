package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
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
    private const val MAX_GENEROUS_HEIGHT = 6000
    private const val HEIGHT_MARGIN = 15

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

    interface HeightBridge {
        @JavascriptInterface
        fun onContentHeight(height: Int)
    }

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
        return if (VIEWPORT_META_REGEX.containsMatchIn(html)) {
            VIEWPORT_META_REGEX.replace(html) { matchResult ->
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
                html,
                "<head>\n<meta name=\"viewport\" content=\"width=${width}, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n<style>html,body{margin:0;padding:0;height:auto !important;min-height:0 !important;overflow:hidden;}</style>"
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
                try {
                    wv.destroy()
                } catch (_: Exception) {
                }
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

            val coverStart = System.currentTimeMillis()
            val coverDataUri = coverUrlToDataUri(filteredData.coverUrl)
            BookplateLogger.log("RENDER", "封面转换耗时: ${System.currentTimeMillis() - coverStart}ms")
            val dataWithCover = if (coverDataUri != null) {
                filteredData.copy(coverUrl = coverDataUri)
            } else {
                filteredData
            }

            val htmlStart = System.currentTimeMillis()
            val html = replaceVariables(template.htmlContent, dataWithCover)
            BookplateLogger.log("RENDER", "变量替换耗时: ${System.currentTimeMillis() - htmlStart}ms, HTML长度: ${html.length}")

            if (html.isBlank()) {
                val msg = "模板变量替换后 HTML 为空"
                BookplateLogger.log("RENDER", msg)
                lastError = msg
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

    private suspend fun renderHtml(context: Context, html: String, width: Int): Bitmap? {
        val t0 = System.currentTimeMillis()
        val webView = getWebView(context)
        val heightDeferred = CompletableDeferred<Int>()

        withContext(Dispatchers.Main) {
            webView.addJavascriptInterface(object : HeightBridge {
                override fun onContentHeight(height: Int) {
                    if (!heightDeferred.isCompleted) {
                        val safeHeight = height.coerceAtMost(MAX_GENEROUS_HEIGHT) + HEIGHT_MARGIN
                        BookplateLogger.log("RENDER", "JS获取文档真实高度: $height, 最终布局高度:$safeHeight")
                        heightDeferred.complete(safeHeight)
                    }
                }
            }, "HeightBridge")

            webView.webViewClient = object : WebViewClient() {
                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                ) {
                    val err = "WebView错误[code=$errorCode]: $description"
                    BookplateLogger.log("RENDER", err)
                    lastError = err
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("""
                        (function(){
                            document.body.style.minHeight = '0px';
                            document.body.style.height = 'auto';
                            document.documentElement.style.height = 'auto';
                            var imgs = document.querySelectorAll('img');
                            var loaded = 0;
                            if(imgs.length === 0){
                                HeightBridge.onContentHeight(document.documentElement.scrollHeight);
                                return;
                            }
                            imgs.forEach(function(img){
                                if(img.complete){
                                    loaded++;
                                    if(loaded >= imgs.length){
                                        HeightBridge.onContentHeight(document.documentElement.scrollHeight);
                                    }
                                }else{
                                    img.onload = function(){
                                        loaded++;
                                        if(loaded >= imgs.length){
                                            HeightBridge.onContentHeight(document.documentElement.scrollHeight);
                                        }
                                    }
                                    img.onerror = function(){
                                        loaded++;
                                        if(loaded >= imgs.length){
                                            HeightBridge.onContentHeight(document.documentElement.scrollHeight);
                                        }
                                    }
                                }
                            })
                        })()
                    """.trimIndent(), null)
                }
            }

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            webView.layout(0, 0, width, 300)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        val contentHeight = withTimeoutOrNull(RENDER_TIMEOUT_MS) {
            heightDeferred.await()
        } ?: run {
            val msg = "页面高度获取超时"
            BookplateLogger.log("RENDER", msg)
            lastError = msg
            try {
                webView.stopLoading()
            } catch (_: Exception) {}
            return null
        }

        if (contentHeight <= 0) {
            lastError = "文档高度为0"
            return null
        }

        withContext(Dispatchers.Main) {
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, contentHeight)
        }

        delay(150)

        val bitmap = captureBitmap(webView, width, contentHeight, t0)
        if (bitmap == null) {
            lastError = "截图生成失败"
        }
        return bitmap
    }

    private fun captureBitmap(webView: WebView, width: Int, height: Int, startTime: Long): Bitmap? {
        var bitmap = tryCapture(webView, width, height)

        if (bitmap == null || isBitmapBlank(bitmap)) {
            BookplateLogger.log("RENDER", "硬件渲染截图空白，降级软件渲染")
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            try {
                bitmap?.recycle()
                bitmap = tryCapture(webView, width, height)
            } finally {
                webView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }

        BookplateLogger.log("RENDER", "截图耗时=${System.currentTimeMillis() - startTime}ms")
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
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                if (r > 250 && g > 250) whiteCount++
                totalCount++
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
