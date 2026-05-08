package io.legado.app.help.ai.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.readRecord.ReadSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读历史数据记录
 */
data class ReadingHistoryRecord(
    val bookTitle: String,
    val author: String,
    val bookUrl: String,
    val lastReadTime: Long,
    val totalReadTime: Long,
    val readCount: Int,
    val lastChapter: String?
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "bookTitle" to bookTitle,
            "author" to author,
            "bookUrl" to bookUrl,
            "lastReadTime" to lastReadTime,
            "totalReadTime" to totalReadTime,
            "readCount" to readCount,
            "lastChapter" to lastChapter
        )
    }
}

/**
 * 阅读历史 Repository
 * 负责从数据库获取和聚合阅读历史数据
 * 
 * 参考 anx53 的 ReadingHistoryRepository 设计
 */
class ReadingHistoryRepository(
    private val appDatabase: AppDatabase
) {
    private val sessionDao = appDatabase.readSessionDao
    
    /**
     * 获取阅读历史记录
     * 
     * @param bookTitleFilter 书籍标题过滤（可选）
     * @param fromDate 开始时间戳（可选，毫秒）
     * @param toDate 结束时间戳（可选，毫秒）
     * @param limit 最大返回记录数
     * @return 按书籍分组的阅读历史记录列表
     */
    suspend fun fetchHistory(
        bookTitleFilter: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        limit: Int = 20
    ): List<ReadingHistoryRecord> = withContext(Dispatchers.IO) {
        // 获取所有阅读会话
        val allSessions = sessionDao.getAll()
        
        // 过滤会话
        val filteredSessions = allSessions.filter { session ->
            // 按书籍标题过滤
            val matchesTitle = bookTitleFilter.isNullOrBlank() ||
                    session.bookName.contains(bookTitleFilter, ignoreCase = true)
            
            // 按时间范围过滤
            val matchesFrom = fromDate == null || session.endTime >= fromDate
            val matchesTo = toDate == null || session.endTime <= toDate
            
            matchesTitle && matchesFrom && matchesTo
        }
        
        // 按书籍分组并聚合数据
        val historyByBook = filteredSessions
            .groupBy { it.bookName }
            .mapValues { (_, bookSessions) ->
                // 获取最新的会话
                val latestSession = bookSessions.maxByOrNull { it.endTime } ?: bookSessions.first()
                
                // 计算总阅读时长
                val totalDuration = bookSessions.sumOf { it.duration }
                
                // 计算阅读次数
                val readCount = bookSessions.size
                
                ReadingHistoryRecord(
                    bookTitle = latestSession.bookName,
                    author = latestSession.author ?: "未知",
                    bookUrl = latestSession.bookUrl,
                    lastReadTime = latestSession.endTime,
                    totalReadTime = totalDuration,
                    readCount = readCount,
                    lastChapter = latestSession.durChapterTitle
                )
            }
            .values
        
        // 按最后阅读时间排序，取前 limit 条
        historyByBook
            .sortedByDescending { it.lastReadTime }
            .take(limit)
    }
    
    /**
     * 获取指定书籍的阅读历史
     */
    suspend fun getBookHistory(
        bookUrl: String,
        limit: Int = 50
    ): List<ReadSession> = withContext(Dispatchers.IO) {
        sessionDao.getByUrl(bookUrl).take(limit)
    }
    
    /**
     * 获取某本书的总阅读时长
     */
    suspend fun getTotalReadTime(bookUrl: String): Long = withContext(Dispatchers.IO) {
        sessionDao.getTotalReadTimeByUrl(bookUrl) ?: 0L
    }
}
