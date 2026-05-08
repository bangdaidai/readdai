package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
     tableName = "readRecordDetail",
     primaryKeys = ["deviceId", "bookName", "date"]
 )
 data class ReadRecordDetail(
     @ColumnInfo(defaultValue = "")
     val deviceId: String = "",
     @ColumnInfo(defaultValue = "")
     val bookName: String = "",
     @ColumnInfo(defaultValue = "")
     val date: String = "",
 
     // 当天阅读总时长
     @ColumnInfo(defaultValue = "0")
     var readTime: Long = 0L,
 
     // 当天阅读总字数
     @ColumnInfo(defaultValue = "0")
     var readWords: Long = 0L,
     // 当天第一次阅读时间
     @ColumnInfo(defaultValue = "0")
     var firstReadTime: Long = 0L,
     // 当天最后一次阅读时间
     @ColumnInfo(defaultValue = "0")
     var lastReadTime: Long = 0L,
     // 封面URL
     @ColumnInfo(defaultValue = "")
     var coverUrl: String = "",
     // 显示名称，用户可修改，不影响同步
     @ColumnInfo(defaultValue = "")
     var displayName: String = ""
 )