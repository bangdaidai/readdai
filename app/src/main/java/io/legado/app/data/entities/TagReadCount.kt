package io.legado.app.data.entities

data class TagReadCount(
    val tag: String,
    val bookCount: Int,
    val totalTime: Long
)
