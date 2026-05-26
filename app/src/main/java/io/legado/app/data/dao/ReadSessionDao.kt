package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.*
import io.legado.app.data.entities.readRecord.ReadSession

@Dao
interface ReadSessionDao {

    @Insert
    suspend fun insert(readSession: ReadSession): Long

    @Update
    suspend fun update(readSession: ReadSession)

    @Delete
    suspend fun delete(readSession: ReadSession)

    @Query("DELETE FROM readSession")
    suspend fun clear()

    @Query("DELETE FROM readSession WHERE bookName = :bookName AND author = :author")
    suspend fun deleteByBookName(bookName: String, author: String)

    /**
     * 批量删除指定书籍在指定时间范围内的所有阅读会�?     * 用于删除合并后的时间线记�?     */
    @Query("""
        DELETE FROM readSession 
        WHERE bookName = :bookName 
        AND startTime >= :startTime 
        AND endTime <= :endTime
    """)
    suspend fun deleteSessionsByTimeRange(bookName: String, startTime: Long, endTime: Long)

    @Query("SELECT * FROM readSession WHERE bookName = :bookName AND author = :author ORDER BY startTime DESC")
    suspend fun getByBook(bookName: String, author: String): List<ReadSession>

    @Query("SELECT * FROM readSession ORDER BY startTime DESC")
    suspend fun getAll(): List<ReadSession>
    
    @Query("SELECT * FROM readSession ORDER BY startTime DESC")
    fun flowGetAll(): kotlinx.coroutines.flow.Flow<List<ReadSession>>
    
    @Query("SELECT * FROM readSession WHERE (type & :type) > 0 ORDER BY startTime DESC")
    suspend fun getAllByTypeSync(type: Int): List<ReadSession>
    
    @Query("SELECT * FROM readSession ORDER BY startTime DESC")
    suspend fun getAllSync(): List<ReadSession>

    @Query("SELECT SUM(duration) FROM readSession WHERE bookUrl = :bookUrl")
    suspend fun getTotalReadTimeByUrl(bookUrl: String): Long?

    @Query("SELECT MIN(startTime) FROM readSession WHERE bookUrl = :bookUrl")
    suspend fun getFirstReadTimeByBook(bookUrl: String): Long?

    @Query("SELECT SUM(duration) FROM readSession WHERE bookName = :bookName")
    suspend fun getTotalReadTime(bookName: String): Long?
    
    @Query("SELECT SUM(duration) FROM readSession WHERE bookName = :bookName AND author = :author")
    suspend fun getTotalReadTime(bookName: String, author: String): Long?

    // 总计统计
    @Query(
        "SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, " +
        "(SELECT COUNT(*) FROM readingMemories WHERE progress >= 100.0) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews) + (SELECT COUNT(*) FROM bookAnnotations WHERE note != '' AND note IS NOT NULL)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "'' as date, " +
        "COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays FROM readSession"
    )
    suspend fun getTotalStatistics(): ReadStatistics

    // 每日统计
    @Query(
        "SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName || '|' || author) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "DATE(endTime / 1000, 'unixepoch', 'localtime') as date, " +
        "1 as readDays " +
        "FROM readSession " +
        "GROUP BY DATE(endTime / 1000, 'unixepoch', 'localtime') " +
        "ORDER BY date DESC"
    )
    suspend fun getDailyStatistics(): List<ReadStatistics>

    // 每月统计
    @Query(
        "SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName || '|' || author) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') as date, " +
        "COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays " +
        "FROM readSession " +
        "GROUP BY strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') " +
        "ORDER BY date DESC"
    )
    suspend fun getMonthlyStatistics(): List<ReadStatistics>

    // 每年统计
    @Query(
        "SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName || '|' || author) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = " +
        "CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') as date, " +
        "COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays " +
        "FROM readSession " +
        "GROUP BY strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') " +
        "ORDER BY date DESC"
    )
    suspend fun getYearlyStatistics(): List<ReadStatistics>

    // 获取特定日期的统计数�?
    @Query(
        "SELECT COUNT(DISTINCT bookName) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = :date) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = :date) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = :date)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "DATE(startTime / 1000, 'unixepoch', 'localtime') as date, " +
        "1 as readDays " +
        "FROM readSession " +
        "WHERE DATE(endTime / 1000, 'unixepoch', 'localtime') = :date " +
        "GROUP BY DATE(endTime / 1000, 'unixepoch', 'localtime')"
    )
    suspend fun getDailyStatisticsByDate(date: String): List<ReadStatistics>

