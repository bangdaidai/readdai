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
     * 创建或更新阅读小票（与Book实体的rating和readingStatus联动）
     */
    suspend fun updateTicket(
        book: Book,
        readTime: Long = 0,
        incrementReadCount: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val existingTicket = appDb.readingTicketDao.getByBookUrl(book.bookUrl)
        
        val ticket = if (existingTicket != null) {
            // 更新现有小票
            existingTicket.copy(
                bookName = book.name,
                author = book.author,
                totalReadTime = existingTicket.totalReadTime + readTime,
                readCount = if (incrementReadCount) existingTicket.readCount + 1 else existingTicket.readCount,
                rating = book.rating, // 直接使用Book的rating，保持联动
                lastReadTime = System.currentTimeMillis(),
                completedChapters = book.durChapterIndex + 1,
                totalChapters = book.totalChapterNum,
                finishTime = if (book.readingStatus == 2) book.finishReadTime else existingTicket.finishTime
            )
        } else {
            // 创建新小票
            ReadingTicket(
                bookUrl = book.bookUrl,
                bookName = book.name,
                author = book.author,
                totalReadTime = readTime,
                readCount = if (incrementReadCount) 1 else 0,
                rating = book.rating, // 使用Book的rating
                firstReadTime = System.currentTimeMillis(),
                lastReadTime = System.currentTimeMillis(),
                completedChapters = book.durChapterIndex + 1,
                totalChapters = book.totalChapterNum,
                finishTime = if (book.readingStatus == 2) book.finishReadTime else 0L
            )
        }
        
        appDb.readingTicketDao.insert(ticket)
    }
    
    /**
     * 标记书籍为读完（可选是否增加阅读次数）
     * @param incrementReadCount 是否增加阅读次数（N刷）
     */
    suspend fun markAsFinished(book: Book, incrementReadCount: Boolean = true) = withContext(Dispatchers.IO) {
        // 更新书籍状态
        book.setReadingStatus(2, userModified = true)
        book.finishReadTime = System.currentTimeMillis()
        appDb.bookDao.update(book)
        
        // 如果需要，增加阅读次数
        if (incrementReadCount) {
            appDb.readingTicketDao.incrementReadCount(book.bookUrl)
        }
        
        // 设置完成时间
        appDb.readingTicketDao.setFinishTime(book.bookUrl)
        
        // 更新小票
        updateTicket(book, incrementReadCount = incrementReadCount)
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
        appDb.readingTicketDao.getByBookUrl(bookUrl)
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
