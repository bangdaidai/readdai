package io.legado.app.help.book

import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读轮次（N刷）相关工具方法
 * 
 * 设计说明：
 * - N刷标签集成到标签管理系统中，不显示在书籍封面上
 * - 不需要"初读"标签，只有二刷及以上才创建标签
 * - 标签颜色等由标签管理系统统一管理
 * 
 * readIteration 语义：
 * 0 -> 未读/在读中
 * 1 -> 首次读完（不创建标签）
 * 2 -> 正在二刷（创建"二刷"标签）
 * 3 -> 二刷完成（更新为"二刷完"标签）
 * 4 -> 正在三刷（更新为"三刷"标签）
 * 5 -> 三刷完成（更新为"三刷完"标签）
 * ...
 * 奇数 = 已读完当前轮次，偶数 = 正在N刷中
 */
object ReadIterationHelper {

    /**
     * 默认标签背景色（带80%不透明度的橙红色）
     * 仅在创建新标签时使用，已存在的标签保留原色
     */
    private const val DEFAULT_TAG_COLOR = 0xCCB5451B.toInt()

    /**
     * 根据 readIteration 值获取标签文本
     * 只有二刷及以上才返回标签文本
     */
    fun getTagText(readIteration: Int): String? {
        if (readIteration < 2) return null
        
        val brushNum = (readIteration + 1) / 2
        val isFinished = readIteration % 2 == 1  // 奇数表示完成
        
        val nthStr = when (brushNum) {
            2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
            6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"
            else -> "$brushNum"
        }
        
        return if (isFinished) "${nthStr}刷完" else "${nthStr}刷"
    }

    /**
     * 是否处于"已读完"状态
     * readIteration 为奇数且 > 0 表示已读完当前轮次
     */
    fun isFinished(book: Book): Boolean {
        return book.readIteration > 0 && book.readIteration % 2 == 1
    }

    /**
     * 标记书籍为已完成当前轮次
     * readIteration++，并同步更新标签系统
     */
    fun markAsFinished(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration++
        book.save()
        
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
        }
    }

    /**
     * 让书进入下一轮次（读完->二刷, 二刷完->三刷, ...）
     * readIteration++，并同步更新标签系统
     */
    fun moveToNextIteration(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration++
        book.save()

        GlobalScope.launch {
            updateBookTag(book, oldIteration)
        }
    }

    /**
     * 标记书籍为弃文
     * 重置 readIteration 为 0，阅读状态设为 ABANDONED
     */
    fun markAsAbandoned(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration = 0
        book.setReadingStatus(ReadingStatus.ABANDONED)
        book.save()
        
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.BOOKSHELF_REFRESH, "")
        }
    }

    /**
     * 重新开始阅读（弃文后重新开始）
     * 设置 readIteration 为 0，阅读状态设为 PENDING
     */
    fun restartReading(book: Book) {
        val oldIteration = book.readIteration
        book.readIteration = 0
        book.setReadingStatus(ReadingStatus.PENDING)
        book.save()
        
        GlobalScope.launch {
            updateBookTag(book, oldIteration)
            io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.BOOKSHELF_REFRESH, "")
        }
    }

    /**
     * 同步更新书籍标签系统
     * 移除旧标签，添加新标签
     */
    private suspend fun updateBookTag(book: Book, oldIteration: Int) {
        withContext(Dispatchers.IO) {
            try {
                val oldTagText = getTagText(oldIteration)
                val newTagText = getTagText(book.readIteration)
                
                if (oldTagText != null) {
                    removeOldTag(book.bookUrl, oldTagText)
                }
                
                if (newTagText != null) {
                    addNewTag(book.bookUrl, newTagText)
                }
                
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
     * 已存在的标签保留原色，新标签使用默认颜色
     */
    private suspend fun addNewTag(bookUrl: String, tagText: String) {
        // 查找或创建标签
        var tag = appDb.bookTagDao.getTagByName(tagText)
        if (tag == null) {
            tag = BookTag(
                name = tagText,
                color = DEFAULT_TAG_COLOR,
                createTime = System.currentTimeMillis()
            )
            val tagId = appDb.bookTagDao.insert(tag)
            tag = tag.copy(id = tagId)
        }
        
        // 检查是否已有关联关系
        val existingRelation = appDb.bookTagRelationDao.getRelation(bookUrl, tag.id)
        if (existingRelation == null) {
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
}
