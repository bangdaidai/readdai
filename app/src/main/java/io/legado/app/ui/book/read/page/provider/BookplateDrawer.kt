package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 藏书票绘制器
 * 在阅读页首尾显示精美的阅读凭证
 */
object BookplateDrawer {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private var ratingRect = RectF()

    /**
     * 获取书评内容，统一从 BookReview 表读取
     */
    private fun getReviewContent(book: Book): String? {
        val reviews = appDb.bookReviewDao.getReviewByBookUrlSync(book.bookUrl)
        return reviews.firstOrNull()?.reviewContent?.takeIf { it.isNotBlank() }
    }

    /**
     * 计算藏书票所需高度
     */
    private fun measureHeight(book: Book, bpWidth: Float): Float {
        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        paint.typeface = Typeface.MONOSPACE

        var h = 0f
        // 顶部留白
        h += 40.dpToPx()
        // 标题（Reading Certificate + 阅读凭证）
        h += 30.dpToPx()
        // 开始时间
        h += 22.dpToPx()
        // 结束时间
        h += 22.dpToPx()
        // 虚线分隔间距（上）
        h += 22.dpToPx()
        // 虚线
        h += 12.dpToPx()
        // 书名
        h += 22.dpToPx()
        // 书摘条数
        h += 22.dpToPx()
        // 阅读时间
        h += 22.dpToPx()
        // 虚线分隔间距（下）
        h += 22.dpToPx()
        // 虚线
        h += 12.dpToPx()
        // 阅读评分
        h += 24.dpToPx()

        // 书评
        val reviewContent = getReviewContent(book)
        if (!reviewContent.isNullOrBlank()) {
            paint.textSize = 12.dpToPx().toFloat()
            val maxWidth = bpWidth - 40.dpToPx()
            val paragraphs = reviewContent.split("\n")
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    h += 12.dpToPx()
                    continue
                }
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    if (paint.measureText(remainingText) <= maxWidth) {
                        h += 18.dpToPx()
                        break
                    } else {
                        var cutIndex = remainingText.length
                        while (cutIndex > 0 && paint.measureText(remainingText.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        if (cutIndex == 0) cutIndex = 1
                        h += 18.dpToPx()
                        remainingText = remainingText.substring(cutIndex)
                    }
                }
            }
            h += 16.dpToPx() // 书评底部间距
            h += 12.dpToPx() // 虚线
            h += 22.dpToPx() // 书评和底部标语之间的间隙
        }

        // 底部标语
        h += 16.dpToPx()
        h += 16.dpToPx()
        // 底部留白
        h += 24.dpToPx()

