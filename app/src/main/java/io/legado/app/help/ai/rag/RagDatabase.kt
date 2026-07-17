package io.legado.app.help.ai.rag

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.data.appDb
import io.legado.app.data.entities.ChunkEntity
import io.legado.app.data.entities.VectorEntity
import io.legado.app.data.entities.VectorizedBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private val executor = Executors.newSingleThreadExecutor()

class TextChunkDatabase {

    private val gson = Gson()

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
        appDb.chunkDao.insert(entities)
    }

    suspend fun getChunkById(id: String): TextChunk? = withContext(Dispatchers.IO) {
        appDb.chunkDao.getById(id)?.let { entity ->
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
        appDb.chunkDao.getByBookUrl(bookUrl).map { entity ->
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
        appDb.chunkDao.deleteByBookUrl(bookUrl)
    }
}

class VectorDb {

    private val gson = Gson()

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
        appDb.vectorDao.insert(entities)
    }

    suspend fun deleteByBookUrl(bookUrl: String) = withContext(Dispatchers.IO) {
        appDb.vectorDao.deleteByBookUrl(bookUrl)
    }

    suspend fun getByBookUrl(bookUrl: String): List<VectorRecord> = withContext(Dispatchers.IO) {
        appDb.vectorDao.getByBookUrl(bookUrl).map { entity ->
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
        appDb.vectorDao.getCountByBookUrl(bookUrl)
    }

    suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) {
        appDb.vectorDao.getTotalCount()
    }

    suspend fun getVectorizedBooks(): List<String> = withContext(Dispatchers.IO) {
        appDb.vectorDao.getVectorizedBooks()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        appDb.vectorDao.clearAll()
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

class VectorizedBookManager {

    suspend fun saveBook(book: VectorizedBookEntity) = withContext(Dispatchers.IO) {
        appDb.vectorizedBookDao.insert(book)
    }

    suspend fun getAllBooks(): List<VectorizedBookEntity> = withContext(Dispatchers.IO) {
        appDb.vectorizedBookDao.getAll()
    }

    suspend fun getBook(bookUrl: String): VectorizedBookEntity? = withContext(Dispatchers.IO) {
        appDb.vectorizedBookDao.getByBookUrl(bookUrl)
    }

    suspend fun deleteBook(bookUrl: String) = withContext(Dispatchers.IO) {
        appDb.vectorizedBookDao.deleteByBookUrl(bookUrl)
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        appDb.vectorizedBookDao.getCount()
    }
}
