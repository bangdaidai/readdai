package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书籍标签关联实体类
 * 用于存储书籍与标签的多对多关系
 */
@Entity(
    tableName = "bookTagRelations",
    indices = [
        Index(value = ["bookUrl"]),
        Index(value = ["tagId"]),
        Index(value = ["bookUrl", "tagId"], unique = true)
    ]
)
data class BookTagRelation(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "", // 关联关系唯一ID，格式如"relation_时间戳_随机数"
    
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String = "", // 书籍URL，与Book实体关联
    
    @ColumnInfo(name = "tagId")
    val tagId: Long = 0, // 标签ID，与BookTag实体关联
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis() // 关联关系创建时间
)