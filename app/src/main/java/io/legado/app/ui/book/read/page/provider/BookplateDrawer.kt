package io.legado.app.ui.book.read.page.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.PaintPool
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.ReadBook
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

    /**
     * 创建藏书票视图（用于在阅读详情页显示）
     */
    @Suppress("DEPRECATION")
    fun createBookplateView(context: Context, book: Book): View {
        // 先计算实际需要的高度
        val bpWidth = 320.dpToPx()
        val (_, totalHeight) = calculateBookplateDimensions(book, bpWidth.toFloat())
        val bpHeight = totalHeight.toInt()

        // 创建自定义视图
        return object : View(context) {
            private val bitmap: Bitmap

            init {
                // 创建位图
                bitmap = Bitmap.createBitmap(bpWidth, bpHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawBookplate(canvas, book, bpWidth.toFloat(), totalHeight)
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(bpWidth, bpHeight)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        }
    }

    /**
     * 计算藏书票的尺寸
     */
    fun calculateBookplateDimensions(book: Book, bpWidth: Float): Pair<Float, Float> {
        val context = appCtx
        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        paint.textSize = 12.dpToPx().toFloat()

        var height = 40.dpToPx().toFloat() // 顶部边距

        // 标题高度
        height += 18.dpToPx() + 25.dpToPx() // Reading Certificate + 阅 读 凭 证 + 间距
        height += 30.dpToPx() // 额外间距

        // 开始时间
        height += 25.dpToPx()
        // 完成时间
        height += 25.dpToPx()
        // 调整
        height += 25.dpToPx() // 文字高度 + 分隔线 + 间距
        height += 30.dpToPx()

        // 书名
        height += 20.dpToPx()
        // 书摘条数
        height += 20.dpToPx()
        // 阅读时间
        height += 20.dpToPx()
        // 分隔线
        height += 30.dpToPx()

        // 评分
        height += 20.dpToPx()
        // 评分和书评之间间距
        height += 40.dpToPx()

        // 书评内容
        if (!book.reviewContent.isNullOrBlank()) {
            val reviewText = book.reviewContent ?: ""
            val maxWidth = bpWidth - 40.dpToPx()
            val paragraphs = reviewText.split("\n")
            
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    height += 10.dpToPx()
                    continue
                }
                var remainingText = paragraph
                while (remainingText.isNotEmpty()) {
                    var lineWidth = paint.measureText(remainingText)
                    if (lineWidth <= maxWidth) {
                        height += 18.dpToPx()
                        break
                    } else {
                        var cutIndex = remainingText.length
                        while (cutIndex > 0 && paint.measureText(remainingText.substring(0, cutIndex)) > maxWidth) {
                            cutIndex--
                        }
                        if (cutIndex == 0) cutIndex = 1
                        height += 18.dpToPx()
                        remainingText = remainingText.substring(cutIndex)
                    }
                }
            }
            height += 10.dpToPx() + 20.dpToPx() // 分隔线 + 间距
        }

        // Footer
        height += 16.dpToPx() + 16.dpToPx() // 两行文字 + 间距
        height += 40.dpToPx() // 底部边距

        PaintPool.recycle(paint)
        return Pair(bpWidth, height)
    }

    fun drawBookplate(canvas: Canvas, book: Book, bpWidth: Float, bpHeight: Float) {
        val context = appCtx
        
        // 获取主题颜色
        val bgColor = ThemeStore.backgroundCard(context)
        val textPrimary = ThemeStore.textColorPrimary(context)
        val textSecondary = ThemeStore.textColorSecondary(context)
        val dividerColor = ThemeStore.dividerColor(context)

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true

        val left = 0f
        val top = 0f
        val right = bpWidth
        val bottom = bpHeight

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
        
        val drawRow = { t: String, v: String, y: Float, isBold: Boolean ->
            paint.isFakeBoldText = isBold
            paint.color = textSecondary
            canvas.drawText(t, left + 20.dpToPx(), y, paint)
            paint.color = textPrimary
            val valWidth = paint.measureText(v)
            canvas.drawText(v, right - 20.dpToPx() - valWidth, y, paint)
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
        
        // Add Time
        val firstReadTime = appDb.readSessionDao.getFirstReadTimeByBookSync(book.bookUrl) ?: book.firstReadTime
        val earliestStartTime = if (firstReadTime > 0) firstReadTime else book.firstReadTime
        val trueStartTime = if (earliestStartTime > 0) earliestStartTime else System.currentTimeMillis()
        
        val addTimeStr = if (trueStartTime > 0) dateFormat.format(Date(trueStartTime)) else "____/__/__"
        drawRow("开始时间", addTimeStr, currentY, false)
        
        currentY += 25.dpToPx()
        
        // 完成时间
        val finishTimeStr = if (book.finishReadTime > 0) dateFormat.format(Date(book.finishReadTime)) else dateFormat.format(Date())
        drawRow("完成时间", finishTimeStr, currentY, false)
        
        currentY += 25.dpToPx()
        
        paint.textSize = 14.dpToPx().toFloat()
        
        // 在完成时间后直接加虚线
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
        paint.isFakeBoldText = false
        paint.color = textPrimary
        
        val drawListRow = { t: String, v: String, y: Float ->
            paint.color = textSecondary
            canvas.drawText(t, left + 20.dpToPx(), y, paint)
            paint.color = textPrimary
            val valWidth = paint.measureText(v)
            val valueX = right - 20.dpToPx() - valWidth
            canvas.drawText(v, valueX, y, paint)
            
            val titleWidth = paint.measureText(t)
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
        
        // Note count
        val noteCount = appDb.bookAnnotationDao.getByBook(book.name, book.author).size
        val noteStr = if (noteCount > 0) "$noteCount" else "?"
        drawListRow("书摘条数", noteStr, currentY)
        currentY += 20.dpToPx()
        
        // Reading time
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
        currentY += 40.dpToPx()
        
        // Rating
        paint.color = textSecondary
        canvas.drawText("阅读打分", left + 20.dpToPx(), currentY, paint)
        
        val starsStr = "[ ☆ ☆ ☆ ☆ ☆ ]"
        val starsWidth = paint.measureText(starsStr)
        val starsX = right - 20.dpToPx() - starsWidth
        paint.color = textPrimary
        canvas.drawText(starsStr, starsX, currentY, paint)
        
        // Draw filled stars - 用同样的文字颜色
        val starWidth = paint.measureText("☆ ")
        val bracketWidth = paint.measureText("[ ")
        for (i in 0 until 5) {
            if (book.rating >= i + 1) {
                canvas.drawText("★", starsX + bracketWidth + i * starWidth, currentY, paint)
            }
        }
        paint.color = textPrimary
        
        currentY += 40.dpToPx()
        
        // 书评内容区域
        if (!book.reviewContent.isNullOrBlank()) {
            paint.isFakeBoldText = false
            paint.color = textPrimary
            paint.textSize = 12.dpToPx().toFloat()
            
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

    fun draw(canvas: Canvas, textPage: TextPage, book: Book) {
        val width = ChapterProvider.visibleWidth.toFloat()
        val height = ChapterProvider.visibleHeight.toFloat()
        
        // 获取主题颜色
        val bgColor = ThemeStore.backgroundCard(appCtx)
        val dividerColor = ThemeStore.dividerColor(appCtx)
        
        // 计算藏书票尺寸
        val bpWidth = width * 0.8f
        val (_, bpHeight) = calculateBookplateDimensions(book, bpWidth)
        
        val left = (width - bpWidth) / 2f + ChapterProvider.paddingLeft
        val top = (height - bpHeight) / 2f + ChapterProvider.paddingTop

        // 创建离屏位图来绘制藏书票
        val bitmap = Bitmap.createBitmap(bpWidth.toInt(), bpHeight.toInt(), Bitmap.Config.ARGB_8888)
        val offscreenCanvas = Canvas(bitmap)
        
        // 绘制藏书票到离屏画布
        drawBookplate(offscreenCanvas, book, bpWidth, bpHeight)
        
        // 绘制阴影和背景
        val paint = PaintPool.obtain()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx(), top + 4.dpToPx(), left + bpWidth + 4.dpToPx(), top + bpHeight + 4.dpToPx(), paint)
        
        // 绘制藏书票到主画布
        canvas.drawBitmap(bitmap, left, top, null)
        
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
    
    @JvmStatic
    fun showRatingDialog(context: Context, book: Book) {
        // 创建对话框视图
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bookplate_rating, null)
        
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rating_bar)
        val etReview = dialogView.findViewById<EditText>(R.id.et_review)
        
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
                val reviewContentStr = etReview.text.toString().trim()
                book.reviewContent = if (reviewContentStr.isBlank()) null else reviewContentStr
                
                // 异步保存到数据库
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. 保存到Book表
                        appDb.bookDao.update(book)
                        
                        // 2. 保存到BookReview表
                        val reviewContentToSave = book.reviewContent
                        val existingReview = appDb.bookReviewDao.getReviewByBookUrl(book.bookUrl)
                        if (existingReview.isNotEmpty()) {
                            // 更新已有书评
                            val updatedReview = existingReview[0].copy(
                                reviewContent = reviewContentToSave ?: "",
                                updateTime = System.currentTimeMillis()
                            )
                            appDb.bookReviewDao.update(updatedReview)
                        } else if (!reviewContentToSave.isNullOrBlank()) {
                            // 创建新书评
                            val newReview = io.legado.app.data.entities.BookReview(
                                bookUrl = book.bookUrl,
                                bookName = book.name,
                                bookAuthor = book.author,
                                reviewContent = reviewContentToSave,
                                createTime = System.currentTimeMillis(),
                                updateTime = System.currentTimeMillis()
                            )
                            appDb.bookReviewDao.insert(newReview)
                        } else {
                            // 如果书评为空且没有现有记录，则不创建
                        }
                        
                        // 3. 同步更新ReadingMemory表
                        val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                        if (memory != null) {
                            val updatedMemory = memory.copy(reviewContent = reviewContentToSave)
                            appDb.readingMemoryDao.update(updatedMemory)
                        }
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            context.longToastOnUi("已保存评价")
                            // 保存成功后，显示藏书票
                            ReadBook.showBookplate = 1
                            ReadBook.callBack?.upContent()
                            // 发送书评更新事件，让书架和阅读详情刷新
                            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.BOOK_REVIEW_UPDATED, book.bookUrl)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .setNegativeButton("取消") { _, _ ->
                // 用户取消，仍显示藏书票（不带评分）
                ReadBook.showBookplate = 1
                ReadBook.callBack?.upContent()
            }
            .setOnDismissListener {
                // 对话框消失时，如果还没有显示藏书票，则显示
                if (ReadBook.showBookplate != 1) {
                    ReadBook.showBookplate = 1
                    ReadBook.callBack?.upContent()
                }
            }
            .show()
    }
}
