package io.legado.app.data.entities

import androidx.room.Entity

@Entity
 data class DailyLongestReadCover(
     val date: String,
     val bookName: String,
     val coverUrl: String?,
     val totalDuration: Long
 )
