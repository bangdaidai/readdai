package io.legado.app.help.ai.repository

import io.legado.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍数据记录
 */
data class BookRecord(
    val bookUrl: String,
    val title: String,
    val author: String,
    val kind: String?,
    val wordCount: String?,
    val totalChapters: Int,
    val currentChapter: Int,
    val progress: Int,
    val lastReadTime: Long,
    val firstReadTime: Long,
    val tags: List<TagRecord> = emptyList()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "bookUrl" to bookUrl,
            "title" to title,
            "author" to author,
            "kind" to kind,
            "wordCount" to wordCount,
            "totalChapters" to totalChapters,
            "currentChapter" to currentChapter,
            "progress" to progress,
            "progressPercentage" to "$progress%",
            "lastReadTime" to lastReadTime,
            "firstReadTime" to firstReadTime,
            "tags" to tags.map { it.toMap() }
        )
    }
}

/**
 * 标签数据记录
 */
data class TagRecord(
    val id: Long,
    val name: String
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name
        )
    }
}

/**
 * 分组数据记录
 */
data class GroupRecord(
    val id: Long,
    val name: String
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name
        )
    }
}

/**
 * 书籍查询 Repository
 * 负责从数据库获取和查询书籍信息
 * 
 * 合并了 list_books 和 bookshelf_lookup 的功能
 * 参考 anx53 的 BooksRepository 设计
 */
class BooksRepository(
    private val appDatabase: AppDatabase
) {
    private val bookDao = appDatabase.bookDao
    private val tagDao = appDatabase.bookTagDao
    private val tagRelationDao = appDatabase.bookTagRelationDao
    private val groupDao = appDatabase.bookTagGroupDao
    
    /**
     * 查询书架上的书籍
     * 
     * @param keyword 关键词搜索（标题或作者）
     * @param category 分类过滤
     * @param groupId 分组ID过滤
     * @param status 阅读状态过滤 (unread/reading/completed)
     * @param sortBy 排序方式 (lastRead/title/author/addTime)
     * @param limit 最大返回数量
     * @param includeTags 是否包含标签信息
     * @return 书籍列表
     */
    suspend fun searchBooks(
        keyword: String? = null,
        category: String? = null,
        groupId: Long? = null,
        status: String? = null,
        sortBy: String = "lastRead",
        limit: Int = 50,
        includeTags: Boolean = true
    ): List<BookRecord> = withContext(Dispatchers.IO) {
        var books = bookDao.all.toList()
        
        // 关键词搜索
        if (!keyword.isNullOrBlank()) {
            books = books.filter { book ->
                book.name.contains(keyword, ignoreCase = true) ||
                (book.author?.contains(keyword, ignoreCase = true) == true)
            }
        }
        
        // 分类过滤
        if (!category.isNullOrBlank()) {
            books = books.filter { book ->
                book.kind?.contains(category, ignoreCase = true) == true
            }
        }
        
        // 阅读状态过滤
        if (!status.isNullOrBlank()) {
            books = when (status) {
                "unread" -> books.filter { it.durChapterIndex == 0 }
                "reading" -> books.filter { 
                    it.durChapterIndex > 0 && 
                    it.durChapterIndex < it.totalChapterNum 
                }
                "completed" -> books.filter { 
                    it.totalChapterNum > 0 && 
                    it.durChapterIndex >= it.totalChapterNum 
                }
                else -> books
            }
        }
        
        // 排序
        books = when (sortBy) {
            "title" -> books.sortedBy { it.name }
            "author" -> books.sortedBy { it.author }
            "addTime" -> books.sortedByDescending { it.firstReadTime }
            else -> books.sortedByDescending { it.lastCheckTime }  // lastRead
        }
        
        // 限制数量
        books = books.take(limit)
        
        // 转换为 BookRecord
        books.map { book ->
            val progress = if (book.totalChapterNum > 0) {
                (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
            } else 0
            
            // 获取标签（如果需要）
            val tags = if (includeTags) {
                val relations = tagRelationDao.getRelationsByBook(book.bookUrl)
                val tagIds = relations.map { it.tagId }
                if (tagIds.isNotEmpty()) {
                    tagDao.getTagsByIds(tagIds).map { TagRecord(it.id, it.name) }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            BookRecord(
                bookUrl = book.bookUrl,
                title = book.name,
                author = book.author ?: "未知",
                kind = book.kind,
                wordCount = book.wordCount,
                totalChapters = book.totalChapterNum,
                currentChapter = book.durChapterIndex,
                progress = progress,
                lastReadTime = book.lastCheckTime,
                firstReadTime = book.firstReadTime,
                tags = tags
            )
        }
    }
    
    /**
     * 获取所有分组
     */
    suspend fun getGroups(): List<GroupRecord> = withContext(Dispatchers.IO) {
        if (groupDao == null) {
            return@withContext emptyList()
        }
        
        try {
            val method = groupDao.javaClass.getMethod("getAll")
            @Suppress("UNCHECKED_CAST")
            (method.invoke(groupDao) as? List<*>)?.mapNotNull { item ->
                try {
                    item?.javaClass?.let { clazz ->
                        val idField = clazz.getField("id")
                        val nameField = clazz.getField("name")
                        GroupRecord(
                            id = idField.get(item) as Long,
                            name = nameField.get(item) as String
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }?.take(100) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 根据书名获取书籍
     */
    suspend fun getBookByName(name: String): BookRecord? = withContext(Dispatchers.IO) {
        val book = bookDao.all.firstOrNull { it.name == name } ?: return@withContext null
        
        val progress = if (book.totalChapterNum > 0) {
            (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
        } else 0
        
        BookRecord(
            bookUrl = book.bookUrl,
            title = book.name,
            author = book.author ?: "未知",
            kind = book.kind,
            wordCount = book.wordCount,
            totalChapters = book.totalChapterNum,
            currentChapter = book.durChapterIndex,
            progress = progress,
            lastReadTime = book.lastCheckTime,
            firstReadTime = book.firstReadTime
        )
    }
}
