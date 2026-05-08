package io.legado.app.data.entities

data class CoverCalendarDayData(
    val date: String,
    val dayOfMonth: Int,
    val coverUrl: String,
    val bookName: String,
    val isCurrentMonth: Boolean = true
)
