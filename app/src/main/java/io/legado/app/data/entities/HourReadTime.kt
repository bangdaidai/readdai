package io.legado.app.data.entities

data class HourReadTime(
    val hour: Int,
    val totalTime: Long
) {
    val period: String
        get() = when (hour) {
            in 0..5 -> "深夜"
            in 6..11 -> "上午"
            in 12..17 -> "下午"
            else -> "晚上"
        }
}
