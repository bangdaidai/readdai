package io.legado.app.data.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordSummary
import io.legado.app.data.entities.readRecord.ReadSession
import kotlinx.coroutines.flow.Flow

@androidx.room.Dao
interface ReadRecordDao {

    // ReadRecord 相关操作
    @Insert
    suspend fun insert(vararg readRecord: ReadRecord)

    @Update
    suspend fun update(vararg record: ReadRecord)

    @Delete
    fun delete(vararg record: ReadRecord)

    @Query("delete from readRecord")
    fun clear()

    @Query("delete from readRecord where bookName = :bookName")
    fun deleteByName(bookName: String)

    // ReadSession 相关操作
    @Insert
    suspend fun insertSession(session: ReadSession)

    @Query("SELECT * FROM readSession WHERE bookName = :bookName ORDER BY startTime DESC")
    suspend fun getSessionsByBook(bookName: String): List<ReadSession>

    @Query(
        """
        SELECT * FROM readSession 
        WHERE bookName = :bookName 
        AND STRFTIME('%Y-%m-%d', datetime(endTime/1000, 'unixepoch', 'localtime')) = :date
        ORDER BY startTime DESC"""
    )
    suspend fun getSessionsByBookAndDate(bookName: String, date: String): List<ReadSession>

    @Query("SELECT * FROM readSession ORDER BY startTime ASC")
    fun getAllSessions(): Flow<List<ReadSession>>

    @Query(
        """
        DELETE FROM readSession 
        WHERE bookName = :bookName 
        AND STRFTIME('%Y-%m-%d', datetime(endTime/1000, 'unixepoch', 'localtime')) = :date"""
    )
    suspend fun deleteSessionsByBookAndDate(bookName: String, date: String)

    @Delete
    suspend fun deleteSession(session: ReadSession)

    @Query("DELETE FROM readSession WHERE bookName = :bookName")
    suspend fun deleteSessionsByBook(bookName: String)

    // 统计相关操作
    @Query("SELECT SUM(duration) FROM readSession")
    fun getTotalReadTime(): kotlinx.coroutines.flow.Flow<Long?>

    @Query(
        """
        SELECT deviceId, bookName, SUM(duration) as readTime, MAX(endTime) as lastRead 
        FROM readSession 
        WHERE bookName LIKE '%' || :searchKey || '%' 
        GROUP BY deviceId, bookName 
        ORDER BY lastRead DESC"""
    )
    fun getLatestReadRecords(searchKey: String): Flow<List<ReadRecordSummary>>

}
