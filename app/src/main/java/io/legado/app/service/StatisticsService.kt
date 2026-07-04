package io.legado.app.service

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookReadTimeRank
import io.legado.app.data.entities.DailyReadTime
import io.legado.app.data.entities.ReadStatistics
import io.legado.app.data.entities.HourReadTime
import io.legado.app.data.entities.AuthorReadTime
import io.legado.app.data.entities.TagReadCount
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.StatisticsCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * 统计服务，统一处理各种统计请求
 */
object StatisticsService {

    private val scope = MainScope()

    /**
     * 获取总计统计数据
     */
    suspend fun getTotalStatistics(): ReadStatistics {
        val cacheKey = "total"
        // 尝试从缓存获取
        StatisticsCacheManager.getTotalStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getTotalStatisticsNew()
        
        // 缓存结果
        StatisticsCacheManager.cacheTotalStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取总计统计数据
     */
    suspend fun getTotalStatisticsByType(type: Int): ReadStatistics {
        val cacheKey = "total_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getTotalStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getTotalStatisticsByType(type)
        
        // 缓存结果
        StatisticsCacheManager.cacheTotalStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取每日统计数据
     */
    suspend fun getDailyStatistics(): List<ReadStatistics> {
        val cacheKey = "daily"
        // 尝试从缓存获取
        StatisticsCacheManager.getDailyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getDailyStatisticsNew()
        
        // 缓存结果
        StatisticsCacheManager.cacheDailyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取每日统计数据
     */
    suspend fun getDailyStatisticsByType(type: Int): List<ReadStatistics> {
        val cacheKey = "daily_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getDailyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getDailyStatisticsByType(type)
        
        // 缓存结果
        StatisticsCacheManager.cacheDailyStatistics(cacheKey, data)
        return data
    }

    /**
     * 分页获取每日统计数据
     */
    suspend fun getDailyStatistics(limit: Int, offset: Int): List<ReadStatistics> {
        val cacheKey = "daily_${limit}_$offset"
        // 尝试从缓存获取
        StatisticsCacheManager.getDailyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getDailyStatistics(limit, offset)
        
        // 缓存结果
        StatisticsCacheManager.cacheDailyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取特定日期的统计数据
     */
    suspend fun getDailyStatisticsByDate(date: String): List<ReadStatistics> {
        val cacheKey = "daily_$date"
        // 尝试从缓存获取
        StatisticsCacheManager.getDailyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getDailyStatisticsByDateNew(date)
        
        // 缓存结果
        StatisticsCacheManager.cacheDailyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取特定日期的统计数据
     */
    suspend fun getDailyStatisticsByDateAndType(date: String, type: Int): List<ReadStatistics> {
        val cacheKey = "daily_${date}_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getDailyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getDailyStatisticsByDateAndType(date, type)
        
        // 缓存结果
        StatisticsCacheManager.cacheDailyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取每月统计数据
     */
    suspend fun getMonthlyStatistics(): List<ReadStatistics> {
        val cacheKey = "monthly"
        // 尝试从缓存获取
        StatisticsCacheManager.getMonthlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getMonthlyStatisticsNew()
        
        // 缓存结果
        StatisticsCacheManager.cacheMonthlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取每月统计数据
     */
    suspend fun getMonthlyStatisticsByType(type: Int): List<ReadStatistics> {
        val cacheKey = "monthly_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getMonthlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getMonthlyStatisticsByType(type)
        
        // 缓存结果
        StatisticsCacheManager.cacheMonthlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 分页获取每月统计数据
     */
    suspend fun getMonthlyStatistics(limit: Int, offset: Int): List<ReadStatistics> {
        val cacheKey = "monthly_${limit}_$offset"
        // 尝试从缓存获取
        StatisticsCacheManager.getMonthlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getMonthlyStatistics(limit, offset)
        
        // 缓存结果
        StatisticsCacheManager.cacheMonthlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取特定月份的统计数据
     */
    suspend fun getMonthlyStatisticsByMonth(month: String): List<ReadStatistics> {
        val cacheKey = "monthly_$month"
        // 尝试从缓存获取
        StatisticsCacheManager.getMonthlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getMonthlyStatisticsByMonthNew(month)
        
        // 缓存结果
        StatisticsCacheManager.cacheMonthlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取特定月份的统计数据
     */
    suspend fun getMonthlyStatisticsByMonthAndType(month: String, type: Int): List<ReadStatistics> {
        val cacheKey = "monthly_${month}_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getMonthlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getMonthlyStatisticsByMonthAndType(month, type)
        
        // 缓存结果
        StatisticsCacheManager.cacheMonthlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取每年统计数据
     */
    suspend fun getYearlyStatistics(): List<ReadStatistics> {
        val cacheKey = "yearly"
        // 尝试从缓存获取
        StatisticsCacheManager.getYearlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getYearlyStatisticsNew()
        
        // 缓存结果
        StatisticsCacheManager.cacheYearlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取每年统计数据
     */
    suspend fun getYearlyStatisticsByType(type: Int): List<ReadStatistics> {
        val cacheKey = "yearly_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getYearlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getYearlyStatisticsByType(type)
        
        // 缓存结果
        StatisticsCacheManager.cacheYearlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 分页获取每年统计数据
     */
    suspend fun getYearlyStatistics(limit: Int, offset: Int): List<ReadStatistics> {
        val cacheKey = "yearly_${limit}_$offset"
        // 尝试从缓存获取
        StatisticsCacheManager.getYearlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getYearlyStatistics(limit, offset)
        
        // 缓存结果
        StatisticsCacheManager.cacheYearlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取特定年份的统计数据
     */
    suspend fun getYearlyStatisticsByYear(year: String): List<ReadStatistics> {
        val cacheKey = "yearly_$year"
        // 尝试从缓存获取
        StatisticsCacheManager.getYearlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getYearlyStatisticsByYearNew(year)
        
        // 缓存结果
        StatisticsCacheManager.cacheYearlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取特定年份的统计数据
     */
    suspend fun getYearlyStatisticsByYearAndType(year: String, type: Int): List<ReadStatistics> {
        val cacheKey = "yearly_${year}_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getYearlyStatisticsCache(cacheKey)?.let { return it }
        
        // 从数据库获取，使用新的查询方法以确保数据一致性
        val data = appDb.readSessionDao.getYearlyStatisticsByYearAndType(year, type)
        
        // 缓存结果
        StatisticsCacheManager.cacheYearlyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取每周统计数据（自然周，周一到周日）
     */
    suspend fun getWeeklyStatisticsByWeek(weekStr: String): List<ReadStatistics> {
        val cacheKey = "weekly_$weekStr"
        StatisticsCacheManager.getWeeklyStatisticsCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getWeeklyStatisticsByWeekNew(weekStr)
        StatisticsCacheManager.cacheWeeklyStatistics(cacheKey, data)
        return data
    }

    /**
     * 按类型获取每周统计数据
     */
    suspend fun getWeeklyStatisticsByWeekAndType(weekStr: String, type: Int): List<ReadStatistics> {
        val cacheKey = "weekly_${weekStr}_${type}"
        StatisticsCacheManager.getWeeklyStatisticsCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getWeeklyStatisticsByWeekAndType(weekStr, type)
        StatisticsCacheManager.cacheWeeklyStatistics(cacheKey, data)
        return data
    }

    /**
     * 获取总阅读时间排行TOP10
     */
    suspend fun getTotalReadTimeTop10(): List<BookReadTimeRank> {
        val cacheKey = "top10_total"
        // 尝试从缓存获取
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getTotalReadTimeTop10()
        
        // 缓存结果
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    /**
     * 按类型获取总阅读时间排行TOP10
     */
    suspend fun getTotalReadTimeTop10ByType(type: Int): List<BookReadTimeRank> {
        val cacheKey = "top10_total_${type}"
        // 尝试从缓存获取
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getTotalReadTimeTop10ByType(type)
        
        // 缓存结果
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    /**
     * 获取特定日期阅读时间排行TOP10
     */
    suspend fun getDailyReadTimeTop10(date: String): List<BookReadTimeRank> {
        val cacheKey = "top10_daily_$date"
        // 尝试从缓存获取
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getDailyReadTimeTop10(date)
        
        // 缓存结果
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    /**
     * 获取特定月份阅读时间排行TOP10
     */
    suspend fun getMonthlyReadTimeTop10(month: String): List<BookReadTimeRank> {
        val cacheKey = "top10_monthly_$month"
        // 尝试从缓存获取
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getMonthlyReadTimeTop10(month)
        
        // 缓存结果
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    /**
     * 获取特定年份阅读时间排行TOP10
     */
    suspend fun getYearlyReadTimeTop10(year: String): List<BookReadTimeRank> {
        val cacheKey = "top10_yearly_$year"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getYearlyReadTimeTop10(year)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    suspend fun getDailyReadTimeTop10ByType(date: String, type: Int): List<BookReadTimeRank> {
        val cacheKey = "top10_daily_${date}_$type"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getDailyReadTimeTop10ByType(date, type)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    suspend fun getMonthlyReadTimeTop10ByType(month: String, type: Int): List<BookReadTimeRank> {
        val cacheKey = "top10_monthly_${month}_$type"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getMonthlyReadTimeTop10ByType(month, type)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    suspend fun getYearlyReadTimeTop10ByType(year: String, type: Int): List<BookReadTimeRank> {
        val cacheKey = "top10_yearly_${year}_$type"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getYearlyReadTimeTop10ByType(year, type)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    suspend fun getWeeklyReadTimeTop10(weekStr: String): List<BookReadTimeRank> {
        val cacheKey = "top10_weekly_$weekStr"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getWeeklyReadTimeTop10(weekStr)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    suspend fun getWeeklyReadTimeTop10ByType(weekStr: String, type: Int): List<BookReadTimeRank> {
        val cacheKey = "top10_weekly_${weekStr}_$type"
        StatisticsCacheManager.getTop10Cache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getWeeklyReadTimeTop10ByType(weekStr, type)
        StatisticsCacheManager.cacheTop10Data(cacheKey, data)
        return data
    }

    /**
     * 获取特定月份的每日阅读时长（用于月度热力图）
     */
    suspend fun getMonthlyReadHeatmapData(month: String): List<DailyReadTime> {
        val cacheKey = "heatmap_monthly_$month"
        // 尝试从缓存获取
        StatisticsCacheManager.getHeatmapCache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getMonthlyReadHeatmapData(month)
        
        // 缓存结果
        StatisticsCacheManager.cacheHeatmapData(cacheKey, data)
        return data
    }

    /**
     * 获取特定年份的每日阅读时长（用于年度热力图）
     */
    suspend fun getYearlyReadHeatmapData(year: String): List<DailyReadTime> {
        val cacheKey = "heatmap_yearly_$year"
        // 尝试从缓存获取
        StatisticsCacheManager.getHeatmapCache(cacheKey)?.let { return it }
        
        // 从数据库获取
        val data = appDb.readSessionDao.getYearlyReadHeatmapData(year)
        
        // 缓存结果
        StatisticsCacheManager.cacheHeatmapData(cacheKey, data)
        return data
    }

    /**
     * 异步获取总计统计数据
     */
    fun getTotalStatistics(callback: (ReadStatistics) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getTotalStatistics()
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步按类型获取总计统计数据
     */
    fun getTotalStatisticsByType(type: Int, callback: (ReadStatistics) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getTotalStatisticsByType(type)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取每日统计数据
     */
    fun getDailyStatistics(callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getDailyStatistics()
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步按类型获取每日统计数据
     */
    fun getDailyStatisticsByType(type: Int, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getDailyStatisticsByType(type)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取特定日期的统计数据
     */
    fun getDailyStatisticsByDate(date: String, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getDailyStatisticsByDate(date)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取每月统计数据
     */
    fun getMonthlyStatistics(callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getMonthlyStatistics()
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步按类型获取每月统计数据
     */
    fun getMonthlyStatisticsByType(type: Int, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getMonthlyStatisticsByType(type)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取特定月份的统计数据
     */
    fun getMonthlyStatisticsByMonth(month: String, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getMonthlyStatisticsByMonth(month)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取每年统计数据
     */
    fun getYearlyStatistics(callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getYearlyStatistics()
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步按类型获取每年统计数据
     */
    fun getYearlyStatisticsByType(type: Int, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getYearlyStatisticsByType(type)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取特定年份的统计数据
     */
    fun getYearlyStatisticsByYear(year: String, callback: (List<ReadStatistics>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val statistics = getYearlyStatisticsByYear(year)
            launch(Dispatchers.Main) { callback(statistics) }
        }
    }

    /**
     * 异步获取总阅读时间排行TOP10
     */
    fun getTotalReadTimeTop10(callback: (List<BookReadTimeRank>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val top10 = getTotalReadTimeTop10()
            launch(Dispatchers.Main) { callback(top10) }
        }
    }

    /**
     * 异步按类型获取总阅读时间排行TOP10
     */
    fun getTotalReadTimeTop10ByType(type: Int, callback: (List<BookReadTimeRank>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val top10 = getTotalReadTimeTop10ByType(type)
            launch(Dispatchers.Main) { callback(top10) }
        }
    }

    /**
     * 异步获取特定日期阅读时间排行TOP10
     */
    fun getDailyReadTimeTop10(date: String, callback: (List<BookReadTimeRank>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val top10 = getDailyReadTimeTop10(date)
            launch(Dispatchers.Main) { callback(top10) }
        }
    }

    /**
     * 异步获取特定月份阅读时间排行TOP10
     */
    fun getMonthlyReadTimeTop10(month: String, callback: (List<BookReadTimeRank>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val top10 = getMonthlyReadTimeTop10(month)
            launch(Dispatchers.Main) { callback(top10) }
        }
    }

    /**
     * 异步获取特定年份阅读时间排行TOP10
     */
    fun getYearlyReadTimeTop10(year: String, callback: (List<BookReadTimeRank>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val top10 = getYearlyReadTimeTop10(year)
            launch(Dispatchers.Main) { callback(top10) }
        }
    }

    /**
     * 异步获取特定月份的每日阅读时长（用于月度热力图）
     */
    fun getMonthlyReadHeatmapData(month: String, callback: (List<DailyReadTime>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val data = getMonthlyReadHeatmapData(month)
            launch(Dispatchers.Main) { callback(data) }
        }
    }

    /**
     * 异步获取特定年份的每日阅读时长（用于年度热力图）
     */
    fun getYearlyReadHeatmapData(year: String, callback: (List<DailyReadTime>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val data = getYearlyReadHeatmapData(year)
            launch(Dispatchers.Main) { callback(data) }
        }
    }

    // ===== 分析数据 =====

    suspend fun getHourlyDistribution(): List<HourReadTime> {
        val cacheKey = "hourly_total"
        StatisticsCacheManager.getHourlyDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getHourlyReadTimeDistribution()
        StatisticsCacheManager.cacheHourlyData(cacheKey, data)
        return data
    }

    suspend fun getHourlyDistributionByType(type: Int): List<HourReadTime> {
        val cacheKey = "hourly_total_$type"
        StatisticsCacheManager.getHourlyDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getHourlyReadTimeDistributionByType(type)
        StatisticsCacheManager.cacheHourlyData(cacheKey, data)
        return data
    }

    suspend fun getHourlyDistributionInRange(startTime: Long, endTime: Long): List<HourReadTime> {
        val cacheKey = "hourly_${startTime}_$endTime"
        StatisticsCacheManager.getHourlyDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getHourlyReadTimeInRange(startTime, endTime)
        StatisticsCacheManager.cacheHourlyData(cacheKey, data)
        return data
    }

    suspend fun getHourlyDistributionInRangeByType(startTime: Long, endTime: Long, type: Int): List<HourReadTime> {
        val cacheKey = "hourly_${startTime}_${endTime}_$type"
        StatisticsCacheManager.getHourlyDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getHourlyReadTimeInRangeByType(startTime, endTime, type)
        StatisticsCacheManager.cacheHourlyData(cacheKey, data)
        return data
    }

    suspend fun getAuthorTop5(): List<AuthorReadTime> {
        val cacheKey = "author_total"
        StatisticsCacheManager.getAuthorDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getAuthorReadTimeTop5()
        StatisticsCacheManager.cacheAuthorData(cacheKey, data)
        return data
    }

    suspend fun getAuthorTop5ByType(type: Int): List<AuthorReadTime> {
        val cacheKey = "author_total_$type"
        StatisticsCacheManager.getAuthorDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getAuthorReadTimeTop5ByType(type)
        StatisticsCacheManager.cacheAuthorData(cacheKey, data)
        return data
    }

    suspend fun getAuthorTop5InRange(startTime: Long, endTime: Long): List<AuthorReadTime> {
        val cacheKey = "author_${startTime}_$endTime"
        StatisticsCacheManager.getAuthorDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getAuthorReadTimeTop5InRange(startTime, endTime)
        StatisticsCacheManager.cacheAuthorData(cacheKey, data)
        return data
    }

    suspend fun getAuthorTop5InRangeByType(startTime: Long, endTime: Long, type: Int): List<AuthorReadTime> {
        val cacheKey = "author_${startTime}_${endTime}_$type"
        StatisticsCacheManager.getAuthorDataCache(cacheKey)?.let { return it }
        val data = appDb.readSessionDao.getAuthorReadTimeTop5InRangeByType(startTime, endTime, type)
        StatisticsCacheManager.cacheAuthorData(cacheKey, data)
        return data
    }

    suspend fun getTagTop5(): List<TagReadCount> {
        val cacheKey = "tag_total"
        StatisticsCacheManager.getTagDataCache(cacheKey)?.let { return it }
        val bookNames = appDb.readSessionDao.getDistinctBookNames()
        val result = computeTagTop5(bookNames)
        StatisticsCacheManager.cacheTagData(cacheKey, result)
        return result
    }

    suspend fun getTagTop5InRange(startTime: Long, endTime: Long): List<TagReadCount> {
        val cacheKey = "tag_${startTime}_$endTime"
        StatisticsCacheManager.getTagDataCache(cacheKey)?.let { return it }
        val bookNames = appDb.readSessionDao.getDistinctBookNamesInRange(startTime, endTime)
        val result = computeTagTop5(bookNames)
        StatisticsCacheManager.cacheTagData(cacheKey, result)
        return result
    }

    private suspend fun computeTagTop5(bookNames: List<String>): List<TagReadCount> {
        if (bookNames.isEmpty()) return emptyList()
        val kinds = bookNames.chunked(500).flatMap { chunk ->
            appDb.readingMemoryDao.getKindsByBookNames(chunk)
        }
        val tagCounts = mutableMapOf<String, Int>()
        kinds.forEach { kind ->
            kind.split(",", "\n", ";", "，").map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
                tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
            }
        }
        return tagCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { TagReadCount(it.key, it.value, 0L) }
    }

    suspend fun getContinuousReadingDays(): Int {
        val cacheKey = "continuous_total"
        StatisticsCacheManager.getContinuousDaysCache(cacheKey)?.let { return it }
        val dates = appDb.readSessionDao.getAllReadingDates()
        val days = computeContinuousDays(dates)
        StatisticsCacheManager.cacheContinuousDays(cacheKey, days)
        return days
    }

    suspend fun getContinuousReadingDaysInRange(startTime: Long, endTime: Long): Int {
        val cacheKey = "continuous_${startTime}_$endTime"
        StatisticsCacheManager.getContinuousDaysCache(cacheKey)?.let { return it }
        val dates = appDb.readSessionDao.getReadingDatesInRange(startTime, endTime)
        val days = computeContinuousDays(dates)
        StatisticsCacheManager.cacheContinuousDays(cacheKey, days)
        return days
    }

    private fun computeContinuousDays(dates: List<String>): Int {
        if (dates.isEmpty()) return 0
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val sorted = dates.mapNotNull { try { sdf.parse(it) } catch (_: Exception) { null } }
            .map { java.util.Date(it.time).let { it } }
            .distinctBy { sdf.format(it) }
            .sortedBy { it.time }

        if (sorted.isEmpty()) return 0
        var maxStreak = 1
        var currentStreak = 1
        for (i in 1 until sorted.size) {
            val diff = (sorted[i].time - sorted[i - 1].time) / (1000 * 60 * 60 * 24)
            if (diff == 1L) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        return maxStreak
    }

    /**
     * 汇总时段分布数据，计算各时段占比，返回占比最高的时段和百分比
     */
    fun summarizeHourlyDistribution(hourlyData: List<HourReadTime>): Pair<String, Int>? {
        if (hourlyData.isEmpty()) return null
        val periodTotals = mutableMapOf<String, Long>()
        hourlyData.forEach { h ->
            periodTotals[h.period] = (periodTotals[h.period] ?: 0) + h.totalTime
        }
        val total = periodTotals.values.sum()
        if (total == 0L) return null
        val top = periodTotals.maxByOrNull { it.value } ?: return null
        val pct = (top.value * 100 / total).toInt()
        return top.key to pct
    }

}