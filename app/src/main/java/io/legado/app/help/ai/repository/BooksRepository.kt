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
    val tags: List<TagRecord> = emptyList(),
    val isVectorized: Boolean = false  // ✅ 添加向量化状态
) {
    /**
     * ✅ 精简版 toMap：只返回 AI 需要的核心信息，减少 token 消耗
     * 参考 ReadAny 的设计：title, author, progress, isVectorized, lastReadTime
     */
    fun toMap(): Map<String, Any?> {
        // ✅ 只返回标签名称列表，而不是完整对象
        val tagNames = tags.map { it.name }
        
        return mapOf(
            "title" to title,                    // 书名（必需）
            "author" to author,                  // 作者（必需）
            "progress" to "$progress%",          // ✅ 阅读进度百分比（字符串格式，易读）
            "lastReadTime" to lastReadTime,      // 最后阅读时间（用于排序）
            "isVectorized" to isVectorized,      // 是否已向量化（决定能否使用 RAG）
            "tags" to tagNames                   // ✅ 标签名称列表（用于分类搜索）
            // ❌ 移除以下字段以节省 token：
            // - bookUrl: AI 不需要直接访问 URL
            // - kind: 分类信息不常用
            // - wordCount: 字数不重要
            // - totalChapters / currentChapter: 可以用 progress 代替
            // - firstReadTime: 不常用
            // - progressPercentage: 与 progress 重复，AI 可以自己格式化
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
    private val vectorizedBookDao = appDatabase.vectorizedBookDao  // ✅ 添加向量化 DAO（属性，不是函数）
    
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
            else -> books.sortedByDescending { it.durChapterTime }  // ✅ 使用阅读时间，而不是更新时间
        }
        
        // 限制数量
        books = books.take(limit)
        
        // 转换为 BookRecord
        books.map { book ->
            val progress = if (book.totalChapterNum > 0) {
                (book.durChapterIndex.toFloat() / book.totalChapterNum * 100).toInt()
            } else 0
            
            // ✅ 检查是否已向量化
            val isVectorized = try {
                vectorizedBookDao.getByBookUrl(book.bookUrl) != null
            } catch (e: Exception) {
                false
            }
            
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
                lastReadTime = book.durChapterTime,  // ✅ 使用阅读时间
                firstReadTime = book.firstReadTime,
                tags = tags,
                isVectorized = isVectorized  // ✅ 设置向量化状态
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
