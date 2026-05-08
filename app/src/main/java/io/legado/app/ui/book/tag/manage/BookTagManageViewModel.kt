package io.legado.app.ui.book.tag.manage

import androidx.fragment.app.FragmentActivity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.ui.widget.dialog.BookTagEditDialog
import io.legado.app.utils.TagColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书籍标签管理ViewModel
 */
class BookTagManageViewModel(application: android.app.Application) : BaseViewModel(application) {

    private val _tags = MutableLiveData<List<BookTag>>()
    val tags: LiveData<List<BookTag>> = _tags
    private val _tagUpdated = MutableLiveData<Pair<BookTag, String>>() // 标签更新事件，Pair<标签, 更新类型>
    val tagUpdated: LiveData<Pair<BookTag, String>> = _tagUpdated
    private val _groups = MutableLiveData<List<io.legado.app.data.entities.BookTagGroup>>()
    val groups: LiveData<List<io.legado.app.data.entities.BookTagGroup>> = _groups
    private var isAddingTag = false // 防止重复添加标签的标志

    init {
        loadTags()
        loadGroups()
    }

    /**
     * 加载所有标签，包含书籍数量统计，按分组和使用次数排序
     */
    fun loadTags() {
        viewModelScope.launch {
            try {
                android.util.Log.d("BookTagManageViewModel", "开始加载标签")
                // 获取所有标签
                val allTags = appDb.bookTagDao.getAll()
                android.util.Log.d("BookTagManageViewModel", "获取到 ${allTags.size} 个标签")

                // 获取所有排除标签
                val excludedTags = appDb.excludedTagDao.getAllSync()
                android.util.Log.d("BookTagManageViewModel", "获取到 ${excludedTags.size} 个排除标签")

                // 过滤掉排除的标签
                var filteredTags = allTags.filter { tag ->
                    excludedTags.none { excluded -> excluded.name == tag.name }
                }
                android.util.Log.d("BookTagManageViewModel", "过滤后剩余 ${filteredTags.size} 个标签")

                // 获取每个标签的书籍数量
                val tagBookCounts = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getTagBookCounts()
                }
                // 创建标签ID到书籍数量的映射
                val tagCountMap = tagBookCounts.associateBy({ it.tagId }, { it.bookCount })
                android.util.Log.d("BookTagManageViewModel", "获取到 ${tagBookCounts.size} 个标签的书籍数量统计")

                // 按分组ID和使用次数排序
                filteredTags = filteredTags.sortedWith(compareBy<BookTag> {
                    it.groupId
                }.thenByDescending { 
                    tagCountMap[it.id] ?: 0
                }.thenBy { 
                    it.name
                })

                _tags.postValue(filteredTags)
                android.util.Log.d("BookTagManageViewModel", "标签加载完成")
            } catch (e: Exception) {
                android.util.Log.e("BookTagManageViewModel", "加载标签失败", e)
            }
        }
    }

    /**
     * 加载所有分组
     */
    fun loadGroups() {
        viewModelScope.launch {
            try {
                val allGroups = appDb.bookTagGroupDao.getAllSorted()
                _groups.postValue(allGroups)
            } catch (e: Exception) {
                android.util.Log.e("BookTagManageViewModel", "加载分组失败", e)
            }
        }
    }

    /**
     * 添加新分组
     */
    fun addGroup(groupName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 检查分组是否已存在
                    val existingGroup = appDb.bookTagGroupDao.getByName(groupName)
                    if (existingGroup == null) {
                        // 创建新分组
                        val sortOrder = appDb.bookTagGroupDao.getMaxSortOrder() + 1
                        val newGroup = io.legado.app.data.entities.BookTagGroup(
                            name = groupName,
                            sortOrder = sortOrder
                        )
                        appDb.bookTagGroupDao.insert(newGroup)
                    }
                }
                // 重新加载分组列表
                loadGroups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 删除分组
     */
    fun deleteGroup(group: io.legado.app.data.entities.BookTagGroup) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 先将该分组下的所有标签移到未分组
                    appDb.bookTagDao.updateGroupIdByGroupId(group.id, 0)
                    // 然后删除分组本身
                    appDb.bookTagGroupDao.delete(group)
                }
                // 重新加载分组和标签列表
                loadGroups()
                loadTags()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 更新分组名称
     */
    fun updateGroupName(group: io.legado.app.data.entities.BookTagGroup, newName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 检查新名称是否已存在
                    val existingGroup = appDb.bookTagGroupDao.getByName(newName)
                    if (existingGroup == null || existingGroup.id == group.id) {
                        // 更新分组名称
                        val updatedGroup = group.copy(
                            name = newName,
                            updateTime = System.currentTimeMillis()
                        )
                        appDb.bookTagGroupDao.update(updatedGroup)
                    }
                }
                // 重新加载分组列表
                loadGroups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 更新分组排序
     */
    fun updateGroupSort(groups: List<io.legado.app.data.entities.BookTagGroup>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    groups.forEachIndexed { index, group ->
                        appDb.bookTagGroupDao.updateSortOrder(group.id, index)
                    }
                }
                // 重新加载分组列表
                loadGroups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 显示添加标签对话框
     */
    fun showAddTagDialog(activity: FragmentActivity) {
        BookTagEditDialog.show(
            fragmentManager = activity.supportFragmentManager,
            bookUrl = null,
            oldTagName = null,
            callback = { tagInfo: io.legado.app.ui.widget.dialog.BookTagEditDialog.TagInfo ->
                addTag(tagInfo.name, tagInfo.color, tagInfo.groupId)
            }
        )
    }

    /**
     * 显示添加标签对话框 - 接受Context参数的版本
     */
    fun showAddTagDialog(context: Context) {
        if (context is FragmentActivity) {
            showAddTagDialog(context)
        } else {
            // 如果Context不是FragmentActivity，则不执行任何操作
            // 或者可以抛出异常，取决于需求
        }
    }
    
    /**
     * 搜索标签
     */
    fun searchTags(keyword: String) {
        viewModelScope.launch {
            try {
                val tags = withContext(Dispatchers.IO) {
                    if (keyword.isBlank()) {
                        appDb.bookTagDao.getAll()
                    } else {
                        appDb.bookTagDao.searchByKeyword(keyword)
                    }
                }
                
                // 获取所有排除标签
                val excludedTags = appDb.excludedTagDao.getAllSync()
                
                // 过滤掉排除的标签
                val filteredTags = tags.filter { tag ->
                    excludedTags.none { excluded -> excluded.name == tag.name }
                }
                
                // 获取每个标签的书籍数量
                val tagBookCounts = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getTagBookCounts()
                }
                // 创建标签ID到书籍数量的映射
                val tagCountMap = tagBookCounts.associateBy({ it.tagId }, { it.bookCount })
                
                // 按使用次数排序
                val sortedTags = filteredTags.sortedWith<io.legado.app.data.entities.BookTag>(compareByDescending<io.legado.app.data.entities.BookTag> { 
                    tagCountMap[it.id] ?: 0
                }.thenBy { 
                    it.name
                })
                
                _tags.postValue(sortedTags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 替换标签
     */
    fun replaceTag(oldTag: io.legado.app.data.entities.BookTag, newTagName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 检查新标签是否已存在
                    var newTag = appDb.bookTagDao.getTagByName(newTagName)
                    if (newTag == null) {
                        // 创建新标签
                        newTag = io.legado.app.data.entities.BookTag(
                            name = newTagName,
                            color = oldTag.color,
                            groupId = oldTag.groupId,
                            createTime = System.currentTimeMillis()
                        )
                        val tagId = appDb.bookTagDao.insert(newTag)
                        newTag = newTag.copy(id = tagId)
                    }
                    
                    // 创建标签映射关系，记录旧标签名称到新标签的映射
                    val tagMapping = io.legado.app.data.entities.TagMapping(
                        oldTagName = oldTag.name,
                        newTagId = newTag.id
                    )
                    appDb.tagMappingDao.insert(tagMapping)
                    
                    // 获取所有使用旧标签的书籍关联
                    val relations = appDb.bookTagRelationDao.getRelationsByTag(oldTag.id)
                    
                    // 将这些关联转移到新标签
                    relations.forEach { relation ->
                        // 检查新标签是否已经与该书籍关联
                        val existingRelation = appDb.bookTagRelationDao.getRelation(relation.bookUrl, newTag.id)
                        if (existingRelation == null) {
                            // 创建新的关联关系
                            val newRelation = io.legado.app.data.entities.BookTagRelation(
                                id = "relation_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                                bookUrl = relation.bookUrl,
                                tagId = newTag.id,
                                createTime = System.currentTimeMillis()
                            )
                            appDb.bookTagRelationDao.insert(newRelation)
                        }
                    }
                    
                    // 删除旧标签的所有关联
                    appDb.bookTagRelationDao.deleteRelationsByTag(oldTag.id)
                    
                    // 删除旧标签
                    appDb.bookTagDao.delete(oldTag)
                }
                
                // 重新加载标签列表
                loadTags()
                
                // 发送标签更新事件
                io.legado.app.utils.postEvent(io.legado.app.constant.EventBus.TAGS_UPDATED, "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 添加新标签
     */
    private fun addTag(tagName: String, color: Int = TagColorUtils.generateRandomColor(tagName), groupId: Long = 0) {
        // 防止重复添加
        if (isAddingTag) {
            return
        }

        isAddingTag = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 检查标签是否已存在
                    val existingTag = appDb.bookTagDao.getTagByName(tagName)
                    if (existingTag == null) {
                        // 创建新标签
                        val newTag = BookTag(
                            name = tagName,
                            color = color,
                            groupId = groupId,
                            createTime = System.currentTimeMillis()
                        )
                        appDb.bookTagDao.insert(newTag)
                    }
                }
                // 重新加载标签列表
                loadTags()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isAddingTag = false
            }
        }
    }

    /**
     * 删除标签
     */
    fun deleteTag(tag: BookTag) {
        viewModelScope.launch {
            try {
                android.util.Log.d("BookTagManageViewModel", "开始删除标签: ${tag.name}")
                withContext(Dispatchers.IO) {
                    // 先删除该标签与所有书籍的关联关系
                    val relationCount = appDb.bookTagRelationDao.countBooksByTagId(tag.id)
                    appDb.bookTagRelationDao.deleteRelationsByTag(tag.id)
                    android.util.Log.d("BookTagManageViewModel", "删除了 ${relationCount} 个标签关联关系")

                    // 然后删除标签本身
                    appDb.bookTagDao.delete(tag)
                    android.util.Log.d("BookTagManageViewModel", "成功删除标签: ${tag.name}")
                }
                // 重新加载标签列表
                loadTags()
            } catch (e: Exception) {
                android.util.Log.e("BookTagManageViewModel", "删除标签失败: ${tag.name}", e)
            }
        }
    }

    /**
     * 更新标签
     */
    fun updateTag(tag: BookTag, oldTag: BookTag? = null) {
        viewModelScope.launch {
            try {
                android.util.Log.d("BookTagManageViewModel", "开始更新标签: ${tag.name}")
                withContext(Dispatchers.IO) {
                    appDb.bookTagDao.update(tag)
                    android.util.Log.d("BookTagManageViewModel", "成功更新标签: ${tag.name}")
                }
                // 发送标签更新事件
                if (oldTag != null) {
                    val updateType = if (tag.color != oldTag.color && tag.name == oldTag.name) {
                        "COLOR_CHANGE"
                    } else if (tag.name != oldTag.name) {
                        "NAME_CHANGE"
                    } else if (tag.groupId != oldTag.groupId) {
                        "GROUP_CHANGE"
                    } else {
                        ""
                    }
                    if (updateType.isNotEmpty()) {
                        _tagUpdated.postValue(Pair(tag, updateType))
                        android.util.Log.d("BookTagManageViewModel", "发送标签更新事件: ${updateType}")
                    } else {
                        // 如果没有实际变化，不发送事件
                        android.util.Log.d("BookTagManageViewModel", "标签无实际变化，不发送更新事件")
                        return@launch
                    }
                } else {
                    // 如果没有旧标签信息，使用全量刷新
                    loadTags()
                }
            } catch (e: Exception) {
                android.util.Log.e("BookTagManageViewModel", "更新标签失败: ${tag.name}", e)
                // 发生错误时，使用全量刷新
                loadTags()
            }
        }
    }

    /**
     * 加载所有标签映射
     */
    suspend fun loadTagMappings(): List<io.legado.app.data.entities.TagMapping> {
        return withContext(Dispatchers.IO) {
            appDb.tagMappingDao.getAll()
        }
    }

    /**
     * 删除标签映射
     */
    fun deleteTagMapping(mapping: io.legado.app.data.entities.TagMapping) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDb.tagMappingDao.delete(mapping)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取标签名称
     */
    suspend fun getTagName(tagId: Long): String? {
        return withContext(Dispatchers.IO) {
            appDb.bookTagDao.getTag(tagId)?.name
        }
    }
}