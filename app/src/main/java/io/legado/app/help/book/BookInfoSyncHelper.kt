package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.constant.EventBus
import io.legado.app.utils.TagColorUtils
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍信息同步工具类
 * 用于统一处理Book实体和我的阅读记录之间的数据同步
 */
object BookInfoSyncHelper {

    // 添加锁保护，防止并发调用导致的重复更新
    private val syncLock = Any()

    /**
     * 同步书籍基本信息到Book实体
     * @param bookUrl 书籍URL
     * @param readingMemory 我的阅读记录数据
     */
    suspend fun syncToBookEntity(bookUrl: String, readingMemory: ReadingMemory) {
        withContext(Dispatchers.IO) {
            synchronized(syncLock) {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@synchronized
                
                // 检查书源是否属于正版分组
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                
                // 同步基本信息
                var needUpdate = false
                var updatedBook = book
                
                // 同步简介 - 只有当用户未修改过Book的简介且是正版书源时才同步
                if (!book.userModifiedIntro && readingMemory.intro != null && readingMemory.intro != book.customIntro && isOfficialSource) {
                    updatedBook = updatedBook.copy(customIntro = readingMemory.intro)
                    needUpdate = true
                }
                
                // 同步评分 - 只有当用户未修改过Book的评分时才同步
                if (!book.userModifiedRating && readingMemory.rating != book.rating) {
                    updatedBook = updatedBook.copy(rating = readingMemory.rating)
                    needUpdate = true
                }
                
                // 同步封面URL - 只有当用户未修改过Book的封面时才同步
                if (!book.userModifiedCover && readingMemory.coverUrl != null && readingMemory.coverUrl != book.customCoverUrl) {
                    updatedBook = updatedBook.copy(customCoverUrl = readingMemory.coverUrl)
                    needUpdate = true
                }
                
                // 同步字数 - 只有当用户未修改过Book的字数时才同步
                // 如果是正版书源，则同步；否则保留原有字数
                if (!book.userModifiedWordCount) {
                    if (isOfficialSource && readingMemory.wordCount != null && readingMemory.wordCount.toString() != book.wordCount) {
                        updatedBook = updatedBook.copy(wordCount = readingMemory.wordCount.toString())
                        needUpdate = true
                    }
                    // 非正版书源不更新字数，保留原有值
                }
                
                // 同步分类 - 只有当用户未修改过Book的分类时才同步
                // 如果是正版书源，则同步；否则保留原有分类
                if (!book.userModifiedKind) {
                    if (isOfficialSource && readingMemory.kind != null && readingMemory.kind != book.kind) {
                        updatedBook = updatedBook.copy(kind = readingMemory.kind)
                        needUpdate = true
                    }
                    // 非正版书源不更新分类，保留原有值
                }
                
                if (needUpdate) {
                    appDb.bookDao.update(updatedBook)
                }
            }
        }
    }
    
