package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.constant.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 阅读数据一致性检查工具类
 * 用于定期检查和修复Book与ReadingMemory之间的数据不一致问题
 */
object ReadingDataConsistencyHelper {
    
    /**
     * 检查并修复所有书籍的数据一致性
     * 1. 确保Book和ReadingMemory的阅读状态一致
     * 2. 确保Book和ReadingMemory的阅读进度一致
     * 3. 确保Book和ReadingMemory的用户修改标记一致
     */
    suspend fun checkAndFixAllDataConsistency() {
        withContext(Dispatchers.IO) {
            // 获取所有书籍
            val books = appDb.bookDao.all
            
            for (book in books) {
                // 获取对应书籍的阅读记忆
                val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
                
                if (memory != null) {
                    // 检查并修复阅读状态一致性
                    checkAndFixReadingStatusConsistency(book, memory)
                    
                    // 检查并修复阅读进度一致性
                    checkAndFixReadingProgressConsistency(book, memory)
                    
                    // 检查并修复用户修改标记一致性
                    checkAndFixUserModifiedFlagsConsistency(book, memory)
                }
            }
            
            // 检查并修复孤立的阅读记忆（没有对应Book的记忆）
            checkAndFixOrphanedMemories()
        }
    }
    
    /**
     * 检查并修复阅读状态一致性
     */
    private suspend fun checkAndFixReadingStatusConsistency(book: Book, memory: ReadingMemory) {
        // 获取Book和ReadingMemory的阅读状态
        val bookStatus = book.getReadingStatusEnum()
        val memoryStatus = memory.readingStatus
        
        // 如果任意一方是已读完状态，保持已读完状态
        if (bookStatus == ReadingStatus.FINISHED || memoryStatus == ReadingStatus.FINISHED) {
            if (bookStatus != ReadingStatus.FINISHED) {
                // Book不是已读完状态，更新为已读完
                book.setReadingStatus(ReadingStatus.FINISHED, false)
                appDb.bookDao.update(book)
            }
            if (memoryStatus != ReadingStatus.FINISHED) {
                // ReadingMemory不是已读完状态，更新为已读完
                memory.readingStatus = ReadingStatus.FINISHED
                memory.userModifiedReadingStatus = false
                // 确保finishReadTime已设置
                if (memory.finishReadTime == 0L) {
                    memory.finishReadTime = book.finishReadTime
                }
                appDb.readingMemoryDao.update(memory)
            }
            return
        }
        
        // 只有当两者状态不同且至少有一个未被用户手动修改时，才进行修复
        if (bookStatus != memoryStatus) {
            if (book.userModifiedReadingStatus && memory.userModifiedReadingStatus) {
                // 两者都被用户手动修改，保持原状
                return
            } else if (book.userModifiedReadingStatus) {
                // 只有Book被用户手动修改，将Book的状态同步到ReadingMemory
                updateMemoryStatusFromBook(book, memory)
            } else if (memory.userModifiedReadingStatus) {
                // 只有ReadingMemory被用户手动修改，将ReadingMemory的状态同步到Book
                updateBookStatusFromMemory(book, memory)
            } else {
                // 两者都未被用户手动修改，根据阅读进度重新计算状态
                val progress = ReadingProgressHelper.calculateReadingProgress(book)
                val readTime = appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
                val newStatus = ReadingProgressHelper.calculateReadingStatus(progress, readTime)
                
                // 更新Book和ReadingMemory的状态
                book.readingStatus = newStatus.value
                book.userModifiedReadingStatus = false
                appDb.bookDao.update(book)
                
                memory.readingStatus = newStatus
                memory.userModifiedReadingStatus = false
                appDb.readingMemoryDao.update(memory)
            }
        }
    }
    
    /**
     * 检查并修复阅读进度一致性
     */
    private suspend fun checkAndFixReadingProgressConsistency(book: Book, memory: ReadingMemory) {
        // 只有当用户未手动修改状态时，才修复阅读进度
        if (!book.userModifiedReadingStatus && !memory.userModifiedReadingStatus) {
            // 更新ReadingMemory的阅读进度
            val progress = ReadingProgressHelper.calculateReadingProgress(book)
            memory.progress = progress
            memory.durChapterIndex = book.durChapterIndex
            memory.durChapterPos = book.durChapterPos
            memory.durChapterTitle = book.durChapterTitle
            appDb.readingMemoryDao.update(memory)
        }
    }
    
    /**
     * 检查并修复用户修改标记一致性
     */
    private suspend fun checkAndFixUserModifiedFlagsConsistency(book: Book, memory: ReadingMemory) {
        // 同步用户修改标记
        val userModifiedRating = book.userModifiedRating || memory.userModifiedRating
        val userModifiedReadingStatus = book.userModifiedReadingStatus || memory.userModifiedReadingStatus
        
        if (book.userModifiedRating != userModifiedRating || book.userModifiedReadingStatus != userModifiedReadingStatus) {
            book.userModifiedRating = userModifiedRating
            book.userModifiedReadingStatus = userModifiedReadingStatus
            appDb.bookDao.update(book)
        }
        
        if (memory.userModifiedRating != userModifiedRating || memory.userModifiedReadingStatus != userModifiedReadingStatus) {
            memory.userModifiedRating = userModifiedRating
            memory.userModifiedReadingStatus = userModifiedReadingStatus
            appDb.readingMemoryDao.update(memory)
        }
    }
    
    /**
     * 检查并修复孤立的阅读记忆（没有对应Book的记忆）
     */
    private suspend fun checkAndFixOrphanedMemories() {
        // 获取所有阅读记忆
        val allMemories = appDb.readingMemoryDao.all
        
        for (memory in allMemories) {
            // 检查是否存在对应的Book
            val book = appDb.bookDao.getBook(memory.bookUrl)
            
            if (book == null) {
                // 没有对应Book，但检查是否有相同书名和作者的Book
                val existingBook = appDb.bookDao.getBook(memory.bookName, memory.bookAuthor)
                
                if (existingBook != null) {
                    // 有相同书名和作者的Book，更新阅读记忆的bookUrl
                    memory.bookUrl = existingBook.bookUrl
                    appDb.readingMemoryDao.update(memory)
                } else {
                    // 没有对应Book，且没有相同书名和作者的Book，保留阅读记忆
                    // 这些记忆可能是已删除书籍的阅读记录，不应该删除
                }
            }
        }
    }
    
    /**
     * 从Book更新ReadingMemory的状态
     */
    private suspend fun updateMemoryStatusFromBook(book: Book, memory: ReadingMemory) {
        memory.readingStatus = book.getReadingStatusEnum()
        memory.userModifiedReadingStatus = book.userModifiedReadingStatus
        
        // 如果状态变为已完成，确保finishReadTime已设置
        if (memory.readingStatus == ReadingStatus.FINISHED && memory.finishReadTime == 0L) {
            memory.finishReadTime = book.finishReadTime
        }
        
        appDb.readingMemoryDao.update(memory)
    }
    
    /**
     * 从ReadingMemory更新Book的状态
     */
    private suspend fun updateBookStatusFromMemory(book: Book, memory: ReadingMemory) {
        book.readingStatus = memory.readingStatus.value
        book.userModifiedReadingStatus = memory.userModifiedReadingStatus
        
        // 如果状态变为已完成，确保finishReadTime已设置
        if (book.readingStatus == ReadingStatus.FINISHED.value && book.finishReadTime == 0L) {
            book.finishReadTime = memory.finishReadTime
        }
        
        appDb.bookDao.update(book)
    }
}
