package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookTagRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTagRelationDao {

    @Query("SELECT DISTINCT * FROM bookTagRelations WHERE bookUrl = :bookUrl")
    suspend fun getRelationsByBook(bookUrl: String): List<BookTagRelation>

    @Query("SELECT * FROM bookTagRelations WHERE tagId = :tagId")
    suspend fun getRelationsByTag(tagId: Long): List<BookTagRelation>

    @Query("SELECT * FROM bookTagRelations WHERE bookUrl = :bookUrl")
    fun observeRelationsByBook(bookUrl: String): Flow<List<BookTagRelation>>

    @Query("SELECT * FROM bookTagRelations WHERE tagId = :tagId")
    fun observeRelationsByTag(tagId: Long): Flow<List<BookTagRelation>>

    @Query("SELECT * FROM bookTagRelations WHERE bookUrl = :bookUrl AND tagId = :tagId")
    suspend fun getRelation(bookUrl: String, tagId: Long): BookTagRelation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: BookTagRelation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relations: List<BookTagRelation>): List<Long>

    @Update
    suspend fun update(relation: BookTagRelation)

    @Delete
    suspend fun delete(relation: BookTagRelation)

    @Query("DELETE FROM bookTagRelations WHERE bookUrl = :bookUrl AND tagId = :tagId")
    suspend fun deleteRelation(bookUrl: String, tagId: Long)

    @Query("DELETE FROM bookTagRelations WHERE bookUrl = :bookUrl")
    suspend fun deleteRelationsByBook(bookUrl: String)

    @Query("DELETE FROM bookTagRelations WHERE tagId = :tagId")
    suspend fun deleteRelationsByTag(tagId: Long)

    @Query("DELETE FROM bookTagRelations")
    suspend fun deleteAll()
    
    @Query("DELETE FROM bookTagRelations WHERE tagId IN (SELECT id FROM bookTags WHERE name = :tagName)")
    suspend fun deleteRelationsByTagName(tagName: String)

    @Query("SELECT COUNT(DISTINCT bookUrl) FROM bookTagRelations WHERE tagId = :tagId")
    suspend fun countBooksByTagId(tagId: Long): Int

    @Query("SELECT tagId, COUNT(DISTINCT bookUrl) as bookCount FROM bookTagRelations GROUP BY tagId")
    fun observeTagBookCounts(): Flow<List<TagBookCount>>

    @Query("SELECT tagId, COUNT(DISTINCT bookUrl) as bookCount FROM bookTagRelations GROUP BY tagId")
    suspend fun getTagBookCounts(): List<TagBookCount>
    
    /**
     * 获取所有标签关系
     */
    @Query("SELECT * FROM bookTagRelations")
    suspend fun getAll(): List<BookTagRelation>

    // 内部数据类，用于存储标签书籍数量统计结果
    data class TagBookCount(
        val tagId: Long,
        val bookCount: Int
    )
}