package io.legado.app.utils

import io.legado.app.data.entities.BookReadTimeRank
import io.legado.app.data.entities.DailyReadTime
import io.legado.app.data.entities.HeatmapDayData
import io.legado.app.data.entities.ReadStatistics
import io.legado.app.data.entities.HourReadTime
import io.legado.app.data.entities.AuthorReadTime
import io.legado.app.data.entities.TagReadCount
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 统计结果缓存管理器，用于缓存统计查询结果，提高查询性能
 */
object StatisticsCacheManager {

    // 缓存过期时间：1分钟，减少缓存时间以提高数据及时性
    private const val CACHE_EXPIRE_TIME = 60 * 1000L

    // 缓存项数据类
    private data class CacheItem<T>(
        val data: T,
        val timestamp: Long
    )

    // 总计统计缓存
    private val totalStatisticsCache = ConcurrentHashMap<String, CacheItem<ReadStatistics>>()

    // 每日统计缓存
    private val dailyStatisticsCache = ConcurrentHashMap<String, CacheItem<List<ReadStatistics>>>()

    // 每月统计缓存
    private val monthlyStatisticsCache = ConcurrentHashMap<String, CacheItem<List<ReadStatistics>>>()

    // 每年统计缓存
    private val yearlyStatisticsCache = ConcurrentHashMap<String, CacheItem<List<ReadStatistics>>>()

    // 每周统计缓存
    private val weeklyStatisticsCache = ConcurrentHashMap<String, CacheItem<List<ReadStatistics>>>()

    // TOP10 统计缓存
    private val top10Cache = ConcurrentHashMap<String, CacheItem<List<BookReadTimeRank>>>()

    // 热力图数据缓存
    private val heatmapCache = ConcurrentHashMap<String, CacheItem<List<DailyReadTime>>>()
    
    // 热力图DayData缓存
    private val heatmapDayDataCache = ConcurrentHashMap<String, CacheItem<List<HeatmapDayData>>>()

    // 时段分布缓存
    private val hourlyCache = ConcurrentHashMap<String, CacheItem<List<HourReadTime>>>()

    // 作者TOP缓存
    private val authorCache = ConcurrentHashMap<String, CacheItem<List<AuthorReadTime>>>()

    // 标签TOP缓存
    private val tagCache = ConcurrentHashMap<String, CacheItem<List<TagReadCount>>>()

    // 连续阅读天数缓存
    private val continuousDaysCache = ConcurrentHashMap<String, CacheItem<Int>>()

    /**
     * 缓存总计统计数据
     */
    fun cacheTotalStatistics(key: String, data: ReadStatistics) {
        totalStatisticsCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取总计统计数据缓存
     */
    fun getTotalStatisticsCache(key: String): ReadStatistics? {
        return totalStatisticsCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存每日统计数据
     */
    fun cacheDailyStatistics(key: String, data: List<ReadStatistics>) {
        dailyStatisticsCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取每日统计数据缓存
     */
    fun getDailyStatisticsCache(key: String): List<ReadStatistics>? {
        return dailyStatisticsCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存每月统计数据
     */
    fun cacheMonthlyStatistics(key: String, data: List<ReadStatistics>) {
        monthlyStatisticsCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取每月统计数据缓存
     */
    fun getMonthlyStatisticsCache(key: String): List<ReadStatistics>? {
        return monthlyStatisticsCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存每年统计数据
     */
    fun cacheYearlyStatistics(key: String, data: List<ReadStatistics>) {
        yearlyStatisticsCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取每年统计数据缓存
     */
    fun getYearlyStatisticsCache(key: String): List<ReadStatistics>? {
        return yearlyStatisticsCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存每周统计数据
     */
    fun cacheWeeklyStatistics(key: String, data: List<ReadStatistics>) {
        weeklyStatisticsCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取每周统计数据缓存
     */
    fun getWeeklyStatisticsCache(key: String): List<ReadStatistics>? {
        return weeklyStatisticsCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存TOP10统计数据
     */
    fun cacheTop10Data(key: String, data: List<BookReadTimeRank>) {
        top10Cache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取TOP10统计数据缓存
     */
    fun getTop10Cache(key: String): List<BookReadTimeRank>? {
        return top10Cache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存热力图数据
     */
    fun cacheHeatmapData(key: String, data: List<DailyReadTime>) {
        heatmapCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取热力图数据缓存
     */
    fun getHeatmapCache(key: String): List<DailyReadTime>? {
        return heatmapCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    /**
     * 缓存热力图DayData数据
     */
    fun cacheHeatmapDayData(key: String, data: List<HeatmapDayData>) {
        heatmapDayDataCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    /**
     * 获取热力图DayData数据缓存
     */
    fun getHeatmapDayDataCache(key: String): List<HeatmapDayData>? {
        return heatmapDayDataCache[key]?.let { 
            if (isCacheValid(it.timestamp)) it.data else null
        }
    }

    fun cacheHourlyData(key: String, data: List<HourReadTime>) {
        hourlyCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    fun getHourlyDataCache(key: String): List<HourReadTime>? {
        return hourlyCache[key]?.let { if (isCacheValid(it.timestamp)) it.data else null }
    }

    fun cacheAuthorData(key: String, data: List<AuthorReadTime>) {
        authorCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    fun getAuthorDataCache(key: String): List<AuthorReadTime>? {
        return authorCache[key]?.let { if (isCacheValid(it.timestamp)) it.data else null }
    }

    fun cacheTagData(key: String, data: List<TagReadCount>) {
        tagCache[key] = CacheItem(data, System.currentTimeMillis())
    }

    fun getTagDataCache(key: String): List<TagReadCount>? {
        return tagCache[key]?.let { if (isCacheValid(it.timestamp)) it.data else null }
    }

    fun cacheContinuousDays(key: String, days: Int) {
        continuousDaysCache[key] = CacheItem(days, System.currentTimeMillis())
    }

    fun getContinuousDaysCache(key: String): Int? {
        return continuousDaysCache[key]?.let { if (isCacheValid(it.timestamp)) it.data else null }
    }

    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < CACHE_EXPIRE_TIME
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        totalStatisticsCache.clear()
        dailyStatisticsCache.clear()
        monthlyStatisticsCache.clear()
        yearlyStatisticsCache.clear()
        weeklyStatisticsCache.clear()
        top10Cache.clear()
        heatmapCache.clear()
        heatmapDayDataCache.clear()
        hourlyCache.clear()
        authorCache.clear()
        tagCache.clear()
        continuousDaysCache.clear()
    }

    /**
     * 清除特定类型的缓存
     */
    fun clearCache(type: CacheType) {
        when (type) {
            CacheType.TOTAL -> totalStatisticsCache.clear()
            CacheType.DAILY -> dailyStatisticsCache.clear()
            CacheType.MONTHLY -> monthlyStatisticsCache.clear()
            CacheType.YEARLY -> yearlyStatisticsCache.clear()
            CacheType.WEEKLY -> weeklyStatisticsCache.clear()
            CacheType.TOP10 -> top10Cache.clear()
            CacheType.HEATMAP -> {
                heatmapCache.clear()
                heatmapDayDataCache.clear()
            }
        }
    }

    /**
     * 缓存类型枚举
     */
    enum class CacheType {
        TOTAL,
        DAILY,
        MONTHLY,
        YEARLY,
        WEEKLY,
        TOP10,
        HEATMAP
    }

}
