package io.legado.app.help.ai.rag

import io.legado.app.data.entities.BookChapter

/**
 * 内容分块器
 * 参照ReadAny的chunker.ts实现
 * 针对legado特性优化：支持按章节分块，支持网络书源
 */
object TextChunker {

    /**
     * 默认配置
     */
    const val DEFAULT_CHUNK_SIZE = 500
    const val DEFAULT_CHUNK_OVERLAP = 100
    const val MIN_CHUNK_SIZE = 50

    /**
     * 从章节内容创建文本块
     */
    fun chunkChapter(
        bookUrl: String,
        chapter: BookChapter,
        content: String,
        config: ChunkerConfig = ChunkerConfig()
    ): List<TextChunk> {
        if (content.isBlank()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        val paragraphs = splitToParagraphs(content)
        
        var currentText = StringBuilder()
        var currentTokens = 0
        var startIndex = 0
        var chunkIndex = 0

        for ((index, paragraph) in paragraphs.withIndex()) {
            if (paragraph.isBlank()) continue

            val paraTokens = estimateTokens(paragraph)
            
            // 如果单个段落就超过目标大小，需要进一步分割
            if (paraTokens > config.targetTokens) {
                // 先保存当前累积的内容
                if (currentText.isNotEmpty()) {
                    chunks.add(createChunk(
                        bookUrl = bookUrl,
                        chapterIndex = chapter.index,
                        chapterTitle = chapter.title,
                        content = currentText.toString().trim(),
                        startIndex = startIndex,
                        chunkIndex = chunkIndex++
                    ))
                    currentText = StringBuilder()
                    currentTokens = 0
                }
                
                // 分割大段落
                val subChunks = splitLargeParagraph(
                    bookUrl = bookUrl,
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    paragraph = paragraph,
                    startIndex = index,
                    chunkIndex = chunkIndex,
                    config = config
                )
                chunks.addAll(subChunks)
                chunkIndex += subChunks.size
                startIndex = index + 1
                continue
            }

            // 检查是否需要创建新块
            if (currentTokens + paraTokens > config.targetTokens && currentTokens >= config.minTokens) {
                chunks.add(createChunk(
                    bookUrl = bookUrl,
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    content = currentText.toString().trim(),
                    startIndex = startIndex,
                    chunkIndex = chunkIndex++
                ))

                // 处理重叠
                val overlapContent = getOverlapContent(
                    currentText.toString(),
                    config.overlapSize
                )
                
                currentText = StringBuilder(overlapContent)
                currentTokens = estimateTokens(overlapContent)
                startIndex = index - countParagraphsBefore(paragraphs, overlapContent)
            }

            currentText.append(paragraph).append("\n\n")
            currentTokens += paraTokens
        }

        // 添加最后一个块
        if (currentText.isNotBlank() && currentTokens >= MIN_CHUNK_SIZE) {
            chunks.add(createChunk(
                bookUrl = bookUrl,
                chapterIndex = chapter.index,
                chapterTitle = chapter.title,
                content = currentText.toString().trim(),
                startIndex = startIndex,
                chunkIndex = chunkIndex
            ))
        }

        return chunks
    }

    /**
     * 从多章节内容创建文本块
     */
    fun chunkBook(
        bookUrl: String,
        bookTitle: String,
        chapters: List<Pair<BookChapter, String>>,
        config: ChunkerConfig = ChunkerConfig()
    ): List<TextChunk> {
        val allChunks = mutableListOf<TextChunk>()
        
        for ((chapter, content) in chapters) {
            val chapterChunks = chunkChapter(bookUrl, chapter, content, config)
            allChunks.addAll(chapterChunks)
        }

        return allChunks
    }

    /**
     * 估算token数量
     * 约等于字符数/4
     */
    fun estimateTokens(text: String): Int {
        return (text.length / 4.0).toInt() + 1
    }

    /**
     * 按段落分割
     */
    private fun splitToParagraphs(content: String): List<String> {
        // 使用简单的换行符分割，避免可变长度后瞻断言的问题
        return content
            .split("\n", "\r", "\r\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * 分割过大的段落
     */
    private fun splitLargeParagraph(
        bookUrl: String,
        chapterIndex: Int,
        chapterTitle: String,
        paragraph: String,
        startIndex: Int,
        chunkIndex: Int,
        config: ChunkerConfig
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val sentences = splitToSentences(paragraph)
        
        var currentText = StringBuilder()
        var currentTokens = 0
        var currentChunkIndex = chunkIndex

        for (sentence in sentences) {
            val sentenceTokens = estimateTokens(sentence)
            
            if (currentTokens + sentenceTokens > config.targetTokens && currentTokens >= config.minTokens) {
                chunks.add(createChunk(
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    content = currentText.toString().trim(),
                    startIndex = startIndex,
                    chunkIndex = currentChunkIndex++
                ))
                
                val overlapContent = getOverlapContent(currentText.toString(), config.overlapSize)
                currentText = StringBuilder(overlapContent)
                currentTokens = estimateTokens(overlapContent)
            }
            
            currentText.append(sentence)
            currentTokens += sentenceTokens
        }

        // 添加剩余内容
        if (currentText.isNotBlank()) {
            chunks.add(createChunk(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                content = currentText.toString().trim(),
                startIndex = startIndex,
                chunkIndex = currentChunkIndex
            ))
        }

        return chunks
    }

    /**
     * 按句子分割（支持中英文）
     */
    private fun splitToSentences(text: String): List<String> {
        // 匹配中英文句末标点
        val sentencePattern = Regex("(?<=[。！？.!?])\\s*")
        return text.split(sentencePattern)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * 获取重叠内容
     */
    private fun getOverlapContent(text: String, overlapSize: Int): String {
        if (overlapSize <= 0 || text.isBlank()) return ""
        
        // 按字符计算重叠
        val overlapChars = minOf(overlapSize * 4, text.length)
        return text.takeLast(overlapChars)
    }

    /**
     * 统计段落数量
     */
    private fun countParagraphsBefore(paragraphs: List<String>, text: String): Int {
        var count = 0
        for (para in paragraphs) {
            if (text.contains(para)) break
            count++
        }
        return count
    }

    /**
     * 创建文本块
     */
    private fun createChunk(
        bookUrl: String,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        startIndex: Int,
        chunkIndex: Int
    ): TextChunk {
        val id = "${bookUrl}_${chapterIndex}_$chunkIndex"
        
        return TextChunk(
            id = id,
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            content = content,
            startIndex = startIndex,
            endIndex = startIndex + content.length,
            tokenCount = estimateTokens(content)
        )
    }
}

/**
 * 分块配置
 */
data class ChunkerConfig(
    val targetTokens: Int = TextChunker.DEFAULT_CHUNK_SIZE,
    val minTokens: Int = TextChunker.MIN_CHUNK_SIZE,
    val overlapSize: Int = TextChunker.DEFAULT_CHUNK_OVERLAP
)
