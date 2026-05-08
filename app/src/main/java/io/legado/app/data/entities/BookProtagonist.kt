package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书籍主角名实体类
 * 用于存储书籍的主角名信息
 */
@Entity(
    tableName = "bookProtagonists",
    indices = [
        Index(value = ["bookUrl", "name"], unique = true) // 确保每本书的主角名唯一
    ]
)
data class BookProtagonist(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0, // 主角名唯一ID，自动生成
    
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String = "", // 书籍URL
    
    @ColumnInfo(name = "name")
    val name: String = "", // 主角名
    
    @ColumnInfo(name = "isCustom")
    val isCustom: Boolean = false, // 是否是用户自定义的主角名
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis(), // 创建时间
    
    @ColumnInfo(name = "updateTime")
    val updateTime: Long = System.currentTimeMillis() // 更新时间
)
