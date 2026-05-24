package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 阅读小票实体类
 * 用于存储书籍的阅读统计信息，在书籍详情页和阅读页尾部展示
 */
@Entity(tableName = "readingTickets")
data class ReadingTicket(
    @PrimaryKey
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String = "", // 书籍URL，作为主键
    
    @ColumnInfo(name = "bookName")
    val bookName: String = "", // 书籍名称
    
    @ColumnInfo(name = "author")
    val author: String = "", // 作者
    
    @ColumnInfo(name = "totalReadTime")
    val totalReadTime: Long = 0L, // 总阅读时长（毫秒）
    
    @ColumnInfo(name = "readCount")
    val readCount: Int = 0, // 阅读次数（N刷）
    
    @ColumnInfo(name = "rating")
    val rating: Float = 0f, // 评分（0-5分）
    
    @ColumnInfo(name = "finishTime")
    val finishTime: Long = 0L, // 读完时间戳
    
    @ColumnInfo(name = "firstReadTime")
    val firstReadTime: Long = 0L, // 首次阅读时间戳
    
    @ColumnInfo(name = "lastReadTime")
    val lastReadTime: Long = 0L, // 最后阅读时间戳
    
    @ColumnInfo(name = "completedChapters")
    val completedChapters: Int = 0, // 已读章节数
    
    @ColumnInfo(name = "totalChapters")
    val totalChapters: Int = 0, // 总章节数
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis() // 小票创建时间
) {
    /**
     * 获取格式化的阅读时长
     */
    fun getFormattedReadTime(): String {
        val hours = totalReadTime / (1000 * 60 * 60)
        val minutes = (totalReadTime % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "不足1分钟"
        }
    }
    
    /**
     * 获取阅读进度百分比
     */
    fun getProgressPercentage(): Int {
        if (totalChapters == 0) return 0
        return (completedChapters * 100 / totalChapters).coerceIn(0, 100)
    }
    
    /**
     * 是否已读完
     */
    fun isFinished(): Boolean {
        return finishTime > 0 || completedChapters >= totalChapters
    }
    
    /**
     * 获取阅读次数显示文本
     */
    fun getReadCountText(): String {
        return when (readCount) {
            0 -> "初读"
            1 -> "二刷"
            2 -> "三刷"
            else -> "${readCount + 1}刷"
        }
    }
}
