package io.legado.app.data.entities.readRecord

data class ReadRecordSummary(
    var deviceId: String = "",
    var bookName: String = "",
    var readTime: Long = 0L,
    var lastRead: Long = 0L
)
