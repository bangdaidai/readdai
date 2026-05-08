package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 书评实体类
 */
@Parcelize
@Entity(tableName = "bookReviews")
data class BookReview(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = System.currentTimeMillis(), // 使用时间戳作为ID
    
    @ColumnInfo(name = "bookUrl")
    val bookUrl: String, // 书籍URL
    
    @ColumnInfo(name = "bookName")
    val bookName: String, // 书名
    
    @ColumnInfo(name = "bookAuthor")
    val bookAuthor: String, // 作者
    
    @ColumnInfo(name = "reviewContent")
    val reviewContent: String, // 书评内容
    
    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis(), // 创建时间
    
    @ColumnInfo(name = "updateTime")
    val updateTime: Long = System.currentTimeMillis() // 更新时间
) : Parcelable