        PaintPool.recycle(paint)
        return h
    }

    /**
     * 绘制藏书票
     */
    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val width = ChapterProvider.visibleWidth.toFloat()
        val height = ChapterProvider.visibleHeight.toFloat()

        // 计算藏书票尺寸和位置（居中显示）
        val bpWidth = width * 0.8f
        val bpHeight = measureHeight(book, bpWidth)
        val left = (width - bpWidth) / 2f + ChapterProvider.paddingLeft
        val top = (height - bpHeight) / 2f + ChapterProvider.paddingTop
        val right = left + bpWidth
        val bottom = top + bpHeight

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true

        // 获取主题色
        val primaryColor = ThemeStore.primaryColor(appCtx)
        val dividerColor = ThemeStore.dividerColor(appCtx)
        val textColor = ThemeStore.textColorPrimary(appCtx)

        // 绘制阴影
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx(), top + 4.dpToPx(), right + 4.dpToPx(), bottom + 4.dpToPx(), paint)

        // 绘制背景（使用主题主色调）
        paint.color = primaryColor
        canvas.drawRect(left, top, right, bottom, paint)

        // 绘制顶部和底部的虚线边框
        paint.color = dividerColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.dpToPx().toFloat()
        paint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        canvas.drawLine(left, top, right, top, paint)
        canvas.drawLine(left, bottom, right, bottom, paint)
        paint.pathEffect = null

        // 绘制内容
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.typeface = Typeface.MONOSPACE

        var currentY = top + 40.dpToPx()

        // 标题：Reading Certificate
        paint.textSize = 18.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val titleText1 = "Reading Certificate"
        val titleWidth1 = paint.measureText(titleText1)
        canvas.drawText(titleText1, left + (bpWidth - titleWidth1) / 2f, currentY, paint)

        currentY += 22.dpToPx()
        paint.textSize = 14.dpToPx().toFloat()
        paint.isFakeBoldText = false
        val titleText2 = "=== 阅 读 凭 证 ==="
        val titleWidth2 = paint.measureText(titleText2)
        canvas.drawText(titleText2, left + (bpWidth - titleWidth2) / 2f, currentY, paint)

        currentY += 30.dpToPx()

        // 辅助函数：绘制一行（左右对齐）
        val drawRow = { rowTitle: String, value: String, y: Float ->
            paint.isFakeBoldText = false
            paint.color = textColor
            paint.style = Paint.Style.FILL
            canvas.drawText(rowTitle, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            canvas.drawText(value, right - 20.dpToPx() - valWidth, y, paint)
        }

        // 辅助函数：绘制虚线分隔线
        val drawDivider = { y: Float ->
            paint.color = dividerColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
            canvas.drawLine(left + 20.dpToPx(), y, right - 20.dpToPx(), y, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.color = textColor
        }

        // 辅助函数：绘制清单一行（带虚线连接）
        val drawListRow = { rowTitle: String, value: String, y: Float ->
            paint.color = textColor
            paint.style = Paint.Style.FILL
            canvas.drawText(rowTitle, left + 20.dpToPx(), y, paint)
            val valWidth = paint.measureText(value)
            val valueX = right - 20.dpToPx() - valWidth
            canvas.drawText(value, valueX, y, paint)
            val titleW = paint.measureText(rowTitle)
            val dashStartX = left + 20.dpToPx() + titleW + 5.dpToPx()
            val dashEndX = valueX - 5.dpToPx()
            if (dashEndX > dashStartX) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.dpToPx().toFloat()
                paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                val textMiddleY = y - paint.textSize / 3f
                canvas.drawLine(dashStartX, textMiddleY, dashEndX, textMiddleY, paint)
                paint.pathEffect = null
                paint.style = Paint.Style.FILL
                paint.color = textColor
            }
        }

        // 开始时间
        val startTime = if (book.firstReadTime > 0) book.firstReadTime else book.durChapterTime
        val addTimeStr = if (startTime > 0) dateFormat.format(Date(startTime)) else "____/__/__"
        drawRow("开始时间", addTimeStr, currentY)

        currentY += 22.dpToPx()

        // 结束时间
        val finishTimeStr = if (book.finishReadTime > 0) dateFormat.format(Date(book.finishReadTime)) else "____/__/__"
        drawRow("结束时间", finishTimeStr, currentY)

        currentY += 22.dpToPx()
        drawDivider(currentY)
        currentY += 22.dpToPx()

        // 书名（过长时截断）
        var displayBookName = book.name
        val maxNameWidth = bpWidth - 40.dpToPx() - paint.measureText("书名") - 25.dpToPx()
        if (paint.measureText(displayBookName) > maxNameWidth) {
            val ellipsizeWidth = paint.measureText("...")
            while (displayBookName.isNotEmpty() && paint.measureText(displayBookName) + ellipsizeWidth > maxNameWidth) {
                displayBookName = displayBookName.substring(0, displayBookName.length - 1)
            }
            displayBookName += "..."
        }
        drawListRow("书名", displayBookName, currentY)
        currentY += 22.dpToPx()

        // 书摘数量
        val annotationCount = appDb.bookAnnotationDao.getByBook(book.name, book.author).size
        val annotationStr = "$annotationCount"
        drawListRow("书摘条数", annotationStr, currentY)
        currentY += 22.dpToPx()

        // 阅读时间（实际累计阅读时长，不到一天换算成小时/分钟）
        val totalReadMillis = run {
            var time = appDb.readSessionDao.getTotalReadTimeByNameAndAuthorSync(book.name, book.author)
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTimeByBookNameSync(book.name)
            }
            if (time == null || time == 0L) {
                time = appDb.readSessionDao.getTotalReadTimeByUrlSync(book.bookUrl)
            }
            time ?: 0L
        }
        val readingTimeStr = if (totalReadMillis > 0) {
            val totalMinutes = totalReadMillis / (60 * 1000L)
            val days = totalMinutes / (24 * 60)
            val hours = totalMinutes / 60
            when {
                days > 0 -> "${days} 天"
                hours > 0 -> "${hours} 小时"
                else -> "${totalMinutes} 分钟"
            }
        } else {
            "0 分钟"
        }
        drawListRow("阅读时间", readingTimeStr, currentY)

        currentY += 22.dpToPx()
        drawDivider(currentY)
        currentY += 22.dpToPx()
        
        // 阅读评分
        paint.isFakeBoldText = false
        paint.textSize = 12.dpToPx().toFloat()
        paint.color = textColor
        paint.style = Paint.Style.FILL
        canvas.drawText("阅读评分", left + 20.dpToPx(), currentY, paint)

        // 绘制星星：统一文字颜色，选中的实心★，未选中的空心☆，大小一致
        val starSize = 14.dpToPx().toFloat()
        val starGap = 4.dpToPx().toFloat()
        paint.textSize = starSize
        val totalStarWidth = starSize * 5 + starGap * 4
        var starDrawX = right - 20.dpToPx() - totalStarWidth

        for (i in 0 until 5) {
            paint.color = textColor
            paint.isFakeBoldText = false
            val starChar = if (book.rating >= i + 1) "★" else "☆"
            canvas.drawText(starChar, starDrawX, currentY, paint)
            starDrawX += starSize + starGap
        }
        paint.color = textColor
        paint.textSize = 12.dpToPx().toFloat()
        paint.isFakeBoldText = false

        // 记录评分区域的点击范围
        ratingRect.set(right - 20.dpToPx() - totalStarWidth, currentY - 24.dpToPx(), right - 20.dpToPx(), currentY + 10.dpToPx())

        currentY += 24.dpToPx()

        // 书评内容区域
        val reviewContent = getReviewContent(book)
        if (!reviewContent.isNullOrBlank()) {
            paint.isFakeBoldText = false
            paint.textSize = 12.dpToPx().toFloat()
            paint.color = textColor
            val maxWidth = bpWidth - 40.dpToPx()
            val paragraphs = reviewContent.split("\n")

            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    currentY += 12.dpToPx()
                    continue
                }
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    if (paint.measureText(remainingText) <= maxWidth) {
                        canvas.drawText(remainingText, left + 20.dpToPx(), currentY, paint)
                        currentY += 18.dpToPx()
                        break
                    } else {
                        var cutIndex = remainingText.length
                        while (cutIndex > 0 && paint.measureText(remainingText.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        if (cutIndex == 0) cutIndex = 1
                        canvas.drawText(remainingText.substring(0, cutIndex), left + 20.dpToPx(), currentY, paint)
                        currentY += 18.dpToPx()
                        remainingText = remainingText.substring(cutIndex)
                    }
                }
            }
            currentY += 16.dpToPx()
            drawDivider(currentY)
            currentY += 22.dpToPx() // 书评和底部标语之间的间隙
        }

        // 底部标语
        paint.color = ColorUtils.withAlpha(textColor, 0.7f)
        paint.textSize = 9.dpToPx().toFloat()
        paint.isFakeBoldText = false

        val footer1 = "BAD READS, NO RECEIPTS; GOOD READS, ON REPEAT."
        val footer2 = "烂书不退款，好书请多读。"
        val f1Width = paint.measureText(footer1)
        val f2Width = paint.measureText(footer2)
        canvas.drawText(footer1, left + (bpWidth - f1Width) / 2f, currentY, paint)
        currentY += 16.dpToPx()
        canvas.drawText(footer2, left + (bpWidth - f2Width) / 2f, currentY, paint)

        PaintPool.recycle(paint)
    }

    /**
     * 处理点击事件（藏书票仅做展示，不再弹窗）
     */
    fun onClick(context: Context, x: Float, y: Float, textPage: TextPage, book: Book, relativeOffset: Float): Boolean {
        // 藏书票不再拦截任何点击，纯展示
        return false
    }
}
