package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.TagMapping

/**
 * 标签映射数据访问对象
 */
@Dao
interface TagMappingDao {

    /**
     * 获取所有标签映射
     */
    @Query("SELECT * FROM tagMappings ORDER BY createTime DESC")
    suspend fun getAll(): List<TagMapping>

    /**
     * 根据旧标签名称获取标签映射
     */
    @Query("SELECT * FROM tagMappings WHERE oldTagName = :oldTagName LIMIT 1")
    suspend fun getMappingByOldName(oldTagName: String): TagMapping?

    /**
     * 根据新标签ID获取标签映射
     */
    @Query("SELECT * FROM tagMappings WHERE newTagId = :newTagId")
    suspend fun getMappingsByNewTagId(newTagId: Long): List<TagMapping>

    /**
     * 插入标签映射
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tagMapping: TagMapping): Long

    /**
     * 批量插入标签映射
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tagMappings: List<TagMapping>): List<Long>

    /**
     * 更新标签映射
     */
    @Update
    suspend fun update(tagMapping: TagMapping)

    /**
     * 删除标签映射
     */
    @Delete
    suspend fun delete(tagMapping: TagMapping)

    /**
     * 根据ID删除标签映射
     */
    @Query("DELETE FROM tagMappings WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 根据旧标签名称删除标签映射
     */
    @Query("DELETE FROM tagMappings WHERE oldTagName = :oldTagName")
    suspend fun deleteByOldName(oldTagName: String)

    /**
     * 根据新标签ID删除标签映射
     */
    @Query("DELETE FROM tagMappings WHERE newTagId = :newTagId")
    suspend fun deleteByNewTagId(newTagId: Long)

    /**
     * 删除所有标签映射
     */
    @Query("DELETE FROM tagMappings")
    suspend fun deleteAll()
}
