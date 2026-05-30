package io.legado.app.help.book

import io.legado.app.constant.AppConst
import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadingMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

/**
 * 阅读进度和状态管理助手
 * 统一管理所有页面的阅读进度计算和阅读状态管理逻辑
 * 与阅读页面的计算逻辑完全一致
 */
object ReadingProgressHelper {
    
    private val readProgressFormatter = DecimalFormat("0.0%")
    
    /**
     * 根据阅读进度和阅读时间计算阅读状态
     * 统一的阅读状态计算逻辑，适用于所有场景
     * 
     * 注意：不再仅凭进度100%就自动设为FINISHED
     * FINISHED状态只能通过用户手动确认"标记读完"来设置
     * 
     * @param progress 阅读进度百分比 (0-100)
     * @param readTime 阅读时间（毫秒）
     * @param isAbandoned 是否已弃文
     * @param isFinished 是否已手动标记为完成
     * @return 对应的阅读状态
     */
    fun calculateReadingStatus(progress: Float, readTime: Long, isAbandoned: Boolean = false, isFinished: Boolean = false): ReadingStatus {
        // 优先处理手动设置的状态
        if (isAbandoned) return ReadingStatus.ABANDONED
        if (isFinished) return ReadingStatus.FINISHED
        
        // 根据进度和阅读时间自动计算状态
        // 不再根据进度100%自动设为FINISHED，只区分为在读和待读
        return when {
            progress > 0f || readTime > 0 -> ReadingStatus.READING
            else -> ReadingStatus.PENDING
        }
    }
    
    /**
     * 根据阅读进度更新阅读状态（简化版）
     * @param progress 阅读进度百分比 (0-100)
     * @return 对应的阅读状态
     */
    fun getReadingStatusByProgress(progress: Float): ReadingStatus {
        return calculateReadingStatus(progress, 0)
    }
    
    /**
     * 根据阅读进度和阅读时间更新阅读状态（简化版）
     * @param progress 阅读进度百分比 (0-100)
     * @param readTime 阅读时间（毫秒）
     * @return 对应的阅读状态
     */
    fun getReadingStatusByProgressAndTime(progress: Float, readTime: Long): ReadingStatus {
        return calculateReadingStatus(progress, readTime)
    }
    
    /**
     * 根据书籍信息自动计算阅读状态
     * 逻辑说明：
     * - 只有用户手动确认"标记读完"后，状态才会变为FINISHED（通过ReadIterationHelper.markAsFinished）
     * - 不会仅凭阅读进度100%就自动设为FINISHED
     * - 已标记为FINISHED的书保持FINISHED状态（除非用户开始n刷）
     * - 弃文状态保持不变
     * 
     * @param book 书籍实体
     * @return 阅读状态枚举
     */
    suspend fun calculateReadingStatus(book: Book): ReadingStatus {
        // 首先检查是否是弃文状态
        if (book.readingStatus == ReadingStatus.ABANDONED.value) {
            return ReadingStatus.ABANDONED
        }
        
        // 如果当前状态是已读完，保持已读完状态
        // 只有用户开始n刷时才会通过moveToNextIteration改为READING
        if (book.readingStatus == ReadingStatus.FINISHED.value) {
            return ReadingStatus.FINISHED
        }
        
        // 计算阅读进度
        val progress = calculateReadingProgress(book)
        
        // 检查用户是否真正阅读过这本书
        val hasRead = book.durChapterIndex > 0 || book.durChapterPos > 0
        
        // 不再根据进度100%自动设为FINISHED
        // 只有用户在阅读末尾手动确认"标记读完"才会变为FINISHED
        return when {
            progress > 0f || hasRead -> ReadingStatus.READING
            else -> ReadingStatus.PENDING
        }
    }
    
    /**
     * 根据书籍对象更新阅读状态
     * 统一管理所有页面的阅读状态更新逻辑
     * 
     * @param book 书籍对象
     * @param autoUpdate 是否自动更新（用户未手动修改过状态时）
     * @return 更新后的书籍对象
     */
    suspend fun updateBookReadingStatusByProgress(book: Book, autoUpdate: Boolean = true): Book {
        if (!autoUpdate || book.userModifiedReadingStatus) {
            return book
        }
        
        // 如果当前状态是已读完，保持已读完状态
        if (book.getReadingStatusEnum() == ReadingStatus.FINISHED) {
            return book
        }
        
        // 计算阅读进度
        val progress = calculateReadingProgress(book)
        // 使用统一的计算方法计算阅读状态，同时考虑阅读进度和阅读时间
        val newStatus = calculateReadingStatus(book)
        
        // 更新书籍的阅读状态
        if (book.getReadingStatusEnum() != newStatus) {
            book.setReadingStatus(newStatus, false)
        }
        
        return book
    }
    
