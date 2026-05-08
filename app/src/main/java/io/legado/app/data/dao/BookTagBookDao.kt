package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookTagBook
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTagBookDao {

    @Query("SELECT * FROM bookTagBooks ORDER BY bookUrl ASC")
    fun observeAll(): Flow<List<BookTagBook>>

    @Query("SELECT * FROM bookTagBooks ORDER BY bookUrl ASC")
    suspend fun getAll(): List<BookTagBook>

    @Query("SELECT * FROM bookTagBooks WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): List<BookTagBook>

    @Query("SELECT * FROM bookTagBooks WHERE tagName = :tagName")
    suspend fun getByTagName(tagName: String): List<BookTagBook>

    @Query("SELECT * FROM bookTagBooks WHERE bookUrl = :bookUrl AND tagName = :tagName")
    suspend fun getByBookUrlAndTagName(bookUrl: String, tagName: String): BookTagBook?

    @Query("SELECT * FROM bookTagBooks WHERE tagName = :tagName ORDER BY bookUrl ASC")
    fun flowByTagName(tagName: String): Flow<List<BookTagBook>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookTagBook: BookTagBook): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookTagBooks: List<BookTagBook>): List<Long>

    @Update
    suspend fun update(bookTagBook: BookTagBook)

    @Delete
    suspend fun delete(bookTagBook: BookTagBook)

    @Query("DELETE FROM bookTagBooks WHERE bookUrl = :bookUrl AND tagName = :tagName")
    suspend fun deleteByBookUrlAndTagName(bookUrl: String, tagName: String)

    @Query("DELETE FROM bookTagBooks WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)

    @Query("DELETE FROM bookTagBooks WHERE tagName = :tagName")
    suspend fun deleteByTagName(tagName: String)

    @Query("DELETE FROM bookTagBooks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM bookTagBooks WHERE tagName = :tagName")
    suspend fun countBooksByTagName(tagName: String): Int

    @Query("SELECT tagName, COUNT(*) as bookCount FROM bookTagBooks GROUP BY tagName")
    fun observeTagBookCounts(): Flow<List<TagBookCount>>

    @Query("SELECT tagName, COUNT(*) as bookCount FROM bookTagBooks GROUP BY tagName")
    suspend fun getTagBookCounts(): List<TagBookCount>

    // 内部数据类，用于存储标签书籍数量统计结果
    data class TagBookCount(
        val tagName: String,
        val bookCount: Int
    )
}