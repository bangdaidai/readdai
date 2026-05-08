package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 统一阅读会话记录表
 * 融合了ReadSession和ReadRecordSession的功能，统一数据源
 */
@Entity(
     tableName = "readSession",
     indices = [
         Index(name = "index_readSession_bookName_author", value = ["bookName", "author"]),
         Index(name = "index_readSession_startTime", value = ["startTime"]),
         Index(name = "index_readSession_endTime", value = ["endTime"]),
         Index(name = "index_readSession_bookName", value = ["bookName"]),
         Index(name = "index_readSession_bookUrl", value = ["bookUrl"])
     ]
 )
 data class ReadSession(
     @PrimaryKey(autoGenerate = true)
     val id: Long = 0,
     @ColumnInfo(defaultValue = "")
     val bookName: String = "",
     @ColumnInfo(defaultValue = "")
     val author: String = "",
     @ColumnInfo(defaultValue = "")
     val bookUrl: String = "",
     @ColumnInfo(defaultValue = "")
     val deviceId: String = "",
     @ColumnInfo(defaultValue = "0")
     val startTime: Long = 0L,
     @ColumnInfo(defaultValue = "0")
     val endTime: Long = 0L,
     @ColumnInfo(defaultValue = "0")
     val duration: Long = 0L,
     @ColumnInfo(defaultValue = "0")
     val words: Long = 0L,
     @ColumnInfo(defaultValue = "8")
     val type: Int = io.legado.app.constant.BookType.text,
     @ColumnInfo(defaultValue = "")
     val durChapterTitle: String = "",
     @ColumnInfo(defaultValue = "")
     val coverUrl: String = "",
     // 显示名称，用户可修改，不影响同步
     @ColumnInfo(defaultValue = "")
     val displayName: String = ""
 )