    /**
     * 根据阅读记忆对象更新阅读状态
     * 统一管理所有页面的阅读状态更新逻辑
     * 
     * @param memory 阅读记忆对象
     * @param autoUpdate 是否自动更新（用户未手动修改过状态时）
     * @return 更新后的阅读记忆对象
     */
    fun updateMemoryReadingStatusByProgress(memory: ReadingMemory, autoUpdate: Boolean = true): ReadingMemory {
        if (!autoUpdate || memory.userModifiedReadingStatus) {
            return memory
        }
        
        // 如果当前状态是已读完，保持已读完状态，不更新任何时间
        if (memory.readingStatus == ReadingStatus.FINISHED) {
            return memory
        }
        
        // 使用getReadingStatusByProgressAndTime计算阅读状态，同时考虑阅读进度和阅读时间
        val newStatus = getReadingStatusByProgressAndTime(memory.progress, memory.readTime)
        
        // 更新阅读记忆的阅读状态
        if (memory.readingStatus != newStatus) {
            memory.readingStatus = newStatus
            // 如果状态变为已完成，设置完成时间
            if (newStatus == ReadingStatus.FINISHED && memory.finishReadTime == 0L) {
                memory.finishReadTime = System.currentTimeMillis()
            }
            memory.updateTime = System.currentTimeMillis()
        }
        
        return memory
    }
    
