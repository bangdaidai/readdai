package io.legado.app.data.entities

data class ReadStatistics(
    val bookCount: Int,
    val finishedBookCount: Int,
    val abandonedBookCount: Int = 0,
    val totalWords: Long = 0,
    val reviewCount: Int,
    val totalTime: Long,
    val date: String,
    val readDays: Int = 0
)