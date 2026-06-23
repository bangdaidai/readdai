package io.legado.app.help.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.app.data.entities.BookplateData
import io.legado.app.data.entities.BookplateTemplate
import io.legado.app.help.config.DataVisibilitySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object BookplateHtmlRenderer {

    private const val IMAGE_WIDTH = 640
    private const val RENDER_TIMEOUT_SECONDS = 5L

    suspend fun render(
        context: Context,
        template: BookplateTemplate,
        data: BookplateData,
        settings: DataVisibilitySettings = DataVisibilitySettings
    ): Bitmap? = withContext(Dispatchers.Main) {
        BookplateLogger.log("RENDER", "开始渲染: 模板=${template.name}, 可见性: basic=${settings.isBasicInfoVisible()}")
        val filteredData = applyVisibility(data, settings)
        val html = replaceVariables(template.htmlContent, filteredData)
        BookplateLogger.log("RENDER", "变量替换后HTML长度: ${html.length}")

        if (html.isBlank()) {
            BookplateLogger.log("RENDER", "HTML为空，返回null")
            return@withContext null
        }

        BookplateLogger.log("RENDER", "开始WebView离屏渲染...")
        val bitmap = renderHtml(context, html)
        if (bitmap != null) {
            BookplateLogger.log("RENDER", "WebView渲染成功: ${bitmap.width}x${bitmap.height}")
        } else {
            BookplateLogger.log("RENDER", "WebView渲染失败: onPageFinished未完成或超时")
        }
        bitmap
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
        return html
            .replace("{{bookName}}", data.bookName)
            .replace("{{author}}", data.author)
            .replace("{{coverUrl}}", data.coverUrl)
            .replace("{{intro}}", escapeHtml(data.intro))
            .replace("{{kind}}", data.kind)
            .replace("{{wordCount}}", data.wordCount)
            .replace("{{originName}}", data.originName)
            .replace("{{totalChapterNum}}", data.totalChapterNum.toString())
            .replace("{{latestChapterTitle}}", data.latestChapterTitle)
            .replace("{{typeText}}", data.typeText)
            .replace("{{charset}}", data.charset)
            .replace("{{readingStatusText}}", data.readingStatusText)
            .replace("{{readingProgress}}", data.readingProgress)
            .replace("{{readChapters}}", data.readChapters)
            .replace("{{unreadChapters}}", data.unreadChapters.toString())
            .replace("{{readIteration}}", data.readIteration.toString())
            .replace("{{readIterationText}}", data.readIterationText)
            .replace("{{durChapterTitle}}", data.durChapterTitle)
            .replace("{{totalReadTime}}", data.totalReadTime)
            .replace("{{totalReadHours}}", data.totalReadHours.toString())
            .replace("{{totalReadMinutes}}", data.totalReadMinutes.toString())
            .replace("{{readingDays}}", data.readingDays.toString())
            .replace("{{maxDayReadTime}}", data.maxDayReadTime)
            .replace("{{maxDayReadDate}}", data.maxDayReadDate)
            .replace("{{totalReadWords}}", data.totalReadWords)
            .replace("{{remainingWords}}", data.remainingWords)
            .replace("{{firstReadTime}}", data.firstReadTime)
            .replace("{{lastReadTime}}", data.lastReadTime)
            .replace("{{finishReadTime}}", data.finishReadTime)
            .replace("{{addBookshelfTime}}", data.addBookshelfTime)
            .replace("{{lastCheckTime}}", data.lastCheckTime)
            .replace("{{lastReadTimeRelative}}", data.lastReadTimeRelative)
            .replace("{{rating}}", data.rating.toString())
            .replace("{{ratingStars}}", data.ratingStars)
            .replace("{{ratingMax}}", data.ratingMax.toString())
            .replace("{{reviewContent}}", escapeHtml(data.reviewContent))
            .replace("{{annotationCount}}", data.annotationCount.toString())
            .replace("{{thoughtCount}}", data.thoughtCount.toString())
            .replace("{{latestAnnotation}}", escapeHtml(data.latestAnnotation))
            .replace("{{latestAnnotationNote}}", escapeHtml(data.latestAnnotationNote))
            .replace("{{latestAnnotationChapter}}", data.latestAnnotationChapter)
            .replace("{{protagonists}}", data.protagonists)
            .replace("{{tags}}", data.tags)
            .replace("{{tagCount}}", data.tagCount.toString())
            .replace("{{bookSourceName}}", data.bookSourceName)
            .replace("{{bookSourceGroup}}", data.bookSourceGroup)
            .replace("{{readTimeRank}}", data.readTimeRank)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun renderHtml(context: Context, html: String): Bitmap? {
        val bitmapRef = AtomicReference<Bitmap?>()
        val latch = CountDownLatch(1)

        val webView = WebView(context.applicationContext)
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                try {
                    BookplateLogger.log("RENDER", "onPageFinished触发")
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(IMAGE_WIDTH, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    val measuredW = view.measuredWidth
                    val measuredH = view.measuredHeight
                    BookplateLogger.log("RENDER", "测量结果: ${measuredW}x${measuredH}")
                    if (measuredW <= 0 || measuredH <= 0) {
                        BookplateLogger.log("RENDER", "测量尺寸无效，跳过渲染")
                        return
                    }
                    view.layout(0, 0, measuredW, measuredH)

                    val bitmap = Bitmap.createBitmap(measuredW, measuredH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    bitmapRef.set(bitmap)
                    BookplateLogger.log("RENDER", "位图创建成功: ${bitmap.width}x${bitmap.height}")
                } catch (e: Exception) {
                    BookplateLogger.log("RENDER", "onPageFinished异常: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            BookplateLogger.log("RENDER", "开始加载HTML (${html.length} chars)...")
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            val ok = latch.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!ok) {
                BookplateLogger.log("RENDER", "超时: ${RENDER_TIMEOUT_SECONDS}秒内onPageFinished未完成")
            }
        } catch (e: Exception) {
            BookplateLogger.log("RENDER", "加载异常: ${e.message}")
        } finally {
            webView.destroy()
        }

        return bitmapRef.get()
    }
}
