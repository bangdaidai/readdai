package io.legado.app.data.entities

/**
 * 热力图日期数据类
 */
data class HeatmapDayData(
    val date: String,           // 日期，格式：yyyy-MM-dd
    val readMinutes: Int,       // 阅读时长（分钟）
    val dayOfWeek: Int,         // 星期几，1-7（周一到周日）
    val weekOfMonth: Int,       // 月份中的第几周
    val isCurrentMonth: Boolean = true  // 是否为当前月份，用于区分灰度显示
)