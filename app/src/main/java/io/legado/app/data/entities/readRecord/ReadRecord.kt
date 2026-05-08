package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "readRecord", primaryKeys = ["deviceId", "bookName"])
 data class ReadRecord(
     @ColumnInfo(defaultValue = "")
     var deviceId: String = "",
     @ColumnInfo(defaultValue = "")
     var bookName: String = "",
     @ColumnInfo(defaultValue = "0")
     var readTime: Long = 0L,
     @ColumnInfo(defaultValue = "0")
     var lastRead: Long = System.currentTimeMillis(),
     @ColumnInfo(defaultValue = "")
     var coverUrl: String = "",
     // 显示名称，用户可修改，不影响同步
     @ColumnInfo(defaultValue = "")
     var displayName: String = ""
 )