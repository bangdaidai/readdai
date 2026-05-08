package io.legado.app.help.ai.rag

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.data.dao.ChunkDao
import io.legado.app.data.dao.VectorDao
import io.legado.app.data.dao.VectorizedBookDao
import io.legado.app.data.entities.ChunkEntity
import io.legado.app.data.entities.VectorEntity
import io.legado.app.data.entities.VectorizedBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * RAG数据库管理器
 * 独立管理向量和文本块数据
 */
object RagDatabase {

    private var database: RagDatabase_? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun getInstance(context: Context): RagDatabase_ {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                RagDatabase_::class.java,
                "legado_rag.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { database = it }
        }
    }

    fun close() {
        database?.close()
        database = null
    }
}

/**
 * RAG数据库
 */
@Database(
    entities = [
        VectorEntity::class,
        ChunkEntity::class,
        VectorizedBookEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RagDatabase_ : RoomDatabase() {
    abstract fun vectorDao(): VectorDao
    abstract fun chunkDao(): ChunkDao
    abstract fun vectorizedBookDao(): VectorizedBookDao
}

/**
 * 简化版向量数据库操作
 */
class TextChunkDatabase(private val context: Context) {

    private val gson = Gson()
    private val db get() = RagDatabase.getInstance(context)

    suspend fun insertChunks(chunks: List<TextChunk>) = withContext(Dispatchers.IO) {
        val entities = chunks.map { chunk ->
            ChunkEntity(
                id = chunk.id,
                bookUrl = chunk.bookUrl,
                chapterIndex = chunk.chapterIndex,
                chapterTitle = chunk.chapterTitle,
                content = chunk.content,
                startIndex = chunk.startIndex,
                endIndex = chunk.endIndex,
                tokenCount = chunk.tokenCount,
                createdAt = chunk.createdAt
            )
        }
        db.chunkDao().insert(entities)
    }

    suspend fun getChunkById(id: String): TextChunk? = withContext(Dispatchers.IO) {
        db.chunkDao().getById(id)?.let { entity ->
            TextChunk(
                id = entity.id,
                bookUrl = entity.bookUrl,
                chapterIndex = entity.chapterIndex,
                chapterTitle = entity.chapterTitle,
                content = entity.content,
                startIndex = entity.startIndex,
                endIndex = entity.endIndex,
                tokenCount = entity.tokenCount,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun getChunksByBookUrl(bookUrl: String): List<TextChunk> = withContext(Dispatchers.IO) {
        db.chunkDao().getByBookUrl(bookUrl).map { entity ->
            TextChunk(
                id = entity.id,
                bookUrl = entity.bookUrl,
                chapterIndex = entity.chapterIndex,
                chapterTitle = entity.chapterTitle,
                content = entity.content,
                startIndex = entity.startIndex,
                endIndex = entity.endIndex,
                tokenCount = entity.tokenCount,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun deleteByBookUrl(bookUrl: String) = withContext(Dispatchers.IO) {
        db.chunkDao().deleteByBookUrl(bookUrl)
    }
}

/**
 * 向量数据库操作（更新版）
 */
class VectorDb(private val context: Context) {

    private val gson = Gson()
    private val db get() = RagDatabase.getInstance(context)

    suspend fun insertVectors(records: List<VectorRecord>) = withContext(Dispatchers.IO) {
        val entities = records.map { record ->
            VectorEntity(
                id = record.id,
                chunkId = record.chunkId,
                bookUrl = record.bookUrl,
                chapterIndex = record.chapterIndex,
                embedding = gson.toJson(record.embedding.toList()),
                dimension = record.embedding.size,
                createdAt = record.createdAt
            )
        }
        db.vectorDao().insert(entities)
    }

    suspend fun deleteByBookUrl(bookUrl: String) = withContext(Dispatchers.IO) {
        db.vectorDao().deleteByBookUrl(bookUrl)
    }

    suspend fun getByBookUrl(bookUrl: String): List<VectorRecord> = withContext(Dispatchers.IO) {
        db.vectorDao().getByBookUrl(bookUrl).map { entity ->
            val embeddingList: List<Double> = gson.fromJson(
                entity.embedding,
                object : TypeToken<List<Double>>() {}.type
            )
            VectorRecord(
                id = entity.id,
                chunkId = entity.chunkId,
                bookUrl = entity.bookUrl,
                chapterIndex = entity.chapterIndex,
                embedding = embeddingList.map { it.toFloat() }.toFloatArray(),
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun getCountByBookUrl(bookUrl: String): Int = withContext(Dispatchers.IO) {
        db.vectorDao().getCountByBookUrl(bookUrl)
    }

    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) {
        db.vectorDao().getTotalCount()
    }

    suspend fun getVectorizedBooks(): List<String> = withContext(Dispatchers.IO) {
        db.vectorDao().getVectorizedBooks()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.vectorDao().clearAll()
    }

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
            return if (denominator > 0) dotProduct / denominator else 0f
        }
    }
}

/**
 * 向量化书籍管理
 */
class VectorizedBookManager(private val context: Context) {

    private val db get() = RagDatabase.getInstance(context)

    suspend fun saveBook(book: VectorizedBookEntity) = withContext(Dispatchers.IO) {
        db.vectorizedBookDao().insert(book)
    }

    suspend fun getAllBooks(): List<VectorizedBookEntity> = withContext(Dispatchers.IO) {
        db.vectorizedBookDao().getAll()
    }

    suspend fun getBook(bookUrl: String): VectorizedBookEntity? = withContext(Dispatchers.IO) {
        db.vectorizedBookDao().getByBookUrl(bookUrl)
    }

    suspend fun deleteBook(bookUrl: String) = withContext(Dispatchers.IO) {
        db.vectorizedBookDao().deleteByBookUrl(bookUrl)
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        db.vectorizedBookDao().getCount()
    }
}
