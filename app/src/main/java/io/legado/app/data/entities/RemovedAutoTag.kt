package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已移除的自动标签实体类
 * 用于记录用户手动移除的自动添加标签，防止这些标签再次被自动添加
 */
@Entity(
    tableName = "removedAutoTags",
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["tagName"])
    ]
)
data class RemovedAutoTag(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "", // 记录唯一ID，格式如"removed_时间戳_随机数"
    
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String = "", // 书籍URL，与Book实体关联
    
    @ColumnInfo(name = "tagName")
    val tagName: String = "", // 被移除的标签名称
    
    @ColumnInfo(name = "removeTime")
    val removeTime: Long = System.currentTimeMillis() // 标签移除时间
)