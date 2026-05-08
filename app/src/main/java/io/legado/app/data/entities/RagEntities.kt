package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 向量记录实体
 */
@Entity(
    tableName = "ai_vectors",
    indices = [
        Index("bookUrl"),
        Index("chunkId")
    ]
)
data class VectorEntity(
    @PrimaryKey
    val id: String,
    val chunkId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val embedding: String, // JSON格式存储float数组
    val dimension: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 文本块实体
 */
@Entity(
    tableName = "ai_chunks",
    indices = [
        Index("bookUrl"),
        Index("chapterIndex")
    ]
)
data class ChunkEntity(
    @PrimaryKey
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val content: String,
    val startIndex: Int,
    val endIndex: Int,
    val tokenCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 向量化书籍记录
 */
@Entity(tableName = "ai_vectorized_books")
data class VectorizedBookEntity(
    @PrimaryKey
    val bookUrl: String,
    val bookTitle: String,
    val totalChunks: Int,
    val totalVectors: Int,
    val chunkSize: Int,
    val modelProvider: String,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
