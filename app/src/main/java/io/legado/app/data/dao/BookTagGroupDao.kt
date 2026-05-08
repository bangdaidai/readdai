package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookTagGroup

/**
 * 标签分组数据库操作接口
 */
@Dao
interface BookTagGroupDao {

    /**
     * 获取所有分组
     */
    @Query("SELECT * FROM bookTagGroups")
    fun getAll(): List<BookTagGroup>

    /**
     * 获取所有分组，按排序顺序排列
     */
    @Query("SELECT * FROM bookTagGroups ORDER BY sortOrder ASC")
    fun getAllSorted(): List<BookTagGroup>

    /**
     * 根据ID获取分组
     */
    @Query("SELECT * FROM bookTagGroups WHERE id = :id")
    fun getById(id: Long): BookTagGroup?

    /**
     * 根据名称获取分组
     */
    @Query("SELECT * FROM bookTagGroups WHERE name = :name")
    fun getByName(name: String): BookTagGroup?

    /**
     * 获取最大排序值
     */
    @Query("SELECT MAX(sortOrder) FROM bookTagGroups")
    fun getMaxSortOrder(): Int

    /**
     * 插入分组
     */
    @Insert
    fun insert(group: BookTagGroup): Long

    /**
     * 更新分组
     */
    @Update
    fun update(group: BookTagGroup)

    /**
     * 更新分组排序
     */
    @Query("UPDATE bookTagGroups SET sortOrder = :sortOrder WHERE id = :id")
    fun updateSortOrder(id: Long, sortOrder: Int)

    /**
     * 删除分组
     */
    @Delete
    fun delete(group: BookTagGroup)

    /**
     * 根据ID删除分组
     */
    @Query("DELETE FROM bookTagGroups WHERE id = :id")
    fun deleteById(id: Long)

    /**
     * 根据名称删除分组
     */
    @Query("DELETE FROM bookTagGroups WHERE name = :name")
    fun deleteByName(name: String)

    /**
     * 清空所有分组
     */
    @Query("DELETE FROM bookTagGroups")
    fun clear()
}