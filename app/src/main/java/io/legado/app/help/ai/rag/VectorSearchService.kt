package io.legado.app.help.ai.rag

import android.content.Context
import io.legado.app.data.appDb
import io.legado.app.help.ai.rag.VectorConfig
import io.legado.app.help.ai.rag.EmbeddingService
import io.legado.app.help.ai.rag.SearchResult
import io.legado.app.help.ai.rag.TextChunk
import io.legado.app.data.entities.VectorizedBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 向量搜索服务
 * 整合向量数据库、文本块存储和嵌入服务
 * 使用AppDatabase统一管理数据
 */
class VectorSearchService(private val context: Context) {

    private val vectorDao get() = appDb.vectorDao
    private val chunkDao get() = appDb.chunkDao
    
    // ✅ 缓存机制：缓存已加载的 chunks，避免重复查询数据库
    private val chunkCache = android.util.LruCache<String, List<io.legado.app.data.entities.ChunkEntity>>(10)
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5分钟
    private data class CacheEntry(val chunks: List<io.legado.app.data.entities.ChunkEntity>, val timestamp: Long)

    /**
     * 向量化书籍
     */
    suspend fun vectorizeBook(
        bookUrl: String,
        bookTitle: String,
        chapters: List<Pair<Int, String>>, // chapterIndex to content
        config: VectorConfig,
        onProgress: (VectorProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                "Vectorize",
                "========== 开始向量化书籍: $bookTitle =========="
            )
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                "Vectorize",
                "配置: provider=${config.modelProvider}, model=${config.modelName}, chunkSize=${config.chunkSize}"
            )
            
