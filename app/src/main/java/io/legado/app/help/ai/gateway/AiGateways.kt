package io.legado.app.help.ai.gateway

import io.legado.app.help.ai.AiEntities
import io.legado.app.help.ai.AiEntities.ChatMessage
import io.legado.app.help.ai.AiEntities.ChatTool
import io.legado.app.help.ai.AiEntities.StreamChunk
import io.legado.app.help.ai.AiEntities.StreamResponseResult
import io.legado.app.help.ai.AiProviderEntity

interface AiChatGateway {

    suspend fun generate(
        provider: AiProviderEntity,
        messages: List<ChatMessage>,
        onChunk: suspend (String) -> Unit
    ): Result<String>

    suspend fun generateNoStream(
        provider: AiProviderEntity,
        messages: List<ChatMessage>
    ): Result<String>

    suspend fun generateWithTools(
        provider: AiProviderEntity,
        messages: List<Map<String, Any>>,
        tools: List<ChatTool>,
        onChunk: suspend (StreamChunk) -> Unit
    ): Result<StreamResponseResult>
}

interface AiProviderGateway {
    suspend fun getAll(): List<AiProviderEntity>
    suspend fun getById(id: String): AiProviderEntity?
    suspend fun getDefault(): AiProviderEntity?
    suspend fun save(provider: AiProviderEntity)
    suspend fun delete(id: String)
}

interface AiHistoryGateway {
    suspend fun getSessions(): List<AiEntities.AiChatSession>
    suspend fun saveSession(session: AiEntities.AiChatSession)
    suspend fun deleteSession(id: String)
    suspend fun clear()
}