    // 获取特定月份的统计数�?
    @Query(
        "SELECT COUNT(DISTINCT bookName) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = :month) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = :month) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = :month)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "strftime('%Y-%m', startTime / 1000, 'unixepoch', 'localtime') as date, " +
        "COUNT(DISTINCT CASE WHEN startTime = 0 THEN '未知日期' ELSE DATE(startTime / 1000, 'unixepoch', 'localtime') END) as readDays " +
        "FROM readSession " +
        "WHERE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') = :month " +
        "GROUP BY strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime')"
    )
    suspend fun getMonthlyStatisticsByMonth(month: String): List<ReadStatistics>

    // 获取特定年份的统计数�?
    @Query(
        "SELECT COUNT(DISTINCT bookName) as bookCount, " +
        "(SELECT COUNT(DISTINCT bookName) FROM readingMemories " +
        "WHERE progress >= 100.0 AND " +
        "CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = :year) as finishedBookCount, " +
        "0 as abandonedBookCount, " +
        "0 as totalWords, " +
        "((SELECT COUNT(*) FROM bookReviews " +
        "WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = :year) + " +
        "(SELECT COUNT(*) FROM bookAnnotations " +
        "WHERE note != '' AND note IS NOT NULL AND " +
        "CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = :year)) as reviewCount, " +
        "SUM(duration) as totalTime, " +
        "strftime('%Y', startTime / 1000, 'unixepoch', 'localtime') as date, " +
        "COUNT(DISTINCT CASE WHEN startTime = 0 THEN '未知日期' ELSE DATE(startTime / 1000, 'unixepoch', 'localtime') END) as readDays " +
        "FROM readSession " +
        "WHERE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') = :year " +
        "GROUP BY strftime('%Y', endTime / 1000, 'unixepoch', 'localtime')"
    )
    suspend fun getYearlyStatisticsByYear(year: String): List<ReadStatistics>

    // 从ReadRecordDao合并的方�?
    @get:Query(
        """
        select bookName, author, sum(duration) as readTime, max(endTime) as lastRead 
        from readSession 
        group by bookName, author 
        order by bookName collate localized"""
    )
    val allShow: List<ReadRecordShow>

    @get:Query("select sum(duration) from readSession")
    val allTime: Long

    @Query(
        """
        select bookName, author, sum(duration) as readTime, max(endTime) as lastRead 
        from readSession 
        where bookName like '%' || :searchKey || '%' or author like '%' || :searchKey || '%'
        group by bookName, author 
        order by bookName collate localized"""
    )
    fun search(searchKey: String): List<ReadRecordShow>

    @Query("select sum(duration) from readSession where bookUrl = :bookUrl")
    fun getReadTimeByUrl(bookUrl: String): Long?
    
    @Query("select * from readSession where bookUrl = :bookUrl")
    suspend fun getByUrl(bookUrl: String): List<ReadSession>
    
    @Query("select * from readSession where bookName = :bookName and author = :author")
    fun getByBookSync(bookName: String, author: String): List<ReadSession>?

    @Query("delete from readSession where bookName = :bookName")
    fun deleteByName(bookName: String)

    // 总计统计 - 从readSession表获取阅读时间和阅读天数，更准确
    @Query(
            """
        SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, 
            (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
            WHERE progress >= 100.0) as finishedBookCount, 
            (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
            WHERE readingStatus = 'ABANDONED') as abandonedBookCount, 
            (SELECT SUM(wordCount) FROM readingMemories 
            WHERE wordCount IS NOT NULL AND wordCount != '') as totalWords, 
            ((SELECT COUNT(*) FROM bookReviews) + (SELECT COUNT(*) FROM bookAnnotations WHERE note != '' AND note IS NOT NULL)) as reviewCount, 
            SUM(duration) as totalTime, 
            COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, '' as date 
        FROM readSession
        """)
        suspend fun getTotalStatisticsNew(): ReadStatistics