            // 1. 清空现有向量
            vectorDao.deleteByBookUrl(bookUrl)
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "Vectorize",
                "已清空现有向量数据"
            )

            // 2. 创建嵌入服务
            val embeddingService = EmbeddingService(config)

            // 3. 分块并向量化
            var processedChunks = 0
            var totalChapters = 0
            var skippedChapters = 0

            for ((chapterIndex, content) in chapters) {
                totalChapters++
                
                if (content.isBlank()) {
                    skippedChapters++
                    continue
                }
                
                val chapterTitle = "第${chapterIndex + 1}章"
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "Vectorize",
                    "处理章节: $chapterTitle, 内容长度=${content.length}"
                )

                // 分块
                val dummyChapter = io.legado.app.data.entities.BookChapter().apply {
                    index = chapterIndex
                    title = chapterTitle
                }

                val chunks = TextChunker.chunkChapter(
                    bookUrl = bookUrl,
                    chapter = dummyChapter,
                    content = content,
                    config = ChunkerConfig(
                        targetTokens = config.chunkSize,
                        overlapSize = config.chunkOverlap
                    )
                )
                
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                    "Vectorize",
                    "章节 $chapterTitle 分块完成: ${chunks.size}个块"
                )

                if (chunks.isEmpty()) {
                    continue
                }

                // 转换TextChunk为ChunkEntity并保存
                val chunkEntities = chunks.map { chunk ->
                    io.legado.app.data.entities.ChunkEntity(
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
                chunkDao.insert(chunkEntities)

                // 向量化
                val texts = chunks.map { it.content }
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                    "Vectorize",
                    "开始向量化章节 $chapterTitle: ${texts.size}个文本块"
                )
                val embeddings = embeddingService.embedBatch(texts)

                // 转换并保存向量
                val gson = com.google.gson.Gson()
                val vectorEntities = chunks.mapIndexed { index, chunk ->
                    io.legado.app.data.entities.VectorEntity(
                        id = chunk.id,
                        chunkId = chunk.id,
                        bookUrl = bookUrl,
                        chapterIndex = chapterIndex,
                        embedding = gson.toJson(embeddings[index].toList()),
                        dimension = embeddings[index].size
                    )
                }
                
                vectorDao.insert(vectorEntities)
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                    "Vectorize",
                    "章节 $chapterTitle 向量化完成并保存"
                )

                processedChunks += chunks.size

                onProgress(VectorProgress(
                    bookUrl = bookUrl,
                    totalChunks = processedChunks,
                    processedChunks = processedChunks,
                    status = VectorStatus.PROCESSING
                ))
            }
            
            // 如果没有处理任何块，返回失败
            if (processedChunks == 0) {
                val errorMsg = if (skippedChapters > 0) {
                    "所有章节内容为空，无法向量化"
                } else {
                    "没有可向量化的内容"
                }
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "Vectorize",
                    "向量化失败: $errorMsg (总章节=$totalChapters, 跳过=$skippedChapters)"
                )
                onProgress(VectorProgress(
                    bookUrl = bookUrl,
                    totalChunks = 0,
                    processedChunks = 0,
                    status = VectorStatus.FAILED,
                    errorMessage = errorMsg
                ))
                return@withContext Result.failure(Exception(errorMsg))
            }

            onProgress(VectorProgress(
                bookUrl = bookUrl,
                totalChunks = processedChunks,
                processedChunks = processedChunks,
                status = VectorStatus.COMPLETED
            ))
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
                "Vectorize",
                "========== 向量化完成: 总章节=$totalChapters, 处理块数=$processedChunks =========="
            )

            // 4. 保存向量化书籍记录到AppDatabase
            val vectorizedBook = VectorizedBookEntity(
                bookUrl = bookUrl,
                bookTitle = bookTitle,
                totalChunks = processedChunks,
                totalVectors = processedChunks,
                chunkSize = config.chunkSize,
                modelProvider = config.modelProvider,
                modelName = config.modelName
            )
            
            appDb.vectorizedBookDao.insert(vectorizedBook)

            Result.success(Unit)
        } catch (e: Exception) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "Vectorize",
                "向量化异常: ${e.message}\n${e.stackTraceToString()}"
            )
            
            onProgress(VectorProgress(
                bookUrl = bookUrl,
                totalChunks = 0,
                processedChunks = 0,
                status = VectorStatus.FAILED,
                errorMessage = e.message
            ))
            Result.failure(e)
        }
    }

    /**
     * 获取缓存的 chunks（参考 ReadAny 实现）
     */
    private suspend fun getCachedChunks(bookUrl: String): List<io.legado.app.data.entities.ChunkEntity> {
        // 检查缓存
        chunkCache.get(bookUrl)?.let { return it }
        
        // 从数据库加载
        val chunks = chunkDao.getByBookUrl(bookUrl)
        
        // 存入缓存
        if (chunks.isNotEmpty()) {
            chunkCache.put(bookUrl, chunks)
        }
        
        return chunks
    }

    /**
     * 智能截断内容，控制 token 数量（参考 ReadAny 实现）
     */
    private fun truncateByTokens(content: String, maxTokens: Int): String {
        // 简单估算：1个中文字符 ≈ 1 token，1个英文单词 ≈ 1.3 tokens
        // 保守估计：按字符数截断
        if (content.length <= maxTokens) return content
        
        // 找到合适的截断点（尽量在句子边界）
        val truncated = content.take(maxTokens)
        val lastSentenceEnd = maxOf(
            truncated.lastIndexOf('。'),
            truncated.lastIndexOf('！'),
            truncated.lastIndexOf('？'),
            truncated.lastIndexOf('.'),
            truncated.lastIndexOf('!'),
            truncated.lastIndexOf('?')
        )
        
        return if (lastSentenceEnd > maxTokens / 2) {
            truncated.substring(0, lastSentenceEnd + 1) + "..."
        } else {
            truncated + "..."
        }
    }

    /**
     * 语义搜索（优化版：添加缓存和智能截断）
     */
    suspend fun semanticSearch(
        query: String,
        bookUrl: String?,
        config: VectorConfig,
        topK: Int = 5,
        maxTokensPerResult: Int = 500  // ✅ 新增：每个结果的最大 token 数
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "VectorSearch",
            "开始语义搜索: query='$query', topK=$topK"
        )
        
        // 1. 向量化查询
        val embeddingService = EmbeddingService(config)
        val queryEmbedding = embeddingService.embed(query)

        // 2. 搜索向量
        val bookUrls = if (bookUrl != null) listOf(bookUrl) else vectorDao.getVectorizedBooks()

        val allResults = mutableListOf<Pair<TextChunk, Float>>()
        val gson = com.google.gson.Gson()

        for (url in bookUrls) {
            val vectorEntities = vectorDao.getByBookUrl(url)

            if (vectorEntities.isEmpty()) continue

            // ✅ 使用缓存获取 chunks（减少数据库查询）
            val cachedChunks = getCachedChunks(url)
            val chunkMap = cachedChunks.associateBy { it.id }

            // 计算余弦相似度
            val scoredVectors = vectorEntities.map { entity ->
                val embeddingList: List<Double> = gson.fromJson(
                    entity.embedding,
                    object : com.google.gson.reflect.TypeToken<List<Double>>() {}.type
                )
                val embedding = embeddingList.map { it.toFloat() }.toFloatArray()
                val score = VectorDb.cosineSimilarity(queryEmbedding, embedding)
                entity to score
            }.sortedByDescending { it.second }
                .take(topK)

            for ((entity, score) in scoredVectors) {
                // ✅ 从缓存中获取 chunk（而不是再次查询数据库）
                val chunkEntity = chunkMap[entity.chunkId]
                if (chunkEntity != null) {
                    val chunk = TextChunk(
                        id = chunkEntity.id,
                        bookUrl = chunkEntity.bookUrl,
                        chapterIndex = chunkEntity.chapterIndex,
                        chapterTitle = chunkEntity.chapterTitle,
                        content = chunkEntity.content,
                        startIndex = chunkEntity.startIndex,
                        endIndex = chunkEntity.endIndex,
                        tokenCount = chunkEntity.tokenCount,
                        createdAt = chunkEntity.createdAt
                    )
                    allResults.add(chunk to score)
                }
            }
        }

        // 3. 排序返回topK，并智能截断内容
        val results = allResults.sortedByDescending { it.second }
            .take(topK)
            .map { (chunk, score) ->
                // ✅ 智能截断，控制 token 数量
                val truncatedContent = truncateByTokens(chunk.content, maxTokensPerResult)
                SearchResult(
                    chunk = chunk,
                    score = score,
                    content = truncatedContent
                )
            }
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "VectorSearch",
            "搜索完成: 返回 ${results.size} 条结果"
        )
        
        return@withContext results
    }

    /**
     * 删除书籍向量
     */
    suspend fun deleteBookVectors(bookUrl: String) {
        vectorDao.deleteByBookUrl(bookUrl)
        chunkDao.deleteByBookUrl(bookUrl)
    }

    /**
     * 获取向量统计
     */
    suspend fun getStats(): Map<String, Any> {
        val totalVectors = vectorDao.getTotalCount()
        val vectorizedBooks = vectorDao.getVectorizedBooks()

        return mapOf(
            "totalVectors" to totalVectors,
            "totalBooks" to vectorizedBooks.size,
            "books" to vectorizedBooks
        )
    }
    
    // ============================================
    // BM25 关键词搜索（参考 ReadAny 实现）
    // ============================================
    
    /**
     * BM25 关键词搜索
     */
    suspend fun bm25Search(
        query: String,
        bookUrl: String,
        topK: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "BM25Search",
            "开始BM25搜索: query='$query', topK=$topK"
        )
        
        val chunks = getCachedChunks(bookUrl)
        if (chunks.isEmpty()) return@withContext emptyList()
        
        // 分词
        val queryTerms = tokenizeQuery(query)
        if (queryTerms.isEmpty()) return@withContext emptyList()
        
        // 计算 BM25 分数
        val results = chunks.mapNotNull { chunk ->
            val score = calculateBM25Score(chunk.content, queryTerms, chunks.size)
            if (score > 0) SearchResult(
                chunk = TextChunk(
                    id = chunk.id,
                    bookUrl = chunk.bookUrl,
                    chapterIndex = chunk.chapterIndex,
                    chapterTitle = chunk.chapterTitle,
                    content = chunk.content,
                    startIndex = chunk.startIndex,
                    endIndex = chunk.endIndex,
                    tokenCount = chunk.tokenCount,
                    createdAt = chunk.createdAt
                ),
                score = score.toFloat(),
                content = chunk.content.take(500)  // 限制长度
            ) else null
        }.sortedByDescending { it.score }
         .take(topK)
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "BM25Search",
            "BM25搜索完成: 返回 ${results.size} 条结果"
        )
        
        return@withContext results
    }
    
    /**
     * 混合搜索（vector + BM25 + RRF融合）
     */
    suspend fun hybridSearch(
        query: String,
        bookUrl: String?,
        config: VectorConfig,
        topK: Int = 5
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "HybridSearch",
            "开始混合搜索: query='$query', topK=$topK"
        )
        
        // ✅ 扩大搜索范围，获取足够多的结果用于融合（至少3倍）
        // 这样可以确保靠后章节的 chunk 有机会进入候选列表
        val expandedTopK = topK * 3
        
        // 并行执行两种搜索
        val vectorResults = try {
            semanticSearch(query, bookUrl, config, expandedTopK)
        } catch (e: Exception) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                "HybridSearch",
                "向量搜索失败: ${e.message}，回退到BM25"
            )
            emptyList()
        }
        
        val bm25Results = if (bookUrl != null) {
            bm25Search(query, bookUrl, expandedTopK)
        } else {
            emptyList()
        }
        
        // RRF 融合
        val fusedResults = rrfFusion(vectorResults, bm25Results, topK)
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "HybridSearch",
            "混合搜索完成: vector=${vectorResults.size}, bm25=${bm25Results.size}, fused=${fusedResults.size}"
        )
        
        return@withContext fusedResults
    }
    
    /**
     * RRF (Reciprocal Rank Fusion) 融合算法
     * ✅ 增强版：添加章节位置权重，优先返回靠后章节的内容
     */
    private fun rrfFusion(
        vectorResults: List<SearchResult>,
        bm25Results: List<SearchResult>,
        topK: Int,
        k: Int = 60
    ): List<SearchResult> {
        val scores = mutableMapOf<String, Double>()
        val chunkMap = mutableMapOf<String, SearchResult>()
        
        // 找到最大章节索引，用于归一化
        val allChunks = vectorResults.map { it.chunk } + bm25Results.map { it.chunk }
        val maxChapterIndex = allChunks.maxOfOrNull { it.chapterIndex } ?: 0
        
        // 向量结果的分数：1 / (k + 排名)
        vectorResults.forEachIndexed { rank, result ->
            val id = result.chunk.id
            scores[id] = scores.getOrDefault(id, 0.0) + 1.0 / (k + rank + 1)
            chunkMap[id] = result
        }
        
        // BM25 结果的分数：1 / (k + 排名)
        bm25Results.forEachIndexed { rank, result ->
            val id = result.chunk.id
            scores[id] = scores.getOrDefault(id, 0.0) + 1.0 / (k + rank + 1)
            if (!chunkMap.containsKey(id)) {
                chunkMap[id] = result.copy(matchType = "hybrid")
            }
        }
        
        // ✅ 按综合分数排序，然后应用章节位置权重
        val sortedResults = scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, score) ->
                chunkMap[id]?.let { result ->
                    // 计算章节位置权重（0-1之间，越靠后权重越高）
                    val positionWeight = if (maxChapterIndex > 0) {
                        result.chunk.chapterIndex.toFloat() / maxChapterIndex
                    } else {
                        0.5f
                    }
                    
                    // 综合分数 = RRF分数 × 0.7 + 位置权重 × 0.3
                    val combinedScore = (score * 0.7 + positionWeight * 0.3).toFloat()
                    
                    // ✅ 创建新的 SearchResult，更新分数
                    result.copy(score = combinedScore)
                }
            }
        
        // 返回 topK
        return sortedResults.take(topK)
    }
    
    /**
     * 简化的中文分词（用于 BM25）
     * ✅ 增强版：对“结局”、“最后”等关键词给予特殊处理
     */
    private fun tokenizeQuery(query: String): List<String> {
        // 移除停用词
        val stopWords = setOf("的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "什么", "怎么")
        
        // 提取中英文词汇
        val tokens = mutableListOf<String>()
        
        // 英文单词
        Regex("[a-zA-Z0-9]+").findAll(query).forEach {
            tokens.add(it.value.lowercase())
        }
        
        // 中文双字组合（bigram）
        val chineseChars = query.filter { it in '\u4e00'..'\u9fff' }
        for (i in 0 until chineseChars.length - 1) {
            val bigram = chineseChars.substring(i, i + 2)
            if (!stopWords.contains(bigram)) {
                tokens.add(bigram)
            }
        }
        
        // ✅ 特殊处理：如果查询包含“结局”、“结尾”、“最后”等词，添加额外的关键词
        val endingKeywords = listOf("结局", "结尾", "最后", "最终", "结束")
        for (keyword in endingKeywords) {
            if (query.contains(keyword)) {
                tokens.add(keyword)  // 添加完整关键词，提高匹配度
            }
        }
        
        return tokens.distinct().filter { it.length >= 2 }
    }
    
    /**
     * 计算 BM25 分数（简化版）
     */
    private fun calculateBM25Score(content: String, queryTerms: List<String>, totalDocs: Int): Double {
        if (queryTerms.isEmpty()) return 0.0
        
        var totalScore = 0.0
        val k1 = 1.2  // BM25 参数
        val b = 0.75  // BM25 参数
        
        for (term in queryTerms) {
            // 计算词频
            val termFreq = content.countOccurrences(term)
            if (termFreq == 0) continue
            
            // 简化的 IDF（假设所有文档都包含该词）
            val idf = Math.log((totalDocs + 1.0) / (totalDocs * 0.5 + 0.5))
            
            // TF 归一化
            val docLen = content.length
            val avgDocLen = 500.0  // 假设平均文档长度
            val normalizedTf = termFreq.toDouble() / (k1 * (1 - b + b * docLen / avgDocLen) + termFreq)
            
            totalScore += idf * normalizedTf
        }
        
        return totalScore
    }
    
    /**
     * 计算字符串中子串出现次数
     */
    private fun String.countOccurrences(substring: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = this.indexOf(substring, index)
            if (index == -1) break
            count++
            index += substring.length
        }
        return count
    }
}