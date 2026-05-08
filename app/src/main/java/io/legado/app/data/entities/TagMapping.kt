package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签映射实体类
 * 用于存储旧标签名称到新标签的映射关系
 */
@Entity(
    tableName = "tagMappings",
    indices = [
        Index(value = ["oldTagName"], unique = true), // 确保旧标签名称唯一
        Index(value = ["newTagId"]) // 提高按新标签ID查询的性能
    ]
)
data class TagMapping(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0, // 映射唯一ID，自动生成
    
    @ColumnInfo(name = "oldTagName")
    val oldTagName: String = "", // 旧标签名称
    
    @ColumnInfo(name = "newTagId")
    val newTagId: Long = 0, // 新标签ID
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis() // 映射创建时间
)
