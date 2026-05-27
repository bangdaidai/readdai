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
        // 藏书票尺寸
        val bpWidth = 320.dpToPx()
        var baseHeight = 385.dpToPx()
        var extraHeight = if (!book.reviewContent.isNullOrBlank()) {
            val lines = book.reviewContent?.split("\n")?.size ?: 1
            (lines * 18 + 20).dpToPx()
        } else {
            0
        }
        val bpHeight = baseHeight + extraHeight

        // 创建自定义视图
        return object : View(context) {
            private val bitmap: Bitmap

            init {
                // 创建位图
                bitmap = Bitmap.createBitmap(bpWidth, bpHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawBookplate(canvas, book, bpWidth, bpHeight)
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

    private fun drawBookplate(canvas: Canvas, book: Book, bpWidth: Int, bpHeight: Int) {
        val context = appCtx
        
        // 获取主题颜色
        val bgColor = ThemeStore.backgroundCard(context)
        val textPrimary = ThemeStore.textColorPrimary(context)
        val textSecondary = ThemeStore.textColorSecondary(context)
        val dividerColor = ThemeStore.dividerColor(context)
        val primaryColor = ThemeStore.primaryColor(context)

        val paint = PaintPool.obtain()
        paint.isAntiAlias = true

        val left = 0f
        val top = 0f
        val right = bpWidth.toFloat()
        val bottom = bpHeight.toFloat()
        val currentY = 0f

        // Draw shadow
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.FILL
        canvas.drawRect(left + 4.dpToPx().toFloat(), top + 4.dpToPx().toFloat(), right + 4.dpToPx().toFloat(), bottom + 4.dpToPx().toFloat(), paint)

        // Draw background
        paint.color = bgColor
        canvas.drawRect(left, top, right, bottom, paint)

        // Draw border
        paint.color = primaryColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.dpToPx().toFloat()
        canvas.drawRect(left + 10.dpToPx().toFloat(), top + 10.dpToPx().toFloat(), right - 10.dpToPx().toFloat(), bottom - 10.dpToPx().toFloat(), paint)

        // 重置 paint
        paint.style = Paint.Style.FILL

        // 计算实际绘制区域
        val contentLeft = left + 20.dpToPx()
        val contentRight = right - 20.dpToPx()
        var y = 30.dpToPx().toFloat()

        // Draw title
        paint.color = primaryColor
        paint.textSize = 16.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val title = "藏书票"
        val titleWidth = paint.measureText(title)
        canvas.drawText(title, (bpWidth - titleWidth) / 2f, y, paint)

        // Draw divider under title
        y += 15.dpToPx()
        paint.color = dividerColor
        paint.strokeWidth = 1.dpToPx().toFloat()
        canvas.drawLine(contentLeft, y, contentRight, y, paint)

        y += 25.dpToPx()

        // Draw book name
        paint.color = textPrimary
        paint.textSize = 14.dpToPx().toFloat()
        paint.isFakeBoldText = true
        val bookName = book.name
        canvas.drawText(bookName, contentLeft, y, paint)

        y += 20.dpToPx()

        // Note count
        paint.color = textSecondary
        paint.textSize = 12.dpToPx().toFloat()
        paint.isFakeBoldText = false
        val noteCount = appDb.bookAnnotationDao.getByBook(book.name, book.author).size
        val noteStr = if (noteCount > 0) "$noteCount" else "?"
        canvas.drawText("书摘条数: $noteStr", contentLeft, y, paint)

        y += 20.dpToPx()

        // Reading time
        val totalReadMillis = appDb.readSessionDao.getTotalReadTimeByUrlSync(book.bookUrl) ?: 0L
        val readingTimeStr = if (totalReadMillis > 0) {
            val days = totalReadMillis / (24 * 60 * 60 * 1000L)
            if (days > 0) "$days 天" else "${totalReadMillis / (60 * 60 * 1000L)} 小时"
        } else {
            "? 天"
        }
        canvas.drawText("阅读时间: $readingTimeStr", contentLeft, y, paint)

        y += 20.dpToPx()

        // Rating
        paint.color = textSecondary
        canvas.drawText("阅读打分", contentLeft, y, paint)

        val starsStr = "[ ☆ ☆ ☆ ☆ ☆ ]"
        val starsWidth = paint.measureText(starsStr)
        val starsX = contentRight - starsWidth
        paint.color = textPrimary
        canvas.drawText(starsStr, starsX, y, paint)

        paint.color = primaryColor
        val starWidth = paint.measureText("☆ ")
        val bracketWidth = paint.measureText("[ ")
        for (i in 0 until 5) {
            if (book.rating >= i + 1) {
                canvas.drawText("★", starsX + bracketWidth + i * starWidth, y, paint)
            }
        }
        paint.color = textPrimary

        y += 20.dpToPx()

        // 书评内容
        if (!book.reviewContent.isNullOrBlank()) {
            paint.isFakeBoldText = false
            paint.color = textPrimary
            paint.textSize = 12.dpToPx().toFloat()
            
            // 换行显示书评
            val maxWidth = contentRight - contentLeft
            val lines = wrapText(book.reviewContent!!, paint, maxWidth.toFloat())
            for (line in lines) {
                canvas.drawText(line, contentLeft, y, paint)
                y += 18.dpToPx().toFloat()
            }
        }

        // Draw divider before footer
        y += 10.dpToPx()
        paint.color = dividerColor
        canvas.drawLine(contentLeft, y, contentRight, y, paint)

        y += 20.dpToPx()

        // Footer
        paint.color = textSecondary
        paint.textSize = 10.dpToPx().toFloat()
        val footer1 = "「读万卷书，行万里路」"
        val footer2 = "—— ${dateFormat.format(Date())}"
        val f1Width = paint.measureText(footer1)
        val f2Width = paint.measureText(footer2)
        canvas.drawText(footer1, (bpWidth - f1Width) / 2f, y, paint)
        y += 16.dpToPx()
        canvas.drawText(footer2, (bpWidth - f2Width) / 2f, y, paint)

        PaintPool.recycle(paint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }
            
            val words = paragraph.toCharArray()
            var currentLine = StringBuilder()
            
            for (char in words) {
                val testLine = currentLine.toString() + char
                val testWidth = paint.measureText(testLine)
                
                if (testWidth > maxWidth) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder()
                    }
                    // 如果单个字符就超过宽度，直接添加
                    if (paint.measureText(char.toString()) > maxWidth) {
                        lines.add(char.toString())
                    } else {
                        currentLine.append(char)
                    }
                } else {
                    currentLine.append(char)
                }
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        
        return lines
    }

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
        val listTitle = "- 藏 书 票 -"
        val listTitleWidth = paint.measureText(listTitle)
        canvas.drawText(listTitle, left + (bpWidth - listTitleWidth) / 2f, currentY, paint)
        currentY += 25.dpToPx()
        
        paint.isFakeBoldText = false
        paint.color = textPrimary
        
        // 在标题后加虚线
        currentY += 10.dpToPx()
        drawDivider(currentY)
        currentY += 30.dpToPx()
        
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
