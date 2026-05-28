package io.legado.app.help.book

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * 阅读轮次（读完/刷书）相关工具方法
 */
object ReadIterationHelper {

    /**
     * 默认标签背景色（带80%不透明度的橙红色）
     */
    private const val DEFAULT_TAG_COLOR = 0xCCB5451B.toInt() // ARGB: 80% opacity warm red

    /**
     * 根据 readIteration 值获取标签文本
     * 0 -> 不显示
     * 1 -> 读完
     * 2 -> 二刷
     * 3 -> 二刷完
     * 4 -> 三刷
     * 5 -> 三刷完
     * ...
     */
    fun getTagText(readIteration: Int): String? {
        if (readIteration <= 0) return null
        return when (readIteration) {
            1 -> "读完"
            else -> {
                val nth = (readIteration + 2) / 2 // 2->2, 3->2, 4->3, 5->3...
                val nthStr = when (nth) {
                    2 -> "二"
                    3 -> "三"
                    4 -> "四"
                    5 -> "五"
                    6 -> "六"
                    7 -> "七"
                    8 -> "八"
                    9 -> "九"
                    else -> "${nth}"
                }
                if (readIteration % 2 == 0) {
                    // 偶数：刷中 (二刷, 三刷...)
                    "${nthStr}刷"
                } else {
                    // 奇数且>1：刷完 (二刷完, 三刷完...)
                    "${nthStr}刷完"
                }
            }
        }
    }

    /**
     * 获取标签颜色（从偏好设置读取，未设置则使用默认）
     */
    fun getTagColor(): Int {
        return appCtx.getPrefInt(PreferKey.readIterationTagColor, DEFAULT_TAG_COLOR)
    }

    /**
     * 是否处于"已读完"状态（奇数）
     * 即 1, 3, 5, 7...
     */
    fun isFinished(book: Book): Boolean {
        return book.readIteration > 0 && book.readIteration % 2 == 1
    }

    /**
     * 让书进入下一轮次（读完->二刷, 二刷完->三刷, ...）
     * 即 readIteration + 1
     */
    fun moveToNextIteration(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration++
        book.save()
        
        // 同步更新标签系统
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
            // 通知书架刷新，确保分组更新
            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.BOOKSHELF_REFRESH, "")
        }
    }

    /**
     * 将书标记为已读完（readIteration变为下一个奇数）
     * 若 readIteration 为 0 -> 1（读完）
     * 若 readIteration 为 2 -> 3（二刷完）
     * 若 readIteration 为 4 -> 5（三刷完）
     */
    fun markAsFinished(book: Book) {
        val oldIteration = book.readIteration
        if (book.readIteration == 0) {
            book.readIteration = 1
        } else if (book.readIteration % 2 == 0) {
            book.readIteration++
        }
        book.save()
        
        // 同步更新标签系统
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
            // 通知书架刷新，确保分组更新
            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.BOOKSHELF_REFRESH, "")
        }
    }

    /**
     * 标记书籍为弃文
     * 重置 readIteration 为 0
     * 阅读状态设为 ABANDONED
     */
    fun markAsAbandoned(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration = 0
        book.setReadingStatus(ReadingStatus.ABANDONED)
        book.save()
        
        // 同步更新标签系统
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
        }
    }

    /**
     * 重新开始阅读（弃文后重新开始）
     * 设置 readIteration 为 0
     * 阅读状态设为 PENDING
     */
    fun restartReading(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration = 0
        book.setReadingStatus(ReadingStatus.PENDING)
        book.save()
        
        // 同步更新标签系统
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
        }
    }

    /**
     * 同步更新书籍标签系统
     */
    private suspend fun updateBookTag(book: Book, oldIteration: Int) {
        withContext(Dispatchers.IO) {
            try {
                val oldTagText = getTagText(oldIteration)
                val newTagText = getTagText(book.readIteration)
                
                // 如果旧标签存在，先移除
                if (oldTagText != null) {
                    removeOldTag(book.bookUrl, oldTagText)
                }
                
                // 如果新标签存在，添加新标签
                if (newTagText != null) {
                    addNewTag(book.bookUrl, newTagText)
                }
                
                // 通知标签更新
                io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.TAGS_UPDATED, book.bookUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 从书籍移除旧的N刷标签
     */
    private suspend fun removeOldTag(bookUrl: String, tagText: String) {
        val existingTag = appDb.bookTagDao.getTagByName(tagText) ?: return
        val existingRelation = appDb.bookTagRelationDao.getRelation(bookUrl, existingTag.id) ?: return
        appDb.bookTagRelationDao.delete(existingRelation)
        TagManager.clearCache(bookUrl)
    }

    /**
     * 为书籍添加新的N刷标签
     */
    private suspend fun addNewTag(bookUrl: String, tagText: String) {
        val tagColor = getTagColor()
        
        // 查找或创建标签
        var tag = appDb.bookTagDao.getTagByName(tagText)
        if (tag == null) {
            tag = BookTag(
                name = tagText,
                color = tagColor,
                createTime = System.currentTimeMillis()
            )
            val tagId = appDb.bookTagDao.insert(tag)
            tag = tag.copy(id = tagId)
        }
        
        // 检查是否已有关联关系
        val existingRelation = appDb.bookTagRelationDao.getRelation(bookUrl, tag.id)
        if (existingRelation == null) {
            // 创建关联关系
            val relation = BookTagRelation(
                id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                bookUrl = bookUrl,
                tagId = tag.id,
                createTime = System.currentTimeMillis()
            )
            appDb.bookTagRelationDao.insert(relation)
        }
        
        TagManager.clearCache(bookUrl)
    }

    /**
     * 统一设置 TextView 的阅读轮次标签样式。
     * 有标签时显示圆角背景 + 文本，无标签时隐藏。
     */
    fun applyTagStyle(tv: TextView, readIteration: Int) {
        if (!appCtx.getPrefBoolean(PreferKey.readIterationShowTag, true)) {
            tv.gone()
            return
        }
        val tagText = getTagText(readIteration)
        if (tagText != null) {
            tv.text = tagText
            val color = getTagColor()
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3f.dpToPx()
                setColor(color)
            }
            tv.background = drawable
            tv.visible()
        } else {
            tv.gone()
        }
    }
}
