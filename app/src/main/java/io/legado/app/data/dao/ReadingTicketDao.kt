package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.ReadingTicket
import kotlinx.coroutines.flow.Flow

/**
 * 阅读小票数据访问对象
 */
@Dao
interface ReadingTicketDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: ReadingTicket)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tickets: List<ReadingTicket>)
    
    @Update
    suspend fun update(ticket: ReadingTicket)
    
    @Delete
    suspend fun delete(ticket: ReadingTicket)
    
    @Query("SELECT * FROM readingTickets WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): ReadingTicket?
    
    @Query("SELECT * FROM readingTickets WHERE bookUrl = :bookUrl")
    fun observeByBookUrl(bookUrl: String): Flow<ReadingTicket?>
    
    @Query("SELECT * FROM readingTickets ORDER BY lastReadTime DESC")
    suspend fun getAll(): List<ReadingTicket>
    
    @Query("SELECT * FROM readingTickets ORDER BY lastReadTime DESC")
    fun observeAll(): Flow<List<ReadingTicket>>
    
    @Query("SELECT * FROM readingTickets WHERE finishTime > 0 ORDER BY finishTime DESC")
    suspend fun getFinishedBooks(): List<ReadingTicket>
    
    @Query("SELECT * FROM readingTickets WHERE readCount >= :count ORDER BY readCount DESC")
    suspend fun getMultiReadBooks(count: Int = 1): List<ReadingTicket>
    
    @Query("DELETE FROM readingTickets WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)
    
    @Query("UPDATE readingTickets SET totalReadTime = totalReadTime + :addTime, lastReadTime = :lastReadTime WHERE bookUrl = :bookUrl")
    suspend fun addReadTime(bookUrl: String, addTime: Long, lastReadTime: Long = System.currentTimeMillis())
    
    @Query("UPDATE readingTickets SET readCount = readCount + 1 WHERE bookUrl = :bookUrl")
    suspend fun incrementReadCount(bookUrl: String)
    
    @Query("UPDATE readingTickets SET rating = :rating WHERE bookUrl = :bookUrl")
    suspend fun updateRating(bookUrl: String, rating: Float)
    
    @Query("UPDATE readingTickets SET finishTime = :finishTime WHERE bookUrl = :bookUrl")
    suspend fun setFinishTime(bookUrl: String, finishTime: Long = System.currentTimeMillis())
    
    @Query("UPDATE readingTickets SET completedChapters = :completedChapters, totalChapters = :totalChapters WHERE bookUrl = :bookUrl")
    suspend fun updateProgress(bookUrl: String, completedChapters: Int, totalChapters: Int)
}
