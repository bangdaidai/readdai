package io.legado.app.help.book

import io.legado.app.constant.AppConst
import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.entities.ReadingMemory
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读记忆管理工具类
 * 用于生成和更新书籍的阅读记忆
 */
object ReadingMemoryHelper {
    
    /**
     * 为书籍生成阅读记忆
     */
    suspend fun createReadingMemory(book: Book) {
        // 检查是否已存在该书籍的阅读记忆
        var existingMemory = withContext(Dispatchers.IO) {
            appDb.readingMemoryDao.getByBookUrl(book.bookUrl)
        }
        
        // 如果没有找到匹配bookUrl的记忆，尝试查找相同书名和作者的记忆
        if (existingMemory == null) {
            val memoriesByBook = withContext(Dispatchers.IO) {
                appDb.readingMemoryDao.getByBook(book.name, book.author)
            }
            if (memoriesByBook.isNotEmpty()) {
                // 使用最新的记忆（按更新时间降序排列，第一个是最新的）
                existingMemory = memoriesByBook.first()
            }
        }
        
        // 获取实际阅读时间，而不是使用章节阅读时间
        var readTime = 0L
        
        // 1. 首先从ReadSession表中获取总阅读时间
        readTime = try {
            withContext(Dispatchers.IO) {
                appDb.readSessionDao.getTotalReadTime(book.name, book.author) ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
        
        // 2. 如果ReadSession中没有，尝试使用bookUrl查询
        if (readTime == 0L) {
            readTime = try {
                withContext(Dispatchers.IO) {
                    appDb.readSessionDao.getTotalReadTimeByUrl(book.bookUrl) ?: 0L
                }
            } catch (e: Exception) {
                0L
            }
        }
        
        // 获取书摘数量，包括相同书名和作者的书摘
        val annotations = withContext(Dispatchers.IO) {
            appDb.bookAnnotationDao.getByBook(book.name, book.author)
        }
        
        // 使用书籍的最后阅读时间作为阅读记忆的时间基准
        val bookLastReadTime = book.durChapterTime
        
        // 计算阅读进度和阅读状态
        val progress = ReadingProgressHelper.calculateReadingProgress(book)
        val readingStatus = ReadingProgressHelper.getReadingStatusByProgressAndTime(progress, readTime)
        
        // 检查书籍当前状态是否为已读完，或现有记忆是否为已读完
        val isBookFinished = book.readingStatus == ReadingStatus.FINISHED.value
        var isMemoryFinished = false
        if (existingMemory != null) {
            isMemoryFinished = existingMemory.readingStatus == ReadingStatus.FINISHED
        }
        
        // 使用事务确保数据一致性
        withContext(Dispatchers.IO) {
            // 更新书籍的首次阅读时间
            if (book.firstReadTime == 0L) {
                book.firstReadTime = bookLastReadTime
                appDb.bookDao.update(book)
            }
            
            if (existingMemory != null) {
                // 如果书籍或现有记忆是已读完状态，保持已读完状态
                val finalReadingStatus = if (isBookFinished || isMemoryFinished) {
                    ReadingStatus.FINISHED
                } else if (existingMemory.userModifiedReadingStatus) {
                    // 如果现有记忆是用户手动修改过状态，保持原状态不变
                    existingMemory.readingStatus
                } else {
                    readingStatus
                }
                
                // 保留原有的完成时间，不更新它们
                val finishReadTime = if (finalReadingStatus == ReadingStatus.FINISHED) {
                    if (isBookFinished) {
                        // 如果书籍是已读完状态，使用书籍的完成时间
                        book.finishReadTime
                    } else if (isMemoryFinished) {
                        // 如果现有记忆是已读完状态，使用记忆的完成时间
                        existingMemory.finishReadTime
                    } else if (existingMemory.finishReadTime == 0L) {
                        // 如果状态变为已完成且没有完成时间，使用书籍的最后阅读时间作为完成时间
                        bookLastReadTime
                    } else {
                        // 否则保留原有的完成时间
                        existingMemory.finishReadTime
                    }
                } else {
                    // 否则保留原有的完成时间
                    existingMemory.finishReadTime
                }
                
                // 同时更新书籍的finishReadTime和readingStatus，确保两处数据一致
                if (finalReadingStatus == ReadingStatus.FINISHED) {
                    if (book.finishReadTime == 0L) {
                        book.finishReadTime = finishReadTime
                    }
                    if (book.readingStatus != finalReadingStatus.value) {
                        book.readingStatus = finalReadingStatus.value
                        book.userModifiedReadingStatus = existingMemory.userModifiedReadingStatus
                        appDb.bookDao.update(book)
                    }
                } else if (book.readingStatus != finalReadingStatus.value && !book.userModifiedReadingStatus) {
                    // 仅当书籍状态不同且用户未手动修改过时，更新书籍状态
                    book.readingStatus = finalReadingStatus.value
                    appDb.bookDao.update(book)
                }
                
                // 处理首次阅读时间：
                // 1. 如果记忆中有首次阅读时间，使用记忆中的
                // 2. 否则，如果书籍有首次阅读时间，使用书籍的
                val firstReadTime = if (existingMemory.firstReadTime > 0L) {
                    existingMemory.firstReadTime
                } else {
                    book.firstReadTime
                }
                
                // 同步用户修改标记
                val userModifiedRating = existingMemory.userModifiedRating || book.userModifiedRating
                val userModifiedReadingStatus = existingMemory.userModifiedReadingStatus || book.userModifiedReadingStatus
                
                // 更新阅读记忆
                val updatedMemory = existingMemory.copy(
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    bookAuthor = book.author,
                    wordCount = book.wordCount,
                    kind = book.kind,
                    coverUrl = book.getDisplayCover(),
                    intro = book.intro,
                    rating = book.rating,
                    totalChapterNum = book.totalChapterNum,
                    durChapterIndex = book.durChapterIndex,
                    durChapterTitle = book.durChapterTitle,
                    durChapterPos = book.durChapterPos,
                    progress = if (isBookFinished || isMemoryFinished) existingMemory.progress else progress,
                    readTime = readTime,
                    annotationCount = annotations.size,
                    readingStatus = finalReadingStatus,
                    userModifiedRating = userModifiedRating,
                    userModifiedReadingStatus = userModifiedReadingStatus,
                    finishReadTime = finishReadTime,
                    firstReadTime = firstReadTime,
                    readIteration = book.readIteration,
                    type = book.type
                )
                appDb.readingMemoryDao.update(updatedMemory)
                
                // 删除相同书名和作者的其他旧记忆，避免重复
                appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, updatedMemory.id)
            } else {
                // 创建新记忆
                val finishReadTime = if (readingStatus == ReadingStatus.FINISHED) {
                    // 如果状态是已完成，使用书籍的最后阅读时间作为完成时间
                    bookLastReadTime
                } else {
                    // 否则使用默认值0
                    0L
                }
                
                // 同时更新书籍的finishReadTime和readingStatus，确保两处数据一致
                if (readingStatus == ReadingStatus.FINISHED && book.finishReadTime == 0L) {
                    book.finishReadTime = finishReadTime
                    book.readingStatus = readingStatus.value
                    appDb.bookDao.update(book)
                }
                
                // 同步用户修改标记
                val userModifiedRating = book.userModifiedRating
                val userModifiedReadingStatus = book.userModifiedReadingStatus
                
                // 使用书籍的首次阅读时间作为阅读记忆的创建时间，当前时间作为更新时间
                val createTime = if (book.firstReadTime > 0L) {
                    book.firstReadTime
                } else {
                    System.currentTimeMillis()
                }
                
                val memory = ReadingMemory(
                    id = "memory_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}",
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    bookAuthor = book.author,
                    wordCount = book.wordCount,
                    kind = book.kind,
                    coverUrl = book.getDisplayCover(),
                    intro = book.intro,
                    rating = book.rating,
                    totalChapterNum = book.totalChapterNum,
                    durChapterIndex = book.durChapterIndex,
                    durChapterTitle = book.durChapterTitle,
                    durChapterPos = book.durChapterPos,
                    progress = progress,
                    readTime = readTime,
                    annotationCount = annotations.size,
                    readingStatus = readingStatus,
                    userModifiedRating = userModifiedRating,
                    userModifiedReadingStatus = userModifiedReadingStatus,
                    finishReadTime = finishReadTime,
                    firstReadTime = book.firstReadTime,
                    readIteration = book.readIteration,
                    createTime = createTime,
                    updateTime = bookLastReadTime,
                    type = book.type
                )
                appDb.readingMemoryDao.insert(memory)
                
                // 删除相同书名和作者的其他旧记忆，避免重复
                appDb.readingMemoryDao.deleteByBookNameAndAuthorExcept(book.name, book.author, memory.id)
            }
        }
    }
    
    /**
     * 批量为书籍生成阅读记忆
     */
    suspend fun createReadingMemories(books: List<Book>) {
        books.forEach {
            createReadingMemory(it)
        }
    }
}