    /**
     * 同步书籍基本信息到我的阅读记录
     * @param bookUrl 书籍URL
     * @param book Book实体数据
     */
    suspend fun syncToReadingMemory(bookUrl: String, book: Book) {
        withContext(Dispatchers.IO) {
            synchronized(syncLock) {
                val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl) ?: return@synchronized
                
                // 检查书源是否属于正版分组
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                
                // 同步基本信息
                var needUpdate = false
                var updatedReadingMemory = readingMemory
                
                // 同步简介 - 只有当用户未修改过我的阅读记录的简介且是正版书源时才同步
                if (!readingMemory.userModifiedIntro && book.getDisplayIntro() != null && book.getDisplayIntro() != readingMemory.intro && isOfficialSource) {
                    updatedReadingMemory = updatedReadingMemory.copy(intro = book.getDisplayIntro())
                    needUpdate = true
                }
                
                // 同步评分 - 只有当用户未修改过我的阅读记录的评分时才同步
                if (!readingMemory.userModifiedRating && book.rating != readingMemory.rating) {
                    updatedReadingMemory = updatedReadingMemory.copy(rating = book.rating)
                    needUpdate = true
                }
                
                // 同步封面URL - 只有当用户未修改过我的阅读记录的封面时才同步
                if (!readingMemory.userModifiedCover && book.getDisplayCover() != null && book.getDisplayCover() != readingMemory.coverUrl) {
                    updatedReadingMemory = updatedReadingMemory.copy(coverUrl = book.getDisplayCover())
                    needUpdate = true
                }
                
                // 同步字数 - 只有当用户未修改过我的阅读记录的字数时才同步
                // 如果是正版书源，则同步；否则保留原有字数
                if (!readingMemory.userModifiedWordCount) {
                    if (isOfficialSource && book.wordCount != null && book.wordCount != readingMemory.wordCount) {
                        updatedReadingMemory = updatedReadingMemory.copy(wordCount = book.wordCount)
                        needUpdate = true
                    }
                    // 非正版书源不更新字数，保留原有值
                }
                
                // 同步分类 - 只有当用户未修改过我的阅读记录的分类时才同步
                // 如果是正版书源，则同步；否则保留原有分类
                if (!readingMemory.userModifiedKind) {
                    if (isOfficialSource && book.kind != null && book.kind != readingMemory.kind) {
                        updatedReadingMemory = updatedReadingMemory.copy(kind = book.kind)
                        needUpdate = true
                    }
                    // 非正版书源不更新分类，保留原有值
                }
                
                if (needUpdate) {
                    appDb.readingMemoryDao.update(updatedReadingMemory)
                }
            }
        }
    }
    
    /**
     * 同步标签信息到Book实体
     * @param bookUrl 书籍URL
     */
    suspend fun syncTagsToBookEntity(bookUrl: String) {
        withContext(Dispatchers.IO) {
            val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext
            
            // 检查书源是否属于正版分组
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
            
            // 只有正版书源才会在没有关联标签时清空kind字段
            // 非正版书源保留原有kind字段，防止标签丢失
            val relations = appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
            if (relations.isEmpty()) {
                // 只有正版书源才清空Book的kind字段
                if (isOfficialSource && !book.kind.isNullOrBlank()) {
                    val updatedBook = book.copy(kind = null)
                    appDb.bookDao.update(updatedBook)
                }
                return@withContext
            }
            
            // 只有正版书源才会更新Book的kind字段
            // 非正版书源保留原有kind字段，防止标签丢失
            if (isOfficialSource) {
                // 获取所有关联标签的名称
                val tagNames = mutableListOf<String>()
                for (relation in relations) {
                    val tag = appDb.bookTagDao.getTag(relation.tagId)
                    if (tag != null) {
                        tagNames.add(tag.name)
                    }
                }
                
                // 将标签名称合并为kind字段
                val newKind = tagNames.joinToString(",")
                
                // 只有当kind字段发生变化时才更新
                if (newKind != book.kind) {
                    val updatedBook = book.copy(kind = newKind)
                    appDb.bookDao.update(updatedBook)
                }
            }
        }
    }
    
    /**
     * 同步Book实体的kind字段到标签系统
     * @param bookUrl 书籍URL
     * @param preserveBookInfo 是否保留书籍信息
     * @param bookOrigin 书源
     */
    suspend fun syncBookKindToTags(bookUrl: String, preserveBookInfo: Boolean, bookOrigin: String) {
        withContext(Dispatchers.IO) {
            // 检查书源是否属于"正版"分组
            val bookSource = appDb.bookSourceDao.getBookSource(bookOrigin)
            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
            
            // 只有正版书源才同步标签，非正版书源不更新标签
            if (!isOfficialSource) {
                return@withContext
            }
            
            val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext
            
            if (book.kind.isNullOrBlank()) {
                return@withContext
            }
            
            // 解析kind字段为标签列表
            val kindTags = book.kind!!.split(Regex("[,\n]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                // 应用与processKindTags相同的过滤规则
                .filter { tag ->
                    // 排除包含标点的标签（只允许中文、英文、数字）
                    tag.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9]+\$")) &&
                    // 排除包含数字的标签
                    !tag.any { char -> char.isDigit() } &&
                    // 限制标签长度不超过5个字符
                    tag.length <= 5
                }
                .distinct()
            
            // 获取当前书籍关联的标签
            val currentRelations = appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
            val currentTagIds = currentRelations.map { it.tagId }.toSet()
            
            // 获取被移除的自动标签
            val removedAutoTags = appDb.removedAutoTagDao.getRemovedTagsByBook(bookUrl)
            val removedTagNames = removedAutoTags.map { it.tagName }.toSet()
            
            // 处理每个kind标签
            for (tagName in kindTags) {
                // 跳过被用户移除的自动标签
                if (removedTagNames.contains(tagName)) {
                    continue
                }
                
                // 查找或创建标签
                var tag = appDb.bookTagDao.getTagByName(tagName)
                if (tag == null) {
                    // 创建新标签
                    val newTag = BookTag(
                        name = tagName,
                        color = generateRandomDarkColor(),
                        createTime = System.currentTimeMillis()
                    )
                    val tagId = appDb.bookTagDao.insert(newTag)
                    tag = newTag.copy(id = tagId)
                }
                
                // 检查标签是否已关联到书籍
                val isAlreadyRelated = currentRelations.any { it.tagId == tag.id }
                if (!isAlreadyRelated) {
                    // 创建关联关系
                    val relation = BookTagRelation(
                        bookUrl = bookUrl,
                        tagId = tag.id
                    )
                    appDb.bookTagRelationDao.insert(relation)
                }
            }
            
            // 同时更新ReadingMemory的kind字段
            val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemory != null) {
                val updatedMemory = readingMemory.copy(
                    kind = book.kind,
                    updateTime = System.currentTimeMillis()
                )
                appDb.readingMemoryDao.update(updatedMemory)
            }
            
            // 清除标签缓存
            TagManager.clearCache(bookUrl)
            // 发送标签更新事件，通知相关页面刷新
            postEvent(EventBus.SOURCE_CHANGED, bookUrl)
            // 发送标签更新事件，通知相关页面刷新
            postEvent(EventBus.TAGS_UPDATED, bookUrl)
        }
    }
    
    /**
     * 生成随机亮色，提高可见度
     */
    private fun generateRandomDarkColor(): Int {
        return TagColorUtils.generateRandomColor()
    }
    
    /**
     * 统一更新书籍简介
     * 同时更新Book实体和我的阅读记录
     */
    suspend fun updateBookIntro(bookUrl: String, intro: String?) {
        withContext(Dispatchers.IO) {
            // 更新Book实体
            val book = appDb.bookDao.getBook(bookUrl)
            if (book != null) {
                val updatedBook = book.copy(
                    customIntro = intro,
                    userModifiedIntro = true
                )
                appDb.bookDao.update(updatedBook)
            }
            
            // 获取书籍名称和作者
            val bookForName = appDb.bookDao.getBook(bookUrl)
            if (bookForName != null) {
                // 更新所有与同一本书相关的阅读记录
                val memories = appDb.readingMemoryDao.getByBook(bookForName.name, bookForName.author)
                for (memory in memories) {
                    val updatedReadingMemory = memory.copy(
                        intro = intro,
                        userModifiedIntro = true
                    )
                    appDb.readingMemoryDao.update(updatedReadingMemory)
                }
            }
            
            // 同时更新通过bookUrl查询到的阅读记录
            val readingMemoryByUrl = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemoryByUrl != null) {
                val updatedReadingMemory = readingMemoryByUrl.copy(
                    intro = intro,
                    userModifiedIntro = true
                )
                appDb.readingMemoryDao.update(updatedReadingMemory)
            }

            // 发送简介更新事件，通知相关页面刷新
            postEvent(EventBus.BOOK_INTRO_UPDATED, bookUrl)
        }
    }
    
    /**
     * 统一更新书籍评分
     * 同时更新Book实体和我的阅读记录
     */
    suspend fun updateBookRating(bookUrl: String, rating: Float) {
        withContext(Dispatchers.IO) {
            // 更新Book实体
            val book = appDb.bookDao.getBook(bookUrl)
            if (book != null) {
                val updatedBook = book.copy(
                    rating = rating,
                    userModifiedRating = true
                )
                appDb.bookDao.update(updatedBook)
            }
            
            // 更新我的阅读记录
            val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemory != null) {
                val updatedReadingMemory = readingMemory.copy(
                    rating = rating,
                    userModifiedRating = true
                )
                appDb.readingMemoryDao.update(updatedReadingMemory)
            }
        }
    }
    
    /**
     * 统一更新书籍封面
     * 同时更新 Book 实体、我的阅读记录和阅读记录
     */
    suspend fun updateBookCover(bookUrl: String, coverUrl: String?) {
        withContext(Dispatchers.IO) {
            // 更新 Book 实体
            val book = appDb.bookDao.getBook(bookUrl)
            if (book != null) {
                val updatedBook = book.copy(
                    customCoverUrl = coverUrl,
                    userModifiedCover = true
                )
                appDb.bookDao.update(updatedBook)
            }
            
            // 更新我的阅读记录
            val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemory != null) {
                val updatedReadingMemory = readingMemory.copy(
                    coverUrl = coverUrl,
                    userModifiedCover = true
                )
                appDb.readingMemoryDao.update(updatedReadingMemory)
            }
            
            // 更新阅读记录（ReadSession）中的封面
            val sessions = appDb.readSessionDao.getByBook(book?.name ?: "", book?.author ?: "")
            sessions.forEach { session ->
                if (session.coverUrl != coverUrl) {
                    val updatedSession = session.copy(coverUrl = coverUrl ?: "")
                    appDb.readSessionDao.update(updatedSession)
                }
            }
            
            // 发送阅读记录更新事件，通知阅读记录页面刷新
            // 这样当封面更新时，阅读记录页面会自动刷新显示新封面
            postEvent(EventBus.READ_SESSION_UPDATED, book?.name ?: "")
        }
    }
    
    /**
     * 统一更新书籍字数
     * 同时更新Book实体和我的阅读记录
     */
    suspend fun updateBookWordCount(bookUrl: String, wordCount: String?) {
        withContext(Dispatchers.IO) {
            // 更新Book实体
            val book = appDb.bookDao.getBook(bookUrl)
            if (book != null) {
                val updatedBook = book.copy(
                    wordCount = wordCount,
                    userModifiedWordCount = true
                )
                appDb.bookDao.update(updatedBook)
            }
            
            // 更新我的阅读记录
            val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemory != null) {
                val updatedReadingMemory = readingMemory.copy(
                    wordCount = wordCount,
                    userModifiedWordCount = true
                )
                appDb.readingMemoryDao.update(updatedReadingMemory)
            }
        }
    }
    
    /**
     * 统一更新书籍分类
     * 同时更新Book实体和我的阅读记录
     */
    suspend fun updateBookKind(bookUrl: String, kind: String?) {
        withContext(Dispatchers.IO) {
            // 更新Book实体
            val book = appDb.bookDao.getBook(bookUrl)
            if (book != null) {
                val updatedBook = book.copy(
                    kind = kind,
                    userModifiedKind = true
                )
                appDb.bookDao.update(updatedBook)
            }
            
            // 更新我的阅读记录
            val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
            if (readingMemory != null) {
                val updatedReadingMemory = readingMemory.copy(
                    kind = kind,
                    userModifiedKind = true
                )
                appDb.readingMemoryDao.update(updatedReadingMemory)
            }
        }
    }
    
    /**
     * 同步单个标签到Book实体
     * @param bookUrl 书籍URL
     * @param tagName 标签名称
     * 
     * 注意：该方法已被废弃，不再修改Book的kind字段
     * 标签系统应该独立管理标签，不影响原始kind字段
     */
    @Suppress("unused")
    suspend fun syncTagToBookKind(bookUrl: String, tagName: String) {
        withContext(Dispatchers.IO) {
            // 应用与processKindTags相同的过滤规则
            val isValidTag = 
                    // 排除包含标点的标签（只允许中文、英文、数字）
                    tagName.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9]+\$")) &&
                    !tagName.any { char -> char.isDigit() } &&
                    tagName.length <= 5
            
            // 如果标签不符合过滤规则，则不添加
            if (!isValidTag) {
                return@withContext
            }
            
            // 不再修改Book的kind字段，标签系统独立管理标签
            // kind字段应该保持为正版书源的原始元数据
        }
    }
    
    /**
     * 从Book实体的kind字段中移除标签
     * @param bookUrl 书籍URL
     * @param tagName 要移除的标签名称
     * 
     * 注意：该方法已被废弃，不再修改Book的kind字段
     * 标签系统应该独立管理标签，不影响原始kind字段
     */
    @Suppress("unused")
    suspend fun removeTagFromBookKind(bookUrl: String, tagName: String) {
        withContext(Dispatchers.IO) {
            // 不再修改Book的kind字段，标签系统独立管理标签
            // kind字段应该保持为正版书源的原始元数据
        }
    }
    
    /**
     * 同步Book实体的字数和分类到我的阅读记录（带AppDatabase参数）
     * @param book Book实体
     * @param memory 我的阅读记录
     * @param appDb 数据库实例
     * @return 是否同步成功
     */
    suspend fun syncBookWordCountAndKindToMemory(
        book: Book, 
        memory: ReadingMemory, 
        appDb: io.legado.app.data.AppDatabase
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                synchronized(syncLock) {
                    // 检查书源是否属于正版分组
                    val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                    
                    var needUpdate = false
                    var updatedReadingMemory = memory
                    
                    // 同步字数 - 只有当用户未修改过我的阅读记录的字数时才同步
                    // 如果是正版书源，则同步；否则保留原有字数
                    if (!memory.userModifiedWordCount) {
                        if (isOfficialSource && book.wordCount != null && book.wordCount != memory.wordCount) {
                            updatedReadingMemory = updatedReadingMemory.copy(wordCount = book.wordCount)
                            needUpdate = true
                        }
                        // 非正版书源不更新字数，保留原有值
                    }
                    
                    // 同步分类 - 只有当用户未修改过我的阅读记录的分类时才同步
                    // 如果是正版书源，则同步；否则保留原有分类
                    if (!memory.userModifiedKind) {
                        if (isOfficialSource && book.kind != null && book.kind != memory.kind) {
                            updatedReadingMemory = updatedReadingMemory.copy(kind = book.kind)
                            needUpdate = true
                        }
                        // 非正版书源不更新分类，保留原有值
                    }
                    
                    // 注意：这里不同步简介，因为简介应该由用户完全控制
                    // 即使书源变更，也不应该覆盖用户已经设置或默认的简介
                    
                    if (needUpdate) {
                        appDb.readingMemoryDao.update(updatedReadingMemory)
                    }
                    
                    true // 同步成功
                }
            }
        } catch (e: Exception) {
            false // 同步失败
        }
    }
    
    /**
     * 同步我的阅读记录的字数和分类到Book实体（带AppDatabase参数）
     * @param memory 我的阅读记录
     * @param book Book实体
     * @param appDb 数据库实例
     * @return 是否同步成功
     */
    suspend fun syncMemoryWordCountAndKindToBook(
        memory: ReadingMemory, 
        book: Book, 
        appDb: io.legado.app.data.AppDatabase
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                synchronized(syncLock) {
                    // 检查书源是否属于正版分组
                    val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true
                    
                    var needUpdate = false
                    var updatedBook = book
                    
                    // 同步字数 - 只有当用户未修改过Book的字数时才同步
                    // 如果是正版书源，则同步；否则保留原有字数
                    if (!book.userModifiedWordCount) {
                        if (isOfficialSource && memory.wordCount != null && memory.wordCount.toString() != book.wordCount) {
                            updatedBook = updatedBook.copy(wordCount = memory.wordCount.toString())
                            needUpdate = true
                        }
                        // 非正版书源不更新字数，保留原有值
                    }
                    
                    // 同步分类 - 只有当用户未修改过Book的分类时才同步
                    // 如果是正版书源，则同步；否则保留原有分类
                    if (!book.userModifiedKind) {
                        if (isOfficialSource && memory.kind != null && memory.kind != book.kind) {
                            updatedBook = updatedBook.copy(kind = memory.kind)
                            needUpdate = true
                        }
                        // 非正版书源不更新分类，保留原有值
                    }
                    
                    if (needUpdate) {
                        appDb.bookDao.update(updatedBook)
                    }
                    
                    true // 同步成功
                }
            }
        } catch (e: Exception) {
            false // 同步失败
        }
    }
    
    /**
     * 同步Book实体的阅读状态到我的阅读记录
     * @param book Book实体
     * @param appDb 数据库实例
     * @return 是否同步成功
     */
    suspend fun syncBookReadingStatusToMemory(
        book: Book, 
        appDb: io.legado.app.data.AppDatabase
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val memory = appDb.readingMemoryDao.getByBookUrl(book.bookUrl) ?: return@withContext true
                
                // 只有当用户未修改过我的阅读记录的阅读状态时才同步
                if (!memory.userModifiedReadingStatus) {
                    // 将Book的ReadingStatus枚举转换为ReadingMemory的ReadingStatus枚举
                    val bookStatusEnum = io.legado.app.constant.ReadingStatus.fromValue(book.readingStatus)
                    
                    // 如果状态有变化，更新我的阅读记录
                    if (memory.readingStatus != bookStatusEnum) {
                        val updatedMemory = memory.copy(
                            readingStatus = bookStatusEnum,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.readingMemoryDao.update(updatedMemory)
                    }
                }
                
                true // 同步成功
            }
        } catch (e: Exception) {
            false // 同步失败
        }
    }
    
    /**
     * 同步我的阅读记录的阅读状态到Book实体
     * @param memory 我的阅读记录
     * @param appDb 数据库实例
     * @return 是否同步成功
     */
    suspend fun syncMemoryReadingStatusToBook(
        memory: ReadingMemory, 
        appDb: io.legado.app.data.AppDatabase
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val book = appDb.bookDao.getBook(memory.bookUrl) ?: return@withContext true
                
                // 只有当用户未修改过Book的阅读状态时才同步
                if (!book.userModifiedReadingStatus) {
                    // 如果状态有变化，更新Book实体
                    if (book.readingStatus != memory.readingStatus.value) {
                        val updatedBook = book.copy()
                        updatedBook.setReadingStatus(memory.readingStatus, false) // 不标记为用户修改
                        appDb.bookDao.update(updatedBook)
                    }
                }
                
                true // 同步成功
            }
        } catch (e: Exception) {
            false // 同步失败
        }
    }
    
    /**
     * 同步书籍名称和作者变更到阅读会话
     * @param oldBookName 原书籍名称
     * @param oldBookAuthor 原作者名称
     * @param newBookName 新书籍名称
     * @param newBookAuthor 新作者名称
     */
    suspend fun syncBookNameChangeToReadSessions(
        oldBookName: String, 
        oldBookAuthor: String, 
        newBookName: String, 
        newBookAuthor: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 获取所有相关的阅读会话
                val sessions = appDb.readSessionDao.getByBook(oldBookName, oldBookAuthor)
                
                // 更新每个阅读会话的书籍名称和作者
                sessions.forEach { session ->
                    val updatedSession = session.copy(
                        bookName = newBookName,
                        author = newBookAuthor
                    )
                    appDb.readSessionDao.update(updatedSession)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 统一更新书籍阅读状态
     * 同时更新Book实体和我的阅读记录
     * @param bookUrl 书籍URL
     * @param status 阅读状态
     * @param userModified 是否由用户修改
     */
    suspend fun updateBookReadingStatus(
        bookUrl: String, 
        status: io.legado.app.constant.ReadingStatus, 
        userModified: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 更新Book实体
                val book = appDb.bookDao.getBook(bookUrl)
                if (book != null) {
                    val updatedBook = book.copy()
                    updatedBook.setReadingStatus(status, userModified)
                    appDb.bookDao.update(updatedBook)
                }
                
                // 更新我的阅读记录
                val readingMemory = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                if (readingMemory != null) {
                    val updatedReadingMemory = readingMemory.copy(
                        readingStatus = status,
                        userModifiedReadingStatus = userModified,
                        updateTime = System.currentTimeMillis()
                    )
                    appDb.readingMemoryDao.update(updatedReadingMemory)
                }
                
                // 更新书籍分组
                ReadingStatusGroupHelper.updateBookGroupByReadingStatus(bookUrl, status, userModified)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 根据阅读进度自动计算并更新阅读状态
     * @param bookUrl 书籍URL
     * @param progress 阅读进度
     * @param readTime 阅读时间
     */
    suspend fun autoUpdateReadingStatus(
        bookUrl: String, 
        progress: Float, 
        readTime: Long
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 获取Book实体
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext
                
                // 如果用户已手动修改过阅读状态，则不自动更新
                if (book.userModifiedReadingStatus) {
                    return@withContext
                }
                
                // 根据进度计算阅读状态
                val newStatus = when {
                    progress >= 100f -> io.legado.app.constant.ReadingStatus.FINISHED
                    progress > 0f -> io.legado.app.constant.ReadingStatus.READING
                    else -> io.legado.app.constant.ReadingStatus.PENDING
                }
                
                // 如果状态有变化，更新
                if (book.readingStatus != newStatus.value) {
                    updateBookReadingStatus(bookUrl, newStatus, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 统一更新书籍名称和作者
     * 同时更新Book实体、阅读记忆和阅读会话
     * @param bookUrl 书籍URL
     * @param oldBookName 原书籍名称
     * @param oldBookAuthor 原作者名称
     * @param newBookName 新书籍名称
     * @param newBookAuthor 新作者名称
     */
    suspend fun updateBookNameAndAuthor(
        bookUrl: String, 
        oldBookName: String, 
        oldBookAuthor: String, 
        newBookName: String, 
        newBookAuthor: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 更新Book实体
                val book = appDb.bookDao.getBook(bookUrl)
                if (book != null) {
                    val updatedBook = book.copy(
                        name = newBookName,
                        author = newBookAuthor
                    )
                    appDb.bookDao.update(updatedBook)
                }
                
                // 2. 同步到阅读会话
                syncBookNameChangeToReadSessions(oldBookName, oldBookAuthor, newBookName, newBookAuthor)
                
                // 3. 同步到阅读记忆
                // 更新通过bookUrl查询到的阅读记忆
                val readingMemoryByUrl = appDb.readingMemoryDao.getByBookUrl(bookUrl)
                if (readingMemoryByUrl != null) {
                    val updatedMemory = readingMemoryByUrl.copy(
                        bookName = newBookName,
                        bookAuthor = newBookAuthor,
                        updateTime = System.currentTimeMillis()
                    )
                    appDb.readingMemoryDao.update(updatedMemory)
                }
                
                // 更新通过原书名和作者查询到的阅读记忆
                val memoriesByBook = appDb.readingMemoryDao.getByBook(oldBookName, oldBookAuthor)
                for (memory in memoriesByBook) {
                    // 跳过已经通过bookUrl更新的记录
                    if (memory.bookUrl == bookUrl) continue
                    
                    val updatedMemory = memory.copy(
                        bookName = newBookName,
                        bookAuthor = newBookAuthor,
                        updateTime = System.currentTimeMillis()
                    )
                    appDb.readingMemoryDao.update(updatedMemory)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 同步书籍名称和作者变更到阅读记忆（已废弃，使用updateBookNameAndAuthor替代）
     */
    @Deprecated("Use updateBookNameAndAuthor instead")
    suspend fun syncBookNameChangeToReadingMemory(
        bookUrl: String, 
        oldBookName: String, 
        oldBookAuthor: String, 
        newBookName: String, 
        newBookAuthor: String
    ) {
        updateBookNameAndAuthor(bookUrl, oldBookName, oldBookAuthor, newBookName, newBookAuthor)
    }
}