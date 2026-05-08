package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 书摘实体类
 */
@Parcelize
@Entity(tableName = "bookAnnotations", indices = [(Index(value = ["bookName", "bookAuthor"], unique = false))])
data class BookAnnotation(
    @PrimaryKey
    val time: Long = System.currentTimeMillis(),
    val bookName: String = "",
    val bookAuthor: String = "",
    var chapterIndex: Int = 0,
    var chapterPos: Int = 0,
    var chapterName: String = "",
    var bookText: String = "",
    var content: String = "",
    var note: String = ""
) : Parcelable