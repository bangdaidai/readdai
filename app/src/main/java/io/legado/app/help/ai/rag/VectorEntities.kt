package io.legado.app.help.ai.rag

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter

/**
 * 向量配置
 */
data class VectorConfig(
    val enabled: Boolean = false,
    val modelProvider: String = "openai",
    val modelName: String = "text-embedding-3-small",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val batchSize: Int = 20,
    val chunkSize: Int = 500,
    val chunkOverlap: Int = 100
)

/**
 * 向量模型
 */
data class EmbeddingModel(
    val provider: String,
    val name: String,
    val dimension: Int
)

/**
 * 内置嵌入模型列表
 */
object EmbeddingModels {
    val models = listOf(
        // OpenAI
        EmbeddingModel("openai", "text-embedding-3-small", 1536),
        EmbeddingModel("openai", "text-embedding-3-large", 3072),
        EmbeddingModel("openai", "text-embedding-ada-002", 1536),
        
        // 本地模型
        EmbeddingModel("local", "bge-m3", 1024),
        EmbeddingModel("local", "bge-base-zh-v1.5", 768),
        EmbeddingModel("local", "bge-small-zh-v1.5", 512),
        EmbeddingModel("local", "bge-small-en-v1.5", 384),
        EmbeddingModel("local", "gte-Qwen2-7B-instruct", 3584),
        EmbeddingModel("local", "gte-Qwen2-1.5B-instruct", 1536),
        EmbeddingModel("local", "bge-multilingual-gemma2", 1024),
        
        // 阿里云
        EmbeddingModel("aliyun", "text-embedding-v3", 1536),
        
        // SiliconFlow
        EmbeddingModel("siliconflow", "BAAI/bge-m3", 1024),
        EmbeddingModel("siliconflow", "BAAI/bge-base-zh-v1.5", 768),
        EmbeddingModel("siliconflow", "BAAI/bge-small-zh-v1.5", 512),
        EmbeddingModel("siliconflow", "Alibaba-NLP/gte-Qwen2-7B-instruct", 3584),
        EmbeddingModel("siliconflow", "Alibaba-NLP/gte-Qwen2-1.5B-instruct", 1536),
        
        // Ollama
        EmbeddingModel("ollama", "nomic-embed-text", 768),
        EmbeddingModel("ollama", "mxbai-embed-large", 1536),
        
        // DeepSeek
        EmbeddingModel("deepseek", "deepseek-embedding", 1536)
    )

    fun getModel(provider: String, name: String): EmbeddingModel? {
        return models.find { it.provider == provider && it.name == name }
    }

    fun getModelsByProvider(provider: String): List<EmbeddingModel> {
        return models.filter { it.provider == provider }
    }
}

/**
 * 文本块
 */
data class TextChunk(
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
 * 向量记录
 */
data class VectorRecord(
    val id: String,
    val chunkId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val embedding: FloatArray,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectorRecord
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * 搜索结果
 */
data class SearchResult(
    val chunk: TextChunk,
    val score: Float,
    val content: String
)

/**
 * 向量化进度
 */
data class VectorProgress(
    val bookUrl: String,
    val totalChunks: Int,
    val processedChunks: Int,
    val status: VectorStatus,
    val errorMessage: String? = null
)

enum class VectorStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
