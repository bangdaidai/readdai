package io.legado.app.help.ai.rag

import io.legado.app.help.ai.rag.VectorConfig
import io.legado.app.help.ai.rag.EmbeddingModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 向量化服务
 * 参照ReadAny的embedding-service.ts实现
 * 支持多种嵌入模型提供商
 */
class EmbeddingService(private val config: VectorConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取嵌入维度
     */
    fun getDimension(): Int {
        return EmbeddingModels.getModel(config.modelProvider, config.modelName)?.dimension
            ?: 1536 // 默认
    }

    /**
     * 单文本嵌入
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val results = embedBatch(listOf(text))
        results.first()
    }

    /**
     * 批量嵌入
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "Vectorize",
            "开始批量向量化: 文本数量=${texts.size}, 批次大小=${config.batchSize}"
        )
        
        val results = mutableListOf<FloatArray>()
        
        // 分批处理
        for (i in texts.indices step config.batchSize) {
            val batch = texts.subList(i, minOf(i + config.batchSize, texts.size))
            val batchNum = i / config.batchSize + 1
            val totalBatches = (texts.size + config.batchSize - 1) / config.batchSize
            
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "Vectorize",
                "处理批次 $batchNum/$totalBatches: 文本数=${batch.size}"
            )
            
            try {
                val batchResults = callEmbeddingAPI(batch)
                // 验证返回结果数量
                if (batchResults.size != batch.size) {
                    val errorMsg = "批次${batchNum}返回结果数量不匹配: 期望${batch.size}个，实际${batchResults.size}个"
                    io.legado.app.help.ai.AiLogManager.log(
                        io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                        "Vectorize",
                        errorMsg
                    )
                    throw Exception(errorMsg)
                }
                results.addAll(batchResults)
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "Vectorize",
                    "批次${batchNum}成功: 返回${batchResults.size}个向量"
                )
            } catch (e: Exception) {
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "Vectorize",
                    "批次${batchNum}批量处理失败: ${e.message}\n堆栈:\n${e.stackTraceToString()}"
                )
                
                // 如果批量失败，尝试逐个处理
                for ((textIndex, text) in batch.withIndex()) {
                    try {
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                            "Vectorize",
                            "逐个处理文本 ${textIndex + 1}/${batch.size}: 长度=${text.length}, 前50字符=${text.take(50)}"
                        )
                        val singleResult = callEmbeddingAPI(listOf(text))
                        if (singleResult.isNotEmpty()) {
                            results.add(singleResult[0])
                            io.legado.app.help.ai.AiLogManager.log(
                                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                                "Vectorize",
                                "文本 ${textIndex + 1} 成功: 维度=${singleResult[0].size}"
                            )
                        }
                    } catch (ex: Exception) {
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                            "Vectorize",
                            "文本 ${textIndex + 1} 向量化失败: ${ex.message}\n堆栈:\n${ex.stackTraceToString()}"
                        )
                        // 对于失败的文本，使用零向量作为占位符
                        val dimension = getDimension()
                        results.add(FloatArray(dimension) { 0f })
                        io.legado.app.help.ai.AiLogManager.log(
                            io.legado.app.help.ai.AiLogManager.LogLevel.WARNING,
                            "Vectorize",
                            "文本 ${textIndex + 1} 使用零向量占位: 维度=$dimension"
                        )
                    }
                }
            }
        }
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.INFO,
            "Vectorize",
            "批量向量化完成: 输入${texts.size}个文本，输出${results.size}个向量"
        )
        
        results
    }

    /**
     * 调用嵌入API
     */
    private suspend fun callEmbeddingAPI(texts: List<String>): List<FloatArray> {
        return when (config.modelProvider) {
            "openai" -> callOpenAI(texts)
            "siliconflow" -> callSiliconFlow(texts)
            "aliyun" -> callAliyun(texts)
            "deepseek" -> callDeepSeek(texts)
            "ollama" -> callOllama(texts)
            else -> callOpenAI(texts) // 默认使用OpenAI兼容格式
        }
    }

    /**
     * OpenAI兼容格式
     */
    private fun callOpenAI(texts: List<String>): List<FloatArray> {
        val requestBody = JSONObject().apply {
            put("input", JSONArray(texts))
            put("model", config.modelName)
        }.toString()
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "OpenAI请求: URL=${config.baseUrl}/embeddings, Model=${config.modelName}, 文本数=${texts.size}"
        )
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "请求体: $requestBody"
        )
        
        val request = Request.Builder()
            .url("${config.baseUrl}/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error body"
            val errorMsg = "OpenAI API错误: code=${response.code}, message=${response.message}, body=$errorBody"
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "Vectorize",
                errorMsg
            )
            throw Exception(errorMsg)
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "OpenAI响应: ${body.take(200)}..."
        )
        return parseOpenAIResponse(body)
    }

    /**
     * SiliconFlow API
     */
    private fun callSiliconFlow(texts: List<String>): List<FloatArray> {
        val requestBody = JSONObject().apply {
            put("input", JSONArray(texts))  // 修复：使用 JSONArray 确保正确序列化
            put("model", config.modelName)
        }.toString()
        
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "SiliconFlow请求: Model=${config.modelName}, 文本数=${texts.size}"
        )
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "请求体: $requestBody"
        )

        val request = Request.Builder()
            .url("https://api.siliconflow.cn/v1/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "No error body"
            val errorMsg = "SiliconFlow API错误: code=${response.code}, message=${response.message}, body=$errorBody"
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "Vectorize",
                errorMsg
            )
            throw Exception(errorMsg)
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        io.legado.app.help.ai.AiLogManager.log(
            io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
            "Vectorize",
            "SiliconFlow响应: ${body.take(200)}..."
        )
        return parseOpenAIResponse(body)
    }

    /**
     * 阿里云DashScope API
     */
    private fun callAliyun(texts: List<String>): List<FloatArray> {
        val requestBody = JSONObject().apply {
            put("input", texts)
            put("model", config.modelName)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Aliyun API error: ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        return parseOpenAIResponse(body)
    }

    /**
     * DeepSeek API
     */
    private fun callDeepSeek(texts: List<String>): List<FloatArray> {
        val requestBody = JSONObject().apply {
            put("input", texts)
            put("model", config.modelName)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("DeepSeek API error: ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response")
        return parseOpenAIResponse(body)
    }

    /**
     * Ollama 本地API
     */
    private fun callOllama(texts: List<String>): List<FloatArray> {
        val results = mutableListOf<FloatArray>()
        
        for (text in texts) {
            val requestBody = JSONObject().apply {
                put("model", config.modelName)
                put("input", text)
            }.toString().toRequestBody("application/json".toMediaType())

            val baseUrl = config.baseUrl.replace("/v1", "")
            val request = Request.Builder()
                .url("$baseUrl/api/embeddings")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Ollama API error: ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response")
            val json = JSONObject(body)
            val embedding = json.getJSONArray("embedding")
            
            val floatArray = FloatArray(embedding.length())
            for (i in 0 until embedding.length()) {
                floatArray[i] = embedding.getDouble(i).toFloat()
            }
            results.add(floatArray)
        }
        
        return results
    }

    /**
     * 解析OpenAI格式响应
     */
    private fun parseOpenAIResponse(body: String): List<FloatArray> {
        try {
            val json = JSONObject(body)
            
            // 检查是否有错误信息
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = "API返回错误: ${error.optString("message", "Unknown error")}"
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                    "Vectorize",
                    "$errorMsg\n完整响应: $body"
                )
                throw Exception(errorMsg)
            }
            
            val data = json.getJSONArray("data")
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                "Vectorize",
                "解析响应: data数组长度=${data.length()}"
            )
            
            val results = mutableListOf<FloatArray>()
            // 按index排序确保顺序正确
            val sortedData = (0 until data.length())
                .map { data.getJSONObject(it) }
                .sortedBy { it.getInt("index") }
            
            for ((idx, item) in sortedData.withIndex()) {
                val embedding = item.getJSONArray("embedding")
                val floatArray = FloatArray(embedding.length())
                for (i in 0 until embedding.length()) {
                    floatArray[i] = embedding.getDouble(i).toFloat()
                }
                results.add(floatArray)
                io.legado.app.help.ai.AiLogManager.log(
                    io.legado.app.help.ai.AiLogManager.LogLevel.DEBUG,
                    "Vectorize",
                    "解析向量[$idx]: 维度=${floatArray.size}"
                )
            }
            
            return results
        } catch (e: Exception) {
            io.legado.app.help.ai.AiLogManager.log(
                io.legado.app.help.ai.AiLogManager.LogLevel.ERROR,
                "Vectorize",
                "解析响应失败: ${e.message}\n响应内容: ${body.take(500)}\n堆栈:\n${e.stackTraceToString()}"
            )
            throw e
        }
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testText = "测试"
            embed(testText)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
