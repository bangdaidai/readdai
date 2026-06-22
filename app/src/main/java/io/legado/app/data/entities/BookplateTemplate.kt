package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookplateTemplates")
data class BookplateTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val htmlContent: String = "",
    @ColumnInfo(defaultValue = "0") val isBuiltin: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createTime: Long = 0L,
    @ColumnInfo(defaultValue = "0") val updateTime: Long = 0L
)
