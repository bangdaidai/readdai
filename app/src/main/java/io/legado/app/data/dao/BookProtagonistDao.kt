package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookProtagonist

/**
 * 书籍主角名数据访问对象
 */
@Dao
interface BookProtagonistDao {
    
    /**
     * 获取书籍的所有主角名
     */
    @Query("SELECT * FROM bookProtagonists WHERE bookUrl = :bookUrl ORDER BY createTime ASC")
    fun getByBook(bookUrl: String): List<BookProtagonist>
    
    /**
     * 插入主角名
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(protagonist: BookProtagonist)
    
    /**
     * 批量插入主角名
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(protagonists: List<BookProtagonist>)
    
    /**
     * 删除主角名
     */
    @Query("DELETE FROM bookProtagonists WHERE id = :id")
    fun delete(id: Long)
    
    /**
     * 删除书籍的所有主角名
     */
    @Query("DELETE FROM bookProtagonists WHERE bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)
    
    /**
     * 检查书籍是否有主角名
     */
    @Query("SELECT COUNT(*) FROM bookProtagonists WHERE bookUrl = :bookUrl")
    fun countByBook(bookUrl: String): Int
    
    /**
     * 获取所有书籍主角
     */
    @Query("SELECT * FROM bookProtagonists")
    fun getAll(): List<BookProtagonist>
}
