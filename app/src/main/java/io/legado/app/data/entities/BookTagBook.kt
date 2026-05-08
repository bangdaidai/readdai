package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书籍标签关联实体类
 * 用于存储书籍与标签的多对多关联关系
 */
@Entity(tableName = "bookTagBooks")
data class BookTagBook(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0, // 关联ID，自动生成
    
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String = "", // 书籍URL
    
    @ColumnInfo(name = "tagName")
    val tagName: String = "", // 标签名称
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis() // 创建时间
) {
    // 用于创建新关联的辅助构造函数
    constructor(
        bookUrl: String,
        tagName: String,
        createTime: Long = System.currentTimeMillis()
    ) : this(
        id = 0, // 自动生成
        bookUrl = bookUrl,
        tagName = tagName,
        createTime = createTime
    )
}