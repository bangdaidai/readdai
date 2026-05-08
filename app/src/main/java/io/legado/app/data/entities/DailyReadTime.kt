package io.legado.app.data.entities

import androidx.room.Entity

/**
 * 每日阅读时长数据类，用于热力图显示
 */
@Entity
data class DailyReadTime(
    val date: String,  // 格式: yyyy-MM-dd
    val readMinutes: Int  // 阅读分钟数
)