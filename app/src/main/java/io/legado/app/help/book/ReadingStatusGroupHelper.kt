package io.legado.app.help.book

import io.legado.app.R
import io.legado.app.constant.ReadingStatus
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读状态分组管理工具类
 * 用于创建和管理基于阅读状态的书籍分组
 */
object ReadingStatusGroupHelper {

    // 阅读状态分组的ID定义
    // 使用2的幂次方作为分组ID，确保位运算正确
    const val GROUP_ID_PENDING = 1L shl 20  // 待读分组ID (1048576)
    const val GROUP_ID_READING = 1L shl 21  // 在读分组ID (2097152)
    const val GROUP_ID_FINISHED = 1L shl 22  // 读完分组ID (4194304)
    const val GROUP_ID_ABANDONED = 1L shl 23  // 弃文分组ID (8388608)

    /**
     * 初始化阅读状态分组
     * 如果分组不存在，则创建对应的分组
     */
    suspend fun initReadingStatusGroups() {
        withContext(Dispatchers.IO) {
            try {
                // 检查并创建待读分组
                if (appDb.bookGroupDao.getByID(GROUP_ID_PENDING) == null) {
                    val pendingGroup = BookGroup(
                        groupId = GROUP_ID_PENDING,
                        groupName = "待读",
                        order = getMaxOrder() + 1
                    )
                    appDb.bookGroupDao.insert(pendingGroup)
                }

                // 检查并创建在读分组
                if (appDb.bookGroupDao.getByID(GROUP_ID_READING) == null) {
                    val readingGroup = BookGroup(
                        groupId = GROUP_ID_READING,
                        groupName = "在读",
                        order = getMaxOrder() + 1
                    )
                    appDb.bookGroupDao.insert(readingGroup)
                }

                // 检查并创建读完分组
                if (appDb.bookGroupDao.getByID(GROUP_ID_FINISHED) == null) {
                    val finishedGroup = BookGroup(
                        groupId = GROUP_ID_FINISHED,
                        groupName = "读完",
                        order = getMaxOrder() + 1
                    )
                    appDb.bookGroupDao.insert(finishedGroup)
                }

                // 检查并创建弃文分组
                if (appDb.bookGroupDao.getByID(GROUP_ID_ABANDONED) == null) {
                    val abandonedGroup = BookGroup(
                        groupId = GROUP_ID_ABANDONED,
                        groupName = "弃文",
                        order = getMaxOrder() + 1
                    )
                    appDb.bookGroupDao.insert(abandonedGroup)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 根据阅读状态获取对应的分组ID
     */
    fun getGroupIdByReadingStatus(status: ReadingStatus): Long {
        return when (status) {
            ReadingStatus.PENDING -> GROUP_ID_PENDING
            ReadingStatus.READING -> GROUP_ID_READING
            ReadingStatus.FINISHED -> GROUP_ID_FINISHED
            ReadingStatus.ABANDONED -> GROUP_ID_ABANDONED
        }
    }

    /**
     * 根据分组ID获取对应的阅读状态
     */
    fun getReadingStatusByGroupId(groupId: Long): ReadingStatus? {
        return when (groupId) {
            GROUP_ID_PENDING -> ReadingStatus.PENDING
            GROUP_ID_READING -> ReadingStatus.READING
            GROUP_ID_FINISHED -> ReadingStatus.FINISHED
            GROUP_ID_ABANDONED -> ReadingStatus.ABANDONED
            else -> null
        }
    }

    /**
     * 更新书籍的分组，根据其阅读状态
     * @param bookUrl 书籍URL
     * @param status 阅读状态
     * @param forceUpdate 是否强制更新，即使已手动修改过状态
     */
    suspend fun updateBookGroupByReadingStatus(bookUrl: String, status: ReadingStatus, forceUpdate: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext
                
                // 如果用户已手动修改过阅读状态且不强制更新，则跳过
                if (book.userModifiedReadingStatus && !forceUpdate) {
                    return@withContext
                }
                
                // 获取对应的分组ID
                val groupId = getGroupIdByReadingStatus(status)
                
                // 清除所有阅读状态相关的分组ID，然后设置新的分组ID
                // 使用位运算确保只保留非阅读状态分组的ID
                val readingStatusGroupIds = listOf(GROUP_ID_PENDING, GROUP_ID_READING, GROUP_ID_FINISHED, GROUP_ID_ABANDONED)
                var newGroupValue = book.group
                
                // 清除所有阅读状态分组的ID
                for (statusGroupId in readingStatusGroupIds) {
                    newGroupValue = newGroupValue and statusGroupId.inv()
                }
                
                // 设置新的阅读状态分组ID
                newGroupValue = newGroupValue or groupId
                
                // 更新书籍的分组
                book.group = newGroupValue
                appDb.bookDao.update(book)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 批量更新所有书籍的分组，根据其阅读状态
     */
    suspend fun updateAllBooksGroupByReadingStatus() {
        withContext(Dispatchers.IO) {
            try {
                val allBooks = appDb.bookDao.all
                val readingStatusGroupIds = listOf(GROUP_ID_PENDING, GROUP_ID_READING, GROUP_ID_FINISHED, GROUP_ID_ABANDONED)
                
                for (book in allBooks) {
                    // 获取书籍的阅读状态
                    val status = ReadingStatus.fromValue(book.readingStatus)
                    
                    // 获取对应的分组ID
                    val groupId = getGroupIdByReadingStatus(status)
                    
                    // 清除所有阅读状态相关的分组ID，然后设置新的分组ID
                    var newGroupValue = book.group
                    
                    // 清除所有阅读状态分组的ID
                    for (statusGroupId in readingStatusGroupIds) {
                        newGroupValue = newGroupValue and statusGroupId.inv()
                    }
                    
                    // 设置新的阅读状态分组ID
                    newGroupValue = newGroupValue or groupId
                    
                    // 如果分组有变化，更新书籍
                    if (book.group != newGroupValue) {
                        book.group = newGroupValue
                        appDb.bookDao.update(book)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取当前最大的分组排序值
     */
    private suspend fun getMaxOrder(): Int {
        return try {
            appDb.bookGroupDao.maxOrder
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 获取阅读状态分组的显示名称
     * @param context 上下文
     * @param status 阅读状态
     * @return 显示名称
     */
    fun getGroupDisplayName(status: ReadingStatus): String {
        return when (status) {
            ReadingStatus.PENDING -> "待读"
            ReadingStatus.READING -> "在读"
            ReadingStatus.FINISHED -> "读完"
            ReadingStatus.ABANDONED -> "弃文"
        }
    }
}