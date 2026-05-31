package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookReview
import kotlinx.coroutines.flow.Flow

@Dao
interface BookReviewDao {

    @Query("SELECT * FROM bookReviews WHERE bookUrl = :bookUrl ORDER BY updateTime DESC")
    fun getByBook(bookUrl: String): Flow<List<BookReview>>

    @Query("SELECT * FROM bookReviews WHERE bookUrl = :bookUrl ORDER BY updateTime DESC")
    suspend fun getReviewByBookUrl(bookUrl: String): List<BookReview>

    @Query("SELECT * FROM bookReviews WHERE bookUrl = :bookUrl ORDER BY updateTime DESC")
    fun getReviewByBookUrlSync(bookUrl: String): List<BookReview>

    @Query("SELECT * FROM bookReviews WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY updateTime DESC")
    fun getByBook(bookName: String, bookAuthor: String): Flow<List<BookReview>>
    
    @Query("SELECT * FROM bookReviews WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY updateTime DESC")
    suspend fun getReviewByBook(bookName: String, bookAuthor: String): List<BookReview>

    @Query("SELECT * FROM bookReviews WHERE id = :id")
    fun getById(id: Long): BookReview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookReview: BookReview): LongArray

    @Update
    fun update(vararg bookReview: BookReview)

    @Delete
    fun delete(vararg bookReview: BookReview)

    @Query("DELETE FROM bookReviews WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM bookReviews WHERE bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)

    @Query("DELETE FROM bookReviews WHERE bookUrl = :bookUrl")
    fun deleteByBookUrl(bookUrl: String)
    
    @Query("SELECT * FROM bookReviews ORDER BY updateTime DESC")
    fun flowAll(): Flow<List<BookReview>>
    
    @Query("SELECT * FROM bookReviews ORDER BY updateTime DESC")
    fun getAll(): List<BookReview>
}