    // 总计统计 - 按类型筛�?
    @Query(
            """
        SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount, 
            (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
            WHERE progress >= 100.0) as finishedBookCount, 
            (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
            WHERE readingStatus = 'ABANDONED') as abandonedBookCount, 
            (SELECT SUM(wordCount) FROM readingMemories 
            WHERE wordCount IS NOT NULL AND wordCount != '') as totalWords, 
            ((SELECT COUNT(*) FROM bookReviews) + (SELECT COUNT(*) FROM bookAnnotations WHERE note != '' AND note IS NOT NULL)) as reviewCount, 
            SUM(duration) as totalTime, 
            COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, '' as date 
        FROM readSession
        WHERE type = :type
        """)
        suspend fun getTotalStatisticsByType(type: Int): ReadStatistics

    // 每日统计 - 从readSession表获取数据，将每一次阅读会话的时长归到正确的日期中
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '未知日期' ELSE DATE(finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               1 as readDays, 
               CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getDailyStatisticsNew(): List<ReadStatistics>

    // 每日统计 - 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '未知日期' ELSE DATE(finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               1 as readDays, 
               CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getDailyStatisticsByType(type: Int): List<ReadStatistics>

    // 每月统计 - 从readSession表获取数据，将每一次阅读会话的时长归到正确的月份中
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getMonthlyStatisticsNew(): List<ReadStatistics>

    // 每月统计 - 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getMonthlyStatisticsByType(type: Int): List<ReadStatistics>

    // 每年统计 - 从readSession表获取数据，将每一次阅读会话的时长归到正确的年份中
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000' ELSE strftime('%Y', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getYearlyStatisticsNew(): List<ReadStatistics>

    // 每年统计 - 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000' ELSE strftime('%Y', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
    """)
        suspend fun getYearlyStatisticsByType(type: Int): List<ReadStatistics>
    
    // 获取特定年份的统计数�?- �?readSession 表获取数据，基于阅读会话的结束时�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount,
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories
                WHERE progress >= 100.0 AND
                CASE WHEN finishReadTime = 0 THEN '0000' ELSE strftime('%Y', finishReadTime / 1000, 'unixepoch', 'localtime') END = :year) as finishedBookCount,
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories
                WHERE readingStatus = 'ABANDONED' AND
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = :year) as abandonedBookCount,
               (SELECT SUM(wordCount) FROM readingMemories
                WHERE wordCount IS NOT NULL AND wordCount != '' AND
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = :year) as totalWords,
               ((SELECT COUNT(*) FROM bookReviews
                WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = :year) +
                (SELECT COUNT(*) FROM bookAnnotations
                WHERE note != '' AND note IS NOT NULL AND
                CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = :year)) as reviewCount,
               SUM(duration) as totalTime,
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays,
               CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession
        WHERE CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END = :year
        GROUP BY CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getYearlyStatisticsByYearNew(year: String): List<ReadStatistics>

    // 获取特定年份的统计数�?- 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000' ELSE strftime('%Y', finishReadTime / 1000, 'unixepoch', 'localtime') END = :year) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = :year) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = :year) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = :year) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = :year)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END = :year
          AND type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getYearlyStatisticsByYearAndType(year: String, type: Int): List<ReadStatistics>


    
    // 获取特定日期的统计数�?- �?readSession 表获取数据，基于阅读会话的结束时�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '未知日期' ELSE DATE(finishReadTime / 1000, 'unixepoch', 'localtime') END = :date) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = :date) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = :date) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = :date) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = :date)) as reviewCount, 
               SUM(duration) as totalTime, 
               1 as readDays, 
               CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END = :date
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getDailyStatisticsByDateNew(date: String): List<ReadStatistics>

    // 获取特定日期的统计数�?- 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '未知日期' ELSE DATE(finishReadTime / 1000, 'unixepoch', 'localtime') END = :date) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = :date) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = :date) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = :date) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = :date)) as reviewCount, 
               SUM(duration) as totalTime, 
               1 as readDays, 
               CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END = :date
          AND type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getDailyStatisticsByDateAndType(date: String, type: Int): List<ReadStatistics>

    // 获取特定月份的统计数�?- �?readSession 表获取数据，基于阅读会话的结束时�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END = :month
        GROUP BY CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getMonthlyStatisticsByMonthNew(month: String): List<ReadStatistics>

    // 获取特定月份的统计数�?- 按类型筛�?
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END = :month
          AND type = :type
        GROUP BY CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END
    """)
        suspend fun getMonthlyStatisticsByMonthAndType(month: String, type: Int): List<ReadStatistics>
    

    
    // 获取总阅读时间排行TOP10 - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getTotalReadTimeTop10(): List<BookReadTimeRank>

