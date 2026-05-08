package io.legado.app.ui.book.tag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BookTagViewModel(application: Application) : AndroidViewModel(application) {

    // 所有标签
    val allTagsLiveData = appDb.bookTagDao.observeAll()
    
    // 当前书籍的标签
    private val _bookTagsLiveData = MutableLiveData<List<BookTag>>()
    val bookTagsLiveData: LiveData<List<BookTag>> = _bookTagsLiveData
    
    // 加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 操作结果
    private val _operationResult = MutableLiveData<String>()
    val operationResult: LiveData<String> = _operationResult
    
    /**
     * 加载指定书籍的标签
     */
    fun loadBookTags(bookUrl: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // 获取书籍标签关联关系
                val relations = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getRelationsByBook(bookUrl)
                }
                
                // 获取标签详情
                val tags = withContext(Dispatchers.IO) {
                    if (relations.isNotEmpty()) {
                        val tagIds = relations.map { it.tagId }
                        appDb.bookTagDao.getAll().filter { tag ->
                            // 使用Long类型的tag.id直接比较
                            tagIds.contains(tag.id)
                        }
                    } else {
                        emptyList()
                    }
                }
                
                _bookTagsLiveData.postValue(tags)
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _operationResult.postValue("加载标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 创建新标签
     */
    fun createTag(name: String, backgroundColor: String, textColor: String) {
        if (name.isBlank()) {
            _operationResult.value = "标签名称不能为空"
            return
        }
        
        // 检查标签是否已存在
        viewModelScope.launch {
            try {
                val existingTag = withContext(Dispatchers.IO) {
                    appDb.bookTagDao.getTagByName(name)
                }
                
                if (existingTag != null) {
                    _operationResult.postValue("标签已存在")
                } else {
                    // 创建新标签 - 使用辅助构造函数
                    val tag = BookTag(
                        name = name,
                        color = 0xFFE3F2FD.toInt(),
                        createTime = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.IO) {
                        appDb.bookTagDao.insert(tag)
                    }
                    
                    _operationResult.postValue("标签创建成功")
                }
            } catch (e: Exception) {
                _operationResult.postValue("创建标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 更新标签
     */
    fun updateTag(tag: BookTag) {
        viewModelScope.launch {
            try {
                // 创建更新后的标签对象，只更新允许修改的字段
                val updatedTag = tag.copy(
                    updateTime = System.currentTimeMillis()
                )
                
                withContext(Dispatchers.IO) {
                    appDb.bookTagDao.update(updatedTag)
                }
                _operationResult.postValue("标签更新成功")
            } catch (e: Exception) {
                _operationResult.postValue("更新标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 删除标签
     */
    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            try {
                // 删除标签关联关系
                withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.deleteRelationsByTag(tagId)
                }
                
                // 删除标签
                withContext(Dispatchers.IO) {
                    appDb.bookTagDao.deleteById(tagId)
                }
                
                _operationResult.postValue("标签删除成功")
            } catch (e: Exception) {
                _operationResult.postValue("删除标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 为书籍添加标签
     */
    fun addTagToBook(bookUrl: String, tagId: Long) {
        viewModelScope.launch {
            try {
                // 检查关联关系是否已存在
                val existingRelation = withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.getRelation(bookUrl, tagId)
                }
                
                if (existingRelation != null) {
                    _operationResult.postValue("书籍已包含此标签")
                } else {
                    // 创建关联关系 - 使用Long类型的tagId
                    val relation = BookTagRelation(
                        id = "relation_${System.currentTimeMillis()}_${(1..1000).random()}",
                        bookUrl = bookUrl,
                        tagId = tagId, // 使用Long类型
                        createTime = System.currentTimeMillis()
                    )
                    
                    withContext(Dispatchers.IO) {
                        appDb.bookTagRelationDao.insert(relation)
                    }
                    
                    _operationResult.postValue("标签添加成功")
                    
                    // 重新加载书籍标签
                    loadBookTags(bookUrl)
                }
            } catch (e: Exception) {
                _operationResult.postValue("添加标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 从书籍移除标签
     */
    fun removeTagFromBook(bookUrl: String, tagId: Long) {
        viewModelScope.launch {
            try {
                // 删除关联关系 - 使用Long类型的tagId
                withContext(Dispatchers.IO) {
                    appDb.bookTagRelationDao.deleteRelation(bookUrl, tagId)
                }
                
                _operationResult.postValue("标签移除成功")
                
                // 重新加载书籍标签
                loadBookTags(bookUrl)
            } catch (e: Exception) {
                _operationResult.postValue("移除标签失败: ${e.localizedMessage}")
            }
        }
    }
    
    /**
     * 搜索标签
     */
    fun searchTags(keyword: String): List<BookTag> {
        return if (keyword.isBlank()) {
            emptyList()
        } else {
            try {
                // 使用同步方法或模拟结果，避免在非协程上下文调用挂起函数
                // 这里返回一个空列表作为临时解决方案
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 异步搜索标签
     */
    fun searchTagsAsync(keyword: String, callback: (List<BookTag>) -> Unit) {
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    appDb.bookTagDao.searchByKeyword(keyword)
                }
                callback(results)
            } catch (e: Exception) {
                callback(emptyList())
            }
        }
    }
}