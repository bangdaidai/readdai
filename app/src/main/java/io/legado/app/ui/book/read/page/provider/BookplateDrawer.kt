package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longToastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookplateDrawer {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private var ratingRect = RectF()

    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val width = ChapterProvider.visibleWidth.toFloat()
        val height = ChapterProvider.visibleHeight.toFloat()
        
        // 获取主题颜色
        val bgColor = ThemeStore.backgroundCard(appCtx)
        val textPrimary = ThemeStore.textColorPrimary(appCtx)
        val textSecondary = ThemeStore.textColorSecondary(appCtx)
        val dividerColor = ThemeStore.dividerColor(appCtx)
        val primaryColor = ThemeStore.primaryColor(appCtx)
        
        // 计算高度：基础高度 + 书评占用的高度
        val baseHeight = 385.dpToPx().toFloat()
        var extraHeight = if (!book.reviewContent.isNullOrBlank()) {
            // 粗略估算书评占用的高度
            val lines = book.reviewContent?.split("\n")?.size ?: 1
            (lines * 18 + 20).dpToPx().toFloat()
        } else {
            0f
        }
        
        val bpWidth = width * 0.8f
        val bpHeight = baseHeight + extraHeight
        val left = (width - bpWidth) / 2f + ChapterProvider.paddingLeft
        val top = (height - bpHeight) / 2f + ChapterProvider.paddingTop
        val right = left + bpWidth
        val bottom = top + bpHeight

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        
        // Draw shadow
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx(), top + 4.dpToPx(), right + 4.dpToPx(), bottom + 4.dpToPx(), paint)
        
        // Draw background
        paint.color = bgColor
        canvas.drawRect(left, top, right, bottom, paint)
        
        // Draw dashed top/bottom borders
        paint.color = dividerColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.dpToPx().toFloat()
        paint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        canvas.drawLine(left, top, right, top, paint)
        canvas.drawLine(left, bottom, right, bottom, paint)
        paint.pathEffect = null

        // Draw content
        paint.style = Paint.Style.FILL
        paint.color = textPrimary
        paint.typeface = Typeface.MONOSPACE
        
        var currentY = top + 40.dpToPx()
        
        // Title
        paint.textSize = 18.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val titleText = "Reading Certificate"
        val titleWidth = paint.measureText(titleText)
        canvas.drawText(titleText, left + (bpWidth - titleWidth) / 2f, currentY, paint)
        
        currentY += 25.dpToPx()
        paint.textSize = 14.dpToPx().toFloat()
        paint.isFakeBoldText = false
        val subtitleText = "=== 阅 读 凭 证 ==="
        val subtitleWidth = paint.measureText(subtitleText)
        canvas.drawText(subtitleText, left + (bpWidth - subtitleWidth) / 2f, currentY, paint)
        
        currentY += 30.dpToPx()
        
        val drawRow = { title: String, value: String, y: Float, isBold: Boolean ->
            paint.isFakeBoldText = isBold
            paint.color = textSecondary
            canvas.drawText(title, left + 20.dpToPx(), y, paint)
            paint.color = textPrimary
            val valWidth = paint.measureText(value)
            canvas.drawText(value, right - 20.dpToPx() - valWidth, y, paint)
        }
        
        val drawDivider = { y: Float ->
            paint.color = dividerColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.dpToPx().toFloat()
            paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
            canvas.drawLine(left + 20.dpToPx(), y, right - 20.dpToPx(), y, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.color = textPrimary
        }
        
        paint.textSize = 12.dpToPx().toFloat()
        
        // Add Time - 获取首次阅读时间（使用同步方法）
        val firstReadTime = appDb.readSessionDao.getFirstReadTimeByBookSync(book.bookUrl) ?: book.firstReadTime
        val earliestStartTime = if (firstReadTime > 0) firstReadTime else book.firstReadTime
        val trueStartTime = if (earliestStartTime > 0) earliestStartTime else System.currentTimeMillis()
        
        val addTimeStr = if (trueStartTime > 0) dateFormat.format(Date(trueStartTime)) else "____/__/__"
        drawRow("开始时间", addTimeStr, currentY, false)
        
        currentY += 25.dpToPx()
        
        // 完成时间 - 藏书票只有读完才显示，所以肯定有完成时间
        val finishTimeStr = if (book.finishReadTime > 0) dateFormat.format(Date(book.finishReadTime)) else dateFormat.format(Date())
        drawRow("完成时间", finishTimeStr, currentY, false)
        
        currentY += 25.dpToPx()
        
        paint.textSize = 14.dpToPx().toFloat()
        
        // 结算清单标题
        paint.isFakeBoldText = true
        paint.color = primaryColor
        val listTitle = "- 结 算 清 单 -"
        val listTitleWidth = paint.measureText(listTitle)
        canvas.drawText(listTitle, left + (bpWidth - listTitleWidth) / 2f, currentY, paint)
        currentY += 25.dpToPx()
        
        paint.isFakeBoldText = false
        paint.color = textPrimary
        
        val drawListRow = { title: String, value: String, y: Float ->
            paint.color = textSecondary
            canvas.drawText(title, left + 20.dpToPx(), y, paint)
            paint.color = textPrimary
            val valWidth = paint.measureText(value)
            val valueX = right - 20.dpToPx() - valWidth
            canvas.drawText(value, valueX, y, paint)
            
            val titleWidth = paint.measureText(title)
            val dashStartX = left + 20.dpToPx() + titleWidth + 5.dpToPx()
            val dashEndX = valueX - 5.dpToPx()
            if (dashEndX > dashStartX) {
                val oldStyle = paint.style
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.dpToPx().toFloat()
                paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                val textMiddleY = y - paint.textSize / 3f
                canvas.drawLine(dashStartX, textMiddleY, dashEndX, textMiddleY, paint)
                paint.pathEffect = null
                paint.style = oldStyle
            }
        }
        
        // Book Name
        var displayBookName = book.name
        val maxNameWidth = bpWidth - 40.dpToPx() - paint.measureText("书名  ") - 20.dpToPx()
        if (paint.measureText(displayBookName) > maxNameWidth) {
            val ellipsizeWidth = paint.measureText("...")
            while (displayBookName.isNotEmpty() && paint.measureText(displayBookName) + ellipsizeWidth > maxNameWidth) {
                displayBookName = displayBookName.substring(0, displayBookName.length - 1)
            }
            displayBookName += "..."
        }
        drawListRow("书名", displayBookName, currentY)
        currentY += 20.dpToPx()
        
        // Note count - 使用 bookAnnotationDao
        val noteCount = appDb.bookAnnotationDao.getByBook(book.name, book.author).size
        val noteStr = if (noteCount > 0) "$noteCount" else "?"
        drawListRow("书摘条数", noteStr, currentY)
        currentY += 20.dpToPx()
        
        // Reading time - 使用 ReadSession 的总阅读时长（毫秒）转换为天
        val totalReadMillis = appDb.readSessionDao.getTotalReadTimeByUrlSync(book.bookUrl) ?: 0L
        val readingTimeStr = if (totalReadMillis > 0) {
            val days = totalReadMillis / (24 * 60 * 60 * 1000L)
            if (days > 0) "$days 天" else "${totalReadMillis / (60 * 60 * 1000L)} 小时"
        } else {
            "? 天"
        }
        drawListRow("阅读时间", readingTimeStr, currentY)
        
        currentY += 20.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        // Rating
        paint.color = textSecondary
        canvas.drawText("阅读打分", left + 20.dpToPx(), currentY, paint)
        
        val starsStr = "[ ☆ ☆ ☆ ☆ ☆ ]"
        val starsWidth = paint.measureText(starsStr)
        val starsX = right - 20.dpToPx() - starsWidth
        paint.color = textPrimary
        canvas.drawText(starsStr, starsX, currentY, paint)
        
        ratingRect.set(starsX, currentY - 20.dpToPx(), starsX + starsWidth, currentY + 10.dpToPx())
        
        // Draw filled stars
        paint.color = primaryColor
        val starWidth = paint.measureText("☆ ")
        val bracketWidth = paint.measureText("[ ")
        for (i in 0 until 5) {
            if (book.rating >= i + 1) {
                canvas.drawText("★", starsX + bracketWidth + i * starWidth, currentY, paint)
            }
        }
        paint.color = textPrimary
        
        currentY += 20.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        // 书评内容区域
        if (!book.reviewContent.isNullOrBlank()) {
            paint.isFakeBoldText = true
            paint.color = primaryColor
            paint.textSize = 12.dpToPx().toFloat()
            canvas.drawText("📝 我的书评", left + 20.dpToPx(), currentY, paint)
            currentY += 20.dpToPx()
            
            paint.isFakeBoldText = false
            paint.color = textPrimary
            paint.textSize = 11.dpToPx().toFloat()
            
            // 绘制完整书评内容（支持多行）
            val reviewText = book.reviewContent ?: ""
            val maxWidth = bpWidth - 40.dpToPx()
            
            // 按换行符分割
            val paragraphs = reviewText.split("\n")
            
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    // 空行，增加间距
                    currentY += 10.dpToPx()
                    continue
                }
                
                // 处理长文本自动换行
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    var lineWidth = paint.measureText(remainingText)
                    if (lineWidth <= maxWidth) {
                        // 整行可以放下
                        canvas.drawText(remainingText, left + 20.dpToPx(), currentY, paint)
                        currentY += 18.dpToPx()
                        break
                    } else {
                        // 需要截断
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
            
            currentY += 10.dpToPx()
            drawDivider(currentY)
            currentY += 20.dpToPx()
        }
        
        // Footer
        paint.color = textSecondary
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

    fun onClick(context: Context, x: Float, y: Float, textPage: TextPage, book: Book, relativeOffset: Float): Boolean {
        if (!textPage.isBookplateStart && !textPage.isBookplateEnd) return false
        
        val realY = y - relativeOffset
        
        if (ratingRect.contains(x, realY)) {
            if (textPage.isBookplateStart && book.finishReadTime <= 0) {
                appCtx.longToastOnUi("请完读后再打分")
            } else {
                showRatingDialog(context, book)
            }
            return true
        }
        
        return false
    }
    
    private fun showRatingDialog(context: Context, book: Book) {
        // 创建对话框视图
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bookplate_rating, null)
        
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rating_bar)
        val etReview = dialogView.findViewById<EditText>(R.id.et_review)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        
        // 设置当前评分
        ratingBar.rating = book.rating
        
        // 加载已有书评
        etReview.setText(book.reviewContent ?: "")
        
        AlertDialog.Builder(context)
            .setView(dialogView)
            .setTitle("阅读评价")
            .setPositiveButton("保存") { _, _ ->
                // 保存评分
                val newRating = ratingBar.rating
                book.rating = newRating
                book.userModifiedRating = true
                
                // 保存书评
                val reviewContent = etReview.text.toString().trim()
                book.reviewContent = if (reviewContent.isBlank()) null else reviewContent
                
                // 异步保存到数据库
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        appDb.bookDao.update(book)
                        CoroutineScope(Dispatchers.Main).launch {
                            context.longToastOnUi("已保存评价")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