    /**
     * 更新书籍的阅读状态（仅当用户未手动修改过状态时）
     * @param bookUrl 书籍URL
     * @return 是否成功更新
     */
    suspend fun updateBookReadingStatus(bookUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext false
                
                // 如果书籍当前状态是弃文，不自动更新
                if (book.readingStatus == ReadingStatus.ABANDONED.value) {
                    return@withContext false
                }
                
                // 如果用户已手动修改过阅读状态，则不自动更新
                if (book.userModifiedReadingStatus) {
                    return@withContext false
                }
                
                // 计算新的阅读状态
                val newStatus = calculateReadingStatus(book)
                
                // 如果状态有变化，更新数据库
                if (book.readingStatus != newStatus.value) {
                    book.readingStatus = newStatus.value
                    // 如果状态变为已完成，设置完成时间
                    if (newStatus == ReadingStatus.FINISHED && book.finishReadTime == 0L) {
                        book.finishReadTime = System.currentTimeMillis()
                    }
                    appDb.bookDao.update(book)
                    
                    // 更新书籍分组，使用forceUpdate=false确保不会覆盖用户手动设置的状态
                    updateBookGroupByReadingStatus(bookUrl, newStatus, forceUpdate = false)
                }
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * 批量更新所有书籍的阅读状态
     * @return 更新成功的书籍数量
     */
    suspend fun updateAllBooksReadingStatus(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val allBooks = appDb.bookDao.all
                var updateCount = 0
                
                for (book in allBooks) {
                    // 如果书籍当前状态是弃文，不自动更新
                    if (book.readingStatus == ReadingStatus.ABANDONED.value) {
                        continue
                    }
                    
                    // 如果用户已手动修改过阅读状态，则跳过
                    if (book.userModifiedReadingStatus) {
                        continue
                    }
                    
                    // 计算新的阅读状态
                    val newStatus = calculateReadingStatus(book)
                    
                    // 如果状态有变化，更新数据库
                    if (book.readingStatus != newStatus.value) {
                        book.readingStatus = newStatus.value
                        // 如果状态变为已完成，设置完成时间
                        if (newStatus == ReadingStatus.FINISHED && book.finishReadTime == 0L) {
                            book.finishReadTime = System.currentTimeMillis()
                        }
                        appDb.bookDao.update(book)
                        
                        // 更新书籍分组，使用forceUpdate=false确保不会覆盖用户手动设置的状态
                        updateBookGroupByReadingStatus(book.bookUrl, newStatus, forceUpdate = false)
                        
                        updateCount++
                    }
                }
                
                updateCount
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }
    
    /**
     * 手动设置书籍阅读状态
     * @param bookUrl 书籍URL
     * @param status 阅读状态
     * @return 是否成功更新
     */
    suspend fun setBookReadingStatus(bookUrl: String, status: ReadingStatus): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext false
                
                // 设置阅读状态
                book.readingStatus = status.value
                // 如果状态变为已完成，设置完成时间
                if (status == ReadingStatus.FINISHED && book.finishReadTime == 0L) {
                    book.finishReadTime = System.currentTimeMillis()
                }
                book.userModifiedReadingStatus = true
                appDb.bookDao.update(book)
                
                // 更新书籍分组，使用forceUpdate=true确保更新
                updateBookGroupByReadingStatus(bookUrl, status, forceUpdate = true)
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * 根据阅读状态更新书籍分组
     * @param bookUrl 书籍URL
     * @param status 阅读状态
     */
    private suspend fun updateBookGroupByReadingStatus(bookUrl: String, status: ReadingStatus, forceUpdate: Boolean = false) {
        try {
            // 使用ReadingStatusGroupHelper更新书籍分组
            ReadingStatusGroupHelper.updateBookGroupByReadingStatus(bookUrl, status, forceUpdate)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取书籍的实际章节数
     * 始终使用实际章节大小，不使用模拟章节数
     */
    private fun getActualChapterSize(book: Book): Int {
        return appDb.bookChapterDao.getChapterCount(book.bookUrl)
    }
    
    /**
     * 计算阅读进度百分比
     * 使用与阅读页面完全相同的计算逻辑，考虑章节内阅读位置
     * 
     * @param book 书籍对象
     * @return 阅读进度百分比 (0-100)
     */
    fun calculateReadingProgress(book: Book): Float {
        // 检查用户是否真正阅读过这本书
        val hasRead = book.durChapterIndex > 0 || book.durChapterPos > 0
        
        // 如果没有真正阅读过，返回0%进度
        if (!hasRead) {
            return 0f
        }
        
        // 获取当前章节索引和实际章节数
        var currentChapterIndex = book.durChapterIndex
        val actualChapterSize = getActualChapterSize(book)
        
        // 如果总章节数为0，使用书籍自身的totalChapterNum
        var chapterSize = actualChapterSize
        if (chapterSize <= 0) {
            chapterSize = book.totalChapterNum
        }
        
        // 如果仍然为0，说明是新书，返回0%进度
        if (chapterSize <= 0) {
            return 0f
        }
        
        // 修复章节索引，确保不超过总章节数
        // 章节索引从0开始，所以最大有效索引是 chapterSize - 1
        if (currentChapterIndex >= chapterSize) {
            currentChapterIndex = chapterSize - 1
        }
        
        // 直接按照章节数量计算进度，(当前章节索引 + 1) / 总章节数 * 100
        // 加1是因为章节索引从0开始，而实际章节数从1开始
        val totalProgress = (currentChapterIndex + 1).toFloat() / chapterSize * 100f
        
        // 确保进度在0-100之间
        return maxOf(0f, minOf(100f, totalProgress))
    }
    
    /**
     * 计算阅读进度百分比
     * 使用与阅读页面完全相同的计算逻辑，包含页面级别的进度
     * 
     * @param chapterIndex 当前章节索引
     * @param chapterSize 总章节数
     * @param pageIndex 当前页面索引
     * @param pageSize 当前章节总页数
     * @return 阅读进度百分比 (0-100)
     */
    fun calculateReadingProgress(
        chapterIndex: Int,
        chapterSize: Int,
        pageIndex: Int = 0,
        pageSize: Int = 1
    ): Float {
        if (chapterSize <= 0) {
            return 0f
        }
        
        // 修复章节索引，确保不超过总章节数
        val safeChapterIndex = Math.min(chapterIndex, chapterSize - 1)
        
        // 计算进度：(当前章节索引/总章节数) + (1/总章节数) * (当前页面索引+1/当前章节总页数)
        val percent = safeChapterIndex * 1.0f / chapterSize + 1.0f / chapterSize * (pageIndex + 1) / Math.max(pageSize.toFloat(), 1.0f)
        
        // 转换为百分比
        return percent * 100f
    }
    
    /**
     * 格式化阅读进度为字符串
     * 与阅读页面显示逻辑完全一致
     * 
     * @param chapterIndex 当前章节索引
     * @param chapterSize 总章节数
     * @param pageIndex 当前页面索引
     * @param pageSize 当前章节总页数
     * @return 格式化后的阅读进度字符串（如"99.9%"）
     */
    fun formatReadingProgress(
        chapterIndex: Int,
        chapterSize: Int,
        pageIndex: Int = 0,
        pageSize: Int = 1
    ): String {
        if (chapterSize == 0 || pageSize == 0 && chapterIndex == 0) {
            return "0.0%"
        } else if (pageSize == 0) {
            return readProgressFormatter.format((chapterIndex + 1.0f) / chapterSize.toDouble())
        }
        
        var percent = readProgressFormatter.format(
            chapterIndex * 1.0f / chapterSize + 1.0f / chapterSize * (pageIndex + 1) / pageSize.toDouble()
        )
        
        // 特殊处理：当进度显示为"100.0%"但实际未完成时，显示为"99.9%"
        if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || pageIndex + 1 != pageSize)) {
            percent = "99.9%"
        }
        
        return percent
    }
    
    /**
     * 格式化阅读进度为字符串
     * 与阅读页面显示逻辑完全一致
     * 
     * @param progress 阅读进度百分比 (0-100)
     * @param chapterIndex 当前章节索引
     * @param chapterSize 总章节数
     * @param pageIndex 当前页面索引
     * @param pageSize 当前章节总页数
     * @return 格式化后的阅读进度字符串（如"99.9%"）
     */
    fun formatReadingProgress(
        progress: Float,
        chapterIndex: Int,
        chapterSize: Int,
        pageIndex: Int = 0,
        pageSize: Int = 1
    ): String {
        // 转换为0-1之间的小数
        val progressDecimal = progress / 100.0
        var percent = readProgressFormatter.format(progressDecimal)
        
        // 特殊处理：当进度显示为"100.0%"但实际未完成时，显示为"99.9%"
        if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || pageIndex + 1 != pageSize)) {
            percent = "99.9%"
        }
        
