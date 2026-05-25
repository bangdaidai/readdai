package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadingTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读小票和阅读状态管理助手
 */
object ReadingTicketHelper {
    
    /**
     * 创建或更新阅读小票（与Book实体的rating和readIteration联动）
     * 使用ReadSession表中的实际阅读时长
     */
    suspend fun updateTicket(
        book: Book,
        readTime: Long = 0
    ) = withContext(Dispatchers.IO) {
        val existingTicket = appDb.readingTicketDao.getByBookUrl(book.bookUrl)
        
        // 从ReadSession表获取总阅读时长
        val totalReadTime = appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
        
        // 根据 readIteration 计算阅读状态
        val isFinished = book.readIteration > 0 && book.readIteration % 2 == 1  // 奇数=读完
        val readCount = if (book.readIteration >= 2) book.readIteration / 2 else 0  // N刷次数
        
        val ticket = if (existingTicket != null) {
            // 更新现有小票
            existingTicket.copy(
                bookName = book.name,
                author = book.author,
                totalReadTime = totalReadTime,
                readCount = readCount,
                rating = book.rating, // 直接使用Book的rating，保持联动
                lastReadTime = System.currentTimeMillis(),
                completedChapters = book.durChapterIndex + 1,
                totalChapters = book.totalChapterNum,
                finishTime = if (isFinished) book.finishReadTime else existingTicket.finishTime
            )
        } else {
            // 创建新小票
            ReadingTicket(
                bookUrl = book.bookUrl,
                bookName = book.name,
                author = book.author,
                totalReadTime = totalReadTime,
                readCount = readCount,
                rating = book.rating, // 使用Book的rating
                firstReadTime = System.currentTimeMillis(),
                lastReadTime = System.currentTimeMillis(),
                completedChapters = book.durChapterIndex + 1,
                totalChapters = book.totalChapterNum,
                finishTime = if (isFinished) book.finishReadTime else 0L
            )
        }
        
        appDb.readingTicketDao.insert(ticket)
    }
    
    /**
     * 标记书籍为读完（增加 readIteration）
     */
    suspend fun markAsFinished(book: Book) = withContext(Dispatchers.IO) {
        // 增加阅读轮次（变成奇数，表示读完）
        book.readIteration++
        book.finishReadTime = System.currentTimeMillis()
        appDb.bookDao.update(book)
        
        // 设置完成时间
        appDb.readingTicketDao.setFinishTime(book.bookUrl)
        
        // 更新小票
        updateTicket(book)
    }
    
    /**
     * 更新阅读时长
     */
    suspend fun addReadTime(bookUrl: String, readTime: Long) = withContext(Dispatchers.IO) {
        appDb.readingTicketDao.addReadTime(bookUrl, readTime)
    }
    
    /**
     * 更新评分（与Book实体的rating联动）
     */
    suspend fun updateRating(bookUrl: String, rating: Float) = withContext(Dispatchers.IO) {
        // 先更新书籍的评分
        val book = appDb.bookDao.getBook(bookUrl)
        book?.let {
            it.rating = rating
            it.userModifiedRating = true
            appDb.bookDao.update(it)
        }
        
        // 再更新小票中的评分
        appDb.readingTicketDao.updateRating(bookUrl, rating)
    }
    
    /**
     * 获取阅读小票
     */
    suspend fun getTicket(bookUrl: String): ReadingTicket? = withContext(Dispatchers.IO) {
        val ticket = appDb.readingTicketDao.getByBookUrl(bookUrl)
        // 确保使用最新的ReadSession阅读时长
        ticket?.let {
            val totalReadTime = appDb.readSessionDao.getTotalReadTimeByUrl(bookUrl) ?: 0L
            it.copy(totalReadTime = totalReadTime)
        }
    }
    
    /**
     * 获取所有已读完的书籍
     */
    suspend fun getFinishedBooks(): List<ReadingTicket> = withContext(Dispatchers.IO) {
        appDb.readingTicketDao.getFinishedBooks()
    }
    
    /**
     * 获取多刷书籍（阅读次数 >= 2）
     */
    suspend fun getMultiReadBooks(minCount: Int = 2): List<ReadingTicket> = withContext(Dispatchers.IO) {
        appDb.readingTicketDao.getMultiReadBooks(minCount)
    }
}
