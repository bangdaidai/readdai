package io.legado.app.help.book

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.utils.ConvertUtils
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.*

/**
 * 书籍显示扩展
 */
fun Book.getDisplaySize(): String {
    val wordCountValue = this.wordCount
    return if (!wordCountValue.isNullOrEmpty()) {
        try {
            val wordCountLong = wordCountValue.toLongOrNull() ?: 0L
            if (wordCountLong > 0) {
                ConvertUtils.formatFileSize(wordCountLong)
            } else {
                appCtx.getString(R.string.unknown_state)
            }
        } catch (e: NumberFormatException) {
            appCtx.getString(R.string.unknown_state)
        }
    } else {
        appCtx.getString(R.string.unknown_state)
    }
}

/**
 * 获取显示的最后阅读时间
 */
fun Book.getDisplayLastReadTime(): String? {
    return if (durChapterTime > 0) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        dateFormat.format(Date(durChapterTime))
    } else {
        null
    }
}

/**
 * 获取显示的剩余时间
 */
fun Book.getDisplayRestTime(): String {
    return if (totalChapterNum > 0 && durChapterIndex > 0) {
        val restCount = totalChapterNum - durChapterIndex
        appCtx.resources?.getQuantityString(R.plurals.chapter_count, restCount, restCount) 
            ?: "$restCount 章"
    } else {
        appCtx.getString(R.string.unknown_state)
    }
}