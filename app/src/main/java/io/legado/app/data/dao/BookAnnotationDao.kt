package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookAnnotation
import kotlinx.coroutines.flow.Flow

@Dao
interface BookAnnotationDao {

    @Query("SELECT * FROM bookAnnotations ORDER BY time DESC")
    fun flowAll(): Flow<List<BookAnnotation>>

    @get:Query("SELECT * FROM bookAnnotations ORDER BY time DESC")
    val all: List<BookAnnotation>

    @Query("SELECT * FROM bookAnnotations WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY time DESC")
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookAnnotation>>

    @Query("SELECT * FROM bookAnnotations WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY time DESC")
    fun getByBook(bookName: String, bookAuthor: String): List<BookAnnotation>

    @Query("SELECT * FROM bookAnnotations WHERE bookName = :bookName AND bookAuthor = :bookAuthor AND time = :time LIMIT 1")
    fun getByTime(bookName: String, bookAuthor: String, time: Long): BookAnnotation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookAnnotation: BookAnnotation)

    @Update
    fun update(bookAnnotation: BookAnnotation)

    @Delete
    fun delete(bookAnnotation: BookAnnotation)

    @Query("DELETE FROM bookAnnotations")
    fun deleteAll()

    @Query("DELETE FROM bookAnnotations WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    fun deleteByBook(bookName: String, bookAuthor: String)

    @Query("SELECT COUNT(*) FROM bookAnnotations WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    fun getCount(bookName: String, bookAuthor: String): Int
}