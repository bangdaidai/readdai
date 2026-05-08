package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ExcludedTag
import kotlinx.coroutines.flow.Flow

/**
 * 排除标签数据访问对象
 */
@Dao
interface ExcludedTagDao {

    /**
     * 获取所有排除标签
     */
    @Query("SELECT * FROM excludedTags ORDER BY createTime DESC")
    fun getAll(): Flow<List<ExcludedTag>>

    /**
     * 获取所有排除标签（同步方法）
     */
    @Query("SELECT * FROM excludedTags ORDER BY createTime DESC")
    suspend fun getAllSync(): List<ExcludedTag>

    /**
     * 根据ID获取排除标签
     */
    @Query("SELECT * FROM excludedTags WHERE id = :id")
    suspend fun getTag(id: Long): ExcludedTag?

    /**
     * 根据名称获取排除标签
     */
    @Query("SELECT * FROM excludedTags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): ExcludedTag?

    /**
     * 检查标签是否被排除
     */
    @Query("SELECT COUNT(*) FROM excludedTags WHERE name = :name")
    suspend fun isExcluded(name: String): Int

    /**
     * 插入排除标签
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: ExcludedTag): Long

    /**
     * 更新排除标签
     */
    @Update
    suspend fun update(tag: ExcludedTag)

    /**
     * 删除排除标签
     */
    @Delete
    suspend fun delete(tag: ExcludedTag)

    /**
     * 根据ID删除排除标签
     */
    @Query("DELETE FROM excludedTags WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有排除标签
     */
    @Query("DELETE FROM excludedTags")
    suspend fun deleteAll()
}