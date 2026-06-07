package io.legado.app.data.entities

import androidx.room.Entity

@Entity
data class BookReadTimeRank(
    val bookName: String,
    val readTime: Long,
    val coverUrl: String = ""
)