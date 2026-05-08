package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 排除标签实体类
 * 用于存储用户自定义的排除标签信息
 */
@Entity(tableName = "excludedTags")
data class ExcludedTag(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0, // 排除标签唯一ID，自动生成
    
    @ColumnInfo(name = "name")
    val name: String = "", // 排除标签名称
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis(), // 排除标签创建时间
    
    @ColumnInfo(name = "updateTime")
    val updateTime: Long = System.currentTimeMillis() // 排除标签更新时间
) {
    // 用于创建新排除标签的辅助构造函数
    constructor(
        name: String,
        createTime: Long = System.currentTimeMillis()
    ) : this(
        id = 0, // 自动生成
        name = name,
        createTime = createTime,
        updateTime = createTime
    )
}