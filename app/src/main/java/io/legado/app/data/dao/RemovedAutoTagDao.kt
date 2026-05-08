package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.RemovedAutoTag

@Dao
interface RemovedAutoTagDao {

    @Query("SELECT * FROM removedAutoTags WHERE bookUrl = :bookUrl")
    suspend fun getRemovedTagsByBook(bookUrl: String): List<RemovedAutoTag>

    @Query("SELECT * FROM removedAutoTags WHERE bookUrl = :bookUrl AND tagName = :tagName")
    suspend fun getRemovedTag(bookUrl: String, tagName: String): RemovedAutoTag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(removedTag: RemovedAutoTag): Long

    @Delete
    suspend fun delete(removedTag: RemovedAutoTag)

    @Query("DELETE FROM removedAutoTags WHERE bookUrl = :bookUrl AND tagName = :tagName")
    suspend fun deleteRemovedTag(bookUrl: String, tagName: String)

    @Query("DELETE FROM removedAutoTags WHERE bookUrl = :bookUrl")
    suspend fun deleteRemovedTagsByBook(bookUrl: String)

    @Query("DELETE FROM removedAutoTags WHERE tagName = :tagName")
    suspend fun deleteRemovedTagsByTagName(tagName: String)

    @Query("DELETE FROM removedAutoTags")
    suspend fun deleteAll()
    
    /**
     * 获取所有移除的自动标签
     */
    @Query("SELECT * FROM removedAutoTags")
    suspend fun getAll(): List<RemovedAutoTag>
}