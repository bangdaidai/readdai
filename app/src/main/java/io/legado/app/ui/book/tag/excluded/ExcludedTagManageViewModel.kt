package io.legado.app.ui.book.tag.excluded

import androidx.fragment.app.FragmentActivity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExcludedTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 排除标签管理ViewModel
 */
class ExcludedTagManageViewModel : ViewModel() {
    
    private val _tags = MutableLiveData<List<ExcludedTag>>()
    val tags: LiveData<List<ExcludedTag>> = _tags
    
    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg
    
    init {
        loadTags()
    }
    
    /**
     * 加载所有排除标签
     */
    fun loadTags() {
        viewModelScope.launch {
            try {
                val tagList = withContext(Dispatchers.IO) {
                    appDb.excludedTagDao.getAllSync()
                }
                _tags.value = tagList
            } catch (e: Exception) {
                _errorMsg.value = "加载排除标签失败: ${e.localizedMessage}"
            }
        }
    }
    
    /**
     * 显示添加标签对话框
     */
    fun showAddTagDialog(activity: androidx.fragment.app.FragmentActivity) {
        ExcludedTagEditDialog.show(
            fragmentManager = activity.supportFragmentManager,
            callback = { tag ->
                addTag(tag)
            }
        )
    }
    
    /**
     * 添加排除标签
     */
    fun addTag(tag: ExcludedTag) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 检查排除标签是否已存在
                    val existingTag: ExcludedTag? = appDb.excludedTagDao.getTagByName(tag.name)
                    if (existingTag == null) {
                        appDb.excludedTagDao.insert(tag)
                    }
                }
                loadTags()
            } catch (e: Exception) {
                _errorMsg.value = "添加排除标签失败: ${e.localizedMessage}"
            }
        }
    }
    
    /**
     * 更新排除标签
     */
    fun updateTag(tag: ExcludedTag) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDb.excludedTagDao.update(tag)
                }
                loadTags()
            } catch (e: Exception) {
                _errorMsg.value = "更新排除标签失败: ${e.localizedMessage}"
            }
        }
    }
    
    /**
     * 删除排除标签
     */
    fun deleteTag(tag: ExcludedTag) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appDb.excludedTagDao.delete(tag)
                }
                loadTags()
            } catch (e: Exception) {
                _errorMsg.value = "删除排除标签失败: ${e.localizedMessage}"
            }
        }
    }
}