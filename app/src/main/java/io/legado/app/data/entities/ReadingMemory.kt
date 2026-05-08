package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.constant.ReadingStatus
import io.legado.app.help.book.ReadingProgressHelper
import kotlinx.parcelize.Parcelize

/**
 * 我的阅读实体类
 * 用于保存书籍的阅读详情信息，即使书籍从书架删除也会保留
 */
@Parcelize
@Entity(tableName = "readingMemories")
data class ReadingMemory(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: String = "", // 我的阅读记录唯一ID，格式如"memory_时间戳_随机数"
    
    @ColumnInfo(name = "bookUrl")
    var bookUrl: String = "", // 原书籍URL
    
    @ColumnInfo(name = "bookName")
    var bookName: String = "", // 书名
    
    @ColumnInfo(name = "bookAuthor")
    var bookAuthor: String = "", // 作者
    
    @ColumnInfo(name = "wordCount")
    var wordCount: String? = null, // 字数
    
    @ColumnInfo(name = "kind")
    var kind: String? = null, // 分类信息
    
    @ColumnInfo(name = "coverUrl")
    var coverUrl: String? = null, // 封面URL
    
    @ColumnInfo(name = "intro")
    var intro: String? = null, // 简介
    
    @ColumnInfo(name = "rating")
    var rating: Float = 0f, // 评分 (0-5分)
    
    @ColumnInfo(name = "totalChapterNum")
    var totalChapterNum: Int = 0, // 总章节数
    
    @ColumnInfo(name = "durChapterIndex")
    var durChapterIndex: Int = 0, // 当前章节索引
    
    @ColumnInfo(name = "durChapterTitle")
    var durChapterTitle: String? = null, // 当前章节标题
    
    @ColumnInfo(name = "durChapterPos")
    var durChapterPos: Int = 0, // 当前章节位置
    
    @ColumnInfo(name = "progress")
    var progress: Float = 0f, // 阅读进度百分比
    
    @ColumnInfo(name = "readTime")
    var readTime: Long = 0L, // 阅读时长(毫秒)
    
    @ColumnInfo(name = "reviewContent")
    var reviewContent: String? = null, // 书评内容
    
    @ColumnInfo(name = "annotationCount")
    var annotationCount: Int = 0, // 书摘数量
    
    @ColumnInfo(name = "readingStatus")
    var readingStatus: ReadingStatus = ReadingStatus.PENDING, // 阅读状态：未读、阅读中、已读完
    
    @ColumnInfo(name = "userModifiedReadingStatus", defaultValue = "false")
    var userModifiedReadingStatus: Boolean = false, // 标记用户是否修改了阅读状态
    
    @ColumnInfo(name = "createTime")
    var createTime: Long = System.currentTimeMillis(), // 创建时间
    
    @ColumnInfo(name = "updateTime")
    var updateTime: Long = System.currentTimeMillis(), // 更新时间
    
    // 用户修改标记字段
    // 标记用户是否修改了评分
    @ColumnInfo(name = "userModifiedRating", defaultValue = "false")
    var userModifiedRating: Boolean = false,
    // 标记用户是否修改了简介
    @ColumnInfo(name = "userModifiedIntro", defaultValue = "false")
    var userModifiedIntro: Boolean = false,
    // 标记用户是否修改了封面
    @ColumnInfo(name = "userModifiedCover", defaultValue = "false")
    var userModifiedCover: Boolean = false,
    // 标记用户是否修改了字数
    @ColumnInfo(name = "userModifiedWordCount", defaultValue = "false")
    var userModifiedWordCount: Boolean = false,
    // 标记用户是否修改了分类
    @ColumnInfo(name = "userModifiedKind", defaultValue = "false")
    var userModifiedKind: Boolean = false,
    // 书籍读完时间
    @ColumnInfo(name = "finishReadTime", defaultValue = "0")
    var finishReadTime: Long = 0L,
    
    // 首次阅读时间
    @ColumnInfo(name = "firstReadTime", defaultValue = "0")
    var firstReadTime: Long = 0L,
    
    // 书籍类型，与Book.type保持一致
    @ColumnInfo(name = "type", defaultValue = "0")
    var type: Int = 0
) : Parcelable {
    /**
     * 获取阅读状态标签，作为书籍的第一个标签显示
     */
    fun getReadingStatusTag(): String {
        return when (readingStatus) {
            ReadingStatus.PENDING -> "待读"
            ReadingStatus.READING -> "在读"
            ReadingStatus.FINISHED -> "已读完"
            ReadingStatus.ABANDONED -> "弃文"
        }
    }
    
    /**
     * 根据阅读进度更新阅读状态（已废弃，使用ReadingProgressHelper.updateMemoryReadingStatusByProgress替代）
     * @param progress 阅读进度
     * @param readTime 阅读时间
     * @param autoUpdate 是否自动更新（用户未手动修改过状态时）
     */
    @Deprecated("Use ReadingProgressHelper.updateMemoryReadingStatusByProgress instead")
    fun updateReadingStatusByProgress(progress: Float, readTime: Long, autoUpdate: Boolean = true) {
        // 内部调用统一的更新方法
        ReadingProgressHelper.updateMemoryReadingStatusByProgress(this, autoUpdate)
    }
    
    /**
     * 获取阅读状态的显示文本
     * @return 阅读状态的显示文本
     */
    fun getReadingStatusDisplayText(): String {
        return readingStatus.displayName
    }
    
    /**
     * 获取阅读状态的数值
     * @return 阅读状态的数值
     */
    fun getReadingStatusValue(): Int {
        return readingStatus.value
    }
}