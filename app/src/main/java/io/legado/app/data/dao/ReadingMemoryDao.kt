package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReadingMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingMemoryDao {

    @Query("SELECT * FROM readingMemories ORDER BY updateTime DESC")
    fun flowAll(): Flow<List<ReadingMemory>>

    @get:Query("SELECT * FROM readingMemories ORDER BY updateTime DESC")
    val all: List<ReadingMemory>

    @Query("SELECT * FROM readingMemories WHERE bookUrl = :bookUrl LIMIT 1")
    fun getByBookUrl(bookUrl: String): ReadingMemory?

    @Query("SELECT * FROM readingMemories WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY updateTime DESC")
    fun getByBook(bookName: String, bookAuthor: String): List<ReadingMemory>

    @Query("SELECT * FROM readingMemories WHERE id = :id LIMIT 1")
    fun getById(id: String): ReadingMemory?

    @Query("SELECT * FROM readingMemories WHERE id = :id LIMIT 1")
    fun getMemoryById(id: String): ReadingMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(readingMemory: ReadingMemory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg readingMemory: ReadingMemory)

    @Update
    fun update(readingMemory: ReadingMemory)

    @Delete
    fun delete(readingMemory: ReadingMemory)

    @Query("DELETE FROM readingMemories WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM readingMemories")
    fun deleteAll()

    @Query("DELETE FROM readingMemories WHERE bookUrl = :bookUrl")
    fun deleteByBookUrl(bookUrl: String)
    
    @Query("DELETE FROM readingMemories WHERE bookName = :bookName AND bookAuthor = :bookAuthor AND id != :excludeId")
    fun deleteByBookNameAndAuthorExcept(bookName: String, bookAuthor: String, excludeId: String)
    
    @Query("SELECT * FROM readingMemories WHERE bookName = :bookName AND bookAuthor = :bookAuthor AND readingStatus = 3 ORDER BY updateTime DESC")
    fun getAbandonedByBook(bookName: String, bookAuthor: String): List<ReadingMemory>
    
    @Query("SELECT * FROM readingMemories WHERE bookName = :bookName ORDER BY updateTime DESC")
    fun getByBookName(bookName: String): List<ReadingMemory>
}