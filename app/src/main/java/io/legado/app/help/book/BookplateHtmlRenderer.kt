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

    private const val RENDER_TIMEOUT_MS = 12000L
    private const val MAX_CACHE_SIZE = 16

    @Volatile
    private var cachedWebViewDeferred: CompletableDeferred<WebView>? = null

    private val bitmapCache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val shouldRemove = size > MAX_CACHE_SIZE
            if (shouldRemove && eldest != null) eldest.value.recycle()
            return shouldRemove
        }
    }

    private val VARIABLE_REGEX = Regex("\\{\\{(\\w+)\\}\\}")
    private val HEAD_TAG_REGEX = Regex("<head>", RegexOption.IGNORE_CASE)
    private val VIEWPORT_META_REGEX = Regex("""<meta\s+name=["']viewport["'][^>]*>""", RegexOption.IGNORE_CASE)

    private fun getRenderWidth(ctx: Context): Int =
        (ctx.resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtLeast(360)

    fun clearCache() {
        synchronized(bitmapCache) { bitmapCache.values.forEach { it.recycle() }; bitmapCache.clear() }
        BookplateLogger.log("RENDER", "缓存已清空")
    }

    /* ── 只加 viewport，绝不注入 body 样式 ── */
    private fun ensureViewportMeta(html: String, w: Int): String {
        val vp = "<meta name=\"viewport\" content=\"width=$w, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        return if (HEAD_TAG_REGEX.containsMatchIn(html))
            HEAD_TAG_REGEX.replaceFirst(html, "<head>\n$vp\n")
        else "$vp\n$html"
    }

    private fun buildVariableMap(d: BookplateData) = mapOf(
        "bookName" to d.bookName, "author" to d.author, "coverUrl" to d.coverUrl,
        "intro" to escapeHtml(d.intro), "kind" to d.kind, "wordCount" to d.wordCount,
        "originName" to d.originName, "totalChapterNum" to d.totalChapterNum.toString(),
        "latestChapterTitle" to d.latestChapterTitle, "typeText" to d.typeText, "charset" to d.charset,
        "readingStatusText" to d.readingStatusText, "readingProgress" to d.readingProgress,
        "readChapters" to d.readChapters, "unreadChapters" to d.unreadChapters.toString(),
        "readIteration" to d.readIteration.toString(), "readIterationText" to d.readIterationText,
        "durChapterTitle" to d.durChapterTitle, "totalReadTime" to d.totalReadTime,
        "totalReadHours" to d.totalReadHours.toString(), "totalReadMinutes" to d.totalReadMinutes.toString(),
        "readingDays" to d.readingDays.toString(), "maxDayReadTime" to d.maxDayReadTime,
        "maxDayReadDate" to d.maxDayReadDate, "totalReadWords" to d.totalReadWords,
        "remainingWords" to d.remainingWords, "firstReadTime" to d.firstReadTime,
        "lastReadTime" to d.lastReadTime, "finishReadTime" to d.finishReadTime,
        "addBookshelfTime" to d.addBookshelfTime, "lastCheckTime" to d.lastCheckTime,
        "lastReadTimeRelative" to d.lastReadTimeRelative, "rating" to d.rating.toString(),
        "ratingStars" to d.ratingStars, "ratingMax" to d.ratingMax.toString(),
        "reviewContent" to escapeHtml(d.reviewContent), "annotationCount" to d.annotationCount.toString(),
        "thoughtCount" to d.thoughtCount.toString(), "latestAnnotation" to escapeHtml(d.latestAnnotation),
        "latestAnnotationNote" to escapeHtml(d.latestAnnotationNote),
        "latestAnnotationChapter" to d.latestAnnotationChapter, "protagonists" to d.protagonists,
        "tags" to d.tags, "tagCount" to d.tagCount.toString(),
        "bookSourceName" to d.bookSourceName, "bookSourceGroup" to d.bookSourceGroup,
        "readTimeRank" to d.readTimeRank
    )

    private suspend fun getWebView(ctx: Context): WebView {
        cachedWebViewDeferred?.let {
            val wv = it.await()
            withContext(Dispatchers.Main) {
                wv.stopLoading(); wv.clearHistory(); wv.clearCache(true)
                wv.removeJavascriptInterface("HeightBridge")
                wv.setLayerType(View.LAYER_TYPE_NONE, null)
                wv.loadUrl("about:blank")
            }
            // 等待 about:blank 加载完成，确保 DOM 彻底清空
            delay(100)
            return wv
        }
        val d = CompletableDeferred<WebView>()
        cachedWebViewDeferred = d
        return withContext(Dispatchers.Main) {
            val wv = WebView(ctx.applicationContext).apply {
                settings.apply {
                    javaScriptEnabled = true; domStorageEnabled = true
                    useWideViewPort = false; loadWithOverviewMode = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    setSupportZoom(false); builtInZoomControls = false; displayZoomControls = false
                    blockNetworkLoads = false; blockNetworkImage = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                setBackgroundColor(Color.TRANSPARENT)
            }
            d.complete(wv); wv
        }
    }

    fun destroyWebView() {
        synchronized(bitmapCache) { bitmapCache.values.forEach { it.recycle() }; bitmapCache.clear() }
        cachedWebViewDeferred?.takeIf { it.isCompleted }?.let { try { it.getCompleted().destroy() } catch (_: Exception) {} }
        cachedWebViewDeferred = null
    }

    suspend fun render(ctx: Context, tpl: BookplateTemplate, data: BookplateData,
                       cfg: DataVisibilitySettings = DataVisibilitySettings): Bitmap? {
        val w = getRenderWidth(ctx)
        val key = "${data.bookName}_${data.author}_${tpl.id}_${tpl.htmlContent.hashCode()}_$w"
        synchronized(bitmapCache) { bitmapCache[key]?.takeIf { !it.isRecycled }?.let { return it }; bitmapCache.remove(key) }
        return withContext(Dispatchers.Main) {
            lastError = null
            val fd = applyVisibility(data, cfg)
            val cov = coverUrlToDataUri(fd.coverUrl)
            val d = if (cov != null) fd.copy(coverUrl = cov) else fd
            val html = replaceVariables(tpl.htmlContent, d)
            if (html.isBlank()) { lastError = "HTML为空"; return@withContext null }
            renderHtml(ctx, ensureViewportMeta(html, w), w)
                ?.also { synchronized(bitmapCache) { bitmapCache[key] = it } }
        }
    }

    /* ═════════════════════ 核心渲染 ═════════════════════ */
    private suspend fun renderHtml(ctx: Context, html: String, w: Int): Bitmap? {
        val t0 = System.currentTimeMillis()

        BookplateLogger.log("RENDER", "========== 渲染开始 ==========")
        BookplateLogger.log("RENDER", "width=$w")

        val wv = getWebView(ctx)

        return try {
            // 第一步：用UNSPECIFIED让WebView自然布局，不限制高度
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val naturalHeight = wv.measuredHeight.coerceAtLeast(100)
            BookplateLogger.log("RENDER", "初始自然高度: ${naturalHeight}px")

            // 用自然高度布局
            wv.layout(0, 0, w, naturalHeight)

            var finished = false
            var contentHeight = 0

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) {
                    finished = true
                    // 页面加载完成后，再次用UNSPECIFIED测量真实内容高度
                    v?.measure(
                        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    contentHeight = v?.measuredHeight ?: 0
                    BookplateLogger.log("RENDER", "页面加载后内容高度: ${contentHeight}px")
                }
            }

            wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

            // 等待页面加载完成
            withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                while (!finished) delay(50)
            } ?: run {
                BookplateLogger.log("RENDER", "页面加载超时")
                return null
            }

            // 给渲染一点稳定时间
            delay(300)

            // 再次测量最终高度
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val finalHeight = wv.measuredHeight.coerceAtLeast(100)
            BookplateLogger.log("RENDER", "最终内容高度: ${finalHeight}px")

            if (finalHeight <= 0) {
                BookplateLogger.log("RENDER", "内容高度为0")
                return null
            }

            // 用最终高度重新布局
            wv.layout(0, 0, w, finalHeight)
            delay(100)

            BookplateLogger.log("RENDER", "截图 ${w}x$finalHeight")
            val bitmap = Bitmap.createBitmap(w, finalHeight, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawColor(Color.WHITE); wv.draw(Canvas(it))
            }

            val totalTime = System.currentTimeMillis() - t0
            BookplateLogger.log("RENDER", "========== 成功 ${bitmap.width}x${bitmap.height} 总=${totalTime}ms ==========")
            bitmap
        } catch (e: CancellationException) { lastError = "取消"; null }
        catch (e: Exception) {
            BookplateLogger.log("RENDER", "渲染异常:${e.message}")
            lastError = e.message
            null
        } finally { try { wv.stopLoading() } catch (_: Exception) {} }
    }

    private fun applyVisibility(d: BookplateData, s: DataVisibilitySettings) = d.copy(
        bookName=if(s.isBasicInfoVisible())d.bookName else "",
        author=if(s.isBasicInfoVisible())d.author else "",
        coverUrl=if(s.isBasicInfoVisible())d.coverUrl else "",
        intro=if(s.isBasicInfoVisible())d.intro else "",
        kind=if(s.isBasicInfoVisible())d.kind else "",
        wordCount=if(s.isBasicInfoVisible())d.wordCount else "",
        originName=if(s.isBasicInfoVisible())d.originName else "",
        totalChapterNum=if(s.isBasicInfoVisible())d.totalChapterNum else 0,
        latestChapterTitle=if(s.isBasicInfoVisible())d.latestChapterTitle else "",
        typeText=if(s.isBasicInfoVisible())d.typeText else "",
        charset=if(s.isBasicInfoVisible())d.charset else "",
        readingStatusText=if(s.isProgressVisible())d.readingStatusText else "",
        readingProgress=if(s.isProgressVisible())d.readingProgress else "",
        readChapters=if(s.isProgressVisible())d.readChapters else "0/0",
        unreadChapters=if(s.isProgressVisible())d.unreadChapters else 0,
        readIteration=if(s.isProgressVisible())d.readIteration else 0,
        readIterationText=if(s.isProgressVisible())d.readIterationText else "",
        durChapterTitle=if(s.isProgressVisible())d.durChapterTitle else "",
        totalReadTime=if(s.isStatisticsVisible())d.totalReadTime else "",
        totalReadHours=if(s.isStatisticsVisible())d.totalReadHours else 0,
        totalReadMinutes=if(s.isStatisticsVisible())d.totalReadMinutes else 0,
        readingDays=if(s.isStatisticsVisible())d.readingDays else 0,
        maxDayReadTime=if(s.isStatisticsVisible())d.maxDayReadTime else "",
        maxDayReadDate=if(s.isStatisticsVisible())d.maxDayReadDate else "",
        totalReadWords=if(s.isStatisticsVisible())d.totalReadWords else "",
        remainingWords=if(s.isStatisticsVisible())d.remainingWords else "",
        firstReadTime=if(s.isBasicInfoVisible())d.firstReadTime else "____/__/__",
        lastReadTime=if(s.isBasicInfoVisible())d.lastReadTime else "____/__/__",
        finishReadTime=if(s.isBasicInfoVisible())d.finishReadTime else "____/__/__",
        addBookshelfTime=if(s.isBasicInfoVisible())d.addBookshelfTime else "____/__/__",
        lastCheckTime=if(s.isBasicInfoVisible())d.lastCheckTime else "____/__/__",
        lastReadTimeRelative=if(s.isBasicInfoVisible())d.lastReadTimeRelative else "",
        rating=if(s.isRatingReviewVisible())d.rating else 0f,
        ratingStars=if(s.isRatingReviewVisible())d.ratingStars else "☆☆☆☆☆",
        ratingMax=if(s.isRatingReviewVisible())d.ratingMax else 5,
        reviewContent=if(s.isRatingReviewVisible())d.reviewContent else "",
        annotationCount=if(s.isAnnotationVisible())d.annotationCount else 0,
        thoughtCount=if(s.isAnnotationVisible())d.thoughtCount else 0,
        latestAnnotation=if(s.isAnnotationVisible())d.latestAnnotation else "",
        latestAnnotationNote=if(s.isAnnotationVisible())d.latestAnnotationNote else "",
        latestAnnotationChapter=if(s.isAnnotationVisible())d.latestAnnotationChapter else "",
        protagonists=if(s.isProtagonistVisible())d.protagonists else "未知",
        tags=if(s.isTagsVisible())d.tags else "",
        tagCount=if(s.isTagsVisible())d.tagCount else 0,
        bookSourceName=if(s.isSourceVisible())d.bookSourceName else "",
        bookSourceGroup=if(s.isSourceVisible())d.bookSourceGroup else "",
        readTimeRank=if(s.isRankVisible())d.readTimeRank else ""
    )

    private fun replaceVariables(h: String, d: BookplateData): String {
        val m = buildVariableMap(d)
        return VARIABLE_REGEX.replace(h) { m[it.groupValues[1]] ?: it.value }
    }

    private fun escapeHtml(t: String): String {
        if (t.isEmpty()) return t
        val sb = StringBuilder(t.length + 8)
        for (c in t) when (c) {
            '&' -> sb.append("&amp;"); '<' -> sb.append("&lt;"); '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;"); else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun coverUrlToDataUri(u: String): String? {
        if (u.isBlank() || u.startsWith("http") || u.startsWith("data:")) return u.ifBlank { null }
        return try {
            val f = when {
                u.startsWith("file://") -> File(URI(u))
                else -> File(u)
            }
            if (f.exists()) {
                val baos = ByteArrayOutputStream()
                BitmapFactory.decodeFile(f.absolutePath)?.let {
                    it.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    it.recycle()
                    "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }
            } else null
        } catch (_: Exception) { null }
    }
}
