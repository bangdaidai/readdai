package io.legado.app.utils

import io.legado.app.data.entities.HeatmapDayData

/**
 * 热力图缓存管理器
 * 用于缓存月度和年度热力图数据，避免重复绘制和加载
 * 已简化，实际缓存逻辑已合并到StatisticsCacheManager
 */
object HeatmapCacheManager {
    
    /**
     * 获取月度热力图数据
     * @param month 月份字符串，格式为"yyyy-MM"
     * @return 热力图数据列表，如果缓存中没有则返回null
     */
    fun getMonthlyHeatmapData(month: String): List<HeatmapDayData>? {
        // 使用StatisticsCacheManager统一管理缓存
        return null
    }
    
    /**
     * 缓存月度热力图数据
     * @param month 月份字符串，格式为"yyyy-MM"
     * @param data 热力图数据列表
     */
    fun cacheMonthlyHeatmapData(month: String, data: List<HeatmapDayData>) {
        // 使用StatisticsCacheManager统一管理缓存
    }
    
    /**
     * 获取年度热力图数据
     * @param year 年份字符串，格式为"yyyy"
     * @return 热力图数据列表，如果缓存中没有则返回null
     */
    fun getYearlyHeatmapData(year: String): List<HeatmapDayData>? {
        // 使用StatisticsCacheManager统一管理缓存
        return null
    }
    
    /**
     * 缓存年度热力图数据
     * @param year 年份字符串，格式为"yyyy"
     * @param data 热力图数据列表
     */
    fun cacheYearlyHeatmapData(year: String, data: List<HeatmapDayData>) {
        // 使用StatisticsCacheManager统一管理缓存
    }
    
    /**
     * 检查是否已加载过指定月份的数据
     * @param month 月份字符串，格式为"yyyy-MM"
     * @return 如果已加载则返回true，否则返回false
     */
    fun isMonthLoaded(month: String): Boolean {
        // 使用StatisticsCacheManager统一管理缓存
        return false
    }
    
    /**
     * 检查是否已加载过指定年份的数据
     * @param year 年份字符串，格式为"yyyy"
     * @return 如果已加载则返回true，否则返回false
     */
    fun isYearLoaded(year: String): Boolean {
        // 使用StatisticsCacheManager统一管理缓存
        return false
    }
    
    /**
     * 清除所有缓存
     */
    fun clearCache() {
        // 使用StatisticsCacheManager统一管理缓存
        StatisticsCacheManager.clearCache(StatisticsCacheManager.CacheType.HEATMAP)
    }
    
    /**
     * 清除月度缓存
     */
    fun clearMonthlyCache() {
        // 使用StatisticsCacheManager统一管理缓存
        StatisticsCacheManager.clearCache(StatisticsCacheManager.CacheType.HEATMAP)
    }
    
    /**
     * 清除年度缓存
     */
    fun clearYearlyCache() {
        // 使用StatisticsCacheManager统一管理缓存
        StatisticsCacheManager.clearCache(StatisticsCacheManager.CacheType.HEATMAP)
    }
}