package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.ChunkEntity
import io.legado.app.data.entities.VectorEntity
import io.legado.app.data.entities.VectorizedBookEntity

/**
 * 向量DAO
 */
@Dao
interface VectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vectors: List<VectorEntity>)

    @Query("DELETE FROM ai_vectors WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)

    @Query("DELETE FROM ai_vectors WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM ai_vectors WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): List<VectorEntity>

    @Query("SELECT * FROM ai_vectors WHERE id = :id")
    suspend fun getById(id: String): VectorEntity?

    @Query("SELECT COUNT(*) FROM ai_vectors WHERE bookUrl = :bookUrl")
    suspend fun getCountByBookUrl(bookUrl: String): Int

    @Query("SELECT COUNT(*)")
    suspend fun getTotalCount(): Int

    @Query("SELECT DISTINCT bookUrl FROM ai_vectors")
    suspend fun getVectorizedBooks(): List<String>

    @Query("DELETE FROM ai_vectors")
    suspend fun clearAll()
}

/**
 * 文本块DAO
 */
@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunks: List<ChunkEntity>)

    @Query("DELETE FROM ai_chunks WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)

    @Query("SELECT * FROM ai_chunks WHERE id = :id")
    suspend fun getById(id: String): ChunkEntity?

    @Query("SELECT * FROM ai_chunks WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM ai_chunks WHERE bookUrl = :bookUrl")
    suspend fun getCountByBookUrl(bookUrl: String): Int
}

/**
 * 向量化书籍DAO
 */
@Dao
interface VectorizedBookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: VectorizedBookEntity)

    @Query("SELECT * FROM ai_vectorized_books")
    suspend fun getAll(): List<VectorizedBookEntity>

    @Query("SELECT * FROM ai_vectorized_books")
    fun flowAll(): kotlinx.coroutines.flow.Flow<List<VectorizedBookEntity>>

    @Query("SELECT * FROM ai_vectorized_books WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): VectorizedBookEntity?

    @Query("DELETE FROM ai_vectorized_books WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)

    @Query("DELETE FROM ai_vectorized_books")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_vectorized_books")
    suspend fun getCount(): Int
}
