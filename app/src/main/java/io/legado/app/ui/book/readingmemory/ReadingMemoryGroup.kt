package io.legado.app.ui.book.readingmemory

import io.legado.app.data.entities.ReadingMemory

/**
 * 阅读记忆分组数据类
 * @param year 年份
 * @param books 该年份下的书籍列表
 */
data class ReadingMemoryGroup(
    val year: Int,
    val books: List<ReadingMemory>
)
