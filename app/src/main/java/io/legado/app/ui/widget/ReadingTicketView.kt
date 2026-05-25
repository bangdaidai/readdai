package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RatingBar
import android.widget.TextView
import io.legado.app.R
import io.legado.app.data.entities.ReadingTicket
import io.legado.app.help.book.ReadingTicketHelper
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 阅读小票视图
 * 在书籍读完时显示在阅读页尾部
 */
class ReadingTicketView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tvBookName: TextView
    private val tvAuthor: TextView
    private val tvReadTime: TextView
    private val tvReadCount: TextView
    private val tvProgress: TextView
    private val tvAnnotationCount: TextView
    private val ratingBar: RatingBar
    private val etReview: EditText
    private val tvFinishTime: TextView

    private var bookUrl: String = ""
    private var onRatingChanged: ((Float) -> Unit)? = null
    private var currentBook: io.legado.app.data.entities.Book? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_reading_ticket, this, true)

        tvBookName = findViewById(R.id.tv_book_name)
        tvAuthor = findViewById(R.id.tv_author)
        tvReadTime = findViewById(R.id.tv_read_time)
        tvReadCount = findViewById(R.id.tv_read_count)
        tvProgress = findViewById(R.id.tv_progress)
        tvAnnotationCount = findViewById(R.id.tv_annotation_count)
        ratingBar = findViewById(R.id.rating_bar)
        etReview = findViewById(R.id.et_review)
        tvFinishTime = findViewById(R.id.tv_finish_time)

        // 监听评分变化
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            if (bookUrl.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    ReadingTicketHelper.updateRating(bookUrl, rating)
                    onRatingChanged?.invoke(rating)
                }
            }
        }
        
        // 监听书评变化（使用防抖）
        var reviewDebounceJob: kotlinx.coroutines.Job? = null
        etReview.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                reviewDebounceJob?.cancel()
                reviewDebounceJob = CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(500) // 500ms 防抖
                    s?.toString()?.let { reviewContent ->
                        saveReview(reviewContent)
                    }
                }
            }
        })
    }

    /**
     * 加载并显示阅读小票
     */
    fun loadTicket(bookUrl: String, bookName: String, author: String) {
        this.bookUrl = bookUrl
        tvBookName.text = bookName
        tvAuthor.text = author

        CoroutineScope(Dispatchers.IO).launch {
            val ticket = ReadingTicketHelper.getTicket(bookUrl)
            
            launch(Dispatchers.Main) {
                if (ticket != null) {
                    displayTicket(ticket)
                } else {
                    // 如果没有小票，使用默认值
                    displayDefaultTicket()
                }
            }
        }
    }
    
    /**
     * 直接设置阅读小票数据
     */
    fun setTicket(ticket: ReadingTicket, book: io.legado.app.data.entities.Book) {
        this.bookUrl = ticket.bookUrl
        this.currentBook = book
        tvBookName.text = ticket.bookName.ifEmpty { book.name }
        tvAuthor.text = ticket.author.ifEmpty { book.author }
        displayTicket(ticket)
        
        // 加载书评
        etReview.setText(book.reviewContent ?: "")
    }

    /**
     * 显示阅读小票数据
     */
    private fun displayTicket(ticket: ReadingTicket) {
        tvReadTime.text = ticket.getFormattedReadTime()
        tvReadCount.text = ticket.getReadCountText()
        tvProgress.text = "${ticket.getProgressPercentage()}%"
        ratingBar.rating = ticket.rating
        
        // 显示书摘数量
        CoroutineScope(Dispatchers.IO).launch {
            // 由于书摘表中没有bookUrl字段，需要使用bookName和author查询
            val bookName = ticket.bookName
            val author = ticket.author
            val annotationCount = io.legado.app.data.appDb.bookAnnotationDao.getCount(bookName, author)
            launch(Dispatchers.Main) {
                tvAnnotationCount.text = "${annotationCount}条"
            }
        }

        // 显示完成时间
        if (ticket.finishTime > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val finishDate = Date(ticket.finishTime)
            tvFinishTime.text = "完成于 ${dateFormat.format(finishDate)}"
            tvFinishTime.visibility = VISIBLE
        } else {
            tvFinishTime.visibility = GONE
        }
    }

    /**
     * 显示默认小票（首次读完）
     */
    private fun displayDefaultTicket() {
        tvReadTime.text = "不足1分钟"
        tvReadCount.text = "初读"
        tvProgress.text = "100%"
        ratingBar.rating = 0f
        tvFinishTime.visibility = GONE
    }

    /**
     * 设置评分变化监听
     */
    fun setOnRatingChangedListener(listener: (Float) -> Unit) {
        this.onRatingChanged = listener
    }
    
    /**
     * 保存书评
     */
    private fun saveReview(reviewContent: String) {
        currentBook?.let { book ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 更新Book实体的书评
                    book.reviewContent = if (reviewContent.isBlank()) null else reviewContent
                    io.legado.app.data.appDb.bookDao.update(book)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