    // 获取总阅读时间排行TOP10 - 按类型筛�?
    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE type = :type
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getTotalReadTimeTop10ByType(type: Int): List<BookReadTimeRank>
    
    // 获取特定日期阅读时间排行TOP10 - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END = :date
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getDailyReadTimeTop10(date: String): List<BookReadTimeRank>
    
    // 获取特定月份阅读时间排行TOP10 - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END = :month
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getMonthlyReadTimeTop10(month: String): List<BookReadTimeRank>

    // 获取特定年份阅读时间排行TOP10 - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END = :year
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getYearlyReadTimeTop10(year: String): List<BookReadTimeRank>

    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END = :date
          AND type = :type
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getDailyReadTimeTop10ByType(date: String, type: Int): List<BookReadTimeRank>

    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END = :month
          AND type = :type
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getMonthlyReadTimeTop10ByType(month: String, type: Int): List<BookReadTimeRank>

    @Query("""
        SELECT bookName, SUM(duration) as readTime 
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END = :year
          AND type = :type
        GROUP BY bookName 
        ORDER BY readTime DESC
        LIMIT 10
    """)
    suspend fun getYearlyReadTimeTop10ByType(year: String, type: Int): List<BookReadTimeRank>
    

    
    // 获取特定书籍的最近阅读记�?
    @Query("SELECT * FROM readSession WHERE bookName = :bookName ORDER BY startTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentReadingByBook(bookName: String, limit: Int, offset: Int): List<ReadSession>
    
    // 分页获取每日统计数据
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '未知日期' ELSE DATE(finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '未知日期' ELSE DATE(updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '未知日期' ELSE DATE(createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '未知日期' ELSE DATE(time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '未知日期' ELSE DATE(readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               1 as readDays, 
               CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getDailyStatistics(limit: Int, offset: Int): List<ReadStatistics>
    
    // 分页获取每月统计数据
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000-00' ELSE strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMonthlyStatistics(limit: Int, offset: Int): List<ReadStatistics>
    
    // 分页获取每年统计数据
    @Query("""
        SELECT COUNT(DISTINCT bookName) as bookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE progress >= 100.0 AND 
                CASE WHEN finishReadTime = 0 THEN '0000' ELSE strftime('%Y', finishReadTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as finishedBookCount, 
               (SELECT COUNT(DISTINCT bookName) FROM readingMemories 
                WHERE readingStatus = 'ABANDONED' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as abandonedBookCount, 
               (SELECT SUM(wordCount) FROM readingMemories 
                WHERE wordCount IS NOT NULL AND wordCount != '' AND 
                CASE WHEN updateTime = 0 THEN '0000' ELSE strftime('%Y', updateTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) as totalWords, 
               ((SELECT COUNT(*) FROM bookReviews 
                WHERE CASE WHEN createTime = 0 THEN '0000' ELSE strftime('%Y', createTime / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END) + 
                (SELECT COUNT(*) FROM bookAnnotations 
                WHERE note != '' AND note IS NOT NULL AND 
                CASE WHEN time = 0 THEN '0000' ELSE strftime('%Y', time / 1000, 'unixepoch', 'localtime') END = 
                CASE WHEN readSession.endTime = 0 THEN '0000' ELSE strftime('%Y', readSession.endTime / 1000, 'unixepoch', 'localtime') END)) as reviewCount, 
               SUM(duration) as totalTime, 
               COUNT(DISTINCT CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END) as readDays, 
               CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END as date
        FROM readSession 
        GROUP BY CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getYearlyStatistics(limit: Int, offset: Int): List<ReadStatistics>
    
    // 获取特定月份的每日阅读时长（用于月度热力图） - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT 
            CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date,
            SUM(duration) / 60000 as readMinutes
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000-00' ELSE strftime('%Y-%m', endTime / 1000, 'unixepoch', 'localtime') END = :month
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date
    """)
    suspend fun getMonthlyReadHeatmapData(month: String): List<DailyReadTime>

    // 获取特定年份的每日阅读时长（用于年度热力图） - 从readSession表获取数据，基于真实的阅读会�?
    @Query("""
        SELECT 
            CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END as date,
            SUM(duration) / 60000 as readMinutes
        FROM readSession 
        WHERE CASE WHEN endTime = 0 THEN '0000' ELSE strftime('%Y', endTime / 1000, 'unixepoch', 'localtime') END = :year
        GROUP BY CASE WHEN endTime = 0 THEN '未知日期' ELSE DATE(endTime / 1000, 'unixepoch', 'localtime') END
        ORDER BY date
    """)
    suspend fun getYearlyReadHeatmapData(year: String): List<DailyReadTime>

    @Query("SELECT COUNT(*) FROM readSession")
    fun getAllOpenCount(): Int

    @Query("SELECT COUNT(*) FROM readSession WHERE bookName = :bookName AND type = :type")
    suspend fun getTypeCount(bookName: String, type: String): Int

    @Query("SELECT * FROM readSession WHERE bookName = :bookName ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestByBookName(bookName: String): ReadSession?

    /**
     * 更新指定书籍的所有阅读会话的书名
     */
    @Query("UPDATE readSession SET bookName = :newBookName WHERE bookName = :oldBookName")
    suspend fun updateBookName(oldBookName: String, newBookName: String)

    /**
     * 更新指定书籍的所有阅读会话的封面
     */
    @Query("UPDATE readSession SET coverUrl = :coverUrl WHERE bookName = :bookName")
    suspend fun updateCoverUrl(bookName: String, coverUrl: String)

    /**
     * 更新指定书籍在指定时间范围内的阅读会话的章节标题
     */
    @Query("UPDATE readSession SET durChapterTitle = :chapterTitle WHERE bookName = :bookName AND startTime >= :startTime AND endTime <= :endTime")
    suspend fun updateChapterTitle(bookName: String, chapterTitle: String, startTime: Long, endTime: Long)

    /**
     * 更新指定书籍的所有阅读会话的显示名称
     */
    @Query("UPDATE readSession SET displayName = :displayName WHERE bookName = :bookName")
    suspend fun updateDisplayName(bookName: String, displayName: String)

    @Query("""
        SELECT 
            date,
            bookName,
            coverUrl,
            totalDuration
        FROM (
            SELECT 
                DATE(startTime / 1000, 'unixepoch', 'localtime') as date,
                bookName,
                coverUrl,
                SUM(duration) as totalDuration,
                ROW_NUMBER() OVER (
                    PARTITION BY DATE(startTime / 1000, 'unixepoch', 'localtime') 
                    ORDER BY SUM(duration) DESC
                ) as rn
            FROM readSession
            WHERE strftime('%Y-%m', startTime / 1000, 'unixepoch', 'localtime') = :month
            GROUP BY DATE(startTime / 1000, 'unixepoch', 'localtime'), bookName
        )
        WHERE rn = 1
        ORDER BY date
    """)
    suspend fun getMonthlyDailyLongestReadCovers(month: String): List<DailyLongestReadCover>

    /**
     * 获取指定月份每天阅读时长最长的书籍封面（按类型过滤�?     * 使用位标志匹配：type 字段的每一位代表一个类型（text=0b1000, audio=0b100000, video=0b100�?     * (type & :type) > 0 表示只要包含指定类型位即匹配
     */
    @Query("""
        SELECT 
            date,
            bookName,
            coverUrl,
            totalDuration
        FROM (
            SELECT 
                DATE(startTime / 1000, 'unixepoch', 'localtime') as date,
                bookName,
                coverUrl,
                SUM(duration) as totalDuration,
                ROW_NUMBER() OVER (
                    PARTITION BY DATE(startTime / 1000, 'unixepoch', 'localtime') 
                    ORDER BY SUM(duration) DESC
                ) as rn
            FROM readSession
            WHERE strftime('%Y-%m', startTime / 1000, 'unixepoch', 'localtime') = :month
              AND (type & :type) > 0
            GROUP BY DATE(startTime / 1000, 'unixepoch', 'localtime'), bookName
        )
        WHERE rn = 1
        ORDER BY date
    """)
    suspend fun getMonthlyDailyLongestReadCoversByType(month: String, type: Int): List<DailyLongestReadCover>

    // 按类型查询读过本�?- 用于书影音统�?
    @Query("""
        SELECT COUNT(DISTINCT bookName || '|' || author) as bookCount
        FROM readSession
        WHERE (type & :type) > 0
    """)
    suspend fun getBookCountByType(type: Int): Int

}