        return percent
    }
    
    /**
     * 从书籍对象计算并格式化阅读进度
     * 与阅读页面显示逻辑完全一致
     * 
     * @param book 书籍对象
     * @return 格式化后的阅读进度字符串（如"99.9%"）
     */
    fun calculateBookProgress(book: Book): String {
        val hasRead = book.durChapterTime > 0 || book.durChapterIndex > 0 || book.durChapterPos > 0
        
        if (!hasRead) {
            return "未看"
        }
        
        var currentChapterIndex = book.durChapterIndex
        val actualChapterSize = getActualChapterSize(book)
        
        if (currentChapterIndex >= actualChapterSize) {
            currentChapterIndex = actualChapterSize - 1
        }
        
        val percentProgress = formatReadingProgress(currentChapterIndex, actualChapterSize)
        
        if (percentProgress == "100.0%" || percentProgress == "99.9%") {
            val isLastChapter = currentChapterIndex + 1 >= actualChapterSize
            if (isLastChapter) {
                return "看完"
            }
        }
        
        val chapterProgress = "(${currentChapterIndex + 1}/$actualChapterSize)"
        
        return "$percentProgress $chapterProgress"
    }
    
    /**
     * 从阅读记忆对象计算并格式化阅读进度
     * 与阅读页面显示逻辑完全一致
     * 
     * @param memory 阅读记忆对象
     * @return 格式化后的阅读进度字符串（如"99.9%"）
     */
    fun calculateMemoryProgress(memory: ReadingMemory): String {
        val book = appDb.bookDao.getBook(memory.bookUrl)
        val hasRead = if (book != null) {
            book.durChapterTime > 0 || book.durChapterIndex > 0 || book.durChapterPos > 0
        } else {
            memory.readingStatus != ReadingStatus.PENDING || memory.progress > 0f || memory.readTime > 0L
        }
        
        if (!hasRead) {
            return "未看"
        }
        
        val progressValue = if (book != null) {
            calculateReadingProgress(book)
        } else {
            val currentChapterIndex = memory.durChapterIndex
            val chapterSize = maxOf(1, memory.totalChapterNum)
            val safeChapterIndex = minOf(currentChapterIndex, chapterSize - 1)
            (safeChapterIndex + 1).toFloat() / chapterSize * 100f
        }
        
        var actualChapterSize = memory.totalChapterNum
        var currentChapterIndex = memory.durChapterIndex
        if (book != null) {
            actualChapterSize = getActualChapterSize(book)
            currentChapterIndex = book.durChapterIndex
        }
        
        if (currentChapterIndex >= actualChapterSize) {
            currentChapterIndex = actualChapterSize - 1
        }
        
        val percentProgress = readProgressFormatter.format(progressValue / 100.0)
        
        if (percentProgress == "100.0%" || percentProgress == "99.9%") {
            val isLastChapter = currentChapterIndex + 1 >= actualChapterSize
            if (isLastChapter) {
                return "看完"
            }
        }
        
        val chapterProgress = "(${currentChapterIndex + 1}/$actualChapterSize)"
        
        return "$percentProgress $chapterProgress"
    }
    
    /**
     * 从书籍对象计算并格式化阅读进度，包含页面级别的进度
     * 与阅读页面显示逻辑完全一致
     * 
     * @param book 书籍对象
     * @param pageIndex 当前页面索引
     * @param pageSize 当前章节总页数
     * @return 格式化后的阅读进度字符串（如"99.9%"）
     */
    fun calculateBookProgressWithPage(book: Book, pageIndex: Int = 0, pageSize: Int = 1): String {
        // 获取当前章节索引和实际章节数
        var currentChapterIndex = book.durChapterIndex
        val actualChapterSize = getActualChapterSize(book)
        
        // 修复章节索引，确保不超过总章节数
        if (currentChapterIndex >= actualChapterSize) {
            currentChapterIndex = actualChapterSize - 1
        }
        
        return formatReadingProgress(currentChapterIndex, actualChapterSize, pageIndex, pageSize)
    }
}