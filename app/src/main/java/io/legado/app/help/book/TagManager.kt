package io.legado.app.help.book

import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.utils.TagColorUtils
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 标签管理器，统一处理标签相关操作
 */
object TagManager {

    // 标签缓存，避免重复查询数据库，添加过期策略和大小限制
    private val tagCache = object : LinkedHashMap<String, Pair<Long, List<BookTag>>>() {
        override fun removeEldestEntry(eldest: Map.Entry<String, Pair<Long, List<BookTag>>>): Boolean {
            // 缓存大小限制为1000条
            if (size > 1000) return true
            // 缓存过期时间为5分钟
            return System.currentTimeMillis() - eldest.value.first > 5 * 60 * 1000
        }
    }

    /**
     * 加载书籍标签
     */
    suspend fun loadBookTags(bookUrl: String): List<BookTag> {
        return withContext(Dispatchers.IO) {
            // 检查缓存
            tagCache[bookUrl]?.let { (timestamp, tags) ->
                if (System.currentTimeMillis() - timestamp < 5 * 60 * 1000) {
                    return@withContext tags
                }
            }

            // 获取标签关联关系
            val tagRelations = appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
            // 获取被排除的标签
            val excludedTags = appDb.excludedTagDao.getAllSync()
            val excludedTagNames = excludedTags.map { it.name }.toSet()

            // 获取标签详情
            val tags = if (tagRelations.isNotEmpty()) {
                val tagIds = tagRelations.map { it.tagId }
                val allTags = appDb.bookTagDao.getTagsByIds(tagIds)
                // 过滤掉被排除的标签
                val filteredTags = allTags.filter { tag -> 
                    !excludedTagNames.contains(tag.name)
                }
                
                // 获取所有标签分组，按排序顺序
                val tagGroups = appDb.bookTagGroupDao.getAllSorted()
                val groupSortOrder = tagGroups.associateBy({ it.id }, { it.sortOrder })
                
                // 按分组排序，同一分组内按名称排序
                filteredTags.sortedWith(compareBy<BookTag> {
                    // 先按分组排序，未分组的放最后
                    groupSortOrder[it.groupId] ?: Int.MAX_VALUE
                }.thenBy { 
                    // 同一分组内按名称排序
                    it.name
                })
            } else {
                emptyList()
            }

            // 缓存结果
            tagCache[bookUrl] = Pair(System.currentTimeMillis(), tags)
            tags
        }
    }

    /**
     * 从书籍分类生成标签
     */
    suspend fun generateTagsFromKind(book: Book): List<BookTag> {
        return withContext(Dispatchers.IO) {
            if (book.kind.isNullOrBlank()) {
                return@withContext emptyList()
            }

            // 检查书源是否为正版
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true

            if (!isOfficialSource) {
                return@withContext emptyList()
            }

            // 处理分类信息
            val processedTags = processKindTags(book.kind!!)

            if (processedTags.isEmpty()) {
                return@withContext emptyList()
            }

            // 获取被排除的标签
            val excludedTags = appDb.excludedTagDao.getAllSync()
            val excludedTagNames = excludedTags.map { it.name }.toSet()

            // 过滤被排除的标签
            val filteredTags = processedTags.filter { (tagName, _) -> 
                !excludedTagNames.contains(tagName)
            }

            if (filteredTags.isEmpty()) {
                return@withContext emptyList()
            }

            // 批量处理标签
            val resultTags = mutableListOf<BookTag>()
            val newTags = mutableListOf<BookTag>()
            val newRelations = mutableListOf<BookTagRelation>()

            // 批量查询现有标签
            val tagNames = filteredTags.map { it.first }
            val existingTags = appDb.bookTagDao.getTagsByNames(tagNames)
            val existingTagsMap = existingTags.associateBy { it.name }.toMutableMap()

            // 批量查询标签映射关系
            val tagMappings = appDb.tagMappingDao.getAll()
            val tagMappingMap = tagMappings.associateBy { it.oldTagName }

            for ((tagName, tagColor) in filteredTags) {
                var tag = existingTagsMap[tagName]

                if (tag == null) {
                    // 检查是否存在标签映射关系
                    val tagMapping = tagMappingMap[tagName]
                    if (tagMapping != null) {
                        // 使用映射后的标签
                        val mappedTag = appDb.bookTagDao.getTag(tagMapping.newTagId)
                        if (mappedTag != null) {
                            tag = mappedTag
                            resultTags.add(tag)
                        } else {
                            // 如果映射的标签不存在，创建新标签
                            val newTag = BookTag(
                                name = tagName,
                                color = tagColor,
                                createTime = System.currentTimeMillis()
                            )
                            newTags.add(newTag)
                        }
                    } else {
                        // 创建新标签
                        val newTag = BookTag(
                            name = tagName,
                            color = tagColor,
                            createTime = System.currentTimeMillis()
                        )
                        newTags.add(newTag)
                    }
                } else {
                    resultTags.add(tag)
                }
            }

            // 批量插入新标签
            if (newTags.isNotEmpty()) {
                val tagIds = appDb.bookTagDao.insertAll(newTags)
                newTags.forEachIndexed { index, tag ->
                    val newTagWithId = tag.copy(id = tagIds[index])
                    resultTags.add(newTagWithId)
                    existingTagsMap[tag.name] = newTagWithId
                }
            }

            // 批量创建关联关系
            for ((tagName, _) in filteredTags) {
                var tag = existingTagsMap[tagName]
                
                // 检查是否存在标签映射关系
                val tagMapping = tagMappingMap[tagName]
                if (tagMapping != null) {
                    val mappedTag = appDb.bookTagDao.getTag(tagMapping.newTagId)
                    if (mappedTag != null) {
                        tag = mappedTag
                    }
                }
                
                if (tag != null) {
                    val relation = BookTagRelation(
                        id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                        bookUrl = book.bookUrl,
                        tagId = tag.id,
                        createTime = System.currentTimeMillis()
                    )
                    newRelations.add(relation)
                }
            }

            // 批量插入关联关系
            if (newRelations.isNotEmpty()) {
                appDb.bookTagRelationDao.insertAll(newRelations)
            }

            // 清除缓存
            clearCache(book.bookUrl)

            resultTags
        }
    }

    /**
     * 处理分类信息，生成标签
     */
    fun processKindTags(kindString: String): List<Pair<String, Int>> {
        if (kindString.isBlank()) {
            return emptyList()
        }

        // 使用逗号或换行符分割标签
        val tags = kindString.split(Regex("[,\n]")
            ).map { it.trim() }
            .filter { it.isNotEmpty() }

        // 过滤标签
        val filteredTags = tags.filter { tag ->
            // 排除包含标点的标签（只允许中文、英文、数字）
            if (!tag.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$") )) {
                return@filter false
            }

            // 排除包含数字的标签
            if (tag.any { char -> char.isDigit() }) {
                return@filter false
            }

            // 限制标签长度不超过5个字
            if (tag.length > 5) {
                return@filter false
            }

            return@filter true
        }.distinct() // 去重

        // 为每个标签生成颜色
        return filteredTags.map { tag ->
            Pair(tag, TagColorUtils.generateRandomColor(tag))
        }
    }

    /**
     * 添加标签到书籍
     */
    suspend fun addTagToBook(bookUrl: String, tagId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 检查标签是否存在
                val tag = appDb.bookTagDao.getTag(tagId)
                if (tag == null) {
                    return@withContext false
                }

                // 检查关联关系是否已存在
                val existingRelation = appDb.bookTagRelationDao.getRelation(bookUrl, tagId)
                if (existingRelation != null) {
                    return@withContext false
                }

                // 创建关联关系
                val relation = BookTagRelation(
                    id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                    bookUrl = bookUrl,
                    tagId = tagId,
                    createTime = System.currentTimeMillis()
                )
                appDb.bookTagRelationDao.insert(relation)

                // 清除缓存
                clearCache(bookUrl)

                // 发送标签更新事件
                postEvent(EventBus.TAGS_UPDATED, bookUrl)

            return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * 从书籍移除标签
     */
    suspend fun removeTagFromBook(bookUrl: String, tagId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 检查关联关系是否存在
                val relation = appDb.bookTagRelationDao.getRelation(bookUrl, tagId)
                if (relation == null) {
                    return@withContext false
                }

                // 删除关联关系
                appDb.bookTagRelationDao.delete(relation)

                // 清除缓存
                clearCache(bookUrl)

                // 发送标签更新事件
                postEvent(EventBus.TAGS_UPDATED, bookUrl)

                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache(bookUrl: String) {
        tagCache.remove(bookUrl)
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        tagCache.clear()
    }

    /**
     * 书源改变时更新标签
     * 先删除旧的标签关联，然后根据新的kind字段重新生成标签
     */
    suspend fun updateTagsOnSourceChange(book: Book): List<BookTag> {
        return withContext(Dispatchers.IO) {
            // 检查书源是否为正版
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            val isOfficialSource = bookSource?.bookSourceGroup?.contains("正版") == true

            // 清除缓存
            clearCache(book.bookUrl)

            if (isOfficialSource) {
                // 正版书源：删除旧的标签关联，然后根据新的kind字段重新生成标签
                appDb.bookTagRelationDao.deleteRelationsByBook(book.bookUrl)
                val tags = generateTagsFromKind(book)
                postEvent(EventBus.TAGS_UPDATED, book.bookUrl)
                return@withContext tags
            } else {
                // 非正版书源：保留原有的标签关联，直接返回现有标签
                val tags = loadBookTags(book.bookUrl)
                postEvent(EventBus.TAGS_UPDATED, book.bookUrl)
                return@withContext tags
            }
        }
    }
}
