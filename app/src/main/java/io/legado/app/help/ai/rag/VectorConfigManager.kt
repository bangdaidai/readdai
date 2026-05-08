package io.legado.app.help.ai.rag

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 向量配置管理器
 */
object VectorConfigManager {

    /**
     * 获取当前向量配置
     */
    fun getConfig(): VectorConfig {
        return VectorConfig(
            enabled = appCtx.getPrefBoolean(PreferKey.aiVectorEnabled, false),
            modelProvider = appCtx.getPrefString(PreferKey.aiVectorProvider, "openai") ?: "openai",
            modelName = appCtx.getPrefString(PreferKey.aiVectorModel, "text-embedding-3-small") ?: "text-embedding-3-small",
            apiKey = appCtx.getPrefString(PreferKey.aiVectorApiKey, "") ?: "",
            baseUrl = appCtx.getPrefString(PreferKey.aiVectorBaseUrl, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            batchSize = appCtx.getPrefInt(PreferKey.aiVectorBatchSize, 20),
            chunkSize = appCtx.getPrefInt(PreferKey.aiVectorChunkSize, 500),
            chunkOverlap = appCtx.getPrefInt(PreferKey.aiVectorChunkOverlap, 100)
        )
    }

    /**
     * 保存向量配置
     */
    fun saveConfig(config: VectorConfig) {
        appCtx.putPrefBoolean(PreferKey.aiVectorEnabled, config.enabled)
        appCtx.putPrefString(PreferKey.aiVectorProvider, config.modelProvider)
        appCtx.putPrefString(PreferKey.aiVectorModel, config.modelName)
        appCtx.putPrefString(PreferKey.aiVectorApiKey, config.apiKey)
        appCtx.putPrefString(PreferKey.aiVectorBaseUrl, config.baseUrl)
        appCtx.putPrefInt(PreferKey.aiVectorBatchSize, config.batchSize)
        appCtx.putPrefInt(PreferKey.aiVectorChunkSize, config.chunkSize)
        appCtx.putPrefInt(PreferKey.aiVectorChunkOverlap, config.chunkOverlap)
    }

    /**
     * 是否启用向量搜索
     */
    fun isEnabled(): Boolean {
        return appCtx.getPrefBoolean(PreferKey.aiVectorEnabled, false)
    }

    /**
     * 获取提供商列表
     */
    fun getProviders(): List<Pair<String, String>> {
        return listOf(
            "openai" to "OpenAI",
            "siliconflow" to "SiliconFlow",
            "aliyun" to "阿里云",
            "deepseek" to "DeepSeek",
            "ollama" to "Ollama (本地)"
        )
    }

    /**
     * 获取模型列表（按提供商）
     */
    fun getModelsByProvider(provider: String): List<Pair<String, String>> {
        return when (provider) {
            "openai" -> listOf(
                "text-embedding-3-small" to "text-embedding-3-small (1536维)",
                "text-embedding-3-large" to "text-embedding-3-large (3072维)",
                "text-embedding-ada-002" to "text-embedding-ada-002 (1536维)"
            )
            "siliconflow" -> listOf(
                "BAAI/bge-m3" to "bge-m3 (1024维)",
                "BAAI/bge-base-zh-v1.5" to "bge-base-zh-v1.5 (768维)",
                "BAAI/bge-small-zh-v1.5" to "bge-small-zh-v1.5 (512维)",
                "Alibaba-NLP/gte-Qwen2-7B-instruct" to "gte-Qwen2-7B (3584维)"
            )
            "aliyun" -> listOf(
                "text-embedding-v3" to "text-embedding-v3 (1536维)"
            )
            "deepseek" -> listOf(
                "deepseek-embedding" to "deepseek-embedding (1536维)"
            )
            "ollama" -> listOf(
                "nomic-embed-text" to "nomic-embed-text (768维)",
                "mxbai-embed-large" to "mxbai-embed-large (1536维)"
            )
            else -> emptyList()
        }
    }